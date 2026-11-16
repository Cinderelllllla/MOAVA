package com.moava.group.procedures.mapper;

import com.moava.group.procedures.dto.WithdrawalDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface WithdrawalMapper {

    // 모임 정보 및 모임장(OWNER_ID) 조회
    Map<String, Object> selectGroupInfo(@Param("groupId") Long groupId);

    // 모임원의 참여 상태 및 기수령 여부(PAYOUT_RECEIVED_YN) 조회
    Map<String, Object> selectGyeMemberInfo(@Param("groupId") Long groupId, @Param("memberId") Long memberId);

    // 진행 중(SETTLEMENT_PENDING, CONSENT_PENDING)인 탈퇴 신청 중복 확인
    int countActiveWithdrawalByMember(@Param("groupId") Long groupId, @Param("memberId") Long memberId);

    // 단건 탈퇴 신청 조회
    Map<String, Object> selectWithdrawalById(@Param("withdrawalRequestId") Long withdrawalRequestId);

    // 1. [신청] 중도 탈퇴 신청 등록 (WITHDRAWAL_REQUEST)
    void insertWithdrawalRequest(@Param("groupId") Long groupId,
                                 @Param("memberId") Long memberId,
                                 @Param("reasonType") String reasonType,
                                 @Param("detail") String detail,
                                 @Param("specialYn") String specialYn);

    // 2. [정산안] 모임장 정산 금액·근거 등록 및 CONSENT_PENDING 전환
    void updateSettlementPlan(@Param("withdrawalRequestId") Long withdrawalRequestId,
                              @Param("settlementAmount") Long settlementAmount,
                              @Param("settlementMemo") String settlementMemo,
                              @Param("leaderId") Long leaderId);

    // 정산안 재등록 시 기존 정산 동의 내역 초기화
    void deleteSettlementConsents(@Param("withdrawalRequestId") Long withdrawalRequestId);

    // 3. [동의] 나머지 회원 정산안 동의 저장 (CONSENT: type='SETTLEMENT')
    void upsertSettlementConsent(@Param("groupId") Long groupId,
                                 @Param("targetId") Long withdrawalRequestId,
                                 @Param("memberId") Long memberId,
                                 @Param("agreedYn") String agreedYn);

    // 4. [재확인] 신청자 탈퇴 재확인 요청 횟수 +1
    void incrementReconfirmCount(@Param("withdrawalRequestId") Long withdrawalRequestId);

    // 5. [승인] 전원 동의 검증용 카운트 조회
    int countRemainingActiveMembers(@Param("groupId") Long groupId, @Param("applicantId") Long applicantId);
    int countAgreedSettlementConsents(@Param("targetId") Long withdrawalRequestId);

    // 탈퇴 신청 상태 변경 (APPROVED / REJECTED)
    void updateWithdrawalStatus(@Param("withdrawalRequestId") Long withdrawalRequestId, @Param("status") String status);

    // GYE_MEMBER 상태를 WITHDRAWN으로 변경 및 LEFT_AT 기록
    void updateGyeMemberWithdrawn(@Param("groupId") Long groupId, @Param("memberId") Long memberId);

    // 신청자 제외 ACTIVE 회원 ID 목록 조회 (알림 발송용)
    List<Long> selectOtherActiveMemberIds(@Param("groupId") Long groupId, @Param("excludeMemberId") Long excludeMemberId);

    // 탈퇴 검토 및 정산안 동의 현황 목록 조회
    List<WithdrawalDTO.StatusResponse> selectWithdrawalList(@Param("groupId") Long groupId);

    // 알림 발송 (NOTIFICATION) & 감사 로그 기록 (AUDIT_LOG)
    void insertNotification(@Param("memberId") Long memberId,
                            @Param("notifType") String notifType,
                            @Param("title") String title,
                            @Param("body") String body,
                            @Param("linkPage") String linkPage,
                            @Param("refId") Long refId);

    void insertAuditLog(@Param("groupId") Long groupId,
                        @Param("actorMemberId") Long actorMemberId,
                        @Param("targetMemberId") Long targetMemberId,
                        @Param("eventType") String eventType,
                        @Param("description") String description);
}