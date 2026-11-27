package com.moava.group.search.dto;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * F11/F12 — Elasticsearch {@code moava_group} 인덱스에 저장되는 공개 모임 문서.
 */
public record GroupSearchDocument(
        Long groupId,
        String title,
        Long ownerId,
        String status,
        String purpose,
        String visibility,
        String regionSido,
        String regionSigungu,
        String regionDong,
        String regionFull,
        Long amount,
        Integer maxMembers,
        Integer currentMembers,
        BigDecimal lat,
        BigDecimal lng,
        String description
) {
    public static GroupSearchDocument fromRow(GroupSearchIndexRow row) {
        String sido = trimToNull(row.regionSido());
        String sigungu = trimToNull(row.regionSigungu());
        String dong = trimToNull(row.regionDong());
        String regionFull = Stream.of(sido, sigungu, dong)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));

        return new GroupSearchDocument(
                row.groupId(),
                trimToNull(row.title()),
                row.ownerId(),
                trimToNull(row.status()),
                trimToNull(row.purpose()),
                trimToNull(row.visibility()),
                sido,
                sigungu,
                dong,
                regionFull.isEmpty() ? null : regionFull,
                row.amount(),
                row.maxMembers(),
                row.currentMembers(),
                row.lat(),
                row.lng(),
                trimToNull(row.description())
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
