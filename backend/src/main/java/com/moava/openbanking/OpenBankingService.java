package com.moava.openbanking;

import com.moava.common.crypto.EnvelopeCryptoProvider;
import com.moava.openbanking.OpenBankingDTO.*;
import com.moava.openbanking.OpenBankingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OpenBankingService {

    private final OpenBankingMapper openBankingMapper;
    private final EnvelopeCryptoProvider cryptoProvider; // 팀 공통 AES-256-GCM + HMAC-SHA256 모듈 주입

    /**
     * 1. CODEF API 등에서 가져온 계좌 정보를 가상 은행 원장(MOCK_BANK_ACCOUNT)에 적재
     */
    @Transactional
    public MockAccountVO syncCodefAccount(CodefSyncRequest req) {
        String cleanAccountNo = normalizeAccountNo(req.getAccountNo());
        String bankCode = (req.getBankCode() != null && !req.getBankCode().isBlank()) ? req.getBankCode() : "099";
        long balance = (req.getInitialBalance() != null) ? req.getInitialBalance() : 1000000L;
        String sourceType = (req.getSourceType() != null && !req.getSourceType().isBlank()) ? req.getSourceType() : "CODEF";

        // 24자리 핀테크이용번호 생성
        String fintechUseNum = generateFintechUseNum(bankCode, cleanAccountNo);

        openBankingMapper.upsertMockBankAccount(
                req.getMemberId(),
                fintechUseNum,
                bankCode,
                req.getBankName().trim(),
                cleanAccountNo,
                req.getHolderName().trim(),
                balance,
                sourceType
        );

        return openBankingMapper.selectMockAccountByBankAndNo(req.getBankName().trim(), cleanAccountNo);
    }

    /**
     * 2. [토이 프로젝트 전용] 가상 은행 계좌 잔액 자유 변경
     */
    @Transactional
    public MockAccountVO updateAccountBalanceForTest(BalanceUpdateRequest req) {
        String cleanAccountNo = normalizeAccountNo(req.getAccountNo());
        int updated = openBankingMapper.updateMockAccountBalanceDirect(
                req.getBankName().trim(),
                cleanAccountNo,
                req.getNewBalanceAmt()
        );
        if (updated == 0) {
            throw new IllegalArgumentException("잔액을 변경할 계좌가 가상 은행에 존재하지 않습니다.");
        }
        return openBankingMapper.selectMockAccountByBankAndNo(req.getBankName().trim(), cleanAccountNo);
    }

    /**
     * 3. 계좌 실존 여부 확인 + 사용자 실명 인증 (금결원 /v2.0/inquiry/real_name 대체)
     */
    @Transactional(readOnly = true)
    public RealNameVerifyResponse verifyAccountAndRealName(RealNameVerifyRequest req) {
        String cleanAccountNo = normalizeAccountNo(req.getAccountNo());
        MockAccountVO account = openBankingMapper.selectMockAccountByBankAndNo(req.getBankName().trim(), cleanAccountNo);

        // ① 계좌 자체가 존재하지 않는 경우
        if (account == null) {
            return RealNameVerifyResponse.builder()
                    .accountExists(false)
                    .realNameMatched(false)
                    .rspCode("A0001")
                    .rspMessage("입력하신 은행에 존재하지 않는 계좌번호입니다.")
                    .bankName(req.getBankName())
                    .build();
        }

        // ② 계좌는 존재하나 입력한 예금주 실명과 다른 경우
        String inputName = req.getHolderName() != null ? req.getHolderName().trim() : "";
        boolean isBankHolderMatched = account.getHolderName().equals(inputName);

        // (선택) memberId가 넘어왔다면 MEMBER 테이블의 가입 실명과도 일치하는지 확인
        if (isBankHolderMatched && req.getMemberId() != null) {
            String memberDbName = openBankingMapper.selectMemberNameById(req.getMemberId());
            if (memberDbName != null && !memberDbName.trim().equals(inputName)) {
                isBankHolderMatched = false;
            }
        }

        String last4 = cleanAccountNo.length() >= 4
                ? cleanAccountNo.substring(cleanAccountNo.length() - 4)
                : cleanAccountNo;

        if (!isBankHolderMatched) {
            return RealNameVerifyResponse.builder()
                    .accountExists(true)
                    .realNameMatched(false)
                    .rspCode("A0003")
                    .rspMessage("계좌는 존재하나 예금주 실명이 일치하지 않습니다.")
                    .fintechUseNum(account.getFintechUseNum())
                    .bankName(account.getBankName())
                    .accountNoLast4(last4)
                    .build();
        }

        // ③ 계좌 존재 + 실명 일치 통과
        return RealNameVerifyResponse.builder()
                .accountExists(true)
                .realNameMatched(true)
                .rspCode("A0000")
                .rspMessage("정상적으로 실명 인증이 완료되었습니다.")
                .fintechUseNum(account.getFintechUseNum())
                .bankName(account.getBankName())
                .accountNoLast4(last4)
                .holderName(account.getHolderName())
                .currentBalanceAmt(account.getBalanceAmt())
                .build();
    }

    /**
     * 4. 모임장 계좌로만 회비 이체 실행 + 실시간 거래내역(확인중/PENDING) 적재
     */
    @Transactional
    public TransferToLeaderResponse transferToGroupLeader(TransferToLeaderRequest req) {
        if (req.getAmount() == null || req.getAmount() <= 0) {
            throw new IllegalArgumentException("이체 금액은 0원보다 커야 합니다.");
        }

        // [STEP 1] 송금자(모임원) 출금 계좌 확인 및 실명 검증
        RealNameVerifyResponse senderVerify = verifyAccountAndRealName(new RealNameVerifyRequest(
                req.getMemberId(),
                req.getSenderBankName(),
                req.getSenderAccountNo(),
                req.getSenderHolderName()
        ));
        if (!senderVerify.isAccountExists() || !senderVerify.isRealNameMatched()) {
            throw new IllegalStateException(senderVerify.getRspMessage());
        }

        String cleanSenderAcctNo = normalizeAccountNo(req.getSenderAccountNo());
        MockAccountVO senderAccount = openBankingMapper.selectMockAccountByBankAndNo(
                req.getSenderBankName().trim(), cleanSenderAcctNo
        );

        // 잔액 부족 체크
        if (senderAccount.getBalanceAmt() < req.getAmount()) {
            throw new IllegalStateException("출금 계좌의 잔액이 부족합니다. (현재 잔액: " + senderAccount.getBalanceAmt() + "원)");
        }

        // [STEP 2] groupId로 모임장의 활성 계좌(GYE_ACCOUNT) 조회 후 [복호화 + HMAC 해시] 동시 검증
        MockAccountVO leaderBankAccount = resolveAndVerifyLeaderAccount(req.getGroupId());

        // 본인 계좌로 이체 방지
        if (senderAccount.getFintechUseNum().equals(leaderBankAccount.getFintechUseNum())) {
            throw new IllegalStateException("출금 계좌와 모임장 입금 계좌가 동일합니다.");
        }

        // [STEP 3] 출금 계좌 잔액 차감(-) 및 모임장 계좌 잔액 증액(+)
        openBankingMapper.addAccountBalance(senderAccount.getFintechUseNum(), -req.getAmount());
        openBankingMapper.addAccountBalance(leaderBankAccount.getFintechUseNum(), req.getAmount());

        long senderAfterBalance = senderAccount.getBalanceAmt() - req.getAmount();
        long leaderAfterBalance = leaderBankAccount.getBalanceAmt() + req.getAmount();

        // [STEP 4] PAYMENT_PROOF 테이블에 'PENDING(확인중)' 상태로 납입내역 생성/갱신 (이중 확인용)
        String bankTranId = "M2026OB" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String printContent = senderAccount.getHolderName() + "_" + req.getPayMonth() + "_" + req.getRoundNo() + "회차";
        String reviewMemo = "오픈뱅킹 계좌이체 완료 (거래번호: " + bankTranId + ") - 증빙 이중 확인 대기중";

        Long paymentProofId = openBankingMapper.selectExistingPaymentProofId(
                req.getGroupId(), req.getMemberId(), req.getPayMonth(), req.getRoundNo()
        );

        if (paymentProofId == null) {
            openBankingMapper.insertPendingPaymentProof(
                    req.getGroupId(), req.getMemberId(), req.getPayMonth(), req.getRoundNo(), req.getAmount(), reviewMemo
            );
            paymentProofId = openBankingMapper.selectExistingPaymentProofId(
                    req.getGroupId(), req.getMemberId(), req.getPayMonth(), req.getRoundNo()
            );
        } else {
            openBankingMapper.updatePaymentProofToPending(paymentProofId, req.getAmount(), reviewMemo);
        }

        // [STEP 5] 가상 은행 거래내역(MOCK_BANK_TRANSACTION)에 출금 1건 + 입금 1건 기록
        // 5-1. 모임원 출금 내역
        openBankingMapper.insertMockTransaction(
                bankTranId + "-W",
                senderAccount.getFintechUseNum(),
                "WITHDRAW",
                req.getAmount(),
                senderAfterBalance,
                leaderBankAccount.getHolderName(),
                leaderBankAccount.getAccountNo(),
                "모임회비_" + leaderBankAccount.getHolderName(),
                req.getGroupId(),
                paymentProofId
        );

        // 5-2. 모임장 입금 내역 (PAYMENT_PROOF_ID와 연결되어 거래내역 탭에 즉시 노출)
        openBankingMapper.insertMockTransaction(
                bankTranId,
                leaderBankAccount.getFintechUseNum(),
                "DEPOSIT",
                req.getAmount(),
                leaderAfterBalance,
                senderAccount.getHolderName(),
                senderAccount.getAccountNo(),
                printContent,
                req.getGroupId(),
                paymentProofId
        );

        String leaderLast4 = leaderBankAccount.getAccountNo().length() >= 4
                ? leaderBankAccount.getAccountNo().substring(leaderBankAccount.getAccountNo().length() - 4)
                : leaderBankAccount.getAccountNo();

        return TransferToLeaderResponse.builder()
                .rspCode("A0000")
                .rspMessage("모임장 계좌로 이체가 완료되었습니다. 납입내역에 '확인중'으로 표시되며 증빙 확인 후 최종 확정됩니다.")
                .bankTranId(bankTranId)
                .paymentProofId(paymentProofId)
                .proofStatus("PENDING")
                .proofStatusKor("확인중")
                .groupId(req.getGroupId())
                .senderMemberId(req.getMemberId())
                .senderName(senderAccount.getHolderName())
                .receiverBankName(leaderBankAccount.getBankName())
                .receiverAccountLast4(leaderLast4)
                .receiverHolderName(leaderBankAccount.getHolderName())
                .tranAmt(req.getAmount())
                .senderAfterBalanceAmt(senderAfterBalance)
                .tranDateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .build();
    }

    /**
     * 5. 모임 납입/거래내역 목록 조회 (납입내역 탭 확인 시 호출)
     */
    @Transactional(readOnly = true)
    public List<GroupPaymentHistoryItem> getGroupPaymentHistory(Long groupId, String payMonth) {
        return openBankingMapper.selectGroupPaymentHistoryList(groupId, payMonth);
    }

    /**
     * [핵심 보안 로직] groupId로 GYE_ACCOUNT 조회 후 AES-256-GCM 복호화 + HMAC-SHA256 해시 동시 검증
     */
    private MockAccountVO resolveAndVerifyLeaderAccount(Long groupId) {
        EncryptedGyeAccountVO gyeAccount = openBankingMapper.selectCurrentGyeAccountByGroupId(groupId);
        if (gyeAccount == null) {
            throw new IllegalStateException("해당 모임에 등록된 활성 계좌(CURRENT)가 없습니다.");
        }

        // 1. KEY_VER에 맞는 DEK로 계좌번호 및 예금주명 AES-256-GCM 복호화
        int keyVer = (gyeAccount.getKeyVer() != null) ? gyeAccount.getKeyVer() : 1;
        String decryptedAccountNo = cryptoProvider.decrypt(gyeAccount.getAccountNoEnc(), keyVer);
        String decryptedHolderName = cryptoProvider.decrypt(gyeAccount.getHolderNameEnc(), keyVer);

        String cleanLeaderAcctNo = normalizeAccountNo(decryptedAccountNo);

        // 2. 복호화된 계좌번호로 HMAC-SHA256 해시를 생성하여 DB의 ACCOUNT_NO_HASH와 일치하는지 이중 검증
        String calculatedHash = cryptoProvider.hmacHash(cleanLeaderAcctNo);
        String HashWithBank = cryptoProvider.hmacHash(gyeAccount.getBankName() + cleanLeaderAcctNo);

        if (!calculatedHash.equalsIgnoreCase(gyeAccount.getAccountNoHash().trim())
                && !HashWithBank.equalsIgnoreCase(gyeAccount.getAccountNoHash().trim())) {
            throw new SecurityException("모임장 계좌의 암호문 복호화 값과 HMAC-SHA256 해시가 일치하지 않습니다. (계좌 무결성 검증 실패)");
        }

        // 3. 검증을 통과한 모임장 계좌가 가상 은행 원장(MOCK_BANK_ACCOUNT)에 존재하는지 확인
        MockAccountVO leaderMockAccount = openBankingMapper.selectMockAccountByBankAndNo(
                gyeAccount.getBankName().trim(), cleanLeaderAcctNo
        );

        // 만약 모임장이 GYE_ACCOUNT에만 등록해두고 아직 MOCK_BANK_ACCOUNT에 적재되지 않았다면 자동 개설(0원)
        if (leaderMockAccount == null) {
            String fintechNum = generateFintechUseNum("088", cleanLeaderAcctNo);
            openBankingMapper.upsertMockBankAccount(
                    null,
                    fintechNum,
                    "088",
                    gyeAccount.getBankName().trim(),
                    cleanLeaderAcctNo,
                    decryptedHolderName.trim(),
                    0L,
                    "GYE_AUTO_SYNC"
            );
            leaderMockAccount = openBankingMapper.selectMockAccountByBankAndNo(
                    gyeAccount.getBankName().trim(), cleanLeaderAcctNo
            );
        }

        return leaderMockAccount;
    }

    private String normalizeAccountNo(String accountNo) {
        if (accountNo == null) return "";
        return accountNo.replaceAll("[^0-9]", "");
    }

    private String generateFintechUseNum(String bankCode, String cleanAccountNo) {
        String raw = "199" + bankCode + cleanAccountNo + "000000000000000000000000";
        return raw.substring(0, 24);
    }
}