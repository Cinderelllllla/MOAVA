package com.moava.admin.sanctions.service;

import com.moava.admin.sanctions.dto.AdminSanctionDTO;
import com.moava.admin.sanctions.mapper.AdminSanctionMapper;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F39 AdminSanctionService 단위 테스트.
 * - 플랫폼 가입 제한 설정(BAN): 중복 제재 차단, 사유 필수, 이력 append, 알림·감사 로그 검증
 * - 플랫폼 가입 제한 해제(BAN_RELEASE): 이전 기록 보존, BANNED_YN='N', 알림·감사 로그 검증
 * - 모임별 회원 경고 현황(n/2회, 2회=강제 탈퇴 검토 대상) 및 마스킹 검증
 * - 경고·강제 탈퇴 이력 검색·유형 필터·8건 페이징 및 v3.1 조치자 구분 검증
 */
@ExtendWith(MockitoExtension.class)
class AdminSanctionServiceTest {

    @Mock
    AdminSanctionMapper mapper;

    @Mock
    NotificationService notificationService;

    @InjectMocks
    AdminSanctionService service;

    @Test
    @DisplayName("가입 제한 설정 성공: BANNED_YN=Y, SANCTION(BAN) append, AUDIT_LOG(BAN), 대상 회원 알림")
    void banMember_success() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        AdminSanctionDTO.MemberBanTargetRow target = memberTarget(10L, "김하늘", "ACTIVE", "N");
        when(mapper.selectMemberForBanUpdate(10L)).thenReturn(target);
        when(mapper.updateMemberBannedYn(10L, "N", "Y")).thenReturn(1);
        when(mapper.nextSanctionId()).thenReturn(501L);

        AdminSanctionDTO.BanActionResponse res = service.banMember(
                1L, new AdminSanctionDTO.BanRequest(10L, "반복 미납 및 허위 증빙 제출")
        );

        assertEquals(501L, res.getSanctionId());
        assertEquals(10L, res.getMemberId());
        assertEquals("김○늘", res.getMemberName());
        assertEquals("BAN", res.getSanctionType());
        assertTrue(res.isBanned());
        assertEquals(1L, res.getIssuedAdminId());

        verify(mapper).updateMemberBannedYn(10L, "N", "Y");
        verify(mapper).insertAdminSanction(501L, "BAN", 10L, 1L, "반복 미납 및 허위 증빙 제출");
        verify(mapper).insertAdminAuditLog(1L, 10L, "BAN", "플랫폼 가입 제한 SANCTION #501");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).notifyMember(eq(10L), eq(NotifType.SANCTION), eq(501L), paramsCaptor.capture());
        assertEquals("MOAVA", paramsCaptor.getValue().get("scope"));
        assertTrue(String.valueOf(paramsCaptor.getValue().get("action")).contains("반복 미납 및 허위 증빙 제출"));
    }

    @Test
    @DisplayName("중복 가입 제한 차단: 이미 BANNED_YN=Y 회원은 409 CONFLICT 및 명세 문구 반환")
    void banMember_duplicate_throws409() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        when(mapper.selectMemberForBanUpdate(10L)).thenReturn(memberTarget(10L, "김하늘", "ACTIVE", "Y"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.banMember(1L, new AdminSanctionDTO.BanRequest(10L, "중복 제재 시도"))
        );

        assertEquals(HttpStatus.CONFLICT, HttpStatus.valueOf(ex.getStatusCode().value()));
        assertEquals("이미 가입 제한 중인 회원입니다.", ex.getReason());
        verify(mapper, never()).insertAdminSanction(anyLong(), any(), anyLong(), anyLong(), any());
        verify(notificationService, never()).notifyMember(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("사유 누락 시 가입 제한 차단: 400 BAD_REQUEST")
    void banMember_blankReason_throws400() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.banMember(1L, new AdminSanctionDTO.BanRequest(10L, "   "))
        );

        assertEquals(HttpStatus.BAD_REQUEST, HttpStatus.valueOf(ex.getStatusCode().value()));
        verify(mapper, never()).selectMemberForBanUpdate(anyLong());
    }

    @Test
    @DisplayName("탈퇴 회원은 가입 제한 설정 차단: 409 CONFLICT")
    void banMember_withdrawn_throws409() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        when(mapper.selectMemberForBanUpdate(10L)).thenReturn(memberTarget(10L, "탈퇴회원", "WITHDRAWN", "N"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.banMember(1L, new AdminSanctionDTO.BanRequest(10L, "사유"))
        );

        assertEquals(HttpStatus.CONFLICT, HttpStatus.valueOf(ex.getStatusCode().value()));
        verify(mapper, never()).updateMemberBannedYn(anyLong(), any(), any());
    }

    @Test
    @DisplayName("가입 제한 해제 성공: BANNED_YN=N, SANCTION(BAN_RELEASE) append(이전 기록 보존), 알림·감사 로그")
    void releaseBan_success() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        when(mapper.selectMemberForBanUpdate(10L)).thenReturn(memberTarget(10L, "김하늘", "ACTIVE", "Y"));
        when(mapper.updateMemberBannedYn(10L, "Y", "N")).thenReturn(1);
        when(mapper.nextSanctionId()).thenReturn(502L);

        AdminSanctionDTO.BanActionResponse res = service.releaseBan(1L, 10L, null);

        assertEquals(502L, res.getSanctionId());
        assertEquals("BAN_RELEASE", res.getSanctionType());
        assertFalse(res.isBanned());
        assertEquals(AdminSanctionService.DEFAULT_RELEASE_REASON, res.getReason());

        verify(mapper).updateMemberBannedYn(10L, "Y", "N");
        verify(mapper).insertAdminSanction(502L, "BAN_RELEASE", 10L, 1L, AdminSanctionService.DEFAULT_RELEASE_REASON);
        verify(mapper).insertAdminAuditLog(1L, 10L, "BAN_RELEASE", "플랫폼 가입 제한 해제 SANCTION #502");
        verify(notificationService).notifyMember(eq(10L), eq(NotifType.SANCTION), eq(502L), any());
    }

    @Test
    @DisplayName("가입 제한 중이 아닌 회원 해제 시 409 CONFLICT")
    void releaseBan_notBanned_throws409() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        when(mapper.selectMemberForBanUpdate(10L)).thenReturn(memberTarget(10L, "김하늘", "ACTIVE", "N"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.releaseBan(1L, 10L, new AdminSanctionDTO.BanReleaseRequest("해제"))
        );

        assertEquals(HttpStatus.CONFLICT, HttpStatus.valueOf(ex.getStatusCode().value()));
        verify(mapper, never()).insertAdminSanction(anyLong(), any(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("모임별 회원 경고 현황: 2회면 강제 탈퇴 검토 대상 true, 이름 마스킹 적용")
    void getMemberWarnings_mapsReviewTargetAndMasksName() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);

        AdminSanctionDTO.MemberWarningRow w1 = new AdminSanctionDTO.MemberWarningRow();
        w1.setGyeMemberId(100L);
        w1.setGroupId(10L);
        w1.setGroupTitle("마포 목돈 모임");
        w1.setGroupStatus("ACTIVE");
        w1.setMemberId(20L);
        w1.setMemberName("박서준");
        w1.setWarningCount(2);

        AdminSanctionDTO.MemberWarningRow w2 = new AdminSanctionDTO.MemberWarningRow();
        w2.setGyeMemberId(101L);
        w2.setGroupId(11L);
        w2.setGroupTitle("강남 여행 모임");
        w2.setGroupStatus("ACTIVE");
        w2.setMemberId(21L);
        w2.setMemberName("이민");
        w2.setWarningCount(1);

        when(mapper.selectMemberWarnings()).thenReturn(List.of(w1, w2));

        List<AdminSanctionDTO.MemberWarningItem> result = service.getMemberWarnings(1L);

        assertEquals(2, result.size());
        assertEquals("박○준", result.get(0).getMemberName());
        assertEquals(2, result.get(0).getWarningCount());
        assertEquals(2, result.get(0).getMaxWarnings());
        assertTrue(result.get(0).isExpelReviewTarget());
        assertEquals("강제 탈퇴 검토 대상", result.get(0).getStatusText());

        assertEquals("이○", result.get(1).getMemberName());
        assertFalse(result.get(1).isExpelReviewTarget());
        assertEquals("경고 관리 중", result.get(1).getStatusText());
    }

    @Test
    @DisplayName("경고·강제 탈퇴 이력: 검색어 이스케이프, 유형 필터 정규화, 8건 페이징 및 v3.1 조치자(ISSUED_MEMBER_ID) 구분")
    void getWarningExpelHistory_filtersAndPaginates() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        when(mapper.countWarningExpelHistory(any())).thenReturn(10L);

        AdminSanctionDTO.SanctionHistoryRow row = new AdminSanctionDTO.SanctionHistoryRow();
        row.setSanctionId(701L);
        row.setSanctionType("WARN");
        row.setGroupId(10L);
        row.setGroupTitle("마포_목돈 모임");
        row.setMemberId(20L);
        row.setMemberName("박서준");
        row.setIssuedMemberId(5L);
        row.setIssuedMemberName("김하늘");
        row.setIssuedAdminId(null);
        row.setReason("규칙 미준수 1회 경고");
        row.setCreatedAt("2026-11-25 11:30");

        when(mapper.selectWarningExpelHistory(any())).thenReturn(List.of(row));

        AdminSanctionDTO.SanctionHistoryPageResponse res =
                service.getWarningExpelHistory(1L, "마포_목돈", "경고", 0, 8);

        ArgumentCaptor<AdminSanctionDTO.SanctionHistorySearchCriteria> captor =
                ArgumentCaptor.forClass(AdminSanctionDTO.SanctionHistorySearchCriteria.class);
        verify(mapper).selectWarningExpelHistory(captor.capture());

        assertEquals("마포\\_목돈", captor.getValue().getEscapedKeyword());
        assertEquals("WARN", captor.getValue().getSanctionType());
        assertEquals(0, captor.getValue().getOffset());
        assertEquals(8, captor.getValue().getSize());

        assertEquals(10L, res.getTotalCount());
        assertEquals(2, res.getTotalPages());
        assertFalse(res.isHasPrev());
        assertTrue(res.isHasNext());
        assertEquals(1, res.getItems().size());
        assertEquals("경고", res.getItems().get(0).getSanctionTypeLabel());
        assertEquals("MEMBER", res.getItems().get(0).getIssuerType());
        assertEquals("김○늘", res.getItems().get(0).getIssuerLabel());
        assertNull(res.getItems().get(0).getIssuedAdminId());
    }

    @Test
    @DisplayName("비활성 관리자 계정은 403 FORBIDDEN")
    void inactiveAdmin_throws403() {
        when(mapper.countActiveAdmin(99L)).thenReturn(0);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.getOverview(99L)
        );

        assertEquals(HttpStatus.FORBIDDEN, HttpStatus.valueOf(ex.getStatusCode().value()));
    }

    private static AdminSanctionDTO.MemberBanTargetRow memberTarget(
            Long id, String name, String status, String bannedYn
    ) {
        AdminSanctionDTO.MemberBanTargetRow row = new AdminSanctionDTO.MemberBanTargetRow();
        row.setMemberId(id);
        row.setName(name);
        row.setEmail("member" + id + "@moava.test");
        row.setStatus(status);
        row.setBannedYn(bannedYn);
        return row;
    }
}
