package com.moava.group.procedures.mapper;

import com.moava.group.procedures.dto.PayoutOrderDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PayoutOrderMapper {

    // 모임 최대 인원 및 상태 조회 (GYE_GROUP)
    Map<String, Object> selectGroupInfo(@Param("groupId") Long groupId);

    // 회원의 현재 진행 중(REQUESTED / CONSENT_PENDING)인 신청 건 조회
    Map<String, Object> selectActiveRequestByMember(@Param("groupId") Long groupId, @Param("memberId") Long memberId);

    // 단건 신청 정보 조회
    Map<String, Object> selectPayoutRequestById(@Param("payoutRequestId") Long payoutRequestId);

    // AI 제안 회차 산출: 모임원 연체 확률(RISK_SCORE) 내림차순 중 해당 회원의 추천 순번 조회
    Integer selectAiSuggestedRound(@Param("groupId") Long groupId, @Param("memberId") Long memberId);

    // 1. [신청] 신규 수령 순서 신청 등록 (PAYOUT_REQUEST)
    void insertPayoutRequest(@Param("groupId") Long groupId,
                             @Param("memberId") Long memberId,
                             @Param("roundNo") Integer roundNo,
                             @Param("reason") String reason,
                             @Param("aiSuggestedRound") Integer aiSuggestedRound);

    // 1-2. [수정] 기존 REQUESTED 건 회차·사유 갱신
    void updatePayoutRequest(@Param("payoutRequestId") Long payoutRequestId,
                             @Param("roundNo") Integer roundNo,
                             @Param("reason") String reason,
                             @Param("aiSuggestedRound") Integer aiSuggestedRound);

    // 기존 동의 내역 삭제 (수정 시 재동의 유도)
    void deleteConsentsByRequestId(@Param("payoutRequestId") Long payoutRequestId);

    // 상태 변경 (REQUESTED -> CONSENT_PENDING / CONFIRMED / REJECTED)
    void updatePayoutRequestStatus(@Param("payoutRequestId") Long payoutRequestId, @Param("status") String status);

    // 신청자 제외 ACTIVE 모임원 ID 목록 조회
    List<Long> selectOtherActiveMemberIds(@Param("groupId") Long groupId, @Param("excludeMemberId") Long excludeMemberId);

    // 3. [동의 요청] 대상 회원들의 초기 CONSENT 레코드 생성
    void insertInitialConsent(@Param("groupId") Long groupId,
                              @Param("targetId") Long payoutRequestId,
                              @Param("memberId") Long memberId);

    // 4. [동의] 모임원 동의 여부(Y/N) 업데이트 (CONSENT: type='ORDER')
    int updateMemberConsent(@Param("targetId") Long payoutRequestId,
                            @Param("memberId") Long memberId,
                            @Param("agreedYn") String agreedYn);

    // 동의 집계: 대상 회원 수 및 'Y' 동의 회원 수 확인
    int countTotalActiveOtherMembers(@Param("groupId") Long groupId, @Param("applicantId") Long applicantId);
    int countAgreedConsents(@Param("targetId") Long payoutRequestId);

    // 5. [확정] 회차 중복 확정 검사 (이미 해당 회차에 CONFIRMED된 건이 있는지 확인)
    int countConfirmedRoundDuplicate(@Param("groupId") Long groupId,
                                     @Param("roundNo") Integer roundNo,
                                     @Param("excludeRequestId") Long excludeRequestId);

    // 모임원 테이블에 확정 회차 반영 및 전체 확정 여부 체크
    void updateMemberAssignedRound(@Param("groupId") Long groupId, @Param("memberId") Long memberId, @Param("roundNo") Integer roundNo);
    int countUnconfirmedActiveMembers(@Param("groupId") Long groupId);
    void updateGroupOrderConfirmedYn(@Param("groupId") Long groupId, @Param("yn") String yn);

    // 신청·동의 현황 목록 조회
    List<PayoutOrderDTO.StatusResponse> selectPayoutRequestList(@Param("groupId") Long groupId);

    // 알림 발송 (NOTIFICATION) & 감사 로그 기록 (AUDIT_LOG)
    void insertNotification(@Param("memberId") Long memberId, @Param("title") String title, @Param("body") String body);
    void insertAuditLog(@Param("groupId") Long groupId, @Param("actorId") Long actorId, @Param("actionType") String actionType, @Param("detail") String detail);
}