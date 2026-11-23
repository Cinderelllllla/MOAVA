/* S03 맞춤 추천 4단계 — /journey · F05~F08 F11 F14 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import useMlFinanceAssessment from '../common/useMlFinanceAssessment';
import { finance } from '../api';
import { PageHead, Card, Input } from '../components/ui';
import { FinanceAssessmentStatus, RiskPanel, RiskLabel } from '../components/shared';
import { won } from '../common/format';
import { useEffect } from 'react';

const STEPS = ['금융 정보 입력', '소비 분석', '납입액 추천', '모임 추천'];
const FIELDS = [['income', '월수입'], ['fixed', '고정지출'], ['variable', '변동지출'], ['saving', '기존 저축액'], ['reserve', '비상예비금'], ['goal', '희망 월 납입액']];

export default function Journey() {
  const { run } = useApp(); const nav = useNavigate();
  const mlAssessment = useMlFinanceAssessment();
  const step = finance.getJourneyStep(), f0 = finance.getFinance(), a = finance.affordability();
  // 처음엔 빈칸. 이전에 이 기기에서 입력한 값이 있으면 그 값으로 채운다
  const [f, setF] = useState(() => Object.fromEntries(FIELDS.map(([k]) => [k, f0[k] ?? ''])));
  const [consent, setConsent] = useState(!!f0.consentAt);
  // F07 지출 기본값 제안: 월수입 입력이 0.6초 멈추면 비슷한 소득대 평균 지출을 조회(안내만, 입력값은 바꾸지 않음)
  const [spendHint, setSpendHint] = useState(null);
  useEffect(() => {
    if (step !== 1 || f.income === '') { setSpendHint(null); return; }
    let alive = true;
    const timer = setTimeout(() => {
      finance.getSpendingDefaults(f.income)
        .then(d => { if (alive) setSpendHint(d); })
        .catch(() => { if (alive) setSpendHint(null); });
    }, 600);
    return () => { alive = false; clearTimeout(timer); };
  }, [f.income, step]);
  const valid = consent && FIELDS.every(([k]) => f[k] !== '' && Number.isFinite(Number(f[k])) && Number(f[k]) >= 0);
  const goStep = n => { run(() => finance.setJourneyStep(n)).then(() => window.scrollTo(0, 0)); };
  const hasProfile = finance.hasFinanceProfile(f0);
  let content;
  if (step === 1) content = <form noValidate onSubmit={e => { e.preventDefault(); if (consent) run(() => finance.saveFinance(f)); }}>
    <div className="form-grid">{FIELDS.map(([k, t]) => <Input key={k} label={`${t} (만 원)`} name={k} type="number" min={0} required value={f[k]} onChange={e => setF({ ...f, [k]: e.target.value })} />)}</div>
    {spendHint && <p className="muted mini" style={{ marginTop: 10 }}>비슷한 소득대 평균 소비지출 {won(spendHint.consumption)} · 비소비지출 {won(spendHint.nonconsumption)}</p>}
    <label className="checkline"><input type="checkbox" checked={consent} onChange={e => setConsent(e.target.checked)} /><span><b>금융정보 분석 및 추천 목적의 데이터 이용에 동의합니다.</b></span></label>
    <p className="muted mini">입력한 금융정보는 AI 분석에만 쓰이고, 로그인해 있는 동안만 이 브라우저에 보관돼요. 로그아웃하면 지워져서 다시 로그인하면 새로 입력해야 해요.</p>
    <div className="actions"><button className="btn primary" disabled={!valid}>다음 단계 →</button></div></form>;
  if (step === 2) content = <><h2>입력 데이터 분석</h2><FinanceAssessmentStatus {...mlAssessment} /><div className="grid cols3">{[['월수입', hasProfile ? won(f0.income) : '-'], ['월지출', hasProfile ? won(Number(f0.fixed) + Number(f0.variable)) : '-'], ['매달 남는 돈', a ? finance.freeText(a.free) : '분석 전', '수입 − 지출 − 저축 − 비상금']].map(([t, v, hint]) => <Card key={t}><small>{t}</small><div className="metric">{v}</div>{hint && <small className="muted">{hint}</small>}</Card>)}</div>{a && a.free < 0 && <p className="warning mini" style={{ marginTop: 10 }}>{finance.noRecommendReason(a)} 지출이나 저축·비상금 금액을 줄이면 납입 여유가 생겨요.</p>}<p className="info journey-analysis-note"><RiskLabel /><br />금액은 입력값에 따른 참고 분석이며 실제 금융 건전성을 보장하지 않습니다.</p><div className="actions"><button className="btn soft" onClick={() => goStep(1)}>← 이전</button><button className="btn primary" onClick={() => goStep(3)}>분석 확인 · 다음 →</button></div></>;
  if (step === 3) content = <>{a ? (a.rec > 0
    ? <div className="hero"><h2>추천 월 납입액은 {won(a.rec)}입니다.</h2><p><RiskLabel /></p></div>
    : <div className="hero"><h2>지금은 계모임 납입을 추천하기 어려워요.</h2><p>{finance.noRecommendReason(a)}<br />지출이나 저축·비상금 금액을 조정한 뒤 다시 분석해 보세요.</p><p><RiskLabel /></p></div>) : <div className="empty">아직 AI 분석 결과가 없어 추천 납입액을 계산하지 못했어요. 아래에서 분석 상태를 확인해 주세요.</div>}<div style={{ marginTop: 14 }}><FinanceAssessmentStatus {...mlAssessment} /></div><div className="section"><Card><RiskPanel /></Card></div><div className="actions" style={{ marginTop: 20 }}><button className="btn soft" onClick={() => goStep(2)}>← 이전</button><button className="btn primary" onClick={() => goStep(4)}>모임 추천 보기 →</button></div></>;
  if (step === 4) {
    // TODO(MOA-69): 서버 API 연결 시 준비 중 안내 제거 — 추천 납입액에 맞는 모임 추천(groups.recommended 목업 참고)
    content = <><h2>나에게 어울리는 모임</h2>
      <div className="empty">맞춤 모임 추천은 아직 준비 중이에요. 지금은 직접 계모임을 만들어 시작할 수 있어요.</div>
      <div className="actions" style={{ marginTop: 16 }}><button className="btn primary" onClick={() => nav('/groups/new')}>계모임 만들기</button><button className="btn soft" onClick={() => nav('/simulator')}>시뮬레이터로 계산해 보기</button><button className="btn soft" onClick={() => goStep(1)}>처음부터 다시</button></div></>;
  }
  return <><PageHead kicker="PERSONALIZED JOURNEY" title="맞춤 추천 시작" sub="입력 → 분석 → 납입액 추천 → 모임 추천 순서로 진행합니다." />
    <div className="steps">{STEPS.map((x, i) => <div key={x} className={`step ${i + 1 === step ? 'current' : i + 1 < step ? 'done' : ''}`}>{i + 1}. {x}</div>)}</div><Card>{content}</Card></>;
}
