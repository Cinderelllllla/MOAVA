/* 알림 변경 이벤트 — 알림함(Misc.jsx)에서 읽음·삭제가 일어나면 발생, 상단 배지(Shell.jsx)가 개수를 다시 불러온다 */
export const NOTIFICATIONS_CHANGED = 'moava:notifications-changed';

export const notifyNotificationsChanged = () => window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED));

/** 서버가 준 linkPage가 앱 내부 경로인지 확인 ('/'로 시작, '//'·'\' 없음) */
export const isInternalLink = link =>
  typeof link === 'string' && link.startsWith('/') && !link.startsWith('//') && !link.includes('\\');
