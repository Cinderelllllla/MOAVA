package com.moava.openbanking.service;

import com.moava.openbanking.client.OpenBankingProperties;
import com.moava.openbanking.codef.CodefBankCode;
import com.moava.openbanking.codef.CodefDto;
import com.moava.openbanking.codef.CodefException;
import com.moava.openbanking.dto.KftcRecordDto;
import com.moava.openbanking.dto.MockBankDto;
import com.moava.openbanking.mapper.KftcOpenBankingMapper;
import com.moava.openbanking.mapper.MockBankLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * CODEF 실계좌인증 결과를 모의 오픈뱅킹 원장(MOCK_KFTC_ACCOUNT / MOCK_KFTC_TRANSACTION)에
 * 표준 규격 값(24자리 핀테크이용번호, 사용자 일련번호, 20자리 멱등 bank_tran_id 등)으로 변환·적재하는 브리지 서비스.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockBankProvisionService {

    private static final long MAX_MOCK_BALANCE = 1_000_000_000_000L; // 1조원 (NUMBER(15) 안전 범위)
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");
    private static final char[] ALPHANUM = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F]");
    private static final Pattern SCRIPT_OR_TAG_PATTERN = Pattern.compile("[<>\"'`]");
    private static final Pattern DANGEROUS_URI_PATTERN = Pattern.compile("(?i)(javascript|vbscript|data)\\s*:");

    private final MockBankLedgerMapper ledgerMapper;
    private final KftcOpenBankingMapper kftcMapper;
    private final KftcSecurityGuard securityGuard;
    private final OpenBankingProperties openBankingProperties;

    /**
     * MOAVA MEMBER_ID -> KFTC 모의 원장 USER_SEQ_NO 결정적 매핑 (예: memberId=1 -> "1000000001")
     */
    public String deriveUserSeqNo(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw CodefException.unauthorized("OPENBANKING_AUTH_REQUIRED", "유효한 회원 정보가 필요합니다.");
        }
        String raw = memberId <= 999_999_999L
                ? String.format("1%09d", memberId)
                : "1" + memberId;
        return raw.length() > 20 ? raw.substring(0, 20) : raw;
    }

    /**
     * CODEF 실계좌인증 결과(은행·계좌·예금주·잔액·실거래내역)를 모의 은행 원장에 생성·적재한다.
     *
     * @param memberId                   로그인한 MOAVA 회원 ID
     * @param bank                       CODEF/KFTC 은행 매핑
     * @param accountDigits              정규화된 계좌번호 숫자열
     * @param holderName                 CODEF 실조회 예금주 실명
     * @param realBalanceAmt             CODEF 실조회 계좌 잔액 (null 가능)
     * @param codefTransactions          CODEF 실조회 거래내역 원시 목록 (null/empty 가능)
     * @param initialMockBalanceOverride 모의 은행 잔액 수동 지정값 (null이면 실잔액 사용)
     * @param leaderAccountOverride      모임장 수취 계좌 여부 수동 지정값 (null이면 GYE_GROUP.OWNER_ID 자동 판별)
     * @param updateBalanceIfExists      기존 모의 계좌가 이미 존재할 때 잔액을 갱신할지 여부
     */
    @Transactional
    public MockBankDto.ProvisionResult provisionFromCodef(
            Long memberId,
            CodefBankCode bank,
            String accountDigits,
            String holderName,
            Long realBalanceAmt,
            List<CodefDto.TransactionItem> codefTransactions,
            Long initialMockBalanceOverride,
            Boolean leaderAccountOverride,
            boolean updateBalanceIfExists
    ) {
        String userSeqNo = deriveUserSeqNo(memberId);
        String kftcBankCode = bank.getKftcCode();
        String safeBankName = sanitizePlainText(bank.getBankName(), 45, bank.getKftcCode());
        String safeHolderName = sanitizePlainText(holderName, 45, "회원");

        if (initialMockBalanceOverride != null
                && (initialMockBalanceOverride < 0L || initialMockBalanceOverride > MAX_MOCK_BALANCE)) {
            throw CodefException.badRequest(
                    "MOCK_BANK_INVALID_BALANCE",
                    "모의 은행 초기 잔액은 0원 이상 1조원 이하여야 합니다."
            );
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        KftcRecordDto.AccountRecord existing = ledgerMapper.findAccountByBankAndDigits(kftcBankCode, accountDigits);

        if (existing != null && !userSeqNo.equals(existing.getUserSeqNo())) {
            log.warn("[MOCK-BANK-SECURITY] 타 회원 소유 모의 계좌 중복 적재 차단: memberId={}, bankCode={}, last4={}",
                    memberId, kftcBankCode, extractLast4(accountDigits));
            throw CodefException.forbidden(
                    "MOCK_BANK_ACCOUNT_CONFLICT",
                    "이미 다른 회원에게 연결된 모의 은행 계좌입니다."
            );
        }

        String leaderYn = resolveLeaderYn(memberId, existing, leaderAccountOverride);
        long safeRealBalance = realBalanceAmt != null ? Math.max(0L, realBalanceAmt) : 0L;

        boolean created;
        String fintechUseNum;
        long targetBalance;

        if (existing == null) {
            created = true;
            fintechUseNum = allocateUniqueFintechUseNum(kftcBankCode, memberId, accountDigits);
            targetBalance = (initialMockBalanceOverride != null) ? initialMockBalanceOverride : safeRealBalance;

            KftcRecordDto.AccountRecord newAcc = new KftcRecordDto.AccountRecord();
            newAcc.setFintechUseNum(fintechUseNum);
            newAcc.setBankCode(kftcBankCode);
            newAcc.setBankName(safeBankName);
            newAcc.setAccountNo(accountDigits);
            newAcc.setHolderName(safeHolderName);
            newAcc.setUserSeqNo(userSeqNo);
            newAcc.setBalance(targetBalance);
            newAcc.setLeaderYn(leaderYn);
            newAcc.setStatus("ACTIVE");
            newAcc.setCreatedAt(now);
            newAcc.setUpdatedAt(now);
            ledgerMapper.insertMockAccount(newAcc);

            log.info("[MOCK-BANK] CODEF 인증 계좌 신규 적재 완료: memberId={}, finNum={}, bank={}, balance={}, leaderYn={}",
                    memberId, securityGuard.maskFintechNum(fintechUseNum), kftcBankCode, targetBalance, leaderYn);
        } else {
            created = false;
            fintechUseNum = existing.getFintechUseNum();
            if (initialMockBalanceOverride != null) {
                targetBalance = initialMockBalanceOverride;
            } else if (updateBalanceIfExists && realBalanceAmt != null) {
                targetBalance = safeRealBalance;
            } else {
                targetBalance = existing.getBalance() != null ? existing.getBalance() : 0L;
            }

            ledgerMapper.updateMockAccountFromSync(
                    fintechUseNum, safeHolderName, userSeqNo, targetBalance, leaderYn, now
            );

            log.info("[MOCK-BANK] CODEF 인증 계좌 갱신 완료: memberId={}, finNum={}, balance={}, leaderYn={}",
                    memberId, securityGuard.maskFintechNum(fintechUseNum), targetBalance, leaderYn);
        }

        SyncTxCounts txCounts = loadCodefTransactions(
                fintechUseNum, safeBankName, safeHolderName, targetBalance, codefTransactions, now
        );

        String last4 = extractLast4(accountDigits);
        MockBankDto.ProvisionResult result = new MockBankDto.ProvisionResult();
        result.setFintechUseNum(fintechUseNum);
        result.setUserSeqNo(userSeqNo);
        result.setBankCode(kftcBankCode);
        result.setBankName(securityGuard.sanitizeOutputText(safeBankName));
        result.setAccountMasked("****" + last4);
        result.setAccountLast4(last4);
        result.setHolderName(securityGuard.sanitizeOutputText(safeHolderName));
        result.setBalanceAmt(targetBalance);
        result.setRealBalanceAmt(realBalanceAmt);
        result.setLeaderYn(leaderYn);
        result.setLeaderAccount("Y".equals(leaderYn));
        result.setCreated(created);
        result.setSyncedTransactionCount(txCounts.syncedCount);
        result.setSkippedDuplicateCount(txCounts.skippedCount);
        result.setOpeningSnapshotCreated(txCounts.openingSnapshotCreated);
        result.setStatus("ACTIVE");
        result.setSyncedAt(now);
        return result;
    }

    private SyncTxCounts loadCodefTransactions(
            String fintechUseNum,
            String bankName,
            String holderName,
            long targetBalance,
            List<CodefDto.TransactionItem> codefTransactions,
            LocalDateTime now
    ) {
        int synced = 0;
        int skipped = 0;
        boolean openingCreated = false;

        if (codefTransactions != null && !codefTransactions.isEmpty()) {
            Map<String, Integer> sigCounter = new HashMap<>();
            for (CodefDto.TransactionItem item : codefTransactions) {
                if (item == null || item.getTranAmt() == null || item.getTranAmt() <= 0L) {
                    continue;
                }
                String inoutType = "입금".equals(item.getInoutType()) ? "입금" : "출금";
                long tranAmt = item.getTranAmt();
                long afterBal = item.getAfterBalanceAmt() != null ? Math.max(0L, item.getAfterBalanceAmt()) : 0L;
                String tranDate = safeStr(item.getTranDate());
                String tranTime = safeStr(item.getTranTime());

                String printContent = resolvePrintContent(item);
                String counterparty = resolveCounterparty(item, holderName);
                String branchName = resolveBranchName(item, bankName);
                LocalDateTime tranAt = parseCodefTranDateTime(tranDate, tranTime, now);

                String baseSig = fintechUseNum + "|" + tranDate + "|" + tranTime + "|"
                        + inoutType + "|" + tranAmt + "|" + afterBal + "|" + printContent;
                int dupIdx = sigCounter.merge(baseSig, 1, Integer::sum);
                String bankTranId = generateDeterministicBankTranId(
                        openBankingProperties.getOrgCode(), baseSig + "#" + dupIdx
                );

                if (kftcMapper.countByBankTranId(bankTranId) > 0) {
                    skipped++;
                    continue;
                }

                KftcRecordDto.TransactionRecord tx = new KftcRecordDto.TransactionRecord();
                tx.setBankTranId(bankTranId);
                tx.setFintechUseNum(fintechUseNum);
                tx.setInoutType(inoutType);
                tx.setTranAmt(tranAmt);
                tx.setAfterBalanceAmt(afterBal);
                tx.setPrintContent(printContent);
                tx.setCounterpartyName(counterparty);
                tx.setCounterpartyFinNum(null);
                tx.setBranchName(branchName);
                tx.setTranAt(tranAt);

                try {
                    kftcMapper.insertTransaction(tx);
                    synced++;
                } catch (DuplicateKeyException e) {
                    skipped++;
                }
            }
        }

        // 실거래내역이 없거나 비어 있는 계좌라면, 최초 1회 초기 잔액 스냅샷을 원장에 적재해 프론트 원장이 비지 않게 한다
        if (synced == 0 && targetBalance > 0L
                && ledgerMapper.countTransactionsByFintechUseNum(fintechUseNum) == 0) {
            String openingTranId = generateDeterministicBankTranId(
                    openBankingProperties.getOrgCode(), "OPENING_SNAPSHOT|" + fintechUseNum
            );
            if (kftcMapper.countByBankTranId(openingTranId) == 0) {
                KftcRecordDto.TransactionRecord openingTx = new KftcRecordDto.TransactionRecord();
                openingTx.setBankTranId(openingTranId);
                openingTx.setFintechUseNum(fintechUseNum);
                openingTx.setInoutType("입금");
                openingTx.setTranAmt(targetBalance);
                openingTx.setAfterBalanceAmt(targetBalance);
                openingTx.setPrintContent("실계좌 연동 초기잔액");
                openingTx.setCounterpartyName(sanitizePlainText(holderName, 45, "본인계좌"));
                openingTx.setCounterpartyFinNum(null);
                openingTx.setBranchName(sanitizePlainText(bankName, 45, "모아바모의점"));
                openingTx.setTranAt(now);
                try {
                    kftcMapper.insertTransaction(openingTx);
                    synced++;
                    openingCreated = true;
                } catch (DuplicateKeyException ignored) {
                    skipped++;
                }
            }
        }

        return new SyncTxCounts(synced, skipped, openingCreated);
    }

    private String resolveLeaderYn(
            Long memberId,
            KftcRecordDto.AccountRecord existing,
            Boolean leaderAccountOverride
    ) {
        if (leaderAccountOverride != null) {
            return leaderAccountOverride ? "Y" : "N";
        }
        if (existing != null && "Y".equals(existing.getLeaderYn())) {
            return "Y";
        }
        int ownedGroups = ledgerMapper.countOwnedActiveGroups(memberId);
        return ownedGroups > 0 ? "Y" : "N";
    }

    /**
     * 24자리 숫자 핀테크이용번호 생성 (199 + 3자리 은행코드 + 6자리 회원번호 + 12자리 해시숫자)
     */
    String allocateUniqueFintechUseNum(String kftcBankCode, Long memberId, String accountDigits) {
        String bank3 = normalizeDigitsFixed(kftcBankCode, 3);
        String member6 = String.format("%06d", Math.abs(memberId % 1_000_000L));

        for (int attempt = 0; attempt < 16; attempt++) {
            byte[] hash = sha256(bank3 + ":" + memberId + ":" + accountDigits + ":" + attempt);
            BigInteger positive = new BigInteger(1, hash);
            String suffix12 = String.format("%012d", positive.mod(BigInteger.valueOf(1_000_000_000_000L)).longValue());
            String candidate = "199" + bank3 + member6 + suffix12;
            if (ledgerMapper.countByFintechUseNum(candidate) == 0) {
                return candidate;
            }
        }
        throw CodefException.serviceUnavailable("MOCK_BANK_FINTECH_ALLOC_FAILED", "핀테크이용번호 채번에 실패했습니다.");
    }

    /**
     * 20자리 결정적 KFTC 은행거래고유번호 생성 (이용기관코드 10 + 'U' + 영숫자 9)
     */
    public String generateDeterministicBankTranId(String orgCode, String seed) {
        String prefix = (orgCode != null && orgCode.length() == 10)
                ? orgCode.toUpperCase()
                : "M202600001";
        byte[] hash = sha256(seed);
        StringBuilder sb = new StringBuilder(20);
        sb.append(prefix).append('U');
        for (int i = 0; i < 9; i++) {
            int idx = (hash[i] & 0xFF) % ALPHANUM.length;
            sb.append(ALPHANUM[idx]);
        }
        return sb.toString();
    }

    /**
     * Oracle VARCHAR2(BYTE) 초과(ORA-12899) 및 스크립트 문자를 방지하는 평문 정제 유틸
     */
    public static String sanitizePlainText(String raw, int maxUtf8Bytes, String fallback) {
        if (raw == null || raw.isBlank()) {
            return truncateUtf8Bytes(fallback, maxUtf8Bytes);
        }
        String cleaned = CONTROL_CHAR_PATTERN.matcher(raw).replaceAll(" ");
        cleaned = DANGEROUS_URI_PATTERN.matcher(cleaned).replaceAll("blocked:");
        cleaned = SCRIPT_OR_TAG_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return truncateUtf8Bytes(fallback, maxUtf8Bytes);
        }
        return truncateUtf8Bytes(cleaned, maxUtf8Bytes);
    }

    private static String truncateUtf8Bytes(String text, int maxBytes) {
        if (text == null) {
            return "";
        }
        byte[] all = text.getBytes(StandardCharsets.UTF_8);
        if (all.length <= maxBytes) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            int byteLen = ch.getBytes(StandardCharsets.UTF_8).length;
            if (used + byteLen > maxBytes) {
                break;
            }
            sb.append(ch);
            used += byteLen;
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    private static String resolvePrintContent(CodefDto.TransactionItem item) {
        String d3 = safeStr(item.getDesc3());
        String d1 = safeStr(item.getDesc1());
        String d2 = safeStr(item.getDesc2());

        String chosen;
        if (!d3.isEmpty() && !d1.isEmpty() && !d3.equals(d1)) {
            chosen = d3 + " (" + d1 + ")";
        } else if (!d3.isEmpty()) {
            chosen = d3;
        } else if (!d1.isEmpty()) {
            chosen = d1;
        } else if (!d2.isEmpty()) {
            chosen = d2;
        } else {
            chosen = "CODEF 실계좌 거래";
        }
        return sanitizePlainText(chosen, 90, "CODEF 실계좌 거래");
    }

    private static String resolveCounterparty(CodefDto.TransactionItem item, String fallbackHolder) {
        String candidate = firstNonBlank(item.getDesc3(), item.getDesc2(), item.getDesc1(), fallbackHolder);
        return sanitizePlainText(candidate, 45, "실계좌거래");
    }

    private static String resolveBranchName(CodefDto.TransactionItem item, String bankName) {
        String candidate = firstNonBlank(item.getDesc4(), bankName, "모아바모의점");
        return sanitizePlainText(candidate, 45, "모아바모의점");
    }

    private static LocalDateTime parseCodefTranDateTime(String tranDate, String tranTime, LocalDateTime fallback) {
        if (tranDate == null || tranDate.length() != 8) {
            return fallback;
        }
        try {
            LocalDate date = LocalDate.parse(tranDate, YYYYMMDD);
            LocalTime time = LocalTime.MIDNIGHT;
            if (tranTime != null) {
                String digits = tranTime.replaceAll("[^0-9]", "");
                if (digits.length() >= 6) {
                    time = LocalTime.parse(digits.substring(0, 6), HHMMSS);
                } else if (digits.length() == 4) {
                    time = LocalTime.parse(digits + "00", HHMMSS);
                }
            }
            return LocalDateTime.of(date, time);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private static String normalizeDigitsFixed(String raw, int len) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.length() > len) {
            return digits.substring(digits.length() - len);
        }
        return "0".repeat(len - digits.length()) + digits;
    }

    private static String extractLast4(String accountDigits) {
        if (accountDigits == null || accountDigits.length() <= 4) {
            return accountDigits == null ? "0000" : accountDigits;
        }
        return accountDigits.substring(accountDigits.length() - 4);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String safeStr(String s) {
        return s == null ? "" : s.trim();
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 해시 계산 실패", e);
        }
    }

    private record SyncTxCounts(int syncedCount, int skippedCount, boolean openingSnapshotCreated) {
    }
}
