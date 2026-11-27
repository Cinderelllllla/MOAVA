/* MOA-96 관리자 신고 처리용 대상 모임 채팅 열람 (읽기 전용).
   - 입력창·보내기·실시간 연결 없음. REST 로 이전 메시지만 불러온다.
   - 열람할 때마다 서버에 열람 기록(AUDIT_LOG CHAT_VIEW)이 남는다. 보낸 사람 이름은 서버가 가려서 준다.
   - 본문은 JSX 로만 그린다(자동 이스케이프). dangerouslySetInnerHTML 금지. */
import { useCallback, useEffect, useState } from 'react';
import { chat } from '../../api';
import { LoadState } from '../shared';

/** "2026-11-24T10:15:00" → "11/24 10:15" */
function timeText(sentAt) {
  const d = new Date(sentAt);
  if (Number.isNaN(d.getTime())) return '';
  const hm = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  return `${d.getMonth() + 1}/${d.getDate()} ${hm}`;
}

export default function AdminReportChatViewer({ reportId }) {
  const [state, setState] = useState({ status: 'loading', items: [], hasMore: false, error: '' });
  const [olderError, setOlderError] = useState('');
  const [loadingOlder, setLoadingOlder] = useState(false);

  const load = useCallback(() => {
    let alive = true;
    setState(s => ({ ...s, status: 'loading', error: '' }));
    setOlderError('');
    chat.fetchReportChatMessages(reportId)
      .then(res => { if (alive) setState({ status: 'ready', items: res?.items || [], hasMore: Boolean(res?.hasMore), error: '' }); })
      .catch(e => { if (alive) setState({ status: 'error', items: [], hasMore: false, error: e?.message || '채팅을 불러오지 못했어요.' }); });
    return () => { alive = false; };
  }, [reportId]);

  useEffect(() => load(), [load]);

  const loadOlder = async () => {
    if (!state.items.length || loadingOlder) return;
    setLoadingOlder(true);
    setOlderError('');
    try {
      const res = await chat.fetchReportChatMessages(reportId, { beforeId: state.items[0].messageId });
      setState(s => ({ ...s, items: [...(res?.items || []), ...s.items], hasMore: Boolean(res?.hasMore) }));
    } catch (e) {
      setOlderError(e?.message || '이전 메시지를 불러오지 못했어요.');
    } finally {
      setLoadingOlder(false);
    }
  };

  return <div style={{ marginTop: 12 }}>
    <p className="info mini" style={{ marginBottom: 8 }}>
      신고 처리를 위한 읽기 전용 화면이에요. 열람 기록이 남고, 보낸 사람 이름은 일부 가려져요.
    </p>
    <LoadState status={state.status} error={state.error} onRetry={load} loadingText="대상 모임 채팅을 불러오는 중이에요.">
      {state.items.length ? (
        <div className="chatlog" style={{ height: 'auto', maxHeight: 360, overflowY: 'auto' }} aria-label="대상 모임 채팅 (읽기 전용)">
          {state.hasMore && <div style={{ textAlign: 'center' }}>
            <button type="button" className="btn small soft" onClick={loadOlder} disabled={loadingOlder}>
              {loadingOlder ? '불러오는 중…' : '이전 메시지 더 보기'}
            </button>
          </div>}
          {olderError && <p className="warning mini" role="alert">{olderError}</p>}
          {state.items.map(m => m.system
            ? <div key={m.messageId} className="chat-system" role="note">
              <span style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{m.body}</span>
              <small>{timeText(m.sentAt)}</small>
            </div>
            : <div key={m.messageId} className="bubble">
              <small>{m.senderName || '회원'} · {timeText(m.sentAt)}</small>
              <span style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{m.body}</span>
            </div>)}
        </div>
      ) : <div className="empty">아직 채팅이 없어요.</div>}
    </LoadState>
  </div>;
}
