package com.moava.group.create.service;

import com.moava.group.create.dto.GroupCreateRequest;
import com.moava.group.create.dto.GroupCreateResponse;
import com.moava.group.create.mapper.GroupCreateMapper;
import com.moava.group.map.GeoPoint;
import com.moava.group.map.KakaoGeocodingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** F15 모임 생성 시 Kakao 지오코딩 위경도 저장 연동 검증 */
@ExtendWith(MockitoExtension.class)
class GroupCreateServiceGeocodingTest {

    @Mock
    GroupCreateMapper mapper;
    @Mock
    GroupAccountCrypto crypto;
    @Mock
    KakaoGeocodingClient kakaoGeocodingClient;

    GroupCreateService service;

    @BeforeEach
    void setUp() {
        service = new GroupCreateService(mapper, crypto, kakaoGeocodingClient);
    }

    private static GroupCreateRequest sampleRequest() {
        return new GroupCreateRequest(
                "마포 목돈 모임",
                "SAVINGS",
                "서울특별시",
                "마포구",
                200_000L,
                6,
                6,
                5,
                new BigDecimal("3.0"),
                "PUBLIC",
                "우리 모임 규칙",
                "매월 5일까지 납입합니다.",
                "마포구 직장인 저축 모임",
                "ALERT_ONLY",
                "카카오뱅크",
                "3333012345678"
        );
    }

    private void stubCommonHappyPath() {
        when(mapper.findActiveMemberName(1L)).thenReturn("홍길동");
        when(mapper.nextGroupId()).thenReturn(101L);
        when(mapper.insertGroup(
                anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(),
                any(), any(),
                anyLong(), anyInt(), anyInt(), anyInt(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString()
        )).thenReturn(1);
        when(mapper.insertOwnerMember(101L, 1L)).thenReturn(1);
        when(crypto.encrypt("홍길동")).thenReturn("enc-name");
        when(crypto.encrypt("3333012345678")).thenReturn("enc-acc");
        when(crypto.hashAccountNumber("3333012345678")).thenReturn("hash-acc");
        when(crypto.keyVersion()).thenReturn(1);
        when(mapper.insertGroupAccount(anyLong(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyInt(), anyLong())).thenReturn(1);
        when(mapper.insertGroupCreateAudit(anyLong(), anyLong(), anyString())).thenReturn(1);
    }

    @Test
    void 지오코딩_성공_시_LAT와_LNG를_함께_저장한다() {
        stubCommonHappyPath();
        BigDecimal lat = new BigDecimal("37.5663245");
        BigDecimal lng = new BigDecimal("126.9018489");
        when(kakaoGeocodingClient.geocodeRegion("서울특별시", "마포구"))
                .thenReturn(Optional.of(new GeoPoint(lat, lng)));

        GroupCreateResponse res = service.createGroup(sampleRequest(), 1L);

        assertEquals(101L, res.groupId());
        verify(mapper).insertGroup(
                eq(101L), eq(1L), eq("마포 목돈 모임"), eq("SAVINGS"),
                eq("서울특별시"), eq("마포구"),
                eq(lat), eq(lng),
                eq(200_000L), eq(6), eq(6), eq(5),
                eq(new BigDecimal("3.0")), eq("PUBLIC"), anyString(),
                eq("우리 모임 규칙"), eq("매월 5일까지 납입합니다."),
                eq("마포구 직장인 저축 모임"), eq("ALERT_ONLY")
        );
    }

    @Test
    void 지오코딩이_실패해도_좌표를_null로_두고_모임_생성은_정상_완료된다() {
        stubCommonHappyPath();
        when(kakaoGeocodingClient.geocodeRegion("서울특별시", "마포구"))
                .thenReturn(Optional.empty());

        GroupCreateResponse res = service.createGroup(sampleRequest(), 1L);

        assertEquals(101L, res.groupId());
        verify(mapper).insertGroup(
                eq(101L), eq(1L), eq("마포 목돈 모임"), eq("SAVINGS"),
                eq("서울특별시"), eq("마포구"),
                isNull(), isNull(),
                eq(200_000L), eq(6), eq(6), eq(5),
                eq(new BigDecimal("3.0")), eq("PUBLIC"), anyString(),
                eq("우리 모임 규칙"), eq("매월 5일까지 납입합니다."),
                eq("마포구 직장인 저축 모임"), eq("ALERT_ONLY")
        );
    }
}
