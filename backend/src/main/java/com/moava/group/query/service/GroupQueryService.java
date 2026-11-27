
package com.moava.group.query.service;

import com.moava.common.util.NameMasker;
import com.moava.group.map.GeoPoint;
import com.moava.group.map.KakaoGeocodingClient;
import com.moava.group.query.dto.GroupHealthRow;
import com.moava.group.query.dto.GroupListResponse;
import com.moava.group.query.dto.GroupMemberResponse;
import com.moava.group.query.dto.GroupRecommendationResponse;
import com.moava.group.query.dto.MemberRegionRow;
import com.moava.group.query.dto.PublicGroupResponse;
import com.moava.group.query.dto.PublicGroupRow;
import com.moava.group.query.dto.RecommendedGroupResponse;
import com.moava.group.query.mapper.GroupQueryMapper;
import com.moava.group.search.GroupElasticsearchClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GroupQueryService {

    private static final int RECOMMENDATION_LIMIT = 3;
    private static final int DEFAULT_HEALTH_SCORE = 80;
    private static final long RECOMMENDATION_AMOUNT_BUFFER = 100_000L;
    private static final long RECOMMENDATION_MIN_CAP = 200_000L;

    private static final Map<String, String> PURPOSE_LABEL_TO_CODE = Map.of(
            "목돈 마련", "SAVINGS",
            "재테크", "INVEST",
            "친목", "SOCIAL",
            "창업", "STARTUP",
            "여행", "TRAVEL"
    );

    private final GroupQueryMapper groupQueryMapper;
    private final KakaoGeocodingClient kakaoGeocodingClient;
    private final GroupElasticsearchClient groupElasticsearchClient;
    private final ConcurrentHashMap<String, GeoPoint> regionGeoCache = new ConcurrentHashMap<>();

    @Autowired
    public GroupQueryService(
            GroupQueryMapper groupQueryMapper,
            KakaoGeocodingClient kakaoGeocodingClient,
            GroupElasticsearchClient groupElasticsearchClient
    ) {
        this.groupQueryMapper = groupQueryMapper;
        this.kakaoGeocodingClient = kakaoGeocodingClient;
        this.groupElasticsearchClient = groupElasticsearchClient;
    }

    public GroupQueryService(
            GroupQueryMapper groupQueryMapper,
            KakaoGeocodingClient kakaoGeocodingClient
    ) {
        this(groupQueryMapper, kakaoGeocodingClient, null);
    }

    public GroupQueryService(GroupQueryMapper groupQueryMapper) {
        this(groupQueryMapper, null, null);
    }

    @Transactional(readOnly = true)
    public List<PublicGroupResponse> getPublicGroups(
            Long memberId,
            String region,
            String purpose,
            String q
    ) {
        String normalizedRegion = normalizeAllFilter(region);
        String normalizedPurpose = normalizePurpose(purpose);
        String normalizedQuery = trimToNull(q);

        // 1) F12: Elasticsearch moava_group 우선 검색 (장애·미연동 시 DB LIKE 폴백)
        if (groupElasticsearchClient != null && groupElasticsearchClient.isEnabled()) {
            Optional<List<Long>> esIds = groupElasticsearchClient.searchPublicGroupIds(
                    normalizedRegion,
                    normalizedPurpose,
                    normalizedQuery
            );
            if (esIds.isPresent()) {
                List<Long> orderedIds = esIds.get();
                if (orderedIds.isEmpty()) {
                    return List.of();
                }
                List<PublicGroupRow> hydratedRows = groupQueryMapper.findPublicGroupsByIds(memberId, orderedIds);
                Map<Long, PublicGroupRow> rowById = new LinkedHashMap<>();
                for (PublicGroupRow row : hydratedRows) {
                    rowById.put(row.groupId(), row);
                }
                return orderedIds.stream()
                        .map(rowById::get)
                        .filter(Objects::nonNull)
                        .map(row -> toPublicGroupResponse(row, memberId))
                        .toList();
            }
        }

        // 2) ES 비활성화 또는 장애 격리 폴백: DB LIKE 검색
        List<PublicGroupRow> rows = groupQueryMapper.findPublicGroups(
                memberId,
                escapeLike(normalizedRegion),
                normalizedPurpose,
                escapeLike(normalizedQuery)
        );

        return rows.stream()
                .map(row -> toPublicGroupResponse(row, memberId))
                .toList();
    }

    /**
     * F11 — 추천 납입액·회원 활동 지역 기반 계모임 추천 (최대 3개).
     * <ol>
     *   <li>{@code VISIBILITY=PUBLIC}, {@code STATUS=RECRUITING}, 정원 미달, {@code amount <= max(추천액+100,000, 200,000)}</li>
     *   <li>동일 시/군/구 우선 조회 → 0건이면 시/도 확대 재검색</li>
     *   <li>정렬: 추천액 차이(|amount - 추천액|) 작은 순 → 모임 건강도(RISK_SCORE) 높은 순 → 최신순, 최대 3건</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public GroupRecommendationResponse getRecommendedGroups(
            Long memberId,
            Long amount,
            String regionSidoParam,
            String regionSigunguParam
    ) {
        long recommendedAmount = amount == null ? 0L : amount;
        if (recommendedAmount < 0L) {
            throw new IllegalArgumentException("추천 납입액은 0원 이상이어야 합니다.");
        }
        long maxAmount = Math.max(recommendedAmount + RECOMMENDATION_AMOUNT_BUFFER, RECOMMENDATION_MIN_CAP);

        String sido = normalizeAllFilter(regionSidoParam);
        String sigungu = normalizeAllFilter(regionSigunguParam);
        String dong = null;

        if (sido == null && sigungu == null) {
            MemberRegionRow memberRegion = groupQueryMapper.findMemberRegionById(memberId);
            if (memberRegion != null) {
                sido = normalizeAllFilter(memberRegion.regionSido());
                sigungu = normalizeAllFilter(memberRegion.regionSigungu());
                dong = normalizeAllFilter(memberRegion.regionDong());
            }
        }

        List<PublicGroupRow> candidates = List.of();
        String matchedScope = "NONE";
        boolean expandedToSido = false;

        // 1) 동일 시/군/구 우선 검색
        if (sigungu != null) {
            candidates = findRecruitingCandidates(memberId, maxAmount, sido, sigungu);
            if (!candidates.isEmpty()) {
                matchedScope = "SIGUNGU";
            }
        }

        // 2) 시/군/구 0건 시 시/도 확대 재검색
        if (candidates.isEmpty() && sido != null) {
            candidates = findRecruitingCandidates(memberId, maxAmount, sido, null);
            if (!candidates.isEmpty()) {
                matchedScope = "SIDO";
                expandedToSido = (sigungu != null);
            } else if (sigungu != null) {
                expandedToSido = true;
            }
        }

        // 3) 회원 활동 지역 미등록 시 전체 공개·모집중 대상 검색
        if (candidates.isEmpty() && sido == null && sigungu == null) {
            candidates = findRecruitingCandidates(memberId, maxAmount, null, null);
            if (!candidates.isEmpty()) {
                matchedScope = "ALL";
            }
        }

        if (candidates.isEmpty()) {
            return new GroupRecommendationResponse(
                    recommendedAmount,
                    maxAmount,
                    sido,
                    sigungu,
                    dong,
                    matchedScope,
                    expandedToSido,
                    List.of()
            );
        }

        List<Long> candidateIds = candidates.stream().map(PublicGroupRow::groupId).toList();
        Map<Long, Integer> healthByGroupId = new HashMap<>();
        List<GroupHealthRow> healthRows = groupQueryMapper.findGroupHealthScoresByIds(candidateIds);
        if (healthRows != null) {
            for (GroupHealthRow hr : healthRows) {
                if (hr != null && hr.groupId() != null && hr.healthScore() != null) {
                    healthByGroupId.put(hr.groupId(), Math.max(0, Math.min(100, hr.healthScore())));
                }
            }
        }

        List<RecommendedGroupResponse> ranked = candidates.stream()
                .sorted(Comparator
                        .comparingLong((PublicGroupRow row) -> Math.abs(row.amount() - recommendedAmount))
                        .thenComparing(
                                Comparator.comparingInt((PublicGroupRow row) ->
                                        healthByGroupId.getOrDefault(row.groupId(), DEFAULT_HEALTH_SCORE)
                                ).reversed()
                        )
                        .thenComparing(Comparator.comparing(PublicGroupRow::groupId).reversed()))
                .limit(RECOMMENDATION_LIMIT)
                .map(row -> {
                    PublicGroupResponse base = toPublicGroupResponse(row, memberId);
                    int health = healthByGroupId.getOrDefault(row.groupId(), DEFAULT_HEALTH_SCORE);
                    long diff = Math.abs(row.amount() - recommendedAmount);
                    return RecommendedGroupResponse.of(base, health, diff);
                })
                .toList();

        return new GroupRecommendationResponse(
                recommendedAmount,
                maxAmount,
                sido,
                sigungu,
                dong,
                matchedScope,
                expandedToSido,
                ranked
        );
    }

    private List<PublicGroupRow> findRecruitingCandidates(
            Long memberId,
            long maxAmount,
            String sido,
            String sigungu
    ) {
        if (groupElasticsearchClient != null && groupElasticsearchClient.isEnabled()) {
            Optional<List<Long>> esIds = groupElasticsearchClient.searchRecommendedGroupIds(
                    maxAmount,
                    sido,
                    sigungu
            );
            if (esIds.isPresent()) {
                List<Long> ids = esIds.get();
                if (ids.isEmpty()) {
                    return List.of();
                }
                List<PublicGroupRow> hydrated = groupQueryMapper.findPublicGroupsByIds(memberId, ids);
                // MOA-100: 내가 만든·가입한(강제 탈퇴 포함)·가입 신청 중인 모임은 추천하지 않는다 (DB 경로 SQL 과 같은 조건)
                Set<Long> excluded = memberId == null
                        ? Set.of()
                        : new HashSet<>(groupQueryMapper.findGroupIdsExcludedFromRecommendation(memberId, ids));
                return hydrated.stream()
                        .filter(row -> isEligibleForRecommendation(row, maxAmount))
                        .filter(row -> !excluded.contains(row.groupId()))
                        .toList();
            }
        }

        List<PublicGroupRow> fallbackRows = groupQueryMapper.findRecruitingGroupsForRecommendation(
                memberId,
                maxAmount,
                escapeLike(sido),
                escapeLike(sigungu)
        );
        return fallbackRows.stream()
                .filter(row -> isEligibleForRecommendation(row, maxAmount))
                .toList();
    }

    private static boolean isEligibleForRecommendation(PublicGroupRow row, long maxAmount) {
        if (row == null) {
            return false;
        }
        if (!"PUBLIC".equals(row.visibility()) || !"RECRUITING".equals(row.status())) {
            return false;
        }
        if (row.amount() == null || row.amount() > maxAmount) {
            return false;
        }
        int maxMembers = row.maxMembers() == null ? 0 : row.maxMembers();
        int currentMembers = row.currentMembers() == null ? 0 : row.currentMembers();
        return maxMembers <= 0 || currentMembers < maxMembers;
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
            Optional<GeoPoint> resolved = resolveMissingCoords(row.regionSido(), row.regionSigungu(), row.regionDong());
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
                row.regionDong(),
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

    private Optional<GeoPoint> resolveMissingCoords(String regionSido, String regionSigungu, String regionDong) {
        String sido = regionSido == null ? "" : regionSido.strip();
        String sigungu = regionSigungu == null ? "" : regionSigungu.strip();
        String dong = regionDong == null ? "" : regionDong.strip();
        String cacheKey = java.util.stream.Stream.of(sido, sigungu, dong)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.joining(" "));
        if (cacheKey.isEmpty()) {
            return Optional.empty();
        }

        GeoPoint cached = regionGeoCache.get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<GeoPoint> fetched = kakaoGeocodingClient.geocodeRegion(
                sido,
                sigungu,
                dong.isEmpty() ? null : dong
        );
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
