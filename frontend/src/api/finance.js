/* F05~F10 F14 — 금융 정보·추천·시뮬레이터 */
import { db, save, nowText, fail } from './mock/db';
import { getAccessToken, getAccessTokenMemberId } from './auth';

export const RISK = { '안정': { coef: .5, prob: '연체 확률 10% 미만' }, '주의': { coef: .35, prob: '연체 확률 10~25%' }, '위험': { coef: .2, prob: '연체 확률 25% 이상' } };
export const riskCoef = () => (RISK[db.finance.risk] || RISK['주의']).coef;
const financeProfileKey = f => ['income', 'fixed', 'variable', 'saving', 'reserve'].map(k => Number(f[k])).join(':');
export const getMlAssessment = () => {
  const assessment = db.finance.mlAssessment;
  const memberId = getAccessToken()
    ? getAccessTokenMemberId()
    : Number(db.session?.id);
  return assessment?.profileKey === financeProfileKey(db.finance)
    && assessment.memberId === memberId ? assessment : null;
};
/** 추천액 = min((월수입−지출−저축−비상금)×안전계수, 월수입×12%), 5만 원 단위 내림 */
export function affordability() {
  const f = db.finance, assessment = getMlAssessment();
  if (assessment) {
    return {
      free: assessment.availableAmount,
      rec: assessment.recommendedAmount,
      capped: assessment.capped,
    };
  }
  const free = f.income - f.fixed - f.variable - f.saving - f.reserve;
  return { free, rec: Math.max(0, Math.floor(Math.min(Math.max(0, free) * riskCoef(), f.income * .12) / 5) * 5), capped: Math.max(0, free) * riskCoef() > f.income * .12 };
}
export const payoutShift = (amount, n, r, rate) => (r - (n + 1) / 2) * amount * n * (rate / 100) / n;
export const getFinance = () => db.finance;
export const getMonthly = () => db.financeMonthly;
export const getJourneyStep = () => db.journey.step;

export async function setRisk(v) { if (!RISK[v]) fail('알 수 없는 등급'); db.finance.risk = v; save(); }
export async function saveFinance(values) {
  for (const k of ['income', 'fixed', 'variable', 'saving', 'reserve', 'goal']) { const v = Number(values[k]); if (!Number.isFinite(v) || v < 0) fail('금액을 확인해 주세요.'); db.finance[k] = v; }
  db.finance.mlAssessment = null;
  db.finance.consentAt ||= nowText(); db.journey.step = 2; save();
}
export async function assessWithMl(memberId) {
  const f = db.finance;
  const input = {
    income: Number(f.income),
    fixed: Number(f.fixed),
    variable: Number(f.variable),
    saving: Number(f.saving),
    reserve: Number(f.reserve),
  };
  const profileKey = financeProfileKey(input);
  const response = await fetch('/ml-api/risk/contribution', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      member_id: memberId,
      monthly_income: Math.round(input.income * 10000),
      monthly_expense: Math.round((input.fixed + input.variable) * 10000),
      monthly_saving: Math.round(input.saving * 10000),
      emergency_fund: Math.round(input.reserve * 10000),
    }),
  });
  const result = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(result?.detail || `ML 분석 요청 실패 (HTTP ${response.status})`);
  }
  if (!result?.risk?.risk_label || !Number.isFinite(Number(result.recommended_amount))
      || !Number.isFinite(Number(result.available_amount))) {
    throw new Error('ML 분석 응답 형식이 올바르지 않습니다.');
  }
  if (!RISK[result.risk.risk_label]) {
    throw new Error(`지원하지 않는 ML 위험 등급입니다: ${result.risk.risk_label}`);
  }
  if (financeProfileKey(db.finance) !== profileKey) {
    throw new Error('분석 중 금융정보가 변경되었습니다. 다시 분석해 주세요.');
  }
  const activeMemberId = getAccessToken()
    ? getAccessTokenMemberId()
    : Number(db.session?.id);
  if (activeMemberId !== memberId) {
    throw new Error('분석 중 로그인 계정이 변경되었습니다. 다시 분석해 주세요.');
  }

  db.finance.risk = result.risk.risk_label;
  db.finance.mlAssessment = {
    profileKey,
    memberId,
    availableAmount: Number(result.available_amount) / 10000,
    recommendedAmount: Number(result.recommended_amount) / 10000,
    capped: Boolean(result.capped),
    risk: result.risk,
    assessedAt: nowText(),
  };
  save();
  return db.finance.mlAssessment;
}
export async function setJourneyStep(n) { if (n > db.journey.step + 1) fail('앞 단계를 먼저 완료해 주세요.'); db.journey.step = n; save(); }
export async function saveMonth(month, income, expense) {
  if (income < 0 || expense < 0) fail('음수는 입력할 수 없어요.');
  db.financeMonthly = db.financeMonthly.filter(x => x.month !== month); db.financeMonthly.push({ month, income, expense });
  db.financeMonthly.sort((a, b) => Number(a.month.replace('월', '')) - Number(b.month.replace('월', ''))); save();
}
export async function removeMonth(month) { db.financeMonthly = db.financeMonthly.filter(x => x.month !== month); save(); }
