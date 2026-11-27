/* F40 (S15 · A08) 고객센터 신고 접수 및 처리 서버 API 클라이언트 */
import { apiFetch } from './client';

export const DEFAULT_GUIDANCE_NOTICE =
  'MOAVA는 실제 계좌 동결 및 피해 구제를 수행할 수 없습니다. 사기 피해가 의심되는 경우 해당 은행 및 경찰(112) 등 공식 기관에 즉시 신고해 주세요.';

export const REPORT_CATEGORIES = [
  { code: 'ACCOUNT_CHANGE', label: '계좌 변경 의심' },
  { code: 'FUND_MISUSE', label: '자금 유용 의심' },
  { code: 'PROOF_MISMATCH', label: '거래 증빙 불일치' },
  { code: 'OTHER', label: '기타 문의' },
];

export const REPORT_STATUSES = [
  { code: 'RECEIVED', label: '접수' },
  { code: 'REVIEWING', label: '검토 중' },
  { code: 'MORE_INFO', label: '추가 자료 요청' },
  { code: 'CLOSED', label: '처리 완료' },
  { code: 'REJECTED', label: '반려' },
];

export const REPORT_STATUS_LABEL_MAP = Object.fromEntries(
  REPORT_STATUSES.map(item => [item.code, item.label])
);

export const REPORT_CATEGORY_LABEL_MAP = Object.fromEntries(
  REPORT_CATEGORIES.map(item => [item.code, item.label])
);

/** S15 회원: 신고 대상 모임 선택 목록 조회 */
export async function fetchMyReportGroups() {
  const data = await apiFetch('/api/v1/support/reports/groups', { method: 'GET' });
  return Array.isArray(data) ? data : [];
}

/** S15 회원: 고객센터 신고 접수 (상세 내용 서버 AES-256-GCM 암호화 저장) */
export async function createSupportReport({ groupId = null, category, detail }) {
  const trimmedDetail = String(detail ?? '').trim();
  if (!trimmedDetail) {
    throw new Error('신고 상세 내용을 입력해 주세요.');
  }
  const normalizedGroupId =
    groupId === '' || groupId == null ? null : Number(groupId);
  if (normalizedGroupId != null && (!Number.isSafeInteger(normalizedGroupId) || normalizedGroupId <= 0)) {
    throw new Error('올바른 대상 모임을 선택해 주세요.');
  }
  return apiFetch('/api/v1/support/reports', {
    method: 'POST',
    body: JSON.stringify({
      groupId: normalizedGroupId,
      category,
      detail: trimmedDetail,
    }),
  });
}

/** S15 회원: 나의 신고 목록 조회 (본인 건만 조회·복호화) */
export async function fetchMySupportReports() {
  const data = await apiFetch('/api/v1/support/reports', { method: 'GET' });
  return {
    items: Array.isArray(data?.items) ? data.items : [],
    totalCount: Number(data?.totalCount ?? 0),
    guidanceNotice: data?.guidanceNotice || DEFAULT_GUIDANCE_NOTICE,
  };
}

/** S15/A08 신고 단건 상세 조회 */
export async function fetchSupportReportDetail(reportId) {
  const id = Number(reportId);
  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error('올바른 신고 번호를 확인해 주세요.');
  }
  return apiFetch(`/api/v1/support/reports/${id}`, { method: 'GET' });
}

/** A08 관리자: 전체 신고 목록 + 상태 필터 조회 */
export async function fetchAdminSupportReports(status = 'ALL') {
  const params = new URLSearchParams();
  if (status && status !== 'all') {
    params.set('status', String(status));
  }
  const qs = params.toString();
  const data = await apiFetch(`/api/v1/admin/support/reports${qs ? `?${qs}` : ''}`, {
    method: 'GET',
  });
  return {
    items: Array.isArray(data?.items) ? data.items : [],
    totalCount: Number(data?.totalCount ?? 0),
    statusFilter: data?.statusFilter || 'ALL',
    statusCounts: data?.statusCounts || {},
    guidanceNotice: data?.guidanceNotice || DEFAULT_GUIDANCE_NOTICE,
  };
}

/** A08 관리자: 신고 상태 변경 (처리 메모 필수) */
export async function updateAdminSupportReportStatus(reportId, { status, adminMemo }) {
  const id = Number(reportId);
  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error('올바른 신고 번호를 확인해 주세요.');
  }
  const trimmedMemo = String(adminMemo ?? '').trim();
  if (!trimmedMemo) {
    throw new Error('처리 메모가 필요합니다');
  }
  return apiFetch(`/api/v1/admin/support/reports/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({
      status,
      adminMemo: trimmedMemo,
    }),
  });
}
