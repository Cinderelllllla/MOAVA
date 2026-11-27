/*
 * F20 — 모임 계좌 변경 API
 * F19 groupAccounts.js와 분리
 */

import { apiFetch } from './client';

function validId(value, label) {
  const id = String(value ?? '');

  if (
    !/^[1-9]\d*$/.test(id) ||
    !Number.isSafeInteger(Number(id))
  ) {
    throw new Error(`${label}가 올바르지 않습니다.`);
  }

  return id;
}

function changePath(groupId, accountId) {
  const group = validId(groupId, '모임 ID');

  const base =
    `/api/v1/groups/${group}/account-changes`;

  if (accountId == null) {
    return base;
  }

  return `${base}/${validId(accountId, '계좌 변경 신청 ID')}`;
}

// F20: 변경 신청 계좌의 회원 동의 현황
export async function fetchAccountChangeConsents(
  groupId,
  accountId
) {
  const response = await apiFetch(
    `${changePath(groupId)}/consents`,
    {
      method: 'GET',
      cache: 'no-store'
    }
  );

  // 모임의 현재 PENDING 신청이
  // 화면에서 조회하려는 신청과 같은지 검증
  if (
    !response ||
    String(response.groupId) !== String(groupId) ||
    String(response.gyeAccountId) !== String(accountId)
  ) {
    throw new Error(
      '해당 신청은 현재 진행 중인 계좌 변경 신청이 아닙니다.'
    );
  }

  return response;
}

// F20: 계좌 변경 최종 준비 상태
export async function fetchAccountChangeReadiness(
  groupId,
  accountId
) {
  return apiFetch(
    `${changePath(groupId, accountId)}/readiness`,
    {
      method: 'GET',
      cache: 'no-store'
    }
  );
}

// F20: 기존·신규 모의 계좌 연결 확인
export async function fetchAccountChangeMockLinks(
  groupId,
  accountId
) {
  return apiFetch(
    `${changePath(groupId, accountId)}/mock-links`,
    {
      method: 'GET',
      cache: 'no-store'
    }
  );
}

// F20: 변경 신청 계좌의 소유 인증
// 실제 연결은 인증 제공자 준비 후 진행
export async function verifyPendingAccount(
  groupId,
  accountId
) {
  return apiFetch(
    `${changePath(groupId, accountId)}/verify`,
    {
      method: 'POST',
      cache: 'no-store'
    }
  );
}


/*
 * F20: 신규 계좌 변경 신청
 * POST /api/v1/groups/{groupId}/account-changes
 *
 * 요청:
 * {
 *   bankName: string,
 *   accountNumber: string
 * }
 *
 * 응답: gyeAccountId를 포함한 변경 신청 결과
 */
export async function createAccountChange(
  groupId,
  { bankName, accountNumber }
) {
  return apiFetch(changePath(groupId), {
    method: 'POST',
    cache: 'no-store',
    body: JSON.stringify({
      bankName,
      accountNumber
    })
  });
}


/*
 * F20 — 계좌 변경 회원 동의·거절
 *
 * POST /api/v1/groups/{groupId}/account-changes/{accountId}/consents
 *
 * agreedYn: 'Y' = 동의, 'N' = 거절
 */
export async function submitAccountChangeConsent(
  groupId,
  accountId,
  agreedYn
) {
  if (!['Y', 'N'].includes(agreedYn)) {
    throw new Error('동의 또는 거절을 선택해 주세요.');
  }

  return apiFetch(
    `${changePath(groupId, accountId)}/consents`,
    {
      method: 'POST',
      cache: 'no-store',
      body: JSON.stringify({
      agreed: agreedYn === 'Y'
      })
    }
  );
}
