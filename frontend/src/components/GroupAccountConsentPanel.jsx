/*
 * MOAVA F20 — 일반 회원 계좌 변경 동의·거절
 *
 * - MEMBER 전용
 * - 현재 PENDING 신청 조회
 * - 변경 은행명 / 마스킹 계좌번호 표시
 * - 본인 동의 상태 확인
 * - 동의 및 거절 중복 제출 방지
 * - 거절 시 PENDING 신청 종료 안내
 * - 실제 잔액 이전 및 최종 확정 없음
 */

import {
  useCallback,
  useEffect,
  useRef,
  useState
} from 'react';

import {
  fetchAccountChangeConsents,
  submitAccountChangeConsent
} from '../api/groupAccountChanges';

// ================================================
// 유효성 검사
// ================================================

function validId(value) {
  const id = String(value ?? '');

  return (
    /^[1-9]\d*$/.test(id) &&
    Number.isSafeInteger(Number(id))
  );
}

function getErrorMessage(error) {
  switch (error?.status) {
    case 400:
      return '모임 또는 신청 정보를 확인해 주세요.';

    case 401:
      return '로그인 후 다시 시도해 주세요.';

    case 403:
      return '해당 모임의 활성 회원만 조회하거나 동의할 수 있어요.';

    case 404:
      return '현재 진행 중인 계좌 변경 신청이 없어요.';

    case 409:
      return '이미 응답했거나 신청 상태가 변경됐어요.';

    default:
      return error?.message ||
        '계좌 변경 동의 요청을 처리하지 못했어요.';
  }
}

function validateResponse(response, groupId, accountId) {
  if (!response) {
    throw new Error('동의 현황 응답이 없습니다.');
  }

  if (
    String(response.groupId) !== String(groupId) ||
    String(response.gyeAccountId) !== String(accountId)
  ) {
    throw new Error(
      '현재 모임에 해당하는 계좌 변경 신청이 아니에요.'
    );
  }

  if (!['Y', 'N', null].includes(response.myAgreedYn)) {
    throw new Error(
      '본인 동의 상태를 확인할 수 없어요.'
    );
  }

  if (typeof response.canRespond !== 'boolean') {
    throw new Error(
      '동의 가능 여부를 확인할 수 없어요.'
    );
  }

  if (
    typeof response.bankName !== 'string' ||
    !response.bankName.trim() ||
    typeof response.accountMasked !== 'string' ||
    !/^\*+\d{4}$/.test(response.accountMasked)
  ) {
    throw new Error(
      '변경 계좌의 표시 정보가 올바르지 않아요.'
    );
  }

  return response;
}

// ================================================
// MEMBER 전용 진입점
// ================================================

export default function GroupAccountConsentPanel({ g }) {
  if (!g || g.role !== 'MEMBER') {
    return null;
  }

  return (
    <GroupAccountConsentContent
      key={g.groupId}
      groupId={g.groupId}
    />
  );
}

// ================================================
// 일반 회원 동의 패널
// ================================================

function GroupAccountConsentContent({ groupId }) {
  const [accountId, setAccountId] = useState('');

  const [info, setInfo] = useState(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const mountedRef = useRef(false);
  const requestRef = useRef(0);
  const submittingRef = useRef(false);

  useEffect(() => {
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
      requestRef.current += 1;
    };
  }, []);

  // ==============================================
  // 신청 ID 변경
  // ==============================================

  const handleIdChange = event => {
    if (loading || submittingRef.current) {
      return;
    }

    const nextValue = event.target.value.trim();

    requestRef.current += 1;

    setAccountId(nextValue);
    setInfo(null);
    setError('');
    setSuccessMessage('');
  };

  // ==============================================
  // 동의 현황 조회
  // ==============================================

  const loadStatus = useCallback(async () => {
    const requestedAccountId = String(accountId);

    if (
      !validId(groupId) ||
      !validId(requestedAccountId)
    ) {
      setInfo(null);
      setError('올바른 계좌 변경 신청 ID를 입력해 주세요.');
      return;
    }

    const requestId = ++requestRef.current;

    setLoading(true);
    setError('');
    setSuccessMessage('');
    setInfo(null);

    try {
      const response = await fetchAccountChangeConsents(
        groupId,
        requestedAccountId
      );

      const checked = validateResponse(
        response,
        groupId,
        requestedAccountId
      );

      if (
        !mountedRef.current ||
        requestId !== requestRef.current
      ) {
        return;
      }

      setInfo(checked);

    } catch (err) {
      if (
        mountedRef.current &&
        requestId === requestRef.current
      ) {
        setError(getErrorMessage(err));
      }

    } finally {
      if (
        mountedRef.current &&
        requestId === requestRef.current
      ) {
        setLoading(false);
      }
    }
  }, [groupId, accountId]);

  // ==============================================
  // 동의 / 거절
  // ==============================================

  const handleRespond = async agreedYn => {
    if (
      !validId(groupId) ||
      !validId(accountId) ||
      loading ||
      submittingRef.current ||
      !info ||
      info.canRespond !== true ||
      info.myAgreedYn !== null
    ) {
      return;
    }

    const isAgree = agreedYn === 'Y';

    const confirmed = window.confirm(
      isAgree
        ? (
          '계좌 변경에 동의하시겠어요?\n\n' +
          `은행: ${info.bankName}\n` +
          `신규 계좌: ${info.accountMasked}\n\n` +
          '동의 후에는 다시 응답할 수 없습니다.'
        )
        : (
          '계좌 변경 신청을 거절하시겠어요?\n\n' +
          `은행: ${info.bankName}\n` +
          `신규 계좌: ${info.accountMasked}\n\n` +
          '거절하면 현재 계좌 변경 신청이 종료됩니다.\n' +
          '기존 계좌는 유지됩니다.'
        )
    );

    if (!confirmed) {
      return;
    }

    submittingRef.current = true;

    setSubmitting(true);
    setError('');
    setSuccessMessage('');

    // 이전 조회 응답 무효화
    requestRef.current += 1;

    try {
      const response = await submitAccountChangeConsent(
        groupId,
        accountId,
        agreedYn
      );

      if (!mountedRef.current) {
        return;
      }

      if (
        String(response?.groupId) !== String(groupId) ||
        String(response?.gyeAccountId) !== String(accountId) ||
        response?.consentStatus !== agreedYn
      ) {
        setInfo(null);
        setError(
          '서버 응답을 확인하지 못했어요. ' +
          '중복 제출하지 말고 조회를 다시 해 주세요.'
        );
        return;
      }

      setInfo(previous => previous ? {
        ...previous,
        myAgreedYn: agreedYn,
        canRespond: false
      } : null);

      setSuccessMessage(
        isAgree
          ? '계좌 변경에 동의했습니다.'
          : '계좌 변경을 거절했습니다. 변경 신청이 종료됐습니다.'
      );

      // 거절 시 신청이 RETIRED로 변경되므로
      // GET 자동 재조회는 하지 않는다.

    } catch (err) {
      if (mountedRef.current) {
        setInfo(null);
        setError(
          `${getErrorMessage(err)} ` +
          '중복 제출하지 말고 현재 신청 상태를 확인해 주세요.'
        );
      }

    } finally {
      submittingRef.current = false;

      if (mountedRef.current) {
        setSubmitting(false);
      }
    }
  };

  // ==============================================
  // 화면 상태
  // ==============================================

  const alreadyAgreed = info?.myAgreedYn === 'Y';
  const alreadyRejected = info?.myAgreedYn === 'N';

  const canSubmit =
    !loading &&
    !submitting &&
    validId(groupId) &&
    validId(accountId) &&
    info?.canRespond === true &&
    info?.myAgreedYn === null;

  // ==============================================
  // 렌더링
  // ==============================================

  return (
    <div
      className="policy-panel"
      style={{ marginTop: 16 }}
      aria-busy={loading || submitting}
    >
      <div className="item-head">
        <h3>계좌 변경 회원 동의</h3>

        <span className="pill">
          회원 전용
        </span>
      </div>

      <p className="muted">
        모임장이 신청한 새 계좌 정보를 확인한 뒤
        동의하거나 거절할 수 있습니다.
      </p>

      {/* ========================================= */}
      {/* 계좌 변경 신청 ID */}
      {/* ========================================= */}

      <div
        className="field"
        style={{
          maxWidth: 400,
          marginTop: 16
        }}
      >
        <label htmlFor={`consent-account-id-${groupId}`}>
          계좌 변경 신청 ID
        </label>

        <input
          id={`consent-account-id-${groupId}`}
          type="text"
          inputMode="numeric"
          value={accountId}
          onChange={handleIdChange}
          placeholder="예: 9"
          autoComplete="off"
          disabled={loading || submitting}
        />
      </div>

      <div
        className="actions"
        style={{ marginTop: 12 }}
      >
        <button
          type="button"
          className="btn soft"
          onClick={loadStatus}
          disabled={
            !validId(groupId) ||
            !validId(accountId) ||
            loading ||
            submitting
          }
        >
          {loading
            ? '동의 현황 조회 중...'
            : '동의 현황 확인'}
        </button>
      </div>

      {/* ========================================= */}
      {/* 오류 및 처리 결과 */}
      {/* ========================================= */}

      {error && (
        <p
          role="alert"
          style={{
            color: '#c0392b',
            marginTop: 12
          }}
        >
          {error}
        </p>
      )}

      {successMessage && (
        <p
          role="status"
          style={{
            color: '#15803d',
            marginTop: 12
          }}
        >
          {successMessage}
        </p>
      )}

      {/* ========================================= */}
      {/* 조회 결과 */}
      {/* ========================================= */}

      {info && (
        <div
          className="policy-lock"
          style={{ marginTop: 20 }}
        >
          <h4>계좌 변경 신청 정보</h4>

          <div className="group-info-rows">
            <div>
              <span>소속 모임 ID</span>
              <strong>{info.groupId}</strong>
            </div>

            <div>
              <span>변경 신청 ID</span>
              <strong>{info.gyeAccountId}</strong>
            </div>

            <div>
              <span>변경할 은행</span>
              <strong>{info.bankName}</strong>
            </div>

            <div>
              <span>변경할 계좌번호</span>
              <strong>{info.accountMasked}</strong>
            </div>

            <div>
              <span>전체 활성 회원</span>
              <strong>
                {info.totalMembers ?? '-'}명
              </strong>
            </div>

            <div>
              <span>동의 완료</span>
              <strong>
                {info.agreedMembers ?? '-'}명
              </strong>
            </div>

            <div>
              <span>거절</span>
              <strong>
                {info.rejectedMembers ?? '-'}명
              </strong>
            </div>

            <div>
              <span>미응답</span>
              <strong>
                {info.pendingMembers ?? '-'}명
              </strong>
            </div>

            <div>
              <span>전원 동의 여부</span>
              <strong>
                {info.allAgreed === true
                  ? '전원 동의 완료'
                  : '동의 진행 중'}
              </strong>
            </div>

            <div>
              <span>내 동의 상태</span>
              <strong>
                {alreadyAgreed
                  ? '동의 완료'
                  : alreadyRejected
                    ? '거절 완료'
                    : info.canRespond
                      ? '응답 대기'
                      : '응답 불가'}
              </strong>
            </div>
          </div>

          {/* ===================================== */}
          {/* 이미 응답한 상태 */}
          {/* ===================================== */}

          {alreadyAgreed && (
            <p
              role="status"
              style={{
                color: '#15803d',
                marginTop: 12
              }}
            >
              이미 계좌 변경에 동의했습니다.
              같은 신청에 다시 응답할 수 없습니다.
            </p>
          )}

          {alreadyRejected && (
            <p
              role="status"
              style={{ marginTop: 12 }}
            >
              계좌 변경을 거절했습니다.
              해당 변경 신청은 종료됩니다.
            </p>
          )}

          {/* ===================================== */}
          {/* 동의 / 거절 버튼 */}
          {/* ===================================== */}

          <div
            className="actions"
            style={{ marginTop: 16 }}
          >
            <button
              type="button"
              className="btn primary"
              disabled={!canSubmit}
              onClick={() => handleRespond('Y')}
            >
              {submitting
                ? '처리 중...'
                : '계좌 변경 동의'}
            </button>

            <button
              type="button"
              className="btn soft"
              disabled={!canSubmit}
              onClick={() => handleRespond('N')}
            >
              계좌 변경 거절
            </button>
          </div>

          {!canSubmit && !loading && (
            <p className="muted mini">
              이미 응답했거나 현재 동의할 수 없는 상태입니다.
            </p>
          )}
        </div>
      )}

      <p
        className="muted mini"
        style={{ marginTop: 16 }}
      >
        회원 전원 동의 및 신규 계좌 인증이 완료돼도
        실제 계좌 변경과 잔액 이전은 별도의
        안전성 검증 후 진행됩니다.
      </p>
    </div>
  );
}
