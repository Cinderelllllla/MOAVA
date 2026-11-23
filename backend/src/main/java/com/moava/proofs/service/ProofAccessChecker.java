package com.moava.proofs.service;

import com.moava.proofs.dto.PaymentProofDto;
import com.moava.proofs.dto.ProofGroupDto;
import com.moava.proofs.mapper.PaymentProofMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 증빙 기능 공통 조회·권한 검사 (등록·조회·심사 서비스가 함께 사용)
 * - 권한은 화면이 아니라 서버에서 다시 확인 (REQ-SEC-04)
 */
@Component
@RequiredArgsConstructor
public class ProofAccessChecker {

    private final PaymentProofMapper paymentProofMapper;

    /** 모임 조회 + 행 잠금(FOR UPDATE) — 해산 처리와 동시에 진행되지 않게 (등록·재제출·심사) */
    public ProofGroupDto getGroupForUpdate(Long groupId) {
        if (paymentProofMapper.lockGroupForProofUpdate(groupId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다.");
        }
        return getGroupOrThrow(groupId);
    }

    /** 모임 조회 — 없으면 404 */
    public ProofGroupDto getGroupOrThrow(Long groupId) {
        ProofGroupDto group = paymentProofMapper.findGroupForProof(groupId);
        if (group == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다.");
        }
        return group;
    }

    /** 증빙 조회 (경로의 groupId 소속인 것만) — 없으면 404 */
    public PaymentProofDto getProofOrThrow(Long groupId, Long proofId) {
        PaymentProofDto proof = paymentProofMapper.findProofForReview(groupId, proofId);
        if (proof == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 증빙입니다.");
        }
        return proof;
    }

    /** ACTIVE 구성원이 아니면 403 (등록·재제출) */
    public void checkActiveMember(Long groupId, Long memberId, String forbiddenMessage) {
        if (paymentProofMapper.countActiveMember(groupId, memberId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
    }

    /**
     * 모임장 또는 ACTIVE 구성원인지 확인 (목록·상세)
     *
     * @return 모임장이면 true, 일반 구성원이면 false. 둘 다 아니면 403
     */
    public boolean checkOwnerOrMember(ProofGroupDto group, Long memberId, String forbiddenMessage) {
        boolean isOwner = memberId.equals(group.getOwnerId());
        if (!isOwner) {
            checkActiveMember(group.getGroupId(), memberId, forbiddenMessage);
        }
        return isOwner;
    }
}