/* F28 곗돈 수령 완료 표시 패널 (운영 홈 /operations 연동용)
   - 모임장만 수령 완료 표시 가능 (일반 회원은 조회 전용)
   - 수령 순서 미확정(orderConfirmed=false) 모임에서는 표시 버튼을 노출하지 않는다
   - 수령 완료자는 이후 F29 일반 탈퇴가 차단된다
*/
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchPayoutReceipts, markPayoutReceived } from '../api/groupOperations';
import { useApp } from '../common/store';
import { LoadState } from './shared';

export default function GroupPayoutReceiptPanel({ groupId, onMarked }) {
  const nav = useNavigate();
  const { toast } = useApp();

  const [state, setState] = useState({
    status: 'loading',
    data: null,
    error: '',
  });
  const [processingId, setProcessingId] = useState(null);
  const [actionError, setActionError] = useState('');

  const load = useCallback(async () => {
    setState(prev => ({ ...prev, status: 'loading', error: '' }));
    try {
      const data = await fetchPayoutReceipts(groupId);
      setState({ status: 'ready', data, error: '' });
    } catch (err) {
      setState({
        status: 'error',
        data: null,
        error: err?.message || '곗돈 수령 현황을 불러오지 못했어요.',
      });
    }
  }, [groupId]);

  useEffect(() => {
    let alive = true;
    setState({ status: 'loading', data: null, error: '' });
    setActionError('');
    fetchPayoutReceipts(groupId)
      .then(data => {
        if (alive) setState({ status: 'ready', data, error: '' });
      })
      .catch(err => {
        if (alive) {
          setState({
            status: 'error',
            data: null,
            error: err?.message || '곗돈 수령 현황을 불러오지 못했어요.',
          });
        }
      });
    return () => {
      alive = false;
    };
  }, [groupId]);

  const handleMarkReceived = async member => {
    if (!state.data?.leader || !state.data?.orderConfirmed || processingId !== null) {
      return;
    }
    const label = member.memberName || `회원 #${member.memberId}`;
    const roundText = member.confirmedRoundNo ? ` (${member.confirmedRoundNo}회차)` : '';
    const confirmed = window.confirm(
      `${label}${roundText}님의 곗돈 수령 완료를 표시할까요?\n\n` +
        '실제 송금은 발생하지 않는 기록용 표시입니다.\n' +
        '표시 후에는 취소할 수 없으며, 해당 회원의 일반 탈퇴가 차단됩니다.'
    );
    if (!confirmed) return;

    setProcessingId(member.memberId);
    setActionError('');
    try {
      await markPayoutReceived(groupId, member.memberId);
      toast(`${label}님의 곗돈 수령 완료를 기록했어요.`);
      await load();
      if (typeof onMarked === 'function') {
        onMarked();
      }
    } catch (err) {
      setActionError(err?.message || '곗돈 수령 완료 표시에 실패했어요.');
    } finally {
      setProcessingId(null);
    }
  };

  const data = state.data;
  const members = data?.members || [];
  const isLeader = !!data?.leader;
  const isOrderConfirmed = !!data?.orderConfirmed;
  const isDissolved = data?.groupStatus === 'DISSOLVED';
  const receivedCount = members.filter(m => m.payoutReceived).length;

  return (
    <div className="policy-panel" style={{ marginTop: 16 }}>
      <div className="item-head">
        <h3>곗돈 수령 완료 표시</h3>
        {state.status === 'ready' && (
          <span className={`pill ${isOrderConfirmed ? '' : 'warn'}`}>
            {isOrderConfirmed
              ? `순서 확정 · 수령 ${receivedCount}/${members.length}명`
              : '수령 순서 미확정'}
          </span>
        )}
      </div>

      <p className="muted mini" style={{ marginTop: 6 }}>
        실제 송금은 발생하지 않는 기록용 표시입니다. 수령 완료로 표시된 회원은 이후 일반 탈퇴가 차단됩니다.
      </p>

      <LoadState
        status={state.status}
        error={state.error}
        onRetry={load}
        loadingText="곗돈 수령 현황을 불러오는 중이에요."
      >
        {!isOrderConfirmed ? (
          <div className="policy-lock" style={{ marginTop: 10 }}>
            <p style={{ margin: 0 }}>
              수령 순서가 아직 확정되지 않은 모임이에요. 모임 절차에서 모든 회원의 수령 순서를 확정한 뒤 수령 완료를 표시할 수 있어요.
            </p>
            <div className="actions" style={{ marginTop: 10 }}>
              <button
                type="button"
                className="btn small soft"
                onClick={() => nav(`/procedures?groupId=${groupId}`)}
              >
                수령 순서로 이동 →
              </button>
            </div>
          </div>
        ) : members.length === 0 ? (
          <p className="muted mini" style={{ marginTop: 10 }}>
            현재 참여 중인 회원이 없어요.
          </p>
        ) : (
          <div className="list" style={{ marginTop: 10 }}>
            {members.map(member => (
              <div className="list-item item-head" key={member.memberId}>
                <div>
                  <b>
                    {member.memberName || `회원 #${member.memberId}`}
                    {member.self && ' (나)'}
                  </b>
                  <p className="muted mini" style={{ marginTop: 4 }}>
                    {member.role === 'OWNER' ? '모임장' : '일반 멤버'}
                    {member.confirmedRoundNo != null && ` · ${member.confirmedRoundNo}회차`}
                    {member.payoutReceived && member.payoutReceivedAt && ` · 기록일시 ${member.payoutReceivedAt}`}
                  </p>
                </div>

                <div className="actions" style={{ margin: 0 }}>
                  {member.payoutReceived ? (
                    <span className="pill">수령 완료</span>
                  ) : isLeader && !isDissolved ? (
                    <button
                      type="button"
                      className="btn small soft"
                      disabled={processingId !== null}
                      onClick={() => handleMarkReceived(member)}
                    >
                      {processingId === member.memberId ? '기록 중...' : '수령 완료 표시'}
                    </button>
                  ) : (
                    <span className="muted mini">미수령</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}

        {actionError && (
          <p role="alert" style={{ color: '#c0392b', marginTop: 10 }}>
            {actionError}
          </p>
        )}
      </LoadState>
    </div>
  );
}
