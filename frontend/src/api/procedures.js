/* S10b / A04 모임 절차 서버 API 연동 (group/procedures 18개 엔드포인트)
   - 수령 순서: /api/v1/groups/{groupId}/payout-requests
   - 중도 탈퇴·정산·동의 면제: /api/v1/groups/{groupId}/withdrawals
   - 계모임 해산: /api/v1/groups/{groupId}/dissolution, /api/v1/admin/dissolutions */
import { apiFetch } from './client';

const gid = v => {
  const n = Number(v);
  if (!Number.isSafeInteger(n) || n <= 0) throw new Error('올바른 모임 번호를 선택해 주세요.');
  return n;
};

/* ============================================================
   1. 수령 순서 (PayoutOrderController)
   ============================================================ */

/** 신청·동의 현황 목록 조회 (AI 추천 회차 포함) */
export async function fetchPayoutRequests(groupId) {
  const data = await apiFetch(`/api/v1/groups/${gid(groupId)}/payout-requests`, { method: 'GET' });
  return Array.isArray(data) ? data : [];
}

/** 회원의 수령 순서 신청 및 수정 */
export async function requestPayoutOrder(groupId, { roundNo, reason }) {
  const round = Number(roundNo);
  if (!Number.isInteger(round) || round < 1) throw new Error('희망 수령 회차를 올바르게 입력해 주세요.');
  if (!String(reason || '').trim()) throw new Error('신청 사유를 입력해 주세요.');
  return apiFetch(`/api/v1/groups/${gid(groupId)}/payout-requests`, {
    method: 'POST',
    body: JSON.stringify({ roundNo: round, reason: String(reason).trim() }),
  });
}

/** 모임장의 동의 요청 발송 (CONSENT_PENDING 전환) */
export async function requestPayoutConsent(groupId, requestId) {
  return apiFetch(`/api/v1/groups/${gid(groupId)}/payout-requests/${Number(requestId)}/request-consent`, {
    method: 'POST',
  });
}

/** 모임원의 수령 순서 변경 동의/거부 (agreedYn: 'Y' | 'N') */
export async function submitPayoutConsent(groupId, requestId, agreedYn = 'Y') {
  return apiFetch(`/api/v1/groups/${gid(groupId)}/payout-requests/${Number(requestId)}/consents`, {
    method: 'POST',
    body: JSON.stringify({ agreedYn }),
  });
}

/** 모임장의 동의 확인 후 최종 확정 (CONFIRMED) */
export async function confirmPayoutOrder(groupId, requestId) {
  return apiFetch(`/api/v1/groups/${gid(groupId)}/payout-requests/${Number(requestId)}/confirm`, {
    method: 'POST',
  });
}

/* ============================================================
   2. 중도 탈퇴 · 정산 · 동의 면제 (WithdrawalController)
   ============================================================ */

/** 탈퇴 검토 및 정산안 동의 현황 목록 조회 */
export async function fetchWithdrawals(groupId) {
  const data = await apiFetch(`/api/v1/groups/${gid(groupId)}/withdrawals`, { method: 'GET' });
  return Array.isArray(data) ? data : [];
}

/** 일반 회원의 중도 탈퇴 신청 (reasonType: 'ECONOMIC' | 'PERSONAL' | 'OTHER', specialYn: 'Y' | 'N') */
export async function requestWithdrawal(groupId, { reasonType = 'PERSONAL', detail, specialYn = 'N' }) {
  if (!String(detail || '').trim()) throw new Error('상세 탈퇴 사유를 입력해 주세요.');
  return apiFetch(`/api/v1/groups/${gid(groupId)}/withdrawals`, {
    method: 'POST',
    body: JSON.stringify({
      reasonType,
      detail: String(detail).trim(),
      specialYn: specialYn === 'Y' ? 'Y' : 'N',
    }),
  });
}

/** 모임장의 정산안 등록 및 전원 동의 요청 (settlementAmount: 원 단위 정수, +환급 / -추가 납부) */
export async function registerSettlement(groupId, withdrawalId, { settlementAmount, settlementMemo }) {
  const amount = Number(settlementAmount);
  if (!Number.isFinite(amount) || amount < -999_999_999_999 || amount > 999_999_999_999) {
    throw new Error('정산 금액(원)을 올바르게 입력해 주세요.');
  }
  const memo = String(settlementMemo || '').trim();
  if (!memo) throw new Error('정산 산출 근거(메모)를 입력해 주세요.');
  return apiFetch(`/api/v1/groups/${gid(groupId)}/withdrawals/${Number(withdrawalId)}/settlement`, {
    method: 'POST',
    body: JSON.stringify({
      settlementAmount: Math.round(amount),
      settlementMemo: memo,
    }),
  });
}

/** 모임원의 정산안 공식 동의/거부 (agreedYn: 'Y' | 'N') */
export async function submitWithdrawalConsent(groupId, withdrawalId, agreedYn = 'Y') {
  return apiFetch(`/api/v1/groups/${gid(groupId)}/withdrawals/${Number(withdrawalId)}/consents`, {
    method: 'POST',
    body: JSON.stringify({ agreedYn }),
  });
}

/** 신청자의 탈퇴 재확인 요청 */
export async function reconfirmWithdrawal(groupId, withdrawalId) {
  return apiFetch(`/api/v1/groups/${gid(groupId)}/withdrawals/${Number(withdrawalId)}/reconfirm`, {
    method: 'POST',
  });
}

/** 모임장의 전원 동의 검증 후 최종 탈퇴 승인 */
export async function approveWithdrawal(groupId, withdrawalId) {
  return apiFetch(`/api/v1/groups/${gid(groupId)}/withdrawals/${Number(withdrawalId)}/approve`, {
    method: 'POST',
  });
}

/** 탈퇴 정산안 동의 대상자 현황 조회 (회원 · 관리자 공용) */
export async function fetchWithdrawalConsentTargets(groupId, withdrawalId) {
  const data = await apiFetch(
    `/api/v1/groups/${gid(groupId)}/withdrawals/${Number(withdrawalId)}/consent-targets`,
    { method: 'GET' }
  );
  return Array.isArray(data) ? data : [];
}

/** 관리자의 정산안 동의 의무 면제 처리 (ADMIN 전용) */
export async function exemptWithdrawalConsentTarget(groupId, withdrawalId, memberId, reason) {
  if (!String(reason || '').trim()) throw new Error('동의 면제 사유를 입력해 주세요.');
  return apiFetch(
    `/api/v1/groups/${gid(groupId)}/withdrawals/${Number(withdrawalId)}/consent-targets/${Number(memberId)}/exemption`,
    {
      method: 'POST',
      body: JSON.stringify({ reason: String(reason).trim() }),
    }
  );
}

/* ============================================================
   3. 계모임 해산 (DissolutionController)
   ============================================================ */

/** 특정 모임의 해산 상태 및 상세 조회 (신청 없으면 204 → null) */
export async function fetchGroupDissolution(groupId) {
  const data = await apiFetch(`/api/v1/groups/${gid(groupId)}/dissolution`, { method: 'GET' });
  if (!data || typeof data !== 'object' || data.dissolutionRequestId == null) return null;
  return data;
}

/** 모임장의 계모임 해산 신청 */
export async function requestDissolution(groupId, reason) {
  if (!String(reason || '').trim()) throw new Error('해산 사유를 입력해 주세요.');
  return apiFetch(`/api/v1/groups/${gid(groupId)}/dissolution`, {
    method: 'POST',
    body: JSON.stringify({ reason: String(reason).trim() }),
  });
}

/** 관리자 해산 목록 조회 (status 생략 시 전체) */
export async function fetchAdminDissolutions(status) {
  const qs = status ? `?status=${encodeURIComponent(status)}` : '';
  const data = await apiFetch(`/api/v1/admin/dissolutions${qs}`, { method: 'GET' });
  return Array.isArray(data) ? data : [];
}

/** 관리자 해산 최종 승인 */
export async function approveAdminDissolution(groupId) {
  return apiFetch(`/api/v1/admin/groups/${gid(groupId)}/dissolution/approve`, {
    method: 'POST',
    body: JSON.stringify({}),
  });
}

/** 관리자 해산 신청 반려 */
export async function rejectAdminDissolution(groupId, rejectReason) {
  if (!String(rejectReason || '').trim()) throw new Error('반려 사유를 입력해 주세요.');
  return apiFetch(`/api/v1/admin/groups/${gid(groupId)}/dissolution/reject`, {
    method: 'POST',
    body: JSON.stringify({ rejectReason: String(rejectReason).trim() }),
  });
}
