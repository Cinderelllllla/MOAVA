import {
  useCallback,
  useEffect,
  useRef,
  useState
} from 'react';

import {
  fetchLeaderTransfer,
  createLeaderTransfer,
  decideLeaderTransfer,
  consentLeaderTransfer,
  confirmLeaderTransfer,
  cancelLeaderTransfer
} from '../api/leaderTransfers';

export default function LeaderTransferPanel({ groupId }) {

  const [data, setData] = useState(null);
  const [targetId, setTargetId] = useState('');

  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);

  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const generation = useRef(0);
  const sendingRef = useRef(false);
  const alive = useRef(false);

  // ==============================================
  // 조회
  // ==============================================

  const load = useCallback(async () => {
    const token = ++generation.current;

    setLoading(true);
    setError('');

    try {
      const result =
        await fetchLeaderTransfer(groupId);

      if (
        !alive.current ||
        token !== generation.current
      ) {
        return;
      }

      if (
        String(result?.groupId) !== String(groupId)
      ) {
        throw new Error(
          '모임 응답이 일치하지 않아요.'
        );
      }

      setData(result);

    } catch (err) {

      if (
        alive.current &&
        token === generation.current
      ) {
        setData(null);
        setError(err.message);
      }

    } finally {

      if (
        alive.current &&
        token === generation.current
      ) {
        setLoading(false);
      }
    }
  }, [groupId]);

  useEffect(() => {
    alive.current = true;

    load();

    return () => {
      alive.current = false;
      generation.current += 1;
    };
  }, [load]);

  // ==============================================
  // POST 공통 처리
  // ==============================================

  const perform = async (
    action,
    question,
    successText
  ) => {
    if (
      sendingRef.current ||
      loading ||
      !data
    ) {
      return;
    }

    if (
      question &&
      !window.confirm(question)
    ) {
      return;
    }

    sendingRef.current = true;

    setSending(true);
    setError('');
    setMessage('');

    generation.current += 1;

    try {
      const result = await action();

      if (!alive.current) {
        return;
      }

      if (
        String(result?.groupId) !== String(groupId)
      ) {
        throw new Error(
          '처리 결과 모임 ID가 일치하지 않습니다.'
        );
      }

      setData(result);
      setMessage(successText);

    } catch (err) {

      if (alive.current) {
        setError(
          `${err.message} ` +
          '재제출 전에 새로고침으로 상태를 확인해 주세요.'
        );

        setData(null);
      }

    } finally {

      sendingRef.current = false;

      if (alive.current) {
        setSending(false);
      }
    }
  };

  // ==============================================
  // 상태
  // ==============================================

  const pending =
    data?.status === 'PENDING' &&
    !!data?.transferId;

  const candidates =
    Array.isArray(data?.candidates)
      ? data.candidates
      : [];

  // ==============================================
  // 화면
  // ==============================================

  return (
    <section
      className="card"
      style={{ marginTop: 16 }}
      aria-busy={loading || sending}
    >
      <h3>F21 모임장 권한 승계</h3>

      <p className="muted">
        승계 대상 회원 수락과 활성 회원 전원 동의가
        완료되면 기존 모임장이 권한을 이전할 수 있습니다.
      </p>

      <button
        type="button"
        className="btn soft"
        disabled={loading || sending}
        onClick={load}
      >
        {loading ? '조회 중...' : '새로고침'}
      </button>

      {error && (
        <p
          role="alert"
          style={{ color: '#b91c1c' }}
        >
          {error}
        </p>
      )}

      {message && (
        <p
          role="status"
          style={{ color: '#15803d' }}
        >
          {message}
        </p>
      )}

      {/* ===================================== */}
      {/* 모임장 신규 신청 */}
      {/* ===================================== */}

      {data?.status === 'NONE' &&
        data.isOwner && (
          <div style={{ marginTop: 12 }}>

            <label
              htmlFor={`transfer-target-${groupId}`}
            >
              승계 대상 활성 회원
            </label>

            <select
              id={`transfer-target-${groupId}`}
              value={targetId}
              disabled={sending || loading}
              onChange={event =>
                setTargetId(event.target.value)
              }
            >
              <option value="">
                대상 회원 선택
              </option>

              {candidates.map(member => (
                <option
                  key={member.memberId}
                  value={member.memberId}
                >
                  {member.name} (#{member.memberId})
                </option>
              ))}
            </select>

            <button
              type="button"
              className="btn primary"
              disabled={
                !targetId ||
                sending ||
                loading
              }
              onClick={() =>
                perform(
                  () =>
                    createLeaderTransfer(
                      groupId,
                      targetId
                    ),
                  '선택한 회원을 다음 모임장으로 지정할까요?',
                  '승계 요청을 등록했습니다.'
                )
              }
            >
              승계 신청
            </button>

          </div>
        )}

      {/* ===================================== */}
      {/* 진행 중 신청 없음 */}
      {/* ===================================== */}

      {data?.status === 'NONE' &&
        !data.isOwner && (
          <p>
            현재 진행 중인 승계 신청이 없어요.
          </p>
        )}

      {/* ===================================== */}
      {/* 진행 중 승계 */}
      {/* ===================================== */}

      {pending && (
        <div style={{ marginTop: 12 }}>

          <h4>
            승계 신청 #{data.transferId}
          </h4>

          <p>
            현재 모임장 #{data.fromMemberId}
            {' → '}
            대상 회원 #{data.toMemberId}
          </p>

          <p>
            대상 회원 수락:
            {' '}
            <strong>
              {data.targetAccepted
                ? '수락 완료'
                : '대기 중'}
            </strong>
          </p>

          <p>
            활성 회원 동의:
            {' '}
            <strong>
              {data.agreedMembers}
              {' / '}
              {data.totalMembers}
            </strong>
          </p>

          <p>
            미응답:
            {' '}
            {data.pendingMembers}명
          </p>

          <p>
            내 동의 상태:
            {' '}
            {data.myConsent === 'Y'
              ? '동의 완료'
              : data.myConsent === 'N'
                ? '거절 완료'
                : '미응답'}
          </p>

          {/* ================================= */}
          {/* 승계 대상 수락 / 거절 */}
          {/* ================================= */}

          {data.isTarget &&
            !data.targetAccepted && (
              <div className="actions">

                <button
                  type="button"
                  className="btn primary"
                  disabled={sending}
                  onClick={() =>
                    perform(
                      () =>
                        decideLeaderTransfer(
                          groupId,
                          data.transferId,
                          true
                        ),
                      '모임장 승계를 수락하시겠습니까?',
                      '승계를 수락했습니다.'
                    )
                  }
                >
                  승계 대상 수락
                </button>

                <button
                  type="button"
                  className="btn soft"
                  disabled={sending}
                  onClick={() =>
                    perform(
                      () =>
                        decideLeaderTransfer(
                          groupId,
                          data.transferId,
                          false
                        ),
                      '거절하면 현재 승계 신청이 종료됩니다. 진행할까요?',
                      '승계 신청을 거절했습니다.'
                    )
                  }
                >
                  승계 대상 거절
                </button>

              </div>
            )}

          {/* ================================= */}
          {/* 회원 동의 / 거절 */}
          {/* ================================= */}

          {data.canConsent &&
            data.myConsent == null && (
              <div className="actions">

                <button
                  type="button"
                  className="btn primary"
                  disabled={sending}
                  onClick={() =>
                    perform(
                      () =>
                        consentLeaderTransfer(
                          groupId,
                          data.transferId,
                          true
                        ),
                      '이 모임장 승계에 동의하시겠습니까? 동의 후 변경할 수 없습니다.',
                      '동의했습니다.'
                    )
                  }
                >
                  승계 동의
                </button>

                <button
                  type="button"
                  className="btn soft"
                  disabled={sending}
                  onClick={() =>
                    perform(
                      () =>
                        consentLeaderTransfer(
                          groupId,
                          data.transferId,
                          false
                        ),
                      '거절하면 현재 승계 신청이 종료됩니다. 진행할까요?',
                      '승계 신청을 거절했습니다.'
                    )
                  }
                >
                  승계 거절
                </button>

              </div>
            )}

          {/* ================================= */}
          {/* 기존 모임장 최종 확정 / 취소 */}
          {/* ================================= */}

          {data.isOwner && (
            <div
              className="actions"
              style={{ marginTop: 12 }}
            >
              <button
                type="button"
                className="btn primary"
                disabled={
                  sending ||
                  !data.canConfirm
                }
                onClick={() =>
                  perform(
                    () =>
                      confirmLeaderTransfer(
                        groupId,
                        data.transferId
                      ),
                    '활성 회원 전원 동의가 완료됐습니다.\n모임장 권한을 실제로 변경할까요?',
                    '모임장 승계가 확정됐습니다. 모임 목록을 새로고침해 주세요.'
                  )
                }
              >
                승계 최종 확정
              </button>

              <button
                type="button"
                className="btn soft"
                disabled={sending}
                onClick={() =>
                  perform(
                    () =>
                      cancelLeaderTransfer(
                        groupId,
                        data.transferId
                      ),
                    '진행 중인 승계 신청을 취소할까요?',
                    '승계 신청을 취소했습니다.'
                  )
                }
              >
                승계 신청 취소
              </button>
            </div>
          )}

        </div>
      )}
    </section>
  );
}
