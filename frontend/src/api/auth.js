/* F01 F02 F03 F04 — 인증·회원. 백엔드 MemberController(/api/auth/*) 연동 버전 */
import { db, save, uid, today, nowText, fail, audit, isAdmin, currentUser, activeJoined, leader, isEnded, reset } from './mock/db';

const TOKEN_KEY = 'moava_access_token';

// 다른 API 파일(groups.js, proofs.js 등)에서도 꺼내 쓸 수 있도록 export
export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const setToken = (token) => {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
};

// 백엔드(/api/auth/me 등)에서 받아온 회원 정보를 프론트 로컬 db.members에 동기화
function syncMemberToLocalDb(memberView, fallback = {}) {
  const rawId = memberView?.memberId ?? memberView?.id ?? fallback.id ?? uid();
  const id = Number.isNaN(Number(rawId)) ? rawId : Number(rawId);

  let m = db.members.find(x => String(x.id) === String(id) || (memberView?.email && x.email === memberView.email));
  const updated = {
    id,
    name: memberView?.name ?? fallback.name ?? '회원',
    email: memberView?.email ?? fallback.email ?? '',
    password: fallback.password ?? m?.password ?? '',
    phone: memberView?.phone ?? fallback.phone ?? '',
    regionSido: memberView?.regionSido ?? fallback.regionSido ?? '서울특별시',
    regionSigungu: memberView?.regionSigungu ?? fallback.regionSigungu ?? '마포구',
    status: memberView?.status ?? 'ACTIVE',
    privacyAgreedAt: m?.privacyAgreedAt ?? today(),
    termsAgreedAt: m?.termsAgreedAt ?? today(),
  };

  if (m) {
    Object.assign(m, updated);
  } else {
    m = updated;
    db.members.push(m);
  }
  return m;
}

export async function login(email, password) {
  // 1. 관리자(ADMIN) 계정은 기존처럼 mock db에서 먼저 확인
  const a = db.admins.find(x => x.email === email && x.password === password);
  if (a) {
    db.session = { type: 'ADMIN', id: a.id };
    audit('로그인', null, `${currentUser().name} (ADMIN)`);
    save();
    return db.session;
  }

  // 2. 일반 회원은 백엔드 MemberController (POST /api/auth/login) 호출
  let res;
  try {
    res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.trim(), password }),
    });
  } catch (e) {
    // 백엔드 서버가 꺼져 있을 때 기존 데모 계정 폴백이 필요하면 처리
    fail('백엔드 서버와 연결할 수 없습니다. 서버 실행 상태를 확인해 주세요.');
  }

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    fail(data?.error?.message || data?.message || '이메일 또는 비밀번호를 확인해 주세요.');
  }

  // 3. JWT 토큰 저장 (AuthResponse 필드명에 맞춰 추출)
  const token = data?.accessToken || data?.token || data?.jwt || data?.data?.accessToken;
  if (token) setToken(token);

  // 4. GET /api/auth/me 호출하여 로그인한 회원의 상세 프로필 가져오기
  let meData = data?.member || data?.user || data;
  if (token) {
    const meRes = await fetch('/api/auth/me', {
      headers: { 'Authorization': `Bearer ${token}` },
    }).catch(() => null);
    if (meRes && meRes.ok) {
      meData = await meRes.json();
    }
  }

  // 5. 기존 리액트 화면들이 currentUser()를 쓸 수 있도록 db.session & db.members 갱신
  const m = syncMemberToLocalDb(meData, { email, password });
  db.session = { type: 'MEMBER', id: m.id };
  audit('로그인', null, `${currentUser().name} (${db.session.type})`);
  save();
  return db.session;
}

export async function logout() {
  setToken(null);
  db.session = null;
  save();
}

export async function signup(f) {
  // 백엔드 MemberController (POST /api/auth/signup) 호출
  const reqBody = {
    name: f.name.trim(),
    email: f.email.trim(),
    password: f.password,
    phone: f.phone.trim(),
    regionSido: f.regionSido.trim(),
    regionSigungu: f.regionSigungu.trim(),
    termsAgreed: true,
    privacyAgreed: true,
  };

  let res;
  try {
    res = await fetch('/api/auth/signup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(reqBody),
    });
  } catch (e) {
    fail('백엔드 서버와 연결할 수 없습니다. 서버 실행 상태를 확인해 주세요.');
  }

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    fail(data?.error?.message || data?.message || '이미 등록된 이메일이거나 입력값이 올바르지 않아요.');
  }

  // 회원가입 직후 발급된 JWT 토큰 저장
  const token = data?.accessToken || data?.token || data?.jwt || data?.data?.accessToken;
  if (token) setToken(token);

  // GET /api/auth/me 로 생성된 회원의 DB PK(memberId) 조회
  let meData = data?.member || data?.user || data;
  if (token) {
    const meRes = await fetch('/api/auth/me', {
      headers: { 'Authorization': `Bearer ${token}` },
    }).catch(() => null);
    if (meRes && meRes.ok) {
      meData = await meRes.json();
    }
  }

  const m = syncMemberToLocalDb(meData, reqBody);
  db.session = { type: 'MEMBER', id: m.id };
  audit('회원가입', null, `${m.name} · 약관·개인정보 동의 ${today()}`);
  save();
  return db.session;
}

export async function updateAccount(f) {
  const u = currentUser();
  if (db.members.some(x => x.id !== u.id && x.email === f.email)) fail('이미 사용 중인 이메일입니다.');
  for (const k of ['name', 'email', 'phone', 'regionSido', 'regionSigungu']) u[k] = String(f[k]).trim();
  audit('내 정보 수정', null, u.name);
  save();
}

/** 탈퇴 = 익명화(STATUS WITHDRAWN · EMAIL NULL). 모임장·참여 중 모임 있으면 불가 */
export async function withdrawAccount() {
  if (db.groups.some(g => leader(g) && !isEnded(g))) fail('모임장인 모임이 있습니다. 승계 또는 해산 후 탈퇴할 수 있어요.');
  if (db.groups.some(activeJoined)) fail('참여 중인 모임이 있습니다. 중도 탈퇴 절차를 먼저 완료해 주세요.');
  Object.assign(currentUser(), { status: 'WITHDRAWN', withdrawnAt: nowText(), email: null, phone: '', name: '탈퇴회원' });
  setToken(null);
  audit('회원 탈퇴', null, '익명화 처리');
  db.session = null;
  save();
}

export async function resetDemo() {
  setToken(null);
  reset();
}
export const session = () => db.session;
export { isAdmin, currentUser };