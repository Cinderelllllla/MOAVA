/* S03 맞춤 추천 4단계 — /journey · F05~F08 F11 F14 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import useMlFinanceAssessment from '../common/useMlFinanceAssessment';
import { finance, groups } from '../api';
import { PageHead, Card, Input, Select } from '../components/ui';
import {
  FallbackNotice,
  FinanceAssessmentStatus,
  GroupCard,
  GroupInfo,
  InlineSimulator,
  LoadState,
  RiskLabel,
  RiskPanel,
} from '../components/shared';
import { won } from '../common/format';

const STEPS = ['금융 정보 입력', '소비 분석', '납입액 추천', '모임 추천'];
const FIELDS = [['income', '월수입'], ['fixed', '고정지출'], ['variable', '변동지출'], ['saving', '월 저축액'], ['reserve', '비상예비금'], ['goal', '희망 월 납입액']];
/* F06 KOSIS 평균 지출 비교 (MOA-94) */
const manwonText = n => `${Math.round(n).toLocaleString('ko-KR')}만 원`;
/** KOSIS 기준 시점 "YYYYMM" → 사람이 읽는 형태. 03·06·09 는 분기, 12 는 연도(수집기가 연간 자료를 YYYY12 로 저장), 그 밖은 "YYYY년 M월 기준" */
export function kosisPeriodText(baseYm) {
  const m = /^(\d{4})(\d{2})$/.exec(String(baseYm || ''));
  if (!m) return '';
  const year = m[1], month = Number(m[2]);
  if (month === 12) return `${year}년`;
  if (month === 3 || month === 6 || month === 9) return `${year}년 ${month / 3}분기`;
  if (month >= 1 && month <= 11) return `${year}년 ${month}월 기준`;
  return '';
}
/** 내 월지출과 같은 소득 분위 평균 가계지출 비교 한 줄. 비교할 수 없으면 아무것도 그리지 않는다(오류 문구 없음) */
function SpendingCompare({ hint, myExpense }) {
  const avg = Number(hint?.total), mine = Number(myExpense);
  if (!hint || !Number.isFinite(avg) || avg <= 0 || !Number.isFinite(mine) || mine <= 0) return null;
  const diff = mine - avg;
  const base = `비슷한 소득대(${hint.decile}분위) 평균 지출 ${manwonText(avg)}`;
  const text = Math.abs(diff) <= avg * 0.05 ? `${base}과 비슷해요.`
    : diff < 0 ? `${base}보다 ${manwonText(-diff)} 적게 쓰고 있어요.`
      : `${base}보다 ${manwonText(diff)} 더 쓰고 있어요.`;
  const period = kosisPeriodText(hint.baseYm);
  return <p className="info mini kosis-compare" style={{ marginTop: 10 }}>
    {text}
    <small className="muted" style={{ display: 'block', marginTop: 4 }}>통계청 KOSIS 가계동향조사{period ? ` ${period}` : ''}</small>
  </p>;
}

const emptyAnswers = () => Object.fromEntries(finance.SELF_REPORT_QUESTIONS.map(q => [q.key, '']));

export default function Journey() {
  const { run } = useApp(); const nav = useNavigate();
  const mlAssessment = useMlFinanceAssessment();
  const step = finance.getJourneyStep(), f0 = finance.getFinance(), a = finance.affordability();
  const recAmountManwon = a && Number.isFinite(Number(a.rec)) ? Math.max(0, Number(a.rec)) : 0;

  // 처음엔 빈칸. 서버에 저장된 금융 프로필이 있으면 자동으로 채운다
  const [f, setF] = useState(() => Object.fromEntries(FIELDS.map(([k]) => [k, f0[k] ?? ''])));
  const [consent, setConsent] = useState(!!f0.consentAt);
  // F09 자가 응답 4문항 — 저장하지 않으므로 이 탭에서 답한 값만 다시 채운다
  const [answers, setAnswers] = useState(() => ({ ...emptyAnswers(), ...(finance.getSelfReport() || {}) }));
  const syncedServerKeyRef = useRef(f0.serverSyncedAt || '');
  // F07 지출 기본값 제안: 월수입 입력이 0.6초 멈추면 비슷한 소득대 평균 지출을 조회(안내만, 입력값은 바꾸지 않음)
  const [spendHint, setSpendHint] = useState(null);

  useEffect(() => {
    const syncKey = f0.serverSyncedAt || '';
    if (!syncKey || syncKey === syncedServerKeyRef.current) return;
    syncedServerKeyRef.current = syncKey;
    setF(Object.fromEntries(FIELDS.map(([k]) => [k, f0[k] ?? ''])));
    setConsent(!!f0.consentAt);
  }, [f0.serverSyncedAt, f0.income, f0.fixed, f0.variable, f0.saving, f0.reserve, f0.goal, f0.consentAt]);

  // F11 ④단계 모임 추천 상태
  const [recState, setRecState] = useState({ status: 'idle', data: null, error: '' });
  const [recReloadTick, setRecReloadTick] = useState(0);
  const [selectedId, setSelectedId] = useState(null);
  const [sim, setSim] = useState(null);

  // F07 안내(1단계)·F06 평균 지출 비교(2단계)에 쓰는 같은 소득 분위 평균 지출.
  // 1단계는 입력 중인 월수입(0.6초 멈추면), 2단계는 저장된 월수입으로 조회한다.
  // 결과는 어떤 월수입 것인지 함께 기억하고, 같은 월수입은 finance.getSpendingDefaults 가 한 번만 서버에 묻는다.
  const hintIncome = step === 1 ? f.income : step === 2 ? f0.income : null;
  useEffect(() => {
    const v = Number(hintIncome);
    if (hintIncome === '' || hintIncome == null || !Number.isFinite(v) || v <= 0) return undefined;
    let alive = true;
    const timer = setTimeout(() => {
      finance.getSpendingDefaults(v)
        .then(d => { if (alive) setSpendHint({ income: v, data: d }); })
        .catch(() => { if (alive) setSpendHint({ income: v, data: null }); });
    }, step === 1 ? 600 : 0);
    return () => { alive = false; clearTimeout(timer); };
  }, [hintIncome, step]);
  /** 지금 화면의 월수입에 맞는 평균 지출 (다른 월수입 것이거나 아직 없으면 null) */
  const hintFor = income => (spendHint && spendHint.income === Number(income) ? spendHint.data : null);
  const stepHint = hintFor(hintIncome);

  useEffect(() => {
    if (step !== 4) return undefined;
    let cancelled = false;
    setRecState((prev) => ({ status: 'loading', data: prev.data, error: '' }));
    setSim(null);

    groups
      .fetchRecommendedGroups({ amountManwon: recAmountManwon })
      .then((data) => {
        if (cancelled) return;
        setRecState({ status: 'ready', data, error: '' });
        setSelectedId((prev) => {
          if (prev != null && data.items.some((g) => g.id === prev)) return prev;
          return data.items[0]?.id ?? null;
        });
      })
      .catch((err) => {
        if (cancelled) return;
        setRecState({
          status: 'error',
          data: null,
          error: err?.message || '추천 모임을 불러오지 못했어요.',
        });
      });

    return () => {
      cancelled = true;
    };
  }, [step, recAmountManwon, recReloadTick]);

  const reloadRecommendations = useCallback(() => {
    setRecReloadTick((n) => n + 1);
  }, []);

  const recItems = recState.data?.items || [];
  const chosen = useMemo(() => {
    if (!recItems.length) return null;
    return recItems.find((g) => g.id === selectedId) || recItems[0];
  }, [recItems, selectedId]);

  const valid = consent && finance.isSelfReportComplete(answers) && FIELDS.every(([k]) => f[k] !== '' && Number.isFinite(Number(f[k])) && Number(f[k]) >= 0);
  const goStep = n => { run(() => finance.setJourneyStep(n)).then(() => window.scrollTo(0, 0)); };
  const hasProfile = finance.hasFinanceProfile(f0);
  let content;
  if (step === 1) content = <form noValidate onSubmit={e => { e.preventDefault(); if (valid) run(async () => { finance.setSelfReport(answers); await finance.saveFinance(f); }); }}>
    <div className="form-grid">{FIELDS.map(([k, t]) => <Input key={k} label={`${t} (만 원)`} name={k} type="number" min={0} required value={f[k]} onChange={e => setF({ ...f, [k]: e.target.value })} />)}</div>
    {step === 1 && stepHint && <p className="muted mini" style={{ marginTop: 10 }}>비슷한 소득대 평균 소비지출 {won(stepHint.consumption)} · 비소비지출 {won(stepHint.nonconsumption)}</p>}
    <h3 style={{ marginTop: 22 }}>신용 관련 질문</h3>
    <p className="muted mini">위험 등급을 계산하는 데만 쓰고 저장하지 않아요. 다시 분석할 때는 다시 답해 주세요.</p>
    <div className="form-grid">{finance.SELF_REPORT_QUESTIONS.map(q => <Select key={q.key} label={q.label} name={q.key} required value={answers[q.key]} onChange={e => setAnswers({ ...answers, [q.key]: e.target.value })} options={[['', '선택해 주세요'], ...q.options]} />)}</div>
    <label className="checkline"><input type="checkbox" checked={consent} onChange={e => setConsent(e.target.checked)} /><span><b>금융정보 분석 및 추천 목적의 데이터 이용에 동의합니다.</b></span></label>
    <p className="muted mini">입력한 금융정보와 AI 분석 결과는 계정에 안전하게 저장되어 다시 로그인해도 유지돼요.</p>
    <div className="actions"><button className="btn primary" disabled={!valid}>다음 단계 →</button></div></form>;
  if (step === 2) content = <><h2>입력 데이터 분석</h2><FinanceAssessmentStatus {...mlAssessment} /><div className="grid cols3">{[['월수입', hasProfile ? won(f0.income) : '-'], ['월지출', hasProfile ? won(Number(f0.fixed) + Number(f0.variable)) : '-'], ['매달 남는 돈', a ? finance.freeText(a.free) : '분석 전', '수입 − 지출 − 저축 − 비상금']].map(([t, v, hint]) => <Card key={t}><small>{t}</small><div className="metric">{v}</div>{hint && <small className="muted">{hint}</small>}</Card>)}</div>{hasProfile && <SpendingCompare hint={stepHint} myExpense={Number(f0.fixed) + Number(f0.variable)} />}{a && a.free < 0 && <p className="warning mini" style={{ marginTop: 10 }}>{finance.noRecommendReason(a)} 지출이나 저축·비상금 금액을 줄이면 납입 여유가 생겨요.</p>}<p className="info journey-analysis-note">금액은 입력값에 따른 참고 분석이며 실제 금융 건전성을 보장하지 않습니다.</p><div className="actions"><button className="btn soft" onClick={() => goStep(1)}>← 이전</button><button className="btn primary" onClick={() => goStep(3)}>분석 확인 · 다음 →</button></div></>;
  if (step === 3) content = <><FallbackNotice />{a ? (a.rec > 0
    ? <div className="hero"><h2>추천 월 납입액은 {won(a.rec)}입니다.</h2><p><RiskLabel /></p></div>
    : <div className="hero"><h2>지금은 계모임 납입을 추천하기 어려워요.</h2><p>{finance.noRecommendReason(a)}<br />지출이나 저축·비상금 금액을 조정한 뒤 다시 분석해 보세요.</p><p><RiskLabel /></p></div>) : <div className="empty">아직 AI 분석 결과가 없어 추천 납입액을 계산하지 못했어요. 아래에서 분석 상태를 확인해 주세요.</div>}<div style={{ marginTop: 14 }}><FinanceAssessmentStatus {...mlAssessment} /></div><div className="section"><Card><RiskPanel /></Card></div><div className="actions" style={{ marginTop: 20 }}><button className="btn soft" onClick={() => goStep(2)}>← 이전</button><button className="btn primary" onClick={() => goStep(4)}>모임 추천 보기 →</button></div></>;
  if (step === 4) {
    const recMeta = recState.data;
    const regionSummary = recMeta?.regionLabel || '활동 지역 미설정';
    const maxCapManwon = recMeta ? recMeta.maxAmount : Math.max(recAmountManwon + 10, 20);

    content = <>
      <h2>나에게 어울리는 모임</h2>
      <p className="muted mini">
        추천 월 납입액 <b>{recAmountManwon > 0 ? won(recAmountManwon) : '기본 기준'}</b> (상한 {won(maxCapManwon)} 이하) · 활동 지역 <b>{regionSummary}</b> 기준으로 모집 중인 공개 모임을 최대 3개 추천해 드려요.
      </p>
      {recState.status === 'ready' && recMeta?.expandedToSido && recItems.length > 0 && (
        <p className="info mini" style={{ marginTop: 10 }}>
          동일 시/군/구({recMeta.regionSigungu})에 조건에 맞는 모집 중 모임이 없어 <b>{recMeta.regionSido}</b> 전체로 범위를 넓혀 추천했어요.
        </p>
      )}
      <div style={{ marginTop: 14 }}>
        <LoadState
          status={recState.status === 'idle' ? 'loading' : recState.status}
          error={recState.error}
          onRetry={reloadRecommendations}
          loadingText="추천 납입액과 활동 지역에 맞는 모임을 찾고 있어요."
        >
          {recItems.length > 0 ? (
            <div className="journey-recommend-layout">
              <div className="journey-recommend-info">
                <h3>{sim ? '간단 시뮬레이션' : '선택한 모임 정보'}</h3>
                {sim ? (
                  <InlineSimulator g={sim} onClose={() => setSim(null)} />
                ) : (
                  <GroupInfo g={chosen} onSim={setSim} />
                )}
              </div>
              <div className="journey-recommend-list">
                <p className="muted mini" style={{ marginBottom: 10 }}>
                  추천 모임 {recItems.length}건 · 추천액 차이가 작고 건강도가 높은 순으로 정렬했어요.
                </p>
                {recItems.map((g) => (
                  <GroupCard
                    key={g.id}
                    g={g}
                    selected={chosen?.id === g.id}
                    onSelect={(id) => {
                      setSim(null);
                      setSelectedId(id);
                    }}
                  />
                ))}
              </div>
            </div>
          ) : (
            <div className="empty">
              <p>
                {recMeta?.regionSido
                  ? `${recMeta.regionSido}${recMeta.regionSigungu ? ` ${recMeta.regionSigungu}` : ''} 지역(시/도 확대 포함)에서 월 ${won(maxCapManwon)} 이하로 모집 중인 공개 모임을 찾지 못했어요.`
                  : `월 ${won(maxCapManwon)} 이하로 모집 중인 공개 모임을 찾지 못했어요.`}
              </p>
              <p className="muted mini" style={{ marginTop: 6 }}>
                원하는 조건으로 직접 계모임을 만들거나 지역 지도에서 다른 지역 모임을 둘러보세요.
              </p>
              <div className="actions" style={{ justifyContent: 'center', marginTop: 14 }}>
                <button type="button" className="btn primary" onClick={() => nav('/groups/new')}>
                  새 모임 만들기
                </button>
              </div>
            </div>
          )}
        </LoadState>
      </div>
      <div className="actions" style={{ marginTop: 20 }}>
        <button type="button" className="btn primary" onClick={() => nav('/groups')}>
          지역 지도에서 더 찾아보기
        </button>
        {recItems.length > 0 && (
          <button type="button" className="btn soft" onClick={() => nav('/groups/new')}>
            새 모임 만들기
          </button>
        )}
        <button type="button" className="btn soft" onClick={() => goStep(1)}>
          처음부터 다시
        </button>
      </div>
    </>;
  }
  return <><PageHead kicker="PERSONALIZED JOURNEY" title="맞춤 추천 시작" sub="입력 → 분석 → 납입액 추천 → 모임 추천 순서로 진행합니다." />
    <div className="steps">{STEPS.map((x, i) => <div key={x} className={`step ${i + 1 === step ? 'current' : i + 1 < step ? 'done' : ''}`}>{i + 1}. {x}</div>)}</div><Card>{content}</Card></>;
}
