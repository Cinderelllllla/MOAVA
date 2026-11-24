package com.moava.proofs.service;

import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import com.moava.proofs.dto.PaymentProofDto;
import com.moava.proofs.dto.PaymentProofReviewRequest;
import com.moava.proofs.dto.ProofActionResponse;
import com.moava.proofs.dto.ProofGroupDto;
import com.moava.proofs.mapper.PaymentProofMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * F23 증빙 승인 · 보완 요청 (모임장)
 */
@Service
@RequiredArgsConstructor
public class ProofReviewService {

    private final PaymentProofMapper paymentProofMapper;
    private final NotificationService notificationService;
    private final ProofAccessChecker proofAccessChecker;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * F23 증빙 승인·보완 요청
     *
     * @param memberId 로그인 회원 ID (JWT principal)
     * @return 심사 결과 (증빙 ID, 변경된 상태, 심사 시각)
     */
    @Transactional
    public ProofActionResponse reviewProof(Long memberId, Long groupId, Long proofId, PaymentProofReviewRequest req) {
        // 1. 결정 값 확인
        String decision = req.getDecision();
        if (!"CONFIRMED".equals(decision) && !"REVISION".equals(decision)) {
            throw badRequest("심사 결과는 CONFIRMED 또는 REVISION만 가능합니다.");
        }

        // 2. 모임 존재 + 모임장 확인 (REQ-SEC-04 서버 재검증)
        ProofGroupDto group = proofAccessChecker.getGroupForUpdate(groupId);
        if (!group.getOwnerId().equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 모임장만 승인할 수 있어요.");
        }
        if ("DISSOLVED".equals(group.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "해산된 모임의 증빙은 심사할 수 없습니다.");
        }

        // 3. 증빙 존재 (이 모임의 증빙인지까지 확인)
        PaymentProofDto proof = proofAccessChecker.getProofOrThrow(groupId, proofId);

        // 4. 확인 대기(PENDING) 상태만 심사 가능
        if (!"PENDING".equals(proof.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "확인 대기 중인 증빙만 심사할 수 있습니다.");
        }

        // 5. 보완 요청이면 사유 필수, 승인이면 사유 저장 안 함
        String memo = null;
        if ("REVISION".equals(decision)) {
            if (req.getReviewMemo() == null || req.getReviewMemo().isBlank()) {
                throw badRequest("보완 사유를 입력해 주세요.");
            }
            memo = req.getReviewMemo().trim();
            if (memo.getBytes(StandardCharsets.UTF_8).length > 500) {   // VARCHAR2(500) = 500바이트, 한글 1자 = 3바이트
                throw badRequest("보완 사유는 한글 기준 약 160자까지 입력할 수 있습니다.");
            }
        }

        // 6. 상태 변경 (PENDING 조건 UPDATE → 0건이면 그 사이에 다른 요청이 먼저 처리한 것)
        //    심사 시각은 여기서 한 번 만들어 DB 저장값과 응답값을 같게 맞춤 (DATE 컬럼이라 초 단위까지)
        LocalDateTime reviewedAt = LocalDateTime.now(SEOUL).withNano(0);
        int updated = paymentProofMapper.updateProofReview(proofId, decision, memberId, memo, reviewedAt);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 증빙입니다.");
        }

        boolean confirmed = "CONFIRMED".equals(decision);

        // 7. 승인 시 같은 회원·귀속 월 미납 건 해소 (F23-2, F26 연계) — 미납 건이 없으면 0행이라 결과 확인 안 함
        if (confirmed) {
            paymentProofMapper.resolveOverdueCase(groupId, proof.getMemberId(), proof.getPayMonth());
        }

        // 8. 보완 요청이면 작성 회원에게 알림 (승인 알림은 보내지 않음 — 알림 README: 알림 축소 결정)
        if (!confirmed) {
            notificationService.notifyMember(proof.getMemberId(), NotifType.PROOF_REVISION, proofId,
                    Map.of("group", group.getTitle(),
                            "round", proof.getRoundNo(),
                            "reason", memo,
                            "groupId", groupId));
        }

        // 9. 감사 로그
        paymentProofMapper.insertAuditLog(groupId, memberId, proof.getMemberId(),
                confirmed ? "PROOF_CONFIRM" : "PROOF_REVISION",
                (confirmed ? "증빙 승인 #" : "증빙 보완 요청 #") + proofId
                        + " (귀속 월 " + proof.getPayMonth() + ", " + proof.getRoundNo() + "회차)");

        return ProofActionResponse.reviewed(proofId, decision, reviewedAt.atZone(SEOUL).toOffsetDateTime());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}