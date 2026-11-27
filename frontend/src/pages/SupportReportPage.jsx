/* S15 고객센터 · 신고 접수 및 나의 신고 내역 (F40 회원 전용 · 서버 API 연동) */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import {
  DEFAULT_GUIDANCE_NOTICE,
  REPORT_CATEGORIES,
  createSupportReport,
  fetchMyReportGroups,
  fetchMySupportReports,
} from '../api/supportReports';
import { notifyNotificationsChanged } from '../common/notificationEvents';
import { PageHead, Card, Empty } from '../components/ui';
import { LoadState } from '../components/shared';

const MAX_DETAIL_LENGTH = 4000;

function statusPillKind(status) {
  switch (status) {
    case 'RECEIVED':
      return 'warn';
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

export default function SupportReportPage() {
  const { isAdmin, ui, run } = useApp();
  const nav = useNavigate();

  const [groupsState, setGroupsState] = useState({ status: 'loading', items: [], error: '' });
  const [reportsState, setReportsState] = useState({
    status: 'loading',
    items: [],
    totalCount: 0,
    guidanceNotice: DEFAULT_GUIDANCE_NOTICE,
    error: '',
  });
  const [reloadKey, setReloadKey] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const groupInitializedRef = useRef(false);

  const [form, setForm] = useState({
    groupId: '',
    category: REPORT_CATEGORIES[0].code,
    detail: '',
  });

  const reload = useCallback(() => setReloadKey(k => k + 1), []);

  useEffect(() => {
    if (isAdmin) return undefined;
    let alive = true;
    setGroupsState(prev => ({ ...prev, status: 'loading', error: '' }));
    fetchMyReportGroups()
      .then(items => {
        if (!alive) return;
        setGroupsState({ status: 'ready', items, error: '' });
        if (!groupInitializedRef.current) {
          groupInitializedRef.current = true;
          setForm(prev => {
            if (prev.groupId) return prev;
            const matchedSelected = items.find(g => Number(g.groupId) === Number(ui?.selected));
            const initialId = matchedSelected?.groupId ?? items[0]?.groupId ?? '';
            return { ...prev, groupId: initialId ? String(initialId) : '' };
          });
        }
      })
      .catch(err => {
        if (!alive) return;
        setGroupsState({
          status: 'error',
          items: [],
          error: err?.message || '참여 모임 목록을 불러오지 못했어요.',
        });
      });
    return () => { alive = false; };
  }, [isAdmin, reloadKey, ui?.selected]);

  useEffect(() => {
    if (isAdmin) return undefined;
    let alive = true;
    setReportsState(prev => ({ ...prev, status: 'loading', error: '' }));
    fetchMySupportReports()
      .then(data => {
        if (!alive) return;
        setReportsState({
          status: 'ready',
          items: data.items,
          totalCount: data.totalCount,
          guidanceNotice: data.guidanceNotice || DEFAULT_GUIDANCE_NOTICE,
          error: '',
        });
      })
      .catch(err => {
        if (!alive) return;
        setReportsState(prev => ({
          ...prev,
          status: 'error',
          error: err?.message || '나의 신고 내역을 불러오지 못했어요.',
        }));
      });
    return () => { alive = false; };
  }, [isAdmin, reloadKey]);

  // 방어적 UI 분리: ADMIN은 접수 불가
  if (isAdmin) {
    return (
      <>
        <PageHead
          kicker="CUSTOMER CENTER"
          title="고객센터 · 자금 안전 신고"
          sub="회원 전용 신고 접수 화면입니다."
        />
        <Card>
          <p className="warning" role="alert">관리자는 신고 처리 메뉴를 이용해 주세요</p>
          <div className="actions">
            <button type="button" className="btn primary" onClick={() => nav('/admin/support')}>
              관리자 신고 처리 메뉴로 이동
            </button>
          </div>
        </Card>
      </>
    );
  }

  const handleSubmit = async e => {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    try {
      const res = await run(
        () => createSupportReport({
          groupId: form.groupId ? Number(form.groupId) : null,
          category: form.category,
          detail: form.detail,
        }),
        data => data?.message || '신고가 접수되었습니다. 실제 계좌 동결·피해 구제는 은행·경찰에 별도로 신고해 주세요.'
      );
      if (res.ok) {
        setForm(prev => ({ ...prev, detail: '' }));
        reload();
        notifyNotificationsChanged();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <PageHead
        kicker="CUSTOMER CENTER"
        title="고객센터 · 자금 안전 신고"
        sub="계좌 변경 의심, 자금 유용 의심, 거래 증빙 불일치 등 안전 관련 신고를 접수하고 처리 상태를 확인합니다."
      >
        <button type="button" className="btn soft" onClick={reload}>새로고침</button>
      </PageHead>

      {/* 필수 법적·안전 안내 배너 */}
      <Card>
        <div className="warning" role="note" style={{ margin: 0 }}>
          <b>중요 안내 (실제 계좌 동결·피해 구제 불가)</b>
          <p style={{ marginTop: 6, marginBottom: 0 }}>{reportsState.guidanceNotice}</p>
        </div>
      </Card>

      {/* 1. 신고 접수 폼 */}
      <div className="section">
        <Card>
          <h2>신고 접수</h2>
          <p className="muted mini">
            신고 상세 내용은 AES-256-GCM으로 암호화되어 저장되며, 신고자 본인과 플랫폼 관리자만 열람할 수 있습니다. 타인의 신고는 열람할 수 없습니다.
          </p>
          <p className="muted mini">
            신고를 처리하는 동안 관리자가 대상 모임의 채팅 내용을 열람할 수 있어요. 열람 기록은 남아요.
          </p>

          <form onSubmit={handleSubmit}>
            <div className="form-grid">
              <div className="field">
                <label htmlFor="support-group-select">대상 모임 (선택)</label>
                <select
                  id="support-group-select"
                  value={form.groupId}
                  onChange={e => setForm(prev => ({ ...prev, groupId: e.target.value }))}
                  disabled={groupsState.status === 'loading'}
                >
                  <option value="">모임 미지정 (일반 문의)</option>
                  {groupsState.items.map(g => (
                    <option key={g.groupId} value={g.groupId}>
                      {g.title} ({g.role === 'OWNER' ? '모임장' : '참여 중'})
                    </option>
                  ))}
                </select>
                {groupsState.status === 'error' && (
                  <small className="muted">{groupsState.error} 모임 미지정으로도 접수할 수 있어요.</small>
                )}
              </div>

              <div className="field">
                <label htmlFor="support-category-select">신고 유형 (필수)</label>
                <select
                  id="support-category-select"
                  value={form.category}
                  onChange={e => setForm(prev => ({ ...prev, category: e.target.value }))}
                >
                  {REPORT_CATEGORIES.map(c => (
                    <option key={c.code} value={c.code}>
                      {c.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="field">
              <label htmlFor="support-detail-textarea">
                상세 내용 (필수 · {form.detail.length}/{MAX_DETAIL_LENGTH}자)
              </label>
              <textarea
                id="support-detail-textarea"
                required
                maxLength={MAX_DETAIL_LENGTH}
                rows={5}
                value={form.detail}
                onChange={e => setForm(prev => ({ ...prev, detail: e.target.value }))}
                placeholder="발생 일시, 대상 모임, 의심 상황 및 관련 내역을 구체적으로 작성해 주세요."
              />
            </div>

            <div className="actions">
              <button type="submit" className="btn primary" disabled={submitting}>
                {submitting ? '신고 접수 중…' : '신고 접수'}
              </button>
            </div>
          </form>
        </Card>
      </div>

      {/* 2. 나의 신고 내역 */}
      <div className="section">
        <Card>
          <h2>나의 신고 내역 · {reportsState.totalCount}건</h2>
          <p className="muted mini">
            본인이 접수한 신고만 표시됩니다. 관리자가 처리 상태를 변경하면 알림과 함께 처리 메모·이력이 업데이트됩니다.
          </p>

          <LoadState
            status={reportsState.status}
            error={reportsState.error}
            onRetry={reload}
            loadingText="나의 신고 내역을 불러오는 중이에요."
          >
            {reportsState.items.length ? (
              <div className="list">
                {reportsState.items.map(r => (
                  <div className="list-item" key={r.reportId}>
                    <div className="item-head">
                      <div>
                        <b>#{r.reportId} · {r.groupTitle} · {r.categoryLabel}</b>
                      </div>
                      <span className={`pill ${statusPillKind(r.status)}`}>{r.statusLabel}</span>
                    </div>

                    <p className="muted mini">
                      신고자: {r.reporterName} · 접수일: {r.createdAt || '-'}
                      {r.updatedAt ? ` · 최근 처리: ${r.updatedAt}` : ''}
                      {r.handledByLabel ? ` (${r.handledByLabel})` : ''}
                    </p>

                    <div className="info mini" style={{ marginTop: 8, whiteSpace: 'pre-wrap' }}>
                      <b>신고 상세</b>
                      <div style={{ marginTop: 4 }}>{r.detail}</div>
                    </div>

                    {r.adminMemo && (
                      <div className="policy-panel" style={{ marginTop: 8 }}>
                        <small className="muted">최근 관리자 처리 메모</small>
                        <p style={{ marginTop: 4, marginBottom: 0 }}>{r.adminMemo}</p>
                      </div>
                    )}

                    {Array.isArray(r.history) && r.history.length > 0 && (
                      <details style={{ marginTop: 10 }}>
                        <summary className="mini">상태 변경 이력 {r.history.length}건</summary>
                        <div style={{ marginTop: 6 }}>
                          {r.history.map(h => (
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
                ))}
              </div>
            ) : (
              <Empty>접수한 신고 내역이 없습니다.</Empty>
            )}
          </LoadState>
        </Card>
      </div>
    </>
  );
}
