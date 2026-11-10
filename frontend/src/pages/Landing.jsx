/* S00 랜딩 — / · 비로그인 · F02 */
import { Link, Navigate } from 'react-router-dom';
import { useApp } from '../common/store';

const PROBLEMS = [['여력 검증이 안 된다', '"매달 낼 수 있을까?"를 서로 믿음으로만 확인했어요.', 'AI 안전 납입액', '수입·지출·저축을 입력하면 무리 없는 납입액을 먼저 계산합니다.'], ['운영이 불투명하다', '수령 순서·규칙·정산이 단톡방 대화 속에 흩어져 있었어요.', '전원 동의 기록', '순서 변경·탈퇴 정산·규칙은 신청 → 동의 → 확정으로 남습니다.'], ['같이 할 사람을 못 찾는다', '동네에 어떤 모임이 있는지 알 길이 없었어요.', '지역 지도 매칭', '내 지역·목적·납입액에 맞는 모임을 지도에서 찾습니다.']];
const FEATURES = [['✧', 'AI 안전 납입액', '가용 금액과 위험도를 바탕으로 추천 납입액을 제시하고, 무리한 가입을 미리 막습니다.'], ['⌖', '지역 지도 매칭', '같은 동네 모임을 지도 핀으로 모아 보고, 조건에 맞는 모임에 바로 가입 신청합니다.'], ['✓', '전원 동의 운영', '수령 순서·탈퇴 정산·규칙·해산을 전원 동의로 확정하고 운영 기록으로 남깁니다.'], ['▣', '증빙 기반 건강도', '입금 증빙을 모임장이 확인하면 납입 현황과 모임 건강도 점수에 반영됩니다.']];
const STEPS = [['금융 정보 입력', '월수입·지출·저축·비상금을 대략적인 숫자로 입력합니다.'], ['소비 분석', '가용 금액과 부담률을 계산해 현재 상태를 보여줍니다.'], ['납입액 추천', '안전 계수를 적용한 추천 월 납입액을 제시합니다.'], ['모임 가입', '추천 납입액에 맞는 지역 모임을 찾아 가입을 신청합니다.']];

export default function Landing() {
  const { session, isAdmin, theme, setTheme } = useApp();
  if (session) return <Navigate to={isAdmin ? '/admin' : '/dashboard'} replace />;
  return <div className="landing">
    <nav className="ld-nav"><div className="ld-brand"><span className="brand-logo">☁</span><div>모아바<small>MOAVA · TOGETHER, BRIGHTER</small></div></div><div className="actions">
      <div className="ld-theme" aria-label="테마"><button type="button" className={theme === 'v9' ? 'on' : ''} onClick={() => setTheme('v9')}>☀ 밝음</button><button type="button" className={theme === 'v9meeting' ? 'on' : ''} onClick={() => setTheme('v9meeting')}>☾ 어두움</button></div>
      <Link className="btn small" to="/login">로그인</Link><Link className="btn small primary" to="/signup">시작하기</Link></div></nav>
    <div className="ld-wrap">
      <section className="ld-hero"><div><span className="pill">✦ AI 맞춤형 지역 계모임 운영 솔루션</span><h1>내 여력에 맞는 계모임,<br /><em>투명하게</em> 함께 모아요.</h1><p className="lead">모아바는 수입·지출 데이터로 안전한 납입액을 계산하고, 동네 모임을 찾아 주고, 수령 순서부터 정산까지 전원 동의로 기록하는 계모임 플랫폼입니다.</p><div className="actions"><Link className="btn primary" to="/signup">무료로 시작하기 →</Link><Link className="btn soft" to="/login">체험 계정으로 로그인</Link></div></div>
        <div className="ld-mock" aria-hidden="true"><div className="hero"><span className="pill">✦ SMART RECOMMENDATION</span><h2>나에게 맞는 월 납입액<br />15만 원부터 시작해 보세요.</h2></div><div className="ld-mock-row"><div><small>월 가용 금액</small><b>52만 원</b></div><div><small>연체 위험</small><b>안정</b></div><div><small>참여 모임</small><b>2개</b></div></div><div className="ld-mock-row" style={{ gridTemplateColumns: '1fr 1fr' }}><div><small>수령 순서</small><b>동의 2/3</b></div><div><small>모임 건강도</small><b>95점</b></div></div></div></section>
      <section className="ld-section"><div className="ld-kicker">WHY MOAVA</div><h2>계모임의 세 가지 불안을 해결합니다</h2><p className="muted">기존 계모임은 신뢰에만 기대야 했습니다. 모아바는 데이터와 기록으로 그 빈칸을 채웁니다.</p><div className="ld-problems">{PROBLEMS.map(([pt, pd, st, sd]) => <div className="ld-problem" key={pt}><div className="was"><b>BEFORE</b>{pt}<br />{pd}</div><div className="now"><b>MOAVA</b><strong>{st}</strong><br />{sd}</div></div>)}</div></section>
      <section className="ld-section"><div className="ld-kicker">CORE FEATURES</div><h2>핵심 기능 4가지</h2><p className="muted">회원이 매일 쓰는 화면부터 모임장이 가끔 쓰는 절차까지, 한 곳에서 처리합니다.</p><div className="ld-features">{FEATURES.map(([i, t, d]) => <div className="ld-feature" key={t}><div className="ico">{i}</div><h3>{t}</h3><p>{d}</p></div>)}</div></section>
      <section className="ld-section"><div className="ld-kicker">HOW IT WORKS</div><h2>4단계로 시작합니다</h2><p className="muted">계좌 연동 없이, 대략적인 숫자만으로도 추천을 받을 수 있어요.</p><div className="ld-steps">{STEPS.map(([t, d], i) => <div className="ld-step" key={t}><span className="n">{i + 1}</span><h3>{t}</h3><p>{d}</p></div>)}</div></section>
      <section className="ld-section"><div className="ld-kicker">TRUST &amp; SAFETY</div><h2>안심하고 써 보세요</h2><div className="ld-trust"><div><b>🔒 계좌 정보 보호</b>모임 계좌는 마스킹해 표시하고, 계좌 변경은 전원 동의 전까지 송금 보류를 안내합니다.</div><div><b>📊 데이터 기반 시연</b>연체 위험 판단은 155,746명 규모의 가명화 신용 데이터로 학습한 모델을 사용합니다.</div><div><b>ⓘ 실제 송금 없음</b>본 서비스는 KDT 팀 프로젝트 시연용이며 실제 금융 거래·인증을 수행하지 않습니다.</div></div></section>
      <section className="ld-cta"><h2>오늘부터 함께 모아봐요</h2><p>회원가입 후 누구나 모임을 만들거나, 동네 모임에 가입 신청할 수 있어요.</p><Link className="btn" to="/signup">무료로 시작하기 →</Link></section>
      <footer className="ld-foot">© 2026 MOAVA · KDT 팀 프로젝트 · 브라우저 시연용<br />체험 계정 — 회원 moa@example.com · 모임장 leader@example.com · 관리자 admin@moava.test</footer>
    </div></div>;
}
