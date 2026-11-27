/* 여러 화면이 함께 쓰는 컴포넌트: BarChart · GroupCard · GroupInfo · InlineSimulator · RiskPanel · ProofAttachment · GroupSelect */
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { finance, groups, joined, leader, activeJoined, db } from '../api';
import { won, krw, signed } from '../common/format';
import { Empty } from './ui';
import Icon from './Icon';

export function BarChart({ data, limit }) {
  if (!data?.length) return <Empty>아직 월별 기록이 없어요. 월별 금융 데이터에서 수입·지출을 저장해 보세요.</Empty>;
  const a = limit ? data.slice(-limit) : data, max = Math.max(1, ...a.flatMap(x => [x.income, x.expense]));
  return <><div className="chart">{a.map(x => <div className="bar-pair" key={x.month}><div className="bar income" style={{ height: `${x.income / max * 100}%` }} title={`수입 ${x.income}만 원`} /><div className="bar expense" style={{ height: `${x.expense / max * 100}%` }} title={`지출 ${x.expense}만 원`} /></div>)}</div>
    <div className="chart-labels">{a.map(x => <span key={x.month}>{x.month}</span>)}</div>
    <p className="muted mini" style={{ textAlign: 'right', marginTop: 12 }}>● 수입(진한 색)　● 지출(연한 색) · 단위: 만 원{limit ? ` · 최근 ${a.length}개월` : ''}</p></>;
}

/** 서버 목록 공통 상태 표시: 불러오는 중 / 오류 + 다시 시도. 정상이면 children 을 그린다. */
export function LoadState({ status, error, onRetry, loadingText = '불러오는 중이에요.', children }) {
  if (status === 'loading') return <div className="empty" role="status">{loadingText}</div>;
  if (status === 'error') return <div className="empty warning" role="alert">{error || '불러오지 못했어요.'}{onRetry && <>{' '}<button type="button" className="btn small soft" onClick={onRetry}>다시 시도</button></>}</div>;
  return children;
}

/** 월별 수입·지출 차트 (서버 값만). 불러오는 중·실패·기록 없음 상태를 함께 보여 준다. */
export function MonthlyChart({ limit }) {
  const { status, items, error } = finance.getMonthlyState();
  return <LoadState status={status} error={error} onRetry={() => finance.refreshMonthly().catch(() => {})} loadingText="월별 기록을 불러오는 중이에요.">
    <BarChart data={items} limit={limit} />
  </LoadState>;
}

function groupParticipation(g) {
  if (g?.role === 'OWNER') return { isJoined: true, isLeader: true };
  if (g?.role === 'MEMBER') return { isJoined: true, isLeader: false };
  if (g?.role === 'NONE') return { isJoined: false, isLeader: false };
  return { isJoined: joined(g), isLeader: leader(g) };
}

function groupMemberCounts(g) {
  const current = Number.isFinite(Number(g?.currentMembers))
    ? Number(g.currentMembers)
    : (g?.members?.length ?? 0);
  const max = Number(g?.max ?? g?.maxMembers ?? 0);
  return { current, max };
}

export function GroupCard({ g, selected, onSelect, onSim }) {
  const nav = useNavigate(); const { setUi } = useApp();
  const { isJoined, isLeader } = groupParticipation(g);
  const { current, max } = groupMemberCounts(g);
  const targetId = g.groupId ?? g.id;
  return <div className={`list-item group-choice ${selected ? 'selected' : ''}`} role="button" tabIndex={0} aria-pressed={!!selected} onClick={() => onSelect(g.id)} onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onSelect(g.id); } }}>
    <div className="item-head"><b>{g.title}</b><span className={`pill ${isJoined ? 'mine' : ''}`}>{isJoined ? (isLeader ? '내 모임 · 모임장' : '참여 중') : g.status}</span></div>
    <p className="muted mini">{g.region} · {g.purpose} · 월 {won(g.amount)} · 매월 {g.payDay}일 · {current}/{max}명{g.healthScore != null ? ` · 건강도 ${g.healthScore}점` : ''}</p>
    <div className="group-card-actions"><button type="button" className="btn small primary" onClick={e => { e.stopPropagation(); onSelect(g.id); }}>상세 보기</button>{isJoined && <button type="button" className="btn small soft" onClick={e => { e.stopPropagation(); setUi({ selected: targetId }); nav(`/operations?groupId=${encodeURIComponent(targetId)}`); }}>운영 홈</button>}</div>
  </div>;
}

export function GroupInfo({ g, onSim }) {
  const nav = useNavigate(); const { setUi, run } = useApp();
  const [joining, setJoining] = useState(false);
  if (!g) return <div className="group-info-empty"><h3>모임을 선택해 주세요</h3><p>목록 카드 또는 지도 핀을 누르면 이곳에 모임 정보가 나타나요.</p></div>;
  const { isJoined, isLeader } = groupParticipation(g);
  const { current, max } = groupMemberCounts(g);
  const targetId = g.groupId ?? g.id;
  const isServerGroup = g.source === 'SERVER' || g.role != null;
  const rulesText = g.rules ?? g.rulesBody ?? '';
  const canJoin = isServerGroup
    ? g.statusCode === 'RECRUITING' && (max <= 0 || current < max)
    : true;

  const handleJoin = async () => {
    if (joining) return;
    setJoining(true);
    try {
      await run(
        () => (isServerGroup ? groups.requestJoinGroup(targetId, 'SEARCH') : groups.joinRequest(targetId)),
        '모임장에게 가입 신청을 전송했어요.'
      );
    } finally {
      setJoining(false);
    }
  };

  const rows = [
    ['지역', g.region],
    ['목적', g.purpose],
    ['월 납입액', won(g.amount)],
    ['납입일', `매월 ${g.payDay}일`],
    ['운영 기간', `${g.months}개월`],
    ['참여 인원', `${current} / ${max}명`],
    ['가정 이율', `연 ${Number(g.rate || 0)}%`],
    ...(g.healthScore != null ? [['모임 건강도', `${g.healthScore}점`]] : []),
  ];
  return <div className="group-info-full"><span className={`pill ${isJoined ? 'mine' : ''}`}>{isJoined ? (isLeader ? '내 모임 · 모임장' : '참여 중 · ' + g.status) : g.status}</span><h2>{g.title}</h2><p>{g.description || '모임 소개가 등록되지 않았습니다.'}</p>
    <div className="group-info-rows">{rows.map(([k, v]) => <div key={k}><span>{k}</span><strong>{v}</strong></div>)}</div>
    <div className="info" style={{ marginTop: 14 }}><b>운영 규칙 · {g.rulesTitle || '운영 규칙'}</b><p>{rulesText || '등록된 규칙이 없습니다.'}</p></div>
    <div className="actions" style={{ marginTop: 16 }}>{isJoined
      ? <button type="button" className="btn primary" onClick={() => { setUi({ selected: targetId }); nav(`/operations?groupId=${encodeURIComponent(targetId)}`); }}>운영 홈</button>
      : <button type="button" className="btn primary" disabled={joining || !canJoin} onClick={handleJoin}>{!canJoin ? '모집 마감' : joining ? '신청 중…' : '가입 신청'}</button>
    }<button type="button" className="btn soft" onClick={() => onSim(g)}>시뮬레이션</button></div></div>;
}

export function InlineSimulator({ g, onClose }) {
  const [v, setV] = useState({ amount: g.amount, members: g.max, round: Math.max(1, Math.min(g.max, Math.ceil(g.max / 2))), rate: g.rate });
  const [saving, setSaving] = useState(null);
  const a = Number(v.amount), n = Number(v.members), r = Number(v.round), rate = Number(v.rate);
  const hasRate = String(v.rate ?? '').trim() !== '';
  const valid =
    Number.isInteger(a) && a >= 5 && a % 5 === 0 &&
    Number.isInteger(n) && n >= 2 && n <= 30 &&
    Number.isInteger(r) && r >= 1 && r <= n &&
    hasRate && Number.isFinite(rate) && rate >= 0 && rate <= 20;

  useEffect(() => {
    if (!Number.isInteger(n) || n < 2 || n > 30) {
      setSaving(null);
      return undefined;
    }
    let alive = true;
    finance.getSavingComparison(n)
      .then((res) => { if (alive) setSaving(res); })
      .catch(() => { if (alive) setSaving(null); });
    return () => { alive = false; };
  }, [n]);

  const total = a * n, shift = finance.payoutShift(a, n, r, rate);
  const bankTop = saving?.available && saving.topMaxRate != null
    ? finance.savingMaturity(a, n, saving.topMaxRate)
    : null;
  const F = (label, key, props) => <div className="field"><label>{label}</label><input type="number" value={v[key]} onChange={e => setV({ ...v, [key]: e.target.value })} {...props} /></div>;
  return <div className="inline-sim"><p className="muted mini">{g.title} · 추천 단계와 금융 정보는 그대로 유지돼요.</p>
    <div className="inline-sim-fields">{F('월 납입액 (만 원)', 'amount', { min: 5, step: 5 })}{F('참여 인원', 'members', { min: 2, max: 30 })}{F('수령 회차', 'round', { min: 1, max: n })}{F('가정 연이율 (%)', 'rate', { min: 0, max: 20, step: 0.5 })}</div>
    {valid ? <>
      <div className="quick-results"><div><small>기본 수령액</small><strong>{krw(total)}</strong></div><div><small>{shift < 0 ? '차감액' : '가산액'}</small><strong>{signed(shift, krw)}</strong></div><div className="quick-total"><small>가정 수령액 · {r}회차</small><strong>{krw(total + shift)}</strong></div></div>
      {bankTop && (
        <p className="info mini" style={{ marginTop: 10 }}>
          시중 {saving.matchedTermMonths ?? n}개월 적금 비교: 평균 기본 <b>{saving.avgBasicRate?.toFixed(2)}%</b> · 최고 우대 <b>{saving.topMaxRate?.toFixed(2)}%</b> (동일 납입 만기 예상 <b>{krw(bankTop.maturity)}</b>, 세전)
        </p>
      )}
    </> : <p className="warning mini">월 납입액은 5만 원 단위, 인원 2~30명, 회차는 인원 이내, 이율 0~20%로 입력하세요.</p>}
    <p className="muted mini">수령 순서에 따른 가상 보정 계산이며 실제 이자나 지급액이 아닙니다.</p><div className="actions"><button type="button" className="btn soft" onClick={onClose}>← 모임 상세로 돌아가기</button></div></div>;
}

/** 연체 위험 등급 패널. AI 분석 결과가 있을 때만 등급·추천액을 보여 주고, 없으면 분석 안내만 한다(가정 등급을 쓰지 않음). */
export function RiskPanel({ compact }) {
  const nav = useNavigate();
  const assessment = finance.getMlAssessment(), a = finance.affordability();
  const cap = Math.round(finance.getPolicy().incomeCapRatio * 100);   // 상한 적용 안내용(%) — ML 서버 값 기준
  return <div className="policy-panel" style={{ marginTop: 0 }}><h3>연체 위험 등급 {assessment && <span className="policy-tag">AI 분석</span>}</h3>
    {assessment ? <><p className="muted mini">위험 등급 <RiskBadge risk={assessment.risk} />{assessment.risk.safety_factor != null && <> · 안전계수 {assessment.risk.safety_factor}</>}</p>
      {!assessment.fallback && <p className="muted mini">입력하신 신용 답변 기준 · 교육용 모델 (실제 신용평가가 아니에요)</p>}</>
      : <><p className="muted mini">아직 분석 결과가 없어요. 맞춤 추천에서 금융정보를 입력하고 이용에 동의하면 AI가 위험 등급과 추천 납입액을 계산해 드려요.</p><div className="actions"><button type="button" className="btn small soft" onClick={() => nav('/journey')}>맞춤 추천 시작 →</button></div></>}
    {!compact && assessment && a && <p className="info mini" style={{ marginTop: 12 }}>추천 월 납입액 <b>{a.rec > 0 ? won(a.rec) : '추천 없음'}</b>{a.rec <= 0 ? ` · ${finance.noRecommendReason(a)}` : ''}{a.capped && a.rec > 0 ? ` · 월수입 ${cap}% 상한 적용` : ''}</p>}
    {!compact && assessment && a && a.free > 0 && <RecommendCalc free={a.free} k={assessment.risk.safety_factor} rec={a.rec} />}</div>;
}

const manwon1 = v => `${Math.round(v * 10) / 10}만 원`;   // 소수 첫째 자리까지 (예: 47.3만 원)
/** 추천 납입액 계산 과정: min(남는 돈 × 안전계수, 월수입 × 상한) → 절사 단위로 내림 */
function RecommendCalc({ free, k, rec }) {
  const income = Number(finance.getFinance().income), policy = finance.getPolicy();
  const factor = Number(k);
  if (!Number.isFinite(income) || !Number.isFinite(factor)) return null;
  const byRisk = free * factor, cap = income * policy.incomeCapRatio;
  return <ol className="muted mini" style={{ margin: '10px 0 0', paddingLeft: 18, lineHeight: 1.8 }}>
    <li>매달 남는 돈 {manwon1(free)} × 안전계수 {factor} = <b>{manwon1(byRisk)}</b></li>
    <li>월수입 {manwon1(income)}의 {Math.round(policy.incomeCapRatio * 100)}% 상한 = <b>{manwon1(cap)}</b></li>
    <li>둘 중 작은 값 {manwon1(Math.min(byRisk, cap))}을 {policy.amountUnitManwon}만 원 단위로 내림 → <b>{won(rec)}</b></li>
  </ol>;
}
/* F09 위험 등급 배지 — 등급 코드(STABLE/CAUTION/RISK) 기준으로 색을 정한다. 라벨 글자로 비교하지 않는다.
   코드가 오는 곳: ML 실시간 응답 risk.risk_level / 저장된 결과(GET /finance/profile) assessment.riskLevel(= RISK_SCORE.TIER)
   → finance.js 가 둘 다 risk.risk_level 로 맞춰 저장한다. 혹시 다른 이름(riskLevel·tier)으로 와도 읽는다.
   색약 사용자를 위해 색과 함께 글자(안정/주의/위험)를 항상 보여 준다. */
const RISK_TIERS = { STABLE: { label: '안정', cls: 'stable' }, CAUTION: { label: '주의', cls: 'caution' }, RISK: { label: '위험', cls: 'risk' } };
/** risk 객체 → { code, label, cls }. 알 수 없는 코드면 cls 'unknown'(회색) + 받은 라벨 그대로 */
export function riskTierOf(risk) {
  const code = String(risk?.risk_level ?? risk?.riskLevel ?? risk?.tier ?? '').trim().toUpperCase();
  const known = RISK_TIERS[code];
  return known ? { code, ...known } : { code, label: risk?.risk_label ?? risk?.riskLabel ?? '알 수 없음', cls: 'unknown' };
}
export function RiskBadge({ risk }) {
  const t = riskTierOf(risk);
  return <span className={`risk-badge ${t.cls}`}>{t.label}</span>;
}
/* MOA-101 위험 등급 분석 근거. 문항 key → 짧은 문구 (질문 + 고른 답) */
const FACTOR_PHRASE = {
  late_count_12m: a => `최근 1년 30일 이상 연체 ${a}`,
  max_late_period: a => `최근 6개월 최장 연체 ${a}`,
  loan_count: a => `상환 중인 대출 ${a}`,
  card_usage: a => `카드 한도 사용 ${a}`,
};
const factorPhrase = f => (FACTOR_PHRASE[f?.key] ? FACTOR_PHRASE[f.key](f.answer_label) : `${f?.question || ''} ${f?.answer_label || ''}`.trim());
/** 위험 등급이 왜 나왔는지 한 문장. 요인 정보가 없으면(대체값·예전 결과·저장된 결과) 그리지 않는다 */
export function RiskExplanation() {
  const assessment = finance.getMlAssessment();
  const factors = assessment?.risk?.factors;
  if (!assessment || assessment.fallback || !Array.isArray(factors)) return null;
  const label = riskTierOf(assessment.risk).label;
  const text = factors.length
    ? `${factors.map(f => `'${factorPhrase(f)}'`).join(', ')} 요인으로 위험 등급이 '${label}'(으)로 분석되었어요.`
    : `연체·대출·카드 사용 응답에서 위험을 크게 높이는 요인이 없어 '${label}'(으)로 분석되었어요.`;
  return <>
    <span style={{ display: 'block', marginTop: 6 }}>{text}</span>
    <small style={{ display: 'block', marginTop: 4, opacity: 0.85 }}>AI 모델이 신용 질문 응답을 바탕으로 계산한 참고 설명이에요. 실제 신용평가와 다를 수 있어요.</small>
  </>;
}
export const RiskLabel = () => { const assessment = finance.getMlAssessment(); return assessment ? <>위험 등급 <RiskBadge risk={assessment.risk} /></> : <>아직 위험 등급 분석 전이에요.</>; };

const FALLBACK_TEXT = {
  PREVIOUS: 'AI 분석 서버에 연결하지 못해 최근 위험 등급으로 계산했어요. 잠시 후 다시 분석하면 최신 결과로 바뀌어요.',
  DEFAULT: "AI 분석 서버에 연결하지 못해 기본 '주의' 등급으로 계산했어요. 잠시 후 다시 분석하면 최신 결과로 바뀌어요.",
};
/** MOA-95 ML 장애 대체값 안내 (오류가 아니라 안내 색). 대체값이 아니면 아무것도 그리지 않는다 */
export function FallbackNotice() {
  const nav = useNavigate();
  const assessment = finance.getMlAssessment();
  if (!assessment?.fallback) return null;
  const hasAnswers = Boolean(finance.getSelfReport());
  // 신용 질문 답이 이 탭에 남아 있으면 바로 다시 분석, 없으면(새로고침 등) 1단계에서 다시 답하게 한다
  const reanalyze = () => {
    if (hasAnswers) { finance.clearMlAssessment(); return; }
    finance.setJourneyStep(1).then(() => nav('/journey')).catch(() => nav('/journey'));
  };
  return <p className="info mini" role="status" style={{ marginBottom: 12 }}>
    {FALLBACK_TEXT[assessment.fallbackSource] || FALLBACK_TEXT.DEFAULT}{' '}
    <button type="button" className="btn small soft" onClick={reanalyze}>{hasAnswers ? '다시 분석' : '1단계에서 다시 분석'}</button>
  </p>;
}

export function FinanceAssessmentStatus({ status, error, retry }) {
  const text = status === 'loading' ? 'AI가 금융정보를 분석하고 있어요.'
    : status === 'success' ? 'AI 금융 분석이 끝났어요.'
      : status === 'needs-answers' ? '맞춤 추천 1단계에서 신용 관련 질문 4개에 답하면 AI 분석을 다시 할 수 있어요.'
      : status === 'error' ? `금융 분석을 하지 못했어요. ${error}`
        : '금융정보를 입력하고 이용에 동의하면 AI 분석을 시작해요.';
  return <p className={`info mini finance-api-status ${status === 'error' ? 'warning' : ''}`} role={status === 'error' ? 'alert' : 'status'}>
    {text}{status === 'error' && <>{' '}<button type="button" className="btn small soft" onClick={retry}>다시 시도</button></>}
  </p>;
}

export const ProofAttachment = ({ data }) => String(data || '').startsWith('data:application/pdf') ? <a className="pdf-proof-link" href={data} target="_blank" rel="noopener noreferrer" download="MOAVA_거래증빙.pdf"><Icon name="file" size={16} />PDF 증빙 보기 · 저장</a> : <img className="upload-preview" src={data} alt="거래 증빙 이미지" />;

/** 운영 대상 모임: ui.selected가 참여 중 모임이면 그것, 아니면 첫 참여 모임 */
export function useOpGroup() { const { ui, setUi } = useApp(); const g = groups.getGroup(ui.selected); const op = g && activeJoined(g) ? g : db.groups.find(activeJoined) || null; return [op, id => setUi({ selected: Number(id) })]; }
export function GroupSelect({ value, onChange }) {
  return <div className="field" style={{ maxWidth: 420 }}><label>운영할 계모임</label><select value={value || ''} onChange={e => onChange(e.target.value)}>{groups.myGroups().map(g => <option key={g.id} value={g.id}>{g.title}{leader(g) ? ' · 모임장' : ''}</option>)}</select></div>;
}
