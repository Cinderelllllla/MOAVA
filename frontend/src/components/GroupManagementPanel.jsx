/* F16, F18 계모임 관리 패널
   모임장 전용 참여 회원 및 가입 신청 관리
   비공개 모임 인증코드 발급 및 초대 링크 복사
*/

import {
  useState,
  useEffect,
  useRef,
  useCallback
} from 'react';

import { useSearchParams } from 'react-router-dom';
import { groups } from '../api';

export default function GroupManagementPanel({
  g,
  onReload
}) {
  // 모임 변경 시 관리 상태 초기화
  return (
    <GroupManagement
      key={g.groupId}
      g={g}
      onReload={onReload}
    />
  );
}

function GroupManagement({ g, onReload }) {
  const [params] = useSearchParams();

  const groupId = g.groupId;
  const isOwner = g.role === 'OWNER';
  const isPrivate = g.visibility === 'PRIVATE';

  const requestedGroupId = params.get('groupId');
  const requestedTab = params.get('tab');

  const autoOpenedRef = useRef(null);

  // 인증코드 발급 상태
  const [issuing, setIssuing] = useState(false);
  const [issuedCode, setIssuedCode] = useState('');
  const [issueError, setIssueError] = useState('');

  // 초대 링크 조회 상태
  const [copying, setCopying] = useState(false);

  // 참여 회원 조회 상태
  const [openMembers, setOpenMembers] = useState(false);
  const [members, setMembers] = useState([]);
  const [membersLoading, setMembersLoading] = useState(false);
  const [membersError, setMembersError] = useState('');

  // 가입 신청 관리 상태
  const [openJoin, setOpenJoin] = useState(false);
  const [joinRequests, setJoinRequests] = useState([]);
  const [joinLoading, setJoinLoading] = useState(false);
  const [joinProcessingId, setJoinProcessingId] = useState(null);
  const [joinError, setJoinError] = useState('');

  // 모집 마감 (RECRUITING -> ACTIVE) 상태
  const [closing, setClosing] = useState(false);
  const [closeError, setCloseError] = useState('');
  const isRecruiting = g.status === 'RECRUITING';
  const currentMembersCount = Number(g.currentMembers || 0);
  const maxMembersCount = Number(g.maxMembers || 0);
  const minCloseMembers = Math.max(2, Math.ceil((maxMembersCount || 2) / 2));
  const canCloseRecruitment = isRecruiting && currentMembersCount >= minCloseMembers;

  // =========================================================
  // F18: 인증코드 발급 및 재발급
  // =========================================================

  const handleIssueJoinCode = async () => {
    if (!isOwner || !isPrivate || issuing) return;

    const confirmed = window.confirm(
      '인증코드를 발급하시겠어요?\n\n' +
      '기존 인증코드가 있다면 무효화되고 새 코드가 발급됩니다.'
    );

    if (!confirmed) return;

    setIssuing(true);
    setIssuedCode('');
    setIssueError('');

    try {
      const result = await groups.issueJoinCode(groupId);

      if (!/^\d{6}$/.test(String(result?.joinCode ?? ''))) {
        throw new Error(
          '인증코드 발급 결과를 확인하지 못했어요.'
        );
      }

      setIssuedCode(String(result.joinCode));

    } catch (error) {
      setIssueError(
        error?.message ||
        '인증코드 발급에 실패했어요.'
      );

    } finally {
      setIssuing(false);
    }
  };

  // =========================================================
  // F18: 초대 링크 복사
  // =========================================================

  const handleCopyInviteLink = async () => {
    if (!isOwner || !isPrivate || copying) return;

    if (
      !Number.isSafeInteger(Number(groupId)) ||
      Number(groupId) <= 0
    ) {
      window.alert('올바르지 않은 모임 ID입니다.');
      return;
    }

    setCopying(true);

    try {
      const result = await groups.fetchInviteLink(groupId);
      const inviteToken = result?.inviteToken;

      if (
        typeof inviteToken !== 'string' ||
        !/^[0-9a-fA-F]{32}$/.test(inviteToken)
      ) {
        throw new Error(
          '초대 토큰을 확인하지 못했어요.'
        );
      }

      const inviteUrl = new URL(
        '/invite',
        window.location.origin
      );

      inviteUrl.searchParams.set('t', inviteToken);

      try {
        await navigator.clipboard.writeText(
          inviteUrl.toString()
        );

        window.alert(
          '초대 링크를 복사했어요!\n' +
          '인증코드는 별도로 전달해 주세요.'
        );

      } catch {
        window.prompt(
          '아래 초대 링크를 직접 복사해 주세요.',
          inviteUrl.toString()
        );
      }

    } catch (error) {
      window.alert(
        error?.message ||
        '초대 링크를 가져오지 못했어요.'
      );

    } finally {
      setCopying(false);
    }
  };

  // =========================================================
  // F16: 참여 회원 목록 조회
  // =========================================================

  const loadGroupMembers = async () => {
    if (!isOwner) return false;

    setMembersLoading(true);
    setMembersError('');

    try {
      const data = await groups.fetchGroupMembers(groupId);

      setMembers(data);

      return true;

    } catch (error) {
      setMembersError(
        error?.message ||
        '참여 회원 목록을 불러오지 못했어요.'
      );

      return false;

    } finally {
      setMembersLoading(false);
    }
  };

  const handleToggleMembers = async () => {
    if (!isOwner || membersLoading) return;

    if (openMembers) {
      setOpenMembers(false);
      return;
    }

    setOpenMembers(true);

    await loadGroupMembers();
  };

  // =========================================================
  // F16: 가입 신청 목록 조회
  // =========================================================

  const loadJoinRequests = useCallback(async () => {
    if (!isOwner) return false;

    setJoinLoading(true);
    setJoinError('');

    try {
      const data = await groups.fetchJoinRequests(groupId);

      setJoinRequests(
        data.filter(r => r.status === 'PENDING')
      );

      return true;

    } catch (error) {
      setJoinError(
        error?.message ||
        '가입 신청 목록을 불러오지 못했어요.'
      );

      return false;

    } finally {
      setJoinLoading(false);
    }
  }, [groupId, isOwner]);

  // =========================================================
  // F16: 알림 클릭 시 가입 신청 관리 자동 열기
  // /operations?groupId=13&tab=joinRequests
  // =========================================================

  useEffect(() => {
    if (
      !isOwner ||
      requestedTab !== 'joinRequests' ||
      requestedGroupId !== String(groupId)
    ) {
      autoOpenedRef.current = null;
      return;
    }

    const requestKey =
      `${requestedGroupId}:${requestedTab}`;

    if (autoOpenedRef.current === requestKey) return;

    autoOpenedRef.current = requestKey;

    setOpenJoin(true);

    loadJoinRequests();

  }, [
    isOwner,
    groupId,
    requestedGroupId,
    requestedTab,
    loadJoinRequests
  ]);

  // 자동으로 열린 가입 신청 영역으로 이동
  useEffect(() => {
    if (
      !isOwner ||
      !openJoin ||
      requestedTab !== 'joinRequests' ||
      requestedGroupId !== String(groupId)
    ) {
      return;
    }

    const frameId = requestAnimationFrame(() => {
      document
        .getElementById(`join-requests-${groupId}`)
        ?.scrollIntoView({
          behavior: 'smooth',
          block: 'start'
        });
    });

    return () => cancelAnimationFrame(frameId);

  }, [
    isOwner,
    openJoin,
    groupId,
    requestedGroupId,
    requestedTab
  ]);

  // =========================================================
  // F16: 가입 신청 관리 열기 및 닫기
  // =========================================================

  const handleToggleJoinRequests = async () => {
    if (
      !isOwner ||
      joinLoading ||
      joinProcessingId !== null
    ) {
      return;
    }

    if (openJoin) {
      setOpenJoin(false);
      return;
    }

    setOpenJoin(true);

    await loadJoinRequests();
  };

  // =========================================================
  // F16: 가입 승인 및 거절
  // =========================================================

  const handleJoinDecision = async (request, action) => {
    if (
      !isOwner ||
      joinLoading ||
      joinProcessingId !== null
    ) {
      return;
    }

    if (action !== 'approve' && action !== 'reject') {
      return;
    }

    const isApprove = action === 'approve';
    const actionLabel = isApprove ? '승인' : '거절';

    const displayName =
      request.memberName ||
      `회원 #${request.memberId}`;

    if (!window.confirm(
      `${displayName}님의 가입 신청을 ${actionLabel}하시겠어요?`
    )) {
      return;
    }

    setJoinProcessingId(request.joinRequestId);
    setJoinError('');

    try {
      if (isApprove) {
        await groups.approveJoinRequest(
          groupId,
          request.joinRequestId
        );
      } else {
        await groups.rejectJoinRequest(
          groupId,
          request.joinRequestId
        );
      }

    } catch (error) {
      setJoinError(
        error?.message ||
        `가입 신청 ${actionLabel}에 실패했어요.`
      );

      setJoinProcessingId(null);
      return;
    }

    // 승인·거절 성공 후 관련 목록 갱신
    try {
      const refreshed = await loadJoinRequests();

      let refreshFailed = !refreshed;

      if (isApprove && openMembers) {
        const membersRefreshed = await loadGroupMembers();

        if (!membersRefreshed) {
          refreshFailed = true;
        }
      }

      if (isApprove && typeof onReload === 'function') {
        try {
          await onReload();
        } catch {
          refreshFailed = true;
        }
      }

      if (refreshFailed) {
        window.alert(
          `가입 신청 ${actionLabel}은 완료됐지만, ` +
          '일부 목록을 새로고침하지 못했어요.'
        );
      } else {
        window.alert(
          `가입 신청 ${actionLabel} 처리가 완료됐어요.`
        );
      }

    } finally {
      setJoinProcessingId(null);
    }
  };

  // =========================================================
  // 모집 마감 (모임장 수동 마감: 정원 절반 이상 충족 시)
  // =========================================================

  const handleCloseRecruitment = async () => {
    if (!isOwner || !isRecruiting || closing) return;

    if (!canCloseRecruitment) {
      setCloseError(
        `모집 마감은 정원의 절반 이상(최소 ${minCloseMembers}명)이 모여야 가능합니다. ` +
        `(현재 ${currentMembersCount}/${maxMembersCount}명)`
      );
      return;
    }

    const confirmed = window.confirm(
      `현재 ${currentMembersCount}/${maxMembersCount}명으로 모집을 마감하고 모임 운영을 시작할까요?\n\n` +
      '• 대기 중인 가입 신청은 자동으로 거절됩니다.\n' +
      '• 마감 후 월 납입 증빙 등록·승인 및 수령 순서 확정을 진행할 수 있습니다.'
    );

    if (!confirmed) return;

    setClosing(true);
    setCloseError('');

    try {
      const result = await groups.closeGroupRecruitment(groupId);

      if (openJoin) {
        try {
          await loadJoinRequests();
        } catch {
          // 목록 갱신 실패 시에도 마감 완료 안내 유지
        }
      }

      if (typeof onReload === 'function') {
        try {
          await onReload();
        } catch {
          // 상위 리로드 실패 시에도 마감 완료 안내 유지
        }
      }

      const rejectedCount = Number(result?.rejectedPendingCount || 0);
      const suffix = rejectedCount > 0
        ? `\n(대기 중이던 가입 신청 ${rejectedCount}건은 자동 거절되었습니다.)`
        : '';
      window.alert(
        (result?.message || '모집을 마감하고 모임 운영을 시작했어요.') + suffix
      );
    } catch (error) {
      setCloseError(
        error?.message ||
        '모집 마감 처리에 실패했어요.'
      );
    } finally {
      setClosing(false);
    }
  };

  // 모임장만 관리 패널 사용 가능
  if (!isOwner) return null;

  // =========================================================
  // 화면 렌더링
  // =========================================================

  return (
    <div
      className="policy-panel"
      style={{ marginTop: 16 }}
    >
      <div className="item-head">
        <h3>모임장 관리 기능</h3>

        <span className="pill">
          모임장 전용
        </span>
      </div>

      <div className="actions">

        {/* 모집 마감 (운영 시작) */}
        {isRecruiting && (
          <button
            type="button"
            className="btn primary"
            disabled={closing || !canCloseRecruitment}
            onClick={handleCloseRecruitment}
          >
            {closing
              ? '모집 마감 처리 중...'
              : '모집 마감 (운영 시작)'}
          </button>
        )}

        {/* 비공개 모임 인증코드 발급 */}
        {isPrivate && (
          <button
            type="button"
            className="btn soft"
            disabled={issuing}
            onClick={handleIssueJoinCode}
          >
            {issuing
              ? '발급 중...'
              : '인증코드 발급·재발급'}
          </button>
        )}

        {/* 비공개 모임 초대 링크 */}
        {isPrivate && (
          <button
            type="button"
            className="btn soft"
            disabled={copying}
            onClick={handleCopyInviteLink}
          >
            {copying
              ? '링크 가져오는 중...'
              : '초대 링크 복사'}
          </button>
        )}

        {/* 참여 회원 목록 */}
        <button
          type="button"
          className="btn soft"
          disabled={membersLoading}
          onClick={handleToggleMembers}
        >
          {openMembers
            ? '참여 회원 목록 닫기'
            : '참여 회원 보기'}
        </button>

        {/* 가입 신청 관리 */}
        <button
          type="button"
          className="btn soft"
          disabled={
            joinLoading ||
            joinProcessingId !== null
          }
          onClick={handleToggleJoinRequests}
        >
          {openJoin
            ? '가입 신청 관리 닫기'
            : '가입 신청 관리'}
        </button>

      </div>

      {/* 모집 마감 안내 */}
      {isRecruiting && (
        <p className="muted" style={{ marginTop: 10 }}>
          {canCloseRecruitment
            ? `현재 ${currentMembersCount}/${maxMembersCount}명 참여 중이에요. 최소 인원(${minCloseMembers}명)을 충족해 모집을 마감하고 운영을 시작할 수 있어요. (정원이 모두 차면 자동 마감)`
            : `현재 ${currentMembersCount}/${maxMembersCount}명 참여 중이에요. 정원의 절반 이상(최소 ${minCloseMembers}명)이 모이면 모집을 마감할 수 있어요. (정원이 모두 차면 자동 마감)`}
        </p>
      )}

      {/* 모집 마감 오류 */}
      {isRecruiting && closeError && (
        <p
          role="alert"
          style={{
            color: '#c0392b',
            marginTop: 8
          }}
        >
          {closeError}
        </p>
      )}

      {/* 발급된 인증코드 표시 */}
      {isPrivate && issuedCode && (
        <div
          className="policy-lock"
          style={{ marginTop: 12 }}
        >
          <p>
            <strong>
              비공개 모임 인증코드
            </strong>
          </p>

          <p
            style={{
              fontSize: 26,
              fontWeight: 700,
              letterSpacing: 6,
              margin: '12px 0'
            }}
          >
            {issuedCode}
          </p>

          <p className="muted">
            참여할 회원에게 인증코드를 안전하게
            전달해 주세요.
            이전 인증코드는 더 이상 사용할 수 없습니다.
          </p>

          <button
            type="button"
            className="btn small soft"
            onClick={() => setIssuedCode('')}
          >
            코드 숨기기
          </button>
        </div>
      )}

      {/* 인증코드 발급 오류 */}
      {isPrivate && issueError && (
        <p
          role="alert"
          style={{
            color: '#c0392b',
            marginTop: 12
          }}
        >
          {issueError}
        </p>
      )}

      {/* 참여 회원 목록 */}
      {openMembers && (
        <div
          className="policy-lock"
          style={{ marginTop: 12 }}
        >
          <div className="item-head">
            <h3>참여 회원 목록</h3>

            <span className="pill">
              {membersLoading
                ? '조회 중'
                : membersError
                  ? '조회 실패'
                  : `총 ${members.length}명`}
            </span>
          </div>

          {membersLoading ? (
            <p className="muted">
              참여 회원 조회 중...
            </p>

          ) : membersError ? (
            <p
              role="alert"
              style={{ color: '#c0392b' }}
            >
              {membersError}
            </p>

          ) : members.length ? (
            members.map(member => (
              <div
                key={member.gyeMemberId}
                className="item-head"
                style={{
                  padding: '14px 0',
                  borderBottom: '1px solid #e5e7eb'
                }}
              >
                <div>
                  <strong>
                    {member.memberName ||
                      `회원 #${member.memberId}`}
                  </strong>

                  <p className="muted mini">
                    회원 ID: {member.memberId}

                    {' · '}

                    가입일: {member.joinedAt
                      ? member.joinedAt
                          .replace('T', ' ')
                          .slice(0, 16)
                      : '-'}
                  </p>
                </div>

                <div
                  className="actions"
                  style={{ margin: 0 }}
                >
                  <span className="pill">
                    {member.role === 'OWNER'
                      ? '모임장'
                      : '일반 멤버'}
                  </span>

                  <span className="pill">
                    {member.status}
                  </span>
                </div>
              </div>
            ))

          ) : (
            <p className="muted">
              현재 참여 중인 회원이 없어요.
            </p>
          )}

          <button
            type="button"
            className="btn small soft"
            disabled={membersLoading}
            onClick={loadGroupMembers}
            style={{ marginTop: 12 }}
          >
            새로고침
          </button>
        </div>
      )}

      {/* 가입 신청 관리 */}
      {openJoin && (
        <div
          id={`join-requests-${groupId}`}
          className="policy-lock"
          style={{
            marginTop: 12,
            scrollMarginTop: 100
          }}
        >
          <div className="item-head">
            <h3>가입 신청 관리</h3>

            <span className="pill">
              {joinLoading
                ? '조회 중'
                : joinError
                  ? '조회 또는 처리 오류'
                  : `대기 ${joinRequests.length}명`}
            </span>
          </div>

          {joinLoading ? (
            <p className="muted">
              가입 신청 조회 중...
            </p>

          ) : (
            <>
              {joinRequests.length > 0 ? (
                joinRequests.map(request => (
                  <div
                    key={request.joinRequestId}
                    style={{
                      padding: '14px 0',
                      borderBottom: '1px solid #e5e7eb'
                    }}
                  >
                    <div className="item-head">
                      <strong>
                        {request.memberName ||
                          `회원 #${request.memberId}`}
                      </strong>

                      <span className="pill">
                        승인 대기
                      </span>
                    </div>

                    <p className="muted">
                      가입 경로: {request.via === 'INVITE'
                        ? '초대 링크'
                        : '공개 모임 검색'}
                    </p>

                    <p className="muted">
                      신청 일시: {request.createdAt
                        ? request.createdAt
                            .replace('T', ' ')
                            .slice(0, 16)
                        : '-'}
                    </p>

                    <div className="actions">

                      <button
                        type="button"
                        className="btn soft"
                        disabled={
                          joinProcessingId !== null
                        }
                        onClick={() =>
                          handleJoinDecision(
                            request,
                            'reject'
                          )
                        }
                      >
                        거절
                      </button>

                      <button
                        type="button"
                        className="btn primary"
                        disabled={
                          joinProcessingId !== null
                        }
                        onClick={() =>
                          handleJoinDecision(
                            request,
                            'approve'
                          )
                        }
                      >
                        승인
                      </button>

                    </div>
                  </div>
                ))

              ) : !joinError ? (
                <p className="muted">
                  대기 중인 가입 신청이 없어요.
                </p>
              ) : null}
            </>
          )}

          {joinError && (
            <p
              role="alert"
              style={{ color: '#c0392b' }}
            >
              {joinError}
            </p>
          )}

          <button
            type="button"
            className="btn small soft"
            disabled={
              joinLoading ||
              joinProcessingId !== null
            }
            onClick={loadJoinRequests}
            style={{ marginTop: 12 }}
          >
            새로고침
          </button>

        </div>
      )}
    </div>
  );
}