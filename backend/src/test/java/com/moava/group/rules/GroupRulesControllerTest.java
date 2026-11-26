package com.moava.group.rules;

import com.moava.chat.dto.ChatMessageResponse;
import com.moava.chat.service.ChatBroadcaster;
import com.moava.chat.service.ChatService;
import com.moava.group.rules.controller.GroupRulesController;
import com.moava.group.rules.controller.GroupRulesExceptionHandler;
import com.moava.group.rules.dto.GroupRulesRow;
import com.moava.group.rules.mapper.GroupRulesMapper;
import com.moava.group.rules.service.GroupRulesService;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F31 운영 규칙 API: 권한·입력 검사·수정 시 알림/감사 로그/채팅 (DB·웹소켓 불필요) */
class GroupRulesControllerTest {

    static final long LEADER = 7L, MEMBER = 8L, OUTSIDER = 9L, GROUP = 101L;

    GroupRulesMapper mapper;
    NotificationService notifications;
    ChatService chat;
    ChatBroadcaster broadcaster;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mapper = mock(GroupRulesMapper.class);
        notifications = mock(NotificationService.class);
        chat = mock(ChatService.class);
        broadcaster = mock(ChatBroadcaster.class);
        GroupRulesService service = new GroupRulesService(mapper, notifications, chat, broadcaster);
        mvc = MockMvcBuilders.standaloneSetup(new GroupRulesController(service))
                .setControllerAdvice(new GroupRulesExceptionHandler())
                .build();
    }

    private static UsernamePasswordAuthenticationToken auth(long id, String role) {
        return new UsernamePasswordAuthenticationToken(String.valueOf(id), null, List.of(new SimpleGrantedAuthority(role)));
    }

    /** 이 모임 규칙 + 로그인 회원 상태 (모임장 7, 기존 규칙 '기본 규칙'/'월 5일 납입') */
    private void group(long me, String groupStatus, String memberStatus) {
        when(mapper.selectRules(GROUP, me)).thenReturn(new GroupRulesRow(GROUP, "동네 여행계", groupStatus, LEADER,
                "기본 규칙", "월 5일 납입", memberStatus));
    }

    private ResultActions putRules(long me, String json) throws Exception {
        return mvc.perform(put("/api/v1/groups/" + GROUP + "/rules").principal(auth(me, "ROLE_MEMBER"))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String title, String rulesBody) {
        return "{\"rulesTitle\":" + (title == null ? "null" : "\"" + title + "\"")
                + ",\"rulesBody\":" + (rulesBody == null ? "null" : "\"" + rulesBody + "\"") + "}";
    }

    private void verifyNothingSent() {
        verify(mapper, never()).updateRules(anyLong(), any(), any());
        verify(mapper, never()).insertAuditLog(anyLong(), anyLong(), any(), any());
        verifyNoInteractions(notifications, chat, broadcaster);
    }

    // ---- 조회 ----

    @Test
    void 회원은_최신_규칙을_본다_모임장이_아니면_leader_false() throws Exception {
        group(MEMBER, "ACTIVE", "ACTIVE");
        mvc.perform(get("/api/v1/groups/" + GROUP + "/rules").principal(auth(MEMBER, "ROLE_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(GROUP))
                .andExpect(jsonPath("$.data.rulesTitle").value("기본 규칙"))
                .andExpect(jsonPath("$.data.rulesBody").value("월 5일 납입"))
                .andExpect(jsonPath("$.data.leader").value(false))
                .andExpect(jsonPath("$.data.groupStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void 모임장이_조회하면_leader_true() throws Exception {
        group(LEADER, "ACTIVE", "ACTIVE");
        mvc.perform(get("/api/v1/groups/" + GROUP + "/rules").principal(auth(LEADER, "ROLE_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leader").value(true));
    }

    @Test
    void 비참여_회원_조회는_403() throws Exception {
        group(OUTSIDER, "ACTIVE", null);
        mvc.perform(get("/api/v1/groups/" + GROUP + "/rules").principal(auth(OUTSIDER, "ROLE_MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }

    @Test
    void 탈퇴한_회원_조회도_403() throws Exception {
        group(MEMBER, "ACTIVE", "WITHDRAWN");
        mvc.perform(get("/api/v1/groups/" + GROUP + "/rules").principal(auth(MEMBER, "ROLE_MEMBER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_GROUP_MEMBER"));
    }

    @Test
    void 없는_모임은_404() throws Exception {
        when(mapper.selectRules(GROUP, MEMBER)).thenReturn(null);
        mvc.perform(get("/api/v1/groups/" + GROUP + "/rules").principal(auth(MEMBER, "ROLE_MEMBER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void 관리자_토큰은_403() throws Exception {
        mvc.perform(get("/api/v1/groups/" + GROUP + "/rules").principal(auth(1, "ROLE_ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBER_ONLY"));
        verifyNoInteractions(mapper);
    }

    // ---- 수정: 성공 ----

    @Test
    void 모임장_수정_성공_알림_감사로그_채팅이_정확한_값으로_호출된다() throws Exception {
        group(LEADER, "ACTIVE", "ACTIVE");
        ChatMessageResponse notice = ChatMessageResponse.systemMessage(55L, GROUP,
                "📌 운영 규칙이 '새 규칙'(으)로 업데이트됐어요.", LocalDateTime.now());
        when(chat.postSystem(eq(GROUP), anyString())).thenReturn(notice);

        putRules(LEADER, body("  새 규칙  ", "매월 10일까지 납입"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rulesTitle").value("새 규칙"))
                .andExpect(jsonPath("$.data.rulesBody").value("매월 10일까지 납입"))
                .andExpect(jsonPath("$.data.leader").value(true))
                .andExpect(jsonPath("$.data.groupStatus").value("ACTIVE"));

        verify(mapper).updateRules(GROUP, "새 규칙", "매월 10일까지 납입");
        verify(mapper).insertAuditLog(GROUP, LEADER, "RULE_UPDATE", "운영 규칙 수정: 새 규칙");
        verify(notifications).notifyGroup(GROUP, NotifType.RULE_UPDATED, GROUP,
                Map.of("group", "동네 여행계", "title", "새 규칙", "groupId", GROUP), List.of(LEADER));
        verify(chat).postSystem(GROUP, "📌 운영 규칙이 '새 규칙'(으)로 업데이트됐어요.");
        verify(broadcaster).broadcastAfterCommit(notice);
    }

    @Test
    void 원문은_이스케이프하지_않고_그대로_저장한다() throws Exception {
        group(LEADER, "ACTIVE", "ACTIVE");
        putRules(LEADER, body("<b>규칙</b>", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rulesBody").value("<script>alert(1)</script>"));
        verify(mapper).updateRules(GROUP, "<b>규칙</b>", "<script>alert(1)</script>");
    }

    @Test
    void 제목_한글_33자_99바이트는_저장된다() throws Exception {
        group(LEADER, "ACTIVE", "ACTIVE");
        putRules(LEADER, body("가".repeat(33), "본문")).andExpect(status().isOk());
        verify(mapper).updateRules(GROUP, "가".repeat(33), "본문");
    }

    // ---- 수정: 거부 ----

    @Test
    void 일반_회원_수정은_403() throws Exception {
        group(MEMBER, "ACTIVE", "ACTIVE");
        putRules(MEMBER, body("새 규칙", "본문"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("LEADER_ONLY"))
                .andExpect(jsonPath("$.error.message").value("모임장만 규칙을 수정할 수 있어요"));
        verifyNothingSent();
    }

    @Test
    void 없는_모임_수정은_404() throws Exception {
        when(mapper.selectRules(GROUP, LEADER)).thenReturn(null);
        putRules(LEADER, body("새 규칙", "본문"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROUP_NOT_FOUND"));
        verifyNothingSent();
    }

    @Test
    void 빈_제목은_400() throws Exception {
        group(LEADER, "ACTIVE", "ACTIVE");
        putRules(LEADER, body("   ", "본문"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNothingSent();
    }

    @Test
    void 빈_본문은_400() throws Exception {
        group(LEADER, "ACTIVE", "ACTIVE");
        putRules(LEADER, body("새 규칙", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNothingSent();
    }

    @Test
    void 제목_101바이트_이상은_400_한글_34자() throws Exception {
        group(LEADER, "ACTIVE", "ACTIVE");
        putRules(LEADER, body("가".repeat(34), "본문"))   // 34 x 3 = 102바이트
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNothingSent();
    }

    @Test
    void 본문이_JSON_이_아니면_400() throws Exception {
        mvc.perform(put("/api/v1/groups/" + GROUP + "/rules").principal(auth(LEADER, "ROLE_MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void 해산_완료_모임은_409() throws Exception {
        group(LEADER, "DISSOLVED", "ACTIVE");
        putRules(LEADER, body("새 규칙", "본문"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GROUP_DISSOLVED"));
        verifyNothingSent();
    }

    @Test
    void 같은_내용이면_저장_알림_로그_채팅_없이_그대로_응답() throws Exception {
        group(LEADER, "ACTIVE", "ACTIVE");
        putRules(LEADER, body("기본 규칙 ", " 월 5일 납입"))   // 앞뒤 공백만 다름
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rulesTitle").value("기본 규칙"))
                .andExpect(jsonPath("$.data.leader").value(true));
        verifyNothingSent();
    }

    // ---- 저장 실패 ----

    @Test
    void 규칙_저장이_실패하면_채팅_전송_안_함() {
        group(LEADER, "ACTIVE", "ACTIVE");
        doThrow(new RuntimeException("DB down")).when(mapper).updateRules(anyLong(), any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> putRules(LEADER, body("새 규칙", "본문")));
        verify(chat, never()).postSystem(anyLong(), any());
        verifyNoInteractions(broadcaster);
    }

    @Test
    void 채팅_저장이_실패하면_실시간_전송_안_함() {
        group(LEADER, "ACTIVE", "ACTIVE");
        when(chat.postSystem(anyLong(), anyString())).thenThrow(new RuntimeException("insert failed"));

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> putRules(LEADER, body("새 규칙", "본문")));
        verifyNoInteractions(broadcaster);
    }
}
