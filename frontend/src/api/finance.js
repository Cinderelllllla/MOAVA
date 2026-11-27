/* F05~F10 F14 — 금융 정보·추천·시뮬레이터 */
import { db, emptyFinance, save, nowText, fail } from './mock/db';
import { getAccessToken, getAccessTokenMemberId } from './auth';
import { apiFetch, getToken } from './client';

import { RISK, INCOME_CAP_RATIO, AMOUNT_UNIT_MANWON } from '../common/policy';
export { RISK };

/* ---- 정책 값: ML 서버가 기준(백엔드 GET /api/v1/finance/ai/policy 가 대신 받아 줌).
        로그인 전이거나 받지 못하면 common/policy.js 기본값을 쓴다 ---- */
let policy = {
  incomeCapRatio: INCOME_CAP_RATIO,       // 추천액 상한 (월수입 대비 비율)
  amountUnitManwon: AMOUNT_UNIT_MANWON,   // 추천액 절사 단위 (만 원)
  source: 'default',                      // 'default' | 'ml'
};
let policyRequested = false;
async function loadPolicy() {
  try {
    const m = await apiFetch('/api/v1/finance/ai/policy');
    const cap = Number(m?.income_cap_ratio), unit = Number(m?.round_unit) / 10000;   // round_unit 은 원 단위
    policy = {
      incomeCapRatio: cap > 0 && cap <= 1 ? cap : policy.incomeCapRatio,
      amountUnitManwon: unit > 0 ? unit : policy.amountUnitManwon,
      source: 'ml',
    };
    save();   // 정책 값을 쓰는 화면 다시 그리기
  } catch {
    // 분석 서버가 꺼져 있거나 실패하면 기본값 유지
  }
}
/** 화면용 정책 값 { incomeCapRatio, amountUnitManwon, source }. 로그인 후 처음 부를 때 한 번 서버에서 받아 온다. */
export function getPolicy() {
  if (!policyRequested && getToken()) {   // 로그인 전에는 부르지 않고 기본값 (로그인 후 다시 시도)
    policyRequested = true;
    Promise.resolve().then(loadPolicy);   // 화면을 그리는 도중에 상태를 바꾸지 않도록 다음 차례에 호출
  }
  return policy;
}
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

const manwon = n => `${Number(n).toLocaleString('ko-KR')}만 원`;

/** 월 가용 금액(수입 − 지출 − 저축 − 비상금) 표시. 마이너스면 "-50만 원" 대신 "50만 원 부족"으로 보여 준다. */
export function freeText(free) {
  const n = Number(free);
  return n < 0 ? `${manwon(-n)} 부족` : manwon(n);
}

/** 추천액이 0원일 때 화면에 보여 줄 이유. 추천액이 있으면 null.
 *  (0원은 오류가 아니라 "지금은 납입 여유가 부족하다"는 정상 결과) */
export function noRecommendReason(a) {
  if (!a || a.rec > 0) return null;
  const unit = getPolicy().amountUnitManwon;
  if (a.free < 0) return `수입보다 지출·저축·비상금 합계가 ${manwon(-a.free)} 더 많아서 매달 모자라는 상태예요.`;
  return a.free === 0
    ? '수입에서 지출·저축·비상금을 빼면 매달 남는 돈이 없어요.'
    : `매달 남는 돈 ${Number(a.free).toLocaleString('ko-KR')}만 원에 안전계수를 적용하면 최소 납입 단위(${unit}만 원)보다 적어요.`;
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

/** F14 시중 정기적금 세전 단리 만기 예상액 계산 (단위: 만 원) */
export function savingMaturity(amountManwon, months, annualRatePercent) {
  const a = Number(amountManwon);
  const n = Number(months);
  const rate = Number(annualRatePercent);
  const total = a * n;
  if (!Number.isFinite(a) || !Number.isFinite(n) || !Number.isFinite(rate) || n <= 0) {
    return { total: 0, interest: 0, maturity: 0 };
  }
  const interest = a * (n * (n + 1) / 2) * (rate / 100 / 12);
  return { total, interest, maturity: total + interest };
}

let savingCache = { token: null, byTerm: new Map() };

/** F14 시중 적금 금리 비교 조회 (GET /api/v1/finance/saving-products?termMonths=...) */
export async function getSavingComparison(termMonths) {
  const token = getToken();
  if (!token) return null;
  if (savingCache.token !== token) {
    savingCache = { token, byTerm: new Map() };
  }
  const key = termMonths != null && Number.isFinite(Number(termMonths)) ? String(Math.round(Number(termMonths))) : 'ALL';
  if (savingCache.byTerm.has(key)) {
    return savingCache.byTerm.get(key);
  }
  const qs = key === 'ALL' ? '' : `?termMonths=${encodeURIComponent(key)}`;
  const data = await apiFetch(`/api/v1/finance/saving-products${qs}`);
  const normalized = {
    available: Boolean(data?.available),
    requestedTermMonths: data?.requestedTermMonths ?? null,
    matchedTermMonths: data?.matchedTermMonths ?? null,
    productCount: Number(data?.productCount || 0),
    avgBasicRate: data?.avgBasicRate != null ? Number(data.avgBasicRate) : null,
    avgMaxRate: data?.avgMaxRate != null ? Number(data.avgMaxRate) : null,
    topMaxRate: data?.topMaxRate != null ? Number(data.topMaxRate) : null,
    fetchedAt: data?.fetchedAt || null,
    topProducts: Array.isArray(data?.topProducts)
      ? data.topProducts.map((p) => ({
          bankName: p.bankName || '',
          productName: p.productName || '',
          termMonths: Number(p.termMonths || 0),
          rateBasic: p.rateBasic != null ? Number(p.rateBasic) : 0,
          rateMax: p.rateMax != null ? Number(p.rateMax) : Number(p.rateBasic || 0),
        }))
      : [],
  };
  if (getToken() === token) {
    savingCache.byTerm.set(key, normalized);
  }
  return normalized;
}

/** 이 기기에 보관된 금융 프로필 (값이 null 이면 아직 입력 안 함) */
export const getFinance = () => db.finance;

/* ---- F09 자가 응답 4문항 (MOA-98). 위험 등급은 이 답변으로 AI 가 계산한다.
        민감한 신용 정보라 서버 DB·브라우저 저장소에 남기지 않고 이 탭의 메모리에만 둔다
        (새로고침하면 사라짐 → 다시 분석할 때 1단계에서 다시 답한다). 코드값은 ML 서버와 같다. ---- */
export const SELF_REPORT_QUESTIONS = [
  { key: 'late_count_12m', label: '최근 1년간 30일 이상 연체한 횟수', options: [['0', '0회'], ['1', '1회'], ['2', '2회'], ['3_PLUS', '3회 이상']] },
  { key: 'max_late_period', label: '최근 6개월 동안 가장 길게 연체한 기간', options: [['NONE', '없음'], ['UNDER_1M', '1개월 미만'], ['M1_2', '1~2개월'], ['M2_3', '2~3개월'], ['OVER_3M', '3개월 이상']] },
  { key: 'loan_count', label: '지금 갚고 있는 대출 수', options: [['0', '0개'], ['1', '1개'], ['2', '2개'], ['3', '3개'], ['4', '4개'], ['5_PLUS', '5개 이상']] },
  { key: 'card_usage', label: '신용카드 한도 중 쓰고 있는 비율', options: [['NO_CARD', '카드 없음'], ['UNDER_30', '30% 미만'], ['P30_50', '30~50%'], ['P50_70', '50~70%'], ['P70_90', '70~90%'], ['OVER_90', '90% 이상']] },
];
let selfReport = null;   // { memberId, answers }
let selfReportVersion = 0;   // 답할 때마다 1씩 늘어 분석 훅이 다시 돌게 한다
export const getSelfReportVersion = () => selfReportVersion;
const currentMemberId = () => (getAccessToken() ? getAccessTokenMemberId() : Number(db.session?.id));
/** 4문항 모두 정해진 선택지로 답했는지 */
export const isSelfReportComplete = answers =>
  SELF_REPORT_QUESTIONS.every(q => q.options.some(([code]) => code === answers?.[q.key]));
/** 지금 로그인한 회원이 이 탭에서 답한 4문항 (없으면 null) */
export const getSelfReport = () => (selfReport && selfReport.memberId === currentMemberId() ? selfReport.answers : null);
export function setSelfReport(answers) {
  if (!isSelfReportComplete(answers)) fail('신용 관련 질문 4개에 모두 답해 주세요.');
  selfReport = { memberId: currentMemberId(), answers: Object.fromEntries(SELF_REPORT_QUESTIONS.map(q => [q.key, answers[q.key]])) };
  selfReportVersion += 1;
}
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
/** F07 지출 기본값 제안 · F06 평균 지출 비교(MOA-94). income 은 화면 단위(만 원).
 *  로그인 상태이고 서버가 available=true 를 줄 때만
 *  { decile, consumption, nonconsumption, total(가계지출 = 소비 + 비소비, 만 원), baseYm }, 아니면 null.
 *  같은 로그인·같은 월수입은 한 번만 조회한다(1단계 ↔ 2단계를 오가도 다시 부르지 않음). 실패한 조회는 기억하지 않는다. */
const spendingCache = new Map();   // `${token}:${income(원)}` → Promise
export function getSpendingDefaults(income) {
  const v = Number(income);
  const token = getToken();
  if (!token || !Number.isFinite(v) || v <= 0) return Promise.resolve(null);
  const won = Math.round(v * 10000);
  const key = `${token}:${won}`;
  if (!spendingCache.has(key)) {
    const req = apiFetch(`/api/v1/finance/spending-defaults?income=${won}`)
      .then(d => {
        if (!d?.available) return null;
        const c = Number(d.avgConsumption), n = Number(d.avgNonconsumption);
        return {
          decile: d.decile,
          consumption: Math.round(c / 10000),
          nonconsumption: Math.round(n / 10000),
          total: Math.round((c + n) / 10000),   // 따로 반올림해 더하면 1만 원 어긋날 수 있어 원 단위 합에서 한 번만 반올림
          baseYm: d.baseYm,
        };
      })
      .catch(e => { spendingCache.delete(key); throw e; });
    spendingCache.set(key, req);
  }
  return spendingCache.get(key);
}
const PROFILE_API = '/api/v1/finance/profile';
let profileInFlight = { token: null, promise: null };

function applyServerProfile(data, token, { preserveAssessment = true } = {}) {
  if (!data?.exists) {
    db.finance = emptyFinance();
    db.journey = { step: 1 };
    save();
    return db.finance;
  }

  const toManwon = v => (v == null ? null : Number(v) / 10000);
  db.finance.income = toManwon(data.income);
  db.finance.fixed = toManwon(data.fixedExp);
  db.finance.variable = toManwon(data.variableExp);
  db.finance.saving = toManwon(data.saving);
  db.finance.reserve = toManwon(data.reserve);
  db.finance.goal = toManwon(data.goalAmount);
  db.finance.consentAt = data.consent ? (data.consentAt || nowText()) : null;
  db.finance.serverSyncedAt = data.updatedAt || nowText();

  const step = Number(data.journeyStep);
  db.journey.step = Number.isInteger(step) && step >= 1 && step <= 4 ? step : 1;

  const profileKey = financeProfileKey(db.finance);
  const memberId = token ? getAccessTokenMemberId(token) : Number(db.session?.id);

  if (preserveAssessment && data.assessment && data.assessment.riskLabel) {
    db.finance.risk = data.assessment.riskLabel;
    db.finance.mlAssessment = {
      profileKey,
      memberId,
      availableAmount: Number(data.assessment.availableAmount) / 10000,
      recommendedAmount: Number(data.assessment.recommendedAmount) / 10000,
      capped: Boolean(data.assessment.capped),
      risk: {
        risk_level: data.assessment.riskLevel,
        risk_label: data.assessment.riskLabel,
        safety_factor: Number(data.assessment.safetyFactor),
      },
      assessedAt: data.assessment.scoredAt || nowText(),
    };
  } else if (!preserveAssessment || db.finance.mlAssessment?.profileKey !== profileKey) {
    db.finance.mlAssessment = null;
  }

  save();
  return db.finance;
}

/** 서버 DB(FINANCE_PROFILE + 최신 RISK_SCORE)에서 금융 프로필과 분석 결과를 불러와 복원한다. */
export async function refreshFinanceProfile() {
  const token = getToken();
  if (!token) return null;
  if (profileInFlight.token === token && profileInFlight.promise) {
    return profileInFlight.promise;
  }
  const req = apiFetch(PROFILE_API)
    .then(data => {
      if (getToken() !== token) return null;
      return applyServerProfile(data, token, { preserveAssessment: true });
    })
    .finally(() => {
      if (profileInFlight.promise === req) {
        profileInFlight = { token: null, promise: null };
      }
    });
  profileInFlight = { token, promise: req };
  return req;
}

export const getJourneyStep = () => db.journey.step;

export async function setRisk(v) { if (!RISK[v]) fail('알 수 없는 등급'); db.finance.risk = v; save(); }
export async function saveFinance(values) {
  const parsed = {};
  for (const k of ['income', 'fixed', 'variable', 'saving', 'reserve', 'goal']) {
    const v = Number(values[k]);
    if (!Number.isFinite(v) || v < 0) fail('금액을 확인해 주세요.');
    parsed[k] = v;
  }
  const token = getToken();
  if (!token) fail('로그인이 필요해요.');

  const saved = await apiFetch(PROFILE_API, {
    method: 'PUT',
    body: JSON.stringify({
      income: Math.round(parsed.income * 10000),
      fixedExp: Math.round(parsed.fixed * 10000),
      variableExp: Math.round(parsed.variable * 10000),
      saving: Math.round(parsed.saving * 10000),
      reserve: Math.round(parsed.reserve * 10000),
      goalAmount: Math.round(parsed.goal * 10000),
      consent: true,
      journeyStep: 2,
    }),
  });
  // 저장 직후에는 새 입력값으로 ML 분석을 다시 실행해 최신 RISK_SCORE 를 기록하도록 mlAssessment 를 비운다
  applyServerProfile(saved, token, { preserveAssessment: false });
}
export async function assessWithMl(memberId) {
  const answers = getSelfReport();
  if (!answers) fail('맞춤 추천 1단계에서 신용 관련 질문 4개에 답하면 AI 분석을 할 수 있어요.');
  const f = db.finance;
  const input = {
    income: Number(f.income),
    fixed: Number(f.fixed),
    variable: Number(f.variable),
    saving: Number(f.saving),
    reserve: Number(f.reserve),
  };
  const profileKey = financeProfileKey(input);
  if (!getToken()) fail('로그인이 필요해요.');
  // 위험 등급은 자가 응답 4문항(self_report)으로, 추천액은 금액으로 계산한다. member_id 는 보내지 않는다.
  // 오류 안내(분석 서버 연결 실패 503, 입력 확인 400 등)는 apiFetch 가 서버의 한국어 문장으로 던져 준다.
  const result = await apiFetch('/api/v1/finance/ai/contribution', {
    method: 'POST',
    body: JSON.stringify({
      monthly_income: Math.round(input.income * 10000),
      monthly_expense: Math.round((input.fixed + input.variable) * 10000),
      monthly_saving: Math.round(input.saving * 10000),
      emergency_fund: Math.round(input.reserve * 10000),
      self_report: answers,
    }),
  });
  // 위험 등급 이름은 ML 서버가 기준이라 프론트 목록(RISK)과 비교하지 않는다
  if (!result?.risk?.risk_label || !Number.isFinite(Number(result.recommended_amount))
      || !Number.isFinite(Number(result.available_amount))) {
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
    // MOA-95 ML 장애 대체값: 서버가 직전 등급(PREVIOUS) 또는 기본 '주의'(DEFAULT)로 계산했는지
    fallback: result.fallback === true,
    fallbackSource: result.fallback === true ? (result.fallback_source || 'DEFAULT') : null,
    assessedAt: nowText(),
  };
  save();
  return db.finance.mlAssessment;
}
/** 지금 분석 결과를 비워 AI 분석을 다시 하게 한다(대체값 안내의 '다시 분석'). 신용 질문 답이 없으면 1단계에서 다시 답해야 한다 */
export function clearMlAssessment() {
  db.finance.mlAssessment = null;
  save();
}
export async function setJourneyStep(n) {
  if (n > db.journey.step + 1) fail('앞 단계를 먼저 완료해 주세요.');
  db.journey.step = n;
  save();
  if (getToken() && hasFinanceProfile(db.finance)) {
    apiFetch(`${PROFILE_API}/step`, {
      method: 'PATCH',
      body: JSON.stringify({ step: n }),
    }).catch(() => {});
  }
}
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
