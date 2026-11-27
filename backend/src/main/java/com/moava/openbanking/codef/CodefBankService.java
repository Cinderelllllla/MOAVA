package com.moava.openbanking.codef;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.openbanking.dto.MockBankDto;
import com.moava.openbanking.service.KftcSecurityGuard;
import com.moava.openbanking.service.MockBankProvisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * CODEF 개인 수시입출 거래내역 조회 기반 실계좌인증 서비스:
 * 1) /v1/account/create — 공동인증서(loginType=0) 또는 은행 ID/PW(loginType=1)로 connectedId 발급·암호화 저장
 * 2) /v1/kr/bank/p/account/account-list — 보유 계좌 목록 조회
 * 3) /v1/kr/bank/p/account/transaction-list (inquiryType=1) — 실제 예금주(resAccountHolder) ↔ MEMBER.NAME 대조로 실계좌 소유 인증
 * 4) 인증 완료 시 MockBankProvisionService와 연계해 모의 은행(MOCK_KFTC_ACCOUNT / MOCK_KFTC_TRANSACTION) 원장 자동 적재
 */
@Slf4j
@Service
public class CodefBankService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern DIGITS_ONLY = Pattern.compile("^[0-9]{8,20}$");
    private static final Pattern BIRTHDATE_6 = Pattern.compile("^[0-9]{6}$");
    private static final long MAX_QUERY_DAYS = 366L;

    private final CodefApiClient codefApiClient;
    private final CodefProperties properties;
    private final CodefAccountMapper mapper;
    private final CryptoKeyService cryptoKeyService;
    private final KftcSecurityGuard securityGuard;
    private final MockBankProvisionService mockBankProvisionService;

    @Autowired
    public CodefBankService(
            CodefApiClient codefApiClient,
            CodefProperties properties,
            CodefAccountMapper mapper,
            CryptoKeyService cryptoKeyService,
            KftcSecurityGuard securityGuard,
            MockBankProvisionService mockBankProvisionService
    ) {
        this.codefApiClient = codefApiClient;
        this.properties = properties;
        this.mapper = mapper;
        this.cryptoKeyService = cryptoKeyService;
        this.securityGuard = securityGuard;
        this.mockBankProvisionService = mockBankProvisionService;
    }

    /** 기존 단위 테스트 호환용 생성자 */
    public CodefBankService(
            CodefApiClient codefApiClient,
            CodefProperties properties,
            CodefAccountMapper mapper,
            CryptoKeyService cryptoKeyService,
            KftcSecurityGuard securityGuard
    ) {
        this(codefApiClient, properties, mapper, cryptoKeyService, securityGuard, null);
    }

    public List<CodefDto.SupportedBank> getSupportedBanks() {
        return Arrays.stream(CodefBankCode.values())
                .map(b -> new CodefDto.SupportedBank(b.getCodefOrgCode(), b.getKftcCode(), b.getBankName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CodefDto.ConnectionSummary> getMyConnections(Long memberId) {
        requireMemberId(memberId);
        List<CodefDto.ConnectionRecord> records = mapper.findConnectionsByMember(memberId);
        List<CodefDto.ConnectionSummary> result = new ArrayList<>(records.size());
        for (CodefDto.ConnectionRecord r : records) {
            CodefDto.ConnectionSummary s = new CodefDto.ConnectionSummary();
            s.setOrganization(r.getOrganization());
            s.setBankName(safeBankName(r.getOrganization()));
            boolean verified = "Y".equals(r.getVerifiedYn());
            s.setVerified(verified);
            if (verified) {
                s.setVerifiedAccountMasked(r.getVerifiedAccountLast4() != null ? "****" + r.getVerifiedAccountLast4() : null);
                s.setVerifiedHolderName(r.getVerifiedHolderName() != null
                        ? securityGuard.sanitizeOutputText(r.getVerifiedHolderName()) : null);
                s.setVerifiedAt(r.getVerifiedAt());
            }
            s.setUpdatedAt(r.getUpdatedAt());
            result.add(s);
        }
        return result;
    }

    /**
     * 1단계: CODEF 계정 등록(/v1/account/create) → connectedId 발급 및 AES-256-GCM 암호화 저장
     */
    @Transactional
    public CodefDto.ConnectResponse connectBankAccount(
            Long memberId,
            CodefDto.ConnectRequest req,
            String clientIp
    ) {
        requireMemberId(memberId);
        checkRateLimit("CODEF_CONNECT", memberId, clientIp, 10);
        if (req == null) {
            throw CodefException.badRequest("CODEF_EMPTY_REQUEST", "요청 본문이 비어 있습니다.");
        }

        CodefBankCode bank = CodefBankCode.resolve(req.getOrganization());
        String loginType = validateLoginType(req.getLoginType());

        Map<String, Object> accountEntry = new LinkedHashMap<>();
        accountEntry.put("countryCode", "KR");
        accountEntry.put("businessType", "BK");
        accountEntry.put("organization", bank.getCodefOrgCode());
        accountEntry.put("clientType", "P");
        accountEntry.put("loginType", loginType);

        if ("0".equals(loginType)) {
            // 공동인증서: DER+KEY 또는 PFX(certFile)
            boolean hasDerKey = isNotBlank(req.getDerFile()) && isNotBlank(req.getKeyFile());
            boolean hasPfx = isNotBlank(req.getCertFile());
            if (!hasDerKey && !hasPfx) {
                throw CodefException.badRequest(
                        "CODEF_CERT_REQUIRED",
                        "공동인증서 로그인(loginType=0)은 derFile+keyFile 또는 PFX certFile(Base64)이 필수입니다."
                );
            }
            if (!isNotBlank(req.getPassword())) {
                throw CodefException.badRequest("CODEF_PASSWORD_REQUIRED", "인증서 비밀번호는 필수입니다.");
            }
            if (hasPfx) {
                accountEntry.put("certType", isNotBlank(req.getCertType()) ? req.getCertType().trim() : "pfx");
                accountEntry.put("certFile", req.getCertFile().trim());
            } else {
                accountEntry.put("certType", isNotBlank(req.getCertType()) ? req.getCertType().trim() : "1");
                accountEntry.put("derFile", req.getDerFile().trim());
                accountEntry.put("keyFile", req.getKeyFile().trim());
            }
            accountEntry.put("password", CodefCryptoUtil.encryptRsa(req.getPassword(), properties.getPublicKey()));
        } else {
            // 은행 인터넷뱅킹 아이디/비밀번호 (loginType="1")
            if (!isNotBlank(req.getId()) || !isNotBlank(req.getPassword())) {
                throw CodefException.badRequest(
                        "CODEF_ID_PW_REQUIRED",
                        "아이디 로그인(loginType=1)은 은행 아이디(id)와 비밀번호(password)가 필수입니다."
                );
            }
            accountEntry.put("id", req.getId().trim());
            accountEntry.put("password", CodefCryptoUtil.encryptRsa(req.getPassword(), properties.getPublicKey()));
        }

        if (isNotBlank(req.getBirthDate())) {
            accountEntry.put("birthDate", validateBirthDate(req.getBirthDate()));
        }

        Map<String, Object> payload = Map.of("accountList", List.of(accountEntry));
        JsonNode data = codefApiClient.postApi("/v1/account/create", payload);

        // errorList 검사
        JsonNode errorList = data.path("errorList");
        if (errorList.isArray() && !errorList.isEmpty()) {
            JsonNode firstErr = errorList.get(0);
            String errCode = firstErr.path("code").asString("CODEF_CONNECT_FAILED");
            String errMsg = firstErr.path("message").asString("은행 계정 등록에 실패했습니다.");
            String extra = firstErr.path("extraMessage").asString("");
            String fullMsg = extra.isBlank() ? errMsg : errMsg + " (" + extra + ")";
            throw CodefException.badRequest(errCode, fullMsg);
        }

        String connectedId = data.path("connectedId").asString("");
        if (connectedId.isBlank()) {
            throw CodefException.badGateway("CODEF_NO_CONNECTED_ID", "CODEF 응답에서 connectedId를 확인할 수 없습니다.");
        }

        int keyVer = cryptoKeyService.getCurrentKeyVersion();
        String connectedIdEnc = cryptoKeyService.encrypt(connectedId, keyVer);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        CodefDto.ConnectionRecord record = new CodefDto.ConnectionRecord();
        record.setMemberId(memberId);
        record.setOrganization(bank.getCodefOrgCode());
        record.setConnectedIdEnc(connectedIdEnc);
        record.setKeyVer(keyVer);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        mapper.upsertConnection(record);

        log.info("[CODEF-AUDIT] 은행 계정 연결 완료: memberId={}, org={}, loginType={}",
                memberId, bank.getCodefOrgCode(), loginType);

        CodefDto.ConnectResponse res = new CodefDto.ConnectResponse();
        res.setOrganization(bank.getCodefOrgCode());
        res.setBankName(bank.getBankName());
        res.setConnected(true);
        res.setMaskedConnectedId(maskConnectedId(connectedId));
        res.setMessage("은행 계정 연결이 완료되었습니다. 이제 실계좌 인증을 진행해 주세요.");
        return res;
    }

    /**
     * 2단계(선택): 연결된 은행의 보유 계좌 목록 조회 (/v1/kr/bank/p/account/account-list)
     */
    @Transactional(readOnly = true)
    public CodefDto.AccountListResponse fetchAccountList(
            Long memberId,
            CodefDto.AccountListRequest req,
            String clientIp
    ) {
        requireMemberId(memberId);
        checkRateLimit("CODEF_ACCOUNTS", memberId, clientIp, 15);
        if (req == null) {
            throw CodefException.badRequest("CODEF_EMPTY_REQUEST", "요청 본문이 비어 있습니다.");
        }

        CodefBankCode bank = CodefBankCode.resolve(req.getOrganization());
        String connectedId = resolveDecryptedConnectedId(memberId, bank);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("organization", bank.getCodefOrgCode());
        payload.put("connectedId", connectedId);
        if (isNotBlank(req.getBirthDate())) {
            payload.put("birthDate", validateBirthDate(req.getBirthDate()));
        }

        JsonNode data = codefApiClient.postApi("/v1/kr/bank/p/account/account-list", payload);
        List<CodefDto.AccountSummary> summaries = parseDepositTrustAccounts(data.path("resDepositTrust"));

        CodefDto.AccountListResponse res = new CodefDto.AccountListResponse();
        res.setOrganization(bank.getCodefOrgCode());
        res.setBankName(bank.getBankName());
        res.setCount(summaries.size());
        res.setAccounts(summaries);
        return res;
    }

    /**
     * 3단계(핵심): 개인 수시입출 거래내역 API(inquiryType=1) 호출로 실계좌 존재·소유 및 예금주 실명 일치 인증
     */
    @Transactional
    public CodefDto.VerifyAccountResponse verifyRealAccount(
            Long memberId,
            CodefDto.VerifyAccountRequest req,
            String clientIp
    ) {
        requireMemberId(memberId);
        checkRateLimit("CODEF_VERIFY", memberId, clientIp, 10);
        if (req == null) {
            throw CodefException.badRequest("CODEF_EMPTY_REQUEST", "요청 본문이 비어 있습니다.");
        }

        String memberName = mapper.findActiveMemberName(memberId);
        if (!isNotBlank(memberName)) {
            throw CodefException.forbidden("CODEF_MEMBER_NOT_FOUND", "활성 회원 정보를 찾을 수 없습니다.");
        }

        CodefBankCode bank = CodefBankCode.resolve(req.getOrganization());
        String accountDigits = normalizeAccountDigits(req.getAccount());
        String connectedId = resolveDecryptedConnectedId(memberId, bank);

        LocalDate today = LocalDate.now();
        LocalDate start = isNotBlank(req.getStartDate()) ? parseDate(req.getStartDate(), "startDate") : today.minusMonths(1);
        LocalDate end = isNotBlank(req.getEndDate()) ? parseDate(req.getEndDate(), "endDate") : today;
        validateDateRange(start, end);

        Map<String, Object> payload = buildTransactionPayload(
                bank.getCodefOrgCode(), connectedId, accountDigits,
                start.format(YYYYMMDD), end.format(YYYYMMDD), "0",
                req.getAccountPassword(), req.getBirthDate()
        );

        JsonNode data = codefApiClient.postApi("/v1/kr/bank/p/account/transaction-list", payload);

        String holderName = data.path("resAccountHolder").asString("").trim();
        if (holderName.isBlank()) {
            throw CodefException.badGateway("CODEF_HOLDER_MISSING", "은행 응답에서 예금주 실명(resAccountHolder)을 확인할 수 없습니다.");
        }

        if (!normalizeName(memberName).equals(normalizeName(holderName))) {
            log.warn("[CODEF-SECURITY] 실계좌 예금주 불일치: memberId={}, org={}", memberId, bank.getCodefOrgCode());
            throw CodefException.forbidden(
                    "CODEF_HOLDER_MISMATCH",
                    "계좌 예금주 실명이 MOAVA 회원 실명과 일치하지 않습니다."
            );
        }

        List<CodefDto.TransactionItem> rawTxList = parseTransactionsRaw(data.path("resTrHistoryList"));
        Long balanceAmt = resolveBalanceAmt(data, rawTxList, "0");
        String accountName = data.path("resAccountName").asString("").trim();

        int keyVer = cryptoKeyService.getCurrentKeyVersion();
        String accountEnc = cryptoKeyService.encrypt(accountDigits, keyVer);
        String last4 = accountDigits.substring(accountDigits.length() - 4);
        LocalDateTime verifiedAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        mapper.markAccountVerified(memberId, bank.getCodefOrgCode(), accountEnc, last4, holderName, keyVer, verifiedAt);

        MockBankDto.ProvisionResult mockAccount = null;
        if (mockBankProvisionService != null && !Boolean.FALSE.equals(req.getSyncToMockBank())) {
            mockAccount = mockBankProvisionService.provisionFromCodef(
                    memberId,
                    bank,
                    accountDigits,
                    holderName,
                    balanceAmt,
                    rawTxList,
                    req.getInitialMockBalance(),
                    req.getLeaderAccount(),
                    true
            );
        }

        log.info("[CODEF-AUDIT] 실계좌 인증 완료: memberId={}, org={}, account=****{}, mockSynced={}",
                memberId, bank.getCodefOrgCode(), last4, mockAccount != null);

        CodefDto.VerifyAccountResponse res = new CodefDto.VerifyAccountResponse();
        res.setVerified(true);
        res.setOrganization(bank.getCodefOrgCode());
        res.setBankName(bank.getBankName());
        res.setAccountName(accountName.isEmpty() ? null : securityGuard.sanitizeOutputText(accountName));
        res.setAccountMasked("****" + last4);
        res.setAccountLast4(last4);
        res.setHolderName(securityGuard.sanitizeOutputText(holderName));
        res.setMemberName(securityGuard.sanitizeOutputText(memberName));
        res.setBalanceAmt(balanceAmt);
        res.setRecentTransactionCount(rawTxList.size());
        res.setVerifiedAt(verifiedAt);
        res.setMockAccount(mockAccount);
        res.setMessage(mockAccount != null
                ? "실계좌 인증 및 모의 은행 원장 적재가 완료되었습니다."
                : "실계좌 소유 및 예금주 실명 인증이 완료되었습니다.");
        return res;
    }

    /**
     * 이미 CODEF 실계좌인증이 완료된 계좌의 최신 잔액·거래내역을 CODEF에서 재조회해 모의 은행 원장에 적재한다.
     */
    @Transactional
    public MockBankDto.ProvisionResult refreshVerifiedAccountToMockBank(
            Long memberId,
            CodefBankCode bank,
            String accountPassword,
            String birthDate,
            String startDate,
            String endDate,
            Long initialMockBalance,
            Boolean leaderAccount,
            String clientIp
    ) {
        requireMemberId(memberId);
        checkRateLimit("CODEF_SYNC", memberId, clientIp, 10);
        if (mockBankProvisionService == null) {
            throw CodefException.serviceUnavailable("MOCK_BANK_UNAVAILABLE", "모의 은행 적재 서비스가 초기화되지 않았습니다.");
        }

        CodefDto.ConnectionRecord conn = mapper.findConnection(memberId, bank.getCodefOrgCode());
        if (conn == null || !isNotBlank(conn.getConnectedIdEnc())) {
            throw CodefException.notFound("CODEF_NOT_CONNECTED", "먼저 해당 은행 계정을 연결해 주세요.");
        }
        if (!"Y".equals(conn.getVerifiedYn()) || !isNotBlank(conn.getVerifiedAccountEnc())) {
            throw CodefException.badRequest("CODEF_NOT_VERIFIED", "먼저 실계좌 인증을 완료해 주세요.");
        }

        String connectedId = requireExternalConnectedId(
                cryptoKeyService.decrypt(conn.getConnectedIdEnc(), conn.getKeyVer()), bank
        );
        String accountDigits = cryptoKeyService.decrypt(conn.getVerifiedAccountEnc(), conn.getKeyVer());

        LocalDate today = LocalDate.now();
        LocalDate start = isNotBlank(startDate) ? parseDate(startDate, "startDate") : today.minusMonths(1);
        LocalDate end = isNotBlank(endDate) ? parseDate(endDate, "endDate") : today;
        validateDateRange(start, end);

        Map<String, Object> payload = buildTransactionPayload(
                bank.getCodefOrgCode(), connectedId, accountDigits,
                start.format(YYYYMMDD), end.format(YYYYMMDD), "0",
                accountPassword, birthDate
        );

        JsonNode data = codefApiClient.postApi("/v1/kr/bank/p/account/transaction-list", payload);
        List<CodefDto.TransactionItem> rawTxList = parseTransactionsRaw(data.path("resTrHistoryList"));
        Long balanceAmt = resolveBalanceAmt(data, rawTxList, "0");

        String holderName = data.path("resAccountHolder").asString("").trim();
        if (holderName.isBlank()) {
            holderName = isNotBlank(conn.getVerifiedHolderName()) ? conn.getVerifiedHolderName() : "회원";
        }

        return mockBankProvisionService.provisionFromCodef(
                memberId,
                bank,
                accountDigits,
                holderName,
                balanceAmt,
                rawTxList,
                initialMockBalance,
                leaderAccount,
                true
        );
    }

    /**
     * 4단계: 인증된 본인 계좌의 실제 입출금 거래내역 조회 (/v1/kr/bank/p/account/transaction-list)
     */
    @Transactional(readOnly = true)
    public CodefDto.TransactionListResponse fetchTransactions(
            Long memberId,
            CodefDto.TransactionRequest req,
            String clientIp
    ) {
        requireMemberId(memberId);
        checkRateLimit("CODEF_TX", memberId, clientIp, 15);
        if (req == null) {
            throw CodefException.badRequest("CODEF_EMPTY_REQUEST", "요청 본문이 비어 있습니다.");
        }

        CodefBankCode bank = CodefBankCode.resolve(req.getOrganization());
        CodefDto.ConnectionRecord conn = mapper.findConnection(memberId, bank.getCodefOrgCode());
        if (conn == null) {
            throw CodefException.notFound("CODEF_NOT_CONNECTED", "먼저 해당 은행 계정을 연결해 주세요.");
        }

        String connectedId = requireExternalConnectedId(
                cryptoKeyService.decrypt(conn.getConnectedIdEnc(), conn.getKeyVer()), bank
        );
        String accountDigits;
        if (isNotBlank(req.getAccount())) {
            accountDigits = normalizeAccountDigits(req.getAccount());
        } else if ("Y".equals(conn.getVerifiedYn()) && isNotBlank(conn.getVerifiedAccountEnc())) {
            accountDigits = cryptoKeyService.decrypt(conn.getVerifiedAccountEnc(), conn.getKeyVer());
        } else {
            throw CodefException.badRequest("CODEF_ACCOUNT_REQUIRED", "조회할 계좌번호(account)를 입력해 주세요.");
        }

        LocalDate today = LocalDate.now();
        LocalDate start = isNotBlank(req.getStartDate()) ? parseDate(req.getStartDate(), "startDate") : today.minusMonths(1);
        LocalDate end = isNotBlank(req.getEndDate()) ? parseDate(req.getEndDate(), "endDate") : today;
        validateDateRange(start, end);

        String orderBy = ("1".equals(req.getOrderBy())) ? "1" : "0";
        String startStr = start.format(YYYYMMDD);
        String endStr = end.format(YYYYMMDD);

        Map<String, Object> payload = buildTransactionPayload(
                bank.getCodefOrgCode(), connectedId, accountDigits,
                startStr, endStr, orderBy,
                req.getAccountPassword(), req.getBirthDate()
        );

        JsonNode data = codefApiClient.postApi("/v1/kr/bank/p/account/transaction-list", payload);
        List<CodefDto.TransactionItem> txList = parseTransactions(data.path("resTrHistoryList"));
        String last4 = accountDigits.substring(accountDigits.length() - 4);

        CodefDto.TransactionListResponse res = new CodefDto.TransactionListResponse();
        res.setOrganization(bank.getCodefOrgCode());
        res.setBankName(bank.getBankName());
        res.setAccountMasked("****" + last4);
        res.setHolderName(securityGuard.sanitizeOutputText(data.path("resAccountHolder").asString("")));
        res.setBalanceAmt(resolveBalanceAmt(data, txList, orderBy));
        res.setStartDate(data.path("commStartDate").asString(startStr));
        res.setEndDate(data.path("commEndDate").asString(endStr));
        res.setCount(txList.size());
        res.setTransactions(txList);
        return res;
    }

    @Transactional
    public void disconnect(Long memberId, String rawOrganization) {
        requireMemberId(memberId);
        CodefBankCode bank = CodefBankCode.resolve(rawOrganization);
        CodefDto.ConnectionRecord conn = mapper.findConnection(memberId, bank.getCodefOrgCode());
        if (conn == null) {
            throw CodefException.notFound("CODEF_NOT_CONNECTED", "연결된 은행 계정이 없습니다.");
        }

        if (isNotBlank(conn.getConnectedIdEnc())) {
            deleteRemoteConnectedAccountBestEffort(memberId, bank.getCodefOrgCode(), conn);
        }

        int deleted = mapper.deleteConnection(memberId, bank.getCodefOrgCode());
        if (deleted == 0) {
            throw CodefException.notFound("CODEF_NOT_CONNECTED", "연결된 은행 계정이 없습니다.");
        }
        log.info("[CODEF-AUDIT] 은행 연결 해제: memberId={}, org={}", memberId, bank.getCodefOrgCode());
    }

    private void deleteRemoteConnectedAccountBestEffort(
            Long memberId,
            String orgCode,
            CodefDto.ConnectionRecord conn
    ) {
        try {
            String connectedId = cryptoKeyService.decrypt(conn.getConnectedIdEnc(), conn.getKeyVer());
            if (!isNotBlank(connectedId) || isQuickOAuthMarker(connectedId)) {
                return;
            }
            if (tryDeleteRemoteAccount(orgCode, connectedId, "0")) {
                return;
            }
            tryDeleteRemoteAccount(orgCode, connectedId, "1");
        } catch (Exception e) {
            log.warn("[CODEF] 원격 계정 삭제 실패(로컬 연결 해제는 계속 진행): memberId={}, org={}, reason={}",
                    memberId, orgCode, e.getMessage());
        }
    }

    private boolean tryDeleteRemoteAccount(String orgCode, String connectedId, String loginType) {
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("countryCode", "KR");
        account.put("businessType", "BK");
        account.put("clientType", "P");
        account.put("organization", orgCode);
        account.put("loginType", loginType);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("connectedId", connectedId);
        payload.put("accountList", List.of(account));

        try {
            JsonNode data = codefApiClient.postApi("/v1/account/delete", payload);
            JsonNode successList = data != null ? data.path("successList") : null;
            return successList != null && successList.isArray() && !successList.isEmpty();
        } catch (CodefException e) {
            if (e.getHttpStatus().is5xxServerError()) {
                throw e;
            }
            return false;
        }
    }

    // ==================== 내부 헬퍼 ====================

    private Map<String, Object> buildTransactionPayload(
            String orgCode,
            String connectedId,
            String accountDigits,
            String startDate,
            String endDate,
            String orderBy,
            String rawAccountPassword,
            String rawBirthDate
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("organization", orgCode);
        payload.put("connectedId", connectedId);
        payload.put("account", accountDigits);
        payload.put("startDate", startDate);
        payload.put("endDate", endDate);
        payload.put("orderBy", orderBy);
        payload.put("inquiryType", "1"); // 예금주명(resAccountHolder) 등 계좌 상세정보 포함 필수
        if (isNotBlank(rawAccountPassword)) {
            payload.put("accountPassword", CodefCryptoUtil.encryptRsa(rawAccountPassword, properties.getPublicKey()));
        }
        if (isNotBlank(rawBirthDate)) {
            payload.put("birthDate", validateBirthDate(rawBirthDate));
        }
        return payload;
    }

    private String resolveDecryptedConnectedId(Long memberId, CodefBankCode bank) {
        CodefDto.ConnectionRecord conn = mapper.findConnection(memberId, bank.getCodefOrgCode());
        if (conn == null || !isNotBlank(conn.getConnectedIdEnc())) {
            throw CodefException.notFound(
                    "CODEF_NOT_CONNECTED",
                    bank.getBankName() + " 계정이 연결되어 있지 않습니다. 먼저 공동인증서 또는 은행 로그인으로 연결해 주세요."
            );
        }
        String decrypted = cryptoKeyService.decrypt(conn.getConnectedIdEnc(), conn.getKeyVer());
        return requireExternalConnectedId(decrypted, bank);
    }

    private String requireExternalConnectedId(String connectedId, CodefBankCode bank) {
        if (!isNotBlank(connectedId) || isQuickOAuthMarker(connectedId)) {
            throw CodefException.badRequest(
                    "CODEF_QUICK_OAUTH_ONLY",
                    bank.getBankName() + "은(는) 가상은행 1원 인증으로 연결된 계좌입니다. CODEF 원격 조회 대신 오픈뱅킹 원장 조회를 이용해 주세요."
            );
        }
        return connectedId;
    }

    private static boolean isQuickOAuthMarker(String connectedId) {
        return connectedId != null && connectedId.startsWith("QUICK_OAUTH:");
    }

    private List<CodefDto.AccountSummary> parseDepositTrustAccounts(JsonNode node) {
        List<CodefDto.AccountSummary> list = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return list;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                list.add(toAccountSummary(item));
            }
        } else if (node.isObject()) {
            list.add(toAccountSummary(node));
        }
        return list;
    }

    private CodefDto.AccountSummary toAccountSummary(JsonNode item) {
        CodefDto.AccountSummary s = new CodefDto.AccountSummary();
        s.setAccountNo(item.path("resAccount").asString(""));
        s.setAccountDisplay(securityGuard.sanitizeOutputText(item.path("resAccountDisplay").asString("")));
        s.setAccountName(securityGuard.sanitizeOutputText(item.path("resAccountName").asString("")));
        s.setBalanceAmt(parseLongSafe(item.path("resAccountBalance").asString("0")));
        s.setDepositType(item.path("resAccountDeposit").asString(""));
        return s;
    }

    private List<CodefDto.TransactionItem> parseTransactions(JsonNode node) {
        return parseTransactionsInternal(node, true);
    }

    private List<CodefDto.TransactionItem> parseTransactionsRaw(JsonNode node) {
        return parseTransactionsInternal(node, false);
    }

    private List<CodefDto.TransactionItem> parseTransactionsInternal(JsonNode node, boolean sanitizeForOutput) {
        List<CodefDto.TransactionItem> list = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return list;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                list.add(toTransactionItem(item, sanitizeForOutput));
            }
        } else if (node.isObject()) {
            list.add(toTransactionItem(node, sanitizeForOutput));
        }
        return list;
    }

    private CodefDto.TransactionItem toTransactionItem(JsonNode item, boolean sanitizeForOutput) {
        long inAmt = parseLongSafe(item.path("resAccountIn").asString("0"));
        long outAmt = parseLongSafe(item.path("resAccountOut").asString("0"));

        CodefDto.TransactionItem tx = new CodefDto.TransactionItem();
        tx.setTranDate(item.path("resAccountTrDate").asString(""));
        tx.setTranTime(item.path("resAccountTrTime").asString(""));
        if (inAmt > 0) {
            tx.setInoutType("입금");
            tx.setTranAmt(inAmt);
        } else {
            tx.setInoutType("출금");
            tx.setTranAmt(outAmt);
        }
        tx.setAfterBalanceAmt(parseLongSafe(item.path("resAfterTranBalance").asString("0")));

        String d1 = item.path("resAccountDesc1").asString("");
        String d2 = item.path("resAccountDesc2").asString("");
        String d3 = item.path("resAccountDesc3").asString("");
        String d4 = item.path("resAccountDesc4").asString("");
        if (sanitizeForOutput) {
            tx.setDesc1(securityGuard.sanitizeOutputText(d1));
            tx.setDesc2(securityGuard.sanitizeOutputText(d2));
            tx.setDesc3(securityGuard.sanitizeOutputText(d3));
            tx.setDesc4(securityGuard.sanitizeOutputText(d4));
        } else {
            tx.setDesc1(d1);
            tx.setDesc2(d2);
            tx.setDesc3(d3);
            tx.setDesc4(d4);
        }
        return tx;
    }

    private Long resolveBalanceAmt(JsonNode data, List<CodefDto.TransactionItem> txList, String orderBy) {
        String rawBal = data.path("resAccountBalance").asString("").trim();
        if (!rawBal.isEmpty()) {
            return parseLongSafe(rawBal);
        }
        if (txList != null && !txList.isEmpty()) {
            CodefDto.TransactionItem latestTx = "1".equals(orderBy)
                    ? txList.get(txList.size() - 1)
                    : txList.get(0);
            if (latestTx.getAfterBalanceAmt() != null) {
                return latestTx.getAfterBalanceAmt();
            }
        }
        return 0L;
    }

    private String validateLoginType(String raw) {
        if (!isNotBlank(raw)) {
            throw CodefException.badRequest("CODEF_LOGIN_TYPE_REQUIRED", "loginType은 필수입니다. (0: 공동인증서, 1: 은행 아이디/비밀번호)");
        }
        String trimmed = raw.trim();
        if ("5".equals(trimmed) || "6".equals(trimmed)) {
            throw CodefException.badRequest(
                    "CODEF_SIMPLE_AUTH_UNSUPPORTED",
                    "CODEF 은행 개인 수시입출 API는 간편인증을 지원하지 않습니다. 공동인증서(loginType=0) 또는 은행 아이디/비밀번호(loginType=1)를 사용해 주세요."
            );
        }
        if (!"0".equals(trimmed) && !"1".equals(trimmed)) {
            throw CodefException.badRequest("CODEF_INVALID_LOGIN_TYPE", "지원하지 않는 loginType입니다. (0: 공동인증서, 1: 은행 아이디/비밀번호)");
        }
        return trimmed;
    }

    private String normalizeAccountDigits(String rawAccount) {
        if (!isNotBlank(rawAccount)) {
            throw CodefException.badRequest("CODEF_ACCOUNT_REQUIRED", "계좌번호(account)는 필수입니다.");
        }
        String digits = rawAccount.trim().replace("-", "");
        if (!DIGITS_ONLY.matcher(digits).matches()) {
            throw CodefException.badRequest("CODEF_INVALID_ACCOUNT", "계좌번호는 숫자 8~20자리여야 합니다.");
        }
        return digits;
    }

    private String validateBirthDate(String rawBirthDate) {
        String trimmed = rawBirthDate.trim();
        if (!BIRTHDATE_6.matcher(trimmed).matches()) {
            throw CodefException.badRequest("CODEF_INVALID_BIRTHDATE", "생년월일(birthDate)은 YYMMDD 6자리 숫자여야 합니다.");
        }
        return trimmed;
    }

    private LocalDate parseDate(String raw, String field) {
        try {
            return LocalDate.parse(raw.trim(), YYYYMMDD);
        } catch (DateTimeParseException e) {
            throw CodefException.badRequest("CODEF_INVALID_DATE", field + "는 yyyyMMdd 형식이어야 합니다.");
        }
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw CodefException.badRequest("CODEF_INVALID_RANGE", "시작일(startDate)은 종료일(endDate)보다 늦을 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_QUERY_DAYS) {
            throw CodefException.badRequest("CODEF_RANGE_TOO_LONG", "조회 기간은 최대 1년(366일)을 초과할 수 없습니다.");
        }
    }

    private void checkRateLimit(String bucket, Long memberId, String clientIp, int maxPerMin) {
        try {
            securityGuard.checkRateLimit(bucket, memberId + ":" + (clientIp != null ? clientIp : "unknown"), maxPerMin);
        } catch (com.moava.openbanking.exception.KftcOpenBankingException e) {
            throw CodefException.tooManyRequests("CODEF_RATE_LIMIT", "CODEF 요청 횟수가 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static void requireMemberId(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw CodefException.unauthorized("CODEF_AUTH_REQUIRED", "로그인한 회원만 실계좌인증 API를 이용할 수 있습니다.");
        }
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").trim();
    }

    private static String maskConnectedId(String connectedId) {
        if (connectedId == null || connectedId.length() <= 6) {
            return "******";
        }
        return connectedId.substring(0, 4) + "****" + connectedId.substring(connectedId.length() - 2);
    }

    private static String safeBankName(String orgCode) {
        try {
            return CodefBankCode.resolve(orgCode).getBankName();
        } catch (Exception e) {
            return orgCode;
        }
    }

    private static long parseLongSafe(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        String cleaned = raw.replaceAll("[^0-9-]", "");
        if (cleaned.isEmpty() || "-".equals(cleaned)) {
            return 0L;
        }
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
