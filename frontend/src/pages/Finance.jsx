/* S04 AI 금융 분석 /finance · S05 월별 데이터 /finance/monthly · F06 F08 F09 F10 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { finance } from '../api';
import { PageHead, Card, Input, Select } from '../components/ui';
import { BarChart, RiskPanel } from '../components/shared';
import { won, MONTHS } from '../common/format';

export function Finance() {
  const nav = useNavigate(); const f = finance.getFinance(), a = finance.affordability();
  return <><PageHead kicker="AI FINANCIAL ANALYSIS" title="금융 분석 · AI 추천" sub="현재 금융정보를 확인하고 월별 입력 데이터를 관리해 보세요."><button className="btn primary" onClick={() => nav('/finance/monthly')}>월별 데이터 저장 →</button></PageHead>
    <div className="grid cols3">{[['월수입', won(f.income)], ['월지출', won(f.fixed + f.variable)], ['추천 납입액', won(a.rec)]].map(([t, v]) => <Card key={t}><small>{t}</small><div className="metric">{v}</div></Card>)}</div>
    <div className="section"><Card><RiskPanel /></Card></div>
    <div className="grid cols2 section"><Card><h2>월별 수입 · 지출 추이</h2><BarChart data={finance.getMonthly()} /></Card>
      <Card><h2>금융 요약</h2><div className="list"><div className="list-item">기존 저축 {won(f.saving)}</div><div className="list-item">비상 예비금 {won(f.reserve)}</div><div className="list-item">월 가용 금액 {won(a.free)}</div><div className="list-item">희망 월 납입액 {won(f.goal)} {f.goal > a.rec ? <span className="pill warn">추천액 초과</span> : <span className="pill">추천 범위</span>}</div></div><p className="info mini">추천 결과는 규칙 기반 데모입니다. 연체 확률은 FastAPI 모델(RISK_SCORE) 연동 후 자동 산출되며, 타임아웃 시 직전 값 또는 주의(0.35)로 폴백합니다.</p></Card></div></>;
}

export function FinanceMonthly() {
  const { run, ui, setUi } = useApp(); const f = finance.getFinance();
  const [v, setV] = useState({ month: ui.financeMonth, income: f.income, expense: f.fixed + f.variable });
  const submit = async e => { e.preventDefault(); const r = await run(() => finance.saveMonth(v.month, Number(v.income), Number(v.expense)), '월별 데이터가 저장되었어요.'); if (r.ok) setUi({ financeMonth: v.month }); };
  return <><PageHead kicker="MONTHLY DATA" title="월별 금융 데이터 관리" sub="월별 수입·지출 데이터를 추가하고 저장합니다." />
    <Card><form className="form-grid" onSubmit={submit}><Select label="대상 월" name="month" options={MONTHS} value={v.month} onChange={e => setV({ ...v, month: e.target.value })} /><Input label="월수입 (만 원)" name="income" type="number" min={0} required value={v.income} onChange={e => setV({ ...v, income: e.target.value })} /><Input label="월지출 (만 원)" name="expense" type="number" min={0} required value={v.expense} onChange={e => setV({ ...v, expense: e.target.value })} /><div className="field"><label>데이터 저장</label><button className="btn primary">이 월 데이터 저장</button></div></form><p className="info mini">현재는 브라우저에 저장됩니다. 실제 저장은 Spring Boot API(FINANCE_MONTHLY) 연결 후 동작합니다.</p></Card>
    <div className="section"><Card><h2>저장된 월별 데이터</h2><BarChart data={finance.getMonthly()} /><div className="tablewrap"><table><thead><tr><th>월</th><th>수입</th><th>지출</th><th>잔여</th><th>관리</th></tr></thead><tbody>{finance.getMonthly().map(x => <tr key={x.month}><td>{x.month}</td><td>{won(x.income)}</td><td>{won(x.expense)}</td><td>{won(x.income - x.expense)}</td><td><button className="btn small danger" onClick={() => confirm(`${x.month} 데이터를 삭제할까요?`) && run(() => finance.removeMonth(x.month))}>삭제</button></td></tr>)}</tbody></table></div></Card></div></>;
}
