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
    @DisplayName("타인이 조회하면 모임장 이름을 마스킹하고, 본인이 모임장이면 원문을 유지한다")
    void masksOwnerNameForNonOwnerOnly() {
        PublicGroupRow othersGroup = sampleRow(
                101L, 2L, "김하늘",
                new BigDecimal("37.5663000"), new BigDecimal("126.9018000"),
                "Y", "MEMBER"
        );
        PublicGroupRow ownGroup = sampleRow(
                102L, 10L, "이모아",
                new BigDecimal("37.5663000"), new BigDecimal("126.9018000"),
                "N", "OWNER"
        );
        when(mapper.findPublicGroups(10L, null, null, null))
                .thenReturn(List.of(othersGroup, ownGroup));

        List<PublicGroupResponse> res = service.getPublicGroups(10L, null, null, null);

        assertEquals(2, res.size());

        PublicGroupResponse first = res.get(0);
        assertEquals("김○늘", first.ownerName());
        assertTrue(first.orderConfirmed());
        assertEquals("MEMBER", first.role());
        assertEquals("우리 모임 규칙", first.rulesTitle());
        assertEquals("매월 5일 납입합니다.", first.rulesBody());
        assertEquals(new BigDecimal("37.5663000"), first.lat());
        assertEquals(new BigDecimal("126.9018000"), first.lng());

        PublicGroupResponse second = res.get(1);
        assertEquals("이모아", second.ownerName());
        assertFalse(second.orderConfirmed());
        assertEquals("OWNER", second.role());

        verifyNoInteractions(kakaoGeocodingClient);
    }

    @Test
    @DisplayName("LAT/LNG가 NULL인 기존 DB 행은 지오코딩 폴백으로 보정하고 동일 지역은 캐시한다")
    void geocodesAndCachesMissingCoordinates() {
        PublicGroupRow missing1 = sampleRow(201L, 2L, "김하늘", null, null, "N", "NONE");
        PublicGroupRow missing2 = sampleRow(202L, 3L, "박철수", null, null, "N", "NONE");
        when(mapper.findPublicGroups(10L, null, null, null))
                .thenReturn(List.of(missing1, missing2));
        when(kakaoGeocodingClient.geocodeRegion("서울특별시", "마포구"))
                .thenReturn(Optional.of(new GeoPoint(
                        new BigDecimal("37.5663245"),
                        new BigDecimal("126.9018489")
                )));

        List<PublicGroupResponse> res = service.getPublicGroups(10L, null, null, null);

        assertEquals(2, res.size());
        assertEquals(new BigDecimal("37.5663245"), res.get(0).lat());
        assertEquals(new BigDecimal("126.9018489"), res.get(0).lng());
        assertEquals(new BigDecimal("37.5663245"), res.get(1).lat());
        assertEquals(new BigDecimal("126.9018489"), res.get(1).lng());
        verify(kakaoGeocodingClient, times(1)).geocodeRegion("서울특별시", "마포구");
    }

    @Test
    @DisplayName("지오코딩 실패 시에도 예외 없이 lat/lng null로 응답을 반환한다")
    void returnsNullCoordsWhenGeocodingFails() {
        PublicGroupRow missing = sampleRow(301L, 2L, "김하늘", null, null, "N", "NONE");
        when(mapper.findPublicGroups(10L, null, null, null))
                .thenReturn(List.of(missing));
        when(kakaoGeocodingClient.geocodeRegion("서울특별시", "마포구"))
                .thenReturn(Optional.empty());

        List<PublicGroupResponse> res = service.getPublicGroups(10L, null, null, null);

        assertEquals(1, res.size());
        assertNull(res.get(0).lat());
        assertNull(res.get(0).lng());
    }
}
