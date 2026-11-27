/* S04 AI 금융 분석 /finance · S05 월별 데이터 /finance/monthly · F06 F08 F09 F10 */
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import useMlFinanceAssessment from '../common/useMlFinanceAssessment';
import { finance } from '../api';
import { PageHead, Card, Input, Select } from '../components/ui';
import { BarChart, FinanceAssessmentStatus, LoadState, MonthlyChart, RiskPanel } from '../components/shared';
import { won, MONTHS } from '../common/format';

export function Finance() {
  const mlAssessment = useMlFinanceAssessment();
  const nav = useNavigate(); const f = finance.getFinance(), a = finance.affordability();
  const hasProfile = finance.hasFinanceProfile(f);
  const show = v => (hasProfile ? won(v) : '-');
  return <><PageHead kicker="AI FINANCIAL ANALYSIS" title="금융 분석 · AI 추천" sub="현재 금융정보를 확인하고 월별 입력 데이터를 관리해 보세요."><button className="btn primary" onClick={() => nav('/finance/monthly')}>월별 데이터 저장 →</button></PageHead>
    <FinanceAssessmentStatus {...mlAssessment} />
    <div className="grid cols3">{[['월수입', show(f.income)], ['월지출', hasProfile ? won(Number(f.fixed) + Number(f.variable)) : '-'], ['추천 납입액', a ? (a.rec > 0 ? won(a.rec) : '추천 없음') : '-']].map(([t, v]) => <Card key={t}><small>{t}</small><div className="metric">{v}</div></Card>)}</div>
    <div className="section"><Card><RiskPanel /></Card></div>
    <div className="grid cols2 section"><Card><h2>월별 수입 · 지출 추이</h2><MonthlyChart /></Card>
      <Card><h2>금융 요약</h2>{hasProfile
        ? <div className="list"><div className="list-item">기존 저축 {won(f.saving)}</div><div className="list-item">비상 예비금 {won(f.reserve)}</div><div className="list-item">매달 남는 돈(수입 − 지출 − 저축 − 비상금) {a ? finance.freeText(a.free) : '분석 전'}</div><div className="list-item">희망 월 납입액 {won(f.goal)} {a && (a.rec <= 0 ? <span className="pill warn">지금은 추천 없음</span> : Number(f.goal) > a.rec ? <span className="pill warn">추천액 초과</span> : <span className="pill">추천 범위</span>)}</div></div>
        : <div className="empty">아직 입력한 금융정보가 없어요. <button type="button" className="btn small soft" onClick={() => nav('/journey')}>맞춤 추천에서 입력하기</button></div>}
        <p className="info mini">{a ? '위험도와 추천액은 입력한 금융정보를 AI가 분석한 참고 결과예요.' : '금융정보를 입력하고 이용에 동의하면 AI 분석 결과를 볼 수 있어요.'} 입력한 금융정보와 AI 분석 결과는 계정에 저장되어 다시 로그인해도 유지돼요.</p></Card></div></>;
}

export function FinanceMonthly() {
  const { run, ui, setUi } = useApp();
  const [v, setV] = useState({ month: ui.financeMonth || finance.currentMonthLabel(), income: '', expense: '' });
  // 화면에 들어올 때 서버의 최신 월별 기록을 받아 온다(실패하면 오류 안내 + 다시 시도)
  useEffect(() => { finance.refreshMonthly().catch(() => {}); }, []);
  const monthly = finance.getMonthlyState();
  const submit = async e => { e.preventDefault(); const r = await run(() => finance.saveMonth(v.month, Number(v.income), Number(v.expense)), '월별 데이터가 저장되었어요.'); if (r.ok) { setUi({ financeMonth: v.month }); setV({ ...v, income: '', expense: '' }); } };
  return <><PageHead kicker="MONTHLY DATA" title="월별 금융 데이터 관리" sub="월별 수입·지출 데이터를 추가하고 저장합니다." />
    <Card><form className="form-grid" onSubmit={submit}><Select label="대상 월" name="month" options={MONTHS} value={v.month} onChange={e => setV({ ...v, month: e.target.value })} /><Input label="월수입 (만 원)" name="income" type="number" min={0} required value={v.income} onChange={e => setV({ ...v, income: e.target.value })} /><Input label="월지출 (만 원)" name="expense" type="number" min={0} required value={v.expense} onChange={e => setV({ ...v, expense: e.target.value })} /><div className="field"><label>데이터 저장</label><button className="btn primary">이 월 데이터 저장</button></div></form><p className="info mini">같은 월을 다시 저장하면 덮어써요. 대상 월은 오늘 기준 최근 12개월 안의 달로 저장돼요.</p></Card>
    <div className="section"><Card><h2>저장된 월별 데이터</h2>
      <LoadState status={monthly.status} error={monthly.error} onRetry={() => finance.refreshMonthly().catch(() => {})} loadingText="월별 기록을 불러오는 중이에요.">
        <BarChart data={monthly.items} />
        {monthly.items.length > 0 && <div className="tablewrap"><table><thead><tr><th>월</th><th>수입</th><th>지출</th><th>잔여</th><th>관리</th></tr></thead><tbody>{monthly.items.map(x => <tr key={x.month}><td>{x.month}</td><td>{won(x.income)}</td><td>{won(x.expense)}</td><td>{won(x.income - x.expense)}</td><td><button className="btn small danger" onClick={() => confirm(`${x.month} 데이터를 삭제할까요?`) && run(() => finance.removeMonth(x.month), `${x.month} 데이터를 삭제했어요.`)}>삭제</button></td></tr>)}</tbody></table></div>}
      </LoadState></Card></div></>;
}
