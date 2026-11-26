
/*
 * MOAVA F01~F04 인증 및 회원 관리
 * - Spring Boot 로그인 / 회원가입 (JWT)
 * - 회원 번호는 JWT 안의 MEMBER_ID, 이름 등은 /api/auth/me 결과를 쓴다
 * - 목업 로그인은 쓰지 않는다 (토큰이 없으면 로그아웃 상태)
 */

import {
  db,
  save,
  nowText,
  fail,
  audit,
  isAdmin,
  currentUser,
  activeJoined,
  leader,
  isEnded,
  reset,
  ensureFinanceOwner,
  clearFinance
} from './mock/db';
import {
  TOKEN_KEY,
  getToken,
  tokenMemberId,
  tokenRole,
  setServerMember,
  apiFetch,
  friendlyError,
  onUnauthorized,
  NETWORK_ERROR
} from './client';
import { encodeRegionForServer } from '../common/regions';

// ========================================
// 1. Spring Boot 인증 API 공통 함수
// ========================================

async function requestAuth(path, body) {
  let response;
  try {
    response = await fetch(`/api/auth/${path}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(body)
    });
  } catch {
    throw new Error(NETWORK_ERROR);
  }

  const result = await response.json().catch(() => null);

  if (!response.ok) {
    throw new Error(friendlyError(response.status, result));
  }

  if (!result?.accessToken || !result?.user) {
    throw new Error('로그인 처리 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.');
  }

  return result;
}

// ========================================
// 2. JWT 관리 (토큰은 client.js 의 sessionStorage 한 곳에서만 읽고 쓴다)
// ========================================

export const getAccessToken = getToken;
export const getAccessTokenMemberId = tokenMemberId;

export function clearAccessToken() {
  sessionStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(TOKEN_KEY);   // 예전 버전이 남긴 토큰도 함께 정리
  setServerMember(null);
}

// ========================================
// 3. 실제 서버 회원/관리자를 화면 세션에 연결
// ========================================

function connectServerSession(result, expectedType = 'MEMBER') {
  sessionStorage.setItem(TOKEN_KEY, result.accessToken);
  const subjectId = tokenMemberId(result.accessToken);
  const role = tokenRole(result.accessToken) || expectedType;

  if (!subjectId || role !== expectedType) {
    clearAccessToken();
    throw new Error('로그인 권한 정보를 확인하지 못했어요. 다시 로그인해 주세요.');
  }

  setServerMember({ ...result.user, role });

  db.session = { type: expectedType, id: subjectId, source: 'SERVER' };
  if (expectedType === 'MEMBER') {
    ensureFinanceOwner();
  } else {
    clearFinance();
  }

  // 기존 Zustand 구독 화면에 변경 알림
  save();

  return db.session;
}

/** 서버에서 최신 회원 정보(/api/auth/me)를 다시 받아 화면 이름 등을 갱신한다. */
export async function refreshMe() {
  if (!getToken()) return null;
  const user = await apiFetch('/api/auth/me');
  setServerMember({ ...user, role: 'MEMBER' });
  save();
  return user;
}

// 토큰 만료(401)면 로그아웃 → Shell 이 로그인 화면으로 보낸다
onUnauthorized(() => {
  clearAccessToken();
  db.session = null;
  clearFinance();   // 금융 프로필을 브라우저에 남기지 않음
  save();
});

// ========================================
// 4. Spring Boot 회원가입
// ========================================

export async function signupWithServer(f) {
  if (!f.termsAgreed || !f.privacyAgreed) {
    throw new Error('필수 약관에 동의해 주세요.');
  }

  const encodedRegion = encodeRegionForServer({
    sido: f.regionSido,
    sigungu: f.regionSigungu,
    dong: f.regionDong
  });

  if (!encodedRegion.regionSido || !encodedRegion.regionSigungu || !encodedRegion.regionDong) {
    throw new Error('활동 지역을 시/도, 시/군/구, 읍/면/동까지 선택해 주세요.');
  }

  const result = await requestAuth('signup', {
    name: f.name.trim(),
    email: f.email.trim(),
    password: f.password,
    phone: f.phone.trim(),
    regionSido: encodedRegion.regionSido,
    regionSigungu: encodedRegion.regionSigungu,
    regionDong: encodedRegion.regionDong,
    termsAgreed: f.termsAgreed,
    privacyAgreed: f.privacyAgreed
  });

  return connectServerSession(result, 'MEMBER');
}

// ========================================
// 5. Spring Boot 로그인 (회원 / 관리자)
// ========================================

export async function loginWithServer(email, password) {
  const result = await requestAuth('login', {
    email: email.trim(),
    password
  });

  return connectServerSession(result, 'MEMBER');
}

export async function loginAdminWithServer(email, password) {
  const result = await requestAuth('admin/login', {
    email: email.trim(),
    password
  });

  return connectServerSession(result, 'ADMIN');
}

// ========================================
// 6. 로그아웃
// ========================================

export async function logout() {
  clearAccessToken();

  db.session = null;
  clearFinance();   // 소득·지출 같은 금융 프로필은 로그아웃하면 지운다 (다시 로그인하면 새로 입력)

  save();
}

// ========================================
// 7. 회원정보 수정
// ========================================

export async function updateAccount(f) {
  const u = currentUser();

  if (!u) {
    fail('로그인이 필요합니다.');
  }

  if (u.source !== 'SERVER') {
    fail('서버 회원 정보만 수정할 수 있습니다.');
  }

  const encodedRegion = encodeRegionForServer({
    sido: f.regionSido,
    sigungu: f.regionSigungu,
    dong: f.regionDong
  });

  if (!encodedRegion.regionSido || !encodedRegion.regionSigungu || !encodedRegion.regionDong) {
    fail('활동 지역을 시/도, 시/군/구, 읍/면/동까지 선택해 주세요.');
  }

  const updatedUser = await apiFetch('/api/auth/me', {
    method: 'PUT',
    body: JSON.stringify({
      name: f.name.trim(),
      email: f.email.trim(),
      phone: f.phone.trim(),
      regionSido: encodedRegion.regionSido,
      regionSigungu: encodedRegion.regionSigungu,
      regionDong: encodedRegion.regionDong
    })
  });
  setServerMember(updatedUser);
  save();
  return updatedUser;
}

// ========================================
// 8. 회원 탈퇴 (서버 API 없음 → 화면에서 "준비 중")
// ========================================

export async function withdrawAccount(password) {
  const u = currentUser();

  if (!u) {
    fail('로그인이 필요합니다.');
  }

  if (u.source !== 'SERVER') {
    fail('서버 회원만 탈퇴할 수 있습니다.');
  }

  if(!password || !password.trim()){
    fail('탈퇴를 위해 비밀번호를 입력해주세요.');
  }

  await apiFetch('/api/auth/me', {
    method: 'DELETE',
    body: JSON.stringify({
      password: password
    })
  });
  clearAccessToken();
  db.session = null;
  clearFinance();

  save();

  return true;
}

// ========================================
// 9. Mock 초기화 (화면 버튼은 제거됨. 목업 데이터 정리용으로만 남김)
// ========================================

export async function resetDemo() {
  clearAccessToken();
  reset();
}

// ========================================
// 10. 세션 및 회원정보 조회
// ========================================

export const session = () => db.session;

export {
  isAdmin,
  currentUser
};
