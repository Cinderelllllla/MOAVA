/* A02 계모임 관리 및 이력 감사 (F38 · 서버 API 연동 · 조회 전용) */
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  fetchAdminGroups,
  fetchAdminGroupDetail,
  fetchAdminAuditLogs,
  fetchAdminAuditFilterOptions,
} from '../../api/adminGroups';
import { PageHead, Card, Empty } from '../../components/ui';
import { LoadState } from '../../components/shared';
import { formatKrw } from '../../common/format';

const PAGE_SIZE = 8;

const GROUP_STATUS_LABEL = {
  RECRUITING: '모집 중',
  ACTIVE: '운영 중',
  DISSOLVE_PENDING: '해산 검토',
  DISSOLVED: '해산 완료',
};

const PURPOSE_LABEL = {
  SAVINGS: '목돈 마련',
  INVEST: '재테크',
  SOCIAL: '친목',
  STARTUP: '창업',
  TRAVEL: '여행',
};

const OVERDUE_POLICY_LABEL = {
  ALERT_ONLY: '5일차 알림',
  ALERT_RESTRICT: '5일차 알림 + 채팅 제한·후순위 검토',
};

const VERIFY_STATUS_LABEL = {
  PENDING: '인증 대기',
  VERIFIED: '확인 완료',
  REVERIFY_REQUIRED: '재인증 필요',
};

const EVENT_TYPE_LABEL = {
  GROUP_CREATE: '모임 개설',
  JOIN_REQUEST: '가입 신청',
  JOIN_APPROVE: '가입 승인',
  JOIN_REJECT: '가입 반려',
  PROOF_UPLOAD: '증빙 업로드',
  PROOF_CONFIRM: '납입 확인',
  PROOF_REVISION: '증빙 보완 요청',
  PROOF_RESUBMIT: '증빙 재제출',
  REF_ASSIGN: '참조번호 지정',
  REF_CORRECT: '참조번호 정정',
  ORDER_REQUEST: '수령 회차 신청',
  ORDER_CONSENT_REQ: '수령 순서 동의 요청',
  ORDER_CONFIRM: '수령 순서 확정',
  PAYOUT_RECEIVED: '곗돈 수령 완료',
  WITHDRAW_REQUEST: '중도 탈퇴 신청',
  SETTLEMENT_SET: '정산안 등록',
  SETTLEMENT_CONSENT: '정산안 동의',
  WITHDRAW_APPROVE: '중도 탈퇴 승인',
  WITHDRAW_CONSENT_EXEMPT: '탈퇴 동의 면제',
  WARN: '회원 경고',
  EXPEL: '강제 탈퇴',
  RULE_UPDATE: '운영 규칙 수정',
  DISSOLVE_REQUEST: '해산 신청',
  DISSOLVE_APPROVE: '해산 최종 승인',
  DISSOLVE_REJECT: '해산 신청 반려',
  ACCOUNT_REGISTER: '계좌 등록',
  ACCOUNT_CHANGE: '계좌 변경',
  ACCOUNT_VERIFY: '계좌 소유 확인',
  LEADER_TRANSFER: '모임장 승계',
  OVERDUE_DETECT: '미납 자동 감지',
  OVERDUE_WARN: '미납 경고 알림',
  CHAT_RESTRICT: '채팅 제한',
  CHAT_RELEASE: '채팅 제한 해제',
  ORDER_REVIEW: '수령 후순위 검토',
  BAN: '플랫폼 가입 제한',
  BAN_RELEASE: '가입 제한 해제',
  REPORT_RECEIVED: '신고 접수',
  REPORT_STATUS: '신고 상태 변경',
  VISIBILITY_CHANGE: '공개 여부 변경',
  JOIN_CODE_VERIFY: '인증코드 검증',
  JOIN_CODE_LOCK: '인증코드 잠금',
  JOIN_CODE_REISSUE: '인증코드 재발급',
  DEMO_TEST_MEMBERS: '시연 테스트 회원 생성',
  DEMO_RESET: '시연 데이터 초기화',
};

const ACTOR_TYPE_LABEL = {
  ADMIN: '관리자',
  MEMBER: '회원',
  SYSTEM: '시스템',
};

const INITIAL_FILTER = {
  groupId: 'all',
  eventType: 'all',
  reviewStatus: 'ALL',
  adminOnly: false,
  actorAdminId: 'all',
  page: 0,
};

function formatEventLabel(code) {
  if (!code) return '-';
  const label = EVENT_TYPE_LABEL[code];
  return label ? `${label} (${code})` : code;
}

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

export default function AdminGroupsPage() {
  const auditSectionRef = useRef(null);

  const [reloadKey, setReloadKey] = useState(0);
  const [groupsState, setGroupsState] = useState({ status: 'loading', items: [], error: '' });
  const [metaState, setMetaState] = useState({ eventTypes: [], admins: [] });

  const [selectedGroupId, setSelectedGroupId] = useState(null);
  const [detailState, setDetailState] = useState({ status: 'idle', data: null, error: '' });

  const [filter, setFilter] = useState(INITIAL_FILTER);
  const [auditState, setAuditState] = useState({
    status: 'loading',
    data: { items: [], page: 0, size: PAGE_SIZE, totalCount: 0, totalPages: 0, hasPrev: false, hasNext: false },
    error: '',
  });

  const reloadAll = useCallback(() => setReloadKey(k => k + 1), []);

  /* 1. 계모임 목록 + 감사 필터 메타 옵션(이벤트 유형, 담당 관리자) 로드 */
  useEffect(() => {
    let alive = true;
    setGroupsState(prev => ({ ...prev, status: 'loading', error: '' }));

    Promise.all([
      fetchAdminGroups(),
      fetchAdminAuditFilterOptions().catch(() => ({ eventTypes: [], admins: [] })),
    ])
      .then(([groupList, meta]) => {
        if (!alive) return;
        setGroupsState({ status: 'ready', items: groupList, error: '' });
        setMetaState(meta);
      })
      .catch(err => {
        if (!alive) return;
        setGroupsState({
          status: 'error',
          items: [],
          error: err?.message || '계모임 목록을 불러오지 못했어요.',
        });
      });

    return () => { alive = false; };
  }, [reloadKey]);

  /* 2. 선택한 계모임의 관리자 상세 패널 조회 */
  useEffect(() => {
    if (!selectedGroupId) {
      setDetailState({ status: 'idle', data: null, error: '' });
      return;
    }
    let alive = true;
    setDetailState({ status: 'loading', data: null, error: '' });

    fetchAdminGroupDetail(selectedGroupId)
      .then(data => {
        if (alive) setDetailState({ status: 'ready', data, error: '' });
      })
      .catch(err => {
        if (alive) {
          setDetailState({
            status: 'error',
            data: null,
            error: err?.message || '계모임 상세 정보를 불러오지 못했어요.',
          });
        }
      });

    return () => { alive = false; };
  }, [selectedGroupId, reloadKey]);

  /* 3. 감사 이력 필터 + 8건 페이징 조회 */
  useEffect(() => {
    let alive = true;
    setAuditState(prev => ({ ...prev, status: 'loading', error: '' }));

    fetchAdminAuditLogs({
      groupId: filter.groupId,
      eventType: filter.eventType,
      reviewStatus: filter.reviewStatus,
      adminOnly: filter.adminOnly,
      actorAdminId: filter.actorAdminId,
      page: filter.page,
      size: PAGE_SIZE,
    })
      .then(data => {
        if (!alive) return;
        setAuditState({
          status: 'ready',
          data: data || { items: [], page: 0, size: PAGE_SIZE, totalCount: 0, totalPages: 0, hasPrev: false, hasNext: false },
          error: '',
        });
      })
      .catch(err => {
        if (!alive) return;
        setAuditState(prev => ({
          ...prev,
          status: 'error',
          error: err?.message || '감사 이력을 불러오지 못했어요.',
        }));
      });

    return () => { alive = false; };
  }, [filter, reloadKey]);

  const updateFilter = patch => {
    setFilter(prev => ({ ...prev, ...patch, page: 0 }));
  };

  const handleAdminOnlyChange = checked => {
    setFilter(prev => ({
      ...prev,
      adminOnly: checked,
      actorAdminId: checked ? prev.actorAdminId : 'all',
      page: 0,
    }));
  };

  const handleActorAdminChange = value => {
    setFilter(prev => ({
      ...prev,
      actorAdminId: value,
      adminOnly: value !== 'all' ? true : prev.adminOnly,
      page: 0,
    }));
  };

  const handleFilterByGroup = groupId => {
    setFilter(prev => ({ ...prev, groupId: String(groupId), page: 0 }));
    auditSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const resetFilters = () => setFilter(INITIAL_FILTER);

  const auditData = auditState.data || {};
  const auditItems = auditData.items || [];
  const totalCount = Number(auditData.totalCount || 0);
  const totalPages = Number(auditData.totalPages || 0);
  const currentPage = Number(auditData.page ?? filter.page);
  const pageNumbers = buildPageNumbers(currentPage, totalPages);

  const detail = detailState.data;

  return <>
    <PageHead
      kicker="ADMIN GROUPS · F38"
      title="계모임 관리 · 이력 감사"
      sub="전체 계모임 현황과 상세 규칙·건수를 조회하고, 감사 이력을 필터·페이징(페이지당 8건)으로 점검합니다. 가입 승인·증빙 승인은 모임장 전용으로 관리자는 조회만 가능합니다."
    >
      <button type="button" className="btn soft" onClick={reloadAll}>새로고침</button>
    </PageHead>

    {/* 1. 계모임 목록 표 */}
    <Card>
      <div className="section-title">
        <h2>전체 계모임 목록 ({groupsState.items.length}개)</h2>
        <span className="muted mini">행을 선택해 규칙 및 가입 요청·증빙·탈퇴 건수를 확인할 수 있습니다.</span>
      </div>

      <LoadState
        status={groupsState.status}
        error={groupsState.error}
        onRetry={reloadAll}
        loadingText="계모임 목록을 불러오는 중이에요."
      >
        {groupsState.items.length ? (
          <div className="tablewrap">
            <table>
              <thead>
                <tr>
                  <th>모임명</th>
                  <th>모임장</th>
                  <th>상태</th>
                  <th>공개</th>
                  <th>회원 수</th>
                  <th>월 납입액 · 납입일</th>
                  <th>계좌 인증</th>
                  <th>상세</th>
                </tr>
              </thead>
              <tbody>
                {groupsState.items.map(g => {
                  const isSelected = selectedGroupId === g.groupId;
                  return (
                    <tr
                      key={g.groupId}
                      style={isSelected ? { background: 'var(--pale)' } : undefined}
                    >
                      <td>
                        <b>{g.title}</b>
                        <div className="muted mini">#{g.groupId} · {g.createdAt || '-'}</div>
                      </td>
                      <td>{g.ownerName || '-'}</td>
                      <td>
                        <span className={`pill ${g.status === 'DISSOLVE_PENDING' ? 'warn' : ''}`}>
                          {GROUP_STATUS_LABEL[g.status] || g.status}
                        </span>
                      </td>
                      <td>{g.visibility === 'PRIVATE' ? '비공개' : '공개'}</td>
                      <td>{g.activeMemberCount}/{g.maxMembers}명</td>
                      <td>{formatKrw(g.amount)} · 매월 {g.payDay}일</td>
                      <td>
                        {g.accountVerifyStatus
                          ? (VERIFY_STATUS_LABEL[g.accountVerifyStatus] || g.accountVerifyStatus)
                          : '미등록'}
                      </td>
                      <td>
                        <button
                          type="button"
                          className={`btn small ${isSelected ? 'primary' : 'soft'}`}
                          onClick={() => setSelectedGroupId(isSelected ? null : g.groupId)}
                        >
                          {isSelected ? '닫기' : '상세'}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty>등록된 계모임이 없습니다.</Empty>
        )}
      </LoadState>
    </Card>

    {/* 2. 관리자 상세 패널 (조회 전용 — 가입 승인·증빙 승인 개입 불가) */}
    {selectedGroupId && (
      <div className="detail-panel section">
        <LoadState
          status={detailState.status}
          error={detailState.error}
          onRetry={reloadAll}
          loadingText="계모임 상세 정보를 불러오는 중이에요."
        >
          {detail && (
            <>
              <div className="item-head" style={{ marginBottom: 10 }}>
                <h2 style={{ margin: 0 }}>관리자 상세 패널 · {detail.title} (#{detail.groupId})</h2>
                <span className="pill">{GROUP_STATUS_LABEL[detail.status] || detail.status}</span>
              </div>

              <p className="muted mini">
                모임장: <b>{detail.ownerName}</b> · 공개: {detail.visibility === 'PRIVATE' ? '비공개' : '공개'} ·
                목적: {PURPOSE_LABEL[detail.purpose] || detail.purpose} ·
                지역: {detail.regionSido} {detail.regionSigungu} ·
                인원: {detail.activeMemberCount}/{detail.maxMembers}명
              </p>
              <p className="muted mini">
                월 납입액: {formatKrw(detail.amount)} · 기간: {detail.months}개월 · 매월 {detail.payDay}일 ·
                가정 이율: {detail.assumedRate ?? 0}% ·
                수령 순서: {detail.orderConfirmed ? '확정' : '미확정'} ·
                미납 정책: {OVERDUE_POLICY_LABEL[detail.overduePolicy] || detail.overduePolicy}
              </p>

              {/* 건수 요약 */}
              <div className="grid cols4" style={{ marginTop: 14, marginBottom: 14 }}>
                <div className="card stat" style={{ padding: 14 }}>
                  <small>가입 요청</small>
                  <div className="value" style={{ fontSize: 20 }}>{detail.joinRequestCount}건</div>
                  <small className="muted">대기 {detail.pendingJoinRequestCount}건 (조회만 가능)</small>
                </div>
                <div className="card stat" style={{ padding: 14 }}>
                  <small>납입 증빙</small>
                  <div className="value" style={{ fontSize: 20 }}>{detail.paymentProofCount}건</div>
                  <small className="muted">미확인 {detail.pendingProofCount}건 (조회만 가능)</small>
                </div>
                <div className="card stat" style={{ padding: 14 }}>
                  <small>중도 탈퇴 신청</small>
                  <div className="value" style={{ fontSize: 20 }}>{detail.withdrawalRequestCount}건</div>
                  <small className="muted">진행 중 {detail.activeWithdrawalCount}건</small>
                </div>
                <div className="card stat" style={{ padding: 14 }}>
                  <small>미납 감지</small>
                  <div className="value" style={{ fontSize: 20 }}>{detail.overdueCaseCount}건</div>
                  <small className="muted">미해소 {detail.openOverdueCaseCount}건</small>
                </div>
              </div>

              {/* 운영 규칙 및 소개 */}
              <div className="policy-panel" style={{ marginTop: 12 }}>
                <h3>운영 규칙 · {detail.rulesTitle || '제목 없음'}</h3>
                <p style={{ whiteSpace: 'pre-wrap' }}>{detail.rulesBody || '등록된 규칙이 없습니다.'}</p>
                {detail.description && (
                  <p className="muted mini" style={{ marginBottom: 0 }}>모임 소개: {detail.description}</p>
                )}
              </div>

              {/* 모임 계좌 현황 (조회 전용) */}
              <div className="policy-panel" style={{ marginTop: 12 }}>
                <h3>모임 계좌 현황 (조회 전용)</h3>
                {detail.currentAccount ? (
                  <p style={{ marginBottom: detail.pendingAccount ? 8 : 0 }}>
                    현재 계좌: <b>{detail.currentAccount.bankName}</b> ·
                    예금주 {detail.currentAccount.maskedHolderName || '-'} ·
                    <span className="acct-mask"> {detail.currentAccount.maskedAccountNo}</span>{' '}
                    <span className="policy-tag">
                      {VERIFY_STATUS_LABEL[detail.currentAccount.verifyStatus] || detail.currentAccount.verifyStatus}
                    </span>
                  </p>
                ) : (
                  <p className="muted mini" style={{ marginBottom: detail.pendingAccount ? 8 : 0 }}>
                    등록된 현재 계좌가 없습니다.
                  </p>
                )}
                {detail.pendingAccount && (
                  <p className="muted mini" style={{ marginBottom: 0 }}>
                    변경 신청 계좌: {detail.pendingAccount.bankName} · {detail.pendingAccount.maskedAccountNo} ·
                    신청일 {detail.pendingAccount.requestedAt || '-'}{' '}
                    <span className="policy-tag">
                      {VERIFY_STATUS_LABEL[detail.pendingAccount.verifyStatus] || detail.pendingAccount.verifyStatus}
                    </span>
                  </p>
                )}
              </div>

              <p className="info mini" style={{ marginTop: 14 }}>
                {detail.readOnlyNotice || '가입 승인 및 납입 증빙 승인은 모임장 전용입니다. 관리자는 조회·감사만 가능합니다.'}
              </p>

              <div className="actions">
                <button
                  type="button"
                  className="btn small primary"
                  onClick={() => handleFilterByGroup(detail.groupId)}
                >
                  이 모임 감사 이력 보기
                </button>
                <button
                  type="button"
                  className="btn small soft"
                  onClick={() => setSelectedGroupId(null)}
                >
                  상세 닫기
                </button>
              </div>
            </>
          )}
        </LoadState>
      </div>
    )}

    {/* 3. AUDIT_LOG 필터·페이징 이력 표 (필터 3종 + v3.1 관리자 필터 2종 + 8건 페이징) */}
    <div className="section" ref={auditSectionRef}>
      <Card>
        <div className="section-title">
          <h2>운영 · 관리자 조치 감사 이력 (AUDIT_LOG)</h2>
          <button type="button" className="btn small soft" onClick={resetFilters}>필터 초기화</button>
        </div>

        <div className="policy-history-wrap">
          {/* 필터 3종 + v3.1 담당 관리자 */}
          <div className="policy-admin-filter">
            <div className="field">
              <label htmlFor="audit-group-filter">계모임 선택</label>
              <select
                id="audit-group-filter"
                value={filter.groupId}
                onChange={e => updateFilter({ groupId: e.target.value })}
              >
                <option value="all">전체 계모임 (+ 플랫폼 공통)</option>
                {groupsState.items.map(g => (
                  <option key={g.groupId} value={String(g.groupId)}>
                    {g.title} (#{g.groupId})
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="audit-event-filter">이벤트 유형</label>
              <select
                id="audit-event-filter"
                value={filter.eventType}
                onChange={e => updateFilter({ eventType: e.target.value })}
              >
                <option value="all">전체 이벤트 유형</option>
                {metaState.eventTypes.map(type => (
                  <option key={type} value={type}>
                    {formatEventLabel(type)}
                  </option>
                ))}
              </select>
            </div>

            <div className="field">
              <label htmlFor="audit-review-filter">검토 / 완료 구분</label>
              <select
                id="audit-review-filter"
                value={filter.reviewStatus}
                onChange={e => updateFilter({ reviewStatus: e.target.value })}
              >
                <option value="ALL">전체 구분</option>
                <option value="REVIEW">검토·감지 (_REQUEST / _DETECT)</option>
                <option value="DONE">완료·처리 (그 외)</option>
              </select>
            </div>
          </div>

          {/* v3.1 관리자 조치만 + 담당 관리자별 조회 */}
          <div
            className="policy-admin-filter"
            style={{ gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', marginTop: 0 }}
          >
            <div className="field">
              <label htmlFor="audit-admin-filter">담당 관리자별 조회 (v3.1)</label>
              <select
                id="audit-admin-filter"
                value={filter.actorAdminId}
                onChange={e => handleActorAdminChange(e.target.value)}
              >
                <option value="all">전체 담당자</option>
                {metaState.admins.map(a => (
                  <option key={a.adminId} value={String(a.adminId)}>
                    {a.name} ({a.email}){a.status !== 'ACTIVE' ? ' · 비활성' : ''}
                  </option>
                ))}
              </select>
            </div>

            <div className="field" style={{ justifyContent: 'center' }}>
              <label htmlFor="audit-admin-only">관리자 조치 필터 (v3.1)</label>
              <label
                htmlFor="audit-admin-only"
                className="checkline"
                style={{ margin: '6px 0 0', alignItems: 'center', cursor: 'pointer' }}
              >
                <input
                  id="audit-admin-only"
                  type="checkbox"
                  checked={filter.adminOnly}
                  onChange={e => handleAdminOnlyChange(e.target.checked)}
                />
                <span>관리자 조치만 보기 (ACTOR_ADMIN_ID IS NOT NULL)</span>
              </label>
            </div>
          </div>

          <div className="policy-history-summary">
            조회 결과 <b>{totalCount}건</b> · 페이지 <b>{totalPages > 0 ? currentPage + 1 : 0} / {totalPages}</b> (페이지당 {PAGE_SIZE}건)
          </div>

          <LoadState
            status={auditState.status}
            error={auditState.error}
            onRetry={reloadAll}
            loadingText="감사 이력을 불러오는 중이에요."
          >
            {auditItems.length ? (
              <div className="tablewrap">
                <table>
                  <thead>
                    <tr>
                      <th style={{ whiteSpace: 'nowrap' }}>일시</th>
                      <th style={{ whiteSpace: 'nowrap' }}>모임</th>
                      <th style={{ whiteSpace: 'nowrap' }}>이벤트 유형</th>
                      <th style={{ whiteSpace: 'nowrap' }}>구분</th>
                      <th style={{ whiteSpace: 'nowrap' }}>행위자</th>
                      <th style={{ whiteSpace: 'nowrap' }}>대상 회원</th>
                      <th>상세 내용</th>
                    </tr>
                  </thead>
                  <tbody>
                    {auditItems.map(log => (
                      <tr key={log.auditLogId}>
                        <td className="muted mini" style={{ whiteSpace: 'nowrap' }}>
                          {log.createdAt || '-'}
                        </td>
                        <td>{log.groupTitle || '플랫폼 공통'}</td>
                        <td>
                          <span className="pill">{EVENT_TYPE_LABEL[log.eventType] || log.eventType}</span>
                          <div className="muted mini" style={{ marginTop: 3 }}>{log.eventType}</div>
                        </td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <span className={`pill ${log.reviewCategory === 'REVIEW' ? 'warn' : ''}`}>
                            {log.reviewCategory === 'REVIEW' ? '검토·감지' : '완료'}
                          </span>
                        </td>
                        <td style={{ whiteSpace: 'nowrap' }}>
                          <span className={`pill ${log.actorType === 'ADMIN' ? 'mine' : ''}`}>
                            {ACTOR_TYPE_LABEL[log.actorType] || log.actorType}
                          </span>{' '}
                          {log.actorLabel || '-'}
                        </td>
                        <td className="muted mini" style={{ whiteSpace: 'nowrap' }}>{log.targetMemberLabel || '-'}</td>
                        <td>{log.description || '-'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <Empty>선택한 조건에 해당하는 감사 이력이 없습니다.</Empty>
            )}
          </LoadState>

          {/* 8건 페이징 컨트롤 */}
          <div className="actions policy-history-pages">
            <button
              type="button"
              className="btn small soft"
              disabled={!auditData.hasPrev || currentPage <= 0}
              onClick={() => setFilter(prev => ({ ...prev, page: Math.max(0, currentPage - 1) }))}
            >
              이전
            </button>
            {pageNumbers.map(num => (
              <button
                key={num}
                type="button"
                className={`btn small ${num === currentPage ? 'primary' : 'soft'}`}
                onClick={() => setFilter(prev => ({ ...prev, page: num }))}
              >
                {num + 1}
              </button>
            ))}
            <button
              type="button"
              className="btn small soft"
              disabled={!auditData.hasNext}
              onClick={() => setFilter(prev => ({ ...prev, page: currentPage + 1 }))}
            >
              다음
            </button>
          </div>
        </div>
      </Card>
    </div>
  </>;
}
