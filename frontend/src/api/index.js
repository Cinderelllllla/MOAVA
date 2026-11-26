/* API 진입점 — 화면은 이 파일만 import 합니다.
   백엔드 연동 시: ./mock 구현을 fetch 기반으로 바꿔도 화면 코드는 그대로입니다. */
export * as auth from './auth';
export * as finance from './finance';
export * as groups from './groups';
export * as procedures from './procedures';
export * as proofs from './proofs';
export * as admin from './admin';
export * as chat from './chat';   // F34 모임별 실시간 채팅 (MOA-78)
export * as openbanking from './openbanking'; // 오픈뱅킹(모의 원장) + CODEF 실계좌인증
export { db, subscribe, getVersion, me, isAdmin, currentUser, memberName, joined, leader, activeJoined, isEnded, groupVisible, proofPeriod, currentPeriod, paidAny, paidThisPeriod, ensureJoinCode } from './mock/db';

/* NotificationController (/api/v1/notifications) 연동 모듈 */
import { apiFetch } from './client';

// 1. 알림 목록 조회 (GET /api/v1/notifications?read=N&page=0&size=20)
export async function fetchNotifications({ read, page = 0, size = 50 } = {}) {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (read) params.set('read', read);
    return apiFetch(`/api/v1/notifications?${params.toString()}`, { method: 'GET' });
}

// 2. 안 읽은 알림 개수 조회 (GET /api/v1/notifications/unread-count)
export async function fetchUnreadNotificationCount() {
    const data = await apiFetch('/api/v1/notifications/unread-count', { method: 'GET' });
    return data?.count ?? 0;
}

// 3. 단건 읽음 처리 (PATCH /api/v1/notifications/{id}/read)
export async function markNotificationRead(notificationId) {
    await apiFetch(`/api/v1/notifications/${notificationId}/read`, { method: 'PATCH' });
}

// 4. 전체 읽음 처리 (POST /api/v1/notifications/read-all)
export async function markAllNotificationsRead() {
    await apiFetch('/api/v1/notifications/read-all', { method: 'POST' });
}

// 5. 선택 알림 삭제/숨김 (POST /api/v1/notifications/hide)
export async function hideNotifications(ids = []) {
    await apiFetch('/api/v1/notifications/hide', {
        method: 'POST',
        body: JSON.stringify({ ids: ids.map(Number) }),
    });
}