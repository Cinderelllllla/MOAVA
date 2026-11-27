/* F34 모임별 실시간 채팅 — REST(방 목록·이전 메시지) + STOMP WebSocket(실시간 송수신)
   주소(API 명세서 7.3절 [초안]):
     연결 /ws  (CONNECT 헤더 Authorization: Bearer {JWT})
     보내기 SEND /app/groups/{groupId}/chat   {"body":"..."}
     받기   SUBSCRIBE /topic/groups/{groupId}/chat
     내 오류 SUBSCRIBE /user/queue/errors      {"code","message"}
   검사(회원 여부·500자·채팅 제한·해산 모임)는 서버가 한다. 화면은 안내만 한다. */
import { Client } from '@stomp/stompjs';
import { apiFetch, getToken } from './client';

export const MAX_LENGTH = 500;

/** 내 채팅방(승인된 회원인 모임) 목록 → [{groupId, title, status, memberCount, lastSentAt, canSend}] */
export const fetchRooms = () => apiFetch('/api/v1/chat/rooms');

/** 이전 메시지 (오래된 것 → 최신 순) → {items:[{messageId, groupId, memberId, senderName, body, sentAt, system}], hasMore, canSend}
    system=true 면 운영 규칙 변경 안내 같은 시스템 메시지 (memberId·senderName 은 null). 실시간(/topic)도 같은 모양 */
export function fetchMessages(groupId, { beforeId, size = 30 } = {}) {
  const q = new URLSearchParams({ size: String(size) });
  if (beforeId) q.set('beforeId', String(beforeId));
  return apiFetch(`/api/v1/chat/groups/${groupId}/messages?${q}`);
}

/* MOA-96 관리자 신고 건 대상 모임 채팅 열람 — 관리자 읽기 전용, 열람할 때마다 서버에 열람 기록(AUDIT_LOG CHAT_VIEW)이 남는다.
   보내기·실시간 구독은 없다. 보낸 사람 이름은 서버가 가려서 준다. */
/** 관리자가 채팅을 열람할 수 있는 신고 상태 (처리 중). 서버 ChatService.REPORT_VIEWABLE_STATUSES 와 같다 */
export const REPORT_CHAT_VIEWABLE_STATUSES = ['RECEIVED', 'REVIEWING', 'MORE_INFO'];
/** 이 신고에서 대상 모임 채팅을 열람할 수 있는지 (대상 모임이 있고 처리 중) */
export const canViewReportChat = report => report?.groupId != null && REPORT_CHAT_VIEWABLE_STATUSES.includes(report.status);
/** 신고 대상 모임의 이전 메시지 (오래된 것 → 최신 순) → {items, hasMore, canSend:false}. 모양은 fetchMessages 와 같다 */
export function fetchReportChatMessages(reportId, { beforeId, size = 30 } = {}) {
  const q = new URLSearchParams({ size: String(size) });
  if (beforeId) q.set('beforeId', String(beforeId));
  return apiFetch(`/api/v1/admin/support/reports/${reportId}/chat/messages?${q}`);
}

/** 채팅방 참여자(모임의 승인된 회원) → [{memberId, name, leader, joinedAt}] — 모임장 먼저 */
export const fetchParticipants = groupId => apiFetch(`/api/v1/chat/groups/${groupId}/participants`);

/** 글자 수 (이모지도 1글자로 셈 — 서버와 같은 기준) */
export const lengthOf = text => [...String(text || '')].length;

function socketUrl() {
  const { protocol, host } = window.location;   // 개발: Vite 가 /ws 를 8080 으로 넘겨 준다
  return `${protocol === 'https:' ? 'wss' : 'ws'}://${host}/ws`;
}

/**
 * 웹소켓 연결 하나를 만든다. 끊기면 5초 뒤 자동으로 다시 연결한다(로그인 오류면 멈춤).
 * @param {{onStatus?: (s:'connecting'|'open'|'closed'|'auth-error') => void, onError?: (msg:string) => void}} handlers
 * @returns {{ subscribeRoom, send, disconnect }}
 */
export function connectChat({ onStatus = () => {}, onError = () => {} } = {}) {
  const client = new Client({
    brokerURL: socketUrl(),
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    // 다시 연결할 때마다 최신 토큰을 넣는다
    beforeConnect: () => {
      client.connectHeaders = { Authorization: `Bearer ${getToken() || ''}` };
      onStatus('connecting');
    },
  });

  client.onConnect = () => {
    onStatus('open');
    // 내가 보낸 메시지가 거부되면 서버가 나에게만 알려 준다 (500자 초과, 채팅 제한 등)
    client.subscribe('/user/queue/errors', frame => {
      try { onError(JSON.parse(frame.body).message || '메시지를 보내지 못했어요.'); }
      catch { onError('메시지를 보내지 못했어요.'); }
    });
  };
  client.onStompError = frame => {
    const msg = frame.headers?.message || '채팅 서버와 연결하지 못했어요.';
    if (/로그인/.test(msg)) {           // 토큰 문제는 다시 연결해도 같으므로 멈춘다
      onStatus('auth-error');
      client.deactivate();
    }
    onError(msg);
  };
  client.onWebSocketClose = () => { if (client.active) onStatus('closed'); };
  client.activate();

  return {
    /** 방 구독. 반환값을 호출하면 구독 해제 */
    subscribeRoom(groupId, onMessage) {
      if (!client.connected) return () => {};
      const sub = client.subscribe(`/topic/groups/${groupId}/chat`, frame => {
        try { onMessage(JSON.parse(frame.body)); } catch { /* 형식이 다른 메시지는 무시 */ }
      });
      return () => { try { sub.unsubscribe(); } catch { /* 이미 끊긴 연결 */ } };
    },
    /** 메시지 보내기. 연결이 없으면 false */
    send(groupId, body) {
      if (!client.connected) return false;
      client.publish({ destination: `/app/groups/${groupId}/chat`, body: JSON.stringify({ body }) });
      return true;
    },
    disconnect() { client.deactivate(); },
  };
}
