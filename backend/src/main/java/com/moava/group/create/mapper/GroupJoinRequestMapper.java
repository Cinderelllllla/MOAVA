
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
    // F16 - 모임장 가입 승인 (신규)
    // ==========================================

    // 승인할 가입 신청 조회
    Map<String, Object> findJoinRequestForDecision(
            @Param("groupId") Long groupId,
            @Param("requestId") Long requestId
    );

    // 가입 신청 상태를 APPROVED로 변경
    int approveJoinRequest(
            @Param("groupId") Long groupId,
            @Param("requestId") Long requestId,
            @Param("ownerId") Long ownerId
    );

    // 승인된 회원을 GYE_MEMBER에 등록
    int insertApprovedMember(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );


    // F16 - 모임장 가입 신청 거절
    int rejectJoinRequest(
            @Param("groupId") Long groupId,
            @Param("requestId") Long requestId,
            @Param("ownerId") Long ownerId
    );

    // F16 - 알림에 표시할 신청자 이름 조회
    String findMemberName(
            @Param("memberId") Long memberId
    );

}
