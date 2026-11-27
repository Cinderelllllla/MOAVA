/* 랜딩 스크롤 스토리 — 화면이 고정된 채로, 스크롤할 때마다 장면이 바뀐다 (토스 소개 페이지 방식)
   ------------------------------------------------------------
   ▶ 장면마다 '모니터 속 브라우저 창' 그림 안에 모아바 웹 화면을 그린다 (PC 웹 서비스이므로 휴대폰이 아닌 브라우저).
     화면 안 숫자·이름은 꾸밈용 예시 값이며 실제 데이터가 아니다.
   ▶ 영상/사진으로 바꾸는 법
     1) 파일을 frontend/public/story/ 폴더에 넣는다. (예: frontend/public/story/scene1.mp4)
     2) 아래 SCENES 의 해당 장면에  media: '/story/scene1.mp4'  를 적는다. (사진이면 .jpg/.png/.webp)
     3) 영상만 보여 주고 모니터 그림을 숨기려면  window: false  도 적는다.
   ▶ 동작 원리
     - 바깥 section 의 높이를 (장면 수 + 1) × 화면 높이로 크게 만들고, 안쪽 화면은 sticky 로 고정한다.
     - 스크롤 위치(0~1)를 장면 번호로 바꿔서, 그 장면만 보이게 한다.
   ============================================================ */
import { useEffect, useRef, useState } from 'react';
import Icon from './Icon';

const SCENES = [
  // 첫 장면 = 랜딩 메인 문구
  { key: 'intro', intro: true, page: 'home', kicker: 'AI 맞춤형 지역 계모임 운영 솔루션', title: <>내 여력에 맞는 계모임,<br /><em>투명하게</em> 함께 모아요.</>,
    lead: '수입·지출 데이터로 안전한 납입액을 계산하고, 동네 모임을 찾아 주고, 수령 순서부터 정산까지 전원 동의로 기록해요.',
    bg: 'linear-gradient(140deg,#0f3d73 0%,#1a72c4 55%,#5bb8ee 100%)' },
  { key: 'input', page: 'journey', kicker: '01 · 금융 정보', title: <>매달 낼 수 있을까?<br />이제 감으로 정하지 마세요</>, bg: 'linear-gradient(140deg,#0f2a4a 0%,#1a5fa8 100%)' },
  { key: 'ai', page: 'finance', kicker: '02 · AI 추천', title: <>AI가 먼저 계산해요<br />무리 없는 내 납입액</>, bg: 'linear-gradient(140deg,#1567b8 0%,#4bb3ed 100%)' },
  { key: 'map', page: 'groups', kicker: '03 · 지역 매칭', title: <>우리 동네 계모임을<br />지도에서 한눈에</>, bg: 'linear-gradient(140deg,#0b5f57 0%,#17b897 100%)' },
  { key: 'consent', page: 'procedures', kicker: '04 · 전원 동의', title: <>순서도, 정산도<br />모두가 동의해야 확정</>, bg: 'linear-gradient(140deg,#2f2a7a 0%,#7d6cf0 100%)' },
  { key: 'health', page: 'operations', kicker: '05 · 함께 모으는 내일', title: <>믿을 수 있는 계모임<br />마침내 MOAVA로</>, bg: 'linear-gradient(140deg,#0b1a2e 0%,#238cdb 100%)' },
];

/* 브라우저 창 안 왼쪽 메뉴: [아이콘, 이름, 페이지 키, 주소] */
const MENU = [['home', '마이 홈', 'home', 'dashboard'], ['sparkles', '맞춤 추천', 'journey', 'journey'], ['pie', 'AI 금융 분석', 'finance', 'finance'],
  ['pin', '계모임 찾기', 'groups', 'groups'], ['list', '모임 절차', 'procedures', 'procedures'], ['sliders', '운영 홈', 'operations', 'operations']];

const Head = ({ k, t }) => <div className="w-head"><small>{k}</small><b>{t}</b></div>;
const Pill = ({ tone = '', children }) => <span className={`w-pill ${tone}`}>{children}</span>;
const BARS = [[70, 48], [76, 52], [72, 58], [80, 50], [78, 46], [84, 52], [82, 49], [86, 51]];

/* ---- 브라우저 창 안 본문 (실제 모아바 웹 화면 배치를 단순화해 그린 것) ---- */
function Page({ page }) {
  if (page === 'home') return <>
    <Head k="마이 홈" t="안녕하세요, 이모아님" />
    <div className="w-hero"><small>AI 맞춤 추천</small><b>나에게 맞는 월 납입액<br />15만 원부터 시작해 보세요.</b><span className="w-btn light">맞춤 추천 시작 →</span></div>
    <div className="w-stats">{[['sparkles', '추천 월 납입액', '15만 원', 'blue'], ['pie', '월 가용 금액', '52만 원', 'green'], ['users', '내 계모임', '2개', 'purple'], ['bell', '읽지 않은 알림', '3건', 'orange']].map(([ic, l, v, tone]) =>
      <div className="w-card" key={l}><span className={`w-ico ${tone}`}><Icon name={ic} /></span><small>{l}</small><b>{v}</b></div>)}</div>
    <div className="w-two">
      <div className="w-card"><b className="w-ct">월별 수입 · 지출</b><div className="w-chart">{BARS.slice(0, 6).map(([a, b], i) => <span key={i}><i style={{ height: `${a}%` }} /><i style={{ height: `${b}%` }} /></span>)}</div></div>
      <div className="w-card"><b className="w-ct">내 계모임</b>{[['마포 직장인 목돈 모으기', '월 20만 원 · 매월 5일'], ['홍대 주말 친목 계모임', '월 10만 원 · 매월 15일']].map(([t, d]) => <div className="w-row" key={t}><span><b>{t}</b><small>{d}</small></span><Pill tone="blue">참여 중</Pill></div>)}</div>
    </div>
  </>;
  if (page === 'journey') return <>
    <Head k="맞춤 추천 시작" t="금융 정보를 알려주세요" />
    <div className="w-steps">{['1. 금융 정보 입력', '2. 소비 분석', '3. 납입액 추천', '4. 모임 추천'].map((s, i) => <span key={s} className={i === 0 ? 'on' : ''}>{s}</span>)}</div>
    <div className="w-card"><div className="w-form">{[['월수입', '320'], ['고정지출', '140'], ['변동지출', '70'], ['월 저축액', '30'], ['비상예비금', '200'], ['희망 월 납입액', '20']].map(([l, v]) =>
      <div key={l}><small>{l} (만 원)</small><span className="w-input">{v}</span></div>)}</div>
      <div className="w-check"><i /> 금융정보 분석 및 추천 목적의 데이터 이용에 동의합니다.</div>
      <span className="w-btn">다음 단계 →</span></div>
  </>;
  if (page === 'finance') return <>
    <Head k="AI 금융 분석" t="이모아님의 분석 결과" />
    <div className="w-two">
      <div className="w-card w-big"><small>추천 월 납입액</small><b>15만 원</b><div className="w-gauge"><i style={{ width: '29%' }} /></div></div>
      <div className="w-card">{[['연체 위험 등급', <Pill tone="green" key="p">안정</Pill>], ['연체 확률', '4.2%'], ['안전계수', '0.5'], ['월 가용 금액', '52만 원']].map(([l, v]) => <div className="w-kv" key={l}><small>{l}</small><b>{v}</b></div>)}</div>
    </div>
    <div className="w-card"><b className="w-ct">월별 수입 · 지출</b><div className="w-chart short">{BARS.map(([a, b], i) => <span key={i}><i style={{ height: `${a}%` }} /><i style={{ height: `${b}%` }} /></span>)}</div></div>
  </>;
  if (page === 'groups') return <>
    <Head k="지역 계모임 찾기" t="우리 동네 계모임" />
    <div className="w-two w-mapgrid">
      <div className="w-list">{[['마포 직장인 목돈 모으기', '마포구 · 월 20만 원 · 2/6명', true], ['서대문 우리동네 재테크', '서대문구 · 월 15만 원 · 1/6명'], ['성수 창업 준비 모임', '성동구 · 월 25만 원 · 1/5명']].map(([t, d, on]) =>
        <div className={`w-card w-group ${on ? 'on' : ''}`} key={t}><b>{t}</b><small>{d}</small><span className={`w-btn sm ${on ? '' : 'soft'}`}>{on ? '가입 신청' : '상세 보기'}</span></div>)}</div>
      <div className="w-map"><span className="w-mpin" style={{ left: '30%', top: '30%' }}>마포 3</span><span className="w-mpin on" style={{ left: '58%', top: '50%' }}>서대문 2</span><span className="w-mpin" style={{ left: '38%', top: '72%' }}>성수 1</span><span className="w-mpin" style={{ left: '78%', top: '22%' }}>강남 1</span></div>
    </div>
  </>;
  if (page === 'procedures') return <>
    <Head k="모임 절차" t="수령 순서 변경 요청" />
    <div className="w-tabs">{['수령 순서', '탈퇴 정산', '규칙 변경', '해산'].map((s, i) => <span key={s} className={i === 0 ? 'on' : ''}>{s}</span>)}</div>
    <div className="w-card">
      <div className="w-row"><span><b>김하늘 → 2회차로 변경</b><small>마포 직장인 목돈 모으기 · 11월 20일 신청</small></span><Pill tone="orange">동의 진행 중</Pill></div>
      {[['김하늘', '모임장', true], ['이모아', '모임원', true], ['박지원', '모임원', false]].map(([n, r, ok]) => <div className="w-row" key={n}><span className="w-who"><i>{n[0]}</i><span><b>{n}</b><small>{r}</small></span></span><Pill tone={ok ? 'green' : ''}>{ok ? '동의' : '대기'}</Pill></div>)}
      <div className="w-gauge"><i style={{ width: '66%' }} /></div>
      <small className="w-note">3명 중 2명이 동의했어요. 모두 동의하면 순서가 확정되고 운영 기록에 남아요.</small>
      <div className="w-actions"><span className="w-btn">동의하기</span><span className="w-btn soft">반대</span></div>
    </div>
  </>;
  return <>
    <Head k="운영 홈" t="마포 직장인 목돈 모으기" />
    <div className="w-stats">{[['가입 신청 대기', '1건', 'on'], ['증빙 확인 대기', '2건', 'on'], ['수령 순서 처리', '0건', ''], ['내 동의 필요', '0건', '']].map(([l, v, on]) =>
      <div className={`w-card w-todo ${on}`} key={l}><small>{l}</small><b>{v}</b></div>)}</div>
    <div className="w-two">
      <div className="w-card w-health"><div className="w-ring"><b>95</b><small>점</small></div><span><b className="w-ct">모임 건강도</b><small>이번 달 납입 6/6명 · 연체 0건</small></span></div>
      <div className="w-card">{[['모집', '진행 중 · 6/6명'], ['납입일', '매월 5일'], ['수령 순서', '확정'], ['모임 계좌', '카카오뱅크 3333-01-****']].map(([l, v]) => <div className="w-kv" key={l}><small>{l}</small><b>{v}</b></div>)}</div>
    </div>
  </>;
}

/** 모니터 그림: 검은 테두리(카메라) 안에 브라우저 창, 아래에 받침대 */
function Monitor({ page }) {
  return <div className="ss-monitor">
    <div className="ss-bezel"><i className="ss-cam" /><div className="ss-display"><BrowserWindow page={page} /><i className="ss-glare" /></div></div>
    <div className="ss-neck" /><div className="ss-foot" /><div className="ss-shadow" />
  </div>;
}

/** 브라우저 창 그림 (주소창 + 왼쪽 메뉴 + 본문) */
function BrowserWindow({ page }) {
  const cur = MENU.find(m => m[2] === page) || MENU[0];
  return <div className="ss-browser">
    <div className="ss-bbar"><i /><i /><i /><span className="ss-url"><Icon name="shield" size={12} />moava.app/{cur[3]}</span></div>
    <div className="ss-bview">
      <aside className="w-side"><div className="w-logo"><span /><b>모아바</b></div>{MENU.map(([ic, name, key]) => <div key={key} className={`w-nav ${key === page ? 'on' : ''}`}><Icon name={ic} /><span>{name}</span></div>)}</aside>
      <div className="w-main">
        <div className="w-top"><span>MOAVA / <b>{cur[1]}</b></span><span className="w-user"><Icon name="user" /> 이모아</span></div>
        <div className="w-body"><Page page={page} /></div>
      </div>
    </div>
  </div>;
}

export default function ScrollStory() {
  const ref = useRef(null);
  const [active, setActive] = useState(0);
  // 휴대폰(760px 이하): 장면을 카드로 쌓고, 화면에 들어온 카드만 아래에서 올라오게 한다
  const [mobile, setMobile] = useState(() => typeof window !== 'undefined' && window.matchMedia?.('(max-width: 760px)').matches);
  const [seen, setSeen] = useState({});   // 한 번 화면에 들어온 카드 { key: true }
  const anim = mobile && typeof window !== 'undefined' && 'IntersectionObserver' in window && !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  const n = SCENES.length;

  useEffect(() => {
    const el = ref.current; if (!el) return undefined;
    // 스크롤 이벤트는 브라우저가 화면 그리는 주기에 맞춰 보내므로 바로 계산해도 가볍다
    const update = () => {
      const r = el.getBoundingClientRect(), vh = window.innerHeight;
      const progress = Math.min(1, Math.max(0, -r.top / Math.max(1, r.height - vh)));
      setActive(Math.min(n - 1, Math.floor(progress * n)));
      // 첫 장면: 둥근 카드(여백 있음) → 화면 가득으로 커지는 정도 (1 = 카드, 0 = 꽉 참)
      // 휴대폰: 스크롤을 내리기 시작하면(화면 높이의 35% 동안) 첫 카드가 좌우로 넓어져 화면 가득 찬다
      const inset = window.matchMedia('(max-width: 760px)').matches
        ? Math.max(0, Math.min(1, 1 - window.scrollY / (vh * 0.35)))
        : (r.top > 0 ? 1 : Math.max(0, 1 - (-r.top) / (vh * 0.45)));
      el.style.setProperty('--ss-in', inset.toFixed(3));
    };
    update();
    window.addEventListener('scroll', update, { passive: true });
    window.addEventListener('resize', update);
    return () => { window.removeEventListener('scroll', update); window.removeEventListener('resize', update); };
  }, [n]);

  useEffect(() => {
    const mq = window.matchMedia('(max-width: 760px)');
    const onChange = e => setMobile(e.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);
  useEffect(() => {
    const el = ref.current; if (!anim || !el) return undefined;
    const io = new IntersectionObserver(entries => entries.forEach(e => {
      if (!e.isIntersecting) return;
      setSeen(s => ({ ...s, [e.target.dataset.key]: true }));
      io.unobserve(e.target);
    }), { threshold: 0.15, rootMargin: '0px 0px -8% 0px' });
    el.querySelectorAll('.ss-scene:not(.ss-intro)').forEach(s => io.observe(s));
    return () => io.disconnect();
  }, [anim]);

  // 왼쪽 눈금을 누르면 그 장면으로 이동
  const go = i => { const el = ref.current; if (!el) return; const top = el.getBoundingClientRect().top + window.scrollY; window.scrollTo({ top: top + (i + 0.5) * ((el.offsetHeight - window.innerHeight) / n), behavior: 'smooth' }); };

  return <section className={`ss ${anim ? 'ss-anim' : ''}`} ref={ref} style={{ height: `${(n + 1) * 100}vh` }} aria-label="모아바 사용 흐름 소개">
    <div className="ss-sticky">
      <div className="ss-frame">
        {SCENES.map((s, i) => <div key={s.key} data-key={s.key} className={`ss-scene ${s.intro ? 'ss-intro' : ''} ${i === active ? 'on' : ''} ${i < active ? 'past' : ''} ${seen[s.key] ? 'seen' : ''}`} style={{ background: s.bg }} aria-hidden={!mobile && i !== active}>
          {s.media && (/\.(mp4|webm|mov)$/i.test(s.media)
            ? <video className="ss-media" src={s.media} autoPlay muted loop playsInline />
            : <img className="ss-media" src={s.media} alt="" />)}
          <div className="ss-glow" />
          <div className="ss-inner">
            <div className="ss-copy"><span className="ss-kicker">{s.kicker}</span><h2>{s.title}</h2>{s.lead && <p className="ss-lead">{s.lead}</p>}</div>
            {s.window !== false && <div className="ss-win"><Monitor page={s.page} /></div>}
          </div>
        </div>)}
        <div className={`ss-hint ${active === 0 ? 'on' : ''}`} aria-hidden="true">스크롤해서 둘러보기<span /></div>
        <nav className="ss-ticks" aria-label="장면 이동">
          {SCENES.map((s, i) => <button type="button" key={s.key} className={i === active ? 'on' : ''} onClick={() => go(i)} aria-label={`${i + 1}번째 장면 보기`} aria-current={i === active} />)}
        </nav>
      </div>
    </div>
  </section>;
}
