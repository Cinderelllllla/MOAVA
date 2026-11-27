package com.moava.chat;

import com.moava.chat.controller.AdminChatViewController;
import com.moava.chat.controller.ChatExceptionHandler;
import com.moava.chat.dto.ChatMessageRow;
import com.moava.chat.dto.ChatReportRef;
import com.moava.chat.mapper.ChatMapper;
import com.moava.chat.service.ChatException;
import com.moava.chat.service.ChatService;
import com.moava.crypto.service.CryptoKeyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MOA-96 관리자 신고 건 대상 모임 채팅 열람 (읽기 전용) — DB·암호화 키 불필요 */
@ExtendWith(MockitoExtension.class)
class AdminChatViewTest {

    @Mock ChatMapper mapper;
    @Mock CryptoKeyService crypto;
    @InjectMocks ChatService service;

    private static final long ADMIN = 3L, REPORT = 77L, GROUP = 101L;

    private void activeAdmin() {
        when(mapper.countActiveAdmin(ADMIN)).thenReturn(1);
    }

    private void report(Long groupId, String status) {
        when(mapper.selectReportForChat(REPORT)).thenReturn(new ChatReportRef(groupId, status));
    }

    private ChatException fails(Integer size) {
        return assertThrows(ChatException.class, () -> service.adminHistory(ADMIN, REPORT, null, size));
    }

    private void verifyNothingReadOrLogged() {
        verify(mapper, never()).selectMessages(anyLong(), any(), anyInt());
        verify(mapper, never()).insertChatViewAudit(anyLong(), anyLong(), anyString());
    }

    // ---- 차단 ----

    @Test
    void 활성_관리자가_아니면_403_ADMIN_ONLY() {
        when(mapper.countActiveAdmin(ADMIN)).thenReturn(0);
        ChatException e = fails(null);
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals("ADMIN_ONLY", e.getCode());
        verify(mapper, never()).selectReportForChat(anyLong());
        verifyNothingReadOrLogged();
    }

    @Test
    void 신고가_없으면_404() {
        activeAdmin();
        when(mapper.selectReportForChat(REPORT)).thenReturn(null);
        ChatException e = fails(null);
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        assertEquals("REPORT_NOT_FOUND", e.getCode());
        verifyNothingReadOrLogged();
    }

    @Test
    void 대상_모임이_없는_신고는_400() {
        activeAdmin();
        report(null, "RECEIVED");
        ChatException e = fails(null);
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("NO_TARGET_GROUP", e.getCode());
        verifyNothingReadOrLogged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "REJECTED"})
    void 처리가_끝난_신고는_403(String status) {
        activeAdmin();
        report(GROUP, status);
        ChatException e = fails(null);
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals("REPORT_NOT_OPEN", e.getCode());
        verifyNothingReadOrLogged();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101})
    void 한_번에_불러올_개수가_1에서_100이_아니면_400(int size) {
        activeAdmin();
        report(GROUP, "RECEIVED");
        ChatException e = fails(size);
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("INVALID_SIZE", e.getCode());
        verifyNothingReadOrLogged();
    }

    // ---- 성공 ----

    @ParameterizedTest
    @ValueSource(strings = {"RECEIVED", "REVIEWING", "MORE_INFO"})
    void 처리_중인_신고는_읽기_전용으로_이름을_가려서_오래된_순으로_준다(String status) {
        activeAdmin();
        report(GROUP, status);
        LocalDateTime t = LocalDateTime.of(2026, 11, 27, 10, 0);
        // DB 는 최신부터 limit+1(=3) 개
        when(mapper.selectMessages(GROUP, null, 3)).thenReturn(List.of(
                new ChatMessageRow(30, GROUP, null, null, "ES", 1, t, true),
                new ChatMessageRow(20, GROUP, 7L, "이모아", "E20", 1, t),
                new ChatMessageRow(10, GROUP, 8L, "김바", "E10", 1, t)));
        when(crypto.decrypt(anyString(), eq(1))).thenAnswer(inv -> "본문" + inv.getArgument(0, String.class).substring(1));

        var res = service.adminHistory(ADMIN, REPORT, null, 2);

        assertFalse(res.canSend());
        assertTrue(res.hasMore());
        assertEquals(2, res.items().size());
        assertEquals(20, res.items().get(0).messageId());   // 오래된 것 → 최신
        assertEquals(30, res.items().get(1).messageId());
        assertEquals("이○아", res.items().get(0).senderName());
        assertEquals("본문20", res.items().get(0).body());
        assertTrue(res.items().get(1).system());
        assertNull(res.items().get(1).senderName());
        // 회원 검사는 하지 않는다 (관리자는 모임 회원이 아님)
        verify(mapper, never()).selectAccess(anyLong(), anyLong());
    }

    @Test
    void 두_글자_이름도_가린다() {
        activeAdmin();
        report(GROUP, "REVIEWING");
        when(mapper.selectMessages(GROUP, null, 31)).thenReturn(List.of(
                new ChatMessageRow(1, GROUP, 8L, "김바", "E1", 1, LocalDateTime.now())));
        when(crypto.decrypt("E1", 1)).thenReturn("안녕");

        var items = service.adminHistory(ADMIN, REPORT, null, null).items();

        assertEquals("김○", items.get(0).senderName());
    }

    // ---- 감사 기록 ----

    @Test
    void 열람할_때마다_CHAT_VIEW_를_남기고_본문과_이름은_넣지_않는다() {
        activeAdmin();
        report(GROUP, "RECEIVED");
        when(mapper.selectMessages(GROUP, null, 31)).thenReturn(List.of(
                new ChatMessageRow(1, GROUP, 8L, "김바다", "E1", 1, LocalDateTime.now())));
        when(crypto.decrypt("E1", 1)).thenReturn("비밀 이야기");

        service.adminHistory(ADMIN, REPORT, null, null);

        ArgumentCaptor<String> desc = ArgumentCaptor.forClass(String.class);
        verify(mapper, times(1)).insertChatViewAudit(eq(GROUP), eq(ADMIN), desc.capture());
        assertEquals("REPORT #77 · 채팅 열람", desc.getValue());
        assertFalse(desc.getValue().contains("비밀 이야기"));
        assertFalse(desc.getValue().contains("김바다"));
    }

    @Test
    void 이전_메시지를_불러오면_이전_메시지_열람으로_기록한다() {
        activeAdmin();
        report(GROUP, "MORE_INFO");
        when(mapper.selectMessages(GROUP, 50L, 31)).thenReturn(List.of());

        var res = service.adminHistory(ADMIN, REPORT, 50L, null);

        assertTrue(res.items().isEmpty());
        verify(mapper).insertChatViewAudit(GROUP, ADMIN, "REPORT #77 · 채팅 이전 메시지 열람");
    }

    @Test
    void 감사_기록이_실패하면_채팅도_보여_주지_않는다() {
        activeAdmin();
        report(GROUP, "RECEIVED");
        doThrow(new RuntimeException("db down")).when(mapper).insertChatViewAudit(anyLong(), anyLong(), anyString());

        assertThrows(RuntimeException.class, () -> service.adminHistory(ADMIN, REPORT, null, null));
        verify(mapper, never()).selectMessages(anyLong(), any(), anyInt());
    }

    // ---- 컨트롤러 ----

    @Test
    void 회원_토큰이면_403_ADMIN_ONLY() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminChatViewController(service))
                .setControllerAdvice(new ChatExceptionHandler())
                .build();
        var member = new UsernamePasswordAuthenticationToken("7", null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

        mvc.perform(get("/api/v1/admin/support/reports/77/chat/messages").principal(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ADMIN_ONLY"));
        verifyNoInteractions(mapper);
    }

    @Test
    void 관리자_토큰이면_200_이고_canSend_false() throws Exception {
        activeAdmin();
        report(GROUP, "RECEIVED");
        when(mapper.selectMessages(GROUP, null, 31)).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminChatViewController(service))
                .setControllerAdvice(new ChatExceptionHandler())
                .build();
        var admin = new UsernamePasswordAuthenticationToken(String.valueOf(ADMIN), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mvc.perform(get("/api/v1/admin/support/reports/77/chat/messages").principal(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canSend").value(false))
                .andExpect(jsonPath("$.data.hasMore").value(false));
    }
}
