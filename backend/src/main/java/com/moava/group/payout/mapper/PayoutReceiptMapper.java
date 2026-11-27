package com.moava.group.payout.mapper;

import com.moava.group.payout.dto.PayoutReceiptMemberRow;
import com.moava.group.payout.dto.PayoutReceiptTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * F28 곗돈 수령 완료 표시 매퍼.
 * - GYE_MEMBER.PAYOUT_RECEIVED_YN / PAYOUT_RECEIVED_AT 갱신
 * - AUDIT_LOG(PAYOUT_RECEIVED) 기록 (append-only)
 */
@Mapper
public interface PayoutReceiptMapper {

    /** 동시 요청(F28 중복 표시 / F29 탈퇴 신청 등) 직렬화를 위한 모임 행 잠금 */
    Long lockGroup(@Param("groupId") long groupId);

    /** 모임 기본 정보 + 특정 회원 참여/수령 상태 + 확정 회차 조회 */
    PayoutReceiptTarget selectTarget(@Param("groupId") long groupId,
                                     @Param("targetMemberId") long targetMemberId);

    /** 모임 내 ACTIVE 회원별 곗돈 수령 현황 목록 (확정 회차 오름차순) */
    List<PayoutReceiptMemberRow> selectMemberReceipts(@Param("groupId") long groupId);

    /** 조건부 갱신: ACTIVE 이고 아직 PAYOUT_RECEIVED_YN='N' 인 행만 'Y' 로 변경 */
    int markPayoutReceived(@Param("groupId") long groupId,
                           @Param("targetMemberId") long targetMemberId);

    /** AUDIT_LOG(PAYOUT_RECEIVED) 1건 기록 (CK_AUDIT_LOG_1: 회원 행위라 ACTOR_ADMIN_ID=NULL) */
    int insertAuditLog(@Param("groupId") long groupId,
                       @Param("actorMemberId") long actorMemberId,
                       @Param("targetMemberId") long targetMemberId,
                       @Param("eventType") String eventType,
                       @Param("description") String description);
}
