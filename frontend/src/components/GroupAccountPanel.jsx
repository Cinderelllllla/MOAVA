
/* F19 — 모임 계좌 관리 패널
   모임장 전용 현재 계좌 조회 및 소유 인증
*/

import {
  useState,
  useEffect,
  useRef,
  useCallback
} from 'react';

import {
  fetchCurrentAccount,
  verifyCurrentAccount
} from '../api/groupAccounts';

// F19 전용 계좌 인증 상태 문구
const ACCOUNT_VERIFY_LABEL = {
  PENDING: '인증 대기',
  VERIFIED: '인증 완료',
  REVERIFY_REQUIRED: '재인증 필요'
};

// 계좌 사용 상태 문구
const ACCOUNT_STATUS_LABEL = {
  CURRENT: '사용 중',
  PENDING: '변경 대기',
  RETIRED: '사용 종료'
};

// 인증 요청이 가능한 상태
const VERIFIABLE_STATUSES = [
  'PENDING',
  'REVERIFY_REQUIRED'
];

// 계좌 조회 응답 검증
function validateAccount(data, groupId) {
  if (
    !data ||
    data.gyeAccountId == null ||
    String(data.groupId) !== String(groupId) ||
    typeof data.bankName !== 'string' ||
    !data.bankName.trim() ||
    typeof data.accountMasked !== 'string' ||
    !/^\*+\d{4}$/.test(data.accountMasked) ||
    !/^\d{4}$/.test(String(data.accountLast4 ?? '')) ||
    !data.accountMasked.endsWith(
      String(data.accountLast4)
    ) ||
    !Object.prototype.hasOwnProperty.call(
      ACCOUNT_VERIFY_LABEL,
      data.verifyStatus
    ) ||
    data.accountStatus !== 'CURRENT'
  ) {
    throw new Error(
      '등록된 계좌 정보를 확인하지 못했어요.'
    );
  }

  // 화면에서 필요한 필드만 사용
  return {
    gyeAccountId: data.gyeAccountId,
    groupId: data.groupId,
    bankName: data.bankName,
    accountMasked: data.accountMasked,
    accountLast4: data.accountLast4,
    verifyStatus: data.verifyStatus,
    accountStatus: data.accountStatus,
    verifiedAt: data.verifiedAt ?? null
  };
}

// 인증 완료 일시 표시
function formatVerifiedAt(value) {
  if (!value) return '미인증';

  return String(value)
    .replace('T', ' ')
    .slice(0, 16);
}

// HTTP 상태별 오류 문구
function accountErrorMessage(error) {
  switch (error?.status) {
    case 400:
      return '유효하지 않은 모임 정보입니다.';

    case 401:
      return '로그인이 만료되었어요. 다시 로그인해 주세요.';

    case 403:
      return '모임장만 계좌 관리 기능을 이용할 수 있어요.';

    case 404:
      return '등록된 현재 모임 계좌를 찾지 못했어요.';

    case 409:
      return '계좌 상태가 변경되었어요. 새로고침 후 확인해 주세요.';

    case 422:
      return '계좌 소유 정보를 확인하지 못했어요.';

    case 429:
      return '인증 요청이 너무 많아요. 잠시 후 다시 시도해 주세요.';

    case 502:
      return '계좌 인증 서비스에 문제가 발생했어요.';

    case 503:
      return '계좌 소유 인증 서비스를 현재 이용할 수 없어요. 잠시 후 다시 시도해 주세요.';

    default:
      return error?.message ||
        '계좌 정보를 처리하지 못했어요.';
  }
}

// =========================================================
// 모임 변경 시 컴포넌트 상태 초기화
// =========================================================

export default function GroupAccountPanel({ g }) {
  if (!g || g.role !== 'OWNER') {
    return null;
  }

  return (
    <GroupAccount
      key={g.groupId}
      groupId={g.groupId}
    />
  );
}

// =========================================================
// F19 계좌 관리
// =========================================================

function GroupAccount({ groupId }) {
  const [account, setAccount] = useState(null);

  const [loading, setLoading] = useState(true);
  const [verifying, setVerifying] = useState(false);

  const [loadError, setLoadError] = useState('');
  const [verifyError, setVerifyError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  // 컴포넌트 해제 후 상태 갱신 방지
  const mountedRef = useRef(false);

  // 이전 조회 응답이 최신 상태를 덮어쓰는 문제 방지
  const requestIdRef = useRef(0);

  // 인증 요청 중복 실행 방지
  const verifyingRef = useRef(false);

  // =========================================================
  // F19: 현재 계좌 조회
  // =========================================================

  const loadAccount = useCallback(async () => {
    const requestId = ++requestIdRef.current;

    if (!mountedRef.current) {
      return false;
    }

    setLoading(true);
    setLoadError('');

    try {
      const result = await fetchCurrentAccount(groupId);

      if (
        !mountedRef.current ||
        requestId !== requestIdRef.current
      ) {
        return false;
      }

      const validated = validateAccount(
        result,
        groupId
      );

      setAccount(validated);

      return true;

    } catch (error) {
      if (
        !mountedRef.current ||
        requestId !== requestIdRef.current
      ) {
        return false;
      }

      // 조회 실패 시 기존 계좌 정보 노출 방지
      setAccount(null);

      setLoadError(
        accountErrorMessage(error)
      );

      return false;

    } finally {
      if (
        mountedRef.current &&
        requestId === requestIdRef.current
      ) {
        setLoading(false);
      }
    }
  }, [groupId]);

  // 최초 계좌 조회 및 컴포넌트 정리
  useEffect(() => {
    mountedRef.current = true;

    loadAccount();

    return () => {
      mountedRef.current = false;
      requestIdRef.current += 1;
    };
  }, [loadAccount]);

  // =========================================================
  // F19: 계좌 소유 인증
  // =========================================================

  const handleVerify = async () => {
    if (
      loading ||
      verifying ||
      verifyingRef.current ||
      !account ||
      account.accountStatus !== 'CURRENT' ||
      !VERIFIABLE_STATUSES.includes(
        account.verifyStatus
      )
    ) {
      return;
    }

    const confirmed = window.confirm(
      '등록된 모임 계좌의 소유 인증을 진행하시겠어요?\n\n' +
      '계좌 소유 확인 과정에서 실제 송금은 발생하지 않습니다.'
    );

    if (!confirmed) {
      return;
    }

    verifyingRef.current = true;

    setVerifying(true);
    setVerifyError('');
    setSuccessMessage('');

    try {
      const result = await verifyCurrentAccount(groupId);

      if (!mountedRef.current) {
        return;
      }

      // 서버에서 인증 성공을 반환했는지 확인
      if (
        !result ||
        String(result.groupId) !== String(groupId) ||
        String(result.gyeAccountId) !==
          String(account.gyeAccountId) ||
        result.verifyStatus !== 'VERIFIED'
      ) {
        await loadAccount();

        if (mountedRef.current) {
          setVerifyError(
            '계좌 인증 결과를 확인하지 못했어요.'
          );
        }

        return;
      }

      // 인증 성공 응답만으로 화면 상태를 임의 변경하지 않고
      // 현재 계좌 API를 다시 호출하여 DB 상태 확인
      const refreshed = await loadAccount();

      if (!mountedRef.current) {
        return;
      }

      if (!refreshed) {
        setVerifyError(
          '인증 요청은 성공했지만 현재 계좌 정보를 다시 불러오지 못했어요. 새로고침해 주세요.'
        );

        return;
      }

      setSuccessMessage(
        '계좌 인증 요청이 완료되었어요. 현재 계좌 상태를 확인해 주세요.'
      );

    } catch (error) {
      if (!mountedRef.current) {
        return;
      }

      // 권한 변경 또는 계좌 상태 변경 시 재조회
      if (
        [401, 403, 404, 409].includes(
          error?.status
        )
      ) {
        await loadAccount();
      }

      if (mountedRef.current) {
        setVerifyError(
          accountErrorMessage(error)
        );
      }

    } finally {
      verifyingRef.current = false;

      if (mountedRef.current) {
        setVerifying(false);
      }
    }
  };

  // =========================================================
  // 계좌 정보 새로고침
  // =========================================================

  const handleRefresh = async () => {
    if (
      loading ||
      verifying ||
      verifyingRef.current
    ) {
      return;
    }

    setVerifyError('');
    setSuccessMessage('');

    await loadAccount();
  };

  const canVerify =
    account &&
    account.accountStatus === 'CURRENT' &&
    VERIFIABLE_STATUSES.includes(
      account.verifyStatus
    );

  // =========================================================
  // 화면 렌더링
  // =========================================================

  return (
    <div
      className="policy-panel"
      style={{ marginTop: 16 }}
      aria-busy={loading || verifying}
    >
      <div className="item-head">
        <h3>모임 계좌 관리</h3>

        <span className="pill">
          모임장 전용
        </span>
      </div>

      {loading ? (
        <p className="muted" role="status">
          모임 계좌를 불러오는 중이에요.
        </p>

      ) : loadError ? (
        <p
          role="alert"
          style={{
            color: '#c0392b',
            marginTop: 12
          }}
        >
          {loadError}
        </p>

      ) : account ? (
        <>
          {/* 등록된 계좌 정보 */}
          <div
            className="policy-lock"
            style={{ marginTop: 12 }}
          >
            <div className="item-head">
              <div>
                <p className="muted mini">
                  등록된 모임 계좌
                </p>

                <strong>
                  {account.bankName}
                </strong>

                <p
                  className="acct-mask"
                  style={{
                    fontSize: 20,
                    fontWeight: 700,
                    margin: '8px 0'
                  }}
                >
                  {account.accountMasked}
                </p>
              </div>

              <span
                className={
                  account.verifyStatus === 'VERIFIED'
                    ? 'pill'
                    : 'pill warn'
                }
              >
                {
                  ACCOUNT_VERIFY_LABEL[
                    account.verifyStatus
                  ]
                }
              </span>
            </div>

            <div className="group-info-rows">
              <div>
                <span>계좌 상태</span>

                <strong>
                  {
                    ACCOUNT_STATUS_LABEL[
                      account.accountStatus
                    ] || '확인 중'
                  }
                </strong>
              </div>

              <div>
                <span>소유 인증 상태</span>

                <strong>
                  {
                    ACCOUNT_VERIFY_LABEL[
                      account.verifyStatus
                    ] || '확인 중'
                  }
                </strong>
              </div>

              <div>
                <span>인증 완료 일시</span>

                <strong>
                  {
                    formatVerifiedAt(
                      account.verifiedAt
                    )
                  }
                </strong>
              </div>
            </div>
          </div>

          {/* 인증 상태별 안내 */}
          {account.verifyStatus === 'PENDING' && (
            <p
              className="info"
              style={{ marginTop: 12 }}
            >
              등록된 모임 계좌의 소유 인증을
              진행해 주세요.
            </p>
          )}

          {account.verifyStatus ===
            'REVERIFY_REQUIRED' && (
            <p
              className="info"
              style={{ marginTop: 12 }}
            >
              계좌 소유 재인증이 필요합니다.
              인증 완료 전까지 송금을 보류해 주세요.
            </p>
          )}

          {account.verifyStatus === 'VERIFIED' && (
            <p
              className="muted"
              style={{ marginTop: 12 }}
            >
              계좌 소유 인증이 완료되었어요.
            </p>
          )}

        </>
      ) : null}

      {/* 인증 요청 오류 */}
      {verifyError && (
        <p
          role="alert"
          style={{
            color: '#c0392b',
            marginTop: 12
          }}
        >
          {verifyError}
        </p>
      )}

      {/* 인증 성공 안내 */}
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

      {/* 계좌 관리 버튼 */}
      <div
        className="actions"
        style={{ marginTop: 12 }}
      >
        {!loading && !loadError && canVerify && (
          <button
            type="button"
            className="btn primary"
            disabled={verifying}
            onClick={handleVerify}
          >
            {verifying
              ? '인증 진행 중...'
              : account.verifyStatus ===
                  'REVERIFY_REQUIRED'
                ? '계좌 재인증하기'
                : '계좌 소유 인증하기'}
          </button>
        )}

        <button
          type="button"
          className="btn soft"
          disabled={loading || verifying}
          onClick={handleRefresh}
        >
          새로고침
        </button>
      </div>

      {/* 계좌 안전 안내 */}
      <details className="policy-fold">
        <summary>계좌 안전 안내</summary>

        <p className="muted">
          계좌번호는 개인정보 보호를 위해
          마스킹하여 표시합니다.
          계좌 소유 인증은 등록된 계좌 정보를
          기준으로 서버에서 진행합니다.
          인증 과정에서 실제 송금은 발생하지 않습니다.
        </p>
      </details>
    </div>
  );
}
