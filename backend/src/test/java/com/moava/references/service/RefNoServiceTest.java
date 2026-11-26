package com.moava.references.service;

import com.moava.common.util.NameMasker;
import com.moava.references.dto.RefNoRequest;
import com.moava.references.dto.RefNoResponse;
import com.moava.references.dto.RefProofDto;
import com.moava.references.mapper.RefNoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RefNoService 단위 테스트 (F24 거래 참조번호) — DB 없이 가짜 Mapper(Mock)로 검사 로직만 확인
 * 관리자 로그인(F41) 연동 전이라 adminId는 테스트에서 직접 넣음
 */
@ExtendWith(MockitoExtension.class)
class RefNoServiceTest {

    @Mock
    RefNoMapper refNoMapper;            // 가짜 Mapper (DB 대신)

    @InjectMocks
    RefNoService refNoService;          // 위 가짜를 넣어서 만든 진짜 Service

    static final Long ADMIN_ID = 1L;
    static final Long GROUP_ID = 10L;
    static final Long MEMBER_ID = 20L;
    static final Long PROOF_ID = 100L;

    @BeforeEach
    void setUp() {
        // 대부분의 테스트는 ACTIVE 관리자로 호출 (관리자 아님 테스트는 따로 덮어씀)
        lenient().when(refNoMapper.countActiveAdmin(ADMIN_ID)).thenReturn(1);
    }

    // ===================== 권한 · 목록 =====================

    @Test
    @DisplayName("관리자가 아니면(ACTIVE 관리자 아님) 403, 조회 안 함")
    void notAdmin_403() {
        when(refNoMapper.countActiveAdmin(99L)).thenReturn(0);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.getProofs(99L, "UNASSIGNED"));

        assertEquals(403, e.getStatusCode().value());
        verify(refNoMapper, never()).findProofs(anyBoolean());
    }

    @Test
    @DisplayName("목록: 미지정 조회는 assigned=false로 조회하고 회원 이름은 마스킹")
    void list_unassigned_masksName() {
        RefProofDto p = confirmedProof(null);
        p.setMemberName("이회원");
        when(refNoMapper.findProofs(false)).thenReturn(List.of(p));

        List<RefProofDto> list = refNoService.getProofs(ADMIN_ID, "UNASSIGNED");

        assertEquals(NameMasker.mask("이회원"), list.get(0).getMemberName());
    }

    @Test
    @DisplayName("목록: ref 값이 UNASSIGNED/ASSIGNED가 아니면 400")
    void list_wrongRef_400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.getProofs(ADMIN_ID, "ALL"));

        assertEquals(400, e.getStatusCode().value());
    }

    // ===================== 지정 =====================

    @Test
    @DisplayName("지정: 번호를 비워 보내면 다음 번호(시퀀스)로 지정 + 감사 로그 REF_ASSIGN")
    void assign_auto_success() {
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(confirmedProof(null));
        when(refNoMapper.nextRefNo()).thenReturn("MOAVA-00012");
        when(refNoMapper.countRefNo("MOAVA-00012")).thenReturn(0);
        when(refNoMapper.assignRefNo(eq(PROOF_ID), eq("MOAVA-00012"), any())).thenReturn(1);

        RefNoResponse res = refNoService.assignRefNo(ADMIN_ID, PROOF_ID, request(null, null));

        assertEquals("MOAVA-00012", res.getRefNo());
        assertNotNull(res.getRefAssignedAt());
        verify(refNoMapper).insertAdminAuditLog(eq(GROUP_ID), eq(ADMIN_ID), eq(MEMBER_ID),
                eq("REF_ASSIGN"), contains("MOAVA-00012"));
    }

    @Test
    @DisplayName("지정: 시퀀스 번호가 이미 수동으로 쓰였으면 다음 번호로 넘어감")
    void assign_auto_skipsUsedNumber() {
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(confirmedProof(null));
        when(refNoMapper.nextRefNo()).thenReturn("MOAVA-00005", "MOAVA-00006");
        when(refNoMapper.countRefNo("MOAVA-00005")).thenReturn(1);   // 이미 사용 중
        when(refNoMapper.countRefNo("MOAVA-00006")).thenReturn(0);
        when(refNoMapper.assignRefNo(eq(PROOF_ID), eq("MOAVA-00006"), any())).thenReturn(1);

        RefNoResponse res = refNoService.assignRefNo(ADMIN_ID, PROOF_ID, request(null, null));

        assertEquals("MOAVA-00006", res.getRefNo());
    }

    @Test
    @DisplayName("지정: 수동 입력 번호가 형식에 안 맞으면 400")
    void assign_manual_wrongFormat_400() {
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(confirmedProof(null));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.assignRefNo(ADMIN_ID, PROOF_ID, request("REF-1", null)));

        assertEquals(400, e.getStatusCode().value());
        verify(refNoMapper, never()).assignRefNo(any(), any(), any());
    }

    @Test
    @DisplayName("지정: 수동 입력 번호가 이미 쓰이고 있으면 409 '이미 사용 중인 참조번호입니다'")
    void assign_manual_duplicate_409() {
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(confirmedProof(null));
        when(refNoMapper.countRefNo("MOAVA-00003")).thenReturn(1);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.assignRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00003", null)));

        assertEquals(409, e.getStatusCode().value());
        assertEquals("이미 사용 중인 참조번호입니다.", e.getReason());
    }

    @Test
    @DisplayName("지정: 승인 전(PENDING) 증빙이면 409, 번호 발급 안 함")
    void assign_notConfirmed_409() {
        RefProofDto pending = confirmedProof(null);
        pending.setStatus("PENDING");
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(pending);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.assignRefNo(ADMIN_ID, PROOF_ID, request(null, null)));

        assertEquals(409, e.getStatusCode().value());
        verify(refNoMapper, never()).nextRefNo();
    }

    @Test
    @DisplayName("지정: 이미 번호가 있으면 409 (정정을 써야 함)")
    void assign_alreadyAssigned_409() {
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(confirmedProof("MOAVA-00001"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.assignRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00002", null)));

        assertEquals(409, e.getStatusCode().value());
    }

    @Test
    @DisplayName("지정: 중복 검사 직후 같은 번호가 동시에 저장되면(UNIQUE 위반) 409")
    void assign_concurrentDuplicate_409() {
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(confirmedProof(null));
        when(refNoMapper.countRefNo("MOAVA-00007")).thenReturn(0);
        when(refNoMapper.assignRefNo(eq(PROOF_ID), eq("MOAVA-00007"), any()))
                .thenThrow(new DuplicateKeyException("UK_PAYMENT_PROOF_REF_NO"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.assignRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00007", null)));

        assertEquals(409, e.getStatusCode().value());
        verify(refNoMapper, never()).insertAdminAuditLog(any(), any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("지정: 없는 증빙이면 404")
    void assign_notFound_404() {
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.assignRefNo(ADMIN_ID, PROOF_ID, request(null, null)));

        assertEquals(404, e.getStatusCode().value());
    }

    // ===================== 정정 =====================

    @Test
    @DisplayName("정정 성공: 번호 변경 + 정정 횟수 +1, 감사 로그에 이전 번호·새 번호·사유 기록")
    void correct_success() {
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(confirmedProof("MOAVA-00001"));
        when(refNoMapper.countRefNo("MOAVA-00009")).thenReturn(0);
        when(refNoMapper.correctRefNo(PROOF_ID, "MOAVA-00001", "MOAVA-00009")).thenReturn(1);

        RefNoResponse res = refNoService.correctRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00009", "오입력 정정"));

        assertEquals("MOAVA-00009", res.getRefNo());
        assertEquals(1, res.getRefCorrectCount());                 // 0회 → 1회
        verify(refNoMapper).insertAdminAuditLog(eq(GROUP_ID), eq(ADMIN_ID), eq(MEMBER_ID), eq("REF_CORRECT"),
                argThat(d ->
                        d.contains("MOAVA-00001") && d.contains("MOAVA-00009") && d.contains("오입력 정정")));
    }

    @Test
    @DisplayName("정정: 사유가 비어 있으면 400, 조회도 안 함")
    void correct_noReason_400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.correctRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00009", "  ")));

        assertEquals(400, e.getStatusCode().value());
        assertEquals("정정 사유를 입력해 주세요.", e.getReason());
        verify(refNoMapper, never()).findProof(any());
    }
    @Test
    @DisplayName("정정: 사유에 계좌번호·연락처 같은 긴 숫자가 있으면 400, 조회도 안 함")
    void correct_reasonWithLongNumber_400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.correctRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00009", "입금 계좌 110-123-456789 확인 후 정정")));

        assertEquals(400, e.getStatusCode().value());
        assertEquals("정정 사유에 계좌번호·연락처 같은 긴 숫자는 입력할 수 없습니다.", e.getReason());
        verify(refNoMapper, never()).findProof(any());
    }
    @Test
    @DisplayName("정정: 사유에 날짜+시각(2026-11-24 10시)이 있어도 계좌번호로 보지 않고 정정됨")
    void correct_reasonWithDate_ok() {
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(confirmedProof("MOAVA-00001"));
        when(refNoMapper.countRefNo("MOAVA-00009")).thenReturn(0);
        when(refNoMapper.correctRefNo(PROOF_ID, "MOAVA-00001", "MOAVA-00009")).thenReturn(1);

        RefNoResponse res = refNoService.correctRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00009", "2026-11-24 10시 입금 건 오기입"));

        assertEquals("MOAVA-00009", res.getRefNo());
    }

    @Test
    @DisplayName("정정: 날짜처럼 보이는 부분이 섞인 계좌번호(123456-78-901234)는 그대로 400")
    void correct_reasonWithAccountLikeDate_400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.correctRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00009", "계좌 123456-78-901234 확인")));

        assertEquals(400, e.getStatusCode().value());
        verify(refNoMapper, never()).findProof(any());
    }     @Test
        @DisplayName("정정: 날짜 뒤에 구분자로 긴 숫자가 이어지면(2026-11-24-12345678) 날짜로 보지 않고 400")
        void correct_reasonWithDateJoinedNumber_400() {
            ResponseStatusException e = assertThrows(ResponseStatusException.class,
                    () -> refNoService.correctRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00009", "2026-11-24-12345678 확인")));

            assertEquals(400, e.getStatusCode().value());
            verify(refNoMapper, never()).findProof(any());
        }

        @Test
        @DisplayName("정정: 날짜 뒤에 공백과 시각(2026-11-24 10:00)이 오면 날짜로 보고 정정됨")
        void correct_reasonWithDateAndTime_ok() {
            when(refNoMapper.findProof(PROOF_ID)).thenReturn(confirmedProof("MOAVA-00001"));
            when(refNoMapper.countRefNo("MOAVA-00009")).thenReturn(0);
            when(refNoMapper.correctRefNo(PROOF_ID, "MOAVA-00001", "MOAVA-00009")).thenReturn(1);

            RefNoResponse res = refNoService.correctRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00009", "2026-11-24 10:00 입금 건 오기입"));

            assertEquals("MOAVA-00009", res.getRefNo());
        }
    @Test
    @DisplayName("정정: 번호가 아직 없는 증빙이면 409")
    void correct_notAssigned_409() {
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(confirmedProof(null));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.correctRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00009", "오입력 정정")));

        assertEquals(409, e.getStatusCode().value());
    }

    @Test
    @DisplayName("정정: 현재 번호와 같은 번호면 400")
    void correct_sameNumber_400() {
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(confirmedProof("MOAVA-00001"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.correctRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00001", "오입력 정정")));

        assertEquals(400, e.getStatusCode().value());
        verify(refNoMapper, never()).correctRefNo(any(), any(), any());
    }

    @Test
    @DisplayName("정정: 그 사이 다른 관리자가 먼저 정정했으면(0건 갱신) 409, 감사 로그 안 남김")
    void correct_concurrent_409() {
        when(refNoMapper.findProof(PROOF_ID)).thenReturn(confirmedProof("MOAVA-00001"));
        when(refNoMapper.countRefNo("MOAVA-00009")).thenReturn(0);
        when(refNoMapper.correctRefNo(PROOF_ID, "MOAVA-00001", "MOAVA-00009")).thenReturn(0);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> refNoService.correctRefNo(ADMIN_ID, PROOF_ID, request("MOAVA-00009", "오입력 정정")));

        assertEquals(409, e.getStatusCode().value());
        verify(refNoMapper, never()).insertAdminAuditLog(any(), any(), any(), anyString(), anyString());
    }

    // ===================== 제안 =====================

    @Test
    @DisplayName("제안: 시퀀스를 소모하지 않는 미리 보기 번호를 돌려줌")
    void suggest_peekOnly() {
        when(refNoMapper.peekNextRefNo()).thenReturn("MOAVA-00012");

        RefNoResponse res = refNoService.suggestRefNo(ADMIN_ID);

        assertEquals("MOAVA-00012", res.getRefNo());
        verify(refNoMapper, never()).nextRefNo();       // NEXTVAL(소모)은 부르지 않음
        assertNull(res.getProofId());                   // 제안 응답에는 refNo만
    }

    // ===================== 도우미 =====================

    // 모임장 승인(CONFIRMED)된 증빙. refNo가 null이면 미지정
    private RefProofDto confirmedProof(String refNo) {
        RefProofDto p = new RefProofDto();
        p.setPaymentProofId(PROOF_ID);
        p.setGroupId(GROUP_ID);
        p.setMemberId(MEMBER_ID);
        p.setStatus("CONFIRMED");
        p.setRefNo(refNo);
        p.setRefCorrectCount(0);
        return p;
    }

    private RefNoRequest request(String refNo, String reason) {
        RefNoRequest req = new RefNoRequest();
        req.setRefNo(refNo);
        req.setReason(reason);
        return req;
    }
}