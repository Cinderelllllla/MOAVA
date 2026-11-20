
/* S08 내 계모임 — /my-groups
   서버(GET /api/groups/my)의 "내가 만든 계모임" 목록. 실패하면 목업 대신 오류 안내 + 다시 시도.
*/

import { useNavigate } from 'react-router-dom';
import useMyGroups from '../common/useMyGroups';
import { groups, finance } from '../api';
import { PageHead, Card } from '../components/ui';
import { LoadState } from '../components/shared';
import { wonFromKrw } from '../common/format';
import { INCOME_CAP_RATIO } from '../common/policy';

export default function MyGroups() {
  const nav = useNavigate();
  const myGroups = useMyGroups();
  const list = myGroups.items;

  // 부담률(참고): 사용자가 직접 입력한 월수입이 있을 때만 계산한다 (만 원 단위, 서버 금액은 원 단위)
  const income = Number(finance.getFinance().income) || 0;
  const monthlyTotal = list.reduce((sum, g) => sum + Number(g.amount || 0) / 10000, 0);
  const burden = income > 0 ? Math.round(monthlyTotal / income * 100) : null;

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
            {myGroups.status === 'loading' ? '조회 중' : myGroups.status === 'error' ? '-' : `${list.length}개`}
          </div>
        </Card>

        <Card>
          <small>월 납입 합계</small>
          <div className="metric">
            {myGroups.status === 'ready' ? wonFromKrw(monthlyTotal * 10000) : '-'}
          </div>
        </Card>

        <Card>
          <small>개인 납입 부담률 (참고)</small>
          <div className="metric">
            {myGroups.status !== 'ready' || burden == null ? '-' : `${burden}%`}
            {myGroups.status === 'ready' && burden != null && burden > INCOME_CAP_RATIO * 100 && (
              <span className="pill warn">{INCOME_CAP_RATIO * 100}% 초과</span>
            )}
          </div>
          {burden == null && <small>맞춤 추천에서 월수입을 입력하면 계산돼요.</small>}
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
                  <h2 style={{ margin: 0 }}>{g.title}</h2>
                  <span className="pill">모임장</span>
                </div>

                <p className="muted">
                  {[g.regionSido, g.regionSigungu].filter(Boolean).join(' ')}
                  {' · '}{groups.purposeLabel(g.purpose)}
                  {' · '}월 {wonFromKrw(g.amount)}
                  {' · '}매월 {g.payDay}일
                  {' · '}{g.months}개월
                  {' · '}최대 {g.maxMembers}명
                  {' · '}
                  <span className="pill">{groups.groupStatusLabel(g.status)}</span>
                  {g.visibility === 'PRIVATE' && (
                    <>
                      {' '}
                      <span className="pill">🔒 비공개</span>
                    </>
                  )}
                </p>

                <div className="actions">
                  <button className="btn primary" onClick={() => nav(`/operations?groupId=${g.groupId}`)}>운영 홈</button>
                  <button className="btn soft" onClick={() => nav(`/evidence?groupId=${g.groupId}`)}>납입 증빙</button>
                </div>
              </Card>
            ))
          ) : (
            <div className="empty">
              아직 만든 계모임이 없어요.{' '}
              <button type="button" className="btn small soft" onClick={() => nav('/groups/new')}>계모임 만들기</button>
            </div>
          )}
        </LoadState>
      </div>
    </>
  );
}
