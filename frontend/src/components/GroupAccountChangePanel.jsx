
/*
 * MOAVA F20 — 모임 계좌 변경 신청 및 상태 확인
 *
 * - F19 기존 계좌 관리 코드 수정 없음
 * - 변경 신청 후 계좌번호 입력값 제거
 * - 변경 신청 ID 자동 반영
 * - 동의 / 준비 상태 / 모의 계좌 조회 독립 처리
 * - 일부 조회 실패 시 정상 결과 유지
 * - 모임장 본인 동의 패널 연동
 * - 실제 잔액 이전 및 최종 확정 API 호출 없음
 */

import { useEffect, useRef, useState } from 'react';

import {
  createAccountChange,
  fetchAccountChangeConsents,
  fetchAccountChangeReadiness,
  fetchAccountChangeMockLinks
} from '../api/groupAccountChanges';

import GroupAccountOwnerConsent
  from './GroupAccountOwnerConsent';

// =========================================================
// 1. 입력값 검증
// =========================================================

function validAccountId(value) {
  const id = String(value ?? '');

  return (
    /^[1-9]\d*$/.test(id) &&
    Number.isSafeInteger(Number(id))
  );
}

function validateForm(bankName, accountNumber) {
  const bank = bankName.trim();
  const account = accountNumber.trim();

  if (!bank) {
    return '은행명을 입력해 주세요.';
  }

  if (new TextEncoder().encode(bank).length > 50) {
    return '은행명은 UTF-8 기준 50바이트 이하여야 합니다.';
  }

  if (!/^[0-9]{8,30}$/.test(account)) {
    return '계좌번호는 숫자 8~30자리로 입력해 주세요.';
  }

  return '';
}

function displayMoney(value) {
  if (value == null) {
    return '조회 불가';
  }

  const amount = Number(value);

  if (
    !Number.isSafeInteger(amount) ||
    amount < 0
  ) {
    return '확인 필요';
  }

  return `${amount.toLocaleString('ko-KR')}원`;
}

// =========================================================
// 2. 신청 요청 오류 메시지
// =========================================================

function statusMessage(error) {
  switch (error?.status) {
    case 400:
      return '은행명 또는 계좌번호를 확인해 주세요.';

    case 401:
      return '로그인이 만료됐어요. 다시 로그인해 주세요.';

    case 403:
      return '모임장만 계좌 변경을 신청할 수 있어요.';

    case 404:
      return '모임 또는 계좌 정보를 찾을 수 없어요.';

    case 409:
      return (
        '이미 진행 중인 변경 신청이 있거나 ' +
        '계좌 정보가 중복됐어요.'
      );

    case 422:
      return '계좌 정보 또는 신청 조건을 확인해 주세요.';

    default:
      return (
        error?.message ||
        '계좌 변경 요청을 처리하지 못했어요.'
      );
  }
}

// =========================================================
// 3. 조회 API별 오류 메시지
// =========================================================

function lookupErrorMessage(kind, error) {
  const labels = {
    consents: '회원 동의 현황',
    readiness: '계좌 변경 준비 상태',
    mockLinks: '모의 계좌 연결 상태'
  };

  const messages = {
    consents: {
      401: '로그인 후 다시 조회해 주세요.',
      403: '해당 모임의 활성 회원만 조회할 수 있어요.',
      404: '진행 중인 계좌 변경 신청이 없어요.',
      409: '회원 동의 집계 상태를 확인할 수 없어요.'
    },

    readiness: {
      401: '로그인 후 다시 조회해 주세요.',
      403: '모임장 권한이 필요해요.',
      404: '계좌 변경 준비 상태를 찾을 수 없어요.',
      409: '현재 진행 중인 신청과 일치하지 않아요.'
    },

    mockLinks: {
      401: '로그인 후 다시 조회해 주세요.',
      403: '모임장 권한이 필요해요.',
      404: '기존 또는 신규 계좌 정보를 찾을 수 없어요.',
      503: '모의 계좌 연결 정보를 확인할 수 없어요.'
    }
  };

  const detail =
    messages[kind]?.[error?.status] ||
    error?.message ||
    '조회에 실패했어요.';

  return `${labels[kind]}: ${detail}`;
}

// =========================================================
// 4. API 응답 유효성 검사
// =========================================================

function normalizeLookup(
  kind,
  settledResult,
  groupId,
  accountId
) {
  if (settledResult.status === 'rejected') {
    return {
      value: null,
      error: lookupErrorMessage(
        kind,
        settledResult.reason
      )
    };
  }

  const value = settledResult.value;

  if (
    !value ||
    String(value.groupId) !== String(groupId) ||
    String(value.gyeAccountId) !== String(accountId)
  ) {
    return {
      value: null,
      error:
        `${kind === 'consents'
          ? '회원 동의 현황'
          : kind === 'readiness'
            ? '계좌 변경 준비 상태'
            : '모의 계좌 연결 상태'
        }: 응답의 모임 ID 또는 신청 ID가 일치하지 않아요.`
    };
  }

  return {
    value,
    error: ''
  };
}

// =========================================================
// 5. 모임장 전용 패널
// =========================================================

export default function GroupAccountChangePanel({ g }) {
  if (!g || g.role !== 'OWNER') {
    return null;
  }

  return (
    <GroupAccountChangeContent
      key={g.groupId}
      groupId={g.groupId}
    />
  );
}

// =========================================================
// 6. 계좌 변경 신청 및 상태 조회
// =========================================================

function GroupAccountChangeContent({ groupId }) {

  const [bankName, setBankName] = useState('');
  const [accountNumber, setAccountNumber] = useState('');

  const [accountId, setAccountId] = useState('');

  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(false);

  const [error, setError] = useState('');
  const [lookupError, setLookupError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const [result, setResult] = useState(null);

  const mountedRef = useRef(false);
  const submittingRef = useRef(false);
  const requestIdRef = useRef(0);

  useEffect(() => {
    mountedRef.current = true;

    return () => {
      mountedRef.current = false;
      requestIdRef.current += 1;
    };
  }, []);

  // =========================================================
  // 7. 변경 신청 상태 조회
  // =========================================================

  const loadChangeStatus = async (targetAccountId) => {

    if (!validAccountId(targetAccountId)) {
      setLookupError('올바른 신청 ID를 입력해 주세요.');
      return;
    }

    const requestedGroupId = String(groupId);
    const requestedAccountId = String(targetAccountId);

    const requestId = ++requestIdRef.current;

    setLoading(true);
    setLookupError('');
    setResult(null);

    try {
      // 세 API를 개별적으로 처리한다.
      // 하나가 실패해도 나머지 결과는 유지된다.
      const settled = await Promise.allSettled([
        fetchAccountChangeConsents(
          groupId,
          targetAccountId
        ),
        fetchAccountChangeReadiness(
          groupId,
          targetAccountId
        ),
        fetchAccountChangeMockLinks(
          groupId,
          targetAccountId
        )
      ]);

      // 다른 신청으로 이동했거나 컴포넌트가
      // 제거됐다면 이전 응답을 폐기한다.
      if (
        !mountedRef.current ||
        requestId !== requestIdRef.current
      ) {
        return;
      }

      const consents = normalizeLookup(
        'consents',
        settled[0],
        requestedGroupId,
        requestedAccountId
      );

      const readiness = normalizeLookup(
        'readiness',
        settled[1],
        requestedGroupId,
        requestedAccountId
      );

      const mockLinks = normalizeLookup(
        'mockLinks',
        settled[2],
        requestedGroupId,
        requestedAccountId
      );

      const errors = {
        consents: consents.error,
        readiness: readiness.error,
        mockLinks: mockLinks.error
      };

      // 정상 결과는 API별로 독립 저장
      setResult({
        accountId: requestedAccountId,
        consents: consents.value,
        readiness: readiness.value,
        mockLinks: mockLinks.value,
        errors
      });

      const failedCount =
        Object.values(errors).filter(Boolean).length;

      if (failedCount === 3) {
        setLookupError(
          '모든 상태 조회에 실패했어요. 아래에서 요청별 원인을 확인해 주세요.'
        );

      } else if (failedCount > 0) {
        setLookupError(
          '일부 상태 조회에 실패했어요. 정상 조회된 정보는 아래에 표시됩니다.'
        );
      }

    } catch {
      if (
        mountedRef.current &&
        requestId === requestIdRef.current
      ) {
        setLookupError(
          '계좌 변경 상태 조회 중 오류가 발생했어요.'
        );
      }

    } finally {
      if (
        mountedRef.current &&
        requestId === requestIdRef.current
      ) {
        setLoading(false);
      }
    }
  };

  // =========================================================
  // 8. 신규 계좌 변경 신청
  // =========================================================

  const handleSubmit = async event => {
    event.preventDefault();

    if (submittingRef.current || loading) {
      return;
    }

    const validationError = validateForm(
      bankName,
      accountNumber
    );

    if (validationError) {
      setError(validationError);
      return;
    }

    const confirmed = window.confirm(
      '새 계좌로 변경 신청하시겠어요?\n\n' +
      '신청 후 회원 전원 동의와 계좌 소유 인증이 필요합니다.\n' +
      '신청만으로 잔액이 이전되지는 않습니다.'
    );

    if (!confirmed) {
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);

    setError('');
    setLookupError('');
    setSuccessMessage('');

    // 신규 신청 전에 기존 조회 결과는 무효화
    requestIdRef.current += 1;
    setResult(null);

    try {
      const response = await createAccountChange(
        groupId,
        {
          bankName: bankName.trim(),
          accountNumber: accountNumber.trim()
        }
      );

      if (!mountedRef.current) {
        return;
      }

      // 성공 시 민감정보 입력값 제거
      setAccountNumber('');

      const newAccountId = response?.gyeAccountId;

      if (
        String(response?.groupId) !== String(groupId) ||
        !validAccountId(newAccountId)
      ) {
        setError(
          '신청 응답의 ID를 확인하지 못했어요. ' +
          '중복 신청하지 말고 기존 신청 내역을 확인해 주세요.'
        );
        return;
      }

      // 서버가 반환한 변경 신청 ID 자동 반영
      setAccountId(String(newAccountId));

      setSuccessMessage(
        '계좌 변경 신청이 접수됐어요. ' +
        '회원 동의 및 신규 계좌 인증이 필요합니다.'
      );

      // 자동 상태 조회
      await loadChangeStatus(String(newAccountId));

    } catch (err) {
      if (mountedRef.current) {
        setError(statusMessage(err));
      }

    } finally {
      submittingRef.current = false;

      if (mountedRef.current) {
        setSubmitting(false);
      }
    }
  };

  // =========================================================
  // 9. 기존 신청 ID 입력 및 조회
  // =========================================================

  const handleAccountIdChange = event => {
    if (submittingRef.current || loading) {
      return;
    }

    requestIdRef.current += 1;

    setAccountId(event.target.value.trim());
    setResult(null);
    setLookupError('');
    setError('');
    setSuccessMessage('');
  };

  const handleLookup = async () => {
    if (
      submittingRef.current ||
      loading ||
      !validAccountId(accountId)
    ) {
      return;
    }

    await loadChangeStatus(accountId);
  };

  // =========================================================
  // 10. 조회 결과
  // =========================================================

  const consents = result?.consents;
  const readiness = result?.readiness;
  const links = result?.mockLinks;

  const lookupErrors = result?.errors ?? {};

  // =========================================================
  // 11. 화면 렌더링
  // =========================================================

  return (
    <div
      className="policy-panel"
      style={{ marginTop: 16 }}
      aria-busy={submitting || loading}
    >

      {/* 패널 제목 */}
      <div className="item-head">
        <h3>모임 계좌 변경</h3>

        <span className="pill">
          모임장 전용
        </span>
      </div>

      <p className="muted">
        신규 계좌 변경을 신청하고 회원 동의와
        모의 은행 연결 상태를 확인합니다.
      </p>

      {/* =============================================== */}
      {/* 신규 계좌 변경 신청 */}
      {/* =============================================== */}

      <form
        onSubmit={handleSubmit}
        style={{ marginTop: 16 }}
      >
        <h4>신규 계좌 변경 신청</h4>

        <div
          className="field"
          style={{
            maxWidth: 400,
            marginTop: 12
          }}
        >
          <label htmlFor={`change-bank-${groupId}`}>
            변경할 은행명
          </label>

          <input
            id={`change-bank-${groupId}`}
            type="text"
            value={bankName}
            onChange={event => {
              setBankName(event.target.value);
              setError('');
            }}
            placeholder="예: 국민은행"
            autoComplete="off"
            disabled={submitting}
            maxLength={50}
            required
          />
        </div>

        <div
          className="field"
          style={{ maxWidth: 400 }}
        >
          <label htmlFor={`change-number-${groupId}`}>
            신규 계좌번호
          </label>

          <input
            id={`change-number-${groupId}`}
            type="text"
            inputMode="numeric"
            value={accountNumber}
            onChange={event => {
              setAccountNumber(event.target.value);
              setError('');
            }}
            placeholder="숫자만 입력"
            autoComplete="off"
            disabled={submitting}
            maxLength={30}
            required
          />
        </div>

        <p className="muted mini">
          변경 신청만으로 기존 계좌가 교체되거나
          잔액이 이전되지는 않습니다.
          회원 전원 동의 및 계좌 인증이 필요합니다.
        </p>

        <div
          className="actions"
          style={{ marginTop: 12 }}
        >
          <button
            type="submit"
            className="btn primary"
            disabled={submitting || loading}
          >
            {submitting
              ? '변경 신청 중...'
              : '계좌 변경 신청'}
          </button>
        </div>
      </form>

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

      {/* =============================================== */}
      {/* 신청 ID 및 상태 조회 */}
      {/* =============================================== */}

      <div style={{ marginTop: 24 }}>
        <h4>계좌 변경 신청 상태</h4>

        <div
          className="field"
          style={{
            maxWidth: 400,
            marginTop: 12
          }}
        >
          <label htmlFor={`change-id-${groupId}`}>
            계좌 변경 신청 ID
          </label>

          <input
            id={`change-id-${groupId}`}
            type="text"
            inputMode="numeric"
            value={accountId}
            onChange={handleAccountIdChange}
            placeholder="신청 후 자동 표시 또는 ID 입력"
            disabled={submitting || loading}
            autoComplete="off"
          />
        </div>

        <div
          className="actions"
          style={{ marginTop: 12 }}
        >
          <button
            type="button"
            className="btn soft"
            disabled={
              submitting ||
              loading ||
              !validAccountId(accountId)
            }
            onClick={handleLookup}
          >
            {loading
              ? '조회 중...'
              : '변경 상태 확인'}
          </button>
        </div>

        {lookupError && (
          <p
            role="alert"
            style={{
              color: '#c0392b',
              marginTop: 12
            }}
          >
            신청 ID는 유지돼 있어요. {lookupError}
          </p>
        )}
      </div>

      {/* =============================================== */}
      {/* API별 상태 조회 결과 */}
      {/* =============================================== */}

      {result && (
        <div style={{ marginTop: 20 }}>

          {/* 각 API의 개별 오류 */}
          {Object.values(lookupErrors)
            .filter(Boolean)
            .map((message, index) => (
              <p
                key={`${index}-${message}`}
                role="alert"
                style={{
                  color: '#c0392b',
                  marginTop: 8
                }}
              >
                {message}
              </p>
            ))}

          {/* =========================================== */}
          {/* 1. 회원 동의 현황 */}
          {/* =========================================== */}

          {consents && (
            <div className="policy-lock">
              <h4>회원 동의 현황</h4>

              <div className="group-info-rows">
                <div>
                  <span>전체 활성 회원</span>
                  <strong>
                    {consents.totalMembers ?? '-'}명
                  </strong>
                </div>

                <div>
                  <span>동의 완료</span>
                  <strong>
                    {consents.agreedMembers ?? '-'}명
                  </strong>
                </div>

                <div>
                  <span>거절</span>
                  <strong>
                    {consents.rejectedMembers ?? '-'}명
                  </strong>
                </div>

                <div>
                  <span>미응답</span>
                  <strong>
                    {consents.pendingMembers ?? '-'}명
                  </strong>
                </div>

                <div>
                  <span>전원 동의 여부</span>
                  <strong>
                    {consents.allAgreed === true
                      ? '전원 동의 완료'
                      : '동의 진행 중'}
                  </strong>
                </div>
              </div>
            </div>
          )}

          {/* =========================================== */}
          {/* 2. 모임장 본인 동의 */}
          {/* 동의 현황 GET 성공에만 종속 */}
          {/* =========================================== */}

          {consents && (
            <GroupAccountOwnerConsent
              key={`${groupId}-${result.accountId}`}
              groupId={groupId}
              accountId={result.accountId}
            />
          )}

          {/* =========================================== */}
          {/* 3. 계좌 변경 준비 상태 */}
          {/* =========================================== */}

          {readiness && (
            <div
              className="policy-lock"
              style={{ marginTop: 16 }}
            >
              <h4>계좌 변경 사전 확인</h4>

              <div className="group-info-rows">
                <div>
                  <span>회원 전원 동의</span>
                  <strong>
                    {readiness.consentReady === true
                      ? '충족'
                      : '미충족'}
                  </strong>
                </div>

                <div>
                  <span>신규 계좌 소유 인증</span>
                  <strong>
                    {readiness.ownershipVerified === true
                      ? '인증 완료'
                      : '인증 필요'}
                  </strong>
                </div>

                <div>
                  <span>모의 잔액 이전 준비</span>
                  <strong>
                    {readiness.mockTransferReady === true
                      ? '충족'
                      : '미충족'}
                  </strong>
                </div>

                <div>
                  <span>최종 확정 가능 여부</span>
                  <strong>
                    {readiness.finalizeReady === true
                      ? '서버 사전 조건 충족'
                      : '확정 불가'}
                  </strong>
                </div>
              </div>

              {Array.isArray(readiness.blockers) &&
                readiness.blockers.length > 0 && (
                  <div
                    className="info"
                    style={{ marginTop: 12 }}
                  >
                    <strong>준비 상태 확인 사항</strong>

                    <ul>
                      {readiness.blockers.map(
                        (message, index) => (
                          <li key={`${index}-${message}`}>
                            {message}
                          </li>
                        )
                      )}
                    </ul>
                  </div>
                )}
            </div>
          )}

          {/* =========================================== */}
          {/* 4. 모의 계좌 연결 상태 */}
          {/* =========================================== */}

          {links && (
            <div
              className="policy-lock"
              style={{ marginTop: 16 }}
            >
              <h4>모의 계좌 연결 상태</h4>

              <div className="group-info-rows">
                <div>
                  <span>기존 모의 계좌</span>
                  <strong>
                    {links.currentLinked === true
                      ? '연결 확인'
                      : '연결 불가'}
                  </strong>
                </div>

                <div>
                  <span>신규 모의 계좌</span>
                  <strong>
                    {links.pendingLinked === true
                      ? '연결 확인'
                      : '연결 불가'}
                  </strong>
                </div>

                <div>
                  <span>기존 모의 잔액</span>
                  <strong>
                    {displayMoney(
                      links.currentBalance
                    )}
                  </strong>
                </div>

                <div>
                  <span>신규 모의 잔액</span>
                  <strong>
                    {displayMoney(
                      links.pendingBalance
                    )}
                  </strong>
                </div>

                <div>
                  <span>모의 이체 사전 조건</span>
                  <strong>
                    {links.transferPrerequisitesReady === true
                      ? '충족'
                      : '미충족'}
                  </strong>
                </div>
              </div>

              {Array.isArray(links.blockers) &&
                links.blockers.length > 0 && (
                  <div
                    className="info"
                    style={{ marginTop: 12 }}
                  >
                    <strong>모의 계좌 확인 사항</strong>

                    <ul>
                      {links.blockers.map(
                        (message, index) => (
                          <li key={`${index}-${message}`}>
                            {message}
                          </li>
                        )
                      )}
                    </ul>
                  </div>
                )}
            </div>
          )}

          {/* =========================================== */}
          {/* 5. 최종 확정 기능 보류 */}
          {/* =========================================== */}

          <p
            className="muted"
            style={{ marginTop: 16 }}
          >
            실제 잔액 이전과 계좌 변경 최종 확정은
            트랜잭션 및 모의 은행 연동 검증 이후
            제공됩니다.
          </p>

          <button
            type="button"
            className="btn soft"
            disabled
            title="안전성 검증 완료 후 연결 예정"
          >
            계좌 변경 최종 확정 (준비 중)
          </button>

        </div>
      )}
    </div>
  );
}
