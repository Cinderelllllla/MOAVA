
/* S02 마이 홈 — /dashboard · F08 F17 F35
   로그인 회원의 서버 데이터(내 계모임 · 안 읽은 알림 · 월별 기록)를 보여 준다.
*/

import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import useMlFinanceAssessment from '../common/useMlFinanceAssessment';
import useMyGroups from '../common/useMyGroups';
import { finance, groups, fetchUnreadNotificationCount } from '../api';
import { NOTIFICATIONS_CHANGED } from '../common/notificationEvents';
import { PageHead, Card } from '../components/ui';
import { FinanceAssessmentStatus, LoadState, MonthlyChart, RiskLabel } from '../components/shared';
import { won, wonFromKrw } from '../common/format';
import Icon from '../components/Icon';

/** 서버의 안 읽은 알림 개수. 실패하면 null(화면에는 '-' 표시) */
function useUnreadCount() {
  const [count, setCount] = useState(null);
  useEffect(() => {
    let alive = true;
    const load = () => fetchUnreadNotificationCount()
      .then(c => { if (alive) setCount(c); })
      .catch(() => { if (alive) setCount(null); });
    load();
    window.addEventListener(NOTIFICATIONS_CHANGED, load);
    return () => { alive = false; window.removeEventListener(NOTIFICATIONS_CHANGED, load); };
  }, []);
  return count;
}

export default function Dashboard() {
  const { user } = useApp();
  const mlAssessment = useMlFinanceAssessment();
  const nav = useNavigate();

  const a = finance.affordability();
  const unread = useUnreadCount();
  const myGroups = useMyGroups();
  const mine = myGroups.items;
  const shown = mine.slice(0, 3);

  // 계모임 개수 표시
  const groupCount = myGroups.status === 'loading'
    ? '조회 중'
    : myGroups.status === 'error'
      ? '-'
      : `${mine.length}개`;

  // 통계 카드
  const Stat = ({ label, val, ico, to }) => (
    <button
      type="button"
      className="card stat"
      style={{
        textAlign: 'left',
        cursor: 'pointer',
        font: 'inherit'
      }}
      onClick={() => nav(to)}
    >
      <div className="symbol"><Icon g={ico} size={22} /></div>
      <small>{label}</small>
      <div className="value">{val}</div>
      <small>자세히 보기 →</small>
    </button>
  );

  return (
    <>
      <PageHead
        kicker="MY OVERVIEW"
        title={`안녕하세요, ${user?.name || '회원'}님`}
        sub="오늘도 내 속도에 맞춰, 부담 없이 함께 모아봐요."
      />

      {/* 맞춤 추천 영역 */}
      <div className="hero">
        <span className="pill">
          AI 맞춤 추천
        </span>

        {a && a.rec > 0 ? (
          <h2>
            나에게 맞는 월 납입액{' '}
            <br />
            {won(a.rec)}부터 시작해 보세요.
          </h2>
        ) : a ? (
          <h2>
            지금은 납입 여유가 부족해요.
            <br />
            금융정보를 조정해 다시 분석해 보세요.
          </h2>
        ) : (
          <h2>
            금융정보를 입력하면{' '}
            <br />
            나에게 맞는 월 납입액을 추천해 드려요.
          </h2>
        )}

        <p
          style={{
            opacity: 0.9,
            margin: '0 0 14px',
            fontSize: 13
          }}
        >
          <RiskLabel />
        </p>
        <FinanceAssessmentStatus {...mlAssessment} />

        <button
          className="btn"
          onClick={() => nav('/journey')}
        >
          맞춤 추천 시작 →
        </button>
      </div>

      {/* 통계 카드 4개 */}
      <div className="grid cols4 section">
        <Stat
          label="추천 월 납입액"
          val={a ? (a.rec > 0 ? won(a.rec) : '추천 없음') : '-'}
          ico="₩"
          to="/journey"
        />

        <Stat
          label="매달 남는 돈"
          val={a ? finance.freeText(a.free) : '-'}
          ico="◉"
          to="/finance"
        />

        <Stat
          label="내가 만든 계모임"
          val={groupCount}
          ico="☁"
          to="/my-groups"
        />

        <Stat
          label="읽지 않은 알림"
          val={unread == null ? '-' : `${unread}건`}
          ico="♧"
          to="/notifications"
        />
      </div>

      {/* 하단 그래프 + 내 계모임 */}
      <div className="grid cols2 section">
        <Card>
          <div className="section-title">
            <h2 style={{ margin: 0 }}>
              월별 수입 · 지출 흐름
            </h2>

            <button
              className="btn small soft"
              onClick={() => nav('/finance/monthly')}
            >
              더보기 →
            </button>
          </div>

          <MonthlyChart limit={6} />
        </Card>

        {/* 내 계모임 목록 */}
        <Card>
          <div className="section-title">
            <h2 style={{ margin: 0 }}>
              내가 만든 계모임
              {' '}
              <small className="muted">
                {groupCount}
              </small>
            </h2>

            <button
              className="btn small soft"
              onClick={() => nav('/my-groups')}
            >
              더보기 →
            </button>
          </div>

          <div className="list">
            <LoadState
              status={myGroups.status}
              error={myGroups.error}
              onRetry={myGroups.reload}
              loadingText="내 계모임을 불러오는 중이에요."
            >
              {shown.length ? (
                shown.map(g => (
                  <button
                    type="button"
                    key={g.groupId}
                    className="list-item group-choice"
                    style={{
                      width: '100%',
                      textAlign: 'left',
                      font: 'inherit',
                      cursor: 'pointer'
                    }}
                    onClick={() => nav('/my-groups')}
                  >
                    <div
                      className="item-head"
                      style={{ margin: 0 }}
                    >
                      <b>{g.title}</b>
                      <span className="pill">모임장 · {groups.groupStatusLabel(g.status)}</span>
                    </div>

                    <p
                      className="muted mini"
                      style={{ margin: '6px 0 0' }}
                    >
                      월 {wonFromKrw(g.amount)}
                      {' · '}매월 {g.payDay}일
                      {' · '}{[g.regionSido, g.regionSigungu].filter(Boolean).join(' ')}
                      {` · 최대 ${g.maxMembers}명`}
                      {' · '}내 계모임 보기 →
                    </p>
                  </button>
                ))
              ) : (
                <div className="empty">
                  아직 만든 계모임이 없어요.{' '}
                  <button type="button" className="btn small soft" onClick={() => nav('/groups/new')}>계모임 만들기</button>
                </div>
              )}
            </LoadState>
          </div>

          {mine.length > 3 && myGroups.status === 'ready' && (
            <p className="muted mini mt-3 mb-0">
              외 {mine.length - 3}개 모임은
              더보기에서 확인하세요.
            </p>
          )}
        </Card>
      </div>
    </>
  );
}
