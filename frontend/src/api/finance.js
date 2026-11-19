/* F05~F10 F14 — 금융 정보·추천·시뮬레이터 */
import { db, save, nowText, fail } from './mock/db';
import { getAccessToken, getAccessTokenMemberId } from './auth';
import { apiFetch, getToken } from './client';

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
/* ---- F10 월별 수입·지출 (서버: 원 · yearMonth "202611" ↔ 화면: 만 원 · month "11월") ---- */
const MONTHLY_API = '/api/v1/finance/monthly';
const pad2 = n => String(n).padStart(2, '0');
const ymOf = (y, m) => `${y}${pad2(m)}`;
/** 화면은 '1월'~'12월'만 고르므로 "오늘 기준 최근 12개월" 안의 달로 해석한다.
 *  (예: 2026년 11월에 '11월' → 202611, '12월' → 202512) */
function toYearMonth(month) {
  const m = Number(String(month).replace('월', '')), now = new Date();
  const y = m > now.getMonth() + 1 ? now.getFullYear() - 1 : now.getFullYear();
  return ymOf(y, m);
}
const toMonthLabel = ym => `${Number(String(ym).slice(4))}월`;
/** 최근 12개월 범위 [시작, 이번 달] — 같은 '11월' 라벨이 두 번 나오지 않게 이 범위 밖 기록은 화면에서 뺀다. */
function monthWindow() {
  const now = new Date(), start = new Date(now.getFullYear(), now.getMonth() - 11, 1);
  return [ymOf(start.getFullYear(), start.getMonth() + 1), ymOf(now.getFullYear(), now.getMonth() + 1)];
}

/** 월별 기록을 서버에 저장하는 상태인지(로그인 JWT 있음) — 화면 안내 문구용 */
export const isMonthlyOnServer = () => !!getToken();
let monthlySyncedToken = null;   // 이 토큰으로 이미 서버에서 받아왔는지 (중복 호출 방지)
/** 서버 월별 기록을 받아 db.financeMonthly(화면용 캐시)에 채우고 save()로 화면을 다시 그린다. */
export async function refreshMonthly() {
  const token = getToken();
  if (!token) return db.financeMonthly;                        // 데모(목업) 로그인: 브라우저 데이터 그대로
  monthlySyncedToken = token;
  const data = await apiFetch(`${MONTHLY_API}?months=12`);
  if (getToken() !== token) return db.financeMonthly;           // 그사이 로그아웃/계정 변경 → 버림
  const [from, to] = monthWindow();
  db.financeMonthly = (data?.items || [])
    .filter(x => x.yearMonth >= from && x.yearMonth <= to)
    .map(x => ({ month: toMonthLabel(x.yearMonth), income: Number(x.income) / 10000, expense: Number(x.expense) / 10000 }));
  save();
  return db.financeMonthly;
}
/** 동기 함수 그대로 유지(Dashboard·차트가 그대로 씀). 로그인 상태면 처음 한 번 서버 값을 받아와 채운다.
 *  서버 오류면 기존 브라우저(목업) 데이터를 그대로 보여 준다. */
export const getMonthly = () => {
  const token = getToken();
  if (token && monthlySyncedToken !== token) {
    monthlySyncedToken = token;
    refreshMonthly().catch(err => console.warn('[F10] 월별 기록 조회 실패 — 브라우저 데이터로 표시합니다.', err));
  }
  return db.financeMonthly;
};
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
  if (getToken()) {   // 로그인: 서버에 저장(같은 달이면 덮어쓰기) 후 목록 다시 받기
    await apiFetch(MONTHLY_API, { method: 'PUT', body: JSON.stringify({ yearMonth: toYearMonth(month), income: Math.round(income * 10000), expense: Math.round(expense * 10000) }) });
    await refreshMonthly();
    return;
  }
  db.financeMonthly = db.financeMonthly.filter(x => x.month !== month); db.financeMonthly.push({ month, income, expense });
  db.financeMonthly.sort((a, b) => Number(a.month.replace('월', '')) - Number(b.month.replace('월', ''))); save();
}
export async function removeMonth(month) {
  if (getToken()) {   // 로그인: 서버에서 삭제 후 목록 다시 받기
    await apiFetch(`${MONTHLY_API}/${toYearMonth(month)}`, { method: 'DELETE' });
    await refreshMonthly();
    return;
  }
  db.financeMonthly = db.financeMonthly.filter(x => x.month !== month); save(); }
