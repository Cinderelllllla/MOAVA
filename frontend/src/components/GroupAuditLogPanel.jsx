/* F33 회원용 모임 운영 기록 (감사 로그) 조회 패널
   - 해당 모임의 모임장·ACTIVE 참여 회원 조회 전용
   - append-only 감사 로그이므로 수정·삭제 버튼이나 API 가 없다
   - 이벤트 유형 필터 + 8건 단위 페이징
*/
import { useCallback, useEffect, useState } from 'react';
import {
  fetchGroupAuditLogs,
  formatAuditEventLabel,
} from '../api/groupOperations';
import { Card, Empty } from './ui';
import { LoadState } from './shared';

const PAGE_SIZE = 8;

const ACTOR_TYPE_LABEL = {
  MEMBER: '모임원',
  ADMIN: '관리자',
  SYSTEM: '시스템',
};

function buildPageNumbers(currentPage, totalPages) {
  if (totalPages <= 0) return [];
  if (totalPages === 1) return [0];
  const windowSize = 5;
  let start = Math.max(0, currentPage - Math.floor(windowSize / 2));
  let end = Math.min(totalPages - 1, start + windowSize - 1);
  start = Math.max(0, end - windowSize + 1);
  const nums = [];
  for (let i = start; i <= end; i += 1) nums.push(i);
  return nums;
}

export default function GroupAuditLogPanel({ groupId, refreshKey = 0 }) {
  const [eventType, setEventType] = useState('ALL');
  const [page, setPage] = useState(0);
  const [manualReload, setManualReload] = useState(0);
  const [state, setState] = useState({
    status: 'loading',
    data: null,
    error: '',
  });

  // 모임이 바뀌면 필터와 페이지 초기화
  useEffect(() => {
    setEventType('ALL');
    setPage(0);
  }, [groupId]);

  const reload = useCallback(() => {
    setManualReload(k => k + 1);
  }, []);

  useEffect(() => {
    let alive = true;
    setState(prev => ({ ...prev, status: 'loading', error: '' }));
    fetchGroupAuditLogs(groupId, {
      eventType: eventType === 'ALL' ? '' : eventType,
      page,
      size: PAGE_SIZE,
    })
      .then(data => {
        if (alive) setState({ status: 'ready', data, error: '' });
      })
      .catch(err => {
        if (alive) {
          setState({
            status: 'error',
            data: null,
            error: err?.message || '모임 운영 기록을 불러오지 못했어요.',
          });
        }
      });
    return () => {
      alive = false;
    };
  }, [groupId, eventType, page, refreshKey, manualReload]);

  const data = state.data;
  const items = data?.items || [];
  const eventTypes = data?.eventTypes || [];
  const totalCount = data?.totalCount ?? 0;
  const totalPages = data?.totalPages ?? 0;
  const pageNumbers = buildPageNumbers(page, totalPages);

  const handleFilterChange = e => {
    setEventType(e.target.value);
    setPage(0);
  };

  return (
    <Card>
      <div className="item-head">
        <h2>모임 운영 기록 (감사 로그)</h2>
        <span className="pill">
          {state.status === 'ready' ? `총 ${totalCount}건 · 수정·삭제 불가` : '조회 전용'}
        </span>
      </div>

      <p className="muted mini" style={{ marginTop: 6 }}>
        모임 개설, 가입, 계좌 변경, 납입 증빙, 수령 순서·완료, 탈퇴·경고·규칙 변경 등 모든 운영 이력이 자동으로 보존됩니다.
      </p>

      <div
        className="actions"
        style={{
          marginTop: 12,
          marginBottom: 12,
          alignItems: 'center',
          flexWrap: 'wrap',
        }}
      >
        <div className="field" style={{ marginBottom: 0, minWidth: 220 }}>
          <label htmlFor={`group-audit-filter-${groupId}`}>이벤트 유형</label>
          <select
            id={`group-audit-filter-${groupId}`}
            value={eventType}
            onChange={handleFilterChange}
          >
            <option value="ALL">전체 이벤트</option>
            {eventTypes.map(code => (
              <option key={code} value={code}>
                {formatAuditEventLabel(code)} ({code})
              </option>
            ))}
          </select>
        </div>

        <button
          type="button"
          className="btn small soft"
          style={{ alignSelf: 'flex-end' }}
          disabled={state.status === 'loading'}
          onClick={reload}
        >
          새로고침
        </button>
      </div>

      <LoadState
        status={state.status}
        error={state.error}
        onRetry={reload}
        loadingText="모임 운영 기록을 불러오는 중이에요."
      >
        {items.length === 0 ? (
          <Empty>표시할 모임 운영 기록이 없어요.</Empty>
        ) : (
          <>
            <div className="tablewrap">
              <table className="policy-table">
                <thead>
                  <tr>
                    <th>발생 시각</th>
                    <th>이벤트</th>
                    <th>행위자</th>
                    <th>대상</th>
                    <th>내용</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map(item => (
                    <tr key={item.auditLogId}>
                      <td className="muted mini" style={{ whiteSpace: 'nowrap' }}>
                        {item.createdAt || '-'}
                      </td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        <span className="pill">{formatAuditEventLabel(item.eventType)}</span>
                      </td>
                      <td style={{ whiteSpace: 'nowrap' }}>
                        {item.actorLabel || '-'}{' '}
                        <span className="muted mini">
                          ({ACTOR_TYPE_LABEL[item.actorType] || item.actorType})
                        </span>
                      </td>
                      <td className="muted mini" style={{ whiteSpace: 'nowrap' }}>
                        {item.targetMemberLabel || '-'}
                      </td>
                      <td>{item.description || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <div
                className="actions"
                style={{
                  marginTop: 14,
                  justifyContent: 'center',
                  alignItems: 'center',
                }}
              >
                <button
                  type="button"
                  className="btn small soft"
                  disabled={!data?.hasPrev}
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                >
                  이전
                </button>
                {pageNumbers.map(num => (
                  <button
                    key={num}
                    type="button"
                    className={`btn small ${num === page ? 'primary' : 'soft'}`}
                    onClick={() => setPage(num)}
                  >
                    {num + 1}
                  </button>
                ))}
                <button
                  type="button"
                  className="btn small soft"
                  disabled={!data?.hasNext}
                  onClick={() => setPage(p => p + 1)}
                >
                  다음
                </button>
              </div>
            )}
          </>
        )}
      </LoadState>
    </Card>
  );
}
