
/* S02 마이 홈 — /dashboard · F08 F17 F35
   기존 대시보드 + Oracle 계모임 조회
*/

import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { finance, groups, admin, leader } from '../api';
import { getAccessToken } from '../api/auth';
import { PageHead, Card } from '../components/ui';
import { BarChart, RiskLabel } from '../components/shared';
import { won } from '../common/format';

export default function Dashboard() {
  const { user, setUi } = useApp();
  const nav = useNavigate();

  // 기존 금융·알림 기능 유지
  const a = finance.affordability();
  const pending = admin.myNotices().filter(n => !n.read).length;

  // 서버 로그인 여부
  const token = getAccessToken();
  const isServerUser = Boolean(token);

  // 기존 Mock 계모임
  const mockMine = groups.myGroups();

  // Oracle 계모임 조회 상태
  const [serverGroups, setServerGroups] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isServerUser) {
      setServerGroups([]);
      setLoading(false);
      setError('');
      return;
    }

    let cancelled = false;

    setLoading(true);
    setError('');

    groups.fetchMyGroups()
      .then(data => {
        if (!cancelled) {
          setServerGroups(data);
        }
      })
      .catch(err => {
        if (!cancelled) {
          setError(
            err.message || '계모임 조회에 실패했습니다.'
          );
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [isServerUser, token]);

  // 서버 로그인 시 Oracle, Mock 로그인 시 기존 데이터
  const mine = isServerUser ? serverGroups : mockMine;

  const shown = mine.slice(0, 3);

  // 계모임 개수 표시
  const groupCount = loading
    ? '조회 중'
    : error
      ? '조회 실패'
      : `${mine.length}개`;

  // 기존 통계 카드
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
      <div className="symbol">{ico}</div>
      <small>{label}</small>
      <div className="value">{val}</div>
      <small>자세히 보기 →</small>
    </button>
  );

  return (
    <>
      <PageHead
        kicker="MY FINANCIAL SKY"
        title={`안녕하세요, ${user?.name || '회원'}님 ☁`}
        sub="오늘도 내 속도에 맞춰, 부담 없이 함께 모아봐요."
      />

      {/* 맞춤 추천 영역 */}
      <div className="hero">
        <span className="pill">
          ✦ MOAVA SMART RECOMMENDATION
        </span>

        <h2>
          나에게 맞는 월 납입액
          <br />
          {won(a.rec)}부터 시작해 보세요.
        </h2>

        <p
          style={{
            opacity: 0.9,
            margin: '0 0 14px',
            fontSize: 13
          }}
        >
          <RiskLabel />
        </p>

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
          val={won(a.rec)}
          ico="✧"
          to="/journey"
        />

        <Stat
          label="월 가용 금액"
          val={won(a.free)}
          ico="◉"
          to="/finance"
        />

        <Stat
          label={
            isServerUser
              ? '내가 만든 계모임'
              : '참여 계모임'
          }
          val={groupCount}
          ico="☁"
          to="/my-groups"
        />

        <Stat
          label="읽지 않은 알림"
          val={`${pending}건`}
          ico="♧"
          to="/notifications"
        />
      </div>

      {/* 하단 그래프 + 내 계모임 */}
      <div className="grid cols2 section">
        {/* 기존 금융 그래프 */}
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

          <BarChart
            data={finance.getMonthly()}
            limit={6}
          />
        </Card>

        {/* 내 계모임 목록 */}
        <Card>
          <div className="section-title">
            <h2 style={{ margin: 0 }}>
              {isServerUser
                ? '내가 만든 계모임'
                : '나의 계모임'}

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
            {loading && (
              <div className="empty">
                내 계모임을 불러오는 중입니다.
              </div>
            )}

            {!loading && error && (
              <div className="empty" role="alert">
                {error}
              </div>
            )}

            {!loading && !error && (
              shown.length ? (
                shown.map(g => {
                  // 서버 계모임과 Mock 계모임의
                  // 필드 구조가 다르므로 구분
                  const id = isServerUser
                    ? g.groupId
                    : g.id;

                  const region = isServerUser
                    ? [
                        g.regionSido,
                        g.regionSigungu
                      ].filter(Boolean).join(' ')
                    : g.region;

                  const maxMembers = isServerUser
                    ? g.maxMembers
                    : g.max;

                  return (
                    <button
                      type="button"
                      key={id}
                      className="list-item group-choice"
                      style={{
                        width: '100%',
                        textAlign: 'left',
                        font: 'inherit',
                        cursor: 'pointer'
                      }}
                      onClick={() => {
                        if (isServerUser) {
                          // 서버 계모임의 운영 홈은
                          // 아직 백엔드 연동 전
                          nav('/my-groups');
                          return;
                        }

                        setUi({ selected: id });
                        nav('/operations');
                      }}
                    >
                      <div
                        className="item-head"
                        style={{ margin: 0 }}
                      >
                        <b>{g.title}</b>

                        <span className="pill">
                          {isServerUser
                            ? 'Leader'
                            : leader(g)
                              ? 'Leader'
                              : 'Member'}
                        </span>
                      </div>

                      <p
                        className="muted mini"
                        style={{ margin: '6px 0 0' }}
                      >
                        월 {won(g.amount)}

                        {' · '}매월 {g.payDay}일

                        {' · '}{region}

                        {isServerUser
                          ? ` · 최대 ${maxMembers}명`
                          : ` · ${g.members.length}/${maxMembers}명`}

                        {' · '}

                        {isServerUser
                          ? '내 계모임 보기 →'
                          : '운영 홈 →'}
                      </p>
                    </button>
                  );
                })
              ) : (
                <div className="empty">
                  {isServerUser
                    ? '모임장으로 생성한 계모임이 없어요.'
                    : '참여 중인 모임이 없어요.'}
                </div>
              )
            )}
          </div>

          {mine.length > 3 && !loading && !error && (
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
