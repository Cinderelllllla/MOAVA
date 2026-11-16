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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentProofService 단위 테스트 — DB 없이 가짜 Mapper(Mock)로 검사 로직만 확인
 * 파일 저장 테스트는 JUnit 임시 폴더(@TempDir)를 업로드 폴더로 사용
 */
@ExtendWith(MockitoExtension.class)
class PaymentProofServiceTest {

    @Mock
    PaymentProofMapper paymentProofMapper;          // 가짜 Mapper (DB 대신)

    @Mock
    NotificationService notificationService;        // 가짜 알림 서비스

    @InjectMocks
    PaymentProofService paymentProofService;        // 위 가짜들을 넣어서 만든 진짜 Service

    @TempDir
    Path tempDir;                                   // 테스트마다 새로 생기고 끝나면 지워지는 폴더

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

        // @Value("${moava.upload.dir}") 대신 임시 폴더를 업로드 경로로 지정
        ReflectionTestUtils.setField(paymentProofService, "uploadDir", tempDir.toString());
    }

    // ===================== 검사 로직 =====================

    @Test
    @DisplayName("등록: 약정액과 다르면 400, DB 저장 안 함")
    void register_amountMismatch_400() {
        // given
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);

        PaymentProofCreateRequest req = validRequest();
        req.setAmount(50000L);                       // 약정액(10만 원)과 다름

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

    // ===================== 파일 저장 =====================

    @Test
    @DisplayName("등록 성공: PNG 2개가 실제로 저장되고 메타 2건·감사 로그 기록")
    void register_success_savesFiles() throws IOException {
        // given
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
        givenInsertProofSetsId();

        List<MultipartFile> files = List.of(png("a.png", 1), png("b.png", 2));

        // when
        Long id = paymentProofService.registerProof(MEMBER_ID, validRequest(), files);

        // then
        assertEquals(PROOF_ID, id);
        assertEquals(2, countSavedFiles());                           // 디스크에 파일 2개
        verify(paymentProofMapper, times(2)).insertProofFile(any());  // 파일 메타 2건
        verify(paymentProofMapper).insertAuditLog(eq(GROUP_ID), eq(MEMBER_ID), eq(null),
                eq("PROOF_UPLOAD"), any());
    }

    @Test
    @DisplayName("등록: PNG라고 했는데 내용이 가짜면 400, 파일 저장 안 함")
    void register_fakePng_400() throws IOException {
        // given
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);

        MultipartFile fake = new MockMultipartFile("files", "fake.png", "image/png",
                "hello".getBytes());                  // 매직 넘버가 PNG가 아님
        List<MultipartFile> files = List.of(fake);

        // when
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.registerProof(MEMBER_ID, validRequest(), files));

        // then
        assertEquals(400, e.getStatusCode().value());
        assertEquals(0, countSavedFiles());
        verify(paymentProofMapper, never()).insertProof(any());
    }

    @Test
    @DisplayName("등록: 파일 메타 저장 중 DB 오류가 나면 이미 쓴 파일도 지움")
    void register_dbErrorWhileSaving_cleansFiles() throws IOException {
        // given
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
        givenInsertProofSetsId();
        doReturn(1)                                               // 첫 번째 파일 메타는 성공
                .doThrow(new RuntimeException("DB 오류"))          // 두 번째에서 실패
                .when(paymentProofMapper).insertProofFile(any());

        List<MultipartFile> files = List.of(png("a.png", 1), png("b.png", 2));

        // when
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> paymentProofService.registerProof(MEMBER_ID, validRequest(), files));

        // then
        assertEquals("DB 오류", e.getMessage());
        assertEquals(0, countSavedFiles());                       // 이미 쓴 첫 번째 파일도 정리됨
    }

    // ===================== 도우미 =====================

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

    // 검사를 모두 통과하는 등록 요청 (약정액 10만 원)
    private PaymentProofCreateRequest validRequest() {
        PaymentProofCreateRequest req = new PaymentProofCreateRequest();
        req.setGroupId(GROUP_ID);
        req.setPayMonth("202611");
        req.setRoundNo(1);
        req.setAmount(100000L);
        req.setPayDate(LocalDate.of(2026, 11, 5));
        return req;
    }

    // PNG 매직 넘버로 시작하는 가짜 파일 (seed를 다르게 줘서 해시 중복을 피함)
    private MockMultipartFile png(String name, int seed) {
        byte[] bytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, (byte) seed, 1, 2, 3};
        return new MockMultipartFile("files", name, "image/png", bytes);
    }

    // MyBatis selectKey처럼 INSERT 때 증빙 ID를 채워 줌
    private void givenInsertProofSetsId() {
        doAnswer(inv -> {
            PaymentProofDto p = inv.getArgument(0);
            p.setPaymentProofId(PROOF_ID);
            return 1;
        }).when(paymentProofMapper).insertProof(any());
    }

    // 임시 폴더의 proofs 안에 저장된 파일 수
    private long countSavedFiles() throws IOException {
        Path dir = tempDir.resolve("proofs");
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }
}