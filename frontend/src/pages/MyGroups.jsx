/* S08 내 계모임 — /my-groups
   생성한 계모임 및 참여 중인 계모임 조회
   간략 모임 정보 조회 및 운영 홈 이동
*/

import { useState, useEffect } from 'react';

import {
  useNavigate,
  useSearchParams
} from 'react-router-dom';

import useMyGroups from '../common/useMyGroups';
import { groups, finance } from '../api';
import { PageHead, Card } from '../components/ui';
import { LoadState } from '../components/shared';
import { wonFromKrw } from '../common/format';
import { formatRegionFromServer } from '../common/regions';

export default function MyGroups() {
  const nav = useNavigate();
  const [params] = useSearchParams();

  const myGroups = useMyGroups();
  const list = myGroups.items;

  const [openInfoGroupId, setOpenInfoGroupId] = useState(null);

  const requestedGroupId = params.get('groupId');
  const requestedTab = params.get('tab');

  // 기존 가입 신청 알림 링크 호환 처리
  useEffect(() => {
    if (
      requestedTab === 'joinRequests' &&
      requestedGroupId &&
      Number.isSafeInteger(Number(requestedGroupId)) &&
      Number(requestedGroupId) > 0
    ) {
      nav(
        `/operations?groupId=${requestedGroupId}&tab=joinRequests`,
        { replace: true }
      );
    }
  }, [requestedGroupId, requestedTab, nav]);

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

  // 간략 모임 정보 열기 및 닫기
  const handleToggleInfo = groupId => {
    setOpenInfoGroupId(prev =>
      prev === groupId ? null : groupId
    );
  };

  return (
    <>
      <PageHead
        kicker="MY GROUPS"
        title="내 계모임"
        sub="내가 만든 계모임과 참여 중인 계모임을 확인할 수 있어요."
      >
        <button
          type="button"
          className="btn primary"
          onClick={() => nav('/groups/new')}
        >
          새 계모임 만들기
        </button>
      </PageHead>

      {/* 상단 통계 */}
      <div className="grid cols3">

        <Card>
          <small>내 계모임</small>

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

      {/* 내 계모임 목록 */}
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

                {/* 모임 제목 + 상태 뱃지(역할 · 모집 상태 · 비공개)를 한 줄에 모음 */}
                <div className="item-head mg-head">
                  <h2 style={{ margin: 0 }}>
                    {g.title}
                  </h2>

                  <span className="mg-badges">
                    <span className={`pill ${g.role === 'OWNER' ? 'mine' : ''}`}>
                      {g.role === 'OWNER'
                        ? '모임장'
                        : '일반 멤버'}
                    </span>
                    <span className="pill">
                      {groups.groupStatusLabel(
                        g.status,
                        g.currentMembers,
                        g.maxMembers
                      )}
                    </span>
                    {g.visibility === 'PRIVATE' && <span className="pill">비공개</span>}
                  </span>
                </div>

                {/* 모임 기본 정보 */}
                <p className="muted mg-meta">
                  {formatRegionFromServer(g)}

                  {' · '}
                  {groups.purposeLabel(g.purpose)}

                  {' · '}월 {wonFromKrw(g.amount)}
                  {' · '}매월 {g.payDay}일
                  {' · '}{g.months}개월
                  {' · '}{g.currentMembers ?? '-'} / {g.maxMembers}명
                </p>

                {/* 모임 정보 조회 및 운영 홈 이동 */}
                <div className="actions">

                  <button
                    type="button"
                    className="btn soft"
                    aria-expanded={
                      openInfoGroupId === g.groupId
                    }
                    onClick={() =>
                      handleToggleInfo(g.groupId)
                    }
                  >
                    {openInfoGroupId === g.groupId
                      ? '모임 정보 닫기'
                      : '모임 정보 조회'}
                  </button>

                  <button
                    type="button"
                    className="btn primary"
                    onClick={() =>
                      nav(`/operations?groupId=${g.groupId}`)
                    }
                  >
                    운영 홈 이동 →
                  </button>

                </div>

                {/* 간략 모임 정보 */}
                {openInfoGroupId === g.groupId && (
                  <div
                    className="policy-panel"
                    style={{ marginTop: 6 }}
                  >
                    <h3>모임 정보</h3>

                    <div className="group-info-rows"
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
                      columnGap: 24,
                      rowGap: 0,
                      fontSize: 13
                    }}>

                      <div>
                        <span>모임명</span>
                        <strong>{g.title}</strong>
                      </div>

                      <div>
                        <span>내 역할</span>
                        <strong>
                          {g.role === 'OWNER'
                            ? '모임장'
                            : '일반 멤버'}
                        </strong>
                      </div>

                      <div>
                        <span>모임 목적</span>
                        <strong>
                          {groups.purposeLabel(g.purpose)}
                        </strong>
                      </div>

                      <div>
                        <span>지역</span>
                        <strong>
                          {formatRegionFromServer(g, '미등록')}
                        </strong>
                      </div>

                      <div>
                        <span>월 납입액</span>
                        <strong>
                          {wonFromKrw(g.amount)}
                        </strong>
                      </div>

                      <div>
                        <span>납입일</span>
                        <strong>
                          매월 {g.payDay}일
                        </strong>
                      </div>

                      <div>
                        <span>참여 인원</span>
                        <strong>
                          {g.currentMembers ?? '-'} / {g.maxMembers}명
                        </strong>
                      </div>

                      <div>
                        <span>운영 기간</span>
                        <strong>{g.months}개월</strong>
                      </div>

                      <div>
                        <span>공개 여부</span>
                        <strong>
                          {g.visibility === 'PRIVATE'
                            ? '비공개'
                            : '공개'}
                        </strong>
                      </div>

                      <div>
                        <span>모집 상태</span>
                        <strong>
                          {groups.groupStatusLabel(
                            g.status,
                            g.currentMembers,
                            g.maxMembers
                          )}
                        </strong>
                      </div>

                    </div>

                    <div style={{ marginTop: 16 }}>
                      <h3>모임 소개</h3>

                      <p className="muted">
                        {g.description ||
                          '모임 소개가 등록되지 않았습니다.'}
                      </p>
                    </div>
                  </div>
                )}

              </Card>
            ))
          ) : (
            <div className="empty">
              아직 만들거나 참여한 계모임이 없어요.{' '}

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