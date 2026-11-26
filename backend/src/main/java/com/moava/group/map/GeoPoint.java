package com.moava.group.map;

import java.math.BigDecimal;

/**
 * 위도(lat, Kakao Local y) · 경도(lng, Kakao Local x) 좌표.
 * Oracle GYE_GROUP.LAT / LNG (NUMBER(10, 7)) 컬럼에 그대로 저장된다.
 */
public record GeoPoint(BigDecimal lat, BigDecimal lng) {
}
