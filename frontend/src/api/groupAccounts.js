
/* F19 — 모임 계좌 조회 및 소유 인증 */

import { apiFetch } from './client';

// 유효한 모임 ID 검증
function validateGroupId(groupId) {
  const id = String(groupId ?? '');

  if (
    !/^[1-9]\d*$/.test(id) ||
    !Number.isSafeInteger(Number(id))
  ) {
    throw new Error('유효하지 않은 모임 ID입니다.');
  }

  return id;
}

// 모임 계좌 API 기본 경로
function accountPath(groupId) {
  const id = validateGroupId(groupId);

  return `/api/v1/groups/${encodeURIComponent(id)}/accounts`;
}

// =========================================================
// F19: 현재 모임 계좌 조회
// GET /api/v1/groups/{groupId}/accounts/current
// =========================================================

export async function fetchCurrentAccount(groupId) {
  return apiFetch(`${accountPath(groupId)}/current`, {
    method: 'GET',
    cache: 'no-store'
  });
}

// =========================================================
// F19: 현재 모임 계좌 소유 인증
// POST /api/v1/groups/{groupId}/accounts/verify
// =========================================================

export async function verifyCurrentAccount(groupId) {
  return apiFetch(`${accountPath(groupId)}/verify`, {
    method: 'POST',
    cache: 'no-store'
  });
}
