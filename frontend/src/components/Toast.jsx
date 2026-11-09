/* 전역 토스트 — store의 toastMsg만 구독하므로 메시지가 바뀔 때만 이 컴포넌트가 다시 그려집니다 */
import { useAppStore } from '../common/store';
export default function Toast() {
  const msg = useAppStore(s => s.toastMsg);
  return <div id="toast" role="status" aria-live="polite" className={msg ? 'show' : ''}>{msg}</div>;
}
