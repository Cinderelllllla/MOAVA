/*
 * MOAVA F20 — 모임장 본인 동의·거절
 *
 * 백엔드 응답:
 * - groupId
 * - gyeAccountId
 * - myAgreedYn
 * - canRespond
 * - bankName
 * - accountMasked
 *
 * 실제 계좌 변경 확정·잔액 이전 없음
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
// 공통 검증
// ================================================

function isValidId(value) {
  const id = String(value ?? '');

  return (
    /^[1-9]\d*$/.test(id) &&
    Number.isSafeInteger(Number(id))
  );
}

function getErrorMessage(error) {
  switch (error?.status) {
    case 400:
      return '요청 정보를 확인해 주세요.';

    case 401:
      return '로그인이 필요합니다.';

    case 403:
      return '해당 모임의 활성 회원만 동의할 수 있어요.';

    case 404:
      return '진행 중인 계좌 변경 신청을 찾을 수 없어요.';

    case 409:
      return '이미 응답했거나 신청 상태가 변경됐어요.';

    default:
      return error?.message ||
        '요청을 처리하지 못했어요.';
  }
}

function validateConsentResponse(
  response,
  groupId,
  accountId
) {
  if (!response) {
    throw new Error('동의 현황 응답이 없습니다.');
  }

  if (
    String(response.groupId) !== String(groupId) ||
    String(response.gyeAccountId) !== String(accountId)
  ) {
    throw new Error(
      '현재 모임과 계좌 변경 신청 정보가 일치하지 않아요.'
    );
  }

  if (
    !['Y', 'N', null].includes(response.myAgreedYn)
  ) {
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
      '변경할 계좌의 표시 정보를 확인할 수 없어요.'
    );
  }

  return response;
}

// ================================================
// 메인 컴포넌트
// ================================================

export default function GroupAccountOwnerConsent({
  groupId,
  accountId
}) {
  const [info, setInfo] = useState(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const requestRef = useRef(0);
  const submittingRef = useRef(false);
  const mountedRef = useRef(false);

  const valid =
    isValidId(groupId) &&
    isValidId(accountId);

  // ==============================================
  // 동의 현황 조회
  // ==============================================

  const loadStatus = useCallback(async () => {
    const requestId = ++requestRef.current;

    setInfo(null);
    setError('');
    setLoading(true);

    if (!isValidId(groupId) || !isValidId(accountId)) {
      setError('올바른 모임 및 신청 ID가 필요해요.');
      setLoading(false);
      return;
    }

    try {
      const response = await fetchAccountChangeConsents(
        groupId,
        accountId
      );

      const checked = validateConsentResponse(
        response,
        groupId,
        accountId
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

  useEffect(() => {
    mountedRef.current = true;
    loadStatus();

    return () => {
      mountedRef.current = false;
      requestRef.current += 1;
    };
  }, [loadStatus]);

  // ==============================================
  // 동의·거절 제출
  // ==============================================

  const handleRespond = async agreedYn => {
    if (
      !valid ||
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
          '다음 계좌로 변경하는 데 동의하시겠어요?\n\n' +
          `은행: ${info.bankName}\n` +
          `계좌: ${info.accountMasked}\n\n` +
          '동의 후에는 다시 응답할 수 없습니다.'
        )
        : (
          '계좌 변경 신청을 거절하시겠어요?\n\n' +
          `은행: ${info.bankName}\n` +
          `계좌: ${info.accountMasked}\n\n` +
          '거절하면 현재 계좌 변경 신청이 종료됩니다.\n' +
          '기존 CURRENT 계좌는 유지됩니다.'
        )
    );

    if (!confirmed) {
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    setError('');
    setSuccessMessage('');

    // 제출 중 이전 조회 응답 무효화
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
          '응답 확인이 필요해요. 중복 제출하지 말고 ' +
          '동의 현황을 다시 조회해 주세요.'
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

      // 거절하면 PENDING 신청이 종료되므로
      // 곧바로 GET을 다시 보내지 않는다.

    } catch (err) {
      if (mountedRef.current) {
        setInfo(null);
        setError(
          `${getErrorMessage(err)} ` +
          '처리 여부가 불분명하면 재제출하지 말고 다시 조회해 주세요.'
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
  // 화면
  // ==============================================

  const alreadyAgreed = info?.myAgreedYn === 'Y';
  const alreadyRejected = info?.myAgreedYn === 'N';

  const canSubmit =
    valid &&
    !loading &&
    !submitting &&
    info?.canRespond === true &&
    info?.myAgreedYn === null;

  return (
    <div
      className="policy-lock"
      style={{ marginTop: 16 }}
      aria-busy={loading || submitting}
    >
      <h4>모임장 본인 동의</h4>

      <p className="muted">
        모임장도 활성 회원에 포함되므로
        계좌 변경에 직접 동의해야 합니다.
      </p>

      <div className="actions" style={{ marginTop: 12 }}>
        <button
          type="button"
          className="btn soft"
          onClick={loadStatus}
          disabled={!valid || loading || submitting}
        >
          {loading ? '조회 중...' : '내 동의 상태 새로고침'}
        </button>
      </div>

      {error && (
        <p
          role="alert"
          style={{ color: '#c0392b', marginTop: 12 }}
        >
          {error}
        </p>
      )}

      {successMessage && (
        <p
          role="status"
          style={{ color: '#15803d', marginTop: 12 }}
        >
          {successMessage}
        </p>
      )}

      {info && (
        <>
          <div
            className="group-info-rows"
            style={{ marginTop: 16 }}
          >
            <div>
              <span>변경 신청 ID</span>
              <strong>{info.gyeAccountId}</strong>
            </div>

            <div>
              <span>변경할 은행</span>
              <strong>{info.bankName}</strong>
            </div>

            <div>
              <span>신규 계좌번호</span>
              <strong>{info.accountMasked}</strong>
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

          {alreadyAgreed && (
            <p
              role="status"
              style={{ color: '#15803d', marginTop: 12 }}
            >
              이미 계좌 변경에 동의했습니다.
            </p>
          )}

          {alreadyRejected && (
            <p
              role="status"
              style={{ marginTop: 12 }}
            >
              계좌 변경을 거절했습니다.
              현재 변경 신청은 종료됩니다.
            </p>
          )}

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
              {submitting ? '처리 중...' : '계좌 변경 동의'}
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
              이미 응답했거나 현재 신청에 응답할 수 없는 상태입니다.
            </p>
          )}
        </>
      )}
    </div>
  );
}
