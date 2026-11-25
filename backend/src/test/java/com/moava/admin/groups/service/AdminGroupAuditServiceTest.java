package com.moava.admin.groups.service;

import com.moava.admin.groups.dto.AdminGroupAuditDTO;
import com.moava.admin.groups.mapper.AdminGroupAuditMapper;
import com.moava.crypto.service.CryptoKeyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F38 AdminGroupAuditService 단위 테스트.
 * - 활성 관리자 검증, 모임 목록/상세 패널 조회 전용 검증
 * - AUDIT_LOG 필터 3종 + v3.1 관리자 필터 + 페이지당 8건 페이징 검증
 */
@ExtendWith(MockitoExtension.class)
class AdminGroupAuditServiceTest {

    @Mock
    AdminGroupAuditMapper mapper;

    @Mock
    CryptoKeyService cryptoKeyService;

    @InjectMocks
    AdminGroupAuditService service;

    @Test
    @DisplayName("비활성 또는 존재하지 않는 관리자는 403 FORBIDDEN")
    void inactiveAdmin_throws403() {
        when(mapper.countActiveAdmin(99L)).thenReturn(0);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.getGroups(99L)
        );
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(mapper, never()).selectAdminGroupList();
    }

    @Test
    @DisplayName("계모임 목록 조회: 모임장 이름 마스킹 및 상태·회원 수·계좌 인증 상태 반환")
    void getGroups_masksOwnerName() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);

        AdminGroupAuditDTO.GroupSummaryRow row = new AdminGroupAuditDTO.GroupSummaryRow();
        row.setGroupId(101L);
        row.setTitle("마포 목돈 모임");
        row.setOwnerId(10L);
        row.setOwnerName("김하늘");
        row.setStatus("ACTIVE");
        row.setVisibility("PUBLIC");
        row.setActiveMemberCount(4);
        row.setMaxMembers(6);
        row.setPayDay(25);
        row.setAmount(300000L);
        row.setMonths(12);
        row.setAssumedRate(new BigDecimal("3.50"));
        row.setAccountVerifyStatus("VERIFIED");
        row.setCreatedAt("2026-11-01 10:00");

        when(mapper.selectAdminGroupList()).thenReturn(List.of(row));

        List<AdminGroupAuditDTO.GroupSummaryItem> result = service.getGroups(1L);

        assertEquals(1, result.size());
        AdminGroupAuditDTO.GroupSummaryItem item = result.get(0);
        assertEquals(101L, item.getGroupId());
        assertEquals("김○늘", item.getOwnerName());
        assertEquals(4, item.getActiveMemberCount());
        assertEquals(6, item.getMaxMembers());
        assertEquals("VERIFIED", item.getAccountVerifyStatus());
    }

    @Test
    @DisplayName("계모임 상세 패널 조회: 규칙, 가입 요청·증빙·탈퇴·미납 건수, 계좌 요약, 조회 전용 안내 반환")
    void getGroupDetail_returnsRulesCountsAndMaskedAccount() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);

        AdminGroupAuditDTO.GroupDetailRow detailRow = new AdminGroupAuditDTO.GroupDetailRow();
        detailRow.setGroupId(101L);
        detailRow.setTitle("마포 목돈 모임");
        detailRow.setPurpose("SAVINGS");
        detailRow.setRegionSido("서울");
        detailRow.setRegionSigungu("마포구");
        detailRow.setOwnerId(10L);
        detailRow.setOwnerName("김하늘");
        detailRow.setStatus("ACTIVE");
        detailRow.setVisibility("PRIVATE");
        detailRow.setActiveMemberCount(5);
        detailRow.setMaxMembers(8);
        detailRow.setAmount(200000L);
        detailRow.setMonths(10);
        detailRow.setPayDay(15);
        detailRow.setAssumedRate(new BigDecimal("3.00"));
        detailRow.setOverduePolicy("ALERT_RESTRICT");
        detailRow.setOrderConfirmedYn("Y");
        detailRow.setRulesTitle("매월 15일 납입 원칙");
        detailRow.setRulesBody("납입일 5일 경과 시 미납 감지 대상이 됩니다.");
        detailRow.setDescription("안정적인 목돈 마련 모임");
        detailRow.setJoinRequestCount(3);
        detailRow.setPendingJoinRequestCount(1);
        detailRow.setPaymentProofCount(12);
        detailRow.setPendingProofCount(2);
        detailRow.setWithdrawalRequestCount(1);
        detailRow.setActiveWithdrawalCount(1);
        detailRow.setOverdueCaseCount(2);
        detailRow.setOpenOverdueCaseCount(1);

        AdminGroupAuditDTO.GroupAccountRow currentAcc = new AdminGroupAuditDTO.GroupAccountRow();
        currentAcc.setGyeAccountId(501L);
        currentAcc.setBankName("국민은행");
        currentAcc.setHolderNameEnc("ENC_HOLDER_NAME");
        currentAcc.setKeyVer(1);
        currentAcc.setRegisteredByName("김하늘");
        currentAcc.setAccountNoLast4("6789");
        currentAcc.setVerifyStatus("VERIFIED");
        currentAcc.setAccountStatus("CURRENT");

        when(mapper.selectAdminGroupDetail(101L)).thenReturn(detailRow);
        when(mapper.selectGroupAccountByStatus(101L, "CURRENT")).thenReturn(currentAcc);
        when(mapper.selectGroupAccountByStatus(101L, "PENDING")).thenReturn(null);
        when(cryptoKeyService.decrypt("ENC_HOLDER_NAME", 1)).thenReturn("박예금");

        AdminGroupAuditDTO.GroupDetailResponse res = service.getGroupDetail(1L, 101L);

        assertEquals("김○늘", res.getOwnerName());
        assertTrue(res.isOrderConfirmed());
        assertEquals("매월 15일 납입 원칙", res.getRulesTitle());
        assertEquals(3, res.getJoinRequestCount());
        assertEquals(12, res.getPaymentProofCount());
        assertEquals(1, res.getWithdrawalRequestCount());
        assertEquals(2, res.getOverdueCaseCount());
        assertNotNull(res.getCurrentAccount());
        assertEquals("****6789", res.getCurrentAccount().getMaskedAccountNo());
        assertEquals("박○금", res.getCurrentAccount().getMaskedHolderName());
        assertNull(res.getPendingAccount());
        assertEquals(AdminGroupAuditService.READ_ONLY_NOTICE, res.getReadOnlyNotice());
        verify(cryptoKeyService).decrypt("ENC_HOLDER_NAME", 1);
    }

    @Test
    @DisplayName("계모임 상세 조회: 예금주 복호화 실패 또는 미존재 시 등록자명으로 안전 대체")
    void getGroupDetail_fallsBackToRegisteredByNameWhenDecryptFails() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);

        AdminGroupAuditDTO.GroupDetailRow detailRow = new AdminGroupAuditDTO.GroupDetailRow();
        detailRow.setGroupId(102L);
        detailRow.setTitle("서초 모임");
        detailRow.setOwnerId(10L);
        detailRow.setOwnerName("김하늘");
        detailRow.setStatus("ACTIVE");

        AdminGroupAuditDTO.GroupAccountRow currentAcc = new AdminGroupAuditDTO.GroupAccountRow();
        currentAcc.setGyeAccountId(502L);
        currentAcc.setBankName("신한은행");
        currentAcc.setHolderNameEnc("INVALID_CIPHER");
        currentAcc.setKeyVer(2);
        currentAcc.setRegisteredByName("최등록");
        currentAcc.setAccountNoLast4("1234");
        currentAcc.setVerifyStatus("VERIFIED");
        currentAcc.setAccountStatus("CURRENT");

        when(mapper.selectAdminGroupDetail(102L)).thenReturn(detailRow);
        when(mapper.selectGroupAccountByStatus(102L, "CURRENT")).thenReturn(currentAcc);
        when(mapper.selectGroupAccountByStatus(102L, "PENDING")).thenReturn(null);
        when(cryptoKeyService.decrypt("INVALID_CIPHER", 2))
                .thenThrow(new IllegalStateException("decrypt failed"));

        AdminGroupAuditDTO.GroupDetailResponse res = service.getGroupDetail(1L, 102L);

        assertNotNull(res.getCurrentAccount());
        assertEquals("최○록", res.getCurrentAccount().getMaskedHolderName());
    }

    @Test
    @DisplayName("계모임 상세 조회: 없는 모임은 404 NOT_FOUND")
    void getGroupDetail_notFound_404() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        when(mapper.selectAdminGroupDetail(999L)).thenReturn(null);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.getGroupDetail(1L, 999L)
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("감사 이력 조회: 필터 3종 + v3.1 담당 관리자 + 페이지당 8건 페이징 정확")
    void getAuditLogs_filterCombinationAnd8PerPage() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);

        AdminGroupAuditDTO.AuditLogRow row1 = new AdminGroupAuditDTO.AuditLogRow();
        row1.setAuditLogId(200L);
        row1.setGroupId(101L);
        row1.setGroupTitle("마포 목돈 모임");
        row1.setEventType("DISSOLVE_REQUEST");
        row1.setActorMemberId(10L);
        row1.setMemberName("홍길동");
        row1.setTargetMemberId(11L);
        row1.setTargetMemberName("이영희");
        row1.setDescription("해산 신청 접수");
        row1.setCreatedAt("2026-11-20 14:30");

        AdminGroupAuditDTO.AuditLogRow row2 = new AdminGroupAuditDTO.AuditLogRow();
        row2.setAuditLogId(199L);
        row2.setGroupId(101L);
        row2.setGroupTitle("마포 목돈 모임");
        row2.setEventType("OVERDUE_DETECT");
        row2.setDescription("5일차 미납 자동 감지");
        row2.setCreatedAt("2026-11-20 02:00");

        AdminGroupAuditDTO.AuditLogRow row3 = new AdminGroupAuditDTO.AuditLogRow();
        row3.setAuditLogId(198L);
        row3.setGroupId(101L);
        row3.setGroupTitle("마포 목돈 모임");
        row3.setEventType("DISSOLVE_APPROVE");
        row3.setActorAdminId(2L);
        row3.setAdminName("박운영");
        row3.setDescription("해산 최종 승인");
        row3.setCreatedAt("2026-11-19 18:00");

        when(mapper.countAuditLogs(any())).thenReturn(17L);
        when(mapper.selectAuditLogs(any())).thenReturn(List.of(row1, row2, row3));

        AdminGroupAuditDTO.AuditLogPageResponse res = service.getAuditLogs(
                1L, 101L, "dissolve_request", "검토", false, 2L, 1, 8
        );

        ArgumentCaptor<AdminGroupAuditDTO.AuditLogSearchCriteria> captor =
                ArgumentCaptor.forClass(AdminGroupAuditDTO.AuditLogSearchCriteria.class);
        verify(mapper).countAuditLogs(captor.capture());

        AdminGroupAuditDTO.AuditLogSearchCriteria criteria = captor.getValue();
        assertEquals(101L, criteria.getGroupId());
        assertEquals("DISSOLVE_REQUEST", criteria.getEventType());
        assertEquals("REVIEW", criteria.getReviewStatus());
        assertTrue(criteria.isAdminOnly()); // actorAdminId가 지정되면 자동으로 관리자 조치만 true
        assertEquals(2L, criteria.getActorAdminId());
        assertEquals(8, criteria.getOffset()); // page 1 * size 8
        assertEquals(8, criteria.getSize());

        assertEquals(17L, res.getTotalCount());
        assertEquals(3, res.getTotalPages()); // ceil(17 / 8) = 3
        assertTrue(res.isHasPrev());
        assertTrue(res.isHasNext());

        assertEquals("REVIEW", res.getItems().get(0).getReviewCategory());
        assertEquals("MEMBER", res.getItems().get(0).getActorType());
        assertEquals("홍○동", res.getItems().get(0).getActorLabel());
        assertEquals("이○희", res.getItems().get(0).getTargetMemberLabel());

        assertEquals("REVIEW", res.getItems().get(1).getReviewCategory());
        assertEquals("SYSTEM", res.getItems().get(1).getActorType());
        assertEquals("시스템", res.getItems().get(1).getActorLabel());

        assertEquals("DONE", res.getItems().get(2).getReviewCategory());
        assertEquals("ADMIN", res.getItems().get(2).getActorType());
        assertEquals("관리자 박운영", res.getItems().get(2).getActorLabel());
    }

    @Test
    @DisplayName("감사 이력 조회: 결과 0건이면 목록 쿼리를 생략하고 빈 목록 반환")
    void getAuditLogs_emptyResultSkipsSelect() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        when(mapper.countAuditLogs(any())).thenReturn(0L);

        AdminGroupAuditDTO.AuditLogPageResponse res = service.getAuditLogs(
                1L, null, null, "ALL", true, null, 0, 8
        );

        assertEquals(0L, res.getTotalCount());
        assertEquals(0, res.getTotalPages());
        assertFalse(res.isHasPrev());
        assertFalse(res.isHasNext());
        assertTrue(res.getItems().isEmpty());
        verify(mapper, never()).selectAuditLogs(any());
    }

    @Test
    @DisplayName("감사 이력 조회: 잘못된 reviewStatus 또는 size면 400 BAD_REQUEST")
    void getAuditLogs_invalidParams_400() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);

        ResponseStatusException badStatus = assertThrows(
                ResponseStatusException.class,
                () -> service.getAuditLogs(1L, null, null, "INVALID", false, null, 0, 8)
        );
        assertEquals(HttpStatus.BAD_REQUEST, badStatus.getStatusCode());

        ResponseStatusException badSize = assertThrows(
                ResponseStatusException.class,
                () -> service.getAuditLogs(1L, null, null, "ALL", false, null, 0, 0)
        );
        assertEquals(HttpStatus.BAD_REQUEST, badSize.getStatusCode());
    }

    @Test
    @DisplayName("검토 접미사 판정: _REQUEST 와 _DETECT 만 REVIEW 로 분류")
    void isReviewEventType_exactSuffix() {
        assertTrue(AdminGroupAuditService.isReviewEventType("JOIN_REQUEST"));
        assertTrue(AdminGroupAuditService.isReviewEventType("WITHDRAW_REQUEST"));
        assertTrue(AdminGroupAuditService.isReviewEventType("DISSOLVE_REQUEST"));
        assertTrue(AdminGroupAuditService.isReviewEventType("OVERDUE_DETECT"));

        assertFalse(AdminGroupAuditService.isReviewEventType("JOIN_APPROVE"));
        assertFalse(AdminGroupAuditService.isReviewEventType("ORDER_CONSENT_REQ"));
        assertFalse(AdminGroupAuditService.isReviewEventType("DISSOLVE_APPROVE"));
        assertFalse(AdminGroupAuditService.isReviewEventType("XREQUEST"));
    }
}
