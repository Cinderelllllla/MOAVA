/* S10b 모임 절차 — /procedures · 서버 API 연동
   - 수령 순서: /api/v1/groups/{groupId}/payout-requests
   - 중도 탈퇴·정산: /api/v1/groups/{groupId}/withdrawals
   - 계모임 해산: /api/v1/groups/{groupId}/dissolution
   - 운영 규칙(F31): /api/v1/groups/{groupId}/rules (조회: 모임 회원 / 수정: 모임장)
   - 경고·승계: 준비 중
   알림 링크 /procedures?groupId=…&tab=rules 로 들어오면 규칙 탭을 연다. */
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useApp } from '../common/store';
import useMyGroups from '../common/useMyGroups';
import { procedures as P, groups as G } from '../api';
import { PageHead, Card, Flow, Select, Input, Empty } from '../components/ui';
import { LoadState } from '../components/shared';
import { formatKrw } from '../common/format';

const REASON_LABELS = {
  ECONOMIC: '경제적 사정',
  PERSONAL: '개인 사정',
  OTHER: '기타',
};

const CONSENT_STATUS_LABELS = {
  PENDING: '응답 대기',
  AGREED: '동의 완료',
  REJECTED: '거부',
  EXEMPT: '동의 면제',
};

const TAB_KEYS = ['order', 'withdraw', 'dissolve', 'rules'];

const formatDateTime = v => {
  if (!v) return '-';
  if (typeof v === 'string') return v.replace('T', ' ').slice(0, 16);
  try { return new Date(v).toLocaleString('ko-KR'); } catch { return String(v); }
};

export default function Procedures() {
  const { session, ui, setUi } = useApp();
  const nav = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const myGroups = useMyGroups();
  const items = myGroups.items;
  const myMemberId = Number(session?.id || 0);

  const urlGroupId = searchParams.get('groupId') || '';
  const urlTab = searchParams.get('tab') || '';
  const [activeGroupId, setActiveGroupId] = useState(urlGroupId);
  const [manualInput, setManualInput] = useState(urlGroupId);

  // 알림 링크(?tab=rules 등)로 들어오면 그 탭을 연다
  useEffect(() => {
    if (TAB_KEYS.includes(urlTab)) setUi({ procTab: urlTab });
  }, [urlTab]); // eslint-disable-line react-hooks/exhaustive-deps

  // URL ?groupId= 변경 시 반영, 없으면 내가 만든 첫 모임 자동 선택
  useEffect(() => {
    if (urlGroupId) {
      setActiveGroupId(urlGroupId);
      setManualInput(urlGroupId);
    } else if (myGroups.status === 'ready' && !activeGroupId && items.length > 0) {
      const first = String(items[0].groupId);
      setActiveGroupId(first);
      setManualInput(first);
    }
  }, [urlGroupId, myGroups.status, items, activeGroupId]);

  const selectGroup = nextId => {
    const clean = String(nextId || '').trim();
    setActiveGroupId(clean);
    setManualInput(clean);
    if (clean) setSearchParams({ groupId: clean }, { replace: true });
    else setSearchParams({}, { replace: true });
  };

  const applyManualGroup = e => {
    e.preventDefault();
    const n = Number(manualInput);
    if (!Number.isSafeInteger(n) || n <= 0) return;
    selectGroup(String(n));
  };

  const selectedGroup = items.find(x => String(x.groupId) === String(activeGroupId)) || null;
  const isOwner = selectedGroup ? Number(selectedGroup.ownerId) === myMemberId : false;

  const [dataState, setDataState] = useState({
    status: 'idle',
    error: '',
    payouts: [],
    withdrawals: [],
    dissolution: null,
  });
  const [reloadKey, setReloadKey] = useState(0);
  const reload = useCallback(() => setReloadKey(k => k + 1), []);

  useEffect(() => {
    const gid = Number(activeGroupId);
    if (!Number.isSafeInteger(gid) || gid <= 0) {
      setDataState({ status: 'idle', error: '', payouts: [], withdrawals: [], dissolution: null });
      return undefined;
    }
    let alive = true;
    setDataState(prev => ({ ...prev, status: 'loading', error: '' }));
    Promise.all([
      P.fetchPayoutRequests(gid),
      P.fetchWithdrawals(gid),
      P.fetchGroupDissolution(gid),
    ])
      .then(([payouts, withdrawals, dissolution]) => {
        if (!alive) return;
        setDataState({ status: 'ready', error: '', payouts, withdrawals, dissolution });
      })
      .catch(e => {
        if (!alive) return;
        setDataState({
          status: 'error',
          error: e?.message || '모임 절차 정보를 불러오지 못했어요.',
          payouts: [],
          withdrawals: [],
          dissolution: null,
        });
      });
    return () => { alive = false; };
  }, [activeGroupId, reloadKey]);

  const { payouts, withdrawals, dissolution } = dataState;

  const orderCount = isOwner
    ? payouts.filter(r => r.status === 'REQUESTED' || r.status === 'CONSENT_PENDING').length
    : payouts.filter(r =>
        r.status === 'CONSENT_PENDING'
        && Number(r.memberId) !== myMemberId
        && (r.affected || []).map(Number).includes(myMemberId)
        && !(r.consents || []).map(Number).includes(myMemberId)
      ).length;

  const withdrawCount = isOwner
    ? withdrawals.filter(w => w.status === 'SETTLEMENT_PENDING' || w.status === 'CONSENT_PENDING').length
    : withdrawals.filter(w =>
        w.status === 'CONSENT_PENDING'
        && Number(w.memberId) !== myMemberId
        && !(w.consents || []).map(Number).includes(myMemberId)
      ).length;

  const dissolveCount = dissolution?.status === 'PENDING' ? 1 : 0;

  const tabs = [
    ['order', '수령 순서', orderCount],
    ['withdraw', '탈퇴 · 정산', withdrawCount],
    ['dissolve', '해산', dissolveCount],
    ['rules', '규칙 · 경고 · 승계', 0],
  ];
  const currentTab = tabs.some(([k]) => k === ui.procTab) ? ui.procTab : 'order';

  const groupHeading = selectedGroup
    ? `${selectedGroup.title} (#${selectedGroup.groupId})`
    : activeGroupId
      ? `모임 #${activeGroupId}`
      : '';

  return <>
    <PageHead
      kicker="GROUP PROCEDURES"
      title={groupHeading ? `모임 절차 · ${groupHeading}` : '모임 절차'}
      sub="수령 순서 · 중도 탈퇴 정산 · 해산 절차를 서버와 실시간 연동해 처리합니다."
    >
      <button className="btn soft" onClick={() => nav('/operations')}>← 운영 홈</button>
      {activeGroupId && <button className="btn soft" onClick={reload}>새로고침</button>}
    </PageHead>

    <Card>
      <div className="form-grid">
        <div className="field">
          <label htmlFor="proc-my-group">내가 만든 계모임 선택</label>
          <select
            id="proc-my-group"
            value={selectedGroup ? String(selectedGroup.groupId) : ''}
            onChange={e => e.target.value && selectGroup(e.target.value)}
          >
            <option value="">{items.length ? '내 모임 선택' : '내가 만든 모임이 없습니다'}</option>
            {items.map(g => (
              <option key={g.groupId} value={String(g.groupId)}>
                {g.title} (#{g.groupId}) · 모임장
              </option>
            ))}
          </select>
        </div>

        <form onSubmit={applyManualGroup} className="field">
          <label htmlFor="proc-manual-group">참여 모임 번호 직접 입력 (일반 회원용)</label>
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              id="proc-manual-group"
              type="number"
              min={1}
              placeholder="예: 101"
              value={manualInput}
              onChange={e => setManualInput(e.target.value)}
            />
            <button type="submit" className="btn small soft">불러오기</button>
          </div>
        </form>
      </div>

      {activeGroupId && (
        <p className="muted mini" style={{ marginTop: 8 }}>
          현재 선택: <b>{groupHeading}</b>{' '}
          <span className="pill">{isOwner ? '모임장' : '참여 회원'}</span>
        </p>
      )}
    </Card>

    {!activeGroupId ? (
      <Card>
        <Empty>절차를 진행할 모임을 선택하거나 모임 번호를 입력해 주세요.</Empty>
      </Card>
    ) : currentTab === 'rules' ? (
      // 규칙 탭은 수령 순서·탈퇴·해산 데이터와 따로 불러온다 (그쪽이 실패해도 규칙은 볼 수 있게)
      <>
        <ProcTabs tabs={tabs} currentTab={currentTab} onSelect={k => setUi({ procTab: k })} />
        <RulesTab key={activeGroupId} groupId={Number(activeGroupId)} />
      </>
    ) : dataState.status !== 'ready' ? (
      <>
        <ProcTabs tabs={tabs} currentTab={currentTab} onSelect={k => setUi({ procTab: k })} />
        <Card>
          <LoadState
            status={dataState.status === 'idle' ? 'loading' : dataState.status}
            error={dataState.error}
            onRetry={reload}
            loadingText="모임 절차 정보를 불러오는 중이에요."
          />
        </Card>
      </>
    ) : (
      <>
        <div className="proc-tabs">
          {tabs.map(([k, tt, n]) => (
            <button
              type="button"
              key={k}
              className={`tab ${currentTab === k ? 'active' : ''}`}
              onClick={() => setUi({ procTab: k })}
            >
              {tt}
              {n > 0 && <span className="cnt">{n}</span>}
            </button>
          ))}
        </div>

        {currentTab === 'order' && (
          <OrderTab
            groupId={Number(activeGroupId)}
            selectedGroup={selectedGroup}
            isOwner={isOwner}
            myMemberId={myMemberId}
            payouts={payouts}
            onChanged={reload}
          />
        )}
        {currentTab === 'withdraw' && (
          <WithdrawTab
            groupId={Number(activeGroupId)}
            isOwner={isOwner}
            myMemberId={myMemberId}
            withdrawals={withdrawals}
            onChanged={reload}
          />
        )}
        {currentTab === 'dissolve' && (
          <DissolveTab
            groupId={Number(activeGroupId)}
            isOwner={isOwner}
            dissolution={dissolution}
            onChanged={reload}
          />
        )}
      </>
    )}
  </>;
}

/** 탭 버튼 줄 (데이터를 불러오는 중이거나 규칙 탭일 때 사용) */
function ProcTabs({ tabs, currentTab, onSelect }) {
  return (
    <div className="proc-tabs">
      {tabs.map(([k, tt, n]) => (
        <button type="button" key={k} className={`tab ${currentTab === k ? 'active' : ''}`} onClick={() => onSelect(k)}>
          {tt}
          {n > 0 && <span className="cnt">{n}</span>}
        </button>
      ))}
    </div>
  );
}

function OrderTab({ groupId, selectedGroup, isOwner, myMemberId, payouts, onChanged }) {
  const { run } = useApp();
  const mine = payouts.find(r => Number(r.memberId) === myMemberId && ['REQUESTED', 'CONSENT_PENDING'].includes(r.status));
  const myConfirmed = payouts.find(r => Number(r.memberId) === myMemberId && r.status === 'CONFIRMED');
  const maxMembers = selectedGroup?.maxMembers || 0;

  const [roundNo, setRoundNo] = useState(() => String(mine?.roundNo || 1));
  const [reason, setReason] = useState(() => mine?.reason || '');

  useEffect(() => {
    if (mine) {
      setRoundNo(String(mine.roundNo || 1));
      setReason(mine.reason || '');
    }
  }, [mine?.payoutRequestId, mine?.roundNo, mine?.reason]);

  const submit = async () => {
    if (mine && !confirm('기존 수령 순서 신청을 새 내용으로 수정할까요?')) return;
    const r = await run(
      () => P.requestPayoutOrder(groupId, { roundNo: Number(roundNo), reason }),
      res => res?.message || '수령 순서 신청이 반영되었어요.'
    );
    if (r.ok) {
      if (!mine) setReason('');
      onChanged();
    }
  };

  const confirmedList = payouts.filter(r => r.status === 'CONFIRMED').sort((a, b) => (a.roundNo || 0) - (b.roundNo || 0));

  return (
    <Card>
      <h2>수령 순서 신청 · 조정</h2>
      <Flow steps={[['신청', '회원이 희망 회차 제출'], ['동의 요청', '모임장이 전원 동의 발송'], ['동의', '신청자 제외 전원 동의'], ['확정', '모임장 최종 확정']]} />

      {myConfirmed ? (
        <p className="info">
          나의 수령 순서가 <b>{myConfirmed.roundNo}회차</b>로 확정되었습니다.
        </p>
      ) : (
        <>
          <h3>{mine ? '수령 순서 신청 수정' : '수령 순서 신청'}</h3>
          {mine && (
            <p className="info">
              현재 <b>{mine.roundNo}회차</b> 신청이 <b>{mine.statusLabel || mine.status}</b> 상태입니다.
              {mine.aiSuggestedRound != null && <> · AI 제안 회차: <b>{mine.aiSuggestedRound}회차</b></>}
              {mine.aiAnalyzed === false && ' · AI 분석 전(맞춤 추천에서 분석하면 제안 회차가 정확해져요)'}
              {mine.status === 'REQUESTED'
                ? ' 다시 제출하면 기존 신청 내용이 수정됩니다.'
                : ' 동의 요청 진행 중에는 수정할 수 없습니다.'}
            </p>
          )}
          <div className="form-grid">
            {maxMembers > 0 ? (
              <Select
                label="희망 수령 회차"
                name="orderRound"
                options={Array.from({ length: maxMembers }, (_, i) => [String(i + 1), `${i + 1}회차`])}
                value={roundNo}
                onChange={e => setRoundNo(e.target.value)}
                disabled={mine?.status === 'CONSENT_PENDING'}
              />
            ) : (
              <Input
                label="희망 수령 회차"
                name="orderRound"
                type="number"
                min={1}
                value={roundNo}
                onChange={e => setRoundNo(e.target.value)}
                disabled={mine?.status === 'CONSENT_PENDING'}
              />
            )}
            <Input
              label="신청 사유"
              name="orderReason"
              placeholder="희망 회차 신청 사유를 입력해 주세요"
              value={reason}
              onChange={e => setReason(e.target.value)}
              disabled={mine?.status === 'CONSENT_PENDING'}
            />
          </div>
          <div className="actions">
            <button
              className="btn primary"
              disabled={mine?.status === 'CONSENT_PENDING'}
              onClick={submit}
            >
              {mine ? '신청 내용 수정' : '수령 순서 신청'}
            </button>
          </div>
          <div className="divider" />
        </>
      )}

      <h3>신청 · 동의 현황</h3>
      <div className="list">
        {payouts.length ? payouts.map(r => {
          const rid = r.payoutRequestId ?? r.id;
          const consentedIds = (r.consents || []).map(Number);
          const affectedIds = (r.affected || []).map(Number);
          const needMyConsent = r.status === 'CONSENT_PENDING'
            && Number(r.memberId) !== myMemberId
            && affectedIds.includes(myMemberId)
            && !consentedIds.includes(myMemberId);
          const alreadyConsented = r.status === 'CONSENT_PENDING'
            && consentedIds.includes(myMemberId);

          return (
            <div className="list-item" key={rid}>
              <div className="item-head">
                <b>{r.memberName || `회원 #${r.memberId}`} · {r.roundNo}회차</b>
                <span className="pill">{r.statusLabel || r.status}</span>
              </div>
              <p className="muted mini">
                {r.reason}
                {r.aiSuggestedRound != null && ` · AI 제안 ${r.aiSuggestedRound}회차`}
                {r.aiAnalyzed === false && ' · AI 분석 전'}
                {r.status === 'CONSENT_PENDING' && ` · 동의 ${r.agreedCount ?? consentedIds.length}/${r.targetCount ?? affectedIds.length}`}
                {r.createdAt && ` · 신청 ${formatDateTime(r.createdAt)}`}
              </p>
              <div className="actions">
                {isOwner && r.status === 'REQUESTED' && (
                  <button
                    className="btn small primary"
                    onClick={() => run(
                      () => P.requestPayoutConsent(groupId, rid),
                      res => res?.message || '전원에게 동의 요청을 보냈어요.'
                    ).then(res => res.ok && onChanged())}
                  >
                    동의 요청 보내기
                  </button>
                )}
                {isOwner && r.status === 'CONSENT_PENDING' && (
                  <button
                    className="btn small primary"
                    onClick={() => run(
                      () => P.confirmPayoutOrder(groupId, rid),
                      res => res?.message || '수령 순서를 확정했어요.'
                    ).then(res => res.ok && onChanged())}
                  >
                    동의 확인 후 확정
                  </button>
                )}
                {needMyConsent && (
                  <>
                    <button
                      className="btn small primary"
                      onClick={() => run(
                        () => P.submitPayoutConsent(groupId, rid, 'Y'),
                        res => res?.message || '변경 순서에 동의했어요.'
                      ).then(res => res.ok && onChanged())}
                    >
                      변경 순서 동의
                    </button>
                    <button
                      className="btn small danger"
                      onClick={() => confirm('거부하면 이 신청이 반려(재조정) 상태로 전환됩니다. 거부하시겠습니까?')
                        && run(
                          () => P.submitPayoutConsent(groupId, rid, 'N'),
                          res => res?.message || '거부 의사를 기록했어요.'
                        ).then(res => res.ok && onChanged())}
                    >
                      거부
                    </button>
                  </>
                )}
                {alreadyConsented && <span className="pill">동의 완료</span>}
              </div>
            </div>
          );
        }) : <p className="muted">아직 수령 순서 신청이 없습니다.</p>}
      </div>

      {confirmedList.length > 0 && (
        <p className="info" style={{ marginTop: 14 }}>
          확정 순서: {confirmedList.map(o => `${o.memberName || `회원 #${o.memberId}`} ${o.roundNo}회차`).join(', ')}
        </p>
      )}
    </Card>
  );
}

function WithdrawTab({ groupId, isOwner, myMemberId, withdrawals, onChanged }) {
  const { run } = useApp();
  const [reasonType, setReasonType] = useState('PERSONAL');
  const [detail, setDetail] = useState('');
  const [specialYn, setSpecialYn] = useState(false);

  // 동의 대상자 패널 상태: { [withdrawalId]: { open, status, items, error } }
  const [targetsMap, setTargetsMap] = useState({});

  const myActive = withdrawals.find(
    w => Number(w.memberId) === myMemberId && ['SETTLEMENT_PENDING', 'CONSENT_PENDING'].includes(w.status)
  );

  const toggleTargets = async wid => {
    const current = targetsMap[wid];
    if (current?.open) {
      setTargetsMap(prev => ({ ...prev, [wid]: { ...prev[wid], open: false } }));
      return;
    }
    setTargetsMap(prev => ({ ...prev, [wid]: { open: true, status: 'loading', items: [], error: '' } }));
    try {
      const list = await P.fetchWithdrawalConsentTargets(groupId, wid);
      setTargetsMap(prev => ({ ...prev, [wid]: { open: true, status: 'ready', items: list, error: '' } }));
    } catch (e) {
      setTargetsMap(prev => ({
        ...prev,
        [wid]: { open: true, status: 'error', items: [], error: e?.message || '동의 대상자를 불러오지 못했어요.' },
      }));
    }
  };

  const applyWithdrawal = async () => {
    if (!detail.trim()) return;
    if (!confirm('탈퇴 신청 내용을 다시 확인해 주세요. 신청하시겠습니까?')) return;
    const r = await run(
      () => P.requestWithdrawal(groupId, {
        reasonType,
        detail: detail.trim(),
        specialYn: specialYn ? 'Y' : 'N',
      }),
      res => res?.message || '탈퇴 신청이 접수되었어요.'
    );
    if (r.ok) {
      setDetail('');
      setSpecialYn(false);
      onChanged();
    }
  };

  const openSettlementPrompt = w => {
    const wid = w.withdrawalRequestId ?? w.id;
    const defaultAmt = w.settlementAmount != null ? String(w.settlementAmount) : '';
    const amtInput = prompt(
      '정산 금액(원 단위)을 입력해 주세요.\n(환급은 양수 예: 200000, 추가 납부는 음수 예: -100000)',
      defaultAmt
    );
    if (amtInput === null) return;
    const memoInput = prompt('정산 산출 근거(메모)를 입력해 주세요.', w.settlementMemo || '');
    if (memoInput === null) return;
    run(
      () => P.registerSettlement(groupId, wid, { settlementAmount: Number(amtInput), settlementMemo: memoInput }),
      res => res?.message || '정산안을 등록하고 전원에게 동의 요청을 보냈어요.'
    ).then(res => res.ok && onChanged());
  };

  return (
    <Card>
      <h2>중도 탈퇴 · 정산 동의</h2>
      <Flow steps={[['신청', '탈퇴 사유 제출'], ['정산안', '모임장이 금액(원)·근거 작성'], ['동의', '나머지 회원 전원 동의'], ['승인', '모임장 최종 탈퇴 처리']]} />

      {!isOwner ? (
        <>
          <h3>중도 탈퇴 신청</h3>
          <p className="muted mini">
            곗돈 수령 완료자는 일반 탈퇴가 제한되며 특별 정산 예외 요청만 가능합니다.
          </p>
          {myActive ? (
            <div className="info">
              신청 상태: <b>{myActive.statusLabel || myActive.status}</b>
              {myActive.settlementAmount != null && (
                <> · 정산 금액 <b>{formatKrw(myActive.settlementAmount)}</b> · 동의 {myActive.agreedCount ?? 0}/{myActive.targetCount ?? 0}</>
              )}
              <div className="actions" style={{ marginTop: 8 }}>
                <button
                  className="btn small soft"
                  onClick={() => run(
                    () => P.reconfirmWithdrawal(groupId, myActive.withdrawalRequestId ?? myActive.id),
                    res => res?.message || '재확인 요청을 보냈어요.'
                  ).then(res => res.ok && onChanged())}
                >
                  재확인 요청 ({myActive.reconfirmCount ?? 0}회)
                </button>
              </div>
            </div>
          ) : (
            <>
              <div className="field">
                <label htmlFor="withdraw-reason-type">탈퇴 사유 분류</label>
                <select
                  id="withdraw-reason-type"
                  value={reasonType}
                  onChange={e => setReasonType(e.target.value)}
                >
                  <option value="ECONOMIC">경제적 사정</option>
                  <option value="PERSONAL">개인 사정</option>
                  <option value="OTHER">기타</option>
                </select>
              </div>
              <div className="field">
                <label htmlFor="withdraw-detail">상세 사유</label>
                <textarea
                  id="withdraw-detail"
                  value={detail}
                  onChange={e => setDetail(e.target.value)}
                  placeholder="탈퇴 사유를 작성해 주세요."
                />
              </div>
              <label className="checkline" style={{ marginBottom: 12 }}>
                <input
                  type="checkbox"
                  checked={specialYn}
                  onChange={e => setSpecialYn(e.target.checked)}
                />
                <span>특별 정산 예외 요청 (곗돈 수령 완료자인 경우 선택)</span>
              </label>
              <div className="actions">
                <button
                  className="btn primary"
                  disabled={!detail.trim()}
                  onClick={applyWithdrawal}
                >
                  중도 탈퇴 신청
                </button>
              </div>
            </>
          )}
          <div className="divider" />
        </>
      ) : (
        <p className="info">
          회원의 탈퇴 신청에 대해 모임장이 <b>정산 금액(원 단위)·산출 근거</b>를 등록하면 나머지 회원 전원에게 동의 요청이 발송됩니다.
        </p>
      )}

      <h3>탈퇴 · 정산 현황</h3>
      <div className="list">
        {withdrawals.length ? withdrawals.map(w => {
          const wid = w.withdrawalRequestId ?? w.id;
          const consentedIds = (w.consents || []).map(Number);
          const needMyConsent = w.status === 'CONSENT_PENDING'
            && Number(w.memberId) !== myMemberId
            && !consentedIds.includes(myMemberId);
          const alreadyConsented = w.status === 'CONSENT_PENDING' && consentedIds.includes(myMemberId);
          const canRegisterSettlement = isOwner && ['SETTLEMENT_PENDING', 'CONSENT_PENDING', 'REJECTED'].includes(w.status);
          const tState = targetsMap[wid];

          return (
            <div className="list-item" key={wid}>
              <div className="item-head">
                <b>{w.memberName || `회원 #${w.memberId}`}의 탈퇴 · 정산 (#{wid})</b>
                <span className="pill">
                  {w.statusLabel || w.status}
                  {(w.special || w.specialYn === 'Y') && ' · 특별 정산'}
                </span>
              </div>
              <p className="muted mini">
                {REASON_LABELS[w.reasonType] || w.reasonType} · {w.detail || w.reason} · 신청 {formatDateTime(w.createdAt)}
                {(w.reconfirmCount ?? 0) > 0 && ` · 재확인 요청 ${w.reconfirmCount}회`}
              </p>
              {w.settlementAmount != null ? (
                <p className="mini">
                  정산 금액{' '}
                  <b>
                    {formatKrw(w.settlementAmount)}
                    {w.settlementAmount < 0 ? ' (추가 납부)' : ' (환급)'}
                  </b>
                  {w.settlementMemo && ` · 근거: ${w.settlementMemo}`}
                  {' · '}동의 <b>{w.agreedCount ?? consentedIds.length}/{w.targetCount ?? 0}</b>
                  {(w.exemptedCount ?? 0) > 0 && ` (면제 ${w.exemptedCount}명 포함)`}
                </p>
              ) : (
                <p className="mini muted">정산안이 아직 작성되지 않았습니다.</p>
              )}

              <div className="actions">
                {canRegisterSettlement && (
                  <button className="btn small primary" onClick={() => openSettlementPrompt(w)}>
                    {w.status === 'SETTLEMENT_PENDING' ? '정산안 작성' : '정산안 재작성'}
                  </button>
                )}
                {needMyConsent && (
                  <>
                    <button
                      className="btn small primary"
                      onClick={() => run(
                        () => P.submitWithdrawalConsent(groupId, wid, 'Y'),
                        res => res?.message || '정산안에 동의했어요.'
                      ).then(res => res.ok && onChanged())}
                    >
                      정산안 동의
                    </button>
                    <button
                      className="btn small danger"
                      onClick={() => confirm('정산안을 거부하면 재협의(반려) 상태로 전환됩니다. 거부하시겠습니까?')
                        && run(
                          () => P.submitWithdrawalConsent(groupId, wid, 'N'),
                          res => res?.message || '정산안 거부를 기록했어요.'
                        ).then(res => res.ok && onChanged())}
                    >
                      거부
                    </button>
                  </>
                )}
                {alreadyConsented && <span className="pill">동의 완료</span>}
                {isOwner && w.status === 'CONSENT_PENDING' && (
                  <button
                    className="btn small primary"
                    onClick={() => confirm(`정산(${formatKrw(w.settlementAmount)}) 완료 및 전원 동의를 확인했나요? 실제 송금은 이루어지지 않습니다.`)
                      && run(
                        () => P.approveWithdrawal(groupId, wid),
                        res => res?.message || '탈퇴를 최종 승인했어요.'
                      ).then(res => res.ok && onChanged())}
                  >
                    정산·동의 확인 후 승인
                  </button>
                )}
                {['CONSENT_PENDING', 'APPROVED', 'REJECTED'].includes(w.status) && (
                  <button className="btn small soft" onClick={() => toggleTargets(wid)}>
                    {tState?.open ? '동의 대상자 숨기기' : '동의 대상자 현황'}
                  </button>
                )}
              </div>

              {tState?.open && (
                <div className="policy-panel" style={{ marginTop: 10 }}>
                  <h3>정산안 동의 대상자 현황</h3>
                  <LoadState status={tState.status} error={tState.error} onRetry={() => toggleTargets(wid)} loadingText="동의 대상자를 불러오는 중이에요.">
                    {tState.items.length ? (
                      <div className="tablewrap">
                        <table className="policy-table">
                          <thead>
                            <tr>
                              <th>회원</th>
                              <th>회원 상태</th>
                              <th>동의 상태</th>
                              <th>응답/면제 시각</th>
                              <th>비고</th>
                            </tr>
                          </thead>
                          <tbody>
                            {tState.items.map(t => (
                              <tr key={t.memberId}>
                                <td>{t.memberName || `회원 #${t.memberId}`}</td>
                                <td>{t.memberStatus || '-'}</td>
                                <td><span className="pill">{CONSENT_STATUS_LABELS[t.status] || t.status}</span></td>
                                <td className="muted mini">{formatDateTime(t.exemptedAt || t.respondedAt)}</td>
                                <td className="muted mini">{t.exemptionReason || '-'}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <p className="muted mini">동의 대상자가 없습니다.</p>
                    )}
                  </LoadState>
                </div>
              )}
            </div>
          );
        }) : <p className="muted">진행 중이거나 기록된 탈퇴·정산 건이 없습니다.</p>}
      </div>
    </Card>
  );
}

function DissolveTab({ groupId, isOwner, dissolution, onChanged }) {
  const { run } = useApp();
  const [reason, setReason] = useState('');

  const canApply = isOwner && (!dissolution || dissolution.status === 'REJECTED');

  const apply = async () => {
    if (!reason.trim()) return;
    if (!confirm('계모임 해산 신청을 접수할까요? 관리자 검토 및 승인 후 해산이 완료됩니다.')) return;
    const r = await run(
      () => P.requestDissolution(groupId, reason.trim()),
      res => res?.message || '해산 신청을 접수했어요.'
    );
    if (r.ok) {
      setReason('');
      onChanged();
    }
  };

  return (
    <Card>
      <h2>계모임 해산</h2>
      <Flow steps={[['신청', '모임장이 사유 제출'], ['검토', 'ADMIN이 증빙·탈퇴·정산 확인'], ['승인', '해산 완료 및 기록 보관']]} />
      <p>
        현재 상태: <span className="pill">{dissolution?.statusBadge || '운영 중'}</span>
      </p>

      {dissolution && (
        <div className="info">
          <b>신청 사유:</b> {dissolution.reason}
          <br />
          신청자: {dissolution.leaderName || `회원 #${dissolution.requestedBy}`} · 신청일: {dissolution.requestedAt || '-'}
          {dissolution.approvedAt && <> · 승인일: {dissolution.approvedAt}</>}
          <br />
          참여 회원 {dissolution.memberCount ?? 0}명 · 미처리 증빙 {dissolution.unconfirmedProofCount ?? 0}건 · 진행 중 탈퇴 {dissolution.activeWithdrawalCount ?? 0}건
          <br />
          총 납입 확인 금액(참고): <b>{formatKrw(dissolution.totalConfirmedAmount)}</b> · 1/N 참고 금액: <b>{formatKrw(dissolution.perMemberRefundAmount)}</b>
          {dissolution.settlementNotice && (
            <>
              <br />
              <span className="muted mini">{dissolution.settlementNotice}</span>
            </>
          )}
        </div>
      )}

      {canApply ? (
        <>
          <p className="muted mini" style={{ marginTop: 12 }}>
            미처리 증빙이나 진행 중인 탈퇴 건이 남아 있으면 관리자가 승인하지 않습니다. 해산 전 모든 정산을 마무리해 주세요.
          </p>
          <div className="field">
            <label htmlFor="dissolve-reason">해산 사유</label>
            <textarea
              id="dissolve-reason"
              value={reason}
              onChange={e => setReason(e.target.value)}
              placeholder="계모임 해산 사유를 입력해 주세요."
            />
          </div>
          <div className="actions">
            <button className="btn danger" disabled={!reason.trim()} onClick={apply}>
              해산 신청
            </button>
          </div>
        </>
      ) : !isOwner ? (
        <p className="muted mini" style={{ marginTop: 12 }}>해산 신청은 모임장만 할 수 있습니다.</p>
      ) : null}
    </Card>
  );
}

/* F31 운영 규칙 — 모든 회원: 최신 규칙 + 채팅으로 이동 / 모임장: 화면 안 수정 폼
   규칙은 JSX 텍스트로만 그린다(React 기본 이스케이프). dangerouslySetInnerHTML 금지. */
function RulesTab({ groupId }) {
  const nav = useNavigate();
  const { toast } = useApp();
  const [state, setState] = useState({ status: 'loading', data: null, error: '' });
  const [form, setForm] = useState({ rulesTitle: '', rulesBody: '' });
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let alive = true;
    setState(s => ({ ...s, status: 'loading', error: '' }));
    G.fetchRules(groupId)
      .then(data => {
        if (!alive) return;
        setState({ status: 'ready', data, error: '' });
        setForm({ rulesTitle: data?.rulesTitle || '', rulesBody: data?.rulesBody || '' });
      })
      .catch(e => { if (alive) setState({ status: 'error', data: null, error: e?.message || '운영 규칙을 불러오지 못했어요.' }); });
    return () => { alive = false; };
  }, [groupId, reloadKey]);

  const rules = state.data;
  const dissolved = rules?.groupStatus === 'DISSOLVED';
  const canEdit = !!rules?.leader && !dissolved;
  const titleBytes = G.utf8Bytes(form.rulesTitle);
  const titleTooLong = titleBytes > G.RULES_TITLE_MAX_BYTES;
  const empty = !form.rulesTitle.trim() || !form.rulesBody.trim();
  const unchanged = rules && form.rulesTitle.trim() === (rules.rulesTitle || '').trim()
    && form.rulesBody.trim() === (rules.rulesBody || '').trim();

  const save = async e => {
    e.preventDefault();
    if (saving || empty || titleTooLong || unchanged) return;
    setSaving(true);
    setSaveError('');
    try {
      const saved = await G.updateRules(groupId, { rulesTitle: form.rulesTitle, rulesBody: form.rulesBody });
      setState({ status: 'ready', data: saved, error: '' });
      setForm({ rulesTitle: saved?.rulesTitle || '', rulesBody: saved?.rulesBody || '' });
      toast('운영 규칙을 저장했어요. 모임 회원에게 알림과 채팅 안내가 전송돼요.');
    } catch (err) {
      setSaveError(err?.message || '운영 규칙을 저장하지 못했어요.');
    } finally {
      setSaving(false);
    }
  };

  return <>
    <Card>
      <h2>운영 규칙</h2>
      <LoadState
        status={state.status}
        error={state.error}
        onRetry={() => setReloadKey(k => k + 1)}
        loadingText="운영 규칙을 불러오는 중이에요."
      >
        {rules && <>
          <div className="policy-panel">
            <h3>{rules.rulesTitle || '등록된 규칙 제목이 없어요'}</h3>
            <p style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', margin: 0 }}>
              {rules.rulesBody || '등록된 규칙 내용이 없어요.'}
            </p>
          </div>
          <div className="actions" style={{ marginTop: 12 }}>
            <button type="button" className="btn soft" onClick={() => nav(`/chat?groupId=${groupId}`)}>채팅으로 이동</button>
          </div>

          {canEdit ? (
            <form onSubmit={save} style={{ marginTop: 20 }}>
              <h3>규칙 수정 (모임장)</h3>
              <p className="muted mini">저장하면 모임 회원에게 알림이 가고, 채팅방에 변경 안내가 남아요. 수정 이력은 감사 로그에 기록돼요.</p>
              <div className="field">
                <label htmlFor="rules-title">규칙 제목</label>
                <input
                  id="rules-title"
                  value={form.rulesTitle}
                  onChange={e => setForm(f => ({ ...f, rulesTitle: e.target.value }))}
                  placeholder="예: 매월 5일 납입, 지연 시 채팅으로 먼저 알리기"
                  aria-describedby="rules-title-bytes"
                />
                <small id="rules-title-bytes" className={titleTooLong ? 'warning' : 'muted'}>
                  {titleBytes} / {G.RULES_TITLE_MAX_BYTES}바이트 · 한글은 1자에 3바이트라 약 33자까지 쓸 수 있어요.
                </small>
              </div>
              <div className="field">
                <label htmlFor="rules-body">규칙 내용</label>
                <textarea
                  id="rules-body"
                  rows={8}
                  value={form.rulesBody}
                  onChange={e => setForm(f => ({ ...f, rulesBody: e.target.value }))}
                  placeholder="납입일, 지연 시 안내 방법, 수령 순서 정하는 방법 등을 적어 주세요."
                />
              </div>
              {saveError && <p className="warning mini" role="alert">{saveError}</p>}
              <div className="actions">
                <button type="submit" className="btn primary" disabled={saving || empty || titleTooLong || unchanged}>
                  {saving ? '저장 중…' : '규칙 저장'}
                </button>
                {!saving && !unchanged && (
                  <button type="button" className="btn soft" onClick={() => {
                    setForm({ rulesTitle: rules.rulesTitle || '', rulesBody: rules.rulesBody || '' });
                    setSaveError('');
                  }}>되돌리기</button>
                )}
              </div>
              {empty && <p className="muted mini">제목과 내용을 모두 입력해야 저장할 수 있어요.</p>}
            </form>
          ) : rules.leader && dissolved ? (
            <p className="muted mini" style={{ marginTop: 12 }}>해산된 모임이라 규칙을 수정할 수 없어요.</p>
          ) : (
            <p className="muted mini" style={{ marginTop: 12 }}>운영 규칙은 모임장만 수정할 수 있어요.</p>
          )}
        </>}
      </LoadState>
    </Card>

    <Card>
      <h2>회원 경고 · 모임장 승계</h2>
      <Empty>회원 경고·강제 탈퇴, 모임장 승계 기능은 서버 API 연결 준비 중이에요.</Empty>
    </Card>
  </>;
}
