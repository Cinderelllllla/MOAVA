/* F25 계원별 납입 현황 — 운영 홈 > 모임 정보 (서버 GET /api/v1/groups/{groupId}/payments) */
import { useEffect, useState } from 'react';
import { proofs } from '../api';
import { LoadState } from './shared';

const STATUS_LABEL = {
  CONFIRMED: '납입 확인',
  PENDING: '확인 대기',
  REVISION: '보완 요청',
  NONE: '미제출',
};

export default function PaymentStatusPanel({ groupId }) {
  const [state, setState] = useState({ status: 'loading', data: null, error: '' });
  const [key, setKey] = useState(0);   // '다시 시도' 누르면 +1 → 다시 불러옴

  useEffect(() => {
    let alive = true;   // 모임을 바꾼 뒤 늦게 온 이전 응답은 무시
    setState({ status: 'loading', data: null, error: '' });
    proofs.fetchPaymentStatusFromServer(groupId)
      .then(data => { if (alive) setState({ status: 'ready', data, error: '' }); })
      .catch(e => { if (alive) setState({ status: 'error', data: null, error: e.message || '납입 현황을 불러오지 못했어요.' }); });
    return () => { alive = false; };
  }, [groupId, key]);

  if (state.status !== 'ready') {
    return <div className="policy-panel"><h3>납입 현황</h3>
      <LoadState status={state.status} error={state.error} onRetry={() => setKey(k => k + 1)} loadingText="납입 현황을 불러오는 중이에요." />
    </div>;
  }

  const { payMonth, locked, members } = state.data;
  const showWarning = members.some(m => m.warningCount != null);   // 경고 횟수는 모임장에게만 옴
  return (
    <div className="policy-panel">
      <h3>납입 현황 · {payMonth.slice(0, 4)}년 {Number(payMonth.slice(4))}월분</h3>
      {locked && <p className="policy-lock">본인 납입이 확인되기 전에는 다른 회원의 납입 현황을 볼 수 없습니다.</p>}
      <div className="tablewrap"><table className="policy-table">
        <thead><tr><th>회원</th><th>납입 상태</th>{showWarning && <th>경고</th>}</tr></thead>
        <tbody>{members.map((m, i) => (
          <tr key={i}>
            <td>{m.memberName}{m.me && ' (나)'}</td>
            <td>{STATUS_LABEL[m.status] || m.status}</td>
            {showWarning && <td>경고 {m.warningCount}회</td>}
          </tr>
        ))}</tbody>
      </table></div>
    </div>
  );
}
