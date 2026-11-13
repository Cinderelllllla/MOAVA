package com.moava.group.procedures.mapper;

import com.moava.group.procedures.dto.PayoutOrderDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PayoutOrderMapper {

    Map<String, Object> selectGroupInfo(@Param("groupId") Long groupId);

    int countActiveMember(@Param("groupId") Long groupId, @Param("memberId") Long memberId);

    Map<String, Object> selectActiveRequestByMember(@Param("groupId") Long groupId, @Param("memberId") Long memberId);

    Map<String, Object> selectPayoutRequestById(@Param("groupId") Long groupId,
                                                @Param("payoutRequestId") Long payoutRequestId);

    Integer selectAiSuggestedRound(@Param("groupId") Long groupId, @Param("memberId") Long memberId);

    void insertPayoutRequest(@Param("groupId") Long groupId,
                             @Param("memberId") Long memberId,
                             @Param("roundNo") Integer roundNo,
                             @Param("reason") String reason,
                             @Param("aiSuggestedRound") Integer aiSuggestedRound);

    void updatePayoutRequest(@Param("payoutRequestId") Long payoutRequestId,
                             @Param("roundNo") Integer roundNo,
                             @Param("reason") String reason,
                             @Param("aiSuggestedRound") Integer aiSuggestedRound);

    void deleteConsentsByRequestId(@Param("payoutRequestId") Long payoutRequestId);

    int updatePayoutRequestStatus(@Param("groupId") Long groupId,
                                  @Param("payoutRequestId") Long payoutRequestId,
                                  @Param("expectedStatus") String expectedStatus,
                                  @Param("status") String status);

    List<Long> selectOtherActiveMemberIds(@Param("groupId") Long groupId, @Param("excludeMemberId") Long excludeMemberId);

    void insertInitialConsent(@Param("groupId") Long groupId,
                              @Param("targetId") Long payoutRequestId,
                              @Param("memberId") Long memberId);

    int updateMemberConsent(@Param("groupId") Long groupId,
                            @Param("targetId") Long payoutRequestId,
                            @Param("memberId") Long memberId,
                            @Param("agreedYn") String agreedYn);

    int countTotalActiveOtherMembers(@Param("groupId") Long groupId, @Param("applicantId") Long applicantId);

    int countAgreedConsents(@Param("targetId") Long payoutRequestId);

    int countConfirmedRoundDuplicate(@Param("groupId") Long groupId,
                                     @Param("roundNo") Integer roundNo,
                                     @Param("excludeRequestId") Long excludeRequestId);

    int countUnconfirmedActiveMembers(@Param("groupId") Long groupId);

    void updateGroupOrderConfirmedYn(@Param("groupId") Long groupId, @Param("yn") String yn);

    List<PayoutOrderDTO.StatusResponse> selectPayoutRequestList(@Param("groupId") Long groupId);

    // NOTIF_TYPE 파라미터 추가
    void insertNotification(@Param("memberId") Long memberId,
                            @Param("notifType") String notifType,
                            @Param("title") String title,
                            @Param("body") String body);

    // EVENT_TYPE, DESCRIPTION 파라미터 매핑
    void insertAuditLog(@Param("groupId") Long groupId,
                        @Param("actorId") Long actorId,
                        @Param("eventType") String eventType,
                        @Param("description") String description);

}