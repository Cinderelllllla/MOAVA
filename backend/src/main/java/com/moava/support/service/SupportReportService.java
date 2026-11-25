package com.moava.support.service;

import com.moava.common.util.NameMasker;
import com.moava.crypto.service.CryptoKeyService;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import com.moava.support.dto.SupportReportDTO;
import com.moava.support.mapper.SupportReportMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F40 (S15 · A08) 고객센터 신고 접수 및 처리 서비스.
 * - 화면 분리: 회원 S15(접수·내 신고), 관리자 A08(조회·처리). ADMIN 접수 불가.
 * - [접수] 상세 내용 AES-256-GCM 암호화(DETAIL_ENC CLOB, KEY_VER) 저장(평문 저장 금지),
 *         활성 관리자 전원 알림(NotifType.REPORT_RECEIVED),
 *         AUDIT_LOG(REPORT_RECEIVED)에는 유형·대상 모임만 기록 + 모임 변경 기록 '신고 접수'.
 * - [조회] 회원은 본인 건만, ADMIN은 전체 + 상태 필터. 타인 신고 열람 불가.
 *         복호화는 신고자 본인·ADMIN 응답 시에만 수행. 목록에 신고자(마스킹)·대상 모임·유형·접수일 표시.
 * - [처리] ADMIN 상태 변경 시 처리 메모 필수('처리 메모가 필요합니다') →
 *         STATUS, ADMIN_MEMO, HANDLED_BY(FK ADMIN), UPDATED_AT 갱신,
 *         AUDIT_LOG(REPORT_STATUS)에 이전·이후 상태·사유 이력 기록, 신고자 알림(NotifType.REPORT_STATUS).
 */
@Service
public class SupportReportService {

    public static final String GUIDANCE_NOTICE =
            "MOAVA는 실제 계좌 동결 및 피해 구제를 수행할 수 없습니다. 사기 피해가 의심되는 경우 해당 은행 및 경찰(112) 등 공식 기관에 즉시 신고해 주세요.";
    public static final String ADMIN_SUBMIT_FORBIDDEN_MESSAGE = "관리자는 신고 처리 메뉴를 이용해 주세요";
    public static final String MEMO_REQUIRED_MESSAGE = "처리 메모가 필요합니다";

    public static final int MAX_DETAIL_LENGTH = 4000;
    public static final int MAX_ADMIN_MEMO_BYTES = 800;
    public static final int MAX_AUDIT_DESCRIPTION_BYTES = 1000;

    private static final String EVENT_REPORT_RECEIVED = "REPORT_RECEIVED";
    private static final String EVENT_REPORT_STATUS = "REPORT_STATUS";
    private static final String UNASSIGNED_GROUP_LABEL = "모임 미지정";

    private static final List<String> STATUS_ORDER = List.of(
            "RECEIVED", "REVIEWING", "MORE_INFO", "CLOSED", "REJECTED"
    );

    private static final Pattern REPORT_PREFIX_PATTERN =
            Pattern.compile("^REPORT #(\\d+) · (.*)$", Pattern.DOTALL);
    private static final Pattern STATUS_HISTORY_PATTERN =
            Pattern.compile("^REPORT #(\\d+) · (.+?) → (.+?) · 사유: (.*)$", Pattern.DOTALL);

    private final SupportReportMapper mapper;
    private final CryptoKeyService cryptoKeyService;
    private final NotificationService notificationService;

    public SupportReportService(
            SupportReportMapper mapper,
            CryptoKeyService cryptoKeyService,
            NotificationService notificationService
    ) {
        this.mapper = mapper;
        this.cryptoKeyService = cryptoKeyService;
        this.notificationService = notificationService;
    }

    /** 회원 S15: 신고 대상 모임 선택 목록 (참여 중이거나 모임장인 모임) */
    @Transactional(readOnly = true)
    public List<SupportReportDTO.GroupOptionItem> getMemberGroups(Long memberId) {
        requireActiveMember(memberId);
        return mapper.selectMemberGroups(memberId).stream()
                .map(row -> SupportReportDTO.GroupOptionItem.builder()
                        .groupId(row.getGroupId())
                        .title(row.getTitle())
                        .status(row.getStatus())
                        .role(row.getRole())
                        .build())
                .toList();
    }

    /**
     * 회원 S15: 고객센터 신고 접수.
     * - 상세 내용 AES-256-GCM 암호화(DETAIL_ENC CLOB, KEY_VER) 후 STATUS='RECEIVED' 저장
     * - 활성 관리자 전원 알림(NOTIFICATION.ADMIN_ID, REPORT_RECEIVED → /admin/support)
     * - AUDIT_LOG(REPORT_RECEIVED)에는 유형·대상 모임만 기록(상세 평문 금지), 모임 변경 기록에 '신고 접수'
     */
    @Transactional
    public SupportReportDTO.CreateReportResponse createReport(
            Long memberId,
            SupportReportDTO.CreateReportRequest request
    ) {
        requireActiveMember(memberId);
        if (request == null) {
            throw badRequest("신고 내용을 입력해 주세요.");
        }

        String category = normalizeCategory(request.getCategory());
        String detail = requireDetail(request.getDetail());

        Long groupId = request.getGroupId();
        String groupLabel = UNASSIGNED_GROUP_LABEL;
        if (groupId != null) {
            if (groupId <= 0) {
                throw badRequest("올바른 대상 모임을 선택해 주세요.");
            }
            SupportReportDTO.GroupBasicRow group = mapper.selectGroupById(groupId);
            if (group == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "대상 모임을 찾을 수 없습니다.");
            }
            groupLabel = trimToNull(group.getTitle()) != null ? group.getTitle().trim() : "모임 #" + groupId;
        }

        int keyVer = cryptoKeyService.getCurrentKeyVersion();
        String detailEnc = cryptoKeyService.encrypt(detail, keyVer);
        if (detailEnc == null || detailEnc.isBlank()) {
            throw new IllegalStateException("신고 상세 암호화에 실패했습니다.");
        }

        Long reportId = mapper.nextSupportReportId();
        mapper.insertSupportReport(reportId, memberId, groupId, category, detailEnc, keyVer);

        String categoryLabel = formatCategoryLabel(category);
        String auditDescription = cutBytes(
                "신고 접수 · REPORT #" + reportId + " · 유형: " + categoryLabel + " · 대상 모임: " + groupLabel,
                MAX_AUDIT_DESCRIPTION_BYTES
        );
        mapper.insertMemberAuditLog(groupId, memberId, EVENT_REPORT_RECEIVED, auditDescription);

        notificationService.notifyAdmins(
                NotifType.REPORT_RECEIVED,
                reportId,
                Map.of("category", categoryLabel, "group", groupLabel)
        );

        SupportReportDTO.SupportReportRow saved = mapper.selectReportById(reportId);
        SupportReportDTO.ReportItem item = saved != null
                ? toReportItem(saved, List.of())
                : SupportReportDTO.ReportItem.builder()
                        .reportId(reportId)
                        .memberId(memberId)
                        .reporterName("회원")
                        .groupId(groupId)
                        .groupTitle(groupLabel)
                        .category(category)
                        .categoryLabel(categoryLabel)
                        .detail(detail)
                        .status("RECEIVED")
                        .statusLabel(formatStatusLabel("RECEIVED"))
                        .history(List.of())
                        .build();

        return SupportReportDTO.CreateReportResponse.builder()
                .report(item)
                .message("신고가 접수되었습니다. 실제 계좌 동결·피해 구제는 불가하므로 은행·경찰 등 공식 기관에 별도로 신고해 주세요.")
                .guidanceNotice(GUIDANCE_NOTICE)
                .build();
    }

    /** 회원 S15: 나의 신고 목록 조회 (본인 건만 조회 및 복호화) */
    @Transactional(readOnly = true)
    public SupportReportDTO.MemberReportListResponse getMyReports(Long memberId) {
        requireActiveMember(memberId);
        List<SupportReportDTO.SupportReportRow> rows = mapper.selectReportsByMemberId(memberId);
        Map<Long, List<SupportReportDTO.StatusHistoryItem>> historyByReport = rows.isEmpty()
                ? Map.of()
                : groupHistoryByReportId(mapper.selectReportStatusHistoryByMemberId(memberId));

        List<SupportReportDTO.ReportItem> items = rows.stream()
                .map(row -> toReportItem(row, historyByReport.getOrDefault(row.getSupportReportId(), List.of())))
                .toList();

        return SupportReportDTO.MemberReportListResponse.builder()
                .items(items)
                .totalCount(items.size())
                .guidanceNotice(GUIDANCE_NOTICE)
                .build();
    }

    /** 회원 S15: 나의 신고 단건 조회 (타인 신고 열람 불가, 타인 건은 복호화 없이 403 차단) */
    @Transactional(readOnly = true)
    public SupportReportDTO.ReportItem getMyReportDetail(Long memberId, Long reportId) {
        requireActiveMember(memberId);
        if (reportId == null || reportId <= 0) {
            throw badRequest("올바른 신고 번호를 입력해 주세요.");
        }

        SupportReportDTO.SupportReportRow row = mapper.selectReportById(reportId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "신고 내역을 찾을 수 없습니다.");
        }
        if (!memberId.equals(row.getMemberId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 접수한 신고만 열람할 수 있습니다.");
        }

        List<SupportReportDTO.StatusHistoryItem> history =
                toHistoryItems(mapper.selectReportStatusHistoryByReportId(reportId));
        return toReportItem(row, history);
    }

    /** 관리자 A08: 전체 신고 목록 조회 + 상태 필터 (복호화는 활성 ADMIN 응답 시에만) */
    @Transactional(readOnly = true)
    public SupportReportDTO.AdminReportListResponse getAdminReports(Long adminId, String statusFilter) {
        requireActiveAdmin(adminId);
        String normalizedFilter = normalizeStatusFilter(statusFilter);

        List<SupportReportDTO.SupportReportRow> rows = mapper.selectAdminReports(normalizedFilter);
        Map<Long, List<SupportReportDTO.StatusHistoryItem>> historyByReport = rows.isEmpty()
                ? Map.of()
                : groupHistoryByReportId(mapper.selectAdminReportStatusHistory(normalizedFilter));

        List<SupportReportDTO.ReportItem> items = rows.stream()
                .map(row -> toReportItem(row, historyByReport.getOrDefault(row.getSupportReportId(), List.of())))
                .toList();

        Map<String, Integer> statusCounts = buildStatusCounts(mapper.selectStatusCounts());

        return SupportReportDTO.AdminReportListResponse.builder()
                .items(items)
                .totalCount(items.size())
                .statusFilter(normalizedFilter == null ? "ALL" : normalizedFilter)
                .statusCounts(statusCounts)
                .guidanceNotice(GUIDANCE_NOTICE)
                .build();
    }

    /** 관리자 A08: 신고 단건 조회 */
    @Transactional(readOnly = true)
    public SupportReportDTO.ReportItem getAdminReportDetail(Long adminId, Long reportId) {
        requireActiveAdmin(adminId);
        if (reportId == null || reportId <= 0) {
            throw badRequest("올바른 신고 번호를 입력해 주세요.");
        }

        SupportReportDTO.SupportReportRow row = mapper.selectReportById(reportId);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "신고 내역을 찾을 수 없습니다.");
        }

        List<SupportReportDTO.StatusHistoryItem> history =
                toHistoryItems(mapper.selectReportStatusHistoryByReportId(reportId));
        return toReportItem(row, history);
    }

    /**
     * 관리자 A08: 신고 상태 변경.
     * - 처리 메모 필수 (누락 시 400 '처리 메모가 필요합니다')
     * - STATUS, ADMIN_MEMO, HANDLED_BY(FK ADMIN.ADMIN_ID), UPDATED_AT 갱신
     * - AUDIT_LOG(REPORT_STATUS)에 이전·이후 상태·사유 이력 append
     * - 신고자 알림(NotifType.REPORT_STATUS → /support)
     */
    @Transactional
    public SupportReportDTO.StatusUpdateResponse updateReportStatus(
            Long adminId,
            Long reportId,
            SupportReportDTO.StatusUpdateRequest request
    ) {
        requireActiveAdmin(adminId);
        if (reportId == null || reportId <= 0) {
            throw badRequest("올바른 신고 번호를 입력해 주세요.");
        }
        if (request == null) {
            throw badRequest(MEMO_REQUIRED_MESSAGE);
        }

        String nextStatus = normalizeStatus(request.getStatus());
        String adminMemo = requireAdminMemo(request.getAdminMemo());

        SupportReportDTO.SupportReportRow target = mapper.selectReportForUpdate(reportId);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "신고 내역을 찾을 수 없습니다.");
        }

        String prevStatus = target.getStatus();
        if (nextStatus.equalsIgnoreCase(prevStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 동일한 처리 상태입니다.");
        }

        mapper.updateReportStatus(reportId, nextStatus, adminMemo, adminId);

        String prevStatusLabel = formatStatusLabel(prevStatus);
        String nextStatusLabel = formatStatusLabel(nextStatus);
        String auditDescription = cutBytes(
                "REPORT #" + reportId + " · " + prevStatusLabel + " → " + nextStatusLabel + " · 사유: " + adminMemo,
                MAX_AUDIT_DESCRIPTION_BYTES
        );
        mapper.insertAdminAuditLog(
                target.getGroupId(),
                adminId,
                target.getMemberId(),
                EVENT_REPORT_STATUS,
                auditDescription
        );

        notificationService.notifyMember(
                target.getMemberId(),
                NotifType.REPORT_STATUS,
                reportId,
                Map.of("status", nextStatusLabel)
        );

        SupportReportDTO.SupportReportRow updated = mapper.selectReportById(reportId);
        List<SupportReportDTO.StatusHistoryItem> history =
                toHistoryItems(mapper.selectReportStatusHistoryByReportId(reportId));

        SupportReportDTO.ReportItem item = updated != null
                ? toReportItem(updated, history)
                : toReportItem(target, history);

        return SupportReportDTO.StatusUpdateResponse.builder()
                .report(item)
                .previousStatus(prevStatus)
                .previousStatusLabel(prevStatusLabel)
                .currentStatus(nextStatus)
                .currentStatusLabel(nextStatusLabel)
                .message("신고 처리 상태를 저장했습니다.")
                .build();
    }

    // ---------- 내부 도우미 ----------

    private void requireActiveMember(Long memberId) {
        if (memberId == null || memberId <= 0 || mapper.countActiveMember(memberId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "활성화된 회원 계정만 이용할 수 있습니다.");
        }
    }

    private void requireActiveAdmin(Long adminId) {
        if (adminId == null || adminId <= 0 || mapper.countActiveAdmin(adminId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "활성화된 플랫폼 관리자 계정만 이용할 수 있습니다.");
        }
    }

    private SupportReportDTO.ReportItem toReportItem(
            SupportReportDTO.SupportReportRow row,
            List<SupportReportDTO.StatusHistoryItem> history
    ) {
        int keyVer = row.getKeyVer() == null ? 1 : row.getKeyVer();
        String decryptedDetail = cryptoKeyService.decrypt(row.getDetailEnc(), keyVer);
        String groupTitle = trimToNull(row.getGroupTitle());

        return SupportReportDTO.ReportItem.builder()
                .reportId(row.getSupportReportId())
                .memberId(row.getMemberId())
                .reporterName(NameMasker.mask(trimToNull(row.getReporterName())))
                .groupId(row.getGroupId())
                .groupTitle(groupTitle != null ? groupTitle : UNASSIGNED_GROUP_LABEL)
                .category(row.getCategory())
                .categoryLabel(formatCategoryLabel(row.getCategory()))
                .detail(decryptedDetail)
                .status(row.getStatus())
                .statusLabel(formatStatusLabel(row.getStatus()))
                .adminMemo(row.getAdminMemo())
                .handledBy(row.getHandledBy())
                .handledByLabel(formatAdminLabel(row.getHandledBy(), row.getHandledByName()))
                .createdAt(row.getCreatedAt())
                .updatedAt(row.getUpdatedAt())
                .history(history == null ? List.of() : history)
                .build();
    }

    private Map<Long, List<SupportReportDTO.StatusHistoryItem>> groupHistoryByReportId(
            List<SupportReportDTO.StatusHistoryRow> rows
    ) {
        Map<Long, List<SupportReportDTO.StatusHistoryItem>> grouped = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) {
            return grouped;
        }
        for (SupportReportDTO.StatusHistoryRow row : rows) {
            Long reportId = extractReportId(row.getDescription());
            if (reportId == null) {
                continue;
            }
            grouped.computeIfAbsent(reportId, ignored -> new ArrayList<>())
                    .add(toHistoryItem(row));
        }
        return grouped;
    }

    private List<SupportReportDTO.StatusHistoryItem> toHistoryItems(
            List<SupportReportDTO.StatusHistoryRow> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::toHistoryItem).toList();
    }

    private SupportReportDTO.StatusHistoryItem toHistoryItem(SupportReportDTO.StatusHistoryRow row) {
        String description = row.getDescription() == null ? "" : row.getDescription();
        String fromLabel = null;
        String toLabel = null;
        String memo = description;

        Matcher fullMatcher = STATUS_HISTORY_PATTERN.matcher(description);
        if (fullMatcher.matches()) {
            fromLabel = fullMatcher.group(2).trim();
            toLabel = fullMatcher.group(3).trim();
            memo = fullMatcher.group(4).trim();
        }

        return SupportReportDTO.StatusHistoryItem.builder()
                .auditLogId(row.getAuditLogId())
                .fromStatusLabel(fromLabel)
                .toStatusLabel(toLabel)
                .memo(memo)
                .rawDescription(description)
                .handledBy(row.getActorAdminId())
                .handledByLabel(formatAdminLabel(row.getActorAdminId(), row.getActorAdminName()))
                .createdAt(row.getCreatedAt())
                .build();
    }

    private static Long extractReportId(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        Matcher matcher = REPORT_PREFIX_PATTERN.matcher(description);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Map<String, Integer> buildStatusCounts(List<SupportReportDTO.StatusCountRow> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("ALL", 0);
        for (String status : STATUS_ORDER) {
            counts.put(status, 0);
        }
        int total = 0;
        if (rows != null) {
            for (SupportReportDTO.StatusCountRow row : rows) {
                if (row == null || row.getStatus() == null) {
                    continue;
                }
                int count = row.getCount() == null ? 0 : row.getCount();
                String key = row.getStatus().toUpperCase(Locale.ROOT);
                counts.put(key, count);
                total += count;
            }
        }
        counts.put("ALL", total);
        return counts;
    }

    static String normalizeCategory(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            throw badRequest("신고 유형을 선택해 주세요.");
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "ACCOUNT_CHANGE" -> "ACCOUNT_CHANGE";
            case "FUND_MISUSE" -> "FUND_MISUSE";
            case "PROOF_MISMATCH" -> "PROOF_MISMATCH";
            case "OTHER" -> "OTHER";
            default -> switch (trimmed) {
                case "계좌 변경 의심" -> "ACCOUNT_CHANGE";
                case "자금 유용 의심", "자금 유용" -> "FUND_MISUSE";
                case "거래 증빙 불일치", "증빙 불일치" -> "PROOF_MISMATCH";
                case "기타 문의", "기타" -> "OTHER";
                default -> throw badRequest("올바른 신고 유형을 선택해 주세요.");
            };
        };
    }

    static String formatCategoryLabel(String category) {
        if (category == null) {
            return "-";
        }
        return switch (category.toUpperCase(Locale.ROOT)) {
            case "ACCOUNT_CHANGE" -> "계좌 변경 의심";
            case "FUND_MISUSE" -> "자금 유용 의심";
            case "PROOF_MISMATCH" -> "거래 증빙 불일치";
            case "OTHER" -> "기타 문의";
            default -> category;
        };
    }

    static String normalizeStatusFilter(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null || "ALL".equalsIgnoreCase(trimmed) || "전체".equals(trimmed)) {
            return null;
        }
        return normalizeStatus(trimmed);
    }

    static String normalizeStatus(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            throw badRequest("변경할 처리 상태를 선택해 주세요.");
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "RECEIVED" -> "RECEIVED";
            case "REVIEWING" -> "REVIEWING";
            case "MORE_INFO" -> "MORE_INFO";
            case "CLOSED" -> "CLOSED";
            case "REJECTED" -> "REJECTED";
            default -> switch (trimmed) {
                case "접수" -> "RECEIVED";
                case "검토 중" -> "REVIEWING";
                case "추가 자료 요청" -> "MORE_INFO";
                case "처리 완료" -> "CLOSED";
                case "반려" -> "REJECTED";
                default -> throw badRequest("올바른 처리 상태를 선택해 주세요.");
            };
        };
    }

    static String formatStatusLabel(String status) {
        if (status == null) {
            return "-";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "RECEIVED" -> "접수";
            case "REVIEWING" -> "검토 중";
            case "MORE_INFO" -> "추가 자료 요청";
            case "CLOSED" -> "처리 완료";
            case "REJECTED" -> "반려";
            default -> status;
        };
    }

    private static String requireDetail(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            throw badRequest("신고 상세 내용을 입력해 주세요.");
        }
        if (trimmed.length() > MAX_DETAIL_LENGTH) {
            throw badRequest("신고 상세 내용은 " + MAX_DETAIL_LENGTH + "자 이하로 입력해 주세요.");
        }
        return trimmed;
    }

    private static String requireAdminMemo(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            throw badRequest(MEMO_REQUIRED_MESSAGE);
        }
        if (trimmed.getBytes(StandardCharsets.UTF_8).length > MAX_ADMIN_MEMO_BYTES) {
            throw badRequest("처리 메모는 한글 기준 약 260자 이하로 입력해 주세요.");
        }
        return trimmed;
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

    static String cutBytes(String value, int maxBytes) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        String ellipsis = "…";
        int limit = maxBytes - ellipsis.getBytes(StandardCharsets.UTF_8).length;
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (used + len > limit) {
                break;
            }
            sb.appendCodePoint(cp);
            used += len;
            i += Character.charCount(cp);
        }
        return sb.append(ellipsis).toString();
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
}
