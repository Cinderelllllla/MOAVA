/* S01b 초대 — F16 / F18
 *
 * /invite
 *   → 모임 ID + 6자리 인증코드 직접 입력
 *
 * /invite?groupId=13
 *   → 모임 ID 자동 입력 + 인증 상태 복원
 *
 * /invite?t=토큰
 *   → 토큰 기반 초대 + 인증 상태 복원
 */

import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';

import { useApp } from '../common/store';
import { groups } from '../api';
import { PageHead, Card } from '../components/ui';
import { LoadState } from '../components/shared';
import { formatRegionFromServer } from '../common/regions';

/* =========================================
   공통 함수
   ========================================= */

function isValidGroupId(value) {
  const text = String(value ?? '').trim();

  return (
    /^[1-9]\d*$/.test(text) &&
    Number.isSafeInteger(Number(text))
  );
}

function isVerificationSuccessful(result) {
  return (
    result?.status === 'VERIFIED' ||
    result?.status === 'ALREADY_VERIFIED'
  );
}

function hasVerifiedRecord(result) {
  return result?.verified === true;
}

function canRequestJoinWithVerification(result) {
  return (
    !isLocked(result) &&
    (
      isVerificationSuccessful(result) ||
      hasVerifiedRecord(result)
    )
  );
}

function isLocked(result) {
  return result?.status === 'LOCKED';
}

/* =========================================
   Invite
   ========================================= */

export default function Invite() {
  const { session, isAdmin } = useApp();
  const [sp] = useSearchParams();

  const t = sp.get('t') || '';
  const groupIdParam = sp.get('groupId') || '';

  const returnParams = new URLSearchParams();
  if (t) returnParams.set('t', t);
  if (groupIdParam) returnParams.set('groupId', groupIdParam);
  if (!t && !groupIdParam) returnParams.set('next', '/invite');
  const returnQuery = `?${returnParams.toString()}`;

  /* =========================================
     토큰 방식 상태
     ========================================= */

  const [invite, setInvite] = useState({
    status: 'loading',
    group: null,
    error: ''
  });

  const [retry, setRetry] = useState(0);

  const [joinCode, setJoinCode] = useState('');
  const [verifying, setVerifying] = useState(false);
  const [verifyResult, setVerifyResult] = useState(null);
  const [verifyError, setVerifyError] = useState('');
  const [tokenStateLoading, setTokenStateLoading] = useState(false);

  const [joining, setJoining] = useState(false);
  const [joinResult, setJoinResult] = useState(null);
  const [joinError, setJoinError] = useState('');

  /* =========================================
     직접 입력 방식 상태
     ========================================= */

  const [directGroupId, setDirectGroupId] = useState(
    isValidGroupId(groupIdParam) ? groupIdParam : ''
  );

  const [directCode, setDirectCode] = useState('');
  const [directVerifying, setDirectVerifying] = useState(false);
  const [directVerifyResult, setDirectVerifyResult] = useState(null);
  const [directVerifiedGroupId, setDirectVerifiedGroupId] = useState(null);

  const [directJoining, setDirectJoining] = useState(false);
  const [directJoinResult, setDirectJoinResult] = useState(null);
  const [directError, setDirectError] = useState('');

  const [directStateLoading, setDirectStateLoading] = useState(false);
  const [directStateGroupId, setDirectStateGroupId] = useState(null);

  // 비동기 응답이 다른 모임 ID에 적용되지 않도록 보호
  const directGroupIdRef = useRef(directGroupId);
  directGroupIdRef.current = directGroupId;

  const directVerified =
    directVerifiedGroupId !== null &&
    directVerifiedGroupId === directGroupId &&
    canRequestJoinWithVerification(directVerifyResult);

  const directLocked = isLocked(directVerifyResult);

  const directVerificationSucceeded =
    directVerified &&
    isVerificationSuccessful(directVerifyResult);

  /* =========================================
     F18 — 토큰으로 모임 조회
     ========================================= */

  useEffect(() => {
    if (!session || isAdmin || !t) {
      return undefined;
    }

    let alive = true;

    setInvite({
      status: 'loading',
      group: null,
      error: ''
    });

    setVerifyResult(null);
    setJoinResult(null);
    setJoinError('');

    groups.fetchInvite(t)
      .then(group => {
        if (!alive) return;

        setInvite({
          status: 'ready',
          group,
          error: ''
        });
      })
      .catch(error => {
        if (!alive) return;

        setInvite({
          status: 'error',
          group: null,
          error: error.message
        });
      });

    return () => {
      alive = false;
    };
  }, [session, isAdmin, t, retry]);

  /* =========================================
     URL groupId 변경 반영
     ========================================= */

  useEffect(() => {
    if (t) return;

    setDirectGroupId(
      isValidGroupId(groupIdParam)
        ? groupIdParam
        : ''
    );

    setDirectCode('');
    setDirectVerifyResult(null);
    setDirectVerifiedGroupId(null);
    setDirectStateGroupId(null);
    setDirectJoinResult(null);
    setDirectError('');
  }, [t, groupIdParam]);

  /* =========================================
     F18 — 토큰 방식 F5 인증 상태 복원
     ========================================= */

  const tokenGroupId = invite.group?.groupId;
  const tokenVisibility = invite.group?.visibility;

  useEffect(() => {
    if (
      !session ||
      isAdmin ||
      !t ||
      invite.status !== 'ready' ||
      tokenVisibility !== 'PRIVATE' ||
      !isValidGroupId(tokenGroupId)
    ) {
      setTokenStateLoading(false);
      return undefined;
    }

    let alive = true;

    setTokenStateLoading(true);
    setVerifyResult(null);
    setVerifyError('');

    groups.fetchJoinCodeVerificationState(tokenGroupId)
      .then(result => {
        if (!alive) return;

        setVerifyResult(result);
      })
      .catch(error => {
        if (!alive) return;

        setVerifyError(
          error.message ||
          '인증 상태를 불러오지 못했어요.'
        );
      })
      .finally(() => {
        if (alive) {
          setTokenStateLoading(false);
        }
      });

    return () => {
      alive = false;
    };
  }, [
    session,
    isAdmin,
    t,
    invite.status,
    tokenGroupId,
    tokenVisibility
  ]);

  /* =========================================
     F18 — 직접 입력 방식 F5 인증 상태 복원

     모임 ID 입력 후 500ms 대기
     GET 요청만 사용하므로 인증 실패 횟수 증가 없음
     ========================================= */

  useEffect(() => {
    if (
      !session ||
      isAdmin ||
      t ||
      !isValidGroupId(directGroupId)
    ) {
      setDirectStateLoading(false);
      setDirectStateGroupId(null);
      return undefined;
    }

    let alive = true;
    const requestedGroupId = directGroupId;

    setDirectStateLoading(true);
    setDirectStateGroupId(null);

    const timer = setTimeout(() => {
      groups.fetchJoinCodeVerificationState(requestedGroupId)
        .then(result => {
          if (!alive) return;

          setDirectVerifyResult(result);

          if (canRequestJoinWithVerification(result)) {
            setDirectVerifiedGroupId(requestedGroupId);
          } else {
            setDirectVerifiedGroupId(null);
          }

          setDirectStateGroupId(requestedGroupId);
        })
        .catch(error => {
          if (!alive) return;

          setDirectStateGroupId(requestedGroupId);
          setDirectError(
            error.message ||
            '인증 상태를 불러오지 못했어요.'
          );
        })
        .finally(() => {
          if (alive) {
            setDirectStateLoading(false);
          }
        });
    }, 500);

    return () => {
      alive = false;
      clearTimeout(timer);
    };
  }, [session, isAdmin, t, directGroupId]);

  /* =========================================
     F18 — 토큰 방식 인증코드 검증
     ========================================= */

  const handleVerifyJoinCode = async e => {
    e.preventDefault();

    if (verifying || tokenStateLoading) return;

    const groupId = invite.group?.groupId;

    if (!isValidGroupId(groupId)) {
      setVerifyError('모임 정보를 확인할 수 없어요.');
      return;
    }

    if (!/^\d{6}$/.test(joinCode)) {
      setVerifyError('6자리 숫자 인증코드를 입력해 주세요.');
      return;
    }

    setVerifying(true);
    setVerifyError('');
    setVerifyResult(null);

    try {
      const result = await groups.verifyJoinCode(
        groupId,
        joinCode
      );

      setVerifyResult(result);

      if (isVerificationSuccessful(result)) {
        setJoinCode('');
      }
    } catch (error) {
      setVerifyError(
        error.message ||
        '인증 중 오류가 발생했어요.'
      );
    } finally {
      setVerifying(false);
    }
  };

  /* =========================================
     F16 — 토큰 방식 가입 신청
     ========================================= */

  const handleJoinRequest = async () => {
    if (joining || joinResult || tokenStateLoading) {
      return;
    }

    const groupId = invite.group?.groupId;

    if (!isValidGroupId(groupId)) {
      setJoinError('모임 정보를 확인할 수 없어요.');
      return;
    }

    const isPrivate =
      invite.group.visibility === 'PRIVATE';

    if (
      isPrivate &&
      !canRequestJoinWithVerification(verifyResult)
    ) {
      setJoinError(
        '먼저 비공개 모임 인증을 완료해 주세요.'
      );
      return;
    }

    setJoining(true);
    setJoinError('');

    try {
      const result = await groups.requestJoinGroup(
        groupId,
        isPrivate ? 'INVITE' : 'SEARCH'
      );

      setJoinResult(result);
    } catch (error) {
      setJoinError(
        error.message ||
        '가입 신청 중 오류가 발생했어요.'
      );
    } finally {
      setJoining(false);
    }
  };

  /* =========================================
     F18 — 직접 입력 방식 인증코드 검증
     ========================================= */

  const handleDirectVerify = async e => {
    e.preventDefault();

    if (
      directVerifying ||
      directJoining ||
      directStateLoading
    ) {
      return;
    }

    if (!isValidGroupId(directGroupId)) {
      setDirectError('올바른 모임 ID를 입력해 주세요.');
      return;
    }

    if (!/^\d{6}$/.test(directCode)) {
      setDirectError(
        '6자리 숫자 인증코드를 입력해 주세요.'
      );
      return;
    }

    const requestedGroupId = directGroupId;

    setDirectVerifying(true);
    setDirectError('');
    setDirectVerifyResult(null);
    setDirectVerifiedGroupId(null);
    setDirectJoinResult(null);

    try {
      const result = await groups.verifyJoinCode(
        Number(requestedGroupId),
        directCode
      );

      // 모임 ID가 바뀌었다면 이전 요청 결과 무시
      if (directGroupIdRef.current !== requestedGroupId) {
        return;
      }

      setDirectVerifyResult(result);

      if (canRequestJoinWithVerification(result)) {
        setDirectVerifiedGroupId(requestedGroupId);
      }

      if (isVerificationSuccessful(result)) {
        setDirectCode('');
      } else {
        setDirectError(
          result?.message ||
          '인증코드가 올바르지 않아요.'
        );
      }
    } catch (error) {
      if (directGroupIdRef.current !== requestedGroupId) {
        return;
      }

      setDirectError(
        error.message ||
        '인증코드 확인 중 오류가 발생했어요.'
      );
    } finally {
      setDirectVerifying(false);
    }
  };

  /* =========================================
     F16 — 직접 입력 방식 가입 신청
     ========================================= */

  const handleDirectJoin = async () => {
    if (
      !directVerified ||
      directStateLoading ||
      directJoining ||
      directJoinResult
    ) {
      return;
    }

    setDirectJoining(true);
    setDirectError('');

    try {
      const result = await groups.requestJoinGroup(
        Number(directVerifiedGroupId),
        'INVITE'
      );

      setDirectJoinResult(result);
    } catch (error) {
      setDirectError(
        error.message ||
        '가입 신청 중 오류가 발생했어요.'
      );
    } finally {
      setDirectJoining(false);
    }
  };

  /* =========================================
     비로그인 화면
     ========================================= */

  if (!session) {
    return (
      <div className="moava-guest-wrap">
        <div className="moava-guest-card">
          <h1>
            {t
              ? 'MOAVA 모임에 초대되었어요!'
              : '초대코드로 가입하기'}
          </h1>

          <p className="muted">
            {t
              ? '초대받은 모임을 확인하려면 MOAVA 계정이 필요합니다. 로그인 또는 회원가입 후 이 모임으로 돌아옵니다.'
              : '초대코드 인증과 모임 가입 신청은 로그인 후 이용할 수 있어요.'}
          </p>

          <div className="actions">
            <Link
              className="btn primary"
              to={`/login${returnQuery}`}
            >
              기존 회원 · 로그인
            </Link>

            <Link
              className="btn soft"
              to={`/signup${returnQuery}`}
            >
              처음이에요 · 회원가입
            </Link>
          </div>

          <div
            className="actions"
            style={{ marginTop: 12 }}
          >
            <Link className="btn soft" to="/">
              서비스 소개로
            </Link>
          </div>
        </div>
      </div>
    );
  }

  /* =========================================
     관리자 접근 제한
     ========================================= */

  if (isAdmin) {
    return <Navigate to="/admin" replace />;
  }

  /* =========================================
     직접 입력 화면
     /invite
     /invite?groupId=13
     ========================================= */

  if (!t) {
    return (
      <>
        <PageHead
          kicker="GROUP INVITATION"
          title="초대코드로 계모임 가입하기"
          sub="모임장에게 전달받은 모임 ID와 6자리 인증코드를 입력해 주세요."
        />

        <Card>
          <div className="policy-invite-card">
            <span className="pill">
              비공개 모임 초대
            </span>

            <h2>초대코드 인증</h2>

            <p className="muted">
              비공개 계모임에 가입하려면
              모임 ID와 인증코드가 필요해요.
            </p>

            {directStateLoading && (
              <p className="muted" role="status">
                서버 인증 상태를 확인하는 중이에요...
              </p>
            )}

            {!directVerified && (
              <form onSubmit={handleDirectVerify}>
                <div style={{ marginTop: 20 }}>
                  <label
                    htmlFor="direct-group-id"
                    style={{
                      display: 'block',
                      marginBottom: 8
                    }}
                  >
                    모임 ID
                  </label>

                  <input
                    id="direct-group-id"
                    type="text"
                    inputMode="numeric"
                    autoComplete="off"
                    placeholder="예: 13"
                    value={directGroupId}
                    disabled={directVerifying || directJoining}
                    onChange={e => {
                      const value = e.target.value.replace(/\D/g, '');

                      setDirectGroupId(value);
                      setDirectCode('');
                      setDirectVerifiedGroupId(null);
                      setDirectVerifyResult(null);
                      setDirectJoinResult(null);
                      setDirectError('');
                    }}
                    style={{
                      width: '100%',
                      maxWidth: 300,
                      padding: 12,
                      fontSize: 16,
                      marginBottom: 16
                    }}
                  />
                </div>

                <div>
                  <label
                    htmlFor="direct-join-code"
                    style={{
                      display: 'block',
                      marginBottom: 8
                    }}
                  >
                    6자리 인증코드
                  </label>

                  <input
                    id="direct-join-code"
                    type="text"
                    inputMode="numeric"
                    autoComplete="off"
                    maxLength={6}
                    placeholder="6자리 숫자"
                    value={directCode}
                    disabled={
                      directVerifying ||
                      directLocked ||
                      directStateLoading
                    }
                    onChange={e => {
                      setDirectCode(
                        e.target.value
                          .replace(/\D/g, '')
                          .slice(0, 6)
                      );

                      setDirectError('');
                    }}
                    style={{
                      width: '100%',
                      maxWidth: 300,
                      padding: 12,
                      fontSize: 20,
                      letterSpacing: 4,
                      textAlign: 'center',
                      marginBottom: 12
                    }}
                  />
                </div>

                <div className="actions">
                  <button
                    type="submit"
                    className="btn primary"
                    disabled={
                      directVerifying ||
                      directStateLoading ||
                      directLocked ||
                      !isValidGroupId(directGroupId) ||
                      directCode.length !== 6
                    }
                  >
                    {directVerifying
                      ? '인증 중...'
                      : '인증코드 확인'}
                  </button>
                </div>
              </form>
            )}

            {directError && (
              <p
                role="alert"
                style={{
                  color: '#c0392b',
                  marginTop: 12
                }}
              >
                {directError}
              </p>
            )}

            {directVerifyResult &&
              !directVerified &&
              directVerifyResult.status === 'INVALID_CODE' &&
              directVerifyResult.remainingAttempts != null && (
                <p className="muted">
                  남은 인증 시도:{' '}
                  {directVerifyResult.remainingAttempts}회
                </p>
              )}

            {directLocked && (
              <div
                className="policy-lock"
                role="status"
                style={{ marginTop: 12 }}
              >
                <p>
                  인증코드 입력에 3회 실패했어요.
                  모임장에게 인증코드 재발급을 요청해 주세요.
                </p>
              </div>
            )}

            {directVerified && !directStateLoading && (
              <>
                {directVerificationSucceeded && (
                  <div
                    className="policy-lock"
                    role="status"
                    style={{ marginTop: 20 }}
                  >
                    <h3>인증 완료!</h3>

                    <p>
                      모임 ID {directVerifiedGroupId}의
                      인증코드가 확인되었습니다.
                    </p>

                    <p className="muted">
                      이제 모임 가입 신청을 진행할 수 있어요.
                    </p>
                  </div>
                )}

                <div
                  className="policy-lock"
                  style={{ marginTop: 16 }}
                >
                  {directJoinResult?.status === 'PENDING' ? (
                    <div role="status">
                      <h3>가입 신청 완료!</h3>

                      <p>
                        모임장의 승인을 기다리고 있어요.
                      </p>

                      <p className="muted">
                        승인 전에는 채팅과 납입 기능을
                        이용할 수 없습니다.
                      </p>
                    </div>
                  ) : (
                    <>
                      <h3>모임 가입 신청</h3>

                      <p className="muted">
                        가입 신청 후 모임장의 승인을
                        기다려 주세요.
                      </p>

                      <div className="actions">
                        <button
                          type="button"
                          className="btn primary"
                          onClick={handleDirectJoin}
                          disabled={
                            directJoining ||
                            directStateLoading
                          }
                        >
                          {directJoining
                            ? '신청 중...'
                            : '가입 신청하기'}
                        </button>
                      </div>
                    </>
                  )}
                </div>
              </>
            )}

            <div
              className="actions"
              style={{ marginTop: 20 }}
            >
              <Link className="btn soft" to="/groups">
                모임 찾기로
              </Link>

              <Link className="btn soft" to="/dashboard">
                마이 홈
              </Link>
            </div>
          </div>
        </Card>
      </>
    );
  }

  /* =========================================
     토큰 기반 초대 화면
     /invite?t=토큰
     ========================================= */

  const g = invite.group;

  const verified = isVerificationSuccessful(verifyResult);
  const locked = isLocked(verifyResult);

  const canRequestJoin =
    canRequestJoinWithVerification(verifyResult);

  return (
    <>
      <PageHead
        kicker="GROUP INVITATION"
        title="초대받은 계모임"
        sub="가입 승인 전에는 채팅과 납입 정보에 접근할 수 없습니다."
      />

      <Card>
        <LoadState
          status={invite.status}
          error={invite.error}
          onRetry={() => setRetry(x => x + 1)}
          loadingText="초대받은 모임을 확인하는 중이에요."
        >
          {g && (
            <div className="policy-invite-card">
              <span className="pill">
                {g.visibility === 'PRIVATE'
                  ? '비공개 모임'
                  : '공개 모임'}
              </span>

              <h2>{g.title}</h2>

              <p>
                {formatRegionFromServer(g)}
                {' · '}
                {groups.purposeLabel(g.purpose)}
              </p>

              {/* F18 — 비공개 모임 인증 */}
              {g.visibility === 'PRIVATE' && (
                <div style={{ marginTop: 20 }}>
                  <h3>비공개 모임 인증</h3>

                  {tokenStateLoading && (
                    <p className="muted" role="status">
                      서버 인증 상태를 확인하는 중이에요...
                    </p>
                  )}

                  <p className="muted">
                    모임장에게 전달받은 6자리
                    인증코드를 입력해 주세요.
                  </p>

                  {!verified && !tokenStateLoading && (
                    <form onSubmit={handleVerifyJoinCode}>
                      <input
                        type="text"
                        inputMode="numeric"
                        autoComplete="off"
                        maxLength={6}
                        placeholder="6자리 인증코드"
                        value={joinCode}
                        disabled={
                          verifying ||
                          locked ||
                          tokenStateLoading
                        }
                        onChange={e => {
                          setJoinCode(
                            e.target.value
                              .replace(/\D/g, '')
                              .slice(0, 6)
                          );

                          setVerifyError('');
                        }}
                        style={{
                          width: '100%',
                          maxWidth: 260,
                          padding: 12,
                          fontSize: 20,
                          letterSpacing: 4,
                          textAlign: 'center',
                          marginBottom: 12
                        }}
                      />

                      <div className="actions">
                        <button
                          type="submit"
                          className="btn primary"
                          disabled={
                            verifying ||
                            locked ||
                            joinCode.length !== 6
                          }
                        >
                          {verifying
                            ? '인증 중...'
                            : '인증코드 확인'}
                        </button>
                      </div>
                    </form>
                  )}

                  {verifyError && (
                    <p
                      role="alert"
                      style={{ color: '#c0392b' }}
                    >
                      {verifyError}
                    </p>
                  )}

                  {verifyResult &&
                    !verified &&
                    ['INVALID_CODE', 'LOCKED'].includes(
                      verifyResult.status
                    ) && (
                      <div
                        className="policy-lock"
                        role="status"
                        style={{ marginTop: 12 }}
                      >
                        <p>
                          {verifyResult.message ||
                            '인증에 실패했어요.'}
                        </p>

                        {verifyResult.remainingAttempts != null &&
                          !locked && (
                            <p className="muted">
                              남은 인증 시도:{' '}
                              {verifyResult.remainingAttempts}회
                            </p>
                          )}

                        {locked && (
                          <p className="muted">
                            인증코드 입력에 3회 실패했어요.
                            모임장에게 인증코드 재발급을 요청해 주세요.
                          </p>
                        )}
                      </div>
                    )}

                  {verified && (
                    <div
                      className="policy-lock"
                      role="status"
                      style={{ marginTop: 12 }}
                    >
                      <h3>인증 완료!</h3>

                      <p>
                        비공개 모임 인증코드가
                        확인되었습니다.
                      </p>

                      <p className="muted">
                        이제 아래에서 가입 신청을
                        진행할 수 있어요.
                      </p>
                    </div>
                  )}
                </div>
              )}

              {/* F16 — 가입 신청 */}
              <div
                className="policy-lock"
                style={{ marginTop: 16 }}
              >
                {joinResult?.status === 'PENDING' ? (
                  <div role="status">
                    <h3>가입 신청 완료!</h3>

                    <p>
                      모임장의 승인을 기다리고 있어요.
                    </p>

                    <p className="muted">
                      승인 전에는 채팅과 납입 기능을
                      이용할 수 없습니다.
                    </p>
                  </div>
                ) : (
                  <>
                    <h3>모임 가입 신청</h3>

                    <p className="muted">
                      {g.visibility === 'PRIVATE' &&
                      !canRequestJoin
                        ? '비공개 모임 인증을 완료하면 가입 신청할 수 있어요.'
                        : '가입 신청 후 모임장의 승인을 기다려 주세요.'}
                    </p>

                    <div className="actions">
                      <button
                        type="button"
                        className="btn primary"
                        onClick={handleJoinRequest}
                        disabled={
                          joining ||
                          tokenStateLoading ||
                          (g.visibility === 'PRIVATE' &&
                            !canRequestJoin)
                        }
                      >
                        {joining
                          ? '신청 중...'
                          : '가입 신청하기'}
                      </button>
                    </div>

                    {joinError && (
                      <p
                        role="alert"
                        style={{
                          color: '#c0392b',
                          marginTop: 12
                        }}
                      >
                        {joinError}
                      </p>
                    )}
                  </>
                )}
              </div>

              <div
                className="actions"
                style={{ marginTop: 12 }}
              >
                <Link
                  className="btn soft"
                  to="/dashboard"
                >
                  마이 홈
                </Link>
              </div>
            </div>
          )}
        </LoadState>
      </Card>
    </>
  );
}