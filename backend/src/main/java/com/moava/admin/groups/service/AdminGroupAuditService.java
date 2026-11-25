package com.moava.admin.groups.service;

import com.moava.admin.groups.dto.AdminGroupAuditDTO;
import com.moava.admin.groups.mapper.AdminGroupAuditMapper;
import com.moava.common.util.NameMasker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * F38 (A02) 계모임 관리 및 이력 감사 서비스.
 * - 전체 계모임 목록 + 관리자 상세 패널(규칙, 가입 요청·증빙·탈퇴·미납 건수) 조회
 * - AUDIT_LOG 필터(GROUP_ID, EVENT_TYPE, 검토/완료=_REQUEST/_DETECT 접미사, v3.1 관리자 조치만/담당 관리자별) + 8건 페이징
 * - 가입 승인·증빙 승인은 모임장 전용이며 관리자 개입을 허용하지 않는다(조회 전용).
 */
@Service
public class AdminGroupAuditService {

    public static final int DEFAULT_PAGE_SIZE = 8;
    public static final int MAX_PAGE_SIZE = 50;
    public static final String READ_ONLY_NOTICE =
            "가입 승인 및 납입 증빙 승인은 모임장 전용입니다. 관리자는 조회·감사만 가능합니다.";

    private final AdminGroupAuditMapper mapper;

    public AdminGroupAuditService(AdminGroupAuditMapper mapper) {
        this.mapper = mapper;
    }

    /** 전체 계모임 목록 조회 (모임장 마스킹·상태·활성 회원 수·납입일·계좌 인증 상태) */
    @Transactional(readOnly = true)
    public List<AdminGroupAuditDTO.GroupSummaryItem> getGroups(Long adminId) {
        requireActiveAdmin(adminId);
        return mapper.selectAdminGroupList()
                .stream()
                .map(this::toGroupSummaryItem)
                .toList();
    }

    /** 관리자 상세 패널 조회 (규칙, 가입 요청·증빙·탈퇴·미납 건수, 계좌 조회 전용) */
    @Transactional(readOnly = true)
    public AdminGroupAuditDTO.GroupDetailResponse getGroupDetail(Long adminId, Long groupId) {
        requireActiveAdmin(adminId);
        if (groupId == null || groupId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 모임 번호를 입력해 주세요.");
        }

        AdminGroupAuditDTO.GroupDetailRow row = mapper.selectAdminGroupDetail(groupId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "계모임을 찾을 수 없습니다.");
        }

        AdminGroupAuditDTO.GroupAccountRow currentAcc = mapper.selectGroupAccountByStatus(groupId, "CURRENT");
        AdminGroupAuditDTO.GroupAccountRow pendingAcc = mapper.selectGroupAccountByStatus(groupId, "PENDING");

        return AdminGroupAuditDTO.GroupDetailResponse.builder()
                .groupId(row.getGroupId())
                .title(row.getTitle())
                .purpose(row.getPurpose())
                .regionSido(row.getRegionSido())
                .regionSigungu(row.getRegionSigungu())
                .ownerId(row.getOwnerId())
                .ownerName(NameMasker.mask(trimToNull(row.getOwnerName())))
                .status(row.getStatus())
                .visibility(row.getVisibility())
                .activeMemberCount(row.getActiveMemberCount())
                .maxMembers(row.getMaxMembers())
                .amount(row.getAmount())
                .months(row.getMonths())
                .payDay(row.getPayDay())
                .assumedRate(row.getAssumedRate())
                .overduePolicy(row.getOverduePolicy())
                .orderConfirmed("Y".equalsIgnoreCase(row.getOrderConfirmedYn()))
                .rulesTitle(row.getRulesTitle())
                .rulesBody(row.getRulesBody())
                .description(row.getDescription())
                .startedAt(row.getStartedAt())
                .createdAt(row.getCreatedAt())
                .joinRequestCount(row.getJoinRequestCount())
                .pendingJoinRequestCount(row.getPendingJoinRequestCount())
                .paymentProofCount(row.getPaymentProofCount())
                .pendingProofCount(row.getPendingProofCount())
                .withdrawalRequestCount(row.getWithdrawalRequestCount())
                .activeWithdrawalCount(row.getActiveWithdrawalCount())
                .overdueCaseCount(row.getOverdueCaseCount())
                .openOverdueCaseCount(row.getOpenOverdueCaseCount())
                .currentAccount(toAccountSummary(currentAcc))
                .pendingAccount(toAccountSummary(pendingAcc))
                .readOnlyNotice(READ_ONLY_NOTICE)
                .build();
    }

    /**
     * AUDIT_LOG 필터 + 페이징 조회 (기본 페이지당 8건).
     * - 필터 3종: groupId, eventType, reviewStatus(ALL / REVIEW[_REQUEST,_DETECT] / DONE)
     * - v3.1 추가 필터: adminOnly(ACTOR_ADMIN_ID IS NOT NULL), actorAdminId(담당 관리자별)
     */
    @Transactional(readOnly = true)
    public AdminGroupAuditDTO.AuditLogPageResponse getAuditLogs(
            Long adminId,
            Long groupId,
            String eventType,
            String reviewStatus,
            boolean adminOnly,
            Long actorAdminId,
            int page,
            int size
    ) {
        requireActiveAdmin(adminId);

        if (groupId != null && groupId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 모임 번호를 선택해 주세요.");
        }
        if (actorAdminId != null && actorAdminId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 관리자 번호를 선택해 주세요.");
        }
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "페이지당 조회 건수는 1건 이상 " + MAX_PAGE_SIZE + "건 이하만 가능합니다."
            );
        }

        String normalizedEventType = normalizeEventType(eventType);
        String normalizedReviewStatus = normalizeReviewStatus(reviewStatus);
        boolean effectiveAdminOnly = adminOnly || actorAdminId != null;

        long offsetLong = (long) page * size;
        if (offsetLong > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 가능한 페이지 범위를 초과했습니다.");
        }

        AdminGroupAuditDTO.AuditLogSearchCriteria criteria = AdminGroupAuditDTO.AuditLogSearchCriteria.builder()
                .groupId(groupId)
                .eventType(normalizedEventType)
                .reviewStatus(normalizedReviewStatus)
                .adminOnly(effectiveAdminOnly)
                .actorAdminId(actorAdminId)
                .offset((int) offsetLong)
                .size(size)
                .build();

        long totalCount = mapper.countAuditLogs(criteria);
        List<AdminGroupAuditDTO.AuditLogItem> items = (totalCount == 0 || offsetLong >= totalCount)
                ? List.of()
                : mapper.selectAuditLogs(criteria).stream()
                        .map(this::toAuditLogItem)
                        .toList();

        int totalPages = totalCount == 0 ? 0 : (int) ((totalCount + size - 1) / size);
        boolean hasPrev = page > 0;
        boolean hasNext = (long) (page + 1) * size < totalCount;

        return AdminGroupAuditDTO.AuditLogPageResponse.builder()
                .items(items)
                .page(page)
                .size(size)
                .totalCount(totalCount)
                .totalPages(totalPages)
                .hasPrev(hasPrev)
                .hasNext(hasNext)
                .filters(AdminGroupAuditDTO.AppliedFilters.builder()
                        .groupId(groupId)
                        .eventType(normalizedEventType)
                        .reviewStatus(normalizedReviewStatus)
                        .adminOnly(effectiveAdminOnly)
                        .actorAdminId(actorAdminId)
                        .build())
                .build();
    }

    /** 감사 이력 필터 드롭다운 옵션 조회 (이벤트 유형 목록 + 담당 관리자 목록) */
    @Transactional(readOnly = true)
    public AdminGroupAuditDTO.AuditFilterOptionsResponse getFilterOptions(Long adminId) {
        requireActiveAdmin(adminId);
        List<String> eventTypes = mapper.selectDistinctAuditEventTypes();
        List<AdminGroupAuditDTO.AdminOption> admins = mapper.selectAdminOptions();
        return AdminGroupAuditDTO.AuditFilterOptionsResponse.builder()
                .eventTypes(eventTypes == null ? List.of() : eventTypes)
                .admins(admins == null ? List.of() : admins)
                .build();
    }

    private void requireActiveAdmin(Long adminId) {
        if (adminId == null || mapper.countActiveAdmin(adminId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "활성화된 플랫폼 관리자 계정만 조회할 수 있습니다.");
        }
    }

    private AdminGroupAuditDTO.GroupSummaryItem toGroupSummaryItem(AdminGroupAuditDTO.GroupSummaryRow row) {
        return AdminGroupAuditDTO.GroupSummaryItem.builder()
                .groupId(row.getGroupId())
                .title(row.getTitle())
                .ownerId(row.getOwnerId())
                .ownerName(NameMasker.mask(trimToNull(row.getOwnerName())))
                .status(row.getStatus())
                .visibility(row.getVisibility())
                .activeMemberCount(row.getActiveMemberCount())
                .maxMembers(row.getMaxMembers())
                .payDay(row.getPayDay())
                .amount(row.getAmount())
                .months(row.getMonths())
                .assumedRate(row.getAssumedRate())
                .accountVerifyStatus(row.getAccountVerifyStatus())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private AdminGroupAuditDTO.GroupAccountSummary toAccountSummary(AdminGroupAuditDTO.GroupAccountRow row) {
        if (row == null) {
            return null;
        }
        String last4 = trimToNull(row.getAccountNoLast4());
        String maskedAccountNo = last4 == null ? "미등록" : "****" + last4;
        String holder = trimToNull(row.getRegisteredByName());
        return AdminGroupAuditDTO.GroupAccountSummary.builder()
                .gyeAccountId(row.getGyeAccountId())
                .bankName(row.getBankName())
                .maskedHolderName(holder == null ? null : NameMasker.mask(holder))
                .maskedAccountNo(maskedAccountNo)
                .accountNoLast4(last4)
                .verifyStatus(row.getVerifyStatus())
                .accountStatus(row.getAccountStatus())
                .requestedAt(row.getRequestedAt())
                .verifiedAt(row.getVerifiedAt())
                .build();
    }

    private AdminGroupAuditDTO.AuditLogItem toAuditLogItem(AdminGroupAuditDTO.AuditLogRow row) {
        String actorType = resolveActorType(row);
        String targetLabel = row.getTargetMemberId() == null
                ? null
                : NameMasker.mask(trimToNull(row.getTargetMemberName()));

        return AdminGroupAuditDTO.AuditLogItem.builder()
                .auditLogId(row.getAuditLogId())
                .groupId(row.getGroupId())
                .groupTitle(row.getGroupTitle())
                .eventType(row.getEventType())
                .reviewCategory(isReviewEventType(row.getEventType()) ? "REVIEW" : "DONE")
                .actorType(actorType)
                .actorAdminId(row.getActorAdminId())
                .actorMemberId(row.getActorMemberId())
                .actorLabel(formatActorLabel(row))
                .targetMemberId(row.getTargetMemberId())
                .targetMemberLabel(targetLabel)
                .description(row.getDescription())
                .createdAt(row.getCreatedAt())
                .build();
    }

    private static String resolveActorType(AdminGroupAuditDTO.AuditLogRow row) {
        if (row.getActorAdminId() != null) {
            return "ADMIN";
        }
        if (row.getActorMemberId() != null) {
            return "MEMBER";
        }
        return "SYSTEM";
    }

    private static String formatActorLabel(AdminGroupAuditDTO.AuditLogRow row) {
        if (row.getActorAdminId() != null) {
            String adminName = trimToNull(row.getAdminName());
            if (adminName == null) {
                return "관리자";
            }
            return adminName.startsWith("관리자") ? adminName : "관리자 " + adminName;
        }
        if (row.getActorMemberId() != null) {
            return NameMasker.mask(trimToNull(row.getMemberName()));
        }
        return "시스템";
    }

    static boolean isReviewEventType(String eventType) {
        if (eventType == null) {
            return false;
        }
        String upper = eventType.trim().toUpperCase(Locale.ROOT);
        return upper.endsWith("_REQUEST") || upper.endsWith("_DETECT");
    }

    private static String normalizeEventType(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null || "ALL".equalsIgnoreCase(trimmed) || "전체".equals(trimmed)) {
            return null;
        }
        if (trimmed.length() > 40) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이벤트 유형 값이 올바르지 않습니다.");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String normalizeReviewStatus(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null || "ALL".equalsIgnoreCase(trimmed) || "전체".equals(trimmed)) {
            return "ALL";
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if ("REVIEW".equals(upper) || "검토".equals(trimmed)) {
            return "REVIEW";
        }
        if ("DONE".equals(upper) || "COMPLETED".equals(upper) || "완료".equals(trimmed) || "처리".equals(trimmed)) {
            return "DONE";
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "검토/완료 구분은 ALL, REVIEW, DONE 중 하나여야 합니다."
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
