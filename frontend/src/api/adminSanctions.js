/* F39 (A03) 플랫폼 가입 제한 설정·해제 및 경고 현황 서버 API 클라이언트 */
import { apiFetch } from './client';

/** A03 종합 조회 (제재 대상 후보 + 현재 가입 제한 목록 + 모임별 회원 경고 현황) */
export async function fetchAdminSanctionOverview() {
  const data = await apiFetch('/api/v1/admin/sanctions/overview', { method: 'GET' });
  return {
    candidates: Array.isArray(data?.candidates) ? data.candidates : [],
    bannedMembers: Array.isArray(data?.bannedMembers) ? data.bannedMembers : [],
    bannedCount: Number(data?.bannedCount ?? 0),
    warnings: Array.isArray(data?.warnings) ? data.warnings : [],
  };
}

/** A03 제재 대상 후보 회원 목록 (현재 가입 제한 중 제외) */
export async function fetchBanCandidates() {
  const data = await apiFetch('/api/v1/admin/sanctions/candidates', { method: 'GET' });
  return Array.isArray(data) ? data : [];
}

/** A03 현재 가입 제한 회원 목록 */
export async function fetchBannedMembers() {
  const data = await apiFetch('/api/v1/admin/sanctions/bans', { method: 'GET' });
  return Array.isArray(data) ? data : [];
}

/** A03 플랫폼 가입 제한 설정 (사유 필수, 중복 BAN 차단) */
export async function banMember(memberId, reason) {
  const id = Number(memberId);
  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error('가입 제한할 회원을 선택해 주세요.');
  }
  const trimmed = String(reason ?? '').trim();
  if (!trimmed) {
    throw new Error('제재 사유를 입력해 주세요.');
  }
  return apiFetch('/api/v1/admin/sanctions/bans', {
    method: 'POST',
    body: JSON.stringify({ memberId: id, reason: trimmed }),
  });
}

/** A03 플랫폼 가입 제한 해제 (이전 제재 기록 보존, SANCTION(BAN_RELEASE) 추가) */
export async function releaseMemberBan(memberId, reason = '') {
  const id = Number(memberId);
  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error('해제할 회원을 선택해 주세요.');
  }
  const trimmed = String(reason ?? '').trim();
  return apiFetch(`/api/v1/admin/sanctions/bans/${id}/release`, {
    method: 'POST',
    body: JSON.stringify(trimmed ? { reason: trimmed } : {}),
  });
}

/** A03 모임별 회원 경고 현황 (n/2회, 2회 = 강제 탈퇴 검토 대상) */
export async function fetchMemberWarnings() {
  const data = await apiFetch('/api/v1/admin/sanctions/warnings', { method: 'GET' });
  return Array.isArray(data) ? data : [];
}

/**
 * A03 경고·강제 탈퇴 이력 검색·유형 필터·페이징 조회 (기본 8건)
 * @param {object} options
 * @param {string} [options.keyword=''] 회원명 또는 모임명 검색어
 * @param {'ALL'|'WARN'|'EXPEL'} [options.type='ALL'] 처리 유형
 * @param {number} [options.page=0] 0-based 페이지 번호
 * @param {number} [options.size=8] 페이지당 건수 (기본 8건)
 */
export async function fetchSanctionHistory({
  keyword = '',
  type = 'ALL',
  page = 0,
  size = 8,
} = {}) {
  const params = new URLSearchParams();
  const trimmedKeyword = String(keyword ?? '').trim();
  if (trimmedKeyword) {
    params.set('keyword', trimmedKeyword);
  }
  if (type && type !== 'all') {
    params.set('type', String(type));
  }
  params.set('page', String(Math.max(0, Number(page) || 0)));
  params.set('size', String(Number(size) || 8));

  return apiFetch(`/api/v1/admin/sanctions/history?${params.toString()}`, { method: 'GET' });
}
