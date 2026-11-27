package com.moava.group.payout.service;

import com.moava.common.util.NameMasker;
import com.moava.group.payout.dto.PayoutReceiptActionResponse;
import com.moava.group.payout.dto.PayoutReceiptMemberItem;
import com.moava.group.payout.dto.PayoutReceiptMemberRow;
import com.moava.group.payout.dto.PayoutReceiptSummaryResponse;
import com.moava.group.payout.dto.PayoutReceiptTarget;
import com.moava.group.payout.mapper.PayoutReceiptMapper;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * F28 곗돈 수령 완료 표시 서비스.
 * <ul>
 *   <li>조회(getSummary): 해당 모임의 ACTIVE 회원(모임장 포함)만 가능. 순서 확정 여부·회원별 수령 여부·본인 수령 여부 반환.</li>
 *   <li>표시(markReceived): 모임장만 가능. 해산 완료 모임 불가. 수령 순서 확정(ORDER_CONFIRMED_YN='Y') 전 불가.</li>
 *   <li>한 트랜잭션: GYE_GROUP 행 잠금 → GYE_MEMBER(PAYOUT_RECEIVED_YN='Y', PAYOUT_RECEIVED_AT=SYSDATE) →
 *       AUDIT_LOG(PAYOUT_RECEIVED) → NOTIFICATION(NotifType.PAYOUT_RECEIVED).</li>
 *   <li>수령 완료자는 이후 F29 일반 탈퇴가 차단된다 (WithdrawalService 검증 + 프론트 버튼 비활성).</li>
 * </ul>
 */
@Service
public class PayoutReceiptService {

    public static final String AUDIT_EVENT_PAYOUT_RECEIVED = "PAYOUT_RECEIVED";

    private static final int DESCRIPTION_MAX_BYTES = 1000;

    private final PayoutReceiptMapper mapper;
    private final NotificationService notifications;

    public PayoutReceiptService(PayoutReceiptMapper mapper, NotificationService notifications) {
        this.mapper = mapper;
        this.notifications = notifications;
    }

    /** GET /api/v1/groups/{groupId}/payout-receipts */
    @Transactional(readOnly = true)
    public PayoutReceiptSummaryResponse getSummary(long requesterId, long groupId) {
        if (groupId <= 0) {
            throw new PayoutReceiptException(HttpStatus.BAD_REQUEST, "INVALID_GROUP_ID", "올바른 모임 번호를 입력해 주세요.");
        }

        PayoutReceiptTarget me = mapper.selectTarget(groupId, requesterId);
        if (me == null) {
            throw new PayoutReceiptException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "모임을 찾을 수 없어요.");
        }
        boolean isLeader = me.isOwner(requesterId);
        if (!isLeader && !me.isActiveMember()) {
            throw new PayoutReceiptException(HttpStatus.FORBIDDEN, "NOT_GROUP_MEMBER", "이 모임에 참여 중인 회원만 조회할 수 있어요.");
        }

        List<PayoutReceiptMemberRow> rows = mapper.selectMemberReceipts(groupId);
        List<PayoutReceiptMemberItem> members = rows == null
                ? List.of()
                : rows.stream()
                        .map(row -> new PayoutReceiptMemberItem(
                                row.memberId(),
                                formatDisplayName(row.memberName(), row.memberId() == requesterId),
                                row.role(),
                                row.confirmedRoundNo(),
                                row.payoutReceived(),
                                row.payoutReceivedAt(),
                                row.memberId() == requesterId
                        ))
                        .toList();

        return new PayoutReceiptSummaryResponse(
                me.groupId(),
                me.groupTitle(),
                me.groupStatus(),
                me.orderConfirmed(),
                isLeader,
                me.payoutReceived(),
                me.payoutReceivedAt(),
                members
        );
    }

    /** POST /api/v1/groups/{groupId}/members/{memberId}/payout-received */
    @Transactional
    public PayoutReceiptActionResponse markReceived(long leaderId, long groupId, long targetMemberId) {
        if (groupId <= 0 || targetMemberId <= 0) {
            throw new PayoutReceiptException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해 주세요.");
        }

        // F29 중도 탈퇴 신청과 동일한 행을 잠가 순서 보장 및 중복 처리 방지
        mapper.lockGroup(groupId);

        PayoutReceiptTarget target = mapper.selectTarget(groupId, targetMemberId);
        if (target == null) {
            throw new PayoutReceiptException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "모임을 찾을 수 없어요.");
        }
        if (!target.isOwner(leaderId)) {
            throw new PayoutReceiptException(HttpStatus.FORBIDDEN, "LEADER_ONLY", "모임장만 곗돈 수령 완료를 표시할 수 있어요.");
        }
        if (target.dissolved()) {
            throw new PayoutReceiptException(HttpStatus.CONFLICT, "GROUP_DISSOLVED", "해산된 모임에서는 수령 완료를 표시할 수 없어요.");
        }
        if (!target.orderConfirmed()) {
            throw new PayoutReceiptException(
                    HttpStatus.CONFLICT,
                    "ORDER_NOT_CONFIRMED",
                    "수령 순서가 확정된 모임에서만 곗돈 수령 완료를 표시할 수 있어요."
            );
        }
        if (!target.isActiveMember()) {
            throw new PayoutReceiptException(HttpStatus.NOT_FOUND, "NOT_GROUP_MEMBER", "이 모임에 참여 중인 회원이 아니에요.");
        }
        if (target.payoutReceived()) {
            throw new PayoutReceiptException(HttpStatus.CONFLICT, "ALREADY_RECEIVED", "이미 곗돈 수령 완료로 표시된 회원이에요.");
        }

        if (mapper.markPayoutReceived(groupId, targetMemberId) == 0) {
            throw new PayoutReceiptException(HttpStatus.CONFLICT, "ALREADY_RECEIVED", "이미 곗돈 수령 완료로 표시된 회원이에요.");
        }

        PayoutReceiptTarget updated = mapper.selectTarget(groupId, targetMemberId);
        String receivedAt = updated != null ? updated.payoutReceivedAt() : null;
        String maskedName = NameMasker.mask(target.memberName());
        Integer roundNo = target.confirmedRoundNo();

        String roundText = roundNo != null ? " (" + roundNo + "회차)" : "";
        String auditDesc = cutBytes(
                "곗돈 수령 완료 표시: " + maskedName + roundText + " · 기록용 표시(실제 송금 없음)",
                DESCRIPTION_MAX_BYTES
        );
        mapper.insertAuditLog(groupId, leaderId, targetMemberId, AUDIT_EVENT_PAYOUT_RECEIVED, auditDesc);

        String groupTitle = target.groupTitle() != null ? target.groupTitle() : "";
        notifications.notifyMember(
                targetMemberId,
                NotifType.PAYOUT_RECEIVED,
                groupId,
                Map.of(
                        "group", groupTitle,
                        "round", roundText,
                        "groupId", groupId
                )
        );

        return new PayoutReceiptActionResponse(
                groupId,
                targetMemberId,
                maskedName,
                roundNo,
                true,
                receivedAt
        );
    }

    private static String formatDisplayName(String rawName, boolean isSelf) {
        if (rawName == null || rawName.isBlank()) {
            return "회원";
        }
        return isSelf ? rawName.trim() : NameMasker.mask(rawName.trim());
    }

    private static String cutBytes(String value, int maxBytes) {
        if (value == null) {
            return "";
        }
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        String suffix = "…";
        int limit = maxBytes - suffix.getBytes(StandardCharsets.UTF_8).length;
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
        return sb.append(suffix).toString();
    }
}
