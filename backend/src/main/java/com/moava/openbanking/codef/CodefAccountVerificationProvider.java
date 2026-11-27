package com.moava.openbanking.codef;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.group.account.service.AccountVerificationProvider;
import com.moava.openbanking.dto.KftcRecordDto;
import com.moava.openbanking.mapper.MockBankLedgerMapper;
import com.moava.openbanking.service.MockBankProvisionService;
import com.moava.openbanking.service.MockBankQuickConnectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * F19 모임 계좌 소유 인증({@link AccountVerificationProvider}) 구현체.
 *
 * <p>동작 순서:
 * <ol>
 *   <li>동일 회원의 해당 은행 인증 이력({@code VERIFIED_YN = 'Y'})과 계좌번호가 일치하면
 *       외부 API 호출 없이 즉시 소유 확인 성공을 반환한다.</li>
 *   <li>가상은행 1원 인증으로 적재된 본인 소유 활성 계좌({@code MOCK_KFTC_ACCOUNT})가 일치하면
 *       동일 은행 다계좌라도 즉시 소유 확인 성공을 반환한다.</li>
 *   <li>외부 CODEF 연결({@code connectedId})이 존재하면 {@link CodefBankService#verifyRealAccount}로
 *       실시간 검증을 시도하고, 가상은행 전용 마커({@code QUICK_OAUTH:})는 외부 호출 없이 {@code false}를 반환한다.</li>
 * </ol>
 */
@Slf4j
@Component
public class CodefAccountVerificationProvider implements AccountVerificationProvider {

    private static final Set<String> VERIFICATION_FAIL_CODES = Set.of(
            "CODEF_HOLDER_MISMATCH",
            "CODEF_NOT_CONNECTED",
            "CODEF_INVALID_ACCOUNT",
            "CODEF_UNSUPPORTED_BANK",
            "CODEF_QUICK_OAUTH_ONLY"
    );

    private final CodefAccountMapper codefAccountMapper;
    private final CryptoKeyService cryptoKeyService;
    private final CodefBankService codefBankService;
    private final MockBankLedgerMapper ledgerMapper;
    private final MockBankProvisionService provisionService;

    @Autowired
    public CodefAccountVerificationProvider(
            CodefAccountMapper codefAccountMapper,
            CryptoKeyService cryptoKeyService,
            CodefBankService codefBankService,
            MockBankLedgerMapper ledgerMapper,
            MockBankProvisionService provisionService
    ) {
        this.codefAccountMapper = codefAccountMapper;
        this.cryptoKeyService = cryptoKeyService;
        this.codefBankService = codefBankService;
        this.ledgerMapper = ledgerMapper;
        this.provisionService = provisionService;
    }

    /** 기존 단위 테스트 호환용 생성자 */
    public CodefAccountVerificationProvider(
            CodefAccountMapper codefAccountMapper,
            CryptoKeyService cryptoKeyService,
            CodefBankService codefBankService
    ) {
        this(codefAccountMapper, cryptoKeyService, codefBankService, null, null);
    }

    @Override
    public boolean verifyAccount(Long memberId, String bankName, String accountNumber) {
        if (memberId == null || memberId <= 0
                || bankName == null || bankName.isBlank()
                || accountNumber == null || accountNumber.isBlank()) {
            return false;
        }

        CodefBankCode bank;
        try {
            bank = CodefBankCode.resolve(bankName);
        } catch (CodefException e) {
            log.warn("[CODEF-F19] 지원하지 않는 은행으로 계좌 소유 인증 불가: memberId={}", memberId);
            return false;
        }

        String accountDigits = normalizeDigits(accountNumber);
        if (accountDigits.length() < 8 || accountDigits.length() > 20) {
            return false;
        }

        // 1) 이미 인증 완료된 동일 계좌 이력 일치 시 외부 호출 없이 즉시 통과
        CodefDto.ConnectionRecord conn = codefAccountMapper.findConnection(memberId, bank.getCodefOrgCode());
        if (conn != null && "Y".equals(conn.getVerifiedYn()) && !isBlank(conn.getVerifiedAccountEnc())) {
            String verifiedDigits = normalizeDigits(
                    cryptoKeyService.decrypt(conn.getVerifiedAccountEnc(), conn.getKeyVer())
            );
            if (accountDigits.equals(verifiedDigits)) {
                log.info("[CODEF-F19] 기존 계좌 인증 이력 일치로 소유 확인 완료: memberId={}, org={}, account=****{}",
                        memberId, bank.getCodefOrgCode(), extractLast4(accountDigits));
                promoteGroupReceivingAccount(memberId, bank, accountDigits, null);
                return true;
            }
        }

        // 2) 가상은행 1원 인증으로 등록된 본인 소유 활성 계좌(동일 은행 다계좌 포함) 일치 시 즉시 통과
        if (ledgerMapper != null && provisionService != null) {
            String userSeqNo = provisionService.deriveUserSeqNo(memberId);
            KftcRecordDto.AccountRecord mockAcc =
                    ledgerMapper.findAccountByBankAndDigits(bank.getKftcCode(), accountDigits);
            if (mockAcc != null
                    && "ACTIVE".equalsIgnoreCase(mockAcc.getStatus())
                    && userSeqNo.equals(mockAcc.getUserSeqNo())) {
                log.info("[CODEF-F19] 가상은행 1원 인증 계좌 일치로 소유 확인 완료: memberId={}, org={}, account=****{}",
                        memberId, bank.getCodefOrgCode(), extractLast4(accountDigits));
                promoteGroupReceivingAccount(memberId, bank, accountDigits, mockAcc);
                return true;
            }
        }

        if (conn == null || isBlank(conn.getConnectedIdEnc())) {
            log.warn("[CODEF-F19] 은행 연결 이력이 없어 소유 확인 불가: memberId={}, org={}",
                    memberId, bank.getCodefOrgCode());
            return false;
        }

        // 3) 가상은행 간편연결(QUICK_OAUTH:) 전용 레코드는 외부 CODEF API를 호출하지 않고 소유 미일치 처리
        if (conn.getKeyVer() != null) {
            String decryptedConnId = cryptoKeyService.decrypt(conn.getConnectedIdEnc(), conn.getKeyVer());
            if (decryptedConnId != null
                    && decryptedConnId.startsWith(MockBankQuickConnectService.QUICK_OAUTH_CONN_PREFIX)) {
                log.warn("[CODEF-F19] 가상은행 연결 계좌와 불일치하여 소유 확인 불가: memberId={}, org={}, account=****{}",
                        memberId, bank.getCodefOrgCode(), extractLast4(accountDigits));
                return false;
            }
        }

        // 4) 외부 CODEF 연결이 존재하는 경우 실시간 예금주 조회 수행
        CodefDto.VerifyAccountRequest req = new CodefDto.VerifyAccountRequest();
        req.setOrganization(bank.getCodefOrgCode());
        req.setAccount(accountDigits);
        req.setSyncToMockBank(Boolean.TRUE);

        try {
            CodefDto.VerifyAccountResponse res = codefBankService.verifyRealAccount(memberId, req, "internal-f19");
            boolean verified = res != null && res.isVerified();
            if (verified) {
                promoteGroupReceivingAccount(memberId, bank, accountDigits, null);
            }
            return verified;
        } catch (CodefException e) {
            if (VERIFICATION_FAIL_CODES.contains(e.getCode())) {
                log.warn("[CODEF-F19] 계좌 소유 확인 실패: memberId={}, org={}, code={}",
                        memberId, bank.getCodefOrgCode(), e.getCode());
                return false;
            }
            throw e;
        }
    }

    /**
     * 모임 계좌 소유 확인(F19/F20)이 통과된 계좌가 가상은행 원장에 본인 소유 활성 계좌로 존재하면
     * 모임 수취 계좌(LEADER_YN='Y')로 자동 승격한다. (모임 생성 이전 연결한 계좌 보정)
     */
    private void promoteGroupReceivingAccount(
            Long memberId,
            CodefBankCode bank,
            String accountDigits,
            KftcRecordDto.AccountRecord preloadedAcc
    ) {
        if (ledgerMapper == null || provisionService == null) {
            return;
        }
        try {
            String userSeqNo = provisionService.deriveUserSeqNo(memberId);
            KftcRecordDto.AccountRecord mockAcc = preloadedAcc != null
                    ? preloadedAcc
                    : ledgerMapper.findAccountByBankAndDigits(bank.getKftcCode(), accountDigits);
            if (mockAcc == null
                    || isBlank(mockAcc.getFintechUseNum())
                    || !"ACTIVE".equalsIgnoreCase(mockAcc.getStatus())
                    || !userSeqNo.equals(mockAcc.getUserSeqNo())
                    || "Y".equalsIgnoreCase(mockAcc.getLeaderYn())) {
                return;
            }
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            int updated = ledgerMapper.updateAccountLeaderYn(mockAcc.getFintechUseNum(), userSeqNo, "Y", now);
            if (updated == 1) {
                log.info("[CODEF-F19] 모임 계좌 소유 확인에 따라 모의 수취계좌(LEADER_YN=Y) 자동 승격: memberId={}, org={}, account=****{}",
                        memberId, bank.getCodefOrgCode(), extractLast4(accountDigits));
            }
        } catch (RuntimeException e) {
            log.warn("[CODEF-F19] 모의 수취계좌 자동 승격 중 오류(소유 확인 결과는 유지): memberId={}, org={}",
                    memberId, bank.getCodefOrgCode(), e);
        }
    }

    private static String normalizeDigits(String raw) {
        return raw == null ? "" : raw.replaceAll("[^0-9]", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String extractLast4(String digits) {
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }
}
