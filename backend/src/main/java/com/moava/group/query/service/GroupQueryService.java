
package com.moava.group.query.service;

import com.moava.common.util.NameMasker;
import com.moava.group.map.GeoPoint;
import com.moava.group.map.KakaoGeocodingClient;
import com.moava.group.query.dto.GroupListResponse;
import com.moava.group.query.dto.GroupMemberResponse;
import com.moava.group.query.dto.PublicGroupResponse;
import com.moava.group.query.dto.PublicGroupRow;
import com.moava.group.query.mapper.GroupQueryMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GroupQueryService {

    private static final Map<String, String> PURPOSE_LABEL_TO_CODE = Map.of(
            "목돈 마련", "SAVINGS",
            "재테크", "INVEST",
            "친목", "SOCIAL",
            "창업", "STARTUP",
            "여행", "TRAVEL"
    );

    private final GroupQueryMapper groupQueryMapper;
    private final KakaoGeocodingClient kakaoGeocodingClient;
    private final ConcurrentHashMap<String, GeoPoint> regionGeoCache = new ConcurrentHashMap<>();

    @Autowired
    public GroupQueryService(
            GroupQueryMapper groupQueryMapper,
            KakaoGeocodingClient kakaoGeocodingClient
    ) {
        this.groupQueryMapper = groupQueryMapper;
        this.kakaoGeocodingClient = kakaoGeocodingClient;
    }

    public GroupQueryService(GroupQueryMapper groupQueryMapper) {
        this(groupQueryMapper, null);
    }

    @Transactional(readOnly = true)
    public List<PublicGroupResponse> getPublicGroups(
            Long memberId,
            String region,
            String purpose,
            String q
    ) {
        String escapedRegion = escapeLike(normalizeAllFilter(region));
        String normalizedPurpose = normalizePurpose(purpose);
        String escapedQuery = escapeLike(trimToNull(q));

        List<PublicGroupRow> rows = groupQueryMapper.findPublicGroups(
                memberId,
                escapedRegion,
                normalizedPurpose,
                escapedQuery
        );

        return rows.stream()
                .map(row -> toPublicGroupResponse(row, memberId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupListResponse> getMyGroups(Long memberId) {
        
        // 내가 모임장인 모임
        List<GroupListResponse> groups = new ArrayList<>(
                groupQueryMapper.findMyOwnedGroups(memberId)
        );

        // 내가 일반 회원으로 참여 중인 모임
        groups.addAll(
                groupQueryMapper.findMyJoinedGroups(memberId)
        );

        // 모임 ID 내림차순 정렬
        groups.sort(
                Comparator.comparing(
                        GroupListResponse::groupId
                ).reversed()
        );

        return groups;
    }

    // 모임장 전용 참여 회원 목록 조회
    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getGroupMembers(
            Long groupId,
            Long memberId
    ) {

        int ownedCount = groupQueryMapper.countOwnedGroup(
                groupId,
                memberId
        );

        if (ownedCount == 0) {
            throw new AccessDeniedException(
                    "모임장만 참여 회원 목록을 조회할 수 있습니다."
            );
        }

        return groupQueryMapper.findActiveGroupMembers(groupId);
    }

    private PublicGroupResponse toPublicGroupResponse(PublicGroupRow row, Long viewerMemberId) {
        BigDecimal lat = row.lat();
        BigDecimal lng = row.lng();

        if ((lat == null || lng == null) && kakaoGeocodingClient != null) {
            Optional<GeoPoint> resolved = resolveMissingCoords(row.regionSido(), row.regionSigungu());
            if (resolved.isPresent()) {
                lat = resolved.get().lat();
                lng = resolved.get().lng();
            }
        }

        String rawOwnerName = trimToNull(row.ownerName());
        String ownerName = (viewerMemberId != null && Objects.equals(viewerMemberId, row.ownerId()))
                ? (rawOwnerName != null ? rawOwnerName : "회원")
                : NameMasker.mask(rawOwnerName);

        boolean orderConfirmed = "Y".equalsIgnoreCase(trimToNull(row.orderConfirmedYn()));
        String role = trimToNull(row.role());
        if (role == null) {
            role = "NONE";
        }

        return new PublicGroupResponse(
                row.groupId(),
                row.title(),
                row.ownerId(),
                ownerName,
                row.status(),
                row.purpose(),
                row.regionSido(),
                row.regionSigungu(),
                lat,
                lng,
                row.amount(),
                row.maxMembers(),
                row.currentMembers(),
                row.months(),
                row.payDay(),
                row.assumedRate(),
                row.visibility(),
                row.description(),
                row.rulesTitle(),
                row.rulesBody(),
                orderConfirmed,
                role
        );
    }

    private Optional<GeoPoint> resolveMissingCoords(String regionSido, String regionSigungu) {
        String sido = regionSido == null ? "" : regionSido.strip();
        String sigungu = regionSigungu == null ? "" : regionSigungu.strip();
        String cacheKey = (sido + " " + sigungu).strip();
        if (cacheKey.isEmpty()) {
            return Optional.empty();
        }

        GeoPoint cached = regionGeoCache.get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<GeoPoint> fetched = kakaoGeocodingClient.geocodeRegion(sido, sigungu);
        fetched.ifPresent(point -> regionGeoCache.putIfAbsent(cacheKey, point));
        return fetched;
    }

    private static String normalizeAllFilter(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null || "전체".equals(trimmed) || "ALL".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static String normalizePurpose(String purpose) {
        String trimmed = normalizeAllFilter(purpose);
        if (trimmed == null) {
            return null;
        }
        String mapped = PURPOSE_LABEL_TO_CODE.get(trimmed);
        return mapped != null ? mapped : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
