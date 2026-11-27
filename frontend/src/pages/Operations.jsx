/* S10 운영 홈 — /operations · F16 F18 F19 F20 F28 F33 */

import PaymentStatusPanel from '../components/PaymentStatusPanel';
import GroupManagementPanel from '../components/GroupManagementPanel';
import GroupAccountPanel from '../components/GroupAccountPanel';
import GroupAccountChangePanel from '../components/GroupAccountChangePanel';
import GroupAccountConsentPanel from '../components/GroupAccountConsentPanel';

import { useNavigate, useSearchParams } from 'react-router-dom';
import useMyGroups from '../common/useMyGroups';
import { useApp } from '../common/store';

import {
  groups,
  proofs,
  leader,
  me,
  memberName,
  paidAny,
  paidThisPeriod,
  currentPeriod,
  ensureJoinCode,
  db
} from '../api';

import { PageHead, Card, Empty } from '../components/ui';
import { useOpGroup, GroupSelect, LoadState } from '../components/shared';
import { formatRegionFromServer } from '../common/regions';
import Icon from '../components/Icon';

import {
  won,
  wonFromKrw,
  maskName,
  maskAccount,
  VERIFY_LABEL
} from '../common/format';

// 화면은 서버 계모임(ServerOperations)만 보여 준다.
// 아래 MockOperations 와 패널들은 서버 API 연결 시 참고하도록 유지한다.
export default function Operations() {
  return <ServerOperations />;
}

function ServerOperations() {
  const nav = useNavigate();
  const [params] = useSearchParams();

  const myGroups = useMyGroups();
  const items = myGroups.items || [];

  const requestedGroupId = params.get('groupId');

  // URL에 지정된 모임을 선택하고,
  // 해당 모임이 없으면 첫 번째 모임을 선택한다.
  const g =
    items.find(
      item => String(item.groupId) === requestedGroupId
    ) || items[0] || null;

  const selectedId = g ? String(g.groupId) : '';

  const roleLabel = role =>
    role === 'OWNER' ? '모임장' : '일반 멤버';

  // 모임 선택 시 URL 변경
  // 기존 가입 신청 알림 탭은 제거한다.
  const handleSelectGroup = event => {
    const nextId = event.target.value;

    nav(
      `/operations?groupId=${encodeURIComponent(nextId)}`,
      { replace: true }
    );
  };

  // 모임 상태 표시 항목
  const statusRows = g ? [
    ['내 역할', roleLabel(g.role)],
    ['모집 상태', groups.groupStatusLabel(g.status, g.currentMembers, g.maxMembers)],
    [
      '공개 여부',
      g.visibility === 'PRIVATE'
        ? '비공개'
        : '공개'
    ],
    [
      '지역',
      formatRegionFromServer(g, '미등록')
    ],
    ['월 납입액', wonFromKrw(g.amount)],
    ['납입일', `매월 ${g.payDay}일`],
    ['참여 인원', `${g.currentMembers ?? '-'} / ${g.maxMembers}명`],
    ['운영 기간', `${g.months}개월`]
  ] : [];

  return (
    <>
      <PageHead
        kicker="OPERATIONS HOME"
        title={g ? `운영 홈 · ${g.title}` : '운영 홈'}
        sub="내가 만든 계모임과 참여 중인 계모임의 운영 정보를 확인합니다."
      >
        <button
          type="button"
          className="btn soft"
          onClick={() => nav('/my-groups')}
        >
          내 계모임 →
        </button>
      </PageHead>

      {myGroups.status !== 'ready' ? (
        <Card>
          <LoadState
            status={myGroups.status}
            error={myGroups.error}
            onRetry={myGroups.reload}
            loadingText="계모임을 불러오는 중이에요."
          />
        </Card>

      ) : !g ? (
        <Card>
          <Empty>
            아직 만들거나 참여한 계모임이 없어요.
          </Empty>

          <button
            type="button"
            className="btn primary"
            onClick={() => nav('/groups/new')}
          >
            새 계모임 만들기
          </button>
        </Card>

      ) : (
        <>
          {/* 계모임 선택 드롭다운 */}
          <Card>
            <div
              className="field"
              style={{
                maxWidth: 420,
                marginBottom: 0
              }}
            >
              <label htmlFor="server-ops-group">
                계모임 선택
              </label>

              <select
                id="server-ops-group"
                value={selectedId}
                onChange={handleSelectGroup}
              >
                {items.map(item => (
                  <option
                    key={item.groupId}
                    value={String(item.groupId)}
                  >
                    {item.title} · {roleLabel(item.role)}
                  </option>
                ))}
              </select>
            </div>
          </Card>

          {/*
            선택한 모임 전용 영역

            모임 ID가 변경되면 기존 영역을 제거하고
            새 모임의 상태, 관리, 정보 영역을 렌더링한다.
          */}
          <div key={`operations-${selectedId}`}>

            {/* ========================================= */}
            {/* 1. 모임 상태 */}
            {/* ========================================= */}

            <div className="section">
              <Card>
                <div className="item-head">
                  <h2>모임 상태</h2>

                  <span className="pill">
                    {groups.groupStatusLabel(g.status, g.currentMembers, g.maxMembers)}
                  </span>
                </div>

                <div
                  className="group-info-rows"
                  style={{
                    display: 'grid',
                    gridTemplateColumns:
                      'repeat(auto-fit, minmax(240px, 1fr))',
                    columnGap: 24,
                    rowGap: 0,
                    fontSize: 13
                  }}
                >
                  {statusRows.map(([label, value]) => (
                    <div key={label}>
                      <span>{label}</span>
                      <strong>{value}</strong>
                    </div>
                  ))}
                </div>
              </Card>
            </div>

            {/* ========================================= */}
            {/* 2. 모임 관리 */}
            {/* ========================================= */}

            <div className="section">
              <Card>
                <div className="item-head">
                  <h2>모임 관리</h2>

                  <span className="pill">
                    {roleLabel(g.role)}
                  </span>
                </div>

                {/* 공통 관리 기능 */}
                <div className="actions">
                  <button
                    type="button"
                    className="btn primary"
                    onClick={() =>
                      nav(`/evidence?groupId=${g.groupId}`)
                    }
                  >
                    납입 증빙 확인 →
                  </button>
                </div>

                {/* F16, F18 모임장 전용 관리 기능 */}
                {g.role === 'OWNER' && (
                  <GroupManagementPanel
                    key={`management-${g.groupId}`}
                    g={g}
                    onReload={myGroups.reload}
                  />
                )}

                {/* F19 모임장 전용 계좌 관리 */}
                {g.role === 'OWNER' && (
                  <GroupAccountPanel
                    key={`account-${g.groupId}`}
                    g={g}
                  />
                )}

                {/* F20 모임장 전용 계좌 변경 상태 확인 */}
                {g.role === 'OWNER' && (
                  <GroupAccountChangePanel
                    key={`account-change-${g.groupId}`}
                    g={g}
                  />
                )}

                {/* F20 계좌 변경 동의 — 일반 회원 전용 화면 */}
                {g.role === 'MEMBER' && (
                  <GroupAccountConsentPanel
                    key={`account-consent-${g.groupId}`}
                    g={g}
                  />
                )}

                {/* F25 납입 현황 */}
                <PaymentStatusPanel
                  key={`payment-${g.groupId}`}
                  groupId={g.groupId}
                />

              </Card>
            </div>

            {/* ========================================= */}
            {/* 3. 모임 정보 */}
            {/* ========================================= */}

            <div className="section">
              <Card>
                <h2>모임 정보</h2>

                <div
                  className="group-info-rows"
                  style={{
                    display: 'grid',
                    gridTemplateColumns:
                      'repeat(auto-fit, minmax(240px, 1fr))',
                    columnGap: 24,
                    rowGap: 0,
                    fontSize: 13
                  }}
                >
                  <div>
                    <span>모임명</span>
                    <strong>{g.title}</strong>
                  </div>

                  <div>
                    <span>모임 목적</span>
                    <strong>
                      {groups.purposeLabel(g.purpose)}
                    </strong>
                  </div>

                  <div>
                    <span>가정 이율</span>
                    <strong>
                      연 {g.assumedRate ?? '-'}%
                    </strong>
                  </div>
                </div>

                <div style={{ marginTop: 12 }}>
                  <h3>모임 소개</h3>

                  <p className="muted">
                    {g.description ||
                      '모임 소개가 등록되지 않았습니다.'}
                  </p>
                </div>

                <div
                  className="info"
                  style={{ marginTop: 12 }}
                >
                  채팅·계좌 변경 기능은
                  운영 홈 연동 준비 중이에요.
                </div>
              </Card>
            </div>

          </div>
        </>
      )}
    </>
  );
}

// eslint-disable-next-line no-unused-vars -- 서버 API 연결 시 참고용 목업 화면

// eslint-disable-next-line no-unused-vars -- 서버 API 연결 시 참고용 목업 화면
function MockOperations() {
  const { run, setUi } = useApp();
  const nav = useNavigate();
  const [g, setGroup] = useOpGroup();

  if (!g) {
    return (
      <>
        <PageHead
          kicker="OPERATIONS"
          title="운영 홈"
          sub="참여 중인 모임이 없습니다."
        />

        <button
          className="btn primary"
          onClick={() => nav('/groups')}
        >
          모임 찾기
        </button>
      </>
    );
  }

  const l = leader(g);
  const t = groups.opsTodos(g);
  const m = me();

  const proc = tab => {
    setUi({ procTab: tab });
    nav('/procedures');
  };

  const Todo = ({ label, n, onClick, ico, urgent }) => (
    <button
      type="button"
      className={`todo ${n ? (urgent ? 'urgent' : '') : 'zero'}`}
      onClick={onClick}
    >
      <span className="todo-ico"><Icon g={ico} /></span>
      <small>{label}</small>
      <b>{n}건</b>
    </button>
  );

  const todos = l ? (
    <>
      <Todo
        label="가입 신청 대기"
        n={t.joins}
        ico="✦"
        urgent
        onClick={() =>
          document.getElementById('ops-join')?.scrollIntoView({
            behavior: 'smooth'
          })
        }
      />

      <Todo
        label="증빙 확인 대기"
        n={t.proofs}
        ico="▣"
        urgent
        onClick={() => nav('/evidence')}
      />

      <Todo
        label="수령 순서 처리"
        n={t.orders}
        ico="◷"
        onClick={() => proc('order')}
      />

      <Todo
        label="내 동의 필요"
        n={t.consents}
        ico="✓"
        urgent
        onClick={() => proc('withdraw')}
      />
    </>
  ) : (
    <>
      <Todo
        label="내 동의 필요"
        n={t.consents}
        ico="✓"
        urgent
        onClick={() => proc('withdraw')}
      />

      <Todo
        label="수령 순서 신청"
        n={
          db.orderRequests.filter(
            r =>
              r.groupId === g.id &&
              r.userId === m &&
              r.status !== '확정'
          ).length
        }
        ico="◷"
        onClick={() => proc('order')}
      />

      <Todo
        label="나의 누적 경고"
        n={g.warnings[m] || 0}
        ico="⚑"
        onClick={() => proc('rules')}
      />

      <Todo
        label="내 증빙 등록"
        n={
          proofs.listProofs(g.id).filter(
            p => p.memberId === m
          ).length
        }
        ico="▣"
        onClick={() => nav('/evidence')}
      />
    </>
  );

  const joinCard = (
    <Card>
      <h2 id="ops-join">가입 신청 승인</h2>

      <p className="muted">
        {g.status} · {g.members.length}/{g.max}명
      </p>

      <div className="list">
        {g.joinRequests
          .filter(r => r.status === '대기')
          .map(r => (
            <div className="list-item item-head" key={r.id}>
              <div>
                <b>{memberName(r.userId)}</b>

                <p className="muted mini">
                  {r.codeVerified
                    ? '인증코드 확인됨'
                    : '공개 모임 신청'}
                </p>
              </div>

              <div className="actions" style={{ margin: 0 }}>
                <button
                  className="btn small primary"
                  onClick={() =>
                    run(
                      () => groups.approveJoin(g.id, r.id),
                      '가입을 승인했어요.'
                    )
                  }
                >
                  승인
                </button>

                <button
                  className="btn small soft"
                  onClick={() =>
                    run(() => groups.rejectJoin(g.id, r.id))
                  }
                >
                  거절
                </button>
              </div>
            </div>
          ))}

        {!g.joinRequests.some(r => r.status === '대기') && (
          <Empty>대기 중인 가입 신청이 없습니다.</Empty>
        )}
      </div>
    </Card>
  );

  const payoutCard = (
    <Card>
      <h2>곗돈 수령 완료 표시</h2>

      {g.orderConfirmed ? (
        <>
          <p className="muted">
            수령 순서가 확정된 모임입니다.
            실제 송금은 발생하지 않는 기록용 표시입니다.
          </p>

          <div className="list">
            {g.members
              .filter(x => x !== g.ownerId)
              .map(x => (
                <div className="list-item item-head" key={x}>
                  <b>{memberName(x)}</b>

                  {g.payoutReceived.includes(x) ? (
                    <span className="pill">수령 완료</span>
                  ) : (
                    <button
                      className="btn small soft"
                      onClick={() =>
                        confirm(
                          '수령 완료를 표시할까요? 실제 송금은 발생하지 않습니다.'
                        ) &&
                        run(() => groups.markReceived(g.id, x))
                      }
                    >
                      수령 완료 표시
                    </button>
                  )}
                </div>
              ))}
          </div>
        </>
      ) : (
        <>
          <p className="info">
            수령 순서가 아직 확정되지 않은 모임입니다.
          </p>

          <div className="actions">
            <button
              className="btn small soft"
              onClick={() => proc('order')}
            >
              수령 순서로 이동
            </button>
          </div>
        </>
      )}
    </Card>
  );

  const statusCard = (
    <Card>
      <h2>모임 상태</h2>

      <div className="group-info-rows">
        <div>
          <span>모집</span>
          <strong>
            {g.status} · {g.members.length}/{g.max}명
          </strong>
        </div>

        <div>
          <span>납입일</span>
          <strong>매월 {g.payDay}일 (미납 감지 +5일)</strong>
        </div>

        <div>
          <span>수령 순서</span>
          <strong>
            {g.orderConfirmed ? '확정' : '협의 중'}
          </strong>
        </div>

        <div>
          <span>모임 계좌</span>
          <strong>
            {g.account
              ? `${g.account.bank} ${maskAccount(g.account.accountNo)}`
              : '미등록'}
          </strong>
        </div>

        <div>
          <span>해산</span>
          <strong>
            {g.dissolution?.status || '운영 중'}
          </strong>
        </div>

        <div>
          <span>건강도</span>
          <strong>{groups.healthScore(g)}점</strong>
        </div>
      </div>

      <div className="actions">
        <button
          className="btn small soft"
          onClick={() => nav('/chat')}
        >
          채팅
        </button>

        <button
          className="btn small soft"
          onClick={() => proc('order')}
        >
          모임 절차
        </button>
      </div>
    </Card>
  );

  const logs = groups.groupLogs(g).slice(0, 8);

  const logCard = (
    <Card>
      <h2>최근 운영 기록</h2>

      <div className="ops-log">
        {logs.length ? (
          logs.map((x, i) => (
            <div key={i}>
              <span className="when">{x.when}</span>

              <span className="what">
                <b>{x.type}</b>
                {x.desc}
              </span>
            </div>
          ))
        ) : (
          <p className="muted">
            아직 기록이 없어요.
          </p>
        )}
      </div>
    </Card>
  );

  return (
    <>
      <PageHead
        kicker="OPERATIONS HOME"
        title={`운영 홈 · ${g.title}`}
        sub="지금 처리할 일을 먼저 보여드려요."
      >
        <button
          className="btn primary"
          onClick={() => proc('order')}
        >
          모임 절차 →
        </button>
      </PageHead>

      <Card>
        <GroupSelect value={g.id} onChange={setGroup} />

        <div className="divider" />

        <h2>처리할 일</h2>

        <div className="ops-todo">{todos}</div>
      </Card>

      <div className="grid cols2 section">
        {l ? (
          <>
            {joinCard}
            {payoutCard}
          </>
        ) : (
          <>
            {statusCard}
            {logCard}
          </>
        )}
      </div>

      {l && (
        <div className="grid cols2 section">
          {statusCard}
          {logCard}
        </div>
      )}

      <div className="section">
        <Card>
          <h2>모임 정보</h2>

          <p>
            {g.description ||
              '모임 소개가 등록되지 않았습니다.'}
          </p>

          <div className="info">
            <b>{g.rulesTitle}</b>
            <p>{g.rules}</p>
          </div>

          <p className="muted mini">
            {g.purpose}
            {' · '}월 {won(g.amount)}
            {' · '}매월 {g.payDay}일
            {' · '}{g.months}개월
            {' · '}가정 이율 연 {g.rate}%
            {' · '}모임장 {memberName(g.ownerId)}
          </p>

          <VisibilityPanel g={g} />

          <FundsPanel g={g} />

          <details className="policy-fold">
            <summary>초대 링크 · 인증코드</summary>
            <InvitePanel g={g} />
          </details>
        </Card>
      </div>
    </>
  );
}

export function VisibilityPanel({ g }) {
  const { run } = useApp();
  const priv = g.visibility === 'PRIVATE';

  const toggle = () =>
    confirm(
      priv
        ? '공개 모임으로 전환할까요?\n지도·검색·추천에 다시 노출되고 누구나 가입 신청할 수 있습니다.'
        : '비공개 모임으로 전환할까요?\n추천·검색·지도에서 숨겨지고, 초대 링크와 6자리 인증코드로만 가입 신청을 받습니다.'
    ) &&
    run(
      () => groups.togglePrivate(g.id),
      r =>
        r
          ? '비공개 모임으로 전환했어요.'
          : '공개 모임으로 전환했어요.'
    );

  return (
    <div className="policy-panel">
      <h3>
        공개 설정

        <span className="policy-tag">
          {priv ? '비공개 모임' : '공개 모임'}
        </span>
      </h3>

      <p className="muted mini">
        {priv
          ? '추천·검색·지도에 노출되지 않으며, 초대 링크 + 6자리 인증코드로만 가입 신청을 받습니다.'
          : '지도·검색·맞춤 추천에 노출되고, 누구나 가입 신청할 수 있습니다.'}
      </p>

      {leader(g) ? (
        <div className="actions">
          <button
            className="btn small soft"
            onClick={toggle}
          >
            {priv
              ? '공개 모임으로 전환'
              : '비공개 모임으로 전환'}
          </button>
        </div>
      ) : (
        <p className="muted mini">
          공개 설정 변경은 모임장만 할 수 있습니다.
        </p>
      )}
    </div>
  );
}

export function InvitePanel({ g }) {
  const { run, toast } = useApp();
  const l = leader(g);
  const link = groups.inviteLink(g);

  const copy = (text, ok) =>
    navigator.clipboard
      ?.writeText(text)
      .then(() => toast(ok))
      .catch(() => toast('직접 복사해 주세요.'));

  return (
    <>
      <div className="policy-panel">
        <h3>초대 링크</h3>

        <p className="muted mini">
          초대 링크는 모임 ID가 아닌 토큰(INVITE_TOKEN)을 사용합니다.
          링크를 받은 사람도 로그인 후 모임장 승인이 필요합니다.
        </p>

        <input
          readOnly
          value={link}
          style={{ width: '100%' }}
        />

        <div className="actions">
          <button
            className="btn small primary"
            onClick={() =>
              copy(link, '초대 링크를 복사했어요.')
            }
          >
            초대 링크 복사
          </button>
        </div>
      </div>

      {g.visibility === 'PRIVATE' && (
        <div className="moava-code-box">
          <h3>비공개 모임 인증코드</h3>

          <p className="muted mini">
            모임별 공통 6자리 코드입니다.
            초대 링크로 들어온 사람이 이 코드를 입력하면
            가입 신청이 접수됩니다.
          </p>

          {l ? (
            <>
              <div className="moava-code-value">
                {ensureJoinCode(g).code}
              </div>

              <div className="actions">
                <button
                  className="btn small soft"
                  onClick={() =>
                    copy(
                      ensureJoinCode(g).code,
                      '인증코드를 복사했습니다.'
                    )
                  }
                >
                  코드 복사
                </button>

                <button
                  className="btn small"
                  onClick={() =>
                    confirm(
                      '기존 인증코드를 무효화하고 새 코드를 발급할까요?'
                    ) &&
                    run(
                      () => groups.rotateInviteCode(g.id),
                      '인증코드를 변경했습니다.'
                    )
                  }
                >
                  코드 변경
                </button>
              </div>
            </>
          ) : (
            <p className="muted mini">
              인증코드는 모임장만 조회할 수 있습니다.
            </p>
          )}
        </div>
      )}
    </>
  );
}

export function FundsPanel({ g }) {
  const { run, setUi } = useApp();
  const nav = useNavigate();

  const l = leader(g);
  const m = me();
  const minePaid = paidAny(g, m);
  const a = g.account;
  const p = g.pendingAccount;
  const period = currentPeriod();

  const change = () => {
    const bank = prompt(
      '변경할 은행명',
      a?.bank || ''
    );

    if (!bank) return;

    const no = prompt(
      '새 계좌번호 (재인증·회원 동의 필요)'
    );

    if (!no) return;

    run(
      () => groups.accountChange(g.id, bank, no),
      '계좌 변경 신청을 접수했어요. 전원에게 송금 보류 안내가 발송됩니다.'
    );
  };

  const myProofs = proofs
    .listProofs(g.id)
    .filter(x => l || minePaid || x.memberId === m)
    .slice(-6)
    .reverse();

  return (
    <>
      <div className="policy-panel">
        <h3>모임 계좌</h3>

        <p>
          은행 <b>{a?.bank || '미등록'}</b>
          {' · '}
          예금주 <b>{a ? maskName(a.holder) : '미등록'}</b>
          {' · '}
          계좌{' '}

          <b className="acct-mask">
            {maskAccount(a?.accountNo)}
          </b>

          {' '}

          <span className="policy-tag">
            {VERIFY_LABEL[a?.verifyStatus] || '인증 정보 없음'}
          </span>
        </p>

        {l && (
          <div className="actions">
            <button
              className="btn small soft"
              onClick={change}
            >
              계좌 변경 · 회원 알림
            </button>
          </div>
        )}

        {p && (
          <div className="policy-lock">
            <b>송금 보류 권고 · 계좌 변경 검토 중</b>

            <p>
              신청 은행 {p.bank}
              {' · '}{p.requestedAt}
              {' · '}동의 {p.consents.length}/{g.members.length}
              {' · '}소유 확인{' '}

              {p.verifyStatus === 'VERIFIED'
                ? '완료'
                : '관리자 확인 대기'}
            </p>

            <div className="actions">
              {!p.consents.includes(m) ? (
                <button
                  className="btn small soft"
                  onClick={() =>
                    run(
                      () => groups.consentAccount(g.id),
                      '동의를 기록했어요.'
                    )
                  }
                >
                  계좌 변경 동의
                </button>
              ) : (
                <span className="pill">동의 완료</span>
              )}

              {l && (
                <button
                  className="btn small primary"
                  onClick={() =>
                    run(
                      () => groups.confirmAccount(g.id),
                      '계좌 변경을 확정했어요.'
                    )
                  }
                >
                  동의 후 변경 확정
                </button>
              )}
            </div>
          </div>
        )}

        <details className="policy-fold">
          <summary>계좌 안전 안내</summary>

          <p>
            계좌 소유 확인(1원 인증) API는 미연동이며
            VERIFY_STATUS 상태값만 관리합니다.
            계좌 변경·모임장 승계 후에는 재인증이 필요하며,
            확정 전에는 송금을 보류하세요.
          </p>
        </details>
      </div>

      <div className="policy-panel">
        <h3>납입 현황 · {period.replace('-', '년 ')}월분</h3>

        {!minePaid && !l && (
          <p className="policy-lock">
            본인 납입이 확인되기 전에는
            다른 회원의 납입 현황을 볼 수 없습니다.
          </p>
        )}

        <div className="tablewrap">
          <table className="policy-table">
            <thead>
              <tr>
                <th>회원</th>
                <th>납입 상태</th>
                {l && <th>경고</th>}
              </tr>
            </thead>

            <tbody>
              {g.members.map(id => {
                const paid = paidThisPeriod(g, id, period);
                const show = l || minePaid || id === m;

                const od = proofs.overdueCases().find(
                  c =>
                    c.groupId === g.id &&
                    c.memberId === id &&
                    c.period === period &&
                    c.status !== 'RESOLVED'
                );

                return (
                  <tr key={id}>
                    <td>
                      {maskName(memberName(id))}
                      {id === g.ownerId && ' (모임장)'}
                    </td>

                    <td>
                      {show
                        ? paid
                          ? '납입 확인'
                          : od
                            ? (
                              <span className="pill warn">
                                미납 감지
                              </span>
                            )
                            : '확인되지 않음'
                        : '본인 납입 후 열람'}
                    </td>

                    {l && (
                      <td>
                        경고 {g.warnings?.[id] || 0}회
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {myProofs.length > 0 && (
          <>
            <h3 style={{ marginTop: 16 }}>
              최근 납입 증빙
            </h3>

            <div className="tablewrap">
              <table className="policy-table">
                <thead>
                  <tr>
                    <th>날짜</th>
                    <th>회원</th>
                    <th>금액</th>
                    <th>상태</th>
                  </tr>
                </thead>

                <tbody>
                  {myProofs.map(x => (
                    <tr key={x.id}>
                      <td>{x.date}</td>
                      <td>{maskName(memberName(x.memberId))}</td>
                      <td>{won(x.amount)}</td>
                      <td>{x.status}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}

        <div className="actions">
          <button
            className="btn small soft"
            onClick={() => {
              setUi({ selected: g.id });
              nav('/evidence');
            }}
          >
            납입 증빙 등록·확인
          </button>

          <button
            className="btn small soft"
            onClick={() => nav('/support')}
          >
            고객센터 · 신고
          </button>
        </div>

        <details className="policy-fold">
          <summary>미납 관리 안내</summary>

          <p>
            납입일 + 5일 경과 시 자동 감지되어 관리자에게 알림이 가며,
            경고·채팅 제한·수령 후순위 조정은
            관리자 검토 후 적용됩니다.
          </p>
        </details>
      </div>
    </>
  );
}