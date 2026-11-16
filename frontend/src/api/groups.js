
/* F11~F19 F20 F28 F33 F36 — 모임 조회·생성·가입·운영 */

import * as D from './mock/db';

const PURPOSE_CODES = {
  '목돈 마련': 'SAVINGS',
  '재테크': 'INVEST',
  '친목': 'SOCIAL',
  '창업': 'STARTUP',
  '여행': 'TRAVEL'
};

const OVERDUE_POLICY_CODES = {
  '5일차 알림': 'ALERT_ONLY',
  '5일차 알림 + 채팅 제한·수령 후순위 검토': 'ALERT_RESTRICT'
};

function toGroupCreateRequest(f) {
  const region = String(f.region || '').trim().split(/\s+/);

  return {
    title: f.title.trim(),
    purpose: PURPOSE_CODES[f.purpose] || f.purpose,
    regionSido: region[0] || '',
    regionSigungu: region.slice(1).join(' '),
    amount: Number(f.amount) * 10000,
    maxMembers: Number(f.max),
    months: Number(f.months),
    payDay: Number(f.payDay),
    assumedRate: Number(f.rate),
    visibility: f.visibility,
    rulesTitle: f.rulesTitle.trim(),
    rulesBody: f.rules.trim(),
    description: f.description.trim(),
    overduePolicy: OVERDUE_POLICY_CODES[f.overduePolicy] || f.overduePolicy,
    bankName: f.bank.trim(),
    accountNumber: String(f.accountNo || '').replace(/\D/g, '')
  };
}

const { db, save, fail } = D;

/* ---- 조회 ---- */
export const listGroups = () => db.groups;
export const getGroup = id => D.group(id);
export const myGroups = () => db.groups.filter(D.activeJoined);
export const endedGroups = () => db.groups.filter(g => D.joined(g) && D.isEnded(g));
export const byToken = t => db.groups.find(g => g.inviteToken === t) || null;

export const recommended = (rec) =>
  db.groups
    .filter(g =>
      D.groupVisible(g) &&
      g.status === '모집 중' &&
      g.amount <= Math.max(rec + 10, 20)
    )
    .slice(0, 3);

export function healthScore(g) {
  const warnings = Object.values(g.warnings || {})
    .reduce((a, b) => a + b, 0);

  const rejects = db.proofs.filter(
    x => x.groupId === g.id && x.status === '보완 요청'
  ).length;

  const overdue = db.overdueCases.filter(
    x => x.groupId === g.id && x.status !== 'RESOLVED'
  ).length;

  return Math.max(
    0,
    Math.min(
      100,
      Math.round(
        95 -
        warnings * 9 -
        rejects * 6 -
        overdue * 5 -
        (g.dissolution ? 12 : 0)
      )
    )
  );
}

export function inviteLink(g) {
  if (!g.inviteToken) {
    g.inviteToken = D.inviteToken();
    save();
  }

  return `${location.origin}/invite?t=${encodeURIComponent(g.inviteToken)}`;
}

export function opsTodos(g) {
  const l = D.leader(g);
  const m = D.me();
  const t = { joins: 0, proofs: 0, consents: 0, orders: 0 };

  if (l) {
    t.joins = g.joinRequests.filter(
      r => r.status === '대기'
    ).length;

    t.proofs = db.proofs.filter(
      p => p.groupId === g.id && p.status === '확인 대기'
    ).length;

    t.orders = db.orderRequests.filter(
      r =>
        r.groupId === g.id &&
        ['신청', '동의 요청'].includes(r.status)
    ).length;
  }

  db.orderRequests
    .filter(
      r =>
        r.groupId === g.id &&
        r.status === '동의 요청' &&
        r.affected?.includes(m) &&
        !r.consents.includes(m)
    )
    .forEach(() => t.consents++);

  g.withdrawals
    .filter(
      w =>
        w.status === '정산 동의 대기' &&
        w.userId !== m &&
        !w.consents.includes(m)
    )
    .forEach(() => t.consents++);

  if (g.transfer && !g.transfer.consents.includes(m)) {
    t.consents++;
  }

  if (g.pendingAccount && !g.pendingAccount.consents.includes(m)) {
    t.consents++;
  }

  return t;
}

export const groupLogs = g =>
  [
    ...db.history
      .filter(h => h.groupId === g.id)
      .map(h => ({
        when: h.date,
        type: h.type,
        desc: h.description
      })),
    ...(g.log || []).map(x => ({
      when: x.at,
      type: '정책',
      desc: x.msg
    }))
  ].sort((a, b) => String(b.when).localeCompare(String(a.when)));

/* ---- 생성 (F15 F19) ---- */
export async function createGroup(f) {
  const amount = Number(f.amount);
  const payDay = Number(f.payDay);
  const max = Number(f.max);
  const months = Number(f.months);

  // 1. 필수 입력값 검증
  if (
    !f.title?.trim() ||
    !f.rules?.trim() ||
    !f.description?.trim() ||
    !f.rulesTitle?.trim()
  ) {
    fail('모임 필수 정보를 입력해 주세요.');
  }

  // 2. 월 납입액 검증
  if (
    !Number.isInteger(amount) ||
    amount < 5 ||
    amount % 5 !== 0
  ) {
    fail('월 납입액은 5만 원 단위로 입력해 주세요.');
  }

  // 3. 납입일 검증
  if (
    !Number.isInteger(payDay) ||
    payDay < 1 ||
    payDay > 28
  ) {
    fail('납입일은 1~28일 사이여야 합니다.');
  }

  // 4. 인원 검증
  if (
    !Number.isInteger(max) ||
    max < 2 ||
    max > 30
  ) {
    fail('인원은 2~30명입니다.');
  }

  // 5. 기간 검증
  if (
    !Number.isInteger(months) ||
    months < 2 ||
    months > 36
  ) {
    fail('기간은 2~36개월입니다.');
  }

  // 6. 계좌 검증
  const accountNo = String(f.accountNo || '').replace(/\D/g, '');

  if (!f.bank?.trim() || accountNo.length < 8) {
    fail('모임 계좌를 확인해 주세요.');
  }

  // 7. Spring Boot DTO 형식으로 변환
  const payload = toGroupCreateRequest(f);

  if (!payload.regionSido || !payload.regionSigungu) {
    fail('지역을 시·도와 시·군·구까지 입력해 주세요.');
  }

  // 8. Spring Boot API 호출
  const response = await fetch('/api/groups', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });

  // 9. 서버 응답 처리
  const result = await response.json().catch(() => null);

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error(
        '로그인 인증이 필요합니다. 백엔드 인증 연결을 확인해 주세요.'
      );
    }

    if (response.status === 403) {
      throw new Error('모임 생성 권한이 없습니다.');
    }

    throw new Error(
      result?.message ||
      `모임 생성 요청에 실패했습니다. (${response.status})`
    );
  }

  // 10. 서버 응답을 프론트엔드 형식으로 변환
  if (result?.groupId == null) {
    throw new Error(
      '모임 생성 응답에 groupId가 없습니다. 서버 저장 결과를 확인해 주세요.'
    );
  }

  return {
    ...result,
    id: result.groupId
  };
}

/* ---- 가입 (F16 F18 F20) ---- */
// 이 아래부터는 기존 joinRequest 함수와 나머지 코드를 그대로 유지!
