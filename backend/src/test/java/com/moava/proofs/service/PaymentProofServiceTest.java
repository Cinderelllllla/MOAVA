package com.moava.proofs.service;

import com.moava.notification.common.service.NotificationService;
import com.moava.proofs.dto.PaymentProofCreateRequest;
import com.moava.proofs.dto.PaymentProofDetailDto;
import com.moava.proofs.dto.PaymentProofDto;
import com.moava.proofs.dto.PaymentProofReviewRequest;
import com.moava.proofs.dto.ProofGroupDto;
import com.moava.proofs.dto.ProofReviewResponse;
import com.moava.proofs.mapper.PaymentProofMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentProofService 단위 테스트 — DB 없이 가짜 Mapper(Mock)로 검사 로직만 확인
 */
@ExtendWith(MockitoExtension.class)
class PaymentProofServiceTest {

    @Mock
    PaymentProofMapper paymentProofMapper;          // 가짜 Mapper (DB 대신)

    @Mock
    NotificationService notificationService;        // 가짜 알림 서비스

    @InjectMocks
    PaymentProofService paymentProofService;        // 위 가짜들을 넣어서 만든 진짜 Service

    static final Long GROUP_ID = 1L;
    static final Long OWNER_ID = 10L;    // 모임장
    static final Long MEMBER_ID = 20L;   // 일반 회원
    static final Long OTHER_ID = 30L;    // 다른 회원
    static final Long PROOF_ID = 100L;

    ProofGroupDto group;

    @BeforeEach
    void setUp() {
        // 모든 테스트에서 쓰는 모임: 약정액 10만 원, 10회차, 운영 중
        group = new ProofGroupDto();
        group.setGroupId(GROUP_ID);
        group.setOwnerId(OWNER_ID);
        group.setTitle("테스트 모임");
        group.setAmount(100000L);
        group.setMonths(10);
        group.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("등록: 약정액과 다르면 400, DB 저장 안 함")
    void register_amountMismatch_400() {
        // given
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);

        PaymentProofCreateRequest req = new PaymentProofCreateRequest();
        req.setGroupId(GROUP_ID);
        req.setPayMonth("202611");
        req.setRoundNo(1);
        req.setAmount(50000L);                       // 약정액(10만 원)과 다름
        req.setPayDate(LocalDate.of(2026, 11, 5));

        // when
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.registerProof(MEMBER_ID, req, List.of()));

        // then
        assertEquals(400, e.getStatusCode().value());
        verify(paymentProofMapper, never()).insertProof(any());
    }

    @Test
    @DisplayName("심사: 모임장이 아니면 403")
    void review_notOwner_403() {
        // given
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);

        PaymentProofReviewRequest req = new PaymentProofReviewRequest();
        req.setDecision("CONFIRMED");

        // when
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.reviewProof(MEMBER_ID, GROUP_ID, PROOF_ID, req));

        // then
        assertEquals(403, e.getStatusCode().value());
        verify(paymentProofMapper, never()).updateProofReview(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("심사: 보완 요청인데 사유가 공백이면 400")
    void review_revisionWithoutMemo_400() {
        // given
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.findProofForReview(GROUP_ID, PROOF_ID)).thenReturn(pendingProof());

        PaymentProofReviewRequest req = new PaymentProofReviewRequest();
        req.setDecision("REVISION");
        req.setReviewMemo("   ");                     // 공백만 있음

        // when
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.reviewProof(OWNER_ID, GROUP_ID, PROOF_ID, req));

        // then
        assertEquals(400, e.getStatusCode().value());
        verify(paymentProofMapper, never()).updateProofReview(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("상세: 일반 회원이 남의 증빙을 보면 403")
    void detail_otherMembersProof_403() {
        // given
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);

        PaymentProofDetailDto detail = new PaymentProofDetailDto();
        detail.setMemberId(OTHER_ID);                 // 다른 회원의 증빙
        when(paymentProofMapper.findProofDetail(GROUP_ID, PROOF_ID)).thenReturn(detail);

        // when
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.getProofDetail(GROUP_ID, PROOF_ID, MEMBER_ID));

        // then
        assertEquals(403, e.getStatusCode().value());
        verify(paymentProofMapper, never()).findProofFileSummaries(any());
    }

    @Test
    @DisplayName("심사: 모임장이 승인하면 CONFIRMED, 미납 해소 + 감사 로그 기록")
    void review_confirm_success() {
        // given
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.findProofForReview(GROUP_ID, PROOF_ID)).thenReturn(pendingProof());
        when(paymentProofMapper.updateProofReview(any(), any(), any(), any(), any())).thenReturn(1);

        PaymentProofReviewRequest req = new PaymentProofReviewRequest();
        req.setDecision("CONFIRMED");

        // when
        ProofReviewResponse res = paymentProofService.reviewProof(OWNER_ID, GROUP_ID, PROOF_ID, req);

        // then
        assertEquals("CONFIRMED", res.getStatus());
        verify(paymentProofMapper).resolveOverdueCase(GROUP_ID, MEMBER_ID, "202611");
        verify(paymentProofMapper).insertAuditLog(eq(GROUP_ID), eq(OWNER_ID), eq(MEMBER_ID),
                eq("PROOF_CONFIRM"), any());
    }

    // 확인 대기(PENDING) 상태인 일반 회원의 증빙
    private PaymentProofDto pendingProof() {
        PaymentProofDto proof = new PaymentProofDto();
        proof.setPaymentProofId(PROOF_ID);
        proof.setGroupId(GROUP_ID);
        proof.setMemberId(MEMBER_ID);
        proof.setPayMonth("202611");
        proof.setRoundNo(1);
        proof.setStatus("PENDING");
        return proof;
    }
}