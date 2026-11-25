/* 여러 화면이 함께 쓰는 컴포넌트: BarChart · GroupCard · GroupInfo · InlineSimulator · RiskPanel · ProofAttachment · GroupSelect */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { finance, groups, joined, leader, memberName, activeJoined, db } from '../api';
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

export function GroupCard({ g, selected, onSelect, onSim }) {
  const nav = useNavigate(); const { setUi } = useApp();
  return <div className={`list-item group-choice ${selected ? 'selected' : ''}`} role="button" tabIndex={0} aria-pressed={!!selected} onClick={() => onSelect(g.id)} onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onSelect(g.id); } }}>
    <div className="item-head"><b>{g.title}</b><span className={`pill ${joined(g) ? 'mine' : ''}`}>{joined(g) ? (leader(g) ? '내 모임 · 모임장' : '참여 중') : g.status}</span></div>
    <p className="muted mini">{g.region} · {g.purpose} · 월 {won(g.amount)} · 매월 {g.payDay}일 · {g.members.length}/{g.max}명</p>
    <div className="group-card-actions"><button type="button" className="btn small primary" onClick={e => { e.stopPropagation(); onSelect(g.id); }}>상세 보기</button>{joined(g) && <button type="button" className="btn small soft" onClick={e => { e.stopPropagation(); setUi({ selected: g.id }); nav('/operations'); }}>운영 홈</button>}</div>
  </div>;
}

export function GroupInfo({ g, onSim }) {
  const nav = useNavigate(); const { setUi, run } = useApp();
  if (!g) return <div className="group-info-empty"><h3>모임을 선택해 주세요</h3><p>목록 카드 또는 지도 핀을 누르면 이곳에 모임 정보가 나타나요.</p></div>;
  const rows = [['지역', g.region], ['목적', g.purpose], ['월 납입액', won(g.amount)], ['납입일', `매월 ${g.payDay}일`], ['운영 기간', `${g.months}개월`], ['참여 인원', `${g.members.length} / ${g.max}명`], ['모임장', memberName(g.ownerId)], ['수령 순서', g.orderConfirmed ? '확정' : '협의 중'], ['가정 이율', `연 ${Number(g.rate || 0)}%`]];
  return <div className="group-info-full"><span className={`pill ${joined(g) ? 'mine' : ''}`}>{joined(g) ? (leader(g) ? '내 모임 · 모임장' : '참여 중 · ' + g.status) : g.status}</span><h2>{g.title}</h2><p>{g.description || '모임 소개가 등록되지 않았습니다.'}</p>
    <div className="group-info-rows">{rows.map(([k, v]) => <div key={k}><span>{k}</span><strong>{v}</strong></div>)}</div>
    <div className="info" style={{ marginTop: 14 }}><b>운영 규칙 · {g.rulesTitle || '운영 규칙'}</b><p>{g.rules || '등록된 규칙이 없습니다.'}</p></div>
    <div className="actions" style={{ marginTop: 16 }}>{joined(g) ? <button className="btn primary" onClick={() => { setUi({ selected: g.id }); nav('/operations'); }}>운영 홈 · 상세</button> : <button className="btn primary" onClick={() => run(() => groups.joinRequest(g.id), '모임장에게 가입 신청을 전송했어요.')}>가입 신청</button>}<button className="btn soft" onClick={() => onSim(g)}>시뮬레이션</button></div></div>;
}

export function InlineSimulator({ g, onClose }) {
  const [v, setV] = useState({ amount: g.amount, members: g.max, round: Math.max(1, Math.min(g.max, Math.ceil(g.max / 2))), rate: g.rate });
  const a = Number(v.amount), n = Number(v.members), r = Number(v.round), rate = Number(v.rate);
  const valid = Number.isFinite(a) && a > 0 && Number.isInteger(n) && n >= 2 && n <= 30 && Number.isInteger(r) && r >= 1 && r <= n && Number.isFinite(rate) && rate >= 0 && rate <= 20;
  const total = a * n, shift = finance.payoutShift(a, n, r, rate);
  const F = (label, key, props) => <div className="field"><label>{label}</label><input type="number" value={v[key]} onChange={e => setV({ ...v, [key]: e.target.value })} {...props} /></div>;
  return <div className="inline-sim"><p className="muted mini">{g.title} · 추천 단계와 금융 정보는 그대로 유지돼요.</p>
    <div className="inline-sim-fields">{F('월 납입액 (만 원)', 'amount', { min: 5, step: 5 })}{F('참여 인원', 'members', { min: 2, max: 30 })}{F('수령 회차', 'round', { min: 1, max: n })}{F('가정 연이율 (%)', 'rate', { min: 0, max: 20, step: 0.5 })}</div>
    {valid ? <div className="quick-results"><div><small>기본 수령액</small><strong>{krw(total)}</strong></div><div><small>{shift < 0 ? '차감액' : '가산액'}</small><strong>{signed(shift, krw)}</strong></div><div className="quick-total"><small>가정 수령액 · {r}회차</small><strong>{krw(total + shift)}</strong></div></div> : <p className="warning mini">금액, 인원(2~30명), 수령 회차, 이율(0~20%)을 확인해 주세요.</p>}
    <p className="muted mini">수령 순서에 따른 가상 보정 계산이며 실제 이자나 지급액이 아닙니다.</p><div className="actions"><button className="btn soft" onClick={onClose}>← 모임 상세로 돌아가기</button></div></div>;
}

/** 연체 위험 등급 패널. AI 분석 결과가 있을 때만 등급·추천액을 보여 주고, 없으면 분석 안내만 한다(가정 등급을 쓰지 않음). */
export function RiskPanel({ compact }) {
  const nav = useNavigate();
  const assessment = finance.getMlAssessment(), a = finance.affordability();
  const cap = Math.round(finance.getPolicy().incomeCapRatio * 100);   // 상한 적용 안내용(%) — ML 서버 값 기준
  return <div className="policy-panel" style={{ marginTop: 0 }}><h3>연체 위험 등급 {assessment && <span className="policy-tag">AI 분석</span>}</h3>
    {assessment ? <p className="muted mini">위험 등급 <b>{assessment.risk.risk_label}</b>{assessment.risk.safety_factor != null && <> · 안전계수 {assessment.risk.safety_factor}</>}</p>
      : <><p className="muted mini">아직 분석 결과가 없어요. 맞춤 추천에서 금융정보를 입력하고 이용에 동의하면 AI가 위험 등급과 추천 납입액을 계산해 드려요.</p><div className="actions"><button type="button" className="btn small soft" onClick={() => nav('/journey')}>맞춤 추천 시작 →</button></div></>}
    {!compact && assessment && a && <p className="info mini" style={{ marginTop: 12 }}>추천 월 납입액 <b>{a.rec > 0 ? won(a.rec) : '추천 없음'}</b>{a.rec <= 0 ? ` · ${finance.noRecommendReason(a)}` : ''}{a.capped && a.rec > 0 ? ` · 월수입 ${cap}% 상한 적용` : ''}</p>}</div>;
}
export const RiskLabel = () => { const assessment = finance.getMlAssessment(); return assessment ? <>위험 등급 <b>{assessment.risk.risk_label}</b></> : <>아직 위험 등급 분석 전이에요.</>; };

export function FinanceAssessmentStatus({ status, error, retry }) {
  const text = status === 'loading' ? 'AI가 금융정보를 분석하고 있어요.'
    : status === 'success' ? 'AI 금융 분석이 끝났어요.'
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
