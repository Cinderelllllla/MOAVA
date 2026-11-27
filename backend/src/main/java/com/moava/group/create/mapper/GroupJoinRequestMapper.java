package com.moava.group.create.mapper;

import com.moava.group.create.dto.GroupPendingJoinRequestResponse;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface GroupJoinRequestMapper {

    // ==========================================
    // F16 - 모임 가입 신청
    // ==========================================

    Map<String, Object> lockGroup(
            @Param("groupId") Long groupId
    );

    // 플랫폼 가입 제한 및 회원 상태 확인
    Map<String, Object> findMemberStatus(
            @Param("memberId") Long memberId
    );

    // 기존 참여 이력 확인
    // ACTIVE, WITHDRAWN, EXPELLED 모두 가입 제한
    int countExistingMembership(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    int countPendingRequest(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    int countCurrentMembers(
            @Param("groupId") Long groupId
    );

    int countValidVerification(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    Long nextJoinRequestId();

    int insertJoinRequest(
            @Param("joinRequestId") Long joinRequestId,
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("via") String via,
            @Param("codeVerifiedYn") String codeVerifiedYn
    );

    // ==========================================
    // F16 - 모임장 가입 신청 목록 조회
    // ==========================================

    Map<String, Object> findGroup(
            @Param("groupId") Long groupId
    );

    List<GroupPendingJoinRequestResponse> findPendingRequests(
            @Param("groupId") Long groupId
    );

    // ==========================================
    // F16 - 모임장 가입 승인
    // ==========================================

    Map<String, Object> findJoinRequestForDecision(
            @Param("groupId") Long groupId,
            @Param("requestId") Long requestId
    );

    int approveJoinRequest(
            @Param("groupId") Long groupId,
            @Param("requestId") Long requestId,
            @Param("ownerId") Long ownerId
    );

    // 신규 회원 등록
    int insertApprovedMember(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    // ==========================================
    // F16 - 모임장 가입 신청 거절
    // ==========================================

    int rejectJoinRequest(
            @Param("groupId") Long groupId,
            @Param("requestId") Long requestId,
            @Param("ownerId") Long ownerId
    );

    String findMemberName(
            @Param("memberId") Long memberId
    );

    // ==========================================
    // F16 - 가입 신청 7일 자동 만료
    // ==========================================

    // 만료 대상 가입 신청 ID 목록 조회
    List<Long> findExpiredPendingRequestIds();

    // 만료 대상 신청 및 알림 정보 조회
    Map<String, Object> findExpiredPendingRequest(
            @Param("requestId") Long requestId
    );

    // 만료 조건을 충족한 신청만 EXPIRED로 변경
    int expireJoinRequest(
            @Param("requestId") Long requestId
    );

    // ==========================================
    // F16 - 가입 관련 감사 로그
    // ==========================================

    int insertAuditLog(
            @Param("groupId") Long groupId,
            @Param("actorMemberId") Long actorMemberId,
            @Param("targetMemberId") Long targetMemberId,
            @Param("eventType") String eventType,
            @Param("description") String description
    );
}
