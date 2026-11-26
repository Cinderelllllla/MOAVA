package com.moava.openbanking.codef;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.group.account.service.AccountVerificationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * F19 모임 계좌 소유 인증({@link AccountVerificationProvider})의 CODEF 연동 구현체.
 *
 * <p>동작 순서:
 * <ol>
 *   <li>동일 회원의 해당 은행 CODEF 실계좌 인증 이력({@code VERIFIED_YN = 'Y'})과 계좌번호가 일치하면
 *       CODEF 외부 API 호출 없이 즉시 소유 확인 성공을 반환한다(데모 일 100건 한도 보호).</li>
 *   <li>은행 연결({@code connectedId})은 되어 있으나 아직 해당 계좌 인증 이력이 없으면
 *       {@link CodefBankService#verifyRealAccount}를 호출해 실시간 예금주 실명 일치 여부를 검증한다.</li>
 *   <li>미연결·미지원 은행·예금주 불일치는 {@code false}(422)로, CODEF 통신/서버 장애는 예외(502)로 전달한다.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodefAccountVerificationProvider implements AccountVerificationProvider {

    private static final Set<String> VERIFICATION_FAIL_CODES = Set.of(
            "CODEF_HOLDER_MISMATCH",
            "CODEF_NOT_CONNECTED",
            "CODEF_INVALID_ACCOUNT",
            "CODEF_UNSUPPORTED_BANK"
    );

    private final CodefAccountMapper codefAccountMapper;
    private final CryptoKeyService cryptoKeyService;
    private final CodefBankService codefBankService;

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

        CodefDto.ConnectionRecord conn = codefAccountMapper.findConnection(memberId, bank.getCodefOrgCode());
        if (conn == null || isBlank(conn.getConnectedIdEnc())) {
            log.warn("[CODEF-F19] CODEF 은행 연결 이력이 없어 소유 확인 불가: memberId={}, org={}",
                    memberId, bank.getCodefOrgCode());
            return false;
        }

        // 1) 이미 CODEF 실계좌 인증 완료된 동일 계좌면 API 재호출 없이 즉시 통과
        if ("Y".equals(conn.getVerifiedYn()) && !isBlank(conn.getVerifiedAccountEnc())) {
            String verifiedDigits = normalizeDigits(
                    cryptoKeyService.decrypt(conn.getVerifiedAccountEnc(), conn.getKeyVer())
            );
            if (accountDigits.equals(verifiedDigits)) {
                log.info("[CODEF-F19] 기존 CODEF 실계좌 인증 이력 일치로 소유 확인 완료: memberId={}, org={}, account=****{}",
                        memberId, bank.getCodefOrgCode(), extractLast4(accountDigits));
                return true;
            }
        }

        // 2) 미인증 상태이거나 다른 계좌번호이면 CODEF 실거래/예금주 조회로 실시간 인증 수행
        CodefDto.VerifyAccountRequest req = new CodefDto.VerifyAccountRequest();
        req.setOrganization(bank.getCodefOrgCode());
        req.setAccount(accountDigits);
        req.setSyncToMockBank(Boolean.TRUE);

        try {
            CodefDto.VerifyAccountResponse res = codefBankService.verifyRealAccount(memberId, req, "internal-f19");
            return res != null && res.isVerified();
        } catch (CodefException e) {
            if (VERIFICATION_FAIL_CODES.contains(e.getCode())) {
                log.warn("[CODEF-F19] 계좌 소유 확인 실패: memberId={}, org={}, code={}",
                        memberId, bank.getCodefOrgCode(), e.getCode());
                return false;
            }
            throw e;
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
