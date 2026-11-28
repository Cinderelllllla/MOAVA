package com.moava.openbanking.service;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.openbanking.client.OpenBankingProperties;
import com.moava.openbanking.codef.CodefAccountMapper;
import com.moava.openbanking.codef.CodefBankCode;
import com.moava.openbanking.codef.CodefDto;
import com.moava.openbanking.codef.CodefException;
import com.moava.openbanking.dto.KftcOAuthDto;
import com.moava.openbanking.dto.KftcRecordDto;
import com.moava.openbanking.dto.MockBankDto;
import com.moava.openbanking.mapper.MockBankLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 간편 은행 계좌 연결 + 가상은행 1원 인증(4자리 확인) 서비스.
 *
 * <p>기존 CODEF 실계좌인증(공동인증서/인터넷뱅킹 ID·PW) 경로를 보존한 채,
 * 사용자가 은행명·계좌번호·소유주·전화번호만 입력하면 선택한 은행의 가상 로그인/1원 인증 화면으로 이동해
 * 4자리 인증번호 확인 및 오픈뱅킹 동의 후 즉시 계좌 연동(KFTC OAuth + 모의은행 원장 + F19 인증 이력)을 완료한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockBankQuickConnectService {

    public static final String QUICK_OAUTH_CONN_PREFIX = "QUICK_OAUTH:";

    private static final long DEFAULT_INITIAL_MOCK_BALANCE = 1_000_000L;
    private static final long MAX_MOCK_BALANCE = 1_000_000_000_000L;
    private static final long SESSION_TTL_SECONDS = 300L; // 5분
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int MAX_SESSIONS = 5_000;
    private static final String QUICK_OAUTH_SCOPE = "login inquiry transfer";

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CodefAccountMapper codefAccountMapper;
    private final MockBankLedgerMapper ledgerMapper;
    private final CryptoKeyService cryptoKeyService;
    private final KftcOAuthService kftcOAuthService;
    private final MockBankProvisionService provisionService;
    private final KftcSecurityGuard securityGuard;
    private final OpenBankingProperties openBankingProperties;

    private final ConcurrentHashMap<String, QuickSession> sessions = new ConcurrentHashMap<>();

    /**
     * 1단계: 은행명·계좌번호·소유주·전화번호 검증 후 가상은행 1원 인증 세션 발급
     */
    public MockBankDto.QuickConnectSessionResponse startQuickConnect(
            Long memberId,
            MockBankDto.QuickConnectStartRequest req,
            String clientIp
    ) {
        String memberName = requireActiveMemberName(memberId);
        securityGuard.checkRateLimit("MOCK_QUICK_START", memberId + ":" + safeIp(clientIp), 20);
        if (req == null) {
            throw CodefException.badRequest("QUICK_CONNECT_EMPTY_REQUEST", "계좌 연결 요청 본문이 비어 있습니다.");
        }

        CodefBankCode bank = CodefBankCode.resolve(req.getOrganization());
        String accountDigits = validateAndNormalizeAccountDigits(req.getAccountNo());
        String holderName = validateHolderName(req.getHolderName(), memberName);
        String phoneDigits = validatePhoneDigits(req.getPhone());

        Long initialBalanceOverride = req.getInitialMockBalance();
        if (initialBalanceOverride != null
                && (initialBalanceOverride < 0L || initialBalanceOverride > MAX_MOCK_BALANCE)) {
            throw CodefException.badRequest(
                    "MOCK_BANK_INVALID_BALANCE",
                    "초기 모의 잔액은 0원 이상 1조원 이하여야 합니다."
            );
        }

        // 타 회원 소유 모의 계좌 충돌 사전 차단
        String userSeqNo = provisionService.deriveUserSeqNo(memberId);
        KftcRecordDto.AccountRecord existing = ledgerMapper.findAccountByBankAndDigits(bank.getKftcCode(), accountDigits);
        if (existing != null && !userSeqNo.equals(existing.getUserSeqNo())) {
            throw CodefException.forbidden(
                    "MOCK_BANK_ACCOUNT_CONFLICT",
                    "이미 다른 회원에게 연결된 계좌번호입니다."
            );
        }

        cleanupExpiredSessions();
        String sessionToken = securityGuard.generateOpaqueSecret("qb_");
        String authCode = generate4DigitCode();
        Instant expiresAt = Instant.now().plusSeconds(SESSION_TTL_SECONDS);

        QuickSession session = new QuickSession(
                sessionToken,
                memberId,
                bank,
                accountDigits,
                holderName,
                phoneDigits,
                initialBalanceOverride,
                req.getLeaderAccount(),
                authCode,
                expiresAt
        );
        sessions.put(sessionToken, session);

        log.info("[MOCK-QUICK] 가상은행 1원 인증 세션 발급: memberId={}, org={}, account=****{}",
                memberId, bank.getCodefOrgCode(), extractLast4(accountDigits));

        return toSessionResponse(session);
    }

    /**
     * 2단계: 가상은행 로그인/인증 화면 진입 시 세션 정보 조회
     */
    public MockBankDto.QuickConnectSessionResponse getSession(Long memberId, String sessionToken) {
        requireActiveMemberName(memberId);
        QuickSession session = requireOwnedActiveSession(memberId, sessionToken);
        return toSessionResponse(session);
    }

    /**
     * 2-1단계: 1원 인증번호 4자리 재발송
     */
    public MockBankDto.QuickConnectSessionResponse resendAuthCode(
            Long memberId,
            String sessionToken,
            String clientIp
    ) {
        requireActiveMemberName(memberId);
        securityGuard.checkRateLimit("MOCK_QUICK_RESEND", memberId + ":" + safeIp(clientIp), 10);
        QuickSession session = requireOwnedActiveSession(memberId, sessionToken);

        synchronized (session) {
            if (session.completed) {
                throw CodefException.badRequest("QUICK_CONNECT_ALREADY_USED", "이미 인증이 완료된 요청입니다.");
            }
            if (session.verifying) {
                throw CodefException.badRequest("QUICK_CONNECT_IN_PROGRESS", "인증 처리가 진행 중입니다. 잠시만 기다려 주세요.");
            }
            session.authCode = generate4DigitCode();
            session.attempts = 0;
            session.expiresAt = Instant.now().plusSeconds(SESSION_TTL_SECONDS);
        }

        log.info("[MOCK-QUICK] 가상은행 1원 인증번호 재발송: memberId={}, org={}",
                memberId, session.bank.getCodefOrgCode());
        return toSessionResponse(session);
    }

    /**
     * 3단계: 1원 인증번호 4자리 검증 + 오픈뱅킹 동의 확인 후
     * KFTC OAuth 토큰 발급 · 모의은행 계좌 적재 · CODEF_CONNECTION 인증 이력(F19 호환)을 일괄 완료
     */
    @Transactional
    public MockBankDto.QuickConnectVerifyResponse verifyAndComplete(
            Long memberId,
            String sessionToken,
            MockBankDto.QuickConnectVerifyRequest req,
            String clientIp
    ) {
        requireActiveMemberName(memberId);
        securityGuard.checkRateLimit("MOCK_QUICK_VERIFY", memberId + ":" + safeIp(clientIp), 20);
        if (req == null) {
            throw CodefException.badRequest("QUICK_CONNECT_EMPTY_REQUEST", "인증 요청 본문이 비어 있습니다.");
        }
        if (!Boolean.TRUE.equals(req.getAgreed())) {
            throw CodefException.badRequest("QUICK_CONNECT_AGREEMENT_REQUIRED", "오픈뱅킹 계좌조회 및 출금이체 정보제공 동의가 필요합니다.");
        }

        String submittedCode = req.getAuthCode() == null ? "" : req.getAuthCode().trim();
        if (!submittedCode.matches("^[0-9]{4}$")) {
            throw CodefException.badRequest("QUICK_CONNECT_INVALID_CODE", "1원 인증번호는 4자리 숫자로 입력해 주세요.");
        }

        String trimmedToken = sessionToken == null ? "" : sessionToken.trim();
        QuickSession session = requireOwnedActiveSession(memberId, trimmedToken);
        String verifiedAuthCode;
        synchronized (session) {
            if (session.completed) {
                throw CodefException.badRequest("QUICK_CONNECT_ALREADY_USED", "이미 인증이 완료된 세션입니다.");
            }
            if (session.verifying) {
                throw CodefException.badRequest("QUICK_CONNECT_IN_PROGRESS", "인증 처리가 진행 중입니다. 잠시만 기다려 주세요.");
            }
            if (Instant.now().isAfter(session.expiresAt)) {
                sessions.remove(trimmedToken);
                throw CodefException.badRequest("QUICK_CONNECT_EXPIRED", "인증 유효시간(5분)이 만료되었습니다. 처음부터 다시 시도해 주세요.");
            }
            if (!constantTimeEquals(session.authCode, submittedCode)) {
                session.attempts++;
                if (session.attempts >= MAX_VERIFY_ATTEMPTS) {
                    sessions.remove(trimmedToken);
                    throw CodefException.badRequest(
                            "QUICK_CONNECT_MAX_ATTEMPTS",
                            "인증번호를 5회 이상 잘못 입력하여 세션이 만료되었습니다. 처음부터 다시 시도해 주세요."
                    );
                }
                int remaining = MAX_VERIFY_ATTEMPTS - session.attempts;
                throw CodefException.badRequest(
                        "QUICK_CONNECT_CODE_MISMATCH",
                        "인증번호가 일치하지 않습니다. (남은 시도 횟수: " + remaining + "회)"
                );
            }
            session.verifying = true;
            verifiedAuthCode = session.authCode;
        }

        try {
            String ip = safeIp(clientIp);
            String userSeqNo = provisionService.deriveUserSeqNo(memberId);

            // 1) KFTC 모의 OAuth 인가코드 발급 -> Access Token 교환 (실제 OAuth 원장 기록)
            String redirectUri = openBankingProperties.getQuickOAuthRedirectUri();
            KftcOAuthDto.AuthorizeRequest authzReq = new KftcOAuthDto.AuthorizeRequest();
            authzReq.setClientId(openBankingProperties.getClientId());
            authzReq.setRedirectUri(redirectUri);
            authzReq.setScope(QUICK_OAUTH_SCOPE);
            authzReq.setUserSeqNo(userSeqNo);
            authzReq.setState(trimmedToken.substring(0, Math.min(16, trimmedToken.length())));
            KftcOAuthDto.AuthorizeResponse authzRes = kftcOAuthService.issueAuthorizationCode(authzReq, ip);

            KftcOAuthDto.TokenRequest tokenReq = new KftcOAuthDto.TokenRequest();
            tokenReq.setGrantType("authorization_code");
            tokenReq.setCode(authzRes.getCode());
            tokenReq.setClientId(openBankingProperties.getClientId());
            tokenReq.setClientSecret(openBankingProperties.getClientSecret());
            tokenReq.setRedirectUri(redirectUri);
            KftcOAuthDto.TokenResponse tokenRes = kftcOAuthService.issueToken(tokenReq, ip);

            // 2) 모의은행 원장(MOCK_KFTC_ACCOUNT + 1원 인증 입금 및 초기 잔액 거래내역) 적재
            //    - 신규 계좌: initialBalanceOverride 미지정 시 기본 100만 원 적용 (0원 지정 시 1원 입금 행 생략)
            //    - 기존 계좌 재연결: initialBalanceOverride 미지정 시 기존 잔액 + 1원 인증 입금 반영으로 잔액·원장 정합성 유지
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            KftcRecordDto.AccountRecord existingAccount =
                    ledgerMapper.findAccountByBankAndDigits(session.bank.getKftcCode(), session.accountDigits);
            long newAccountDefaultBalance = session.initialBalanceOverride != null
                    ? session.initialBalanceOverride
                    : DEFAULT_INITIAL_MOCK_BALANCE;
            boolean isReconnectWithoutOverride = existingAccount != null && session.initialBalanceOverride == null;
            long existingBalance = (existingAccount != null && existingAccount.getBalance() != null)
                    ? existingAccount.getBalance()
                    : 0L;
            boolean canCreditOneWonOnReconnect = isReconnectWithoutOverride && existingBalance < MAX_MOCK_BALANCE;
            long targetBalanceForProvision = isReconnectWithoutOverride
                    ? (canCreditOneWonOnReconnect ? existingBalance + 1L : existingBalance)
                    : newAccountDefaultBalance;
            boolean updateBalanceIfExists = canCreditOneWonOnReconnect;

            List<CodefDto.TransactionItem> seedTxs = buildInitialSeedTransactions(
                    session.bank.getBankName(),
                    targetBalanceForProvision,
                    "모아바" + verifiedAuthCode,
                    now,
                    existingAccount == null,
                    !isReconnectWithoutOverride || canCreditOneWonOnReconnect
            );
            MockBankDto.ProvisionResult mockAccount = provisionService.provisionFromCodef(
                    memberId,
                    session.bank,
                    session.accountDigits,
                    session.holderName,
                    targetBalanceForProvision,
                    seedTxs,
                    session.initialBalanceOverride,
                    session.leaderAccount,
                    updateBalanceIfExists
            );

            // 3) CODEF_CONNECTION 인증 이력 기록 (기존 CODEF 실연결이 있으면 connectedId 보존, 키 로테이션 시 재암호화)
            int keyVer = cryptoKeyService.getCurrentKeyVersion();
            CodefDto.ConnectionRecord existingConn = codefAccountMapper.findConnection(memberId, session.bank.getCodefOrgCode());
            if (existingConn == null || existingConn.getConnectedIdEnc() == null || existingConn.getConnectedIdEnc().isBlank()) {
                CodefDto.ConnectionRecord connRec = new CodefDto.ConnectionRecord();
                connRec.setMemberId(memberId);
                connRec.setOrganization(session.bank.getCodefOrgCode());
                connRec.setConnectedIdEnc(cryptoKeyService.encrypt(
                        QUICK_OAUTH_CONN_PREFIX + session.bank.getCodefOrgCode(), keyVer
                ));
                connRec.setKeyVer(keyVer);
                connRec.setCreatedAt(now);
                connRec.setUpdatedAt(now);
                codefAccountMapper.upsertConnection(connRec);
            } else if (existingConn.getKeyVer() != null && existingConn.getKeyVer() != keyVer) {
                String plainConnId = cryptoKeyService.decrypt(existingConn.getConnectedIdEnc(), existingConn.getKeyVer());
                String reEncryptedConnId = cryptoKeyService.encrypt(plainConnId, keyVer);
                codefAccountMapper.updateConnectedIdEncryption(
                        memberId,
                        session.bank.getCodefOrgCode(),
                        reEncryptedConnId,
                        keyVer,
                        now
                );
            }
            String accountEnc = cryptoKeyService.encrypt(session.accountDigits, keyVer);
            String last4 = extractLast4(session.accountDigits);
            codefAccountMapper.markAccountVerified(
                    memberId,
                    session.bank.getCodefOrgCode(),
                    accountEnc,
                    last4,
                    MockBankProvisionService.sanitizePlainText(session.holderName, 45, "회원"),
                    keyVer,
                    now
            );

            synchronized (session) {
                session.completed = true;
                session.verifying = false;
                sessions.remove(trimmedToken);
            }

            log.info("[MOCK-QUICK-AUDIT] 간편 은행 1원 인증 및 계좌 연동 완료: memberId={}, org={}, account=****{}, finNum={}",
                    memberId,
                    session.bank.getCodefOrgCode(),
                    last4,
                    securityGuard.maskFintechNum(mockAccount.getFintechUseNum()));

            MockBankDto.QuickConnectVerifyResponse res = new MockBankDto.QuickConnectVerifyResponse();
            res.setVerified(true);
            res.setOrganization(session.bank.getCodefOrgCode());
            res.setBankName(session.bank.getBankName());
            res.setAccountMasked("****" + last4);
            res.setHolderName(securityGuard.sanitizeOutputText(session.holderName));
            res.setOauthScope(tokenRes != null ? tokenRes.getScope() : QUICK_OAUTH_SCOPE);
            res.setMockAccount(mockAccount);
            res.setMessage(session.bank.getBankName() + " 본인 계좌 인증 및 오픈뱅킹 연결이 완료되었습니다.");
            return res;
        } catch (RuntimeException e) {
            synchronized (session) {
                session.verifying = false;
            }
            throw e;
        }
    }

    // ==================== 내부 헬퍼 ====================

    private List<CodefDto.TransactionItem> buildInitialSeedTransactions(
            String bankName,
            long afterBalance,
            String depositMemo,
            LocalDateTime now,
            boolean includeOpeningBalance,
            boolean includeOneWonTx
    ) {
        if (!includeOneWonTx || afterBalance <= 0L) {
            return List.of();
        }

        String ymd = now.toLocalDate().format(YYYYMMDD);
        String hms = now.toLocalTime().format(HHMMSS);

        CodefDto.TransactionItem oneWonTx = new CodefDto.TransactionItem();
        oneWonTx.setTranDate(ymd);
        oneWonTx.setTranTime(hms);
        oneWonTx.setInoutType("입금");
        oneWonTx.setTranAmt(1L);
        oneWonTx.setAfterBalanceAmt(afterBalance);
        oneWonTx.setDesc1("1원 계좌인증");
        oneWonTx.setDesc3(depositMemo);
        oneWonTx.setDesc4(bankName);

        if (!includeOpeningBalance || afterBalance <= 1L) {
            return List.of(oneWonTx);
        }

        LocalDateTime openingTime = now.minusSeconds(1);
        CodefDto.TransactionItem openingTx = new CodefDto.TransactionItem();
        openingTx.setTranDate(openingTime.toLocalDate().format(YYYYMMDD));
        openingTx.setTranTime(openingTime.toLocalTime().format(HHMMSS));
        openingTx.setInoutType("입금");
        openingTx.setTranAmt(afterBalance - 1L);
        openingTx.setAfterBalanceAmt(afterBalance - 1L);
        openingTx.setDesc1("오픈뱅킹 연결 초기잔액");
        openingTx.setDesc3("모아바 가상은행");
        openingTx.setDesc4(bankName);

        return List.of(oneWonTx, openingTx);
    }

    private QuickSession requireOwnedActiveSession(Long memberId, String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw CodefException.notFound("QUICK_CONNECT_SESSION_NOT_FOUND", "유효한 인증 세션을 찾을 수 없습니다.");
        }
        QuickSession session = sessions.get(sessionToken.trim());
        if (session == null || !memberId.equals(session.memberId)) {
            throw CodefException.notFound("QUICK_CONNECT_SESSION_NOT_FOUND", "만료되었거나 존재하지 않는 인증 세션입니다.");
        }
        if (Instant.now().isAfter(session.expiresAt)) {
            sessions.remove(sessionToken.trim());
            throw CodefException.badRequest("QUICK_CONNECT_EXPIRED", "인증 유효시간(5분)이 만료되었습니다. 처음부터 다시 시도해 주세요.");
        }
        return session;
    }

    private MockBankDto.QuickConnectSessionResponse toSessionResponse(QuickSession s) {
        long remaining = Math.max(0L, Duration.between(Instant.now(), s.expiresAt).getSeconds());
        MockBankDto.QuickConnectSessionResponse res = new MockBankDto.QuickConnectSessionResponse();
        res.setSessionToken(s.sessionToken);
        res.setOrganization(s.bank.getCodefOrgCode());
        res.setKftcCode(s.bank.getKftcCode());
        res.setBankName(s.bank.getBankName());
        res.setAccountMasked("****" + extractLast4(s.accountDigits));
        res.setHolderName(securityGuard.sanitizeOutputText(s.holderName));
        res.setPhoneMasked(maskPhone(s.phoneDigits));
        res.setDepositMemo("모아바" + s.authCode);
        res.setAuthCodeHint(s.authCode);
        res.setExpiresInSeconds(remaining);
        res.setBankAuthUrl("/openbanking/bank-auth?session=" + s.sessionToken);
        return res;
    }

    private String requireActiveMemberName(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw CodefException.unauthorized("OPENBANKING_AUTH_REQUIRED", "로그인이 필요합니다.");
        }
        String memberName = codefAccountMapper.findActiveMemberName(memberId);
        if (memberName == null || memberName.isBlank()) {
            throw CodefException.forbidden("OPENBANKING_MEMBER_NOT_FOUND", "활성 회원 정보를 찾을 수 없습니다.");
        }
        return memberName;
    }

    private String validateAndNormalizeAccountDigits(String rawAccountNo) {
        if (rawAccountNo == null || rawAccountNo.isBlank()) {
            throw CodefException.badRequest("QUICK_CONNECT_INVALID_ACCOUNT", "계좌번호를 입력해 주세요.");
        }
        String digits = rawAccountNo.replaceAll("[^0-9]", "");
        if (digits.length() < 8 || digits.length() > 20) {
            throw CodefException.badRequest("QUICK_CONNECT_INVALID_ACCOUNT", "계좌번호는 숫자 8~20자리여야 합니다.");
        }
        return digits;
    }

    private String validateHolderName(String rawHolder, String memberName) {
        String validHolder = securityGuard.validateHolderName(rawHolder);
        if (!normalizeName(validHolder).equals(normalizeName(memberName))) {
            throw CodefException.forbidden(
                    "QUICK_CONNECT_HOLDER_MISMATCH",
                    "입력하신 소유주 이름이 로그인 회원 실명과 일치하지 않습니다."
            );
        }
        return validHolder;
    }

    private String validatePhoneDigits(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw CodefException.badRequest("QUICK_CONNECT_INVALID_PHONE", "전화번호를 입력해 주세요.");
        }
        String digits = rawPhone.replaceAll("[^0-9]", "");
        if (digits.length() < 10 || digits.length() > 11 || !digits.startsWith("01")) {
            throw CodefException.badRequest("QUICK_CONNECT_INVALID_PHONE", "올바른 휴대폰 번호(예: 010-1234-5678)를 입력해 주세요.");
        }
        return digits;
    }

    private void cleanupExpiredSessions() {
        Instant now = Instant.now();
        if (sessions.size() < MAX_SESSIONS) {
            sessions.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt));
            return;
        }
        Iterator<Map.Entry<String, QuickSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, QuickSession> entry = it.next();
            if (now.isAfter(entry.getValue().expiresAt) || sessions.size() > MAX_SESSIONS) {
                it.remove();
            }
        }
    }

    private static String generate4DigitCode() {
        return String.format("%04d", SECURE_RANDOM.nextInt(10_000));
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String extractLast4(String digits) {
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }

    private static String maskPhone(String digits) {
        if (digits == null || digits.length() < 10) {
            return "***-****-****";
        }
        String prefix = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return prefix + "-****-" + tail;
    }

    private static String safeIp(String ip) {
        return (ip == null || ip.isBlank()) ? "unknown" : ip;
    }

    private static final class QuickSession {
        final String sessionToken;
        final Long memberId;
        final CodefBankCode bank;
        final String accountDigits;
        final String holderName;
        final String phoneDigits;
        final Long initialBalanceOverride;
        final Boolean leaderAccount;
        volatile String authCode;
        volatile Instant expiresAt;
        int attempts;
        boolean verifying;
        boolean completed;

        QuickSession(
                String sessionToken,
                Long memberId,
                CodefBankCode bank,
                String accountDigits,
                String holderName,
                String phoneDigits,
                Long initialBalanceOverride,
                Boolean leaderAccount,
                String authCode,
                Instant expiresAt
        ) {
            this.sessionToken = sessionToken;
            this.memberId = memberId;
            this.bank = bank;
            this.accountDigits = accountDigits;
            this.holderName = holderName;
            this.phoneDigits = phoneDigits;
            this.initialBalanceOverride = initialBalanceOverride;
            this.leaderAccount = leaderAccount;
            this.authCode = authCode;
            this.expiresAt = expiresAt;
        }
    }
}
