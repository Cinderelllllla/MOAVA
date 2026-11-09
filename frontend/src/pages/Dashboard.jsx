/* S02 마이 홈 — /dashboard · F08 F17 F35 */
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { finance, groups, admin, leader } from '../api';
import { PageHead, Card } from '../components/ui';
import { BarChart, RiskLabel } from '../components/shared';
import { won } from '../common/format';

export default function Dashboard() {
  const { user, setUi } = useApp(); const nav = useNavigate();
  const a = finance.affordability(), mine = groups.myGroups(), pending = admin.myNotices().filter(n => !n.read).length, shown = mine.slice(0, 3);
  const Stat = ({ label, val, ico, to }) => <button type="button" className="card stat" style={{ textAlign: 'left', cursor: 'pointer', font: 'inherit' }} onClick={() => nav(to)}><div className="symbol">{ico}</div><small>{label}</small><div className="value">{val}</div><small>자세히 보기 →</small></button>;
  return <><PageHead kicker="MY FINANCIAL SKY" title={`안녕하세요, ${user.name}님 ☁`} sub="오늘도 내 속도에 맞춰, 부담 없이 함께 모아봐요." />
    <div className="hero"><span className="pill">✦ MOAVA SMART RECOMMENDATION</span><h2>나에게 맞는 월 납입액<br />{won(a.rec)}부터 시작해 보세요.</h2><p style={{ opacity: .9, margin: '0 0 14px', fontSize: 13 }}><RiskLabel /></p><button className="btn" onClick={() => nav('/journey')}>맞춤 추천 시작 →</button></div>
    <div className="grid cols4 section"><Stat label="추천 월 납입액" val={won(a.rec)} ico="✧" to="/journey" /><Stat label="월 가용 금액" val={won(a.free)} ico="◉" to="/finance" /><Stat label="참여 계모임" val={mine.length + '개'} ico="☁" to="/my-groups" /><Stat label="읽지 않은 알림" val={pending + '건'} ico="♧" to="/notifications" /></div>
    <div className="grid cols2 section">
      <Card><div className="section-title"><h2 style={{ margin: 0 }}>월별 수입 · 지출 흐름</h2><button className="btn small soft" onClick={() => nav('/finance/monthly')}>더보기 →</button></div><BarChart data={finance.getMonthly()} limit={6} /></Card>
      <Card><div className="section-title"><h2 style={{ margin: 0 }}>나의 계모임 <small className="muted">{mine.length}개</small></h2><button className="btn small soft" onClick={() => nav('/my-groups')}>더보기 →</button></div>
        <div className="list">{shown.length ? shown.map(g => <button type="button" key={g.id} className="list-item group-choice" style={{ width: '100%', textAlign: 'left', font: 'inherit', cursor: 'pointer' }} onClick={() => { setUi({ selected: g.id }); nav('/operations'); }}><div className="item-head" style={{ margin: 0 }}><b>{g.title}</b><span className="pill">{leader(g) ? 'Leader' : 'Member'}</span></div><p className="muted mini" style={{ margin: '6px 0 0' }}>월 {won(g.amount)} · 매월 {g.payDay}일 · {g.region} · {g.members.length}/{g.max}명 · 운영 홈 →</p></button>) : <div className="empty">참여 중인 모임이 없어요.</div>}</div>
        {/* Tailwind 보조 사용 예: 기존 클래스(muted mini) + 유틸리티(mt-3 = margin-top 12px) */}
        {mine.length > 3 && <p className="muted mini mt-3 mb-0">외 {mine.length - 3}개 모임은 더보기에서 확인하세요.</p>}</Card>
    </div></>;
}
