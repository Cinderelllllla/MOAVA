/* A03 플랫폼 가입 제한 설정·해제 및 경고 현황 (F39 · 서버 API 연동) */
import { useCallback, useEffect, useState } from 'react';
import { useApp } from '../../common/store';
import {
  fetchAdminSanctionOverview,
  banMember,
  releaseMemberBan,
  fetchSanctionHistory,
} from '../../api/adminSanctions';
import { PageHead, Card, Input, Empty } from '../../components/ui';
import { LoadState } from '../../components/shared';

const PAGE_SIZE = 8;

const INITIAL_HISTORY_FILTER = {
  keyword: '',
  type: 'ALL',
  page: 0,
};

function buildPageNumbers(currentPage, totalPages) {
  if (totalPages <= 1) return [0];
  const windowSize = 5;
  let start = Math.max(0, currentPage - Math.floor(windowSize / 2));
  let end = Math.min(totalPages - 1, start + windowSize - 1);
  start = Math.max(0, end - windowSize + 1);
  const nums = [];
  for (let i = start; i <= end; i += 1) nums.push(i);
  return nums;
}

export default function AdminSanctionsPage() {
  const { run } = useApp();

  const [reloadKey, setReloadKey] = useState(0);
  const [form, setForm] = useState({ memberId: '', reason: '' });

  const [overviewState, setOverviewState] = useState({
    status: 'loading',
    data: { candidates: [], bannedMembers: [], bannedCount: 0, warnings: [] },
    error: '',
  });

  const [filter, setFilter] = useState(INITIAL_HISTORY_FILTER);
  const [searchInput, setSearchInput] = useState('');
  const [historyState, setHistoryState] = useState({
    status: 'loading',
    data: { items: [], page: 0, size: PAGE_SIZE, totalCount: 0, totalPages: 0, hasPrev: false, hasNext: false },
    error: '',
  });

  const reloadAll = useCallback(() => setReloadKey(k => k + 1), []);

  /* 1. 제재 가능 대상 + 현재 가입 제한 목록 + 모임별 경고 현황 로드 */
  useEffect(() => {
    let alive = true;
    setOverviewState(prev => ({ ...prev, status: 'loading', error: '' }));

    fetchAdminSanctionOverview()
      .then(data => {
        if (!alive) return;
        setOverviewState({ status: 'ready', data, error: '' });
      })
      .catch(err => {
        if (!alive) return;
        setOverviewState(prev => ({
          ...prev,
          status: 'error',
          error: err?.message || '제재 및 경고 현황을 불러오지 못했어요.',
        }));
      });

    return () => { alive = false; };
  }, [reloadKey]);

  /* 2. 경고·강제 탈퇴 상세 이력 (검색·유형 필터·8건 페이징) 로드 */
  useEffect(() => {
    let alive = true;
    setHistoryState(prev => ({ ...prev, status: 'loading', error: '' }));

    fetchSanctionHistory({
      keyword: filter.keyword,
      type: filter.type,
      page: filter.page,
      size: PAGE_SIZE,
    })
      .then(data => {
        if (!alive) return;
        setHistoryState({
          status: 'ready',
          data: {
            items: Array.isArray(data?.items) ? data.items : [],
            page: Number(data?.page ?? filter.page),
            size: Number(data?.size ?? PAGE_SIZE),
            totalCount: Number(data?.totalCount ?? 0),
            totalPages: Number(data?.totalPages ?? 0),
            hasPrev: Boolean(data?.hasPrev),
            hasNext: Boolean(data?.hasNext),
          },
          error: '',
        });
      })
      .catch(err => {
        if (!alive) return;
        setHistoryState(prev => ({
          ...prev,
          status: 'error',
          error: err?.message || '경고·강제 탈퇴 이력을 불러오지 못했어요.',
        }));
      });

    return () => { alive = false; };
  }, [filter.keyword, filter.type, filter.page, reloadKey]);

  const handleBanSubmit = async e => {
    e.preventDefault();
    const res = await run(
      () => banMember(form.memberId, form.reason),
      data => data?.message || '플랫폼 가입 제한을 설정했어요.'
    );
    if (res.ok) {
      setForm({ memberId: '', reason: '' });
      reloadAll();
    }
  };

  const handleRelease = async item => {
    const input = prompt(
      `${item.memberName} 회원의 가입 제한을 해제할까요?\n이전 제재 기록은 감사 이력에 보존되며, 해제 후 모임 가입 신청이 다시 가능합니다.\n\n해제 사유를 입력하세요 (비워 두면 기본 문구로 기록됩니다).`,
      ''
    );
    if (input === null) {
      return;
    }
    const res = await run(
      () => releaseMemberBan(item.memberId, input),
      data => data?.message || '플랫폼 가입 제한을 해제했어요.'
    );
    if (res.ok) {
      reloadAll();
    }
  };

  const handleSearchSubmit = e => {
    e.preventDefault();
    setFilter(prev => ({ ...prev, keyword: searchInput.trim(), page: 0 }));
  };

  const handleResetFilters = () => {
    setSearchInput('');
    setFilter(INITIAL_HISTORY_FILTER);
  };

  const { candidates, bannedMembers, bannedCount, warnings } = overviewState.data;
  const history = historyState.data;
  const pageNumbers = buildPageNumbers(history.page, Math.max(1, history.totalPages));

  return (
    <>
      <PageHead
        kicker="PLATFORM SAFETY"
        title="플랫폼 가입 제한 · 경고 현황"
        sub="중복 제재를 방지하고 플랫폼 가입 제한을 설정·해제합니다. 모임별 경고 현황과 경고·강제 탈퇴 이력을 함께 관리합니다."
      >
        <button type="button" className="btn soft" onClick={reloadAll}>새로고침</button>
      </PageHead>

      <LoadState
        status={overviewState.status}
        error={overviewState.error}
        onRetry={reloadAll}
        loadingText="가입 제한 및 경고 현황을 불러오는 중이에요."
      >
        {/* 1. 신규 가입 제한 설정 + 현재 가입 제한 n명 목록 */}
        <Card>
          <h2>신규 가입 제한 설정</h2>
          <p className="muted mini">
            이미 가입 제한 중인 회원은 대상 목록에서 제외되며, 사유 입력이 필수입니다. 제한 회원은 계모임 가입 신청·승인이 차단됩니다.
          </p>

          <form onSubmit={handleBanSubmit}>
            <div className="form-grid">
              <div className="field">
                <label htmlFor="sanction-member-select">제재 대상 (현재 제한 회원 제외)</label>
                <select
                  id="sanction-member-select"
                  value={form.memberId}
                  onChange={e => setForm(prev => ({ ...prev, memberId: e.target.value }))}
                >
                  <option value="">회원 선택</option>
                  {candidates.map(c => (
                    <option key={c.memberId} value={c.memberId}>
                      #{c.memberId} · {c.memberName}{c.email ? ` (${c.email})` : ''}
                    </option>
                  ))}
                </select>
              </div>
              <Input
                label="제재 사유 (필수)"
                name="sanctionReason"
                placeholder="제재 사유를 입력하세요"
                value={form.reason}
                onChange={e => setForm(prev => ({ ...prev, reason: e.target.value }))}
              />
            </div>

            <div className="actions">
              <button
                type="submit"
                className="btn danger"
                disabled={!form.memberId || !form.reason.trim()}
              >
                플랫폼 가입 제한 설정
              </button>
            </div>
          </form>

          <div className="divider" />

          <h2>현재 가입 제한 · {bannedCount}명</h2>
          {bannedMembers.length ? (
            <div className="list">
              {bannedMembers.map(item => (
                <div className="list-item item-head" key={item.memberId}>
                  <div>
                    <b>{item.memberName}</b>
                    {item.email && <span className="muted mini"> · {item.email}</span>}
                    <p className="muted mini">
                      {item.reason || '사유 미기록'}
                      {item.bannedAt ? ` · ${item.bannedAt}` : ''}
                      {item.issuedAdminLabel ? ` · 조치자 ${item.issuedAdminLabel}` : ''}
                    </p>
                  </div>
                  <button
                    type="button"
                    className="btn small soft"
                    onClick={() => handleRelease(item)}
                  >
                    가입 제한 해제
                  </button>
                </div>
              ))}
            </div>
          ) : (
            <Empty>현재 가입 제한 중인 회원이 없습니다.</Empty>
          )}
        </Card>

        {/* 2. 모임별 회원 경고 현황 (n/2회, 2회 = 강제 탈퇴 검토 대상) */}
        <div className="section">
          <Card>
            <h2>모임별 회원 경고 현황 ({warnings.length}건)</h2>
            <p className="muted mini">
              모임별 누적 경고는 최대 2회입니다. 2회 도달 시 자동 탈퇴하지 않고 모임장이 강제 탈퇴 여부를 검토합니다.
            </p>
            {warnings.length ? (
              <div className="list">
                {warnings.map(item => (
                  <div className="list-item item-head" key={`${item.groupId}-${item.memberId}`}>
                    <div>
                      <b>{item.memberName}</b> · {item.groupTitle}
                      <p className="muted mini">
                        경고 {item.warningCount}회 · {item.statusText}
                      </p>
                    </div>
                    <span className={`pill ${item.expelReviewTarget ? 'warn' : ''}`}>
                      {item.warningCount}/{item.maxWarnings}회
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <Empty>현재 경고를 받은 회원이 없습니다.</Empty>
            )}
          </Card>
        </div>
      </LoadState>

      {/* 3. 경고·강제 탈퇴 상세 이력 (검색 + 유형 필터 + 8건 페이징) */}
      <div className="section">
        <Card>
          <h2>경고 · 강제 탈퇴 상세 이력</h2>
          <p className="muted mini">
            모임 내 경고·강제 탈퇴 조치 이력(조치자: 모임장)을 회원명·모임명 및 유형으로 검색합니다.
          </p>

          <form onSubmit={handleSearchSubmit} className="form-grid">
            <Input
              label="회원·모임 검색"
              name="warnSearch"
              placeholder="회원명 또는 모임명"
              value={searchInput}
              onChange={e => setSearchInput(e.target.value)}
            />
            <div className="field">
              <label htmlFor="sanction-history-type">처리 유형</label>
              <select
                id="sanction-history-type"
                value={filter.type}
                onChange={e => setFilter(prev => ({ ...prev, type: e.target.value, page: 0 }))}
              >
                <option value="ALL">전체</option>
                <option value="WARN">경고</option>
                <option value="EXPEL">강제 탈퇴</option>
              </select>
            </div>
            <div className="actions" style={{ alignSelf: 'end' }}>
              <button type="submit" className="btn primary">검색</button>
              <button type="button" className="btn soft" onClick={handleResetFilters}>초기화</button>
            </div>
          </form>

          <div style={{ marginTop: 14 }}>
            <LoadState
              status={historyState.status}
              error={historyState.error}
              onRetry={reloadAll}
              loadingText="경고·강제 탈퇴 이력을 불러오는 중이에요."
            >
              {history.items.length ? (
                <div className="tablewrap">
                  <table>
                    <thead>
                      <tr>
                        <th style={{ whiteSpace: 'nowrap' }}>일시</th>
                        <th style={{ whiteSpace: 'nowrap' }}>모임</th>
                        <th style={{ whiteSpace: 'nowrap' }}>대상 회원</th>
                        <th style={{ whiteSpace: 'nowrap' }}>유형</th>
                        <th style={{ whiteSpace: 'nowrap' }}>조치자</th>
                        <th>사유</th>
                      </tr>
                    </thead>
                    <tbody>
                      {history.items.map(row => (
                        <tr key={row.sanctionId}>
                          <td className="muted mini" style={{ whiteSpace: 'nowrap' }}>
                            {row.createdAt || '-'}
                          </td>
                          <td>{row.groupTitle || '-'}</td>
                          <td style={{ whiteSpace: 'nowrap' }}>{row.memberName || '-'}</td>
                          <td style={{ whiteSpace: 'nowrap' }}>
                            <span className={`pill ${row.sanctionType === 'EXPEL' ? 'warn' : ''}`}>
                              {row.sanctionTypeLabel || row.sanctionType}
                            </span>
                          </td>
                          <td style={{ whiteSpace: 'nowrap' }}>{row.issuerLabel || '-'}</td>
                          <td>{row.reason || '-'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <Empty>조건에 맞는 경고·강제 탈퇴 이력이 없습니다.</Empty>
              )}

              <div className="actions" style={{ justifyContent: 'flex-end', marginTop: 14 }}>
                <span className="muted mini">
                  총 {history.totalCount}건 · {history.totalPages > 0 ? history.page + 1 : 0}/{history.totalPages}페이지
                </span>
                <button
                  type="button"
                  className="btn small soft"
                  disabled={!history.hasPrev}
                  onClick={() => setFilter(prev => ({ ...prev, page: Math.max(0, prev.page - 1) }))}
                >
                  이전
                </button>
                {history.totalPages > 0 && pageNumbers.map(num => (
                  <button
                    key={num}
                    type="button"
                    className={`btn small ${num === history.page ? 'primary' : 'soft'}`}
                    onClick={() => setFilter(prev => ({ ...prev, page: num }))}
                  >
                    {num + 1}
                  </button>
                ))}
                <button
                  type="button"
                  className="btn small soft"
                  disabled={!history.hasNext}
                  onClick={() => setFilter(prev => ({ ...prev, page: prev.page + 1 }))}
                >
                  다음
                </button>
              </div>
            </LoadState>
          </div>
        </Card>
      </div>
    </>
  );
}
