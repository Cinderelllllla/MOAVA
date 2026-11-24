/* S11 모임별 채팅 — /chat · F34 (MOA-78)
   방 목록·이전 메시지는 REST, 실시간 송수신은 STOMP WebSocket(api/chat.js).
   회원 여부·500자·채팅 제한·해산 모임 검사는 서버가 하고, 화면은 결과를 안내만 한다.
   메시지는 JSX 로만 그린다(자동 이스케이프). dangerouslySetInnerHTML 금지. */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { chat, groups, me } from '../api';
import { PageHead } from '../components/ui';
import { LoadState } from '../components/shared';

const STATUS_TEXT = { connecting: '연결 중…', open: '실시간 연결됨', closed: '연결이 끊겼어요. 다시 연결하는 중…', 'auth-error': '로그인이 만료되었어요. 다시 로그인해 주세요.' };

/** "2026-11-24T10:15:00" → 오늘이면 "10:15", 아니면 "11/24 10:15" */
function timeText(sentAt) {
  const d = new Date(sentAt);
  if (Number.isNaN(d.getTime())) return '';
  const hm = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  return d.toDateString() === new Date().toDateString() ? hm : `${d.getMonth() + 1}/${d.getDate()} ${hm}`;
}

/** 메시지 번호 기준으로 합치고 정렬 (REST 로 받은 것과 실시간으로 받은 것이 겹쳐도 한 번만) */
const merge = (a, b) => [...new Map([...a, ...b].map(m => [m.messageId, m])).values()].sort((x, y) => x.messageId - y.messageId);

export default function Chat() {
  const nav = useNavigate();
  const [params, setParams] = useSearchParams();
  const myId = me();

  const [rooms, setRooms] = useState({ status: 'loading', items: [], error: '' });
  const [conn, setConn] = useState('connecting');
  const [messages, setMessages] = useState([]);
  const [history, setHistory] = useState({ status: 'idle', hasMore: false, canSend: true, error: '' });
  const [text, setText] = useState('');
  const [notice, setNotice] = useState('');
  const [people, setPeople] = useState({ status: 'idle', items: [], error: '' });
  const [showPeople, setShowPeople] = useState(false);
  const socket = useRef(null);
  const log = useRef(null);
  const stickBottom = useRef(true);

  const selectedId = Number(params.get('groupId')) || rooms.items[0]?.groupId || null;
  const room = rooms.items.find(r => r.groupId === selectedId) || null;

  const loadRooms = useCallback(() => {
    setRooms(r => ({ ...r, status: 'loading', error: '' }));
    chat.fetchRooms()
      .then(items => setRooms({ status: 'ready', items: items || [], error: '' }))
      .catch(e => setRooms({ status: 'error', items: [], error: e.message }));
  }, []);

  // 화면에 들어오면 방 목록 + 웹소켓 연결 하나. 나가면 연결 종료
  useEffect(() => {
    loadRooms();
    socket.current = chat.connectChat({ onStatus: setConn, onError: setNotice });
    return () => socket.current?.disconnect();
  }, [loadRooms]);

  // 방을 고르거나 다시 연결되면: 구독 먼저 → 이전 메시지 불러와서 합치기 (그 사이 온 메시지도 놓치지 않음)
  useEffect(() => {
    if (!room) return undefined;
    setMessages([]);
    setNotice('');
    stickBottom.current = true;
    let alive = true;
    const unsubscribe = conn === 'open'
      ? socket.current.subscribeRoom(room.groupId, m => { if (alive) setMessages(prev => merge(prev, [m])); })
      : () => {};
    setHistory(h => ({ ...h, status: 'loading', error: '' }));
    chat.fetchMessages(room.groupId)
      .then(res => { if (!alive) return; setMessages(prev => merge(res.items || [], prev)); setHistory({ status: 'ready', hasMore: res.hasMore, canSend: res.canSend, error: '' }); })
      .catch(e => { if (alive) setHistory(h => ({ ...h, status: 'error', error: e.message })); });
    return () => { alive = false; unsubscribe(); };
  }, [room?.groupId, conn]); // eslint-disable-line react-hooks/exhaustive-deps

  // 참여자 목록: 방이 바뀔 때마다 새로 불러온다
  const loadPeople = useCallback(groupId => {
    setPeople(p => ({ ...p, status: 'loading', error: '' }));
    chat.fetchParticipants(groupId)
      .then(items => setPeople({ status: 'ready', items: items || [], error: '' }))
      .catch(e => setPeople({ status: 'error', items: [], error: e.message }));
  }, []);
  useEffect(() => { if (room) loadPeople(room.groupId); }, [room?.groupId, loadPeople]); // eslint-disable-line react-hooks/exhaustive-deps

  // 새 메시지가 오면 맨 아래로 (위쪽 이전 메시지를 보는 중이면 그대로)
  useEffect(() => { if (stickBottom.current) log.current?.scrollTo(0, log.current.scrollHeight); }, [messages]);

  const loadOlder = async () => {
    if (!room || !messages.length) return;
    stickBottom.current = false;
    try {
      const res = await chat.fetchMessages(room.groupId, { beforeId: messages[0].messageId });
      setMessages(prev => merge(res.items || [], prev));
      setHistory(h => ({ ...h, hasMore: res.hasMore }));
    } catch (e) { setNotice(e.message); }
  };

  const length = chat.lengthOf(text);
  const canSend = room?.canSend !== false && history.canSend;
  const send = e => {
    e.preventDefault();
    if (!text.trim() || length > chat.MAX_LENGTH) return;
    setNotice('');
    stickBottom.current = true;
    if (socket.current?.send(room.groupId, text)) setText('');
    else setNotice('채팅 서버에 연결되지 않았어요. 잠시 후 다시 시도해 주세요.');
  };

  const lockText = room?.status === 'DISSOLVED' ? '해산된 모임이라 이전 대화만 볼 수 있어요.' : '채팅이 제한되어 있어 메시지를 보낼 수 없어요. 읽기는 가능해요.';

  return <><PageHead kicker="GROUP CHAT" title="모임별 채팅 · 협의" sub="내가 가입한 계모임 채팅방을 선택하세요. 공식 동의는 모임 절차에서 진행합니다." />
    <div className="chat-workspace">
      <aside className="chat-room-sidebar">
        <div className="chat-sidebar-head"><span className="chat-sidebar-kicker">MY GROUPS</span><h2>채팅방 <small>{rooms.items.length}</small></h2><p>내가 속한 계모임</p></div>
        <div className="chat-room-list">
          <LoadState status={rooms.status} error={rooms.error} onRetry={loadRooms} loadingText="채팅방을 불러오는 중이에요.">
            {rooms.items.length ? rooms.items.map(r => <button type="button" key={r.groupId} className={`chat-room ${r.groupId === selectedId ? 'active' : ''}`} onClick={() => setParams({ groupId: String(r.groupId) })}>
              <span className="chat-room-icon">☁</span>
              <span className="chat-room-copy"><b>{r.title}</b><small>{groups.groupStatusLabel(r.status)} · {r.memberCount}명{r.lastSentAt ? ` · ${timeText(r.lastSentAt)}` : ''}</small></span>
              <span className="chat-room-arrow">›</span>
            </button>) : <div className="empty">가입한 계모임이 없어요.</div>}
          </LoadState>
        </div>
      </aside>

      {/* overflow:hidden → 이 칸이 메시지 양만큼 늘어나지 않고 틀 높이에 맞춰진다. 그래서 대화 목록(.chatlog)만 스크롤되고 입력칸은 항상 보인다 */}
      <section className="chat-main" style={{ overflow: 'hidden' }}>{room ? <>
        <div className="chat-main-head">
          <div className="chat-room-icon large">☁</div>
          <div><h2>{room.title}</h2><p className="muted mini">{groups.groupStatusLabel(room.status)} · {room.memberCount}명 참여 · <span role="status">{STATUS_TEXT[conn]}</span></p></div>
          <button type="button" className="btn soft small" aria-expanded={showPeople} onClick={() => setShowPeople(v => !v)}>
            참여자 {people.status === 'ready' ? `${people.items.length}명` : ''} {showPeople ? '▲' : '▼'}
          </button>
          <button className="btn soft small" onClick={() => nav(`/operations?groupId=${room.groupId}`)}>운영 홈으로</button>
        </div>

        {showPeople && <div className="info mini" style={{ marginTop: 10 }}>
          <LoadState status={people.status} error={people.error} onRetry={() => loadPeople(room.groupId)} loadingText="참여자를 불러오는 중이에요.">
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {people.items.map(p => <span key={p.memberId} className={`pill ${p.memberId === myId ? 'mine' : ''}`}>
                {p.name || '회원'}{p.memberId === myId ? ' (나)' : ''}{p.leader ? ' · 모임장' : ''}
              </span>)}
            </div>
          </LoadState>
        </div>}

        <div className="chatlog section" ref={log}>
          {history.hasMore && <div style={{ textAlign: 'center' }}><button type="button" className="btn small soft" onClick={loadOlder}>이전 메시지 더 보기</button></div>}
          {history.status === 'loading' && !messages.length ? <div className="chat-empty"><small>대화를 불러오는 중이에요.</small></div>
            : history.status === 'error' ? <div className="empty warning" role="alert">{history.error}</div>
              : messages.length ? messages.map(m => <div key={m.messageId} className={`bubble ${m.memberId === myId ? 'mine' : ''}`}>
                <small>{m.memberId === myId ? '나' : (m.senderName || '회원')} · {timeText(m.sentAt)}</small>
                <span style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{m.body}</span>
              </div>)
                : <div className="chat-empty"><span>☁</span><b>대화를 시작해 보세요</b><small>납입 일정이나 모임 소식을 나눠 보세요.</small></div>}
        </div>

        {notice && <p className="warning mini" role="alert">{notice}</p>}
        {canSend
          ? <form className="chat-entry" onSubmit={send}>
            <input className="control" placeholder={conn === 'open' ? '메시지를 입력해 주세요' : '연결되면 입력할 수 있어요'} autoComplete="off" value={text} onChange={e => setText(e.target.value)} disabled={conn !== 'open'} aria-label="메시지" />
            <small className={length > chat.MAX_LENGTH ? 'warning' : 'muted'} style={{ whiteSpace: 'nowrap' }}>{length}/{chat.MAX_LENGTH}</small>
            <button className="btn primary" disabled={conn !== 'open' || !text.trim() || length > chat.MAX_LENGTH}>전송</button>
          </form>
          : <div className="policy-lock">{lockText}</div>}
      </> : <div className="empty chat-no-room">{rooms.status === 'ready' && !rooms.items.length ? '계모임에 가입하면 채팅방이 생겨요.' : '채팅방을 선택해 주세요.'}</div>}
      </section>
    </div></>;
}
