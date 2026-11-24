package com.moava.openbanking.service;

import com.moava.openbanking.exception.KftcOpenBankingException;
import com.moava.openbanking.exception.KftcResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * KFTC v2.0 모의 오픈뱅킹 보안·검증 가드:
 * - 입력 화이트리스트 (핀테크이용번호 24자리, 은행코드, 계좌번호, 20자리 bank_tran_id, 양수 금액/건수, 적요 XSS 차단)
 * - 출력 XSS 무력화 (통장 적요, 예금주 실명)
 * - OAuth 토큰 SHA-256 해싱, CSPRNG 채번(bank_tran_id, auth_code, access_token)
 * - IP 기반 Rate Limiting 및 무차별 대입(토큰/시크릿 때려 맞추기) 차단
 */
@Slf4j
@Component
public class KftcSecurityGuard {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 500;
    private static final long MAX_SINGLE_TRANSFER_AMT = 100_000_000L; // 1회 최대 1억원
    private static final int MAX_INVALID_AUTH_ATTEMPTS = 10;
    private static final long BLOCK_SECONDS = 300;
    private static final long WINDOW_SECONDS = 60;
    private static final int MAX_MAP_SIZE = 10_000;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] ALPHANUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private static final Pattern FINTECH_NUM_PATTERN = Pattern.compile("^[0-9]{24}$");
    private static final Pattern BANK_CODE_PATTERN = Pattern.compile("^[0-9]{3,10}$");
    private static final Pattern ACCOUNT_NO_PATTERN = Pattern.compile("^[0-9]+(-[0-9]+)*$");
    private static final Pattern BANK_TRAN_ID_PATTERN = Pattern.compile("^[A-Z0-9]{10}U[A-Z0-9]{9}$");
    private static final Pattern USER_SEQ_NO_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,20}$");
    private static final Pattern DATE_YYYYMMDD_PATTERN = Pattern.compile("^[0-9]{8}$");
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Pattern DANGEROUS_URI_PATTERN = Pattern.compile("(?i)(javascript|vbscript|data)\\s*:");
    private static final Pattern SCRIPT_OR_TAG_PATTERN = Pattern.compile("[<>\"'`]");
    private static final Set<String> ALLOWED_SCOPES = Set.of("login", "inquiry", "transfer", "oob");

    private final ConcurrentHashMap<String, WindowCounter> rateCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FailureState> authFailures = new ConcurrentHashMap<>();

    // ==================== 1. 입력 화이트리스트 검증 ====================

    public String validateFintechUseNum(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, fieldName + "은(는) 필수입니다.");
        }
        String trimmed = raw.trim();
        if (!FINTECH_NUM_PATTERN.matcher(trimmed).matches()) {
            throw new KftcOpenBankingException(
                    KftcResponseCode.INVALID_PARAMETER,
                    fieldName + "은(는) 24자리 숫자여야 합니다."
            );
        }
        return trimmed;
    }

    public String validateBankCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "은행코드(bank_code_std)는 필수입니다.");
        }
        String trimmed = raw.trim();
        if (!BANK_CODE_PATTERN.matcher(trimmed).matches()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "유효하지 않은 은행코드 형식입니다.");
        }
        return trimmed;
    }

    public String validateAccountNo(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "계좌번호(account_num)는 필수입니다.");
        }
        if (CONTROL_CHAR_PATTERN.matcher(raw).find()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "계좌번호에 허용되지 않은 제어문자가 있습니다.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 30 || !ACCOUNT_NO_PATTERN.matcher(trimmed).matches()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "계좌번호는 숫자와 하이픈(-)만 입력할 수 있습니다.");
        }
        String digits = trimmed.replace("-", "");
        if (digits.length() < 8 || digits.length() > 20) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "계좌번호 숫자 자릿수가 올바르지 않습니다.");
        }
        return trimmed;
    }

    public String validateHolderName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "예금주 실명(account_holder_name)은 필수입니다.");
        }
        if (CONTROL_CHAR_PATTERN.matcher(raw).find() || SCRIPT_OR_TAG_PATTERN.matcher(raw).find()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "예금주 실명에 허용되지 않은 문자가 포함되어 있습니다.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 50) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "예금주 실명은 50자를 초과할 수 없습니다.");
        }
        return trimmed;
    }

    public String validateOrGenerateBankTranId(String raw, String orgCode) {
        if (raw == null || raw.isBlank()) {
            return generateBankTranId(orgCode);
        }
        String trimmed = raw.trim().toUpperCase();
        if (!BANK_TRAN_ID_PATTERN.matcher(trimmed).matches()) {
            throw new KftcOpenBankingException(
                    KftcResponseCode.INVALID_PARAMETER,
                    "bank_tran_id는 20자리 규격(이용기관코드10 + U + 영숫자9)이어야 합니다."
            );
        }
        return trimmed;
    }

    public long validateTransferAmount(Long rawAmount) {
        if (rawAmount == null || rawAmount <= 0) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_AMOUNT);
        }
        if (rawAmount > MAX_SINGLE_TRANSFER_AMT) {
            throw new KftcOpenBankingException(
                    KftcResponseCode.INVALID_AMOUNT,
                    "1회 최대 이체 한도(1억원)를 초과했습니다."
            );
        }
        return rawAmount;
    }

    public int validateLimit(Integer rawLimit) {
        if (rawLimit == null) {
            return DEFAULT_LIMIT;
        }
        if (rawLimit <= 0 || rawLimit > MAX_LIMIT) {
            throw new KftcOpenBankingException(
                    KftcResponseCode.INVALID_PARAMETER,
                    "조회 건수(limit)는 1 이상 " + MAX_LIMIT + " 이하여야 합니다."
            );
        }
        return rawLimit;
    }

    public String validatePrintContent(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        if (CONTROL_CHAR_PATTERN.matcher(raw).find() || SCRIPT_OR_TAG_PATTERN.matcher(raw).find()
                || DANGEROUS_URI_PATTERN.matcher(raw).find()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "통장 적요에 허용되지 않은 특수문자/스크립트가 포함되어 있습니다.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 40) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "통장 적요는 40자를 초과할 수 없습니다.");
        }
        return trimmed;
    }

    public String validateScopeString(String rawScope) {
        if (rawScope == null || rawScope.isBlank()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "scope는 필수입니다.");
        }
        String[] tokens = rawScope.trim().split("\\s+");
        StringBuilder normalized = new StringBuilder();
        for (String token : tokens) {
            if (!ALLOWED_SCOPES.contains(token)) {
                throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "허용되지 않은 scope입니다: " + token);
            }
            if (normalized.length() > 0) {
                normalized.append(' ');
            }
            normalized.append(token);
        }
        return normalized.toString();
    }

    public String validateUserSeqNo(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "user_seq_no는 필수입니다.");
        }
        String trimmed = raw.trim();
        if (!USER_SEQ_NO_PATTERN.matcher(trimmed).matches()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "유효하지 않은 user_seq_no 형식입니다.");
        }
        return trimmed;
    }

    public String validateDateYyyyMmDd(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (!DATE_YYYYMMDD_PATTERN.matcher(trimmed).matches()) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, fieldName + "는 yyyyMMdd 형식이어야 합니다.");
        }
        return trimmed;
    }

    public String sanitizeOutputText(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = CONTROL_CHAR_PATTERN.matcher(raw).replaceAll("").trim();
        cleaned = DANGEROUS_URI_PATTERN.matcher(cleaned).replaceAll("blocked:");
        StringBuilder out = new StringBuilder(cleaned.length() + 16);
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#x27;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    // ==================== 2. 암호학 유틸 & 채번 ====================

    public String generateBankTranId(String orgCode) {
        String prefix = (orgCode != null && orgCode.length() == 10) ? orgCode.toUpperCase() : "M202600001";
        StringBuilder sb = new StringBuilder(20);
        sb.append(prefix).append('U');
        for (int i = 0; i < 9; i++) {
            sb.append(ALPHANUM[SECURE_RANDOM.nextInt(ALPHANUM.length)]);
        }
        return sb.toString();
    }

    public String generateOpaqueSecret(String prefix) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 해시 생성 실패", e);
        }
    }

    public boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public String maskFintechNum(String fintechUseNum) {
        if (fintechUseNum == null || fintechUseNum.length() < 6) {
            return "****";
        }
        return fintechUseNum.substring(0, 4) + "****************" + fintechUseNum.substring(fintechUseNum.length() - 4);
    }

    // ==================== 3. Rate Limit & Brute-Force 방어 ====================

    public void checkRateLimit(String bucket, String clientIp, int maxPerMin) {
        evictIfOversized(rateCounters);
        String key = bucket + ":" + safeIp(clientIp);
        long nowEpoch = Instant.now().getEpochSecond();
        WindowCounter counter = rateCounters.compute(key, (k, existing) -> {
            if (existing == null || nowEpoch - existing.windowStartEpoch >= WINDOW_SECONDS) {
                WindowCounter fresh = new WindowCounter(nowEpoch);
                fresh.count.incrementAndGet();
                return fresh;
            }
            existing.count.incrementAndGet();
            return existing;
        });
        if (counter.count.get() > maxPerMin) {
            log.warn("[KFTC-SECURITY] Rate limit 초과 차단: key={}, count={}/{}", key, counter.count.get(), maxPerMin);
            throw new KftcOpenBankingException(KftcResponseCode.RATE_LIMIT_EXCEEDED);
        }
    }

    public void checkNotBlocked(String clientIp) {
        FailureState state = authFailures.get(safeIp(clientIp));
        if (state != null && state.isBlockedNow()) {
            throw new KftcOpenBankingException(
                    KftcResponseCode.RATE_LIMIT_EXCEEDED,
                    "인증 실패가 반복되어 잠시 요청이 제한되었습니다."
            );
        }
    }

    public void recordAuthFailure(String clientIp) {
        evictIfOversized(authFailures);
        String key = safeIp(clientIp);
        authFailures.compute(key, (k, existing) -> {
            long nowEpoch = Instant.now().getEpochSecond();
            if (existing == null || existing.isExpired(nowEpoch)) {
                FailureState fresh = new FailureState(nowEpoch);
                fresh.failures.incrementAndGet();
                return fresh;
            }
            int count = existing.failures.incrementAndGet();
            if (count >= MAX_INVALID_AUTH_ATTEMPTS) {
                existing.blockedUntilEpoch = nowEpoch + BLOCK_SECONDS;
                log.warn("[KFTC-SECURITY] 인증 무차별 대입 의심 IP 차단: ip={}, failures={}", key, count);
            }
            return existing;
        });
    }

    public void clearAuthFailures(String clientIp) {
        authFailures.remove(safeIp(clientIp));
    }

    private static String safeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        String trimmed = ip.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }

    private static <V extends Expirable> void evictIfOversized(ConcurrentHashMap<String, V> map) {
        if (map.size() < MAX_MAP_SIZE) {
            return;
        }
        long nowEpoch = Instant.now().getEpochSecond();
        Iterator<Map.Entry<String, V>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isStale(nowEpoch)) {
                it.remove();
            }
        }
    }

    private interface Expirable {
        boolean isStale(long nowEpoch);
    }

    private static final class WindowCounter implements Expirable {
        final long windowStartEpoch;
        final AtomicInteger count = new AtomicInteger(0);

        WindowCounter(long windowStartEpoch) {
            this.windowStartEpoch = windowStartEpoch;
        }

        @Override
        public boolean isStale(long nowEpoch) {
            return nowEpoch - windowStartEpoch >= WINDOW_SECONDS * 2;
        }
    }

    private static final class FailureState implements Expirable {
        final long firstEpoch;
        final AtomicInteger failures = new AtomicInteger(0);
        volatile long blockedUntilEpoch = 0;

        FailureState(long firstEpoch) {
            this.firstEpoch = firstEpoch;
        }

        boolean isBlockedNow() {
            return blockedUntilEpoch > Instant.now().getEpochSecond();
        }

        boolean isExpired(long nowEpoch) {
            return blockedUntilEpoch <= nowEpoch && (nowEpoch - firstEpoch) >= BLOCK_SECONDS;
        }

        @Override
        public boolean isStale(long nowEpoch) {
            return isExpired(nowEpoch);
        }
    }
}
