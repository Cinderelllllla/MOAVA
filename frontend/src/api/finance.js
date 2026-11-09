/* F05~F10 F14 — 금융 정보·추천·시뮬레이터 */
import { db, save, nowText, fail } from './mock/db';

export const RISK = { '안정': { coef: .5, prob: '연체 확률 10% 미만' }, '주의': { coef: .35, prob: '연체 확률 10~25%' }, '위험': { coef: .2, prob: '연체 확률 25% 이상' } };
export const riskCoef = () => (RISK[db.finance.risk] || RISK['주의']).coef;
/** 추천액 = min((월수입−지출−저축−비상금)×안전계수, 월수입×12%), 5만 원 단위 내림 */
export function affordability() {
  const f = db.finance, free = f.income - f.fixed - f.variable - f.saving - f.reserve;
  return { free, rec: Math.max(0, Math.floor(Math.min(Math.max(0, free) * riskCoef(), f.income * .12) / 5) * 5), capped: Math.max(0, free) * riskCoef() > f.income * .12 };
}
export const payoutShift = (amount, n, r, rate) => (r - (n + 1) / 2) * amount * n * (rate / 100) / n;
export const getFinance = () => db.finance;
export const getMonthly = () => db.financeMonthly;
export const getJourneyStep = () => db.journey.step;

export async function setRisk(v) { if (!RISK[v]) fail('알 수 없는 등급'); db.finance.risk = v; save(); }
export async function saveFinance(values) {
  for (const k of ['income', 'fixed', 'variable', 'saving', 'reserve', 'goal']) { const v = Number(values[k]); if (!Number.isFinite(v) || v < 0) fail('금액을 확인해 주세요.'); db.finance[k] = v; }
  db.finance.consentAt ||= nowText(); db.journey.step = 2; save();
}
export async function setJourneyStep(n) { if (n > db.journey.step + 1) fail('앞 단계를 먼저 완료해 주세요.'); db.journey.step = n; save(); }
export async function saveMonth(month, income, expense) {
  if (income < 0 || expense < 0) fail('음수는 입력할 수 없어요.');
  db.financeMonthly = db.financeMonthly.filter(x => x.month !== month); db.financeMonthly.push({ month, income, expense });
  db.financeMonthly.sort((a, b) => Number(a.month.replace('월', '')) - Number(b.month.replace('월', ''))); save();
}
export async function removeMonth(month) { db.financeMonthly = db.financeMonthly.filter(x => x.month !== month); save(); }
