package com.moava.admin.sanctions.service;

import com.moava.admin.sanctions.dto.AdminSanctionDTO;
import com.moava.admin.sanctions.mapper.AdminSanctionMapper;
import com.moava.common.util.NameMasker;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * F39 (A03) 플랫폼 가입 제한 설정·해제 및 경고 현황 서비스.
 * - 설정: BANNED_YN='N' ACTIVE 회원 대상, 사유 필수, 중복 BAN 차단('이미 가입 제한 중인 회원입니다.'),
 *         SANCTION(BAN) 이력 append(덮어쓰기 금지), AUDIT_LOG(BAN), 대상 회원 알림(NotifType.SANCTION)
 * - 해제: BANNED_YN='Y' 회원 대상, BANNED_YN='N', SANCTION(BAN_RELEASE) append(이전 기록 보존),
 *         AUDIT_LOG(BAN_RELEASE), 대상 회원 알림(NotifType.SANCTION)
 * - 모임별 회원 경고 현황: n/2회 (2회 = 강제 탈퇴 검토 대상)
 * - 경고·강제 탈퇴 이력: 회원·모임명 검색 + 유형 필터(WARN/EXPEL) + 8건 페이징
 * - (v3.1) 가입 제한·해제는 SANCTION.ISSUED_ADMIN_ID, 모임 경고·강퇴는 SANCTION.ISSUED_MEMBER_ID로 구분
 */
@Service
public class AdminSanctionService {

    public static final int DEFAULT_PAGE_SIZE = 8;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_WARNING_COUNT = 2;
    public static final int MAX_REASON_BYTES = 500;
    public static final int MAX_KEYWORD_LENGTH = 100;
    public static final String DEFAULT_RELEASE_REASON = "관리자 플랫폼 가입 제한 해제";

    private static final String SANCTION_TYPE_BAN = "BAN";
    private static final String SANCTION_TYPE_BAN_RELEASE = "BAN_RELEASE";
    private static final String SANCTION_TYPE_WARN = "WARN";
    private static final String SANCTION_TYPE_EXPEL = "EXPEL";

    private final AdminSanctionMapper mapper;
    private final NotificationService notificationService;

    public AdminSanctionService(AdminSanctionMapper mapper, NotificationService notificationService) {
        this.mapper = mapper;
        this.notificationService = notificationService;
    }

    /** A03 종합 조회 (제재 대상 후보 + 현재 가입 제한 목록 + 모임별 회원 경고 현황) */
    @Transactional(readOnly = true)
    public AdminSanctionDTO.SanctionOverviewResponse getOverview(Long adminId) {
        requireActiveAdmin(adminId);
        List<AdminSanctionDTO.BanCandidateItem> candidates = mapper.selectBanCandidates()
                .stream()
                .map(this::toBanCandidateItem)
                .toList();
        List<AdminSanctionDTO.BannedMemberItem> bannedMembers = mapper.selectBannedMembers()
                .stream()
                .map(this::toBannedMemberItem)
                .toList();
        List<AdminSanctionDTO.MemberWarningItem> warnings = mapper.selectMemberWarnings()
                .stream()
                .map(this::toMemberWarningItem)
                .toList();

        return AdminSanctionDTO.SanctionOverviewResponse.builder()
                .candidates(candidates)
                .bannedMembers(bannedMembers)
                .bannedCount(bannedMembers.size())
                .warnings(warnings)
                .build();
    }

    /** 제재 가능 회원 목록 (현재 제한 중인 회원 및 탈퇴 회원 제외) */
    @Transactional(readOnly = true)
    public List<AdminSanctionDTO.BanCandidateItem> getBanCandidates(Long adminId) {
        requireActiveAdmin(adminId);
        return mapper.selectBanCandidates()
                .stream()
                .map(this::toBanCandidateItem)
                .toList();
    }

    /** 현재 가입 제한 중인 회원 목록 */
    @Transactional(readOnly = true)
    public List<AdminSanctionDTO.BannedMemberItem> getBannedMembers(Long adminId) {
        requireActiveAdmin(adminId);
        return mapper.selectBannedMembers()
                .stream()
                .map(this::toBannedMemberItem)
                .toList();
    }

    /**
     * 플랫폼 가입 제한 설정 (BAN).
     * - 중복 제재 차단: '이미 가입 제한 중인 회원입니다.'
     * - 제재 사유 필수, 기존 SANCTION 이력을 덮어쓰지 않고 새 행 append
     */
    @Transactional
    public AdminSanctionDTO.BanActionResponse banMember(Long adminId, AdminSanctionDTO.BanRequest request) {
        requireActiveAdmin(adminId);
        if (request == null || request.getMemberId() == null || request.getMemberId() <= 0) {
            throw badRequest("가입 제한할 회원을 선택해 주세요.");
        }
        String reason = requireReason(request.getReason());
        Long memberId = request.getMemberId();

        AdminSanctionDTO.MemberBanTargetRow target = mapper.selectMemberForBanUpdate(memberId);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "대상 회원을 찾을 수 없습니다.");
        }
        if (!"ACTIVE".equalsIgnoreCase(target.getStatus())) {
            throw conflict("탈퇴한 회원은 가입 제한을 설정할 수 없습니다.");
        }
        if ("Y".equalsIgnoreCase(target.getBannedYn())) {
            throw conflict("이미 가입 제한 중인 회원입니다.");
        }

        int updated = mapper.updateMemberBannedYn(memberId, "N", "Y");
        if (updated == 0) {
            throw conflict("이미 가입 제한 중인 회원입니다.");
        }

        Long sanctionId = mapper.nextSanctionId();
        mapper.insertAdminSanction(sanctionId, SANCTION_TYPE_BAN, memberId, adminId, reason);
        mapper.insertAdminAuditLog(adminId, memberId, SANCTION_TYPE_BAN,
                "플랫폼 가입 제한 SANCTION #" + sanctionId);

        notificationService.notifyMember(
                memberId,
                NotifType.SANCTION,
                sanctionId,
                Map.of("scope", "MOAVA", "action", "플랫폼 가입 제한이 설정됐어요. 사유: " + reason)
        );

        return AdminSanctionDTO.BanActionResponse.builder()
                .sanctionId(sanctionId)
                .memberId(memberId)
                .memberName(NameMasker.mask(trimToNull(target.getName())))
                .sanctionType(SANCTION_TYPE_BAN)
                .banned(true)
                .reason(reason)
                .issuedAdminId(adminId)
                .message("플랫폼 가입 제한을 설정했어요.")
                .build();
    }

    /**
     * 플랫폼 가입 제한 해제 (BAN_RELEASE).
     * - 이전 BAN 기록은 보존하고 SANCTION(BAN_RELEASE) 행을 추가한다.
     * - 해제 완료 시 MEMBER.BANNED_YN='N'으로 복구되어 F16 모임 가입 신청이 다시 가능해진다.
     */
    @Transactional
    public AdminSanctionDTO.BanActionResponse releaseBan(
            Long adminId,
            Long memberId,
            AdminSanctionDTO.BanReleaseRequest request
    ) {
        requireActiveAdmin(adminId);
        if (memberId == null || memberId <= 0) {
            throw badRequest("올바른 회원 번호를 입력해 주세요.");
        }
        String reason = normalizeReleaseReason(request == null ? null : request.getReason());

        AdminSanctionDTO.MemberBanTargetRow target = mapper.selectMemberForBanUpdate(memberId);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "대상 회원을 찾을 수 없습니다.");
        }
        if (!"ACTIVE".equalsIgnoreCase(target.getStatus())) {
            throw conflict("탈퇴한 회원의 가입 제한은 해제할 수 없습니다.");
        }
        if (!"Y".equalsIgnoreCase(target.getBannedYn())) {
            throw conflict("현재 가입 제한 중인 회원이 아닙니다.");
        }

        int updated = mapper.updateMemberBannedYn(memberId, "Y", "N");
        if (updated == 0) {
            throw conflict("현재 가입 제한 중인 회원이 아닙니다.");
        }

        Long sanctionId = mapper.nextSanctionId();
        mapper.insertAdminSanction(sanctionId, SANCTION_TYPE_BAN_RELEASE, memberId, adminId, reason);
        mapper.insertAdminAuditLog(adminId, memberId, SANCTION_TYPE_BAN_RELEASE,
                "플랫폼 가입 제한 해제 SANCTION #" + sanctionId);

        notificationService.notifyMember(
                memberId,
                NotifType.SANCTION,
                sanctionId,
                Map.of("scope", "MOAVA", "action", "플랫폼 가입 제한이 해제됐어요.")
        );

        return AdminSanctionDTO.BanActionResponse.builder()
                .sanctionId(sanctionId)
                .memberId(memberId)
                .memberName(NameMasker.mask(trimToNull(target.getName())))
                .sanctionType(SANCTION_TYPE_BAN_RELEASE)
                .banned(false)
                .reason(reason)
                .issuedAdminId(adminId)
                .message("플랫폼 가입 제한을 해제했어요.")
                .build();
    }

    /** 모임별 회원 경고 현황 조회 (n/2회, 2회 = 강제 탈퇴 검토 대상) */
    @Transactional(readOnly = true)
    public List<AdminSanctionDTO.MemberWarningItem> getMemberWarnings(Long adminId) {
        requireActiveAdmin(adminId);
        return mapper.selectMemberWarnings()
                .stream()
                .map(this::toMemberWarningItem)
                .toList();
    }

    /** 경고·강제 탈퇴 상세 이력 조회 (회원·모임명 검색 + 유형 필터 + 기본 8건 페이징) */
    @Transactional(readOnly = true)
    public AdminSanctionDTO.SanctionHistoryPageResponse getWarningExpelHistory(
            Long adminId,
            String keyword,
            String sanctionType,
            int page,
            int size
    ) {
        requireActiveAdmin(adminId);
        if (page < 0) {
            throw badRequest("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest("페이지당 조회 건수는 1건 이상 " + MAX_PAGE_SIZE + "건 이하만 가능합니다.");
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedType = normalizeHistoryType(sanctionType);

        long offsetLong = (long) page * size;
        if (offsetLong > Integer.MAX_VALUE) {
            throw badRequest("조회 가능한 페이지 범위를 초과했습니다.");
        }

        AdminSanctionDTO.SanctionHistorySearchCriteria criteria =
                AdminSanctionDTO.SanctionHistorySearchCriteria.builder()
                        .escapedKeyword(normalizedKeyword == null ? null : escapeLike(normalizedKeyword))
                        .sanctionType(normalizedType)
                        .offset((int) offsetLong)
                        .size(size)
                        .build();

        long totalCount = mapper.countWarningExpelHistory(criteria);
        List<AdminSanctionDTO.SanctionHistoryItem> items = (totalCount == 0 || offsetLong >= totalCount)
                ? List.of()
                : mapper.selectWarningExpelHistory(criteria).stream()
                        .map(this::toSanctionHistoryItem)
                        .toList();

        int totalPages = totalCount == 0 ? 0 : (int) ((totalCount + size - 1) / size);
        boolean hasPrev = page > 0;
        boolean hasNext = (long) (page + 1) * size < totalCount;

        return AdminSanctionDTO.SanctionHistoryPageResponse.builder()
                .items(items)
                .page(page)
                .size(size)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .hasPrev(hasPrev)
                .hasNext(hasNext)
                .filters(AdminSanctionDTO.AppliedHistoryFilters.builder()
                        .keyword(normalizedKeyword)
                        .sanctionType(normalizedType == null ? "ALL" : normalizedType)
                        .build())
                .build();
    }

    // ---------- 내부 도우미 ----------

    private void requireActiveAdmin(Long adminId) {
        if (adminId == null || mapper.countActiveAdmin(adminId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "활성화된 플랫폼 관리자 계정만 이용할 수 있습니다.");
        }
    }

    private AdminSanctionDTO.BanCandidateItem toBanCandidateItem(AdminSanctionDTO.BanCandidateRow row) {
        return AdminSanctionDTO.BanCandidateItem.builder()
                .memberId(row.getMemberId())
                .memberName(NameMasker.mask(trimToNull(row.getName())))
                .email(row.getEmail())
                .regionSido(row.getRegionSido())
                .regionSigungu(row.getRegionSigungu())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private AdminSanctionDTO.BannedMemberItem toBannedMemberItem(AdminSanctionDTO.BannedMemberRow row) {
        return AdminSanctionDTO.BannedMemberItem.builder()
                .memberId(row.getMemberId())
                .memberName(NameMasker.mask(trimToNull(row.getName())))
                .email(row.getEmail())
                .latestSanctionId(row.getLatestSanctionId())
                .reason(row.getReason())
                .issuedAdminId(row.getIssuedAdminId())
                .issuedAdminLabel(formatAdminLabel(row.getIssuedAdminId(), row.getIssuedAdminName()))
                .bannedAt(row.getBannedAt())
                .build();
    }

    private AdminSanctionDTO.MemberWarningItem toMemberWarningItem(AdminSanctionDTO.MemberWarningRow row) {
        boolean expelReviewTarget = row.getWarningCount() >= MAX_WARNING_COUNT;
        return AdminSanctionDTO.MemberWarningItem.builder()
                .gyeMemberId(row.getGyeMemberId())
                .groupId(row.getGroupId())
                .groupTitle(row.getGroupTitle())
                .groupStatus(row.getGroupStatus())
                .memberId(row.getMemberId())
                .memberName(NameMasker.mask(trimToNull(row.getMemberName())))
                .warningCount(row.getWarningCount())
                .maxWarnings(MAX_WARNING_COUNT)
                .expelReviewTarget(expelReviewTarget)
                .statusText(expelReviewTarget ? "강제 탈퇴 검토 대상" : "경고 관리 중")
                .joinedAt(row.getJoinedAt())
                .build();
    }

    private AdminSanctionDTO.SanctionHistoryItem toSanctionHistoryItem(AdminSanctionDTO.SanctionHistoryRow row) {
        String issuerType = row.getIssuedAdminId() != null ? "ADMIN" : "MEMBER";
        String issuerLabel = row.getIssuedAdminId() != null
                ? formatAdminLabel(row.getIssuedAdminId(), row.getIssuedAdminName())
                : NameMasker.mask(trimToNull(row.getIssuedMemberName()));

        return AdminSanctionDTO.SanctionHistoryItem.builder()
                .sanctionId(row.getSanctionId())
                .sanctionType(row.getSanctionType())
                .sanctionTypeLabel(formatSanctionTypeLabel(row.getSanctionType()))
                .groupId(row.getGroupId())
                .groupTitle(row.getGroupTitle())
                .memberId(row.getMemberId())
                .memberName(NameMasker.mask(trimToNull(row.getMemberName())))
                .issuerType(issuerType)
                .issuedMemberId(row.getIssuedMemberId())
                .issuedAdminId(row.getIssuedAdminId())
                .issuerLabel(issuerLabel)
                .reason(row.getReason())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private static String formatSanctionTypeLabel(String sanctionType) {
        if (SANCTION_TYPE_WARN.equalsIgnoreCase(sanctionType)) {
            return "경고";
        }
        if (SANCTION_TYPE_EXPEL.equalsIgnoreCase(sanctionType)) {
            return "강제 탈퇴";
        }
        return sanctionType;
    }

    private static String formatAdminLabel(Long adminId, String adminName) {
        if (adminId == null) {
            return null;
        }
        String trimmed = trimToNull(adminName);
        if (trimmed == null) {
            return "관리자";
        }
        return trimmed.startsWith("관리자") ? trimmed : "관리자 " + trimmed;
    }

    private static String requireReason(String reason) {
        String trimmed = trimToNull(reason);
        if (trimmed == null) {
            throw badRequest("제재 사유를 입력해 주세요.");
        }
        if (trimmed.getBytes(StandardCharsets.UTF_8).length > MAX_REASON_BYTES) {
            throw badRequest("제재 사유는 한글 기준 약 160자까지 입력할 수 있습니다.");
        }
        return trimmed;
    }

    private static String normalizeReleaseReason(String reason) {
        String trimmed = trimToNull(reason);
        if (trimmed == null) {
            return DEFAULT_RELEASE_REASON;
        }
        if (trimmed.getBytes(StandardCharsets.UTF_8).length > MAX_REASON_BYTES) {
            throw badRequest("해제 사유는 한글 기준 약 160자까지 입력할 수 있습니다.");
        }
        return trimmed;
    }

    private static String normalizeKeyword(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw badRequest("검색어는 " + MAX_KEYWORD_LENGTH + "자 이하로 입력해 주세요.");
        }
        return trimmed;
    }

    private static String normalizeHistoryType(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null || "ALL".equalsIgnoreCase(trimmed) || "전체".equals(trimmed)) {
            return null;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (SANCTION_TYPE_WARN.equals(upper) || "경고".equals(trimmed)) {
            return SANCTION_TYPE_WARN;
        }
        if (SANCTION_TYPE_EXPEL.equals(upper) || "강제 탈퇴".equals(trimmed) || "강퇴".equals(trimmed)) {
            return SANCTION_TYPE_EXPEL;
        }
        throw badRequest("처리 유형은 ALL, WARN, EXPEL 중 하나여야 합니다.");
    }

    static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
