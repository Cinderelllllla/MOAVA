/* S11 모임별 채팅 — /chat · F34 (실서비스: STOMP WebSocket) */
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { groups, proofs, me, memberName } from '../api';
import { PageHead } from '../components/ui';
import { useOpGroup } from '../components/shared';
import { won } from '../common/format';

export default function Chat() {
  const { run } = useApp(); const nav = useNavigate(); const [g, setGroup] = useOpGroup(); const mine = groups.myGroups();
  const [text, setText] = useState(''); const log = useRef();
  const msgs = g ? proofs.messages(g.id) : [];
  useEffect(() => { log.current?.scrollTo(0, 999999); }, [msgs.length, g?.id]);
  const restricted = g && g.restrictions?.[me()];
  const send = async e => { e.preventDefault(); const r = await run(() => proofs.sendChat(g.id, text)); if (r.ok) setText(''); };
  return <><PageHead kicker="GROUP CHAT" title="모임별 채팅 · 협의" sub="내가 가입한 계모임 채팅방을 선택하세요. 공식 동의는 모임 절차에서 진행합니다." />
    <div className="chat-workspace">
      <aside className="chat-room-sidebar"><div className="chat-sidebar-head"><span className="chat-sidebar-kicker">MY GROUPS</span><h2>채팅방 <small>{mine.length}</small></h2><p>내가 속한 계모임</p></div>
        <div className="chat-room-list">{mine.length ? mine.map(x => <button type="button" key={x.id} className={`chat-room ${x.id === g?.id ? 'active' : ''}`} onClick={() => setGroup(x.id)}><span className="chat-room-icon">☁</span><span className="chat-room-copy"><b>{x.title}</b><small>{x.region} · 월 {won(x.amount)}</small></span><span className="chat-room-arrow">›</span></button>) : <div className="empty">가입한 계모임이 없습니다.</div>}</div></aside>
      <section className="chat-main">{g ? <><div className="chat-main-head"><div className="chat-room-icon large">☁</div><div><h2>{g.title}</h2><p className="muted mini">{g.region} · {g.members.length}명 참여</p></div><button className="btn soft small" onClick={() => nav('/operations')}>운영 홈으로</button></div>
        <div className="chatlog section" ref={log}>{msgs.length ? msgs.map(mm => <div key={mm.id} className={`bubble ${mm.memberId === me() ? 'mine' : ''}`}><small>{memberName(mm.memberId)} · {mm.time}</small>{mm.text}</div>) : <div className="chat-empty"><span>☁</span><b>대화를 시작해 보세요</b><small>납입 일정이나 모임 소식을 나눠 보세요.</small></div>}</div>
        {restricted ? <div className="policy-lock">미납 제재로 현재 채팅 전송이 제한되어 있습니다.</div> : <form className="chat-entry" onSubmit={send}><input className="control" placeholder="메시지를 입력해 주세요" required maxLength={500} autoComplete="off" value={text} onChange={e => setText(e.target.value)} /><button className="btn primary">전송</button></form>}</> : <div className="empty chat-no-room">참여 중인 채팅방을 선택하거나 계모임에 가입해 주세요.</div>}
        <p className="muted mini chat-demo-note">브라우저 내 시연용 채팅이며 실시간 서버(STOMP) 연결은 백엔드 연동 후 동작합니다.</p></section>
    </div></>;
}
