package com.moava.group.procedures.mapper;

import com.moava.group.procedures.dto.DissolutionDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DissolutionMapper {

    // 모임 기본 정보 및 OWNER_ID, STATUS 조회
    Map<String, Object> selectGroupInfo(@Param("groupId") Long groupId);

    // 모임 행 잠금 후 상태 전이를 직렬화한다.
    Map<String, Object> lockGroupForDissolution(@Param("groupId") Long groupId);

    // 모임의 현재 진행 중(PENDING)인 해산 신청 건 조회
    Map<String, Object> selectPendingDissolutionByGroup(@Param("groupId") Long groupId);

    // 모임의 최신 해산 신청 상태 단건 조회 (운영 센터 배지용)
    DissolutionDTO.StatusResponse selectLatestDissolutionByGroup(@Param("groupId") Long groupId);

    // 관리자용 전체 해산 신청 목록 조회 (PENDING: 관리자 검토 대기 / APPROVED: 종료된 모임 기록)
    List<DissolutionDTO.StatusResponse> selectAdminDissolutionList(@Param("status") String status);

    // 1. [신청] DISSOLUTION_REQUEST(PENDING) 등록
    void insertDissolutionRequest(@Param("groupId") Long groupId,
                                  @Param("requestedBy") Long requestedBy,
                                  @Param("reason") String reason);

    // GYE_GROUP.STATUS 업데이트 (DISSOLVE_PENDING / DISSOLVED / ACTIVE)
    int updateGroupStatus(@Param("groupId") Long groupId,
                          @Param("expectedStatus") String expectedStatus,
                          @Param("status") String status);

    // 유효한 활성 ADMIN 계정인지 확인
    Integer countActiveAdmin(@Param("adminId") Long adminId);

    List<Long> selectActiveAdminIds();

    int countActiveWithdrawals(@Param("groupId") Long groupId);

    int countActivePayoutRequests(@Param("groupId") Long groupId);

    // 2. [승인 검증] 해당 모임의 미종결 증빙(PAYMENT_PROOF.STATUS != 'CONFIRMED') 건수 조회
    int countUnconfirmedProofs(@Param("groupId") Long groupId);

    // 1/N 정산 안내용: 확인된 총 납입액 합계 조회
    Long selectTotalConfirmedProofAmount(@Param("groupId") Long groupId);

    // 모임의 ACTIVE 회원 ID 목록 조회
    List<Long> selectActiveMemberIds(@Param("groupId") Long groupId);

    // DISSOLUTION_REQUEST 승인/반려 업데이트 (DECIDED_BY = FK ADMIN)
    int updateDissolutionDecision(@Param("groupId") Long groupId,
                                  @Param("dissolutionRequestId") Long dissolutionRequestId,
                                  @Param("status") String status,
                                  @Param("adminId") Long adminId);

    // 인앱 알림 등록 (NOTIFICATION)
    void insertNotification(@Param("memberId") Long memberId,
                            @Param("notifType") String notifType,
                            @Param("title") String title,
                            @Param("body") String body,
                            @Param("linkPage") String linkPage,
                            @Param("refId") Long refId);

    void insertAdminNotification(@Param("adminId") Long adminId,
                                 @Param("notifType") String notifType,
                                 @Param("title") String title,
                                 @Param("body") String body,
                                 @Param("linkPage") String linkPage,
                                 @Param("refId") Long refId);

    // 모임장 행위 감사 로그 등록 (ACTOR_MEMBER_ID 사용, ACTOR_ADMIN_ID = NULL)
    void insertMemberAuditLog(@Param("groupId") Long groupId,
                              @Param("actorMemberId") Long actorMemberId,
                              @Param("eventType") String eventType,
                              @Param("description") String description);

    // 관리자 행위 감사 로그 등록 (ACTOR_ADMIN_ID 사용, ACTOR_MEMBER_ID = NULL)
    void insertAdminAuditLog(@Param("groupId") Long groupId,
                             @Param("actorAdminId") Long actorAdminId,
                             @Param("eventType") String eventType,
                             @Param("description") String description);
}