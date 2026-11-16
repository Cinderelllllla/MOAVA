package com.moava.openbanking;

import com.moava.openbanking.OpenBankingDTO.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OpenBankingMapper {

    // 1. 은행명 + 계좌번호로 가상 은행 계좌 단건 조회
    MockAccountVO selectMockAccountByBankAndNo(
            @Param("bankName") String bankName,
            @Param("accountNo") String accountNo
    );

    // 2. CODEF 계좌 정보 적재 (이미 있으면 잔액/예금주 업데이트, 없으면 INSERT)
    int upsertMockBankAccount(
            @Param("memberId") Long memberId,
            @Param("fintechUseNum") String fintechUseNum,
            @Param("bankCode") String bankCode,
            @Param("bankName") String bankName,
            @Param("accountNo") String accountNo,
            @Param("holderName") String holderName,
            @Param("balanceAmt") Long balanceAmt,
            @Param("sourceType") String sourceType
    );

    // 3. 토이 프로젝트용 잔액 직접 수정
    int updateMockAccountBalanceDirect(
            @Param("bankName") String bankName,
            @Param("accountNo") String accountNo,
            @Param("newBalanceAmt") Long newBalanceAmt
    );

    // 4. 이체 시 계좌 잔액 증감 (+amount / -amount)
    int addAccountBalance(
            @Param("fintechUseNum") String fintechUseNum,
            @Param("deltaAmount") Long deltaAmount
    );

    // 5. 가상 은행 입출금 거래내역 기록
    int insertMockTransaction(
            @Param("bankTranId") String bankTranId,
            @Param("fintechUseNum") String fintechUseNum,
            @Param("inoutType") String inoutType,
            @Param("tranAmt") Long tranAmt,
            @Param("afterBalanceAmt") Long afterBalanceAmt,
            @Param("counterpartyName") String counterpartyName,
            @Param("counterpartyAcct") String counterpartyAcct,
            @Param("printContent") String printContent,
            @Param("groupId") Long groupId,
            @Param("paymentProofId") Long paymentProofId
    );

    // 6. 모임의 현재 활성 계좌(GYE_ACCOUNT, CURRENT) 암호문 및 해시 조회
    EncryptedGyeAccountVO selectCurrentGyeAccountByGroupId(@Param("groupId") Long groupId);

    // 7. 회원 실명 조회 (MEMBER 테이블 교차 확인용)
    String selectMemberNameById(@Param("memberId") Long memberId);

    // 8. 해당 모임·회원·귀속월·회차의 기존 PAYMENT_PROOF ID 조회
    Long selectExistingPaymentProofId(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("payMonth") String payMonth,
            @Param("roundNo") Integer roundNo
    );

    // 9. 이체 성공 시 PAYMENT_PROOF에 'PENDING(확인중)' 상태로 신규 등록
    int insertPendingPaymentProof(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("payMonth") String payMonth,
            @Param("roundNo") Integer roundNo,
            @Param("amount") Long amount,
            @Param("reviewMemo") String reviewMemo
    );

    // 10. 기존 PAYMENT_PROOF 건이 있을 경우 금액 및 상태를 'PENDING(확인중)'으로 갱신
    int updatePaymentProofToPending(
            @Param("paymentProofId") Long paymentProofId,
            @Param("amount") Long amount,
            @Param("reviewMemo") String reviewMemo
    );

    // 11. 모임별 납입/이체 거래내역 목록 조회 (확인중/완료 상태 및 유저 이름 포함)
    List<GroupPaymentHistoryItem> selectGroupPaymentHistoryList(
            @Param("groupId") Long groupId,
            @Param("payMonth") String payMonth
    );
}