/* ============================================================
   MOAVA · api/mock/db.js — 브라우저(localStorage) 가짜 DB
   기준: 테이블 정의서 v1.5. 서버 연동 후에는 이 폴더 전체가 필요 없어집니다.
   ============================================================ */
import { tokenMemberId, tokenRole, getServerMember } from '../client';

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

/** 비어 있는 금융 프로필 (값이 null 이면 "아직 입력 안 함") */
export function emptyFinance() {
  return { income: null, fixed: null, variable: null, saving: null, reserve: null, goal: null, months: 6, risk: null, consentAt: null, mlAssessment: null };
}

export const SEED = {
  members: [
    { id: 1, name: '이모아', email: 'moa@example.com', password: 'demo1234', phone: '010-1234-5678', regionSido: '서울', regionSigungu: '마포구', status: 'ACTIVE', privacyAgreedAt: '2026-10-01' },
    { id: 2, name: '김하늘', email: 'leader@example.com', password: 'demo1234', phone: '010-2222-3333', regionSido: '서울', regionSigungu: '마포구', status: 'ACTIVE', privacyAgreedAt: '2026-10-01' },
  ],
  admins: [{ id: 1, name: '관리자', email: 'admin@moava.test', password: 'admin1234' }],
  session: null,
  // 금융 프로필(F05)은 서버 API가 없어 사용자가 입력한 값을 로그인해 있는 동안만 이 브라우저에 보관한다.
  // 처음엔 비어 있고, 로그아웃·토큰 만료·비로그인 상태가 되면 clearFinance() 로 지운다.
  // TODO(MOA-69): F05 서버 API(/finance/profile) 연결 시 서버 값으로 교체
  finance: emptyFinance(),
  financeMonthly: [],   // 월별 기록은 서버(/api/v1/finance/monthly)만 사용

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

/* 로그인은 서버(JWT)만 인정한다.
   - 토큰이 없거나, 토큰의 주체 번호·역할과 세션이 다르면 세션을 지운다 (예전 목업 로그인 흔적 포함).
   - 예전 버전이 브라우저에 복사해 둔 서버 회원 정보('server:이메일', 전화번호 포함)도 지운다. */
{
  const tokenId = tokenMemberId();
  const role = tokenRole();
  const validMemberSession = db.session?.type === 'MEMBER' && db.session.source === 'SERVER' && db.session.id === tokenId && (!role || role === 'MEMBER');
  const validAdminSession = db.session?.type === 'ADMIN' && db.session.source === 'SERVER' && db.session.id === tokenId && role === 'ADMIN';
  const validServerSession = validMemberSession || validAdminSession;
  const legacyMembers = (db.members || []).some(m => String(m.id).startsWith('server:'));
  const ownerBefore = db.financeOwner;
  if (validMemberSession) ensureFinanceOwner();   // 예전 버전이 넣어 둔 SEED 금융값 비우기
  else clearFinance();                            // 일반 회원 로그인 상태가 아니면 금융 프로필을 남기지 않는다
  // 로그인 상태가 아니면 비운 금융 프로필을 항상 저장소에 반영한다
  if (!validServerSession || legacyMembers || ownerBefore !== db.financeOwner) {
    if (!validServerSession) db.session = null;
    db.members = (db.members || []).filter(m => !String(m.id).startsWith('server:'));
    localStorage.setItem(KEY, JSON.stringify(db));
  }
}

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
/** 금융 프로필(소득·지출 등)과 AI 분석 결과를 비운다. 로그아웃·토큰 만료·비로그인 상태에서 호출 → 브라우저에 남기지 않음 */
export function clearFinance() {
  db.finance = emptyFinance();
  db.financeMonthly = [];
  db.journey = { step: 1 };
  db.financeOwner = null;
}

/** 로그인한 회원이 바뀌면 이 기기에 보관된 금융 프로필을 비운다 (가짜 SEED 값을 넣지 않음).
 *  owner 앞의 'm:' 은 예전 버전이 SEED 값을 복사해 둔 기록과 구분하기 위한 표시 → 예전 기록은 한 번 비워진다. */
export function ensureFinanceOwner() {
  if (db.session?.type !== 'MEMBER') return;      // 관리자 로그인은 무시
  const owner = `m:${db.session.id}`;
  if (db.financeOwner === owner) return;          // 같은 사람이면 그대로 유지
  db.finance = emptyFinance();
  db.financeMonthly = [];
  db.journey = { step: 1 };
  db.financeOwner = owner;
}


/* ---- 공용 헬퍼 ---- */
export const uid = () => Date.now() + Math.floor(Math.random() * 100000);
export const today = () => new Date().toISOString().slice(0, 10);
export const nowText = () => new Date().toLocaleString('ko-KR');
export const fail = msg => { throw new Error(msg); };
export const isAdmin = () => db.session?.type === 'ADMIN';
export const me = () => (db.session && !isAdmin()) ? db.session.id : null;
export const currentUser = () => {
  if (!db.session) return null;
  if (db.session.source === 'SERVER') {   // 서버 로그인 회원: 번호는 JWT, 이름 등은 /api/auth/me 결과
    const m = getServerMember();
    return m ? { id: db.session.id, ...m, source: 'SERVER' } : null;
  }
  return isAdmin() ? db.admins.find(a => a.id === db.session.id) : db.members.find(m => m.id === db.session.id);
};
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
