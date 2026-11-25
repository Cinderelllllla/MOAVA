package com.moava.chat;

import com.moava.chat.dto.ChatAccess;
import com.moava.chat.dto.ChatMessageRow;
import com.moava.chat.dto.ChatParticipant;
import com.moava.chat.dto.ChatRoomRow;
import com.moava.chat.mapper.ChatMapper;
import com.moava.chat.service.ChatException;
import com.moava.chat.service.ChatService;
import com.moava.crypto.service.CryptoKeyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatMapper mapper;
    @Mock CryptoKeyService crypto;
    @InjectMocks ChatService service;

    private static final long ME = 7L, GROUP = 101L;

    private void access(String groupStatus, String memberStatus, String restrictedYn, String lastSanction) {
        when(mapper.selectAccess(GROUP, ME)).thenReturn(new ChatAccess(groupStatus, memberStatus, restrictedYn, lastSanction));
    }

    private ChatException sendFails(String body) {
        return assertThrows(ChatException.class, () -> service.send(ME, GROUP, body));
    }

    // ---- 보내기: 정상 ----

    @Test
    void 승인된_회원은_암호화해서_저장한_뒤_평문으로_돌려준다() {
        access("ACTIVE", "ACTIVE", "N", null);
        when(crypto.getCurrentKeyVersion()).thenReturn(1);
        when(crypto.encrypt("안녕하세요", 1)).thenReturn("ENC");
        when(mapper.nextMessageId()).thenReturn(55L);
        when(mapper.selectMemberName(ME)).thenReturn("이모아");

        var res = service.send(ME, GROUP, "안녕하세요");

        verify(mapper).insertMessage(eq(55L), eq(GROUP), eq(ME), eq("ENC"), eq(1), any(LocalDateTime.class));
        assertEquals(55L, res.messageId());
        assertEquals("안녕하세요", res.body());
        assertEquals("이모아", res.senderName());
    }

    @Test
    void 채팅_제한을_해제받았으면_보낼_수_있다() {
        access("ACTIVE", "ACTIVE", "N", "CHAT_RELEASE");
        when(crypto.getCurrentKeyVersion()).thenReturn(1);
        when(crypto.encrypt(anyString(), anyInt())).thenReturn("ENC");
        assertDoesNotThrow(() -> service.send(ME, GROUP, "다시 왔어요"));
    }

    // ---- 보내기: 차단 ----

    @Test
    void 가입_기록이_없는_회원은_차단() {   // 비회원·가입 승인 전 (JOIN_REQUEST 만 있고 GYE_MEMBER 없음)
        access("ACTIVE", null, null, null);
        assertEquals("NOT_GROUP_MEMBER", sendFails("hi").getCode());
        verify(mapper, never()).insertMessage(anyLong(), anyLong(), anyLong(), any(), anyInt(), any());
    }

    @Test
    void 탈퇴하거나_강퇴된_회원은_차단() {
        access("ACTIVE", "WITHDRAWN", "N", null);
        assertEquals("NOT_GROUP_MEMBER", sendFails("hi").getCode());
        reset(mapper);
        access("ACTIVE", "EXPELLED", "N", null);
        assertEquals("NOT_GROUP_MEMBER", sendFails("hi").getCode());
    }

    @Test
    void 없는_모임은_404() {
        when(mapper.selectAccess(GROUP, ME)).thenReturn(null);
        assertEquals("GROUP_NOT_FOUND", sendFails("hi").getCode());
    }

    @Test
    void 해산_완료_모임은_보내기_차단() {
        access("DISSOLVED", "ACTIVE", "N", null);
        assertEquals("GROUP_DISSOLVED", sendFails("hi").getCode());
    }

    @Test
    void 관리자_채팅_제한_회원은_보내기_차단() {
        access("ACTIVE", "ACTIVE", "N", "CHAT_RESTRICT");
        assertEquals("CHAT_RESTRICTED", sendFails("hi").getCode());
    }

    @Test
    void 미납_제재로_채팅_제한된_회원은_보내기_차단() {
        access("ACTIVE", "ACTIVE", "Y", null);
        assertEquals("CHAT_RESTRICTED", sendFails("hi").getCode());
    }

    // ---- 본문 검사 (DB 조회 전에 거른다) ----

    @Test
    void 빈_메시지는_거부() {
        assertEquals("EMPTY_MESSAGE", sendFails(null).getCode());
        assertEquals("EMPTY_MESSAGE", sendFails("   ").getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void 영문_500자는_통과하고_501자는_거부() {
        access("ACTIVE", "ACTIVE", "N", null);
        when(crypto.getCurrentKeyVersion()).thenReturn(1);
        when(crypto.encrypt(anyString(), anyInt())).thenReturn("ENC");
        assertDoesNotThrow(() -> service.send(ME, GROUP, "a".repeat(500)));
        assertEquals("MESSAGE_TOO_LONG", sendFails("a".repeat(501)).getCode());
    }

    @Test
    void 한글_이모지도_500자까지는_보낼_수_있다() {
        // BODY_ENC(2800) 기준: 한글 500자 = 1500바이트, 이모지 500자 = 2000바이트 → 둘 다 2072바이트 안
        access("ACTIVE", "ACTIVE", "N", null);
        when(crypto.getCurrentKeyVersion()).thenReturn(1);
        when(crypto.encrypt(anyString(), anyInt())).thenReturn("ENC");
        assertDoesNotThrow(() -> service.send(ME, GROUP, "가".repeat(500)));
        assertDoesNotThrow(() -> service.send(ME, GROUP, "😀".repeat(500)));
        assertEquals("MESSAGE_TOO_LONG", sendFails("가".repeat(501)).getCode());
    }

    // ---- 읽기 ----

    @Test
    void 해산_완료_모임도_이전_대화는_읽을_수_있다() {
        access("DISSOLVED", "ACTIVE", "N", null);
        when(mapper.selectMessages(GROUP, null, 31)).thenReturn(List.of());
        var res = service.history(ME, GROUP, null, null);
        assertFalse(res.canSend());
    }

    @Test
    void 채팅_제한_회원도_읽기는_가능() {
        access("ACTIVE", "ACTIVE", "Y", null);
        when(mapper.selectMessages(GROUP, null, 31)).thenReturn(List.of());
        assertFalse(service.history(ME, GROUP, null, null).canSend());
    }

    @Test
    void 비회원은_이전_대화도_못_읽는다() {
        access("ACTIVE", null, null, null);
        assertThrows(ChatException.class, () -> service.history(ME, GROUP, null, null));
        verify(mapper, never()).selectMessages(anyLong(), any(), anyInt());
    }

    @Test
    void 이전_대화는_오래된_순으로_주고_더_있는지_알려준다() {
        access("ACTIVE", "ACTIVE", "N", null);
        LocalDateTime t = LocalDateTime.of(2026, 11, 24, 10, 0);
        // DB 는 최신부터 limit+1(=3) 개를 준다
        when(mapper.selectMessages(GROUP, null, 3)).thenReturn(List.of(
                new ChatMessageRow(30, GROUP, ME, "이모아", "E30", 1, t),
                new ChatMessageRow(20, GROUP, ME, "이모아", "E20", 1, t),
                new ChatMessageRow(10, GROUP, ME, "이모아", "E10", 1, t)));
        when(crypto.decrypt(anyString(), eq(1))).thenAnswer(inv -> "본문" + inv.getArgument(0, String.class).substring(1));

        var res = service.history(ME, GROUP, null, 2);

        assertTrue(res.hasMore());
        assertEquals(2, res.items().size());
        assertEquals(20, res.items().get(0).messageId());
        assertEquals(30, res.items().get(1).messageId());
        assertEquals("본문30", res.items().get(1).body());
        assertTrue(res.canSend());
    }

    @Test
    void 복호화에_실패한_메시지는_안내_문구로_바꾸고_나머지는_보여_준다() {
        access("ACTIVE", "ACTIVE", "N", null);
        LocalDateTime t = LocalDateTime.now();
        when(mapper.selectMessages(GROUP, null, 31)).thenReturn(List.of(
                new ChatMessageRow(2, GROUP, ME, "이모아", "OK", 1, t),
                new ChatMessageRow(1, GROUP, ME, "이모아", "BROKEN", 1, t)));
        when(crypto.decrypt("OK", 1)).thenReturn("정상");
        when(crypto.decrypt("BROKEN", 1)).thenThrow(new RuntimeException("bad tag"));

        var items = service.history(ME, GROUP, null, null).items();

        assertEquals("(메시지를 불러오지 못했어요)", items.get(0).body());
        assertEquals("정상", items.get(1).body());
    }

    @Test
    void 한_번에_불러올_개수는_1에서_100() {
        access("ACTIVE", "ACTIVE", "N", null);
        assertEquals("INVALID_SIZE", assertThrows(ChatException.class, () -> service.history(ME, GROUP, null, 0)).getCode());
        assertEquals("INVALID_SIZE", assertThrows(ChatException.class, () -> service.history(ME, GROUP, null, 101)).getCode());
    }

    // ---- 참여자 ----

    @Test
    void 회원은_참여자_목록을_볼_수_있다() {
        access("ACTIVE", "ACTIVE", "N", null);
        when(mapper.selectParticipants(GROUP)).thenReturn(List.of(
                new ChatParticipant(2, "김하늘", true, null),
                new ChatParticipant(ME, "이모아", false, null)));
        var list = service.participants(ME, GROUP);
        assertEquals(2, list.size());
        assertTrue(list.get(0).leader());
    }

    @Test
    void 비회원은_참여자_목록도_못_본다() {
        access("ACTIVE", null, null, null);
        assertThrows(ChatException.class, () -> service.participants(ME, GROUP));
        verify(mapper, never()).selectParticipants(anyLong());
    }

    // ---- 방 목록 ----

    @Test
    void 방_목록의_보내기_가능_여부() {
        when(mapper.selectRooms(ME)).thenReturn(List.of(
                new ChatRoomRow(1, "정상", "ACTIVE", 3, null, "N", null),
                new ChatRoomRow(2, "해산", "DISSOLVED", 3, null, "N", null),
                new ChatRoomRow(3, "미납 제재", "ACTIVE", 3, null, "Y", null),
                new ChatRoomRow(4, "관리자 제재", "RECRUITING", 3, null, "N", "CHAT_RESTRICT")));
        var rooms = service.rooms(ME);
        assertTrue(rooms.get(0).canSend());
        assertFalse(rooms.get(1).canSend());
        assertFalse(rooms.get(2).canSend());
        assertFalse(rooms.get(3).canSend());
    }
}
