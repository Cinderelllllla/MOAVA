/* 관리자 화면 A01~A06: 대시보드 · 계모임 관리 · 제재 · 해산 승인 · 참조번호 · 미납 관리 (A07 신고 처리 · A08 알림은 Misc.jsx 공용) */
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../../common/store';
import { groups, admin, procedures, memberName } from '../../api';
import { PageHead, Card, Input, Empty } from '../../components/ui';
import { LoadState } from '../../components/shared';
import Icon from '../../components/Icon';
import { formatKrw } from '../../common/format';

const CONSENT_STATUS_LABELS = { PENDING: '응답 대기', AGREED: '동의 완료', REJECTED: '거부', EXEMPT: '동의 면제' };
const DASHBOARD_EVENT_TYPE_LABEL = {
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
const formatDateTime = v => {
  if (!v) return '-';
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 16);
  try { return new Date(v).toLocaleString('ko-KR'); } catch { return String(v); }
};

/* ---- A01 대시보드 (F37 F41 · 서버 API 연동) ---- */
export function AdminDashboard() {
  const { run } = useApp();
  const nav = useNavigate();
  const [state, setState] = useState({ status: 'loading', data: null, error: '' });
  const [demoResult, setDemoResult] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  const reload = useCallback(() => setReloadKey(k => k + 1), []);

  useEffect(() => {
    let alive = true;
    setState(s => ({ ...s, status: 'loading', error: '' }));
    admin.fetchDashboardSummary()
      .then(data => { if (alive) setState({ status: 'ready', data, error: '' }); })
      .catch(e => { if (alive) setState({ status: 'error', data: null, error: e?.message || '관리자 대시보드를 불러오지 못했어요.' }); });
    return () => { alive = false; };
  }, [reloadKey]);

  const Stat = ({ label, val, ico, to }) => (
    <button
      type="button"
      className="card stat"
      style={{ textAlign: 'left', cursor: 'pointer', font: 'inherit' }}
      onClick={() => nav(to)}
    >
      <small>{label}</small>
      <div className="value">{val}</div>
      <div className="symbol"><Icon g={ico} size={22} /></div>
    </button>
  );

  const handleCreateTestMembers = async () => {
    const r = await run(
      () => admin.createDemoTestMembers(),
      res => res?.message || '테스트 회원을 준비했습니다.'
    );
    if (r.ok) {
      setDemoResult(r.value);
      reload();
    }
  };

  const handleResetDemo = async () => {
    if (!confirm('시연용 데이터를 모두 초기화할까요?\n(테스트 계모임·증빙·절차 기록이 삭제됩니다)')) return;
    const r = await run(
      () => admin.resetDemoData(),
      res => res?.message || '시연 데이터를 초기화했습니다.'
    );
    if (r.ok) {
      setDemoResult(null);
      reload();
    }
  };

  const stats = state.data?.stats || {};
  const recentLogs = state.data?.recentLogs || [];
  const demoEnabled = Boolean(state.data?.demoEnabled);

  return <>
    <PageHead
      kicker="ADMIN CONSOLE"
      title="플랫폼 관리자 대시보드"
      sub="관리자는 가입 승인 대신 이력을 조회하고, 해산·제재·미납·참조번호·계좌 인증을 처리합니다."
    >
      <button className="btn soft" onClick={reload}>새로고침</button>
    </PageHead>

    <LoadState
      status={state.status}
      error={state.error}
      onRetry={reload}
      loadingText="대시보드 통계를 불러오는 중이에요."
    >
      <div className="grid cols4">
        <Stat label="전체 회원 (활성)" val={`${stats.totalMembers ?? 0}명`} ico="◌" to="/admin/sanctions" />
        <Stat label="전체 계모임" val={`${stats.totalGroups ?? 0}개`} ico="☁" to="/admin/groups" />
        <Stat label="해산 승인 대기" val={`${stats.pendingDissolutions ?? 0}건`} ico="◇" to="/admin/dissolve" />
        <Stat label="가입 제한 회원" val={`${stats.bannedMembers ?? 0}명`} ico="⚑" to="/admin/sanctions" />
      </div>

      <div className="grid cols2 section">
        <Card>
          <h2>최근 운영 이력 (최근 7건)</h2>
          {recentLogs.length ? (
            <div className="tablewrap">
              <table>
                <thead>
                  <tr>
                    <th style={{ whiteSpace: 'nowrap' }}>일시</th>
                    <th style={{ whiteSpace: 'nowrap' }}>모임</th>
                    <th style={{ whiteSpace: 'nowrap' }}>유형</th>
                    <th style={{ whiteSpace: 'nowrap' }}>행위자</th>
                    <th>내용</th>
                  </tr>
                </thead>
                <tbody>
                  {recentLogs.map(log => (
                    <tr key={log.auditLogId}>
                      <td className="muted mini">{log.createdAt || '-'}</td>
                      <td>{log.groupTitle || '공통'}</td>
                      <td>
                        <span className="pill" title={log.eventType}>
                          {DASHBOARD_EVENT_TYPE_LABEL[log.eventType] || log.eventType}
                        </span>
                      </td>
                      <td style={{ whiteSpace: 'nowrap' }}>{log.actorLabel || '-'}</td>
                      <td>{log.description || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <Empty>최근 운영 이력이 없습니다.</Empty>
          )}
        </Card>

        <Card>
          <h2>시연용 데이터 관리</h2>
          <p className="muted mini">
            관리자 전용 테스트 회원 5명 생성 (이메일 중복 방지, 초기 비밀번호 <b>Test1234!</b>)
          </p>
          {demoEnabled ? (
            <div className="actions">
              <button className="btn small primary" onClick={handleCreateTestMembers}>
                테스트 회원 5명 준비
              </button>
              <button className="btn small danger" onClick={handleResetDemo}>
                시연 데이터 초기화
              </button>
            </div>
          ) : (
            <p className="muted mini" style={{ marginTop: 8 }}>
              현재 환경에서는 시연용 데이터 관리 기능이 비활성화되어 있습니다.
            </p>
          )}
          {demoResult && (
            <div className="info mini" style={{ marginTop: 12 }}>
              <b>{demoResult.message}</b>
              {demoResult.createdEmails?.length > 0 && (
                <div style={{ marginTop: 6 }}>
                  생성된 계정 (초기 비밀번호: <code>Test1234!</code>):{' '}
                  {demoResult.createdEmails.join(', ')}
                </div>
              )}
            </div>
          )}
        </Card>
      </div>
    </LoadState>
  </>;
}

/* ---- A02 계모임 관리 및 이력 감사 (F38 · 서버 API 연동) ---- */
export { default as AdminGroups } from './AdminGroupsPage';

/* ---- A03 플랫폼 가입 제한 설정·해제 및 경고 현황 (F39 · 서버 API 연동) ---- */
export { default as AdminSanctions } from './AdminSanctionsPage';

/* ---- A04 해산 승인 · 탈퇴 동의 면제 (F32 · 서버 API 연동) ---- */
export function AdminDissolve() {
  const { run } = useApp();
  const [state, setState] = useState({ status: 'loading', items: [], error: '' });
  const [reloadKey, setReloadKey] = useState(0);
  const reload = useCallback(() => setReloadKey(k => k + 1), []);

  // 탈퇴 정산안 동의 의무 면제 조회 상태
  const [exemptForm, setExemptForm] = useState({ groupId: '', withdrawalId: '' });
  const [targetsState, setTargetsState] = useState({ status: 'idle', items: [], error: '', queried: null });

  useEffect(() => {
    let alive = true;
    setState(s => ({ ...s, status: 'loading', error: '' }));
    admin.fetchAdminDissolutions()
      .then(items => { if (alive) setState({ status: 'ready', items, error: '' }); })
      .catch(e => { if (alive) setState({ status: 'error', items: [], error: e?.message || '해산 신청 목록을 불러오지 못했어요.' }); });
    return () => { alive = false; };
  }, [reloadKey]);

  const loadConsentTargets = async (gidInput = exemptForm.groupId, widInput = exemptForm.withdrawalId) => {
    const gid = Number(gidInput);
    const wid = Number(widInput);
    if (!Number.isSafeInteger(gid) || gid <= 0 || !Number.isSafeInteger(wid) || wid <= 0) {
      setTargetsState({ status: 'error', items: [], error: '모임 번호와 탈퇴 신청 번호를 모두 입력해 주세요.', queried: null });
      return;
    }
    setTargetsState({ status: 'loading', items: [], error: '', queried: { groupId: gid, withdrawalId: wid } });
    try {
      const list = await procedures.fetchWithdrawalConsentTargets(gid, wid);
      setTargetsState({ status: 'ready', items: list, error: '', queried: { groupId: gid, withdrawalId: wid } });
    } catch (e) {
      setTargetsState({
        status: 'error',
        items: [],
        error: e?.message || '동의 대상자 현황을 불러오지 못했어요.',
        queried: { groupId: gid, withdrawalId: wid },
      });
    }
  };

  const handleExempt = async memberId => {
    if (!targetsState.queried) return;
    const reason = prompt('정산안 동의 의무 면제 사유를 입력해 주세요.');
    if (!reason || !reason.trim()) return;
    const { groupId, withdrawalId } = targetsState.queried;
    const r = await run(
      () => procedures.exemptWithdrawalConsentTarget(groupId, withdrawalId, memberId, reason),
      res => res?.message || '동의 의무를 면제했어요.'
    );
    if (r.ok) {
      loadConsentTargets(groupId, withdrawalId);
    }
  };

  const pending = state.items.filter(x => x.status !== 'APPROVED');
  const done = state.items.filter(x => x.status === 'APPROVED');

  return <>
    <PageHead
      kicker="DISSOLUTION REVIEW"
      title="해산 신청 · 승인"
      sub="모임장의 해산 신청을 확인하고 미처리 증빙·진행 중 탈퇴·정산 내역을 검토한 뒤 최종 승인합니다."
    >
      <button className="btn soft" onClick={reload}>새로고침</button>
    </PageHead>

    <LoadState status={state.status} error={state.error} onRetry={reload} loadingText="해산 신청 목록을 불러오는 중이에요.">
      <Card>
        <h2>해산 신청 · 검토 대상 ({pending.length}건)</h2>
        <div className="list">
          {pending.length ? pending.map(item => (
            <div className="list-item" key={item.dissolutionRequestId ?? item.groupId}>
              <div className="item-head">
                <b>{item.groupTitle || `모임 #${item.groupId}`} (#{item.groupId})</b>
                <span className="pill">{item.statusBadge || item.status}</span>
              </div>
              <p>해산 사유: {item.reason}</p>
              <p className="muted mini">
                모임장: {item.leaderName || `회원 #${item.requestedBy}`} · 신청일: {item.requestedAt || '-'} · 회원 {item.memberCount ?? 0}명 · 미처리 증빙 {item.unconfirmedProofCount ?? 0}건 · 진행 중 탈퇴 {item.activeWithdrawalCount ?? 0}건
              </p>
              <p className="mini">
                총 납입 확인 금액(참고): <b>{formatKrw(item.totalConfirmedAmount)}</b> · 1/N 참고 금액: <b>{formatKrw(item.perMemberRefundAmount)}</b>
              </p>
              {item.settlementNotice && (
                <p className="muted mini">{item.settlementNotice}</p>
              )}
              {item.status === 'PENDING' && (
                <div className="actions">
                  <button
                    className="btn primary"
                    onClick={() => confirm('정산 완료와 참여자 보호 조치를 확인했나요? 이 데모는 실제 송금을 검증하지 않습니다.')
                      && run(
                        () => admin.approveAdminDissolution(item.groupId),
                        res => res?.message || '해산을 승인했어요.'
                      ).then(res => res.ok && reload())}
                  >
                    정산 확인 후 최종 승인
                  </button>
                  <button
                    className="btn soft"
                    onClick={() => {
                      const r = prompt('반려 사유를 입력해 주세요.');
                      if (r && r.trim()) {
                        run(
                          () => admin.rejectAdminDissolution(item.groupId, r),
                          res => res?.message || '해산 신청을 반려했어요.'
                        ).then(res => res.ok && reload());
                      }
                    }}
                  >
                    반려
                  </button>
                </div>
              )}
            </div>
          )) : <Empty>검토 대기 중이거나 반려된 해산 신청이 없습니다.</Empty>}
        </div>
      </Card>

      <div className="section">
        <Card>
          <h2>종료된 모임 기록 ({done.length}건)</h2>
          {done.length ? done.map(item => (
            <div className="list-item" key={item.dissolutionRequestId ?? item.groupId}>
              <div className="item-head">
                <b>{item.groupTitle || `모임 #${item.groupId}`} (#{item.groupId})</b>
                <span className="pill">{item.statusBadge || '승인 완료'}</span>
              </div>
              <p className="muted mini">
                {item.reason} · 승인 {item.approvedAt || '-'} · 총 납입 확인 {formatKrw(item.totalConfirmedAmount)}
              </p>
            </div>
          )) : <p className="muted">종료된 모임이 없습니다.</p>}
        </Card>
      </div>
    </LoadState>

    <div className="section">
      <Card>
        <h2>탈퇴 정산안 동의 의무 면제 (관리자)</h2>
        <p className="muted mini">
          장기 미응답·연락 두절·탈퇴 회원 등으로 정산안 동의가 진행되지 않을 때, 대상자를 조회하고 동의 의무를 면제할 수 있습니다.
        </p>
        <form
          onSubmit={e => { e.preventDefault(); loadConsentTargets(); }}
          className="form-grid"
        >
          <Input
            label="모임 번호 (groupId)"
            name="exemptGroupId"
            type="number"
            min={1}
            placeholder="예: 101"
            value={exemptForm.groupId}
            onChange={e => setExemptForm(prev => ({ ...prev, groupId: e.target.value }))}
          />
          <Input
            label="탈퇴 신청 번호 (withdrawalId)"
            name="exemptWithdrawalId"
            type="number"
            min={1}
            placeholder="예: 1"
            value={exemptForm.withdrawalId}
            onChange={e => setExemptForm(prev => ({ ...prev, withdrawalId: e.target.value }))}
          />
          <div className="actions" style={{ alignSelf: 'end' }}>
            <button type="submit" className="btn primary">동의 대상자 조회</button>
          </div>
        </form>

        {targetsState.status !== 'idle' && (
          <div style={{ marginTop: 14 }}>
            <LoadState
              status={targetsState.status}
              error={targetsState.error}
              onRetry={() => loadConsentTargets()}
              loadingText="동의 대상자를 조회하는 중이에요."
            >
              {targetsState.items.length ? (
                <div className="tablewrap">
                  <table>
                    <thead>
                      <tr>
                        <th>회원 ID</th>
                        <th>이름</th>
                        <th>회원 상태</th>
                        <th>동의 상태</th>
                        <th>응답/면제 시각</th>
                        <th>면제 사유</th>
                        <th>조치</th>
                      </tr>
                    </thead>
                    <tbody>
                      {targetsState.items.map(t => (
                        <tr key={t.memberId}>
                          <td>#{t.memberId}</td>
                          <td>{t.memberName || '-'}</td>
                          <td>{t.memberStatus || '-'}</td>
                          <td><span className="pill">{CONSENT_STATUS_LABELS[t.status] || t.status}</span></td>
                          <td className="muted mini">{formatDateTime(t.exemptedAt || t.respondedAt)}</td>
                          <td className="muted mini">{t.exemptionReason || '-'}</td>
                          <td>
                            {t.status === 'PENDING' ? (
                              <button
                                type="button"
                                className="btn small danger"
                                onClick={() => handleExempt(t.memberId)}
                              >
                                동의 면제
                              </button>
                            ) : (
                              <span className="muted mini">-</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <Empty>조회된 동의 대상자가 없습니다.</Empty>
              )}
            </LoadState>
          </div>
        )}
      </Card>
    </div>
  </>;
}

/* ---- A05 참조번호 (F24: 지정 · 정정(사유) — 서버 API 연동) ---- */
const PROOF_STATUS_LABEL = { PENDING: '확인 대기', CONFIRMED: '납입 확인', REVISION: '보완 요청' };
const monthLabel = ym => (ym && ym.length === 6 ? `${ym.slice(0, 4)}-${ym.slice(4)}` : ym || '-');

export function AdminRefs() {
  const { run } = useApp();
  const [tab, setTab] = useState('UNASSIGNED');
  const [state, setState] = useState({ status: 'loading', items: [], error: '' });
  const [reloadKey, setReloadKey] = useState(0);
  const reload = useCallback(() => setReloadKey(k => k + 1), []);

  useEffect(() => {
    let alive = true;
    setState(s => ({ ...s, status: 'loading', error: '' }));
    admin.fetchAdminRefProofs(tab)
        .then(items => { if (alive) setState({ status: 'ready', items, error: '' }); })
        .catch(e => { if (alive) setState({ status: 'error', items: [], error: e?.message || '증빙 목록을 불러오지 못했어요.' }); });
    return () => { alive = false; };
  }, [tab, reloadKey]);

  const assign = async p => {
    let suggestion = '';
    try { suggestion = (await admin.fetchRefNoSuggestion()) || ''; } catch { /* 제안을 못 받아도 직접 입력하거나 비워서 자동 지정 가능 */ }
    const ref = prompt('거래 참조번호를 지정해 주세요. (비우면 다음 번호로 자동 지정)', suggestion);
    if (ref === null) return; // 취소
    const r = await run(() => admin.assignAdminRefNo(p.paymentProofId, ref), res => `참조번호 ${res?.refNo ?? ''}를 지정했어요.`);
    if (r.ok) reload();
  };

  const correct = async p => {
    const ref = prompt('정정할 참조번호를 입력해 주세요.', p.refNo);
    if (ref === null || ref.trim() === p.refNo) return;
    const reason = prompt('정정 사유를 입력해 주세요. (필수)');
    if (reason === null) return;
    const r = await run(() => admin.correctAdminRefNo(p.paymentProofId, ref, reason), '참조번호를 정정했어요.');
    if (r.ok) reload();
  };

  return <>
    <PageHead kicker="EVIDENCE REFERENCES" title="거래 참조번호 관리" sub="증빙은 회원이 등록하고 거래 참조번호는 ADMIN이 지정합니다. 정정은 사유와 함께 이력이 남습니다.">
      <button className="btn soft" onClick={reload}>새로고침</button>
    </PageHead>
    <div className="actions">
      <button className={`btn small ${tab === 'UNASSIGNED' ? 'primary' : 'soft'}`} onClick={() => setTab('UNASSIGNED')}>미지정</button>
      <button className={`btn small ${tab === 'ASSIGNED' ? 'primary' : 'soft'}`} onClick={() => setTab('ASSIGNED')}>지정 완료</button>
    </div>
    <LoadState status={state.status} error={state.error} onRetry={reload} loadingText="증빙 목록을 불러오는 중이에요.">
      <Card><div className="list">
        {state.items.length ? state.items.map(p => (
            <div className="list-item" key={p.paymentProofId}>
              <div className="item-head">
                <b>{p.groupTitle || `모임 #${p.groupId}`} · {monthLabel(p.payMonth)}월분 {p.roundNo}회차 · {formatKrw(p.amount)}</b>
                <span className="pill">{PROOF_STATUS_LABEL[p.status] || p.status}</span>
              </div>
              <p className="muted mini">
                등록자 {p.memberName || '-'} · 납입일 {p.payDate || '-'} · 파일 {p.fileCount ?? 0}개 · 참조번호 <b>{p.refNo || '미지정'}</b>
                {p.refAssignedAt && ` · 지정 ${formatDateTime(p.refAssignedAt)}`}
                {p.refCorrectCount > 0 && ` · 정정 ${p.refCorrectCount}회`}
              </p>
              <div className="actions">
                {p.refNo
                    ? <button className="btn small soft" onClick={() => correct(p)}>참조번호 정정 (사유)</button>
                    : p.status === 'CONFIRMED'
                        ? <button className="btn small primary" onClick={() => assign(p)}>참조번호 지정</button>
                        : <span className="muted mini">납입 확인 후 지정 가능</span>}
              </div>
            </div>
        )) : <Empty>{tab === 'UNASSIGNED' ? '참조번호를 지정할 증빙이 없습니다.' : '참조번호가 지정된 증빙이 없습니다.'}</Empty>}
      </div></Card>
    </LoadState>
  </>;
}

/* ---- A06 미납 관리 (F26: 감지 목록형 — 서버 API 연동) ---- */
const OD_STATUS = { DETECTED: '감지됨 · 미조치', WARNED: '경고 알림 발송', RESTRICTED: '채팅 제한 조치', RESOLVED: '납입 확인 · 해소' };
const OD_ACTION_MSG = { WARN: '경고 알림을 보냈어요.', CHAT_RESTRICT: '채팅을 제한했어요.', CHAT_RELEASE: '채팅 제한을 해제했어요.', ORDER_REVIEW: '수령 후순위 검토를 기록했어요.' };
const OD_POLICY_LABEL = { ALERT_ONLY: '5일차 알림', ALERT_RESTRICT: '5일차 알림 + 채팅 제한·수령 후순위 검토' };

export function AdminOverdue() {
  const { run } = useApp();
  const [state, setState] = useState({ status: 'loading', open: [], done: [], error: '' });
  const [reloadKey, setReloadKey] = useState(0);
  const reload = useCallback(() => setReloadKey(k => k + 1), []);

  useEffect(() => {
    let alive = true;
    setState(s => ({ ...s, status: 'loading', error: '' }));
    Promise.all([admin.fetchAdminOverdueCases('OPEN'), admin.fetchAdminOverdueCases('RESOLVED')])
        .then(([open, done]) => {
          if (alive) setState({ status: 'ready', open: open.slice().sort((a, b) => (b.elapsedDays ?? 0) - (a.elapsedDays ?? 0)), done, error: '' });
        })
        .catch(e => { if (alive) setState({ status: 'error', open: [], done: [], error: e?.message || '미납 목록을 불러오지 못했어요.' }); });
    return () => { alive = false; };
  }, [reloadKey]);

  const act = async (c, action) => {
    let reason;
    if (action === 'WARN') {
      if (!confirm(`${c.memberName}님에게 미납 경고 알림(SMS 포함)을 보낼까요?`)) return;
    } else {
      reason = prompt('조치 사유를 입력해 주세요. (필수)');
      if (reason === null) return;
    }
    const r = await run(() => admin.applyOverdueAction(c.overdueCaseId, action, reason), OD_ACTION_MSG[action]);
    if (r.ok) reload();
  };

  const alertOnly = c => c.overduePolicy === 'ALERT_ONLY'; // 모임 규칙이 '알림만'이면 채팅 제한 불가 (명세 F26-3)
  // 채팅 제한은 회원 단위(GYE_MEMBER) → 같은 회원의 해소 건이 여러 개여도 1명으로 셈
  const restrictedDone = new Set(
      state.done.filter(c => c.chatRestrictedYn === 'Y').map(c => `${c.groupId}-${c.memberId}`)
  ).size;

  return <>
    <PageHead kicker="OVERDUE CONTROL" title="관리자 미납 관리" sub="매일 02:00 배치가 납입일 + 5일 경과 &amp; 납입 확인 없음을 감지해 목록에 올립니다. 관리자는 감지 건을 선택해 조치합니다.">
      <button className="btn soft" onClick={reload}>새로고침</button>
    </PageHead>
    <LoadState status={state.status} error={state.error} onRetry={reload} loadingText="미납 목록을 불러오는 중이에요.">
      <Card>
        <h2>감지된 미납 · {state.open.length}건</h2>
        <p className="muted mini">5일 미만은 시스템이 납입 안내 알림만 자동 발송하며 이 목록에 오르지 않습니다. 실제 입금 조회는 지원하지 않으며 증빙 승인 시 자동 해소됩니다.</p>
        <div className="tablewrap"><table>
          <thead><tr><th>모임</th><th>회원</th><th>귀속 월</th><th>경과일</th><th>상태</th><th>조치</th></tr></thead>
          <tbody>
          {state.open.length ? state.open.map(c => (
              <tr key={c.overdueCaseId}>
                <td>{c.groupTitle || `모임 #${c.groupId}`}<br /><small className="muted">규칙: {OD_POLICY_LABEL[c.overduePolicy] || c.overduePolicy || '-'}</small></td>
                <td>{c.memberName || '-'}</td>
                <td>{monthLabel(c.payMonth)}</td>
                <td>{c.elapsedDays ?? '-'}일</td>
                <td>
                  <span className={`pill ${c.status === 'DETECTED' ? 'warn' : ''}`}>{OD_STATUS[c.status] || c.status}</span>
                  {c.chatRestrictedYn === 'Y' && <><br /><small className="muted">채팅 제한 중</small></>}
                </td>
                <td><div className="actions" style={{ margin: 0 }}>
                  <button className="btn small soft" disabled={c.status !== 'DETECTED'} title={c.status !== 'DETECTED' ? '이미 경고를 보냈거나 경고할 수 없는 상태입니다.' : ''} onClick={() => act(c, 'WARN')}>경고 알림</button>
                  {c.chatRestrictedYn === 'Y'
                      ? <button className="btn small soft" onClick={() => act(c, 'CHAT_RELEASE')}>채팅 제한 해제</button>
                      : <button className="btn small danger" disabled={alertOnly(c)} title={alertOnly(c) ? '이 모임은 미납 시 알림만 보내는 규칙이라 채팅을 제한할 수 없습니다.' : ''} onClick={() => act(c, 'CHAT_RESTRICT')}>채팅 제한</button>}
                  <button className="btn small soft" onClick={() => act(c, 'ORDER_REVIEW')}>후순위 검토</button>
                </div></td>
              </tr>
          )) : <tr><td colSpan={6} className="muted">감지된 미납이 없습니다.</td></tr>}
          </tbody>
        </table></div>
      </Card>
      <div className="section"><Card>
        <details className="policy-fold">
          <summary>
            해소된 건 {state.done.length}건
            {restrictedDone > 0 && ` · 채팅 제한 남은 회원 ${restrictedDone}명`}
          </summary>
          {state.done.length ? state.done.map(c => (
              <p className="muted mini" key={c.overdueCaseId}>
                {c.groupTitle} · {c.memberName} · {monthLabel(c.payMonth)} · 해소 {formatDateTime(c.resolvedAt)}
                {/* 해소돼도 채팅 제한은 그대로 남음 → RESOLVED 건은 해제만 가능 (명세 v3.5 F26-6) */}
                {c.chatRestrictedYn === 'Y' && <>
                  {' · '}<b>채팅 제한 중</b>{' '}
                  <button type="button" className="btn small soft" onClick={() => act(c, 'CHAT_RELEASE')}>채팅 제한 해제</button>
                </>}
              </p>
          )) : <p className="muted mini">없음</p>}
        </details>
      </Card></div>
    </LoadState>
  </>;
}
