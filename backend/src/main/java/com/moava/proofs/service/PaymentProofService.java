package com.moava.proofs.service;

import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import com.moava.proofs.dto.PaymentProofCreateRequest;
import com.moava.proofs.dto.PaymentProofDto;
import com.moava.proofs.dto.ProofGroupDto;
import com.moava.proofs.mapper.PaymentProofMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * F22 납입 증빙 등록 · 보완 재제출 (회원)
 * - 파일 처리는 ProofFileStorage, 공통 조회·권한은 ProofAccessChecker에 맡김
 */
@Service
@RequiredArgsConstructor
public class PaymentProofService {

    private final PaymentProofMapper paymentProofMapper;
    private final NotificationService notificationService;
    private final ProofFileStorage proofFileStorage;
    private final ProofAccessChecker proofAccessChecker;

    /**
     * F22 납입 증빙 등록
     *
     * @param memberId 로그인 회원 ID (JWT principal)
     * @return 생성된 PAYMENT_PROOF_ID
     */
    @Transactional
    public Long registerProof(Long memberId, PaymentProofCreateRequest req, List<MultipartFile> files) {
        // 1. 필수값 · 귀속 월 형식
        if (memberId == null || req == null || req.getGroupId() == null || req.getPayMonth() == null
                || req.getRoundNo() == null || req.getAmount() == null || req.getPayDate() == null) {
            throw badRequest("필수 입력값이 누락되었습니다.");
        }
        if (!req.getPayMonth().matches("^\\d{4}(0[1-9]|1[0-2])$")) {
            throw badRequest("납입 귀속 월 형식이 올바르지 않습니다. (YYYYMM)");
        }

        // 2. 모임 존재 + 해산 완료 모임 차단
        ProofGroupDto group = proofAccessChecker.getGroupOrThrow(req.getGroupId());
        if ("DISSOLVED".equals(group.getStatus())) {
            throw badRequest("해산된 모임에는 증빙을 등록할 수 없습니다.");
        }

        // 3. 참여 회원만 (REQ-SEC-04 서버 재검증)
        proofAccessChecker.checkActiveMember(req.getGroupId(), memberId, "참여 회원만 증빙을 등록할 수 있습니다.");

        // 4. 회차 범위 1 ~ MONTHS
        if (req.getRoundNo() < 1 || req.getRoundNo() > group.getMonths()) {
            throw badRequest("납입 회차는 1 ~ " + group.getMonths() + " 사이여야 합니다.");
        }

        // 5. 약정액 일치 (F22-02)
        if (!req.getAmount().equals(group.getAmount())) {
            throw badRequest("모임 약정 납입액과 일치해야 합니다.");
        }

        // 6~7. 파일 개수·형식·크기·매직 넘버 + 해시 중복 검사
        List<String> hashes = proofFileStorage.validate(files, null);

        // 8. 같은 모임·회원·귀속 월·회차 중복 제출 차단 (F22-03)
        if (paymentProofMapper.countDuplicateProof(
                req.getGroupId(), memberId, req.getPayMonth(), req.getRoundNo()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "해당 모임·귀속 월·회차에 이미 제출한 증빙이 있습니다.");
        }

        // 9. PAYMENT_PROOF 저장 (STATUS=PENDING) → selectKey로 ID가 채워짐
        PaymentProofDto proof = new PaymentProofDto();
        proof.setGroupId(req.getGroupId());
        proof.setMemberId(memberId);
        proof.setPayMonth(req.getPayMonth());
        proof.setRoundNo(req.getRoundNo());
        proof.setAmount(req.getAmount());
        proof.setPayDate(req.getPayDate());

        try {
            paymentProofMapper.insertProof(proof);
        } catch (DuplicateKeyException e) {
            // 8번 검사 직후 같은 증빙이 동시에 들어온 경우 (UK_PAYMENT_PROOF_1 위반)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "해당 모임·귀속 월·회차에 이미 제출한 증빙이 있습니다.");
        }

        // 10. 파일 저장 + 메타 INSERT
        proofFileStorage.save(proof.getPaymentProofId(), files, hashes);

        // 11. 모임장 알림 (같은 모임 하루 1건)
        notifyLeaderProofPending(group, memberId, proof.getPaymentProofId());

        // 12. 감사 로그
        paymentProofMapper.insertAuditLog(req.getGroupId(), memberId, null, "PROOF_UPLOAD",
                "증빙 등록 #" + proof.getPaymentProofId() + " (귀속 월 " + req.getPayMonth()
                        + ", " + req.getRoundNo() + "회차, 파일 " + files.size() + "개)");
        return proof.getPaymentProofId();
    }

    /**
     * F22-05 보완 재제출 (증빙 작성자)
     * - REVISION 상태만 가능. 귀속 월·회차·금액은 기존 값 유지, 납입일·파일만 교체
     * - 본인 증빙의 기존 파일은 다시 첨부 가능 (다른 증빙 파일 재사용은 계속 차단)
     *
     * @return 재제출 후 누적 횟수
     */
    @Transactional
    public int resubmitProof(Long memberId, Long groupId, Long proofId,
                             LocalDate payDate, List<MultipartFile> files) {
        // 1. 필수값
        if (memberId == null || payDate == null) {
            throw badRequest("필수 입력값이 누락되었습니다.");
        }

        // 1-2. 모임 존재 + 해산 완료 모임 차단 + ACTIVE 구성원만 (등록과 같은 기준, REQ-SEC-04)
        ProofGroupDto group = proofAccessChecker.getGroupOrThrow(groupId);
        if ("DISSOLVED".equals(group.getStatus())) {
            throw badRequest("해산된 모임에는 증빙을 재제출할 수 없습니다.");
        }
        proofAccessChecker.checkActiveMember(groupId, memberId, "참여 회원만 증빙을 재제출할 수 있습니다.");

        // 2. 증빙 존재 (이 모임의 증빙인지까지 확인)
        PaymentProofDto proof = proofAccessChecker.getProofOrThrow(groupId, proofId);

        // 3. 작성자 본인만
        if (!memberId.equals(proof.getMemberId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 제출한 증빙만 재제출할 수 있습니다.");
        }

        // 4. 보완 요청(REVISION) 상태만
        if (!"REVISION".equals(proof.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "보완 요청 상태의 증빙만 재제출할 수 있습니다.");
        }

        // 5. 새 파일 검사 (본인 증빙의 기존 파일은 해시 중복 검사에서 제외)
        List<String> hashes = proofFileStorage.validate(files, proofId);

        // 6. 상태·납입일·재제출 횟수 갱신 (REVISION 조건 UPDATE → 0건이면 다른 요청이 먼저 처리한 것)
        if (paymentProofMapper.updateProofResubmit(proofId, payDate) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 증빙입니다.");
        }

        // 7. 기존 파일 메타 삭제 → 새 파일 저장 (SORT_NO는 1부터 다시)
        List<String> oldPaths = paymentProofMapper.findProofFilePaths(proofId);
        paymentProofMapper.deleteProofFiles(proofId);
        proofFileStorage.save(proofId, files, hashes);

        // 8. 기존 디스크 파일 삭제 — DB 커밋이 성공한 뒤에만
        proofFileStorage.deleteAfterCommit(oldPaths);

        // 9. 모임장 알림 (같은 모임 하루 1건)
        notifyLeaderProofPending(group, memberId, proofId);

        // 10. 감사 로그
        paymentProofMapper.insertAuditLog(groupId, memberId, null, "PROOF_RESUBMIT",
                "증빙 재제출 #" + proofId + " (" + (proof.getResubmitCount() + 1)
                        + "번째 재제출, 파일 " + files.size() + "개)");
        return proof.getResubmitCount() + 1;
    }

    // ---------- 내부 도우미 ----------

    // 모임장에게 '확인할 증빙' 알림 — 같은 모임은 하루 1건, 모임장 본인 증빙이면 생략 (등록·재제출 공통)
    private void notifyLeaderProofPending(ProofGroupDto group, Long submitterId, Long proofId) {
        if (group.getOwnerId().equals(submitterId)) {
            return;
        }
        if (paymentProofMapper.countTodayProofPending(group.getOwnerId(), group.getGroupId()) > 0) {
            return;
        }
        notificationService.notifyMember(group.getOwnerId(), NotifType.PROOF_PENDING, proofId,
                Map.of("group", group.getTitle(), "groupId", group.getGroupId()));
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}