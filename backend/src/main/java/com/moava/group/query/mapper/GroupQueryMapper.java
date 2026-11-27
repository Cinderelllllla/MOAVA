
package com.moava.group.query.mapper;

import com.moava.group.query.dto.GroupHealthRow;
import com.moava.group.query.dto.GroupListResponse;
import com.moava.group.query.dto.GroupMemberResponse;
import com.moava.group.query.dto.MemberRegionRow;
import com.moava.group.query.dto.PublicGroupRow;
import com.moava.group.search.dto.GroupSearchIndexRow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupQueryMapper {

    // F11~F14: 공개 모임 목록·지도 조회 (VISIBILITY = 'PUBLIC' 고정 + 필터, ES 폴백 겸용)
    List<PublicGroupRow> findPublicGroups(
            @Param("memberId") Long memberId,
            @Param("region") String region,
            @Param("purpose") String purpose,
            @Param("q") String q
    );

    // F12: Elasticsearch 검색 결과 ID 목록 기준 공개 모임 상세 조회
    List<PublicGroupRow> findPublicGroupsByIds(
            @Param("memberId") Long memberId,
            @Param("groupIds") List<Long> groupIds
    );

    // F11: 추천 기준 회원 활동 지역 조회
    MemberRegionRow findMemberRegionById(
            @Param("memberId") Long memberId
    );

    // F11: ES 장애·비활성화 시 모집 중 공개 모임 추천 후보 DB 폴백 조회
    List<PublicGroupRow> findRecruitingGroupsForRecommendation(
            @Param("memberId") Long memberId,
            @Param("maxAmount") Long maxAmount,
            @Param("regionSido") String regionSido,
            @Param("regionSigungu") String regionSigungu
    );

    // F11 MOA-100: 추천에서 뺄 모임 번호 (내가 모임장 · 회원(ACTIVE·EXPELLED) · 가입 신청 중(PENDING)).
    // Elasticsearch 경로에서 쓴다. DB 경로는 findRecruitingGroupsForRecommendation 의 같은 조건으로 걸러진다
    List<Long> findGroupIdsExcludedFromRecommendation(
            @Param("memberId") Long memberId,
            @Param("groupIds") List<Long> groupIds
    );

    // F11: 모임별 건강도 점수 조회 (GYE_MEMBER + 최신 RISK_SCORE.PROB 기반)
    List<GroupHealthRow> findGroupHealthScoresByIds(
            @Param("groupIds") List<Long> groupIds
    );

    // F11/F12: Elasticsearch 초기/일괄 색인용 전체 공개 모임 조회
    List<GroupSearchIndexRow> findPublicGroupIndexRows();

    // F11/F12: Elasticsearch 단건 동기화용 모임 조회 (비공개·해산 여부 확인 포함)
    GroupSearchIndexRow findGroupIndexRowById(
            @Param("groupId") Long groupId
    );

    // 내가 모임장인 모임 조회
    List<GroupListResponse> findMyOwnedGroups(
            @Param("memberId") Long memberId
    );

    // 내가 일반 회원으로 참여 중인 모임 조회
    List<GroupListResponse> findMyJoinedGroups(
            @Param("memberId") Long memberId
    );

    // 해당 모임의 모임장 여부 확인
    int countOwnedGroup(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    // 해당 모임의 ACTIVE 참여 회원 목록 조회
    List<GroupMemberResponse> findActiveGroupMembers(
            @Param("groupId") Long groupId
    );
}
