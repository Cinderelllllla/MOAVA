/* F38 (A02) 관리자 계모임 관리 및 이력 감사 서버 API 클라이언트 (조회 전용) */
import { apiFetch } from './client';

/** A02 전체 계모임 목록 조회 */
export async function fetchAdminGroups() {
  const data = await apiFetch('/api/v1/admin/groups', { method: 'GET' });
  return Array.isArray(data) ? data : [];
}

/** A02 계모임 관리자 상세 패널 조회 (규칙, 가입 요청·증빙·탈퇴·미납 건수, 계좌 조회 전용) */
export async function fetchAdminGroupDetail(groupId) {
  return apiFetch(`/api/v1/admin/groups/${Number(groupId)}`, { method: 'GET' });
}

/**
 * A02 감사 이력 필터·페이징 조회 (기본 페이지당 8건)
 * @param {object} options
 * @param {number|string|null} [options.groupId] 모임 선택 (없으면 전체)
 * @param {string|null} [options.eventType] 이벤트 유형 (없으면 전체)
 * @param {'ALL'|'REVIEW'|'DONE'} [options.reviewStatus='ALL'] 검토(_REQUEST/_DETECT)/완료 구분
 * @param {boolean} [options.adminOnly=false] (v3.1) 관리자 조치만 (ACTOR_ADMIN_ID IS NOT NULL)
 * @param {number|string|null} [options.actorAdminId] (v3.1) 담당 관리자별 조회
 * @param {number} [options.page=0] 0-based 페이지 번호
 * @param {number} [options.size=8] 페이지당 건수 (기본 8건)
 */
export async function fetchAdminAuditLogs({
  groupId = null,
  eventType = '',
  reviewStatus = 'ALL',
  adminOnly = false,
  actorAdminId = null,
  page = 0,
  size = 8,
} = {}) {
  const params = new URLSearchParams();
  if (groupId !== null && groupId !== '' && groupId !== 'all') {
    params.set('groupId', String(Number(groupId)));
  }
  if (eventType && eventType !== 'all') {
    params.set('eventType', String(eventType).trim());
  }
  if (reviewStatus && reviewStatus !== 'all') {
    params.set('reviewStatus', String(reviewStatus));
  }
  if (adminOnly) {
    params.set('adminOnly', 'true');
  }
  if (actorAdminId !== null && actorAdminId !== '' && actorAdminId !== 'all') {
    params.set('actorAdminId', String(Number(actorAdminId)));
  }
  params.set('page', String(Math.max(0, Number(page) || 0)));
  params.set('size', String(Number(size) || 8));

  return apiFetch(`/api/v1/admin/audit-logs?${params.toString()}`, { method: 'GET' });
}

/** A02 감사 이력 필터 메타 옵션 (이벤트 유형 목록 + 담당 관리자 목록) */
export async function fetchAdminAuditFilterOptions() {
  const data = await apiFetch('/api/v1/admin/audit-logs/meta', { method: 'GET' });
  return {
    eventTypes: Array.isArray(data?.eventTypes) ? data.eventTypes : [],
    admins: Array.isArray(data?.admins) ? data.admins : [],
  };
}
