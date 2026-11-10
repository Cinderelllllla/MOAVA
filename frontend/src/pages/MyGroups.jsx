/* S08 내 계모임 — /my-groups · F17 F18 (목록 전용 · 해산 완료 접기) */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { groups, finance, leader } from '../api';
import { PageHead, Card } from '../components/ui';
import { won } from '../common/format';

export default function MyGroups() {
  const { setUi } = useApp(); const nav = useNavigate(); const [health, setHealth] = useState(null);
  const mine = groups.myGroups(), ended = groups.endedGroups(), income = finance.getFinance().income;
  const avg = mine.length ? Math.round(mine.reduce((a, g) => a + groups.healthScore(g), 0) / mine.length) : 0;
  const burden = income ? Math.round(mine.reduce((a, g) => a + g.amount, 0) / income * 100) : 0;
  return <><PageHead kicker="MY GROUPS" title="내 계모임" sub="가입한 계모임 목록입니다. 모임별 상세와 운영은 운영 홈에서 진행합니다."><button className="btn primary" onClick={() => nav('/groups/new')}>새 계모임 만들기</button><button className="btn soft" onClick={() => nav('/groups')}>모임 찾기 →</button></PageHead>
    <div className="grid cols3"><Card><small>참여 중인 계모임</small><div className="metric">{mine.length}개</div></Card><Card><small>참여 모임 평균 건강도</small><div className="metric">{mine.length ? avg + '점' : '—'}</div></Card><Card><small>개인 납입 부담률 (참고)</small><div className="metric">{burden}% {burden > 12 && <span className="pill warn">12% 초과</span>}</div></Card></div>
    <div className="section list">{mine.length ? mine.map(g => <Card key={g.id}><div className="item-head"><h2 style={{ margin: 0 }}>{g.title}</h2><span className="pill">{leader(g) ? 'Leader' : 'Member'}</span></div><p className="muted">{g.region} · 월 {won(g.amount)} · 매월 {g.payDay}일 · {g.months}개월 · {g.members.length}/{g.max}명 · <span className="pill">{g.status}</span>{g.visibility === 'PRIVATE' && <> <span className="pill">🔒 비공개</span></>}</p>
      <div className="actions"><button className="btn primary" onClick={() => { setUi({ selected: g.id }); nav('/operations'); }}>운영 홈 · 상세</button><button className="btn soft" onClick={() => setHealth(health === g.id ? null : g.id)}>모임 건강도</button><button className="btn soft" onClick={() => { setUi({ selected: g.id }); nav('/chat'); }}>채팅</button></div>
      {health === g.id && <div className="detail-panel"><h3>이 모임의 건강도 · {groups.healthScore(g)}점</h3><div className="mini-progress"><span style={{ width: `${groups.healthScore(g)}%` }} /></div><p className="muted mini">95점 기준 − 경고 ×9 − 증빙 보완 요청 ×6 − 미납 감지 ×5 − 해산 검토 12. 데모 지표입니다.</p><p>나의 월 납입 부담: 월수입 대비 {income ? Math.round(g.amount / income * 100) : 0}%</p></div>}</Card>) : <div className="empty">아직 가입한 모임이 없어요. 지역 모임을 찾아보세요.</div>}</div>
    {ended.length > 0 && <details className="section policy-fold"><summary>종료된 모임 {ended.length}개 보기</summary><div className="list" style={{ marginTop: 12 }}>{ended.map(g => <div className="list-item" key={g.id}><div className="item-head"><b>{g.title}</b><span className="pill">해산 완료</span></div><p className="muted mini">{g.dissolution?.reason} · {g.dissolution?.requestedAt}</p></div>)}</div></details>}</>;
}
