/* F28 (곗돈 수령 완료 표시) · F33 (회원용 모임 운영 기록 조회) 서버 API 클라이언트 */
import { apiFetch } from './client';

/**
 * 모임 운영 기록(AUDIT_LOG) 이벤트 유형 한글 라벨표 (F33 회원 조회 · F38 관리자 조회 공용 기준).
 */
export const AUDIT_EVENT_LABELS = {
  GROUP_CREATE: '모임 개설',
  JOIN_REQUEST: '가입 신청',
  JOIN_APPROVE: '가입 승인',
  JOIN_REJECT: '가입 거절',
  JOIN_EXPIRE: '가입 신청 만료',
  JOIN_CODE_VERIFY: '인증코드 확인',
  JOIN_CODE_LOCK: '인증코드 잠금',
  JOIN_CODE_REISSUE: '인증코드 재발급',
  ACCOUNT_REGISTER: '계좌 등록',
  ACCOUNT_CHANGE: '계좌 변경',
  ACCOUNT_VERIFY: '계좌 소유 확인',
  PROOF_UPLOAD: '증빙 업로드',
  PROOF_RESUBMIT: '증빙 재제출',
  PROOF_CONFIRM: '납입 확인',
  PROOF_REVISION: '증빙 보완 요청',
  REF_ASSIGN: '참조번호 지정',
  REF_CORRECT: '참조번호 정정',
  ORDER_REQUEST: '수령 회차 신청',
  ORDER_CONSENT_REQ: '수령 순서 동의 요청',
  ORDER_CONSENT: '수령 순서 동의 응답',
  ORDER_CONFIRM: '수령 순서 확정',
  PAYOUT_RECEIVED: '곗돈 수령 완료',
  WITHDRAW_REQUEST: '중도 탈퇴 신청',
  SETTLEMENT_SET: '정산안 등록',
  SETTLEMENT_CONSENT: '정산안 동의 응답',
  WITHDRAW_RECONFIRM: '탈퇴 재확인 요청',
  WITHDRAW_APPROVE: '중도 탈퇴 승인',
  WITHDRAW_CONSENT_EXEMPT: '탈퇴 동의 면제',
  WARN: '회원 경고',
  EXPEL: '강제 탈퇴',
  RULE_UPDATE: '운영 규칙 수정',
  DISSOLVE_REQUEST: '해산 신청',
  DISSOLVE_APPROVE: '해산 최종 승인',
  DISSOLVE_REJECT: '해산 신청 반려',
  LEADER_TRANSFER: '모임장 승계',
  VISIBILITY_CHANGE: '공개 여부 변경',
  OVERDUE_DETECT: '미납 자동 감지',
  OVERDUE_WARN: '미납 경고 알림',
  CHAT_RESTRICT: '채팅 제한',
  CHAT_RELEASE: '채팅 제한 해제',
  ORDER_REVIEW: '수령 후순위 검토',
  BAN: '플랫폼 가입 제한',
  BAN_RELEASE: '가입 제한 해제',
  REPORT_RECEIVED: '신고 접수',
  REPORT_STATUS: '신고 상태 변경',
  CHAT_VIEW: '관리자 채팅 열람',
};

export function formatAuditEventLabel(code) {
  if (!code) return '-';
  return AUDIT_EVENT_LABELS[code] || code;
}

function requireValidId(value, label) {
  const num = Number(value);
  if (!Number.isSafeInteger(num) || num <= 0) {
    throw new Error(`올바르지 않은 ${label}입니다.`);
  }
  return num;
}

/**
 * F28 모임 곗돈 수령 완료 현황 조회
 * GET /api/v1/groups/{groupId}/payout-receipts
 */
export async function fetchPayoutReceipts(groupId) {
  const gid = requireValidId(groupId, '모임 번호');
  return apiFetch(`/api/v1/groups/${gid}/payout-receipts`, { method: 'GET' });
}

/**
 * F28 곗돈 수령 완료 표시 (모임장 전용, 수령 순서 확정 후)
 * POST /api/v1/groups/{groupId}/members/{memberId}/payout-received
 */
export async function markPayoutReceived(groupId, memberId) {
  const gid = requireValidId(groupId, '모임 번호');
  const mid = requireValidId(memberId, '회원 번호');
  return apiFetch(`/api/v1/groups/${gid}/members/${mid}/payout-received`, {
    method: 'POST',
  });
}

/**
 * F33 회원용 모임 운영 기록(감사 로그) 페이징 조회 (조회 전용, 삭제·수정 API 없음)
 * GET /api/v1/groups/{groupId}/audit-logs?eventType=...&page=0&size=8
 */
export async function fetchGroupAuditLogs(
  groupId,
  { eventType = '', page = 0, size = 8 } = {}
) {
  const gid = requireValidId(groupId, '모임 번호');
  const params = new URLSearchParams();
  if (eventType && eventType !== 'ALL' && eventType !== 'all') {
    params.set('eventType', String(eventType).trim());
  }
  params.set('page', String(Math.max(0, Number(page) || 0)));
  params.set('size', String(Number(size) || 8));
  return apiFetch(`/api/v1/groups/${gid}/audit-logs?${params.toString()}`, {
    method: 'GET',
  });
}
