package com.moava.overdue.service;

import com.moava.common.util.NameMasker;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import com.moava.overdue.dto.OverdueActionRequest;
import com.moava.overdue.dto.OverdueCaseDto;
import com.moava.overdue.mapper.OverdueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F26 관리자 미납 관리 (화면 A06 /admin/overdue) — 목록 + 조치
 * 조치: 경고(WARN) / 채팅 제한(CHAT_RESTRICT) / 제한 해제(CHAT_RELEASE) / 수령 순서 후순위 검토 기록(ORDER_REVIEW)
 */
@Service
@RequiredArgsConstructor
public class OverdueAdminService {

    private static final Set<String> STATUS_FILTERS = Set.of("OPEN", "RESOLVED", "ALL");
    private static final Set<String> ACTIONS = Set.of("WARN", "CHAT_RESTRICT", "CHAT_RELEASE", "ORDER_REVIEW");
    private static final int MAX_REASON_BYTES = 500;    // SANCTION.REASON VARCHAR2(500) — 바이트 기준

    private final OverdueMapper overdueMapper;
    private final NotificationService notificationService;

    /** A06 목록 — status: OPEN(미해소) / RESOLVED(해소) / ALL */
    @Transactional(readOnly = true)
    public List<OverdueCaseDto> getCases(Long adminId, String status) {
        checkAdmin(adminId);
        if (status == null || !STATUS_FILTERS.contains(status)) {   // Set.of는 null을 넣으면 예외라 먼저 확인
            throw badRequest("status는 OPEN / RESOLVED / ALL 중 하나여야 합니다.");
        }
        List<OverdueCaseDto> cases = overdueMapper.findOverdueCases(status);
        cases.forEach(c -> c.setMemberName(NameMasker.mask(c.getMemberName())));
        return cases;
    }

    /**
     * A06 조치 — 처리 후 바뀐 미납 건을 돌려줌
     * - 해소(RESOLVED)된 건은 '제한 해제'만 가능 (납입 확인 후에도 남은 채팅 제한을 풀 수 있게)
     * - 모든 조치는 AUDIT_LOG.ACTOR_ADMIN_ID에 관리자 기록 (명세 F26-3)
     */
    @Transactional
    public OverdueCaseDto applyAction(Long adminId, Long overdueCaseId, OverdueActionRequest req) {
        checkAdmin(adminId);
        String action = req == null ? null : req.getAction();
        if (action == null || !ACTIONS.contains(action)) {
            throw badRequest("action은 WARN / CHAT_RESTRICT / CHAT_RELEASE / ORDER_REVIEW 중 하나여야 합니다.");
        }
        OverdueCaseDto c = overdueMapper.findOverdueCaseById(overdueCaseId);
        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "미납 건을 찾을 수 없습니다.");
        }
        if ("RESOLVED".equals(c.getStatus()) && !"CHAT_RELEASE".equals(action)) {
            throw conflict("이미 납입이 확인돼 해소된 미납 건입니다.");
        }

        switch (action) {
            case "WARN" -> warn(adminId, c);
            case "CHAT_RESTRICT" -> restrictChat(adminId, c, requireReason(req.getReason()));
            case "CHAT_RELEASE" -> releaseChat(adminId, c, requireReason(req.getReason()));
            case "ORDER_REVIEW" -> recordOrderReview(adminId, c, requireReason(req.getReason()));
            default -> throw badRequest("지원하지 않는 조치입니다.");   // 위에서 막히지만 switch 완결용
        }

        OverdueCaseDto updated = overdueMapper.findOverdueCaseById(overdueCaseId);
        updated.setMemberName(NameMasker.mask(updated.getMemberName()));
        return updated;
    }

    // ---------- 조치 4종 ----------

    // 경고: WARNED + WARNED_AT, 회원 알림(+SMS), AUDIT_LOG(OVERDUE_WARN). SANCTION·WARNING_COUNT는 안 건드림 (F30 모임장 경고와 별개)
    private void warn(Long adminId, OverdueCaseDto c) {
        if (overdueMapper.updateCaseWarned(c.getOverdueCaseId()) == 0) {
            throw conflict("이미 경고를 보냈거나 경고할 수 없는 상태입니다.");
        }
        // TODO(알림 코드표 v3.1 PR 머지 후 주석 해제): 경고만 SMS 발송 (F36, API 명세서 7.4)
        // notificationService.notifyMember(c.getMemberId(), NotifType.OVERDUE_WARNED, c.getOverdueCaseId(),
        //         Map.of("groupId", c.getGroupId(), "group", c.getGroupTitle(), "month", monthLabel(c.getPayMonth())));
        overdueMapper.insertAdminAuditLog(c.getGroupId(), adminId, c.getMemberId(), "OVERDUE_WARN",
                "미납 경고 #" + c.getOverdueCaseId() + " (" + c.getPayMonth() + ")");
    }

    // 채팅 제한: 모임 규칙이 ALERT_ONLY면 불가. GYE_MEMBER N→Y, 미납 건 RESTRICTED, SANCTION, 회원 알림
    private void restrictChat(Long adminId, OverdueCaseDto c, String reason) {
        if ("ALERT_ONLY".equals(c.getOverduePolicy())) {
            throw conflict("이 모임은 미납 시 알림만 보내는 규칙이라 채팅을 제한할 수 없습니다.");
        }
        if (overdueMapper.updateChatRestricted(c.getGroupId(), c.getMemberId(), "N", "Y") == 0) {
            throw conflict("이미 채팅이 제한됐거나 모임 회원이 아닙니다.");
        }
        overdueMapper.updateCaseRestricted(c.getOverdueCaseId());   // 해제 후 다시 제한하면 이미 RESTRICTED라 0건 — 그대로 둠
        Long sanctionId = saveSanction("CHAT_RESTRICT", adminId, c, reason);
        notificationService.notifyMember(c.getMemberId(), NotifType.SANCTION, sanctionId,
                Map.of("scope", c.getGroupTitle(), "action", "미납으로 모임 채팅이 제한됐어요."));
        overdueMapper.insertAdminAuditLog(c.getGroupId(), adminId, c.getMemberId(), "CHAT_RESTRICT",
                "채팅 제한 SANCTION #" + sanctionId + " (미납 #" + c.getOverdueCaseId() + ")");
    }

    // 제한 해제: GYE_MEMBER Y→N, SANCTION, 회원 알림. 미납 건 상태는 그대로 (명세에 상태 변경 없음)
    private void releaseChat(Long adminId, OverdueCaseDto c, String reason) {
        if (overdueMapper.updateChatRestricted(c.getGroupId(), c.getMemberId(), "Y", "N") == 0) {
            throw conflict("채팅이 제한된 회원이 아닙니다.");
        }
        Long sanctionId = saveSanction("CHAT_RELEASE", adminId, c, reason);
        notificationService.notifyMember(c.getMemberId(), NotifType.SANCTION, sanctionId,
                Map.of("scope", c.getGroupTitle(), "action", "모임 채팅 제한이 해제됐어요."));
        overdueMapper.insertAdminAuditLog(c.getGroupId(), adminId, c.getMemberId(), "CHAT_RELEASE",
                "채팅 제한 해제 SANCTION #" + sanctionId + " (미납 #" + c.getOverdueCaseId() + ")");
    }

    // 후순위 검토 기록: SANCTION만 남김 (수령 순서 자동 변경 없음, 회원 알림 없음)
    private void recordOrderReview(Long adminId, OverdueCaseDto c, String reason) {
        Long sanctionId = saveSanction("ORDER_REVIEW", adminId, c, reason);
        overdueMapper.insertAdminAuditLog(c.getGroupId(), adminId, c.getMemberId(), "ORDER_REVIEW",
                "후순위 검토 기록 SANCTION #" + sanctionId + " (미납 #" + c.getOverdueCaseId() + ")");
    }

    // ---------- 내부 도우미 ----------

    // 사유는 SANCTION.REASON에만 저장, 감사 로그에는 번호만 (감사 로그에 자유 입력값 안 넣음)
    private Long saveSanction(String type, Long adminId, OverdueCaseDto c, String reason) {
        Long sanctionId = overdueMapper.nextSanctionId();
        overdueMapper.insertSanction(sanctionId, type, c.getMemberId(), c.getGroupId(), adminId, reason);
        return sanctionId;
    }

    private static String requireReason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) {
            throw badRequest("조치 사유를 입력해 주세요.");
        }
        if (r.getBytes(StandardCharsets.UTF_8).length > MAX_REASON_BYTES) {
            throw badRequest("조치 사유는 한글 기준 약 160자까지 입력할 수 있습니다.");
        }
        return r;
    }

    // ACTIVE 관리자만 (비활성 관리자의 토큰이 만료 전이어도 차단, F41-4)
    private void checkAdmin(Long adminId) {
        if (adminId == null || overdueMapper.countActiveAdmin(adminId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 이용할 수 있습니다.");
        }
    }

    // "202611" → "11월분" (경고 알림 TODO에서 사용 예정)
    private static String monthLabel(String payMonth) {
        return Integer.parseInt(payMonth.substring(4)) + "월분";
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}