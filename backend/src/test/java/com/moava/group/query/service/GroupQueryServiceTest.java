package com.moava.group.query.service;

import com.moava.group.map.GeoPoint;
import com.moava.group.map.KakaoGeocodingClient;
import com.moava.group.query.dto.PublicGroupResponse;
import com.moava.group.query.dto.PublicGroupRow;
import com.moava.group.query.mapper.GroupQueryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** F11~F14 공개 모임 목록·지도 조회 서비스 단위 테스트 */
@ExtendWith(MockitoExtension.class)
class GroupQueryServiceTest {

    @Mock
    GroupQueryMapper mapper;

    @Mock
    KakaoGeocodingClient kakaoGeocodingClient;

    GroupQueryService service;

    @BeforeEach
    void setUp() {
        service = new GroupQueryService(mapper, kakaoGeocodingClient);
    }

    private static PublicGroupRow sampleRow(
            Long groupId,
            Long ownerId,
            String ownerName,
            String regionDong,
            BigDecimal lat,
            BigDecimal lng,
            String orderConfirmedYn,
            String role
    ) {
        return new PublicGroupRow(
                groupId,
                "마포 목돈 모임",
                ownerId,
                ownerName,
                "RECRUITING",
                "SAVINGS",
                "서울특별시",
                "마포구",
                regionDong,
                lat,
                lng,
                200_000L,
                6,
                2,
                6,
                5,
                new BigDecimal("3.0"),
                "PUBLIC",
                "마포구 직장인 저축 모임",
                "우리 모임 규칙",
                "매월 5일 납입합니다.",
                orderConfirmedYn,
                role
        );
    }

    @Test
    @DisplayName("전체/공백 필터는 null로 정규화되고, 한글 목적 라벨은 코드로 변환되며 LIKE 특수문자는 이스케이프된다")
    void normalizesFiltersAndEscapesLike() {
        when(mapper.findPublicGroups(10L, "마포\\%\\_구", "SAVINGS", "100\\%목돈"))
                .thenReturn(List.of());

        List<PublicGroupResponse> res = service.getPublicGroups(
                10L, " 마포%_구 ", "목돈 마련", " 100%목돈 "
        );

        assertTrue(res.isEmpty());
        verify(mapper).findPublicGroups(10L, "마포\\%\\_구", "SAVINGS", "100\\%목돈");
    }

    @Test
    @DisplayName("지역/목적이 '전체'이거나 공백이면 null을 매퍼에 전달한다")
    void allFilterBecomesNull() {
        when(mapper.findPublicGroups(10L, null, null, null)).thenReturn(List.of());

        service.getPublicGroups(10L, "전체", "전체", "   ");

        verify(mapper).findPublicGroups(10L, null, null, null);
    }

    @Test
    @DisplayName("타인이 조회하면 모임장 이름을 마스킹하고, 본인이 모임장이면 원문을 유지하며 regionDong을 반환한다")
    void masksOwnerNameForNonOwnerOnly() {
        PublicGroupRow othersGroup = sampleRow(
                101L, 2L, "김하늘", "합정동",
                new BigDecimal("37.5663000"), new BigDecimal("126.9018000"),
                "Y", "MEMBER"
        );
        PublicGroupRow ownGroup = sampleRow(
                102L, 10L, "이모아", null,
                new BigDecimal("37.5663000"), new BigDecimal("126.9018000"),
                "N", "OWNER"
        );
        when(mapper.findPublicGroups(10L, null, null, null))
                .thenReturn(List.of(othersGroup, ownGroup));

        List<PublicGroupResponse> res = service.getPublicGroups(10L, null, null, null);

        assertEquals(2, res.size());

        PublicGroupResponse first = res.get(0);
        assertEquals("김○늘", first.ownerName());
        assertEquals("합정동", first.regionDong());
        assertTrue(first.orderConfirmed());
        assertEquals("MEMBER", first.role());
        assertEquals("우리 모임 규칙", first.rulesTitle());
        assertEquals("매월 5일 납입합니다.", first.rulesBody());
        assertEquals(new BigDecimal("37.5663000"), first.lat());
        assertEquals(new BigDecimal("126.9018000"), first.lng());

        PublicGroupResponse second = res.get(1);
        assertEquals("이모아", second.ownerName());
        assertNull(second.regionDong());
        assertFalse(second.orderConfirmed());
        assertEquals("OWNER", second.role());

        verifyNoInteractions(kakaoGeocodingClient);
    }

    @Test
    @DisplayName("LAT/LNG가 NULL인 기존 DB 행은 동 단위까지 지오코딩 폴백으로 보정하고 동일 지역은 캐시한다")
    void geocodesAndCachesMissingCoordinates() {
        PublicGroupRow missing1 = sampleRow(201L, 2L, "김하늘", "합정동", null, null, "N", "NONE");
        PublicGroupRow missing2 = sampleRow(202L, 3L, "박철수", "합정동", null, null, "N", "NONE");
        PublicGroupRow missing3 = sampleRow(203L, 4L, "최영희", "서교동", null, null, "N", "NONE");
        when(mapper.findPublicGroups(10L, null, null, null))
                .thenReturn(List.of(missing1, missing2, missing3));
        when(kakaoGeocodingClient.geocodeRegion("서울특별시", "마포구", "합정동"))
                .thenReturn(Optional.of(new GeoPoint(
                        new BigDecimal("37.5495072"),
                        new BigDecimal("126.9139405")
                )));
        when(kakaoGeocodingClient.geocodeRegion("서울특별시", "마포구", "서교동"))
                .thenReturn(Optional.of(new GeoPoint(
                        new BigDecimal("37.5550000"),
                        new BigDecimal("126.9220000")
                )));

        List<PublicGroupResponse> res = service.getPublicGroups(10L, null, null, null);

        assertEquals(3, res.size());
        assertEquals(new BigDecimal("37.5495072"), res.get(0).lat());
        assertEquals(new BigDecimal("126.9139405"), res.get(0).lng());
        assertEquals(new BigDecimal("37.5495072"), res.get(1).lat());
        assertEquals(new BigDecimal("126.9139405"), res.get(1).lng());
        assertEquals(new BigDecimal("37.5550000"), res.get(2).lat());
        assertEquals(new BigDecimal("126.9220000"), res.get(2).lng());
        verify(kakaoGeocodingClient, times(1)).geocodeRegion("서울특별시", "마포구", "합정동");
        verify(kakaoGeocodingClient, times(1)).geocodeRegion("서울특별시", "마포구", "서교동");
    }

    @Test
    @DisplayName("지오코딩 실패 시에도 예외 없이 lat/lng null로 응답을 반환한다")
    void returnsNullCoordsWhenGeocodingFails() {
        PublicGroupRow missing = sampleRow(301L, 2L, "김하늘", null, null, null, "N", "NONE");
        when(mapper.findPublicGroups(10L, null, null, null))
                .thenReturn(List.of(missing));
        when(kakaoGeocodingClient.geocodeRegion("서울특별시", "마포구", null))
                .thenReturn(Optional.empty());

        List<PublicGroupResponse> res = service.getPublicGroups(10L, null, null, null);

        assertEquals(1, res.size());
        assertNull(res.get(0).lat());
        assertNull(res.get(0).lng());
    }

    @Test
    @DisplayName("F12: ES 검색 성공 시 반환된 groupId 순서대로 DB 상세를 조회·정렬해 반환하고 DB LIKE 검색은 호출하지 않는다")
    void usesElasticsearchSearchAndPreservesHitOrder() {
        com.moava.group.search.GroupElasticsearchClient esClient =
                mock(com.moava.group.search.GroupElasticsearchClient.class);
        GroupQueryService esService = new GroupQueryService(mapper, kakaoGeocodingClient, esClient);

        PublicGroupRow row101 = sampleRow(
                101L, 2L, "김하늘", "합정동",
                new BigDecimal("37.5495072"), new BigDecimal("126.9139405"),
                "Y", "MEMBER"
        );
        PublicGroupRow row205 = sampleRow(
                205L, 10L, "이모아", "서교동",
                new BigDecimal("37.5550000"), new BigDecimal("126.9220000"),
                "N", "OWNER"
        );

        when(esClient.isEnabled()).thenReturn(true);
        when(esClient.searchPublicGroupIds("합정동", "SAVINGS", "목돈"))
                .thenReturn(Optional.of(List.of(205L, 101L)));
        when(mapper.findPublicGroupsByIds(10L, List.of(205L, 101L)))
                .thenReturn(List.of(row101, row205));

        List<PublicGroupResponse> res = esService.getPublicGroups(10L, "합정동", "목돈 마련", "목돈");

        assertEquals(2, res.size());
        assertEquals(205L, res.get(0).groupId());
        assertEquals(101L, res.get(1).groupId());
        verify(mapper, never()).findPublicGroups(any(), any(), any(), any());
    }

    @Test
    @DisplayName("F12: ES 검색이 정상적으로 0건을 반환하면 DB LIKE 폴백 없이 빈 리스트를 반환한다")
    void returnsEmptyListWithoutDbFallbackWhenElasticsearchReturnsZeroHits() {
        com.moava.group.search.GroupElasticsearchClient esClient =
                mock(com.moava.group.search.GroupElasticsearchClient.class);
        GroupQueryService esService = new GroupQueryService(mapper, kakaoGeocodingClient, esClient);

        when(esClient.isEnabled()).thenReturn(true);
        when(esClient.searchPublicGroupIds(null, null, "없는모임"))
                .thenReturn(Optional.of(List.of()));

        List<PublicGroupResponse> res = esService.getPublicGroups(10L, "전체", "전체", "없는모임");

        assertTrue(res.isEmpty());
        verify(mapper, never()).findPublicGroupsByIds(any(), any());
        verify(mapper, never()).findPublicGroups(any(), any(), any(), any());
    }

    @Test
    @DisplayName("F12: ES 장애로 Optional.empty()가 반환되면 DB LIKE 검색으로 격리 폴백한다")
    void fallsBackToDbLikeSearchWhenElasticsearchFails() {
        com.moava.group.search.GroupElasticsearchClient esClient =
                mock(com.moava.group.search.GroupElasticsearchClient.class);
        GroupQueryService esService = new GroupQueryService(mapper, kakaoGeocodingClient, esClient);

        PublicGroupRow fallbackRow = sampleRow(
                305L, 2L, "김하늘", "합정동",
                new BigDecimal("37.5495072"), new BigDecimal("126.9139405"),
                "N", "NONE"
        );

        when(esClient.isEnabled()).thenReturn(true);
        when(esClient.searchPublicGroupIds("마포구", "SAVINGS", "100%목돈"))
                .thenReturn(Optional.empty());
        when(mapper.findPublicGroups(10L, "마포구", "SAVINGS", "100\\%목돈"))
                .thenReturn(List.of(fallbackRow));

        List<PublicGroupResponse> res = esService.getPublicGroups(10L, "마포구", "목돈 마련", "100%목돈");

        assertEquals(1, res.size());
        assertEquals(305L, res.get(0).groupId());
        verify(mapper).findPublicGroups(10L, "마포구", "SAVINGS", "100\\%목돈");
    }

    @Test
    @DisplayName("지도·공개 모임 목록 조회 시 모집 마감(ACTIVE) 또는 정원이 가득 찬 모임은 제외된다")
    void excludesClosedAndFullGroupsFromPublicMapList() {
        when(mapper.findPublicGroups(10L, null, null, null)).thenReturn(List.of(
                recruitingRow(1L, 200_000L, 6, 3, "RECRUITING", "PUBLIC", "NONE"),
                recruitingRow(2L, 200_000L, 6, 4, "ACTIVE", "PUBLIC", "NONE"),
                recruitingRow(3L, 200_000L, 6, 6, "RECRUITING", "PUBLIC", "NONE")
        ));

        List<PublicGroupResponse> res = service.getPublicGroups(10L, null, null, null);

        assertEquals(1, res.size());
        assertEquals(1L, res.get(0).groupId());
    }

    private static PublicGroupRow recruitingRow(
            Long groupId,
            Long amount,
            int maxMembers,
            int currentMembers,
            String status,
            String visibility,
            String role
    ) {
        return new PublicGroupRow(
                groupId,
                "추천 모임 " + groupId,
                2L,
                "김하늘",
                status,
                "SAVINGS",
                "서울특별시",
                "마포구",
                "합정동",
                new BigDecimal("37.5495072"),
                new BigDecimal("126.9139405"),
                amount,
                maxMembers,
                currentMembers,
                6,
                5,
                new BigDecimal("3.0"),
                visibility,
                "마포구 직장인 저축 모임",
                "우리 모임 규칙",
                "매월 5일 납입합니다.",
                "N",
                role
        );
    }

    @Test
    @DisplayName("F11: 동일 시/군/구에서 비공개·마감 모임을 제외하고 추천액 차이 작은 순 → 건강도 높은 순으로 최대 3개를 추천한다")
    void recommendsTop3ByAmountDiffThenHealthScoreExcludingClosedOrPrivate() {
        com.moava.group.search.GroupElasticsearchClient esClient =
                mock(com.moava.group.search.GroupElasticsearchClient.class);
        GroupQueryService esService = new GroupQueryService(mapper, kakaoGeocodingClient, esClient);

        when(mapper.findMemberRegionById(10L))
                .thenReturn(new com.moava.group.query.dto.MemberRegionRow("서울특별시", "마포구", "합정동"));
        when(esClient.isEnabled()).thenReturn(true);
        // 추천액 20만 원 -> 상한 max(30만, 20만) = 30만 원
        when(esClient.searchRecommendedGroupIds(300_000L, "서울특별시", "마포구"))
                .thenReturn(Optional.of(List.of(1L, 2L, 3L, 4L, 5L, 6L)));

        when(mapper.findPublicGroupsByIds(10L, List.of(1L, 2L, 3L, 4L, 5L, 6L)))
                .thenReturn(List.of(
                        recruitingRow(1L, 200_000L, 6, 2, "RECRUITING", "PUBLIC", "NONE"),    // diff 0, health 75
                        recruitingRow(2L, 200_000L, 6, 2, "RECRUITING", "PUBLIC", "MEMBER"),  // diff 0, health 95 -> 1위
                        recruitingRow(3L, 250_000L, 6, 2, "RECRUITING", "PUBLIC", "NONE"),    // diff 5만, health 90 -> 3위
                        recruitingRow(4L, 300_000L, 6, 2, "RECRUITING", "PUBLIC", "NONE"),    // diff 10만 -> 4위(제외)
                        recruitingRow(5L, 200_000L, 6, 6, "RECRUITING", "PUBLIC", "NONE"),    // 정원 마감 -> 제외
                        recruitingRow(6L, 200_000L, 6, 2, "RECRUITING", "PRIVATE", "NONE")    // 비공개 -> 제외
                ));
        when(mapper.findGroupHealthScoresByIds(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(
                        new com.moava.group.query.dto.GroupHealthRow(1L, 75),
                        new com.moava.group.query.dto.GroupHealthRow(2L, 95),
                        new com.moava.group.query.dto.GroupHealthRow(3L, 90),
                        new com.moava.group.query.dto.GroupHealthRow(4L, 99)
                ));

        com.moava.group.query.dto.GroupRecommendationResponse res =
                esService.getRecommendedGroups(10L, 200_000L, null, null);

        assertEquals(200_000L, res.recommendedAmount());
        assertEquals(300_000L, res.maxAmount());
        assertEquals("SIGUNGU", res.matchedScope());
        assertFalse(res.expandedToSido());
        assertEquals(3, res.items().size());
        assertEquals(2L, res.items().get(0).groupId());
        assertEquals(95, res.items().get(0).healthScore());
        assertEquals("MEMBER", res.items().get(0).role());
        assertEquals(1L, res.items().get(1).groupId());
        assertEquals(3L, res.items().get(2).groupId());
    }

    @Test
    @DisplayName("F11: 동일 시/군/구 0건이면 시/도 전체로 확대 재검색하며 expandedToSido=true 를 반환한다")
    void expandsRecommendationToSidoWhenSigunguEmpty() {
        com.moava.group.search.GroupElasticsearchClient esClient =
                mock(com.moava.group.search.GroupElasticsearchClient.class);
        GroupQueryService esService = new GroupQueryService(mapper, kakaoGeocodingClient, esClient);

        when(mapper.findMemberRegionById(10L))
                .thenReturn(new com.moava.group.query.dto.MemberRegionRow("서울특별시", "마포구", "합정동"));
        when(esClient.isEnabled()).thenReturn(true);
        // 추천액 5만 원 -> 상한 max(15만, 20만) = 20만 원
        when(esClient.searchRecommendedGroupIds(200_000L, "서울특별시", "마포구"))
                .thenReturn(Optional.of(List.of()));
        when(esClient.searchRecommendedGroupIds(200_000L, "서울특별시", null))
                .thenReturn(Optional.of(List.of(201L)));
        when(mapper.findPublicGroupsByIds(10L, List.of(201L)))
                .thenReturn(List.of(recruitingRow(201L, 150_000L, 6, 3, "RECRUITING", "PUBLIC", "NONE")));
        when(mapper.findGroupHealthScoresByIds(List.of(201L)))
                .thenReturn(List.of(new com.moava.group.query.dto.GroupHealthRow(201L, 88)));

        com.moava.group.query.dto.GroupRecommendationResponse res =
                esService.getRecommendedGroups(10L, 50_000L, null, null);

        assertEquals(200_000L, res.maxAmount());
        assertEquals("SIDO", res.matchedScope());
        assertTrue(res.expandedToSido());
        assertEquals(1, res.items().size());
        assertEquals(201L, res.items().get(0).groupId());
    }

    @Test
    @DisplayName("F11: ES 장애 시 DB 폴백으로 추천 후보를 조회하고, 시/도 확대 후에도 0건이면 NONE을 반환한다")
    void fallsBackToDbWhenEsFailsForRecommendations() {
        com.moava.group.search.GroupElasticsearchClient esClient =
                mock(com.moava.group.search.GroupElasticsearchClient.class);
        GroupQueryService esService = new GroupQueryService(mapper, kakaoGeocodingClient, esClient);

        when(mapper.findMemberRegionById(10L))
                .thenReturn(new com.moava.group.query.dto.MemberRegionRow("부산광역시", "해운대구", "우동"));
        when(esClient.isEnabled()).thenReturn(true);
        when(esClient.searchRecommendedGroupIds(200_000L, "부산광역시", "해운대구"))
                .thenReturn(Optional.empty());
        when(esClient.searchRecommendedGroupIds(200_000L, "부산광역시", null))
                .thenReturn(Optional.empty());
        when(mapper.findRecruitingGroupsForRecommendation(10L, 200_000L, "부산광역시", "해운대구"))
                .thenReturn(List.of());
        when(mapper.findRecruitingGroupsForRecommendation(10L, 200_000L, "부산광역시", null))
                .thenReturn(List.of());

        com.moava.group.query.dto.GroupRecommendationResponse res =
                esService.getRecommendedGroups(10L, 0L, null, null);

        assertEquals("NONE", res.matchedScope());
        assertTrue(res.expandedToSido());
        assertTrue(res.items().isEmpty());
    }
}
