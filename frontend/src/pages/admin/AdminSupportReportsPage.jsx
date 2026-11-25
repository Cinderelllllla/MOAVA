/* A08 관리자 고객센터 · 신고 처리 (F40 관리자 전용 · 서버 API 연동) */
import { useCallback, useEffect, useState } from 'react';
import { useApp } from '../../common/store';
import {
  DEFAULT_GUIDANCE_NOTICE,
  REPORT_STATUSES,
  fetchAdminSupportReports,
  updateAdminSupportReportStatus,
} from '../../api/supportReports';
import { PageHead, Card, Empty } from '../../components/ui';
import { LoadState } from '../../components/shared';

function statusPillKind(status) {
  switch (status) {
    case 'RECEIVED':
    case 'MORE_INFO':
      return 'warn';
    case 'CLOSED':
      return 'mine';
    case 'REJECTED':
      return 'danger';
    default:
      return '';
  }
}

export default function AdminSupportReportsPage() {
  const { run, toast } = useApp();

  const [statusFilter, setStatusFilter] = useState('ALL');
  const [reloadKey, setReloadKey] = useState(0);
  const [listState, setListState] = useState({
    status: 'loading',
    items: [],
    totalCount: 0,
    statusCounts: { ALL: 0, RECEIVED: 0, REVIEWING: 0, MORE_INFO: 0, CLOSED: 0, REJECTED: 0 },
    guidanceNotice: DEFAULT_GUIDANCE_NOTICE,
    error: '',
  });

  // 신고별 상태 변경 초안 { [reportId]: { status, memo } }
  const [drafts, setDrafts] = useState({});
  const [savingId, setSavingId] = useState(null);

  const reload = useCallback(() => setReloadKey(k => k + 1), []);

  useEffect(() => {
    let alive = true;
    setListState(prev => ({ ...prev, status: 'loading', error: '' }));

    fetchAdminSupportReports(statusFilter)
      .then(data => {
        if (!alive) return;
        setListState({
          status: 'ready',
          items: data.items,
          totalCount: data.totalCount,
          statusCounts: data.statusCounts || {},
          guidanceNotice: data.guidanceNotice || DEFAULT_GUIDANCE_NOTICE,
          error: '',
        });
        setDrafts(prev => {
          const next = { ...prev };
          data.items.forEach(item => {
            if (!next[item.reportId]) {
              next[item.reportId] = { status: item.status, memo: '' };
            }
          });
          return next;
        });
      })
      .catch(err => {
        if (!alive) return;
        setListState(prev => ({
          ...prev,
          status: 'error',
          error: err?.message || '신고 목록을 불러오지 못했어요.',
        }));
      });

    return () => { alive = false; };
  }, [statusFilter, reloadKey]);

  const setDraftField = (reportId, currentStatus, field, value) => {
    setDrafts(prev => ({
      ...prev,
      [reportId]: {
        status: prev[reportId]?.status ?? currentStatus,
        memo: prev[reportId]?.memo ?? '',
        [field]: value,
      },
    }));
  };

  const handleSaveStatus = async item => {
    const draft = drafts[item.reportId] || { status: item.status, memo: '' };
    if (draft.status === item.status) {
      toast('변경할 처리 상태를 선택해 주세요.');
      return;
    }
    if (!String(draft.memo ?? '').trim()) {
      toast('처리 메모가 필요합니다');
      return;
    }

    setSavingId(item.reportId);
    try {
      const res = await run(
        () => updateAdminSupportReportStatus(item.reportId, {
          status: draft.status,
          adminMemo: draft.memo,
        }),
        data => data?.message || '신고 처리 상태를 저장했습니다.'
      );
      if (res.ok) {
        setDrafts(prev => ({
          ...prev,
          [item.reportId]: { status: draft.status, memo: '' },
        }));
        reload();
      }
    } finally {
      setSavingId(null);
    }
  };

  const counts = listState.statusCounts || {};
  const filterTabs = [
    { code: 'ALL', label: '전체' },
    ...REPORT_STATUSES,
  ];

  return (
    <>
      <PageHead
        kicker="CUSTOMER CENTER · ADMIN"
        title="관리자 고객센터 · 신고 처리"
        sub="접수된 신고를 상태별로 조회·검토하고, 처리 메모와 함께 상태를 변경합니다. 모든 변경 이력은 AUDIT_LOG에 보존됩니다."
      >
        <button type="button" className="btn soft" onClick={reload}>새로고침</button>
      </PageHead>

      {/* 법적·안전 안내 배너 */}
      <Card>
        <div className="warning" role="note" style={{ margin: 0 }}>
          <b>중요 안내 (실제 계좌 동결·피해 구제 불가)</b>
          <p style={{ marginTop: 6, marginBottom: 0 }}>{listState.guidanceNotice}</p>
        </div>
        <p className="muted mini" style={{ marginTop: 10, marginBottom: 0 }}>
          관리자는 신고 접수 화면을 이용할 수 없으며 조회·처리만 수행합니다. 신고 상세 원문은 관리자 응답 시에만 복호화되며, 상태 변경마다 처리 메모와 담당 관리자 이력이 기록되고 신고자에게 알림이 전송됩니다.
        </p>
      </Card>

      {/* 상태 필터 + 목록 */}
      <div className="section">
        <Card>
          <div className="item-head" style={{ flexWrap: 'wrap', gap: 12 }}>
            <div>
              <h2 style={{ marginBottom: 6 }}>신고 접수 내역 · {listState.totalCount}건</h2>
              <div className="actions" style={{ margin: 0, flexWrap: 'wrap', gap: 6 }}>
                {filterTabs.map(tab => (
                  <button
                    key={tab.code}
                    type="button"
                    className={`btn small ${statusFilter === tab.code ? 'primary' : 'soft'}`}
                    onClick={() => setStatusFilter(tab.code)}
                  >
                    {tab.label} ({counts[tab.code] ?? 0})
                  </button>
                ))}
              </div>
            </div>

            <div className="field" style={{ minWidth: 200, marginBottom: 0 }}>
              <label htmlFor="admin-report-status-filter">처리 상태 필터</label>
              <select
                id="admin-report-status-filter"
                value={statusFilter}
                onChange={e => setStatusFilter(e.target.value)}
              >
                <option value="ALL">전체 ({counts.ALL ?? 0})</option>
                {REPORT_STATUSES.map(st => (
                  <option key={st.code} value={st.code}>
                    {st.label} ({counts[st.code] ?? 0})
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="divider" />

          <LoadState
            status={listState.status}
            error={listState.error}
            onRetry={reload}
            loadingText="접수된 신고 목록을 불러오는 중이에요."
          >
            {listState.items.length ? (
              <div className="list">
                {listState.items.map(item => {
                  const draft = drafts[item.reportId] || { status: item.status, memo: '' };
                  const isSaving = savingId === item.reportId;

                  return (
                    <div className="list-item" key={item.reportId} style={{ marginTop: 12 }}>
                      <div className="item-head">
                        <div>
                          <b>#{item.reportId} · {item.categoryLabel}</b>
                        </div>
                        <span className={`pill ${statusPillKind(item.status)}`}>{item.statusLabel}</span>
                      </div>

                      <p className="muted mini">
                        신고자: <b>{item.reporterName}</b> · 대상 모임: <b>{item.groupTitle}</b> · 접수일: {item.createdAt || '-'}
                        {item.updatedAt ? ` · 최근 처리: ${item.updatedAt}` : ''}
                        {item.handledByLabel ? ` (${item.handledByLabel})` : ''}
                      </p>

                      <div className="info mini" style={{ marginTop: 8, whiteSpace: 'pre-wrap' }}>
                        <b>신고 상세 내용 (복호화)</b>
                        <div style={{ marginTop: 4 }}>{item.detail}</div>
                      </div>

                      {item.adminMemo && (
                        <p className="muted mini" style={{ marginTop: 8 }}>
                          최근 처리 메모: <b>{item.adminMemo}</b>
                        </p>
                      )}

                      {/* 상태 변경 폼 (처리 메모 필수) */}
                      <div className="policy-panel" style={{ marginTop: 12 }}>
                        <div className="form-grid">
                          <div className="field" style={{ marginBottom: 0 }}>
                            <label htmlFor={`report-status-${item.reportId}`}>처리 상태 변경</label>
                            <select
                              id={`report-status-${item.reportId}`}
                              value={draft.status}
                              onChange={e => setDraftField(item.reportId, item.status, 'status', e.target.value)}
                              disabled={isSaving}
                            >
                              {REPORT_STATUSES.map(st => (
                                <option key={st.code} value={st.code}>
                                  {st.label}
                                </option>
                              ))}
                            </select>
                          </div>

                          <div className="field" style={{ marginBottom: 0 }}>
                            <label htmlFor={`report-memo-${item.reportId}`}>처리 메모 (필수)</label>
                            <input
                              id={`report-memo-${item.reportId}`}
                              type="text"
                              value={draft.memo}
                              onChange={e => setDraftField(item.reportId, item.status, 'memo', e.target.value)}
                              placeholder="처리 사유 또는 안내 메모를 입력해 주세요. (필수)"
                              disabled={isSaving}
                            />
                          </div>
                        </div>

                        <div className="actions" style={{ marginTop: 10 }}>
                          <button
                            type="button"
                            className="btn small primary"
                            disabled={isSaving}
                            onClick={() => handleSaveStatus(item)}
                          >
                            {isSaving ? '저장 중…' : '처리 상태 저장'}
                          </button>
                        </div>
                      </div>

                      {/* 처리 이력 */}
                      {Array.isArray(item.history) && item.history.length > 0 && (
                        <details style={{ marginTop: 10 }}>
                          <summary className="mini">처리 이력 {item.history.length}건</summary>
                          <div style={{ marginTop: 6 }}>
                            {item.history.map(h => (
                              <p className="muted mini" key={h.auditLogId} style={{ margin: '4px 0' }}>
                                {h.createdAt || '-'} ·{' '}
                                {h.fromStatusLabel && h.toStatusLabel
                                  ? `${h.fromStatusLabel} → ${h.toStatusLabel}`
                                  : '상태 변경'}{' '}
                                · {h.memo}
                                {h.handledByLabel ? ` (${h.handledByLabel})` : ''}
                              </p>
                            ))}
                          </div>
                        </details>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <Empty>조건에 해당하는 신고 내역이 없습니다.</Empty>
            )}
          </LoadState>
        </Card>
      </div>
    </>
  );
}
