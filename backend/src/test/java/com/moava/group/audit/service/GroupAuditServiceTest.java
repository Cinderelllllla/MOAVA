package com.moava.group.audit.service;

import com.moava.group.audit.dto.GroupAuditAccessRow;
import com.moava.group.audit.dto.GroupAuditLogPageResponse;
import com.moava.group.audit.dto.GroupAuditLogRow;
import com.moava.group.audit.mapper.GroupAuditMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F33 회원용 모임 운영 기록(감사 로그) 조회 서비스 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class GroupAuditServiceTest {

    @Mock
    GroupAuditMapper mapper;

    @InjectMocks
    GroupAuditService service;

    private static final long GROUP_ID = 10L;
    private static final long OWNER_ID = 1L;
    private static final long MEMBER_ID = 20L;

    @Test
    @DisplayName("조회 성공: ACTIVE 회원이 페이징 목록 조회 + 이름 마스킹 + 관리자/시스템 표기 + 신고자 익명화")
    void getGroupAuditLogs_successAndPrivacy() {
        when(mapper.selectAccess(GROUP_ID, MEMBER_ID))
                .thenReturn(new GroupAuditAccessRow(GROUP_ID, "우리 계모임", OWNER_ID, "ACTIVE"));
        when(mapper.selectDistinctGroupEventTypes(GROUP_ID))
                .thenReturn(List.of("GROUP_CREATE", "PAYOUT_RECEIVED", "REPORT_RECEIVED"));
        when(mapper.countGroupAuditLogs(GROUP_ID, null)).thenReturn(3L);
        when(mapper.selectGroupAuditLogs(GROUP_ID, null, 0, 8)).thenReturn(List.of(
                new GroupAuditLogRow(
                        103L, GROUP_ID, "PAYOUT_RECEIVED",
                        OWNER_ID, "김모임", null,
                        MEMBER_ID, "홍길동",
                        "곗돈 수령 완료 표시: 홍○동 (2회차)", "2026-11-27 17:00"
                ),
                new GroupAuditLogRow(
                        102L, GROUP_ID, "OVERDUE_WARN",
                        null, null, 5L,
                        MEMBER_ID, "홍길동",
                        "미납 경고 발송", "2026-11-27 16:00"
                ),
                new GroupAuditLogRow(
                        101L, GROUP_ID, "REPORT_RECEIVED",
                        MEMBER_ID, "홍길동", null,
                        null, null,
                        "신고 접수 · REPORT #77 · 유형: 계좌 변경 의심 · 대상 모임: 우리 계모임", "2026-11-27 15:00"
                )
        ));

        GroupAuditLogPageResponse res = service.getGroupAuditLogs(MEMBER_ID, GROUP_ID, "ALL", 0, 8);

        assertEquals(GROUP_ID, res.groupId());
        assertEquals("우리 계모임", res.groupTitle());
        assertEquals(3L, res.totalCount());
        assertEquals(1, res.totalPages());
        assertFalse(res.hasPrev());
        assertFalse(res.hasNext());
        assertNull(res.selectedEventType());
        assertEquals(3, res.items().size());

        // 1) 회원 행위 마스킹
        assertEquals("MEMBER", res.items().get(0).actorType());
        assertEquals("김○임", res.items().get(0).actorLabel());
        assertEquals("홍○동", res.items().get(0).targetMemberLabel());

        // 2) 관리자 행위 실명 미노출
        assertEquals("ADMIN", res.items().get(1).actorType());
        assertEquals("관리자", res.items().get(1).actorLabel());

        // 3) 신고 접수 이벤트 신고자 익명화 + 내부 신고 번호 미노출
        assertEquals("REPORT_RECEIVED", res.items().get(2).eventType());
        assertEquals("회원", res.items().get(2).actorLabel());
        assertEquals("모임 관련 신고가 접수됐어요.", res.items().get(2).description());
    }

    @Test
    @DisplayName("비공개 이벤트 필터 차단: REPORT_STATUS / CHAT_VIEW 필터 요청 시 DB 조회 없이 빈 결과 반환")
    void getGroupAuditLogs_hiddenEventReturnsEmpty() {
        when(mapper.selectAccess(GROUP_ID, OWNER_ID))
                .thenReturn(new GroupAuditAccessRow(GROUP_ID, "우리 계모임", OWNER_ID, "ACTIVE"));
        when(mapper.selectDistinctGroupEventTypes(GROUP_ID))
                .thenReturn(List.of("GROUP_CREATE"));

        GroupAuditLogPageResponse res = service.getGroupAuditLogs(OWNER_ID, GROUP_ID, "CHAT_VIEW", 0, 8);

        assertTrue(res.items().isEmpty());
        assertEquals(0L, res.totalCount());
        verify(mapper, never()).countGroupAuditLogs(anyLong(), any());
        verify(mapper, never()).selectGroupAuditLogs(anyLong(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("조회 차단: 모임이 존재하지 않으면 404 GROUP_NOT_FOUND")
    void getGroupAuditLogs_groupNotFound() {
        when(mapper.selectAccess(GROUP_ID, MEMBER_ID)).thenReturn(null);

        GroupAuditException e = assertThrows(
                GroupAuditException.class,
                () -> service.getGroupAuditLogs(MEMBER_ID, GROUP_ID, null, 0, 8)
        );

        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("GROUP_NOT_FOUND", e.getCode());
    }

    @Test
    @DisplayName("조회 차단: 비참여 회원은 403 NOT_GROUP_MEMBER")
    void getGroupAuditLogs_nonMemberForbidden() {
        when(mapper.selectAccess(GROUP_ID, 999L))
                .thenReturn(new GroupAuditAccessRow(GROUP_ID, "우리 계모임", OWNER_ID, null));

        GroupAuditException e = assertThrows(
                GroupAuditException.class,
                () -> service.getGroupAuditLogs(999L, GROUP_ID, null, 0, 8)
        );

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals("NOT_GROUP_MEMBER", e.getCode());
    }

    @Test
    @DisplayName("조회 차단: 페이지 크기 초과 시 400 INVALID_SIZE")
    void getGroupAuditLogs_invalidSize() {
        GroupAuditException e = assertThrows(
                GroupAuditException.class,
                () -> service.getGroupAuditLogs(MEMBER_ID, GROUP_ID, null, 0, 100)
        );

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("INVALID_SIZE", e.getCode());
    }
}
