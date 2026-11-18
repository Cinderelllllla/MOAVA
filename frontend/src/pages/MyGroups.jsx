
/* S08 내 계모임 — /my-groups
   기존 Mock 화면 유지 + Oracle 모임장 목록 조회
*/

import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { groups, finance, leader } from '../api';
import { getAccessToken } from '../api/auth';
import { PageHead, Card } from '../components/ui';
import { won, wonFromKrw } from '../common/format';

export default function MyGroups() {
  const { setUi } = useApp();
  const nav = useNavigate();

  const [health, setHealth] = useState(null);
  const [serverGroups, setServerGroups] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const token = getAccessToken();

  // 로그인한 서버 회원의 Oracle 계모임 조회
  useEffect(() => {
    if (!token) {
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
      .catch(e => {
        if (!cancelled) {
          setError(
            e.message || '내 계모임을 불러오지 못했습니다.'
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
  }, [token]);

  // 기존 Mock 데이터
  const mockMine = groups.myGroups();

  // 서버 로그인 시 Oracle, Mock 로그인 시 기존 데이터 사용
  const mine = token ? [] : mockMine;
  const ended = token ? [] : groups.endedGroups();

  const income = finance.getFinance().income;

  const avg = mine.length
    ? Math.round(
        mine.reduce(
          (a, g) => a + groups.healthScore(g),
          0
        ) / mine.length
      )
    : 0;

  // 서버 데이터 amount는 원 단위
  const allAmounts = token
    ? serverGroups.map(g => Number(g.amount || 0) / 10000)
    : mine.map(g => Number(g.amount || 0));

  const burden = income
    ? Math.round(
        allAmounts.reduce((a, v) => a + v, 0) /
        income * 100
      )
    : 0;

  const count = token
    ? serverGroups.length
    : mine.length;

  return (
    <>
      <PageHead
        kicker="MY GROUPS"
        title="내 계모임"
        sub={
          token
            ? '내가 모임장으로 생성한 계모임 목록입니다.'
            : '가입한 계모임 목록입니다. 모임별 상세와 운영은 운영 홈에서 진행합니다.'
        }
      >
        <button
          className="btn primary"
          onClick={() => nav('/groups/new')}
        >
          새 계모임 만들기
        </button>

        <button
          className="btn soft"
          onClick={() => nav('/groups')}
        >
          모임 찾기 →
        </button>
      </PageHead>

      {/* 상단 통계 */}
      <div className="grid cols3">
        <Card>
          <small>
            {token
              ? '내가 만든 계모임'
              : '참여 중인 계모임'}
          </small>

          <div className="metric">
            {loading
              ? '조회 중'
              : error
                ? '—'
                : `${count}개`}
          </div>
        </Card>

        <Card>
          <small>참여 모임 평균 건강도</small>

          <div className="metric">
            {token
              ? '—'
              : mine.length
                ? `${avg}점`
                : '—'}
          </div>
        </Card>

        <Card>
          <small>개인 납입 부담률 (참고)</small>

          <div className="metric">
            {error ? '—' : `${burden}%`}

            {!error && burden > 12 && (
              <span className="pill warn">
                12% 초과
              </span>
            )}
          </div>
        </Card>
      </div>

      {/* 서버 로그인: Oracle 계모임 */}
      {token ? (
        <div className="section list">
          {loading && (
            <div className="empty">
              Oracle DB에서 내 계모임을 불러오는 중입니다.
            </div>
          )}

          {!loading && error && (
            <div className="empty" role="alert">
              {error}

              {' '}

              <button
                className="btn soft"
                onClick={() => window.location.reload()}
              >
                다시 시도
              </button>
            </div>
          )}

          {!loading && !error && (
            serverGroups.length ? (
              serverGroups.map(g => (
                <Card key={g.groupId}>
                  <div className="item-head">
                    <h2 style={{ margin: 0 }}>
                      {g.title}
                    </h2>

                    <span className="pill">
                      Leader
                    </span>
                  </div>

                  <p className="muted">
                    {[
                      g.regionSido,
                      g.regionSigungu
                    ].filter(Boolean).join(' ')}

                    {' · '}월 {wonFromKrw(g.amount)}

                    {' · '}매월 {g.payDay}일

                    {' · '}{g.months}개월

                    {' · '}최대 {g.maxMembers}명

                    {' · '}

                    <span className="pill">
                      {g.status}
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

                  <p className="muted mini">
                    계모임 번호: {g.groupId}
                    {' · '}
                    모임장으로 생성한 계모임입니다.
                  </p>

                  <p className="muted mini">
                    운영 홈·채팅·건강도는 아직
                    서버 계모임과 연결되지 않았습니다.
                  </p>
                </Card>
              ))
            ) : (
              <div className="empty">
                모임장으로 생성한 계모임이 없습니다.
              </div>
            )
          )}
        </div>
      ) : (
        /* 기존 Mock 계모임 화면 */
        <div className="section list">
          {mine.length ? (
            mine.map(g => (
              <Card key={g.id}>
                <div className="item-head">
                  <h2 style={{ margin: 0 }}>
                    {g.title}
                  </h2>

                  <span className="pill">
                    {leader(g) ? 'Leader' : 'Member'}
                  </span>
                </div>

                <p className="muted">
                  {g.region}

                  {' · '}월 {won(g.amount)}

                  {' · '}매월 {g.payDay}일

                  {' · '}{g.months}개월

                  {' · '}{g.members.length}/{g.max}명

                  {' · '}

                  <span className="pill">
                    {g.status}
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
                    onClick={() => {
                      setUi({ selected: g.id });
                      nav('/operations');
                    }}
                  >
                    운영 홈 · 상세
                  </button>

                  <button
                    className="btn soft"
                    onClick={() => {
                      setHealth(
                        health === g.id ? null : g.id
                      );
                    }}
                  >
                    모임 건강도
                  </button>

                  <button
                    className="btn soft"
                    onClick={() => {
                      setUi({ selected: g.id });
                      nav('/chat');
                    }}
                  >
                    채팅
                  </button>
                </div>

                {health === g.id && (
                  <div className="detail-panel">
                    <h3>
                      이 모임의 건강도 ·
                      {' '}
                      {groups.healthScore(g)}점
                    </h3>

                    <div className="mini-progress">
                      <span
                        style={{
                          width: `${groups.healthScore(g)}%`
                        }}
                      />
                    </div>

                    <p className="muted mini">
                      95점 기준 − 경고 ×9 −
                      증빙 보완 요청 ×6 −
                      미납 감지 ×5 −
                      해산 검토 12.
                      데모 지표입니다.
                    </p>

                    <p>
                      나의 월 납입 부담:
                      월수입 대비
                      {' '}
                      {income
                        ? Math.round(g.amount / income * 100)
                        : 0}%
                    </p>
                  </div>
                )}
              </Card>
            ))
          ) : (
            <div className="empty">
              아직 가입한 모임이 없어요.
              지역 모임을 찾아보세요.
            </div>
          )}
        </div>
      )}

      {/* 종료된 Mock 계모임 */}
      {ended.length > 0 && (
        <details className="section policy-fold">
          <summary>
            종료된 모임 {ended.length}개 보기
          </summary>

          <div
            className="list"
            style={{ marginTop: 12 }}
          >
            {ended.map(g => (
              <div className="list-item" key={g.id}>
                <div className="item-head">
                  <b>{g.title}</b>

                  <span className="pill">
                    해산 완료
                  </span>
                </div>

                <p className="muted mini">
                  {g.dissolution?.reason}
                  {' · '}
                  {g.dissolution?.requestedAt}
                </p>
              </div>
            ))}
          </div>
        </details>
      )}
    </>
  );
}
