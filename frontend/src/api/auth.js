
/*
 * MOAVA F01~F04 인증 및 회원 관리
 * - Spring Boot 로그인 / 회원가입
 * - JWT 저장 및 삭제
 * - React Mock DB 세션 연결
 * - 기존 Mock 기능 유지
 */

import {
  db,
  save,
  uid,
  today,
  nowText,
  fail,
  audit,
  isAdmin,
  currentUser,
  activeJoined,
  leader,
  isEnded,
  reset
} from './mock/db';

const TOKEN_KEY = 'moava_access_token';

// ========================================
// 1. Spring Boot 인증 API 공통 함수
// ========================================

async function requestAuth(path, body) {
  const response = await fetch(`/api/auth/${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body)
  });

  const result = await response.json().catch(() => null);

  if (!response.ok) {
    throw new Error(
      result?.message ||
      `인증 요청에 실패했습니다. (${response.status})`
    );
  }

  if (!result?.accessToken || !result?.user) {
    throw new Error('인증 응답에 필요한 정보가 없습니다.');
  }

  return result;
}

// ========================================
// 2. JWT 관리
// ========================================

export function getAccessToken() {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function clearAccessToken() {
  sessionStorage.removeItem(TOKEN_KEY);
}

// ========================================
// 3. 실제 서버 회원을 React 세션에 연결
// ========================================

function connectServerSession(result) {
  const user = result.user;

  if (!user?.email) {
    throw new Error('서버 회원 이메일 정보가 없습니다.');
  }

  // 프론트엔드에서만 사용하는 임시 식별자
  // 실제 Oracle MEMBER_ID가 아님
  const localId = `server:${user.email}`;

  const existing = db.members.find(
    m => m.id === localId
  );

  const localUser = {
    id: localId,
    name: user.name,
    email: user.email,
    phone: user.phone ?? '',
    regionSido: user.regionSido ?? '',
    regionSigungu: user.regionSigungu ?? '',
    status: user.status ?? 'ACTIVE',
    source: 'SERVER'
  };

  if (existing) {
    Object.assign(existing, localUser);
  } else {
    db.members.push(localUser);
  }

  db.session = {
    type: 'MEMBER',
    id: localId
  };

  // 기존 Zustand 구독 화면에 변경 알림
  save();

  return db.session;
}

// ========================================
// 4. Spring Boot 회원가입
// ========================================

export async function signupWithServer(f) {
  if (!f.termsAgreed || !f.privacyAgreed) {
    throw new Error('필수 약관에 동의해 주세요.');
  }

  const result = await requestAuth('signup', {
    name: f.name.trim(),
    email: f.email.trim(),
    password: f.password,
    phone: f.phone.trim(),
    regionSido: f.regionSido.trim(),
    regionSigungu: f.regionSigungu.trim(),
    termsAgreed: f.termsAgreed,
    privacyAgreed: f.privacyAgreed
  });

  sessionStorage.setItem(
    TOKEN_KEY,
    result.accessToken
  );

  return connectServerSession(result);
}

// ========================================
// 5. Spring Boot 로그인
// ========================================

export async function loginWithServer(email, password) {
  const result = await requestAuth('login', {
    email: email.trim(),
    password
  });

  sessionStorage.setItem(
    TOKEN_KEY,
    result.accessToken
  );

  return connectServerSession(result);
}

// ========================================
// 6. 기존 Mock 로그인
// ========================================

export async function login(email, password) {
  const m = db.members.find(
    x =>
      x.email === email &&
      x.password === password &&
      x.status !== 'WITHDRAWN'
  );

  const a = !m && db.admins.find(
    x =>
      x.email === email &&
      x.password === password
  );

  if (!m && !a) {
    fail('이메일 또는 비밀번호를 확인해 주세요.');
  }

  // Mock 로그인에서는 이전 서버 JWT 제거
  clearAccessToken();

  db.session = m
    ? { type: 'MEMBER', id: m.id }
    : { type: 'ADMIN', id: a.id };

  audit(
    '로그인',
    null,
    `${currentUser().name} (${db.session.type})`
  );

  save();

  return db.session;
}

// ========================================
// 7. 로그아웃
// ========================================

export async function logout() {
  clearAccessToken();

  db.session = null;

  save();
}

// ========================================
// 8. 기존 Mock 회원가입
// ========================================

export async function signup(f) {
  if (
    db.members.some(x => x.email === f.email) ||
    db.admins.some(x => x.email === f.email)
  ) {
    fail('이미 등록된 이메일이에요.');
  }

  const m = {
    id: uid(),
    name: f.name.trim(),
    email: f.email.trim(),
    password: f.password,
    phone: f.phone.trim(),
    regionSido: f.regionSido.trim(),
    regionSigungu: f.regionSigungu.trim(),
    status: 'ACTIVE',
    privacyAgreedAt: today(),
    termsAgreedAt: today()
  };

  db.members.push(m);

  clearAccessToken();

  db.session = {
    type: 'MEMBER',
    id: m.id
  };

  audit(
    '회원가입',
    null,
    `${m.name} · 약관·개인정보 동의 ${today()}`
  );

  save();

  return db.session;
}

// ========================================
// 9. 기존 Mock 회원정보 수정
// ========================================

export async function updateAccount(f) {
  const u = currentUser();

  if (!u) {
    fail('로그인이 필요합니다.');
  }

  if (u.source === 'SERVER') {
    fail('서버 회원정보 수정 API 연결이 필요합니다.');
  }

  if (
    db.members.some(
      x => x.id !== u.id && x.email === f.email
    )
  ) {
    fail('이미 사용 중인 이메일입니다.');
  }

  for (const k of [
    'name',
    'email',
    'phone',
    'regionSido',
    'regionSigungu'
  ]) {
    u[k] = String(f[k]).trim();
  }

  audit('내 정보 수정', null, u.name);

  save();
}

// ========================================
// 10. 기존 Mock 회원 탈퇴
// ========================================

export async function withdrawAccount() {
  const u = currentUser();

  if (!u) {
    fail('로그인이 필요합니다.');
  }

  if (u.source === 'SERVER') {
    fail('서버 회원탈퇴 API 연결이 필요합니다.');
  }

  if (
    db.groups.some(
      g => leader(g) && !isEnded(g)
    )
  ) {
    fail(
      '모임장인 모임이 있습니다. 승계 또는 해산 후 탈퇴할 수 있어요.'
    );
  }

  if (db.groups.some(activeJoined)) {
    fail(
      '참여 중인 모임이 있습니다. 중도 탈퇴 절차를 먼저 완료해 주세요.'
    );
  }

  Object.assign(u, {
    status: 'WITHDRAWN',
    withdrawnAt: nowText(),
    email: null,
    phone: '',
    name: '탈퇴회원'
  });

  audit('회원 탈퇴', null, '익명화 처리');

  clearAccessToken();

  db.session = null;

  save();
}

// ========================================
// 11. Mock 초기화
// ========================================

export async function resetDemo() {
  clearAccessToken();
  reset();
}

// ========================================
// 12. 세션 및 회원정보 조회
// ========================================

export const session = () => db.session;

export {
  isAdmin,
  currentUser
};
