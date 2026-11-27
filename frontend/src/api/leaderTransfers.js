import { apiFetch } from './client';

function validId(value, label) {
  const raw = String(value ?? '');

  const number = Number(raw);

  if (
    !/^[1-9]\d*$/.test(raw) ||
    !Number.isSafeInteger(number) ||
    number <= 0
  ) {
    throw new Error(
      `${label}가 올바르지 않습니다.`
    );
  }

  return String(number);
}

const base = groupId =>
  `/api/v1/groups/${validId(groupId, '모임 ID')}/leader-transfers`;

const item = (groupId, transferId) =>
  `${base(groupId)}/${validId(transferId, '승계 신청 ID')}`;

// 진행 중인 승계 조회
export function fetchLeaderTransfer(groupId) {
  return apiFetch(base(groupId), {
    method: 'GET',
    cache: 'no-store'
  });
}

// 승계 신청
export function createLeaderTransfer(
  groupId,
  toMemberId
) {
  return apiFetch(base(groupId), {
    method: 'POST',
    cache: 'no-store',
    body: JSON.stringify({
      toMemberId: Number(
        validId(toMemberId, '대상 회원 ID')
      )
    })
  });
}

// 승계 대상 수락·거절
export function decideLeaderTransfer(
  groupId,
  transferId,
  accepted
) {
  if (typeof accepted !== 'boolean') {
    throw new Error('수락 여부가 필요합니다.');
  }

  return apiFetch(
    `${item(groupId, transferId)}/decision`,
    {
      method: 'POST',
      cache: 'no-store',
      body: JSON.stringify({ accepted })
    }
  );
}

// 일반 회원 동의·거절
export function consentLeaderTransfer(
  groupId,
  transferId,
  agreed
) {
  if (typeof agreed !== 'boolean') {
    throw new Error('동의 여부가 필요합니다.');
  }

  return apiFetch(
    `${item(groupId, transferId)}/consents`,
    {
      method: 'POST',
      cache: 'no-store',
      body: JSON.stringify({ agreed })
    }
  );
}

// 최종 승계 확정
export function confirmLeaderTransfer(
  groupId,
  transferId
) {
  return apiFetch(
    `${item(groupId, transferId)}/confirm`,
    {
      method: 'POST',
      cache: 'no-store'
    }
  );
}

// 승계 취소
export function cancelLeaderTransfer(
  groupId,
  transferId
) {
  return apiFetch(
    `${item(groupId, transferId)}/cancel`,
    {
      method: 'POST',
      cache: 'no-store'
    }
  );
}
