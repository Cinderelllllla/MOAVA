/* ============================================================
   MOAVA · api/mock/db.js — 브라우저(localStorage) 가짜 DB
   기준: 테이블 정의서 v1.5. 서버 연동 후에는 이 폴더 전체가 필요 없어집니다.
   ============================================================ */
const KEY = 'moava-react-v1';

export function groupDefaults(o) {
  return Object.assign({
    id: 0, title: '', region: '', purpose: '목돈 마련', amount: 20, max: 6, months: 6,
    payDay: 5, ownerId: 0, members: [], status: '모집 중',
    rulesTitle: '운영 규칙', rules: '', rate: 3,
    started: false, orderConfirmed: false, order: [], payoutReceived: [],
    warnings: {}, withdrawals: [], dissolution: null, joinRequests: [], description: '',
    visibility: 'PUBLIC', inviteToken: '', joinCode: null,
    account: null, pendingAccount: null, successor: null, transfer: null,
    restrictions: {}, overduePolicy: '5일차 알림', log: [],
  }, o);
}

export const SEED = {
  members: [
    { id: 1, name: '이모아', email: 'moa@example.com', password: 'demo1234', phone: '010-1234-5678', regionSido: '서울', regionSigungu: '마포구', status: 'ACTIVE', privacyAgreedAt: '2026-10-01' },
    { id: 2, name: '김하늘', email: 'leader@example.com', password: 'demo1234', phone: '010-2222-3333', regionSido: '서울', regionSigungu: '마포구', status: 'ACTIVE', privacyAgreedAt: '2026-10-01' },
  ],
  admins: [{ id: 1, name: '관리자', email: 'admin@moava.test', password: 'admin1234' }],
  session: null,
  finance: { income: 320, fixed: 125, variable: 83, saving: 35, reserve: 25, goal: 20, months: 6, risk: '주의', consentAt: null },
  financeMonthly: [
    { month: '5월', income: 310, expense: 231 }, { month: '6월', income: 320, expense: 224 }, { month: '7월', income: 305, expense: 243 },
    { month: '8월', income: 320, expense: 211 }, { month: '9월', income: 315, expense: 239 }, { month: '10월', income: 320, expense: 208 },
  ],
  groups: [
    groupDefaults({ id: 101, title: '마포 직장인 목돈 모으기', region: '서울 마포구', purpose: '목돈 마련', amount: 20, max: 6, months: 6, payDay: 5, ownerId: 2, members: [1, 2], rulesTitle: '안전한 납입과 정산', rules: '매월 5일 납입 · 탈퇴 시 정산안 공식 동의', rate: 3, description: '지역 직장인을 위한 부담 없는 목돈 마련 모임', inviteToken: 'inv-mapo-101', account: { bank: '카카오뱅크', holder: '김하늘', accountNo: '3333012345678', verifyStatus: 'PENDING', keyVer: 1 } }),
    groupDefaults({ id: 102, title: '서대문 우리동네 재테크', region: '서울 서대문구', purpose: '재테크', amount: 15, max: 6, months: 6, payDay: 10, ownerId: 2, members: [2], rulesTitle: '모임 운영 규칙', rules: '납입일 준수, 규칙 변경 시 합의', rate: 2, description: '꾸준한 소액 재테크 모임', inviteToken: 'inv-sdm-102' }),
    groupDefaults({ id: 103, title: '홍대 주말 친목 계모임', region: '서울 마포구', purpose: '친목', amount: 10, max: 8, months: 8, payDay: 15, ownerId: 2, members: [2], rulesTitle: '친목 모임 규칙', rules: '월 1회 정기 모임', rate: 2, description: '주말 친목과 저축을 함께', inviteToken: 'inv-hongdae-103' }),
    groupDefaults({ id: 104, title: '강남 1년 목돈 챌린지', region: '서울 강남구', purpose: '목돈 마련', amount: 35, max: 10, months: 10, payDay: 1, ownerId: 2, members: [2], rulesTitle: '챌린지 규칙', rules: '월별 납입과 수령 순서 합의', rate: 3, description: '장기 목표를 위한 목돈 챌린지', inviteToken: 'inv-gangnam-104' }),
    groupDefaults({ id: 105, title: '성수 창업 준비 모임', region: '서울 성동구', purpose: '창업', amount: 25, max: 5, months: 5, payDay: 20, ownerId: 2, members: [2], rulesTitle: '창업 준비 규칙', rules: '운영 규칙 변경 시 채팅 협의', rate: 2, description: '창업 준비 자금과 정보 공유', inviteToken: 'inv-seongsu-105' }),
  ],
  notices: [
    { id: 1, memberId: 1, adminId: null, title: '다음 납입 예정', body: '마포 직장인 목돈 모으기 · 20만 원', read: false, deleted: false },
    { id: 2, memberId: 1, adminId: null, title: '10월 금융 분석', body: '새로운 월별 분석 결과를 확인해 보세요.', read: false, deleted: false },
  ],
  proofs: [],
  messages: [{ id: 1, groupId: 101, memberId: 2, text: '안녕하세요! 이번 달 납입일은 5일입니다.', time: '10:15' }],
  orderRequests: [], sanctions: [], history: [], overdueCases: [], reports: [],
  journey: { step: 1 }, refs: 1000,
};

export let db = load();
function load() {
  let d; try { d = JSON.parse(localStorage.getItem(KEY)); } catch (e) { d = null; }
  d = d || structuredClone(SEED);
  d.groups = (d.groups || []).map(groupDefaults); d.overdueCases ||= []; d.reports ||= []; d.admins ||= structuredClone(SEED.admins);
  return d;
}

/* ---- 변경 알림: React 스토어가 구독해서 화면을 다시 그립니다 ---- */
const listeners = new Set();
let version = 0;
export const getVersion = () => version;   // useSyncExternalStore 스냅샷: 구독 전 변경도 놓치지 않음
export function subscribe(fn) { listeners.add(fn); return () => listeners.delete(fn); }
export function save() { localStorage.setItem(KEY, JSON.stringify(db)); version++; listeners.forEach(fn => fn()); }
export function reset() { db = structuredClone(SEED); save(); }

/* ---- 공용 헬퍼 ---- */
export const uid = () => Date.now() + Math.floor(Math.random() * 100000);
export const today = () => new Date().toISOString().slice(0, 10);
export const nowText = () => new Date().toLocaleString('ko-KR');
export const fail = msg => { throw new Error(msg); };
export const isAdmin = () => db.session?.type === 'ADMIN';
export const me = () => (db.session && !isAdmin()) ? db.session.id : null;
export const currentUser = () => !db.session ? null : isAdmin() ? db.admins.find(a => a.id === db.session.id) : db.members.find(m => m.id === db.session.id);
export const memberName = id => db.members.find(m => m.id === id)?.name || '회원';
export const group = id => db.groups.find(g => g.id === Number(id)) || null;
export const isEnded = g => g?.status === '해산 완료';
export const joined = g => !!g?.members.includes(me());
export const activeJoined = g => joined(g) && !isEnded(g);
export const leader = g => !!g && g.ownerId === me();
export const groupVisible = g => g.visibility !== 'PRIVATE' && !isEnded(g);
export function notice(memberId, title, body) { db.notices.unshift({ id: uid(), memberId, adminId: null, title, body, read: false, deleted: false }); }
export function noticeAdmin(title, body) { db.admins.forEach(a => db.notices.unshift({ id: uid(), memberId: null, adminId: a.id, title, body, read: false, deleted: false })); }
export function audit(type, groupId, description) { db.history.unshift({ id: uid(), date: nowText(), type, groupId, description, actor: isAdmin() ? 'ADMIN' : 'MEMBER', actorId: db.session?.id }); }
export function groupLog(g, msg) { (g.log ||= []).unshift({ at: nowText(), msg }); g.log = g.log.slice(0, 60); }
export const proofPeriod = x => x.period || (x.date || '').slice(0, 7) || '귀속 월 미지정';
export const currentPeriod = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`; };
export const paidThisPeriod = (g, id, period) => db.proofs.some(p => p.groupId === g.id && p.memberId === id && proofPeriod(p) === period && p.status === '납입 확인');
export const paidAny = (g, id) => db.proofs.some(p => p.groupId === g.id && p.memberId === id && p.status === '납입 확인');
export function inviteDigits() { const a = new Uint32Array(1); crypto.getRandomValues(a); return String(a[0] % 1000000).padStart(6, '0'); }
export function inviteToken() { const a = new Uint32Array(2); crypto.getRandomValues(a); return 'inv-' + a[0].toString(36) + a[1].toString(36); }
export function ensureJoinCode(g) { if (!g.joinCode) g.joinCode = { code: inviteDigits(), attempts: {}, verified: {} }; g.joinCode.attempts ||= {}; g.joinCode.verified ||= {}; return g.joinCode; }
export const sanctioned = id => db.sanctions.some(s => s.userId === id && s.active);
