/* S03 맞춤 추천 4단계 — /journey · F05~F08 F11 F14 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import useMlFinanceAssessment from '../common/useMlFinanceAssessment';
import { finance, groups } from '../api';
import { PageHead, Card, Input } from '../components/ui';
import { FinanceAssessmentStatus, GroupCard, GroupInfo, InlineSimulator, RiskPanel, RiskLabel } from '../components/shared';
import { won } from '../common/format';
import { useEffect } from 'react';

const STEPS = ['금융 정보 입력', '소비 분석', '납입액 추천', '모임 추천'];
const FIELDS = [['income', '월수입'], ['fixed', '고정지출'], ['variable', '변동지출'], ['saving', '기존 저축액'], ['reserve', '비상예비금'], ['goal', '희망 월 납입액']];

export default function Journey() {
  const { run, ui, setUi } = useApp(); const nav = useNavigate();
  const mlAssessment = useMlFinanceAssessment();
  const step = finance.getJourneyStep(), f0 = finance.getFinance(), a = finance.affordability();
  const [f, setF] = useState(() => Object.fromEntries(FIELDS.map(([k]) => [k, f0[k]])));
  const [consent, setConsent] = useState(!!f0.consentAt);
  const [sim, setSim] = useState(null);
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
  const goStep = n => { setSim(null); run(() => finance.setJourneyStep(n)).then(() => window.scrollTo(0, 0)); };
  let content;
  if (step === 1) content = <form noValidate onSubmit={e => { e.preventDefault(); if (consent) run(() => finance.saveFinance(f)); }}>
    <div className="form-grid">{FIELDS.map(([k, t]) => <Input key={k} label={`${t} (만 원)`} name={k} type="number" min={0} required value={f[k]} onChange={e => setF({ ...f, [k]: e.target.value })} />)}</div>
    {spendHint && <p className="muted mini" style={{ marginTop: 10 }}>비슷한 소득대 평균 소비지출 {won(spendHint.consumption)} · 비소비지출 {won(spendHint.nonconsumption)}</p>}
    <label className="checkline"><input type="checkbox" checked={consent} onChange={e => setConsent(e.target.checked)} /><span><b>금융정보 분석 및 추천 목적의 데이터 이용에 동의합니다.</b></span></label>
    <div className="actions"><button className="btn primary" disabled={!valid}>다음 단계 →</button></div></form>;
  if (step === 2) content = <><h2>입력 데이터 분석</h2><FinanceAssessmentStatus {...mlAssessment} /><div className="grid cols3">{[['월수입', won(f0.income)], ['월지출', won(f0.fixed + f0.variable)], ['가용 금액', won(a.free)]].map(([t, v]) => <Card key={t}><small>{t}</small><div className="metric">{v}</div></Card>)}</div><p className="info journey-analysis-note"><RiskLabel /><br />금액은 입력값에 따른 참고 분석이며 실제 금융 건전성을 보장하지 않습니다.</p><div className="actions"><button className="btn soft" onClick={() => goStep(1)}>← 이전</button><button className="btn primary" onClick={() => goStep(3)}>분석 확인 · 다음 →</button></div></>;
  if (step === 3) content = <><div className="hero"><h2>추천 월 납입액은 {won(a.rec)}입니다.</h2><p><RiskLabel /></p></div><FinanceAssessmentStatus {...mlAssessment} /><div className="section"><Card><RiskPanel /></Card></div><div className="actions" style={{ marginTop: 20 }}><button className="btn soft" onClick={() => goStep(2)}>← 이전</button><button className="btn primary" onClick={() => goStep(4)}>모임 추천 보기 →</button></div></>;
  if (step === 4) {
    const recs = groups.recommended(a.rec), picked = recs.find(g => g.id === ui.focus) || null;
    content = <><h2>나에게 어울리는 모임</h2><div className="journey-recommend-layout"><aside className="journey-recommend-info"><h3>{sim ? '간단 시뮬레이션' : '선택한 모임 정보'}</h3>{sim ? <InlineSimulator g={sim} onClose={() => setSim(null)} /> : <GroupInfo g={picked} onSim={setSim} />}</aside>
      <div className="journey-recommend-list">{recs.length ? recs.map(g => <GroupCard key={g.id} g={g} selected={ui.focus === g.id} onSelect={id => { setSim(null); setUi({ focus: id }); }} />) : <div className="empty">추천 납입액에 맞는 모집 중 모임이 없어요. 지도에서 더 찾아보세요.</div>}</div></div>
      <div className="actions" style={{ marginTop: 16 }}><button className="btn primary" onClick={() => nav('/groups')}>지역 지도에서 더 찾아보기</button><button className="btn soft" onClick={() => goStep(1)}>처음부터 다시</button></div></>;
  }
  return <><PageHead kicker="PERSONALIZED JOURNEY" title="맞춤 추천 시작" sub="입력 → 분석 → 납입액 추천 → 모임 추천 순서로 진행합니다." />
    <div className="steps">{STEPS.map((x, i) => <div key={x} className={`step ${i + 1 === step ? 'current' : i + 1 < step ? 'done' : ''}`}>{i + 1}. {x}</div>)}</div><Card>{content}</Card></>;
}
