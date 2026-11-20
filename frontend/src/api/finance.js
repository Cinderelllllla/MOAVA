/* F05~F10 F14 — 금융 정보·추천·시뮬레이터 */
import { db, save, nowText, fail } from './mock/db';
import { getAccessToken, getAccessTokenMemberId } from './auth';
import { apiFetch, getToken } from './client';

import { RISK, INCOME_CAP_RATIO, AMOUNT_UNIT_MANWON } from '../common/policy';
export { RISK };
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
const PROFILE_FIELDS = ['income', 'fixed', 'variable', 'saving', 'reserve', 'goal'];
/** 사용자가 금융 프로필을 모두 입력했는지 (값이 null/빈칸이면 아직 입력 안 함) */
export const hasFinanceProfile = (f = db.finance) =>
  PROFILE_FIELDS.every(k => f?.[k] !== null && f?.[k] !== undefined && f[k] !== '' && Number.isFinite(Number(f[k])));

/** 화면용 추천 결과. AI(ML) 분석 결과가 있을 때만 { free, rec, capped }(만 원), 없으면 null.
 *  (가정 등급으로 계산한 값은 보여 주지 않는다 — 입력 → AI 분석 → 추천 순서) */
export function affordability() {
  const assessment = getMlAssessment();
  if (!assessment) return null;
  return {
    free: assessment.availableAmount,
    rec: assessment.recommendedAmount,
    capped: assessment.capped,
  };
}

/** 참고용(화면에서 사용하지 않음): ML 결과 없이 정책식으로 계산하던 예전 방식
 *  추천액 = min((월수입−지출−저축−비상금)×안전계수, 월수입×12%), 5만 원 단위 내림 */
export function policyAffordability() {
  const f = db.finance;
  const free = f.income - f.fixed - f.variable - f.saving - f.reserve;
  const cap = f.income * INCOME_CAP_RATIO;
  return { free, rec: Math.max(0, Math.floor(Math.min(Math.max(0, free) * riskCoef(), cap) / AMOUNT_UNIT_MANWON) * AMOUNT_UNIT_MANWON), capped: Math.max(0, free) * riskCoef() > cap };
}
export const payoutShift = (amount, n, r, rate) => (r - (n + 1) / 2) * amount * n * (rate / 100) / n;
/** 이 기기에 보관된 금융 프로필 (값이 null 이면 아직 입력 안 함) */
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

/** 화면 기본 선택 월 = 이번 달 ('11월' 형태) */
export const currentMonthLabel = () => `${new Date().getMonth() + 1}월`;

/* 월별 기록은 서버 값만 쓴다. 브라우저(localStorage)에는 저장하지 않고 메모리에만 둔다.
   status: 'loading'(불러오는 중) | 'ready'(완료, items 가 비어 있으면 기록 없음) | 'error'(실패, error 에 안내 문구) */
let monthly = { token: null, status: 'loading', items: [], error: '' };
function setMonthly(patch) { monthly = { ...monthly, ...patch }; save(); }   // save() → 구독 중인 화면 다시 그리기

/** 서버에서 월별 기록을 다시 받아 온다. 실패하면 status='error' 로 바꾸고 오류를 던진다(목업으로 대체하지 않음). */
export async function refreshMonthly() {
  const token = getToken();
  if (!token) { monthly = { token: null, status: 'ready', items: [], error: '' }; return []; }
  if (monthly.token !== token) monthly = { token, status: 'loading', items: [], error: '' };
  else if (monthly.status === 'error') setMonthly({ status: 'loading', error: '' });
  try {
    const data = await apiFetch(`${MONTHLY_API}?months=12`);
    if (getToken() !== token) return [];                          // 그사이 로그아웃/계정 변경 → 버림
    const [from, to] = monthWindow();
    const items = (data?.items || [])
      .filter(x => x.yearMonth >= from && x.yearMonth <= to)
      .map(x => ({ month: toMonthLabel(x.yearMonth), income: Number(x.income) / 10000, expense: Number(x.expense) / 10000 }));
    setMonthly({ status: 'ready', items, error: '' });
    return items;
  } catch (e) {
    if (getToken() === token) setMonthly({ status: 'error', error: e.message || '월별 기록을 불러오지 못했어요.' });
    throw e;
  }
}
/** 화면용: { status, items, error }. 로그인 계정이 바뀌면 처음 한 번 서버에서 받아 온다. */
export function getMonthlyState() {
  const token = getToken();
  if (!token) return { status: 'ready', items: [], error: '' };
  if (monthly.token !== token) {
    monthly = { token, status: 'loading', items: [], error: '' };
    // 화면을 그리는 도중에 상태를 바꾸지 않도록 다음 차례에 호출
    Promise.resolve().then(refreshMonthly).catch(() => {});
  }
  return monthly;
}
/** 월별 기록 [{ month:'11월', income, expense }] (만 원). 불러오는 중·실패면 빈 배열 */
export const getMonthly = () => getMonthlyState().items;
/** F07 지출 기본값 제안. income 은 화면 단위(만 원).
 *  로그인 상태이고 서버가 available=true 를 줄 때만 { decile, consumption, nonconsumption(만 원), baseYm }, 아니면 null. */
export async function getSpendingDefaults(income) {
  const v = Number(income);
  if (!getToken() || !Number.isFinite(v) || v <= 0) return null;
  const d = await apiFetch(`/api/v1/finance/spending-defaults?income=${Math.round(v * 10000)}`);
  if (!d?.available) return null;
  return {
    decile: d.decile,
    consumption: Math.round(Number(d.avgConsumption) / 10000),
    nonconsumption: Math.round(Number(d.avgNonconsumption) / 10000),
    baseYm: d.baseYm,
  };
}
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
  let response;
  try {
    response = await fetch('/ml-api/risk/contribution', {
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
  } catch {
    throw new Error('분석 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.');
  }
  const result = await response.json().catch(() => null);
  if (!response.ok) {
    // 분석 서버가 준 안내가 한국어면 그대로, 아니면 쉬운 문장으로
    const detail = typeof result?.detail === 'string' && /[가-힣]/.test(result.detail) ? result.detail : null;
    throw new Error(detail || (response.status >= 500 ? '분석 서버에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.' : '입력한 금융정보를 확인해 주세요.'));
  }
  if (!result?.risk?.risk_label || !Number.isFinite(Number(result.recommended_amount))
      || !Number.isFinite(Number(result.available_amount)) || !RISK[result.risk.risk_label]) {
    throw new Error('분석 결과를 읽지 못했어요. 잠시 후 다시 시도해 주세요.');
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
  if (!Number.isFinite(income) || !Number.isFinite(expense)) fail('금액을 입력해 주세요.');
  if (income < 0 || expense < 0) fail('음수는 입력할 수 없어요.');
  if (!getToken()) fail('로그인이 필요해요.');
  // 서버에 저장(같은 달이면 덮어쓰기) 후 목록 다시 받기
  await apiFetch(MONTHLY_API, { method: 'PUT', body: JSON.stringify({ yearMonth: toYearMonth(month), income: Math.round(income * 10000), expense: Math.round(expense * 10000) }) });
  await refreshMonthly();
}
export async function removeMonth(month) {
  if (!getToken()) fail('로그인이 필요해요.');
  await apiFetch(`${MONTHLY_API}/${toYearMonth(month)}`, { method: 'DELETE' });
  await refreshMonthly();
}
