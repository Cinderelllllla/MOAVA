package com.moava.proofs.service;

import com.moava.common.util.NameMasker;
import com.moava.crypto.service.CryptoKeyService;
import com.moava.notification.common.service.NotificationService;
import com.moava.proofs.dto.PaymentProofCreateRequest;
import com.moava.proofs.dto.PaymentProofDetailDto;
import com.moava.proofs.dto.PaymentProofDto;
import com.moava.proofs.dto.PaymentProofFileDto;
import com.moava.proofs.dto.PaymentProofListDto;
import com.moava.proofs.dto.PaymentProofResubmitRequest;
import com.moava.proofs.dto.PaymentProofReviewRequest;
import com.moava.proofs.dto.ProofFileContent;
import com.moava.proofs.dto.ProofGroupDto;
import com.moava.proofs.dto.ProofReviewResponse;
import com.moava.proofs.mapper.PaymentProofMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
 * 암호화는 가짜 CryptoKeyService가 앞에 "ENC:"를 붙이는 방식으로 흉내 냄
 */
@ExtendWith(MockitoExtension.class)
class PaymentProofServiceTest {

    @Mock
    PaymentProofMapper paymentProofMapper;          // 가짜 Mapper (DB 대신)

    @Mock
    NotificationService notificationService;        // 가짜 알림 서비스

    @Mock
    CryptoKeyService cryptoKeyService;              // 가짜 암호화 서비스

    @InjectMocks
    PaymentProofService paymentProofService;        // 위 가짜들을 넣어서 만든 진짜 Service

    @TempDir
    Path tempDir;                                   // 테스트마다 새로 생기고 끝나면 지워지는 폴더

    static final Long GROUP_ID = 1L;
    static final Long OWNER_ID = 10L;    // 모임장
    static final Long MEMBER_ID = 20L;   // 일반 회원
    static final Long OTHER_ID = 30L;    // 다른 회원
    static final Long PROOF_ID = 100L;
    static final Long FILE_ID = 1L;

    // PNG 매직 넘버로 시작하는 원본 파일 내용 (미리보기 테스트용)
    static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 9, 9, 9};

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
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);

        PaymentProofCreateRequest req = validRequest();
        req.setAmount(50000L);                       // 약정액(10만 원)과 다름

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.registerProof(MEMBER_ID, req, List.of()));

        assertEquals(400, e.getStatusCode().value());
        verify(paymentProofMapper, never()).insertProof(any());
    }

    @Test
    @DisplayName("심사: 모임장이 아니면 403")
    void review_notOwner_403() {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);

        PaymentProofReviewRequest req = new PaymentProofReviewRequest();
        req.setDecision("CONFIRMED");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.reviewProof(MEMBER_ID, GROUP_ID, PROOF_ID, req));

        assertEquals(403, e.getStatusCode().value());
        verify(paymentProofMapper, never()).updateProofReview(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("심사: 보완 요청인데 사유가 공백이면 400")
    void review_revisionWithoutMemo_400() {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.findProofForReview(GROUP_ID, PROOF_ID)).thenReturn(pendingProof());

        PaymentProofReviewRequest req = new PaymentProofReviewRequest();
        req.setDecision("REVISION");
        req.setReviewMemo("   ");                     // 공백만 있음

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.reviewProof(OWNER_ID, GROUP_ID, PROOF_ID, req));

        assertEquals(400, e.getStatusCode().value());
        verify(paymentProofMapper, never()).updateProofReview(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("상세: 일반 회원이 남의 증빙을 보면 403")
    void detail_otherMembersProof_403() {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);

        PaymentProofDetailDto detail = new PaymentProofDetailDto();
        detail.setMemberId(OTHER_ID);                 // 다른 회원의 증빙
        when(paymentProofMapper.findProofDetail(GROUP_ID, PROOF_ID)).thenReturn(detail);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.getProofDetail(GROUP_ID, PROOF_ID, MEMBER_ID));

        assertEquals(403, e.getStatusCode().value());
        verify(paymentProofMapper, never()).findProofFileSummaries(any());
    }

    @Test
    @DisplayName("심사: 모임장이 승인하면 CONFIRMED, 미납 해소 + 감사 로그 기록")
    void review_confirm_success() {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.findProofForReview(GROUP_ID, PROOF_ID)).thenReturn(pendingProof());
        when(paymentProofMapper.updateProofReview(any(), any(), any(), any(), any())).thenReturn(1);

        PaymentProofReviewRequest req = new PaymentProofReviewRequest();
        req.setDecision("CONFIRMED");

        ProofReviewResponse res = paymentProofService.reviewProof(OWNER_ID, GROUP_ID, PROOF_ID, req);

        assertEquals("CONFIRMED", res.getStatus());
        verify(paymentProofMapper).resolveOverdueCase(GROUP_ID, MEMBER_ID, "202611");
        verify(paymentProofMapper).insertAuditLog(eq(GROUP_ID), eq(OWNER_ID), eq(MEMBER_ID),
                eq("PROOF_CONFIRM"), any());
    }

    // ===================== 파일 저장 (암호화) =====================

    @Test
    @DisplayName("등록 성공: PNG 2개가 암호화되어 저장되고 메타 2건(ENCRYPTED_YN=Y)·감사 로그 기록")
    void register_success_savesEncryptedFiles() throws IOException {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
        givenInsertProofSetsId();
        givenEncryption();

        List<MultipartFile> files = List.of(png("a.png", 1), png("b.png", 2));

        Long id = paymentProofService.registerProof(MEMBER_ID, validRequest(), files);

        assertEquals(PROOF_ID, id);
        assertEquals(2, countSavedFiles());                           // 디스크에 파일 2개
        assertTrue(readFirstSavedFile().startsWith("ENC:"));           // 원본 PNG가 아니라 암호문으로 저장됨

        ArgumentCaptor<PaymentProofFileDto> captor = ArgumentCaptor.forClass(PaymentProofFileDto.class);
        verify(paymentProofMapper, times(2)).insertProofFile(captor.capture());
        for (PaymentProofFileDto meta : captor.getAllValues()) {
            assertEquals("Y", meta.getEncryptedYn());
            assertEquals(1, meta.getKeyVer());
            assertTrue(meta.getFilePath().endsWith(".enc"));
        }
        verify(paymentProofMapper).insertAuditLog(eq(GROUP_ID), eq(MEMBER_ID), eq(null),
                eq("PROOF_UPLOAD"), any());
    }

    @Test
    @DisplayName("등록: PNG라고 했는데 내용이 가짜면 400, 파일 저장 안 함")
    void register_fakePng_400() throws IOException {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);

        MultipartFile fake = new MockMultipartFile("files", "fake.png", "image/png",
                "hello".getBytes());                  // 매직 넘버가 PNG가 아님
        List<MultipartFile> files = List.of(fake);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.registerProof(MEMBER_ID, validRequest(), files));

        assertEquals(400, e.getStatusCode().value());
        assertEquals(0, countSavedFiles());
        verify(paymentProofMapper, never()).insertProof(any());
    }

    @Test
    @DisplayName("등록: 파일 메타 저장 중 DB 오류가 나면 이미 쓴 파일도 지움")
    void register_dbErrorWhileSaving_cleansFiles() throws IOException {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
        givenInsertProofSetsId();
        givenEncryption();
        doReturn(1)                                               // 첫 번째 파일 메타는 성공
                .doThrow(new RuntimeException("DB 오류"))          // 두 번째에서 실패
                .when(paymentProofMapper).insertProofFile(any());

        List<MultipartFile> files = List.of(png("a.png", 1), png("b.png", 2));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> paymentProofService.registerProof(MEMBER_ID, validRequest(), files));

        assertEquals("DB 오류", e.getMessage());
        assertEquals(0, countSavedFiles());                       // 이미 쓴 첫 번째 파일도 정리됨
    }

    // ===================== 재제출 =====================

    @Test
    @DisplayName("재제출: 남의 증빙이면 403, DB 수정 안 함")
    void resubmit_notWriter_403() {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, OTHER_ID)).thenReturn(1);
        when(paymentProofMapper.findProofForReview(GROUP_ID, PROOF_ID)).thenReturn(revisionProof());  // 작성자는 MEMBER_ID

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.resubmitProof(OTHER_ID, GROUP_ID, PROOF_ID,
                        resubmitRequest(), List.of(png("c.png", 3))));

        assertEquals(403, e.getStatusCode().value());
        verify(paymentProofMapper, never()).updateProofResubmit(any(), any());
    }

    @Test
    @DisplayName("재제출: 이미 승인된(CONFIRMED) 증빙이면 409")
    void resubmit_confirmedProof_409() {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
        PaymentProofDto confirmed = revisionProof();
        confirmed.setStatus("CONFIRMED");
        when(paymentProofMapper.findProofForReview(GROUP_ID, PROOF_ID)).thenReturn(confirmed);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.resubmitProof(MEMBER_ID, GROUP_ID, PROOF_ID,
                        resubmitRequest(), List.of(png("c.png", 3))));

        assertEquals(409, e.getStatusCode().value());
        verify(paymentProofMapper, never()).updateProofResubmit(any(), any());
    }

    @Test
    @DisplayName("재제출 성공: 횟수 +1, 기존 파일 메타 삭제 후 새 파일 암호화 저장, 감사 로그 기록")
    void resubmit_success() throws IOException {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
        when(paymentProofMapper.findProofForReview(GROUP_ID, PROOF_ID)).thenReturn(revisionProof());  // 이전 재제출 1회
        when(paymentProofMapper.updateProofResubmit(any(), any())).thenReturn(1);
        givenEncryption();

        List<MultipartFile> files = List.of(png("c.png", 3));

        int count = paymentProofService.resubmitProof(MEMBER_ID, GROUP_ID, PROOF_ID, resubmitRequest(), files);

        assertEquals(2, count);                                        // 1회 → 2회
        verify(paymentProofMapper).deleteProofFiles(PROOF_ID);         // 기존 파일 메타 삭제
        verify(paymentProofMapper, times(1)).insertProofFile(any());   // 새 파일 메타 1건
        assertEquals(1, countSavedFiles());                            // 디스크에 새 파일 1개
        verify(paymentProofMapper).insertAuditLog(eq(GROUP_ID), eq(MEMBER_ID), eq(null),
                eq("PROOF_RESUBMIT"), any());
    }

    // ===================== 목록 =====================

    @Test
    @DisplayName("목록: 모임장이 보면 본인 이름은 그대로, 다른 회원 이름은 마스킹")
    void list_owner_masksOthers() {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);

        PaymentProofListDto mine = new PaymentProofListDto();
        mine.setMemberId(OWNER_ID);
        mine.setMemberName("김모임");
        PaymentProofListDto others = new PaymentProofListDto();
        others.setMemberId(MEMBER_ID);
        others.setMemberName("이회원");
        when(paymentProofMapper.findProofList(GROUP_ID, null)).thenReturn(List.of(mine, others));  // 모임장은 전체 조회(null)

        List<PaymentProofListDto> list = paymentProofService.getProofList(GROUP_ID, OWNER_ID);

        assertEquals("김모임", list.get(0).getMemberName());                     // 본인은 그대로
        assertNotEquals("이회원", list.get(1).getMemberName());                  // 타인은 바뀜
        assertEquals(NameMasker.mask("이회원"), list.get(1).getMemberName());    // 공통 마스킹 규칙대로
    }

    @Test
    @DisplayName("목록: 일반 회원은 본인 증빙만 조회하도록 본인 ID로 조회")
    void list_member_onlyOwn() {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.countActiveMember(GROUP_ID, MEMBER_ID)).thenReturn(1);
        when(paymentProofMapper.findProofList(GROUP_ID, MEMBER_ID)).thenReturn(List.of());

        paymentProofService.getProofList(GROUP_ID, MEMBER_ID);

        verify(paymentProofMapper).findProofList(GROUP_ID, MEMBER_ID);   // null(전체)이 아니라 본인 ID
    }

    // ===================== 파일 미리보기 =====================

    @Test
    @DisplayName("파일: 모임장이 암호화된 파일을 열면 복호화된 원본 바이트가 나옴")
    void file_owner_decryptsEncryptedFile() throws IOException {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.findProofForReview(GROUP_ID, PROOF_ID)).thenReturn(pendingProof());
        when(paymentProofMapper.findProofFile(PROOF_ID, FILE_ID)).thenReturn(fileMeta("proofs/x.enc", "Y"));
        when(cryptoKeyService.decrypt(any(), eq(1)))
                .thenAnswer(inv -> inv.<String>getArgument(0).substring(4));   // "ENC:" 떼기

        writeFile("proofs/x.enc", ("ENC:" + Base64.getEncoder().encodeToString(PNG_BYTES))
                .getBytes(StandardCharsets.US_ASCII));

        ProofFileContent file = paymentProofService.getProofFile(GROUP_ID, PROOF_ID, FILE_ID, OWNER_ID);

        assertArrayEquals(PNG_BYTES, file.getBytes());    // 원본 그대로
        assertEquals("image/png", file.getMimeType());
        assertEquals("proof-1.png", file.getFileName());  // 원본 파일명·저장 경로 대신 응답용 이름
    }

    @Test
    @DisplayName("파일: 암호화 전에 올라온 옛 파일은 복호화 없이 그대로 읽음")
    void file_plainFile_readsAsIs() throws IOException {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.findProofForReview(GROUP_ID, PROOF_ID)).thenReturn(pendingProof());
        when(paymentProofMapper.findProofFile(PROOF_ID, FILE_ID)).thenReturn(fileMeta("proofs/old.png", "N"));

        writeFile("proofs/old.png", PNG_BYTES);

        ProofFileContent file = paymentProofService.getProofFile(GROUP_ID, PROOF_ID, FILE_ID, MEMBER_ID);  // 작성자 본인

        assertArrayEquals(PNG_BYTES, file.getBytes());
        verify(cryptoKeyService, never()).decrypt(any(), eq(1));
    }

    @Test
    @DisplayName("파일: 작성자도 모임장도 아닌 회원이면 403")
    void file_otherMember_403() {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.findProofForReview(GROUP_ID, PROOF_ID)).thenReturn(pendingProof());  // 작성자는 MEMBER_ID

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.getProofFile(GROUP_ID, PROOF_ID, FILE_ID, OTHER_ID));

        assertEquals(403, e.getStatusCode().value());
        verify(paymentProofMapper, never()).findProofFile(any(), any());
    }

    @Test
    @DisplayName("파일: 이 증빙에 없는 파일 번호면 404")
    void file_notFound_404() {
        when(paymentProofMapper.findGroupForProof(GROUP_ID)).thenReturn(group);
        when(paymentProofMapper.findProofForReview(GROUP_ID, PROOF_ID)).thenReturn(pendingProof());
        when(paymentProofMapper.findProofFile(PROOF_ID, FILE_ID)).thenReturn(null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> paymentProofService.getProofFile(GROUP_ID, PROOF_ID, FILE_ID, OWNER_ID));

        assertEquals(404, e.getStatusCode().value());
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

    // 보완 요청(REVISION) 상태, 이전 재제출 1회인 일반 회원의 증빙
    private PaymentProofDto revisionProof() {
        PaymentProofDto proof = pendingProof();
        proof.setStatus("REVISION");
        proof.setResubmitCount(1);
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

    // 재제출 요청 (납입일만 다시 받음)
    private PaymentProofResubmitRequest resubmitRequest() {
        PaymentProofResubmitRequest req = new PaymentProofResubmitRequest();
        req.setPayDate(LocalDate.of(2026, 11, 7));
        return req;
    }

    // PNG 매직 넘버로 시작하는 가짜 파일 (seed를 다르게 줘서 해시 중복을 피함)
    private MockMultipartFile png(String name, int seed) {
        byte[] bytes = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, (byte) seed, 1, 2, 3};
        return new MockMultipartFile("files", name, "image/png", bytes);
    }

    // 파일 미리보기용 메타 (PNG, 키 버전 1)
    private PaymentProofFileDto fileMeta(String path, String encryptedYn) {
        PaymentProofFileDto meta = new PaymentProofFileDto();
        meta.setFileId(FILE_ID);
        meta.setPaymentProofId(PROOF_ID);
        meta.setFilePath(path);
        meta.setMimeType("image/png");
        meta.setEncryptedYn(encryptedYn);
        meta.setKeyVer(1);
        return meta;
    }

    // MyBatis selectKey처럼 INSERT 때 증빙 ID를 채워 줌
    private void givenInsertProofSetsId() {
        doAnswer(inv -> {
            PaymentProofDto p = inv.getArgument(0);
            p.setPaymentProofId(PROOF_ID);
            return 1;
        }).when(paymentProofMapper).insertProof(any());
    }

    // 가짜 암호화: 현재 키 버전 1, 암호화하면 앞에 "ENC:"를 붙임
    private void givenEncryption() {
        when(cryptoKeyService.getCurrentKeyVersion()).thenReturn(1);
        when(cryptoKeyService.encrypt(any(), eq(1))).thenAnswer(inv -> "ENC:" + inv.getArgument(0));
    }

    // 임시 폴더 안에 파일 만들기 (미리보기 테스트용)
    private void writeFile(String relativePath, byte[] content) throws IOException {
        Path target = tempDir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
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

    // 임시 폴더의 proofs 안에 저장된 첫 번째 파일 내용
    private String readFirstSavedFile() throws IOException {
        try (Stream<Path> s = Files.list(tempDir.resolve("proofs"))) {
            Path first = s.findFirst().orElseThrow();
            return Files.readString(first, StandardCharsets.US_ASCII);
        }
    }
}