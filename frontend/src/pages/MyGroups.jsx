
/* S08 내 계모임 — /my-groups
   서버(GET /api/groups/my)의 "내가 만든 계모임" 목록.
   F18: 비공개 모임 인증코드 발급·재발급
*/

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import useMyGroups from '../common/useMyGroups';
import { groups, finance } from '../api';
import { PageHead, Card } from '../components/ui';
import { LoadState } from '../components/shared';
import { wonFromKrw } from '../common/format';

export default function MyGroups() {
  const nav = useNavigate();
  const myGroups = useMyGroups();
  const list = myGroups.items;

  // F18: 모임별 발급 상태
  const [issuingGroupId, setIssuingGroupId] = useState(null);
  const [issuedCodes, setIssuedCodes] = useState({});
  const [issueErrors, setIssueErrors] = useState({});

  const income = Number(finance.getFinance().income) || 0;
  const monthlyTotal = list.reduce(
    (sum, g) => sum + Number(g.amount || 0) / 10000,
    0
  );
  const burden = income > 0
    ? Math.round(monthlyTotal / income * 100)
    : null;
  const capPercent = Math.round(
    finance.getPolicy().incomeCapRatio * 100
  );

  // F18: 인증코드 발급 및 재발급
  const handleIssueJoinCode = async (groupId) => {
    if (issuingGroupId !== null) return;

    const confirmed = window.confirm(
      '인증코드를 발급하시겠어요?\n\n' +
      '기존 인증코드가 있다면 무효화되고 새 코드가 발급됩니다.'
    );

    if (!confirmed) return;

    setIssuingGroupId(groupId);

    // 재발급 요청 시 기존 코드 숨김
    setIssuedCodes(prev => {
      const next = { ...prev };
      delete next[groupId];
      return next;
    });

    setIssueErrors(prev => ({
      ...prev,
      [groupId]: ''
    }));

    try {
      const result = await groups.issueJoinCode(groupId);

      if (!/^\d{6}$/.test(String(result?.joinCode ?? ''))) {
        throw new Error(
          '인증코드 발급 결과를 확인하지 못했어요.'
        );
      }

      setIssuedCodes(prev => ({
        ...prev,
        [groupId]: String(result.joinCode)
      }));
    } catch (error) {
      setIssueErrors(prev => ({
        ...prev,
        [groupId]: error.message
      }));
    } finally {
      setIssuingGroupId(null);
    }
  };

  const handleHideJoinCode = (groupId) => {
    setIssuedCodes(prev => {
      const next = { ...prev };
      delete next[groupId];
      return next;
    });
  };

  return (
    <>
      <PageHead
        kicker="MY GROUPS"
        title="내 계모임"
        sub="내가 모임장으로 만든 계모임 목록입니다."
      >
        <button
          className="btn primary"
          onClick={() => nav('/groups/new')}
        >
          새 계모임 만들기
        </button>
      </PageHead>

      {/* 상단 통계 */}
      <div className="grid cols3">
        <Card>
          <small>내가 만든 계모임</small>
          <div className="metric">
            {myGroups.status === 'loading'
              ? '조회 중'
              : myGroups.status === 'error'
                ? '-'
                : `${list.length}개`}
          </div>
        </Card>

        <Card>
          <small>월 납입 합계</small>
          <div className="metric">
            {myGroups.status === 'ready'
              ? wonFromKrw(monthlyTotal * 10000)
              : '-'}
          </div>
        </Card>

        <Card>
          <small>개인 납입 부담률 (참고)</small>
          <div className="metric">
            {myGroups.status !== 'ready' || burden == null
              ? '-'
              : `${burden}%`}

            {myGroups.status === 'ready' &&
              burden != null &&
              burden > capPercent && (
                <span className="pill warn">
                  {capPercent}% 초과
                </span>
              )}
          </div>

          {burden == null && (
            <small>
              맞춤 추천에서 월수입을 입력하면 계산돼요.
            </small>
          )}
        </Card>
      </div>

      <div className="section list">
        <LoadState
          status={myGroups.status}
          error={myGroups.error}
          onRetry={myGroups.reload}
          loadingText="내 계모임을 불러오는 중이에요."
        >
          {list.length ? (
            list.map(g => (
              <Card key={g.groupId}>
                <div className="item-head">
                  <h2 style={{ margin: 0 }}>
                    {g.title}
                  </h2>
                  <span className="pill">모임장</span>
                </div>

                <p className="muted">
                  {[g.regionSido, g.regionSigungu]
                    .filter(Boolean)
                    .join(' ')}
                  {' · '}{groups.purposeLabel(g.purpose)}
                  {' · '}월 {wonFromKrw(g.amount)}
                  {' · '}매월 {g.payDay}일
                  {' · '}{g.months}개월
                  {' · '}최대 {g.maxMembers}명
                  {' · '}
                  <span className="pill">
                    {groups.groupStatusLabel(g.status)}
                  </span>

                  {g.visibility === 'PRIVATE' && (
                    <>
                      {' '}
                      <span className="pill">
                        🔒 비공개
                      </span>
                    </>
                  )}
                </p>

                <div className="actions">
                  <button
                    className="btn primary"
                    onClick={() =>
                      nav(`/operations?groupId=${g.groupId}`)
                    }
                  >
                    운영 홈
                  </button>

                  <button
                    className="btn soft"
                    onClick={() =>
                      nav(`/evidence?groupId=${g.groupId}`)
                    }
                  >
                    납입 증빙
                  </button>

                  {/* F18: 비공개 모임장 전용 */}
                  {g.visibility === 'PRIVATE' && (
                    <button
                      type="button"
                      className="btn soft"
                      disabled={issuingGroupId !== null}
                      onClick={() =>
                        handleIssueJoinCode(g.groupId)
                      }
                    >
                      {issuingGroupId === g.groupId
                        ? '발급 중...'
                        : '🔑 인증코드 발급·재발급'}
                    </button>
                  )}
                </div>

                {/* F18: 발급된 인증코드 표시 */}
                {g.visibility === 'PRIVATE' &&
                  issuedCodes[g.groupId] && (
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
                        {issuedCodes[g.groupId]}
                      </p>

                      <p className="muted">
                        참여할 회원에게 인증코드를
                        안전하게 전달해 주세요.
                        이전 인증코드는 더 이상
                        사용할 수 없습니다.
                      </p>

                      <button
                        type="button"
                        className="btn small soft"
                        onClick={() =>
                          handleHideJoinCode(g.groupId)
                        }
                      >
                        코드 숨기기
                      </button>
                    </div>
                  )}

                {/* F18: 발급 오류 */}
                {issueErrors[g.groupId] && (
                  <p
                    role="alert"
                    style={{
                      color: '#c0392b',
                      marginTop: 12
                    }}
                  >
                    {issueErrors[g.groupId]}
                  </p>
                )}
              </Card>
            ))
          ) : (
            <div className="empty">
              아직 만든 계모임이 없어요.{' '}
              <button
                type="button"
                className="btn small soft"
                onClick={() => nav('/groups/new')}
              >
                계모임 만들기
              </button>
            </div>
          )}
        </LoadState>
      </div>
    </>
  );
}
