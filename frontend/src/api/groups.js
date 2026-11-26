
/* F11~F19 F20 F28 F33 F36 — 모임 조회·생성·가입·운영 */

import * as D from './mock/db';
import { getAccessToken } from './auth';
import { apiFetch } from './client';
import { encodeRegionForServer, parseRegionFromServer } from '../common/regions';

const { db, save, fail } = D;

/* ---- Spring Boot 모임 생성 DTO 변환 ---- */

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
  let regionSido = '';
  let regionSigungu = '';

  if (f.regionSido || f.regionSigungu || f.regionDong) {
    const encoded = encodeRegionForServer({
      sido: f.regionSido,
      sigungu: f.regionSigungu,
      dong: f.regionDong
    });
    regionSido = encoded.regionSido;
    regionSigungu = encoded.regionSigungu;
  } else {
    const region = String(f.region || '').trim().split(/\s+/);
    regionSido = region[0] || '';
    regionSigungu = region.slice(1).join(' ');
  }

  return {
    title: f.title.trim(),
    purpose: PURPOSE_CODES[f.purpose] || f.purpose,
    regionSido,
    regionSigungu,
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

/* ---- 조회 ---- */

export const listGroups = () => db.groups;

export const getGroup = id => D.group(id);

export const myGroups = () => db.groups.filter(D.activeJoined);
// Oracle DB에서 로그인한 회원이 모임장인 계모임 조회
export async function fetchMyGroups() {
  if (!getAccessToken()) {
    throw new Error('로그인이 필요해요.');
  }

  const data = await apiFetch('/api/groups/my');

  if (!Array.isArray(data)) {
    throw new Error('내 계모임을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');
  }

  return data;
}

/** 초대 링크 토큰으로 모임 조회 (GET /api/v1/invites/{token}, 로그인 필요) */
export async function fetchInvite(token) {
  if (!token) throw new Error('초대 주소가 올바르지 않아요.');
  try {
    return await apiFetch(`/api/v1/invites/${encodeURIComponent(token)}`);
  } catch (e) {
    if (e.status === 404) throw new Error('유효하지 않거나 만료된 초대 링크예요.');
    throw e;
  }
}

/** 서버 모임 상태 코드(GYE_GROUP.STATUS) → 화면 문구 */
export const GROUP_STATUS_LABEL = { RECRUITING: '모집 중', ACTIVE: '운영 중', DISSOLVE_PENDING: '해산 검토 중', DISSOLVED: '해산 완료' };
// 모집 상태 표시
// 정원에 도달한 경우 DB 상태 변경 없이 모집 종료로 표시
export const groupStatusLabel = (s, currentMembers, maxMembers) => {
  if (
    s === 'RECRUITING' &&
    Number.isInteger(currentMembers) &&
    Number.isInteger(maxMembers) &&
    maxMembers > 0 &&
    currentMembers >= maxMembers
  ) {
    return '모집 종료';
  }

  return GROUP_STATUS_LABEL[s] || '확인 중';
};
/** 서버 모임 목적 코드(GYE_GROUP.PURPOSE) → 화면 문구 */
export const PURPOSE_LABEL = { SAVINGS: '목돈 마련', INVEST: '재테크', SOCIAL: '친목', STARTUP: '창업', TRAVEL: '여행' };
export const purposeLabel = p => PURPOSE_LABEL[p] || p || '-';

/** 서버 공개 모임 DTO(PublicGroupResponse) → 화면(Groups / KakaoGroupMap / GroupCard / GroupInfo) 형식 변환 */
export function mapPublicGroup(dto) {
  const maxMembers = Number(dto.maxMembers || 0);
  const currentMembers = Number(dto.currentMembers ?? 1);
  const amountKrw = Number(dto.amount || 0);
  const rawSido = String(dto.regionSido || '').trim();
  const rawSigungu = String(dto.regionSigungu || '').trim();
  const rawDong = String(dto.regionDong || '').trim();
  const parsedRegion = parseRegionFromServer(rawSido, rawSigungu, rawDong);
  const regionSido = parsedRegion.sido || rawSido;
  const regionSigungu = parsedRegion.sigungu || rawSigungu;
  const regionDong = parsedRegion.dong || rawDong;
  const lat = dto.lat != null && dto.lat !== '' && Number.isFinite(Number(dto.lat)) ? Number(dto.lat) : null;
  const lng = dto.lng != null && dto.lng !== '' && Number.isFinite(Number(dto.lng)) ? Number(dto.lng) : null;

  return {
    id: dto.groupId,
    groupId: dto.groupId,
    title: dto.title || '',
    ownerId: dto.ownerId,
    ownerName: dto.ownerName || '회원',
    statusCode: dto.status || 'RECRUITING',
    status: groupStatusLabel(dto.status, currentMembers, maxMembers),
    purposeCode: dto.purpose || '',
    purpose: purposeLabel(dto.purpose),
    regionSido,
    regionSigungu,
    regionDong,
    region: parsedRegion.displayLabel || [regionSido, regionSigungu, regionDong].filter(Boolean).join(' '),
    lat,
    lng,
    amountKrw,
    amount: amountKrw / 10000,
    max: maxMembers,
    maxMembers,
    currentMembers,
    months: Number(dto.months || 0),
    payDay: Number(dto.payDay || 0),
    rate: Number(dto.assumedRate ?? 0),
    visibility: dto.visibility || 'PUBLIC',
    description: dto.description || '',
    rulesTitle: dto.rulesTitle || '운영 규칙',
    rules: dto.rulesBody || '',
    rulesBody: dto.rulesBody || '',
    orderConfirmed: Boolean(dto.orderConfirmed),
    role: dto.role || 'NONE',
    source: 'SERVER'
  };
}

/** 공개 모임 목록·지도 조회 (GET /api/groups?region=&purpose=&q=) */
export async function fetchPublicGroups({ region, purpose, q } = {}) {
  if (!getAccessToken()) {
    throw new Error('로그인이 필요해요.');
  }

  const params = new URLSearchParams();

  const normalizedRegion = String(region || '').trim();
  if (normalizedRegion && normalizedRegion !== '전체') {
    params.set('region', normalizedRegion);
  }

  const normalizedPurpose = String(purpose || '').trim();
  if (normalizedPurpose && normalizedPurpose !== '전체') {
    params.set('purpose', PURPOSE_CODES[normalizedPurpose] || normalizedPurpose);
  }

  const normalizedQuery = String(q || '').trim();
  if (normalizedQuery) {
    params.set('q', normalizedQuery);
  }

  const qs = params.toString();
  const data = await apiFetch(qs ? `/api/groups?${qs}` : '/api/groups');

  if (!Array.isArray(data)) {
    throw new Error('공개 모임 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');
  }

  return data.map(mapPublicGroup);
}

export const endedGroups = () =>
    db.groups.filter(g => D.joined(g) && D.isEnded(g));

export const byToken = t =>
    db.groups.find(g => g.inviteToken === t) || null;

export const recommended = rec =>
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
    ].sort((a, b) =>
        String(b.when).localeCompare(String(a.when))
    );

/* ---- 생성 (F15 F19) : Spring Boot API ---- */

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

  // 2. 월 납입액 검증 (5만 원 단위)
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

  // 6. 모임 계좌 검증
  const accountNo = String(f.accountNo || '').replace(/\D/g, '');

  if (!f.bank?.trim() || accountNo.length < 8) {
    fail('모임 계좌를 확인해 주세요.');
  }

  // 7. Spring Boot DTO 변환
  const payload = toGroupCreateRequest(f);

  if (!payload.regionSido || !payload.regionSigungu || ('regionDong' in f && !String(f.regionDong || '').trim())) {
    fail('지역을 시/도, 시/군/구, 읍/면/동까지 선택해 주세요.');
  }

  // 8. 서버에 생성 요청 (오류는 apiFetch 가 사용자용 문장으로 바꿔 준다)
  if (!getAccessToken()) {
    throw new Error('로그인이 필요해요.');
  }

  const result = await apiFetch('/api/groups', {
    method: 'POST',
    body: JSON.stringify(payload)
  });

  // 9. 서버 응답을 프론트엔드 형식으로 변환
  if (result?.groupId == null) {
    throw new Error(
        '모임이 만들어졌는지 확인하지 못했어요. 내 계모임에서 확인해 주세요.'
    );
  }

  return {
    ...result,
    id: result.groupId
  };
}


/*
 * 아래 가입·운영 함수들은 기존 GitHub Mock DB 로직을 복구한 것.
 * 아직 Spring Boot API와 연결된 구현은 아님.
 */

/* ---- 가입 (F16 F18 F20) ---- */

// F16 - 기존 가입 및 탈퇴 이력 확인
function hasMembershipHistory(g, memberId) {
  const key = String(memberId);

  return (
    String(g.ownerId) === key ||

    (g.members ?? []).some(
      id => String(id) === key
    ) ||

    (g.joinRequests ?? []).some(
      r => String(r.userId) === key &&
           r.status === '승인'
    ) ||

    (g.withdrawals ?? []).some(
      w => String(w.userId) === key &&
           w.status === '완료'
    ) ||

    (g.formerMemberIds ?? []).some(
      id => String(id) === key
    )
  );
}

export async function joinRequest(id) {
  const g = D.group(id);

  if (!g) fail('모임을 찾을 수 없어요.');

  if (D.sanctioned(D.me())) {
    fail('플랫폼 가입 제한 상태입니다. 관리자에게 문의하세요.');
  }

  if (
    g.status !== '모집 중' ||
    g.started ||
    g.dissolution ||
    g.members.length >= g.max
  ) {
    fail('가입이 마감된 모임입니다.');
  }

  // F16 - 기존 가입 및 탈퇴 이력이 있으면 재가입 불가
  if (hasMembershipHistory(g, D.me())) {
    fail('이미 참여 중이거나 탈퇴 이력이 있는 모임입니다.');
  }

  if (
    g.joinRequests.some(
      x => x.userId === D.me() && x.status === '대기'
    )
  ) {
    fail('이미 신청했어요.');
  }

  if (g.visibility === 'PRIVATE') {
    const d = D.ensureJoinCode(g);
    const key = String(D.me());

    if ((d.attempts[key]?.count ?? 0) >= 3) {
      fail(
        '인증에 3회 실패했습니다. 모임장에게 인증코드 재발급을 요청해 주세요.'
      );
    }

    if (!d.verified[key]) {
      fail(
        '비공개 모임은 초대 링크와 인증코드로만 가입 신청할 수 있어요.'
      );
    }
  }

  g.joinRequests.push({
    id: D.uid(),
    userId: D.me(),
    status: '대기',
    codeVerified: g.visibility === 'PRIVATE'
  });

  D.notice(
    g.ownerId,
    '가입 검토 필요',
    `${g.title}에 가입 신청이 접수되었습니다.`
  );

  D.audit(
    '가입 신청',
    g.id,
    `${D.currentUser().name} 신청`
  );

  save();
}

export async function verifyInviteCode(id, value) {
  const g = D.group(id);

  if (!g || g.visibility !== 'PRIVATE') {
    fail('비공개 모임이 아닙니다.');
  }

  const d = D.ensureJoinCode(g);
  const key = String(D.me());

  const a = d.attempts[key] || {
    count: 0
  };

  const lockedMessage =
    '인증에 3회 실패했습니다. 모임장에게 인증코드 재발급을 요청해 주세요.';

  // 3회 실패 시 코드 재발급 전까지 인증 차단
  if (a.count >= 3) {
    fail(lockedMessage);
  }

  // 인증코드 형식 및 일치 여부 확인
  if (
    !/^\d{6}$/.test(value) ||
    value !== String(d.code)
  ) {
    a.count = Math.min(3, a.count + 1);

    d.attempts[key] = a;

    save();

    if (a.count >= 3) {
      fail(lockedMessage);
    }

    fail('인증코드가 올바르지 않습니다.');
  }

  // 인증 성공 처리
  if (!d.verified[key]) {
    d.verified[key] = true;
    delete d.attempts[key];
  }

  D.groupLog(g, '인증코드 확인');

  save();

  await joinRequest(id);
}

export async function approveJoin(id, rid) {
  const g = D.group(id);
  const r = g?.joinRequests.find(x => x.id === rid);

  if (!D.leader(g) || !r || r.status !== '대기') {
    fail('승인 권한이 없습니다.');
  }

  if (
    g.started ||
    g.members.length >= g.max ||
    g.status !== '모집 중'
  ) {
    fail('가입이 마감되었습니다.');
  }

  if (D.sanctioned(r.userId)) {
    fail('플랫폼 가입 제한 회원입니다.');
  }

  // F16 - 승인 시점에도 기존 가입 이력 재검사
  if (hasMembershipHistory(g, r.userId)) {
    fail('이미 참여 중이거나 탈퇴 이력이 있는 회원입니다.');
  }

  r.status = '승인';

  g.members.push(r.userId);

  D.notice(
    r.userId,
    '계모임 가입 승인',
    `${g.title} 가입이 승인되었습니다.`
  );

  D.audit(
    '가입 승인',
    id,
    D.memberName(r.userId)
  );

  save();
}

export async function rejectJoin(id, rid) {
  const g = D.group(id);
  const r = g?.joinRequests.find(x => x.id === rid);

  if (!D.leader(g) || !r || r.status !== '대기') {
    fail('처리할 수 없습니다.');
  }

  r.status = '거절';

  D.notice(
      r.userId,
      '계모임 가입 거절',
      `${g.title} 가입 신청이 거절되었습니다.`
  );

  D.audit('가입 거절', id, '모임장 처리');
  save();
}

/* ---- 수령 완료 (F28) ---- */

export async function markReceived(id, memberId) {
  const g = D.group(id);

  if (!D.leader(g) || !g.members.includes(memberId)) {
    fail('권한이 없습니다.');
  }

  if (!g.orderConfirmed) {
    fail('수령 순서가 확정된 후에 표시할 수 있어요.');
  }

  g.payoutReceived.push(memberId);

  D.notice(
      memberId,
      '곗돈 수령 완료 표시',
      `${g.title}: 수령 완료로 기록되었습니다.`
  );

  D.audit('수령 완료 기록', id, D.memberName(memberId));
  save();
}

/* ---- 공개 전환 · 초대 (F18) ---- */

export async function togglePrivate(id) {
  const g = D.group(id);

  if (!D.leader(g)) {
    fail('모임장만 공개 설정을 바꿀 수 있어요.');
  }

  if (D.isEnded(g)) {
    fail('종료된 모임은 설정을 바꿀 수 없어요.');
  }

  const toPrivate = g.visibility !== 'PRIVATE';

  g.visibility = toPrivate ? 'PRIVATE' : 'PUBLIC';

  if (toPrivate) {
    D.ensureJoinCode(g);
  }

  D.groupLog(
      g,
      toPrivate ? '비공개 모임으로 전환' : '공개 모임으로 전환'
  );

  D.audit(
      toPrivate ? '비공개 전환' : '공개 전환',
      g.id,
      `${D.currentUser().name} 처리`
  );

  g.members.forEach(x =>
      D.notice(
          x,
          '모임 공개 설정 변경',
          `${g.title}: ${toPrivate ? '비공개' : '공개'} 모임으로 전환되었습니다.`
      )
  );

  save();
  return toPrivate;
}

export async function rotateInviteCode(id) {
  const g = D.group(id);

  if (!D.leader(g) || g.visibility !== 'PRIVATE') {
    fail('비공개 모임장만 변경할 수 있습니다.');
  }

  // 기존 인증코드 확인
  const previousCode = String(D.ensureJoinCode(g).code);

  let newCode = null;

  // 기존 인증코드와 다른 번호 생성
  for (let i = 0; i < 10; i++) {
    const candidate = String(D.inviteDigits());

    if (
      /^\d{6}$/.test(candidate) &&
      candidate !== previousCode
    ) {
      newCode = candidate;
      break;
    }
  }

  if (newCode === null) {
    fail('새로운 인증코드 생성에 실패했습니다.');
  }

  // 인증코드 재발급 및 회원별 인증 상태 초기화
  g.joinCode = {
    code: newCode,
    attempts: {},
    verified: {}
  };

  D.groupLog(g, '비공개 초대 인증코드 변경');

  save();
}

/* ---- 모임 계좌 변경 (F33) ---- */

export async function accountChange(id, bank, accountNo) {
  const g = D.group(id);

  if (!D.leader(g)) {
    fail('모임장만 계좌를 변경할 수 있습니다.');
  }

  if (g.pendingAccount) {
    fail('이미 검토 중인 계좌 변경 신청이 있습니다.');
  }

  if (!bank?.trim() || !accountNo?.trim()) {
    fail('은행과 계좌번호를 입력해 주세요.');
  }

  g.pendingAccount = {
    bank: bank.trim(),
    accountNo: accountNo.replace(/\D/g, ''),
    holder: D.currentUser().name,
    requestedAt: D.nowText(),
    consents: [],
    verifyStatus: 'PENDING'
  };

  D.groupLog(g, '계좌 변경 신청 · 재인증 및 회원 동의 대기');

  g.members.forEach(x =>
      D.notice(
          x,
          '송금 보류 안내',
          `${g.title}에서 계좌 변경 신청이 접수됐습니다. 확인 전 송금하지 마세요.`
      )
  );

  D.noticeAdmin(
      '계좌 변경 확인 필요',
      `${g.title}의 모임 계좌 변경이 신청되었습니다.`
  );

  save();
}

export async function consentAccount(id) {
  const g = D.group(id);
  const p = g?.pendingAccount;

  if (!D.joined(g) || !p) {
    fail('동의 대상이 아닙니다.');
  }

  if (!p.consents.includes(D.me())) {
    p.consents.push(D.me());
  }

  D.groupLog(g, '계좌 변경 검토 동의');
  save();
}

export async function confirmAccount(id) {
  const g = D.group(id);
  const p = g?.pendingAccount;

  if (!D.leader(g) || !p) {
    fail('권한이 없습니다.');
  }

  if (p.verifyStatus !== 'VERIFIED') {
    fail('관리자의 계좌 소유 확인이 먼저 필요합니다.');
  }

  if (g.members.some(x => !p.consents.includes(x))) {
    fail('모든 참여 회원의 동의가 필요합니다.');
  }

  g.account = {
    bank: p.bank,
    holder: p.holder,
    accountNo: p.accountNo,
    verifyStatus: 'VERIFIED',
    keyVer: 1
  };

  g.pendingAccount = null;

  D.groupLog(g, '모든 회원 동의 후 계좌 변경 확정');

  g.members.forEach(x =>
      D.notice(
          x,
          '모임 계좌 변경 확정',
          `${g.title}의 계좌 변경이 확정되었습니다. 송금 전 직접 재확인하세요.`
      )
  );

  save();
}

/* ---- 승계 (F36) ---- */

export async function setSuccessor(id, to) {
  const g = D.group(id);

  if (!D.leader(g)) {
    fail('모임장만 지정할 수 있습니다.');
  }

  if (!to || to === g.ownerId || !g.members.includes(to)) {
    fail('다른 참여 회원을 선택해 주세요.');
  }

  g.successor = to;

  D.groupLog(g, `${D.memberName(to)} 승계 대상 지정`);
  save();
}

export async function requestTransfer(id) {
  const g = D.group(id);

  if (!D.leader(g)) {
    fail('현재 모임장만 승계를 요청할 수 있습니다.');
  }

  if (!g.successor || !g.members.includes(g.successor)) {
    fail('먼저 승계 대상 회원을 지정해 주세요.');
  }

  if (g.transfer) {
    fail('승계 동의가 이미 진행 중입니다.');
  }

  g.transfer = {
    from: g.ownerId,
    to: g.successor,
    consents: [],
    accepted: false
  };

  D.groupLog(g, '모임장 권한 승계 동의 요청');

  g.members.forEach(x =>
      D.notice(
          x,
          '모임장 승계 동의 요청',
          `${g.title}의 모임장 변경에 대한 동의를 요청합니다.`
      )
  );

  save();
}

export async function acceptTransfer(id) {
  const g = D.group(id);

  if (!g?.transfer || g.transfer.to !== D.me()) {
    fail('승계 대상이 아닙니다.');
  }

  g.transfer.accepted = true;

  D.groupLog(g, '승계 대상 수락');
  save();
}

export async function consentTransfer(id) {
  const g = D.group(id);

  if (!g?.transfer || !D.joined(g)) {
    fail('동의 대상이 아닙니다.');
  }

  if (!g.transfer.consents.includes(D.me())) {
    g.transfer.consents.push(D.me());
  }

  D.groupLog(g, '승계 동의');
  save();
}

export async function confirmTransfer(id) {
  const g = D.group(id);
  const t = g?.transfer;

  if (!t || !D.leader(g)) {
    fail('권한이 없습니다.');
  }

  if (
      !t.accepted ||
      g.members.some(x => !t.consents.includes(x))
  ) {
    fail('승계 대상 수락과 모든 회원 동의가 필요합니다.');
  }

  const old = g.ownerId;

  g.ownerId = t.to;
  g.successor = null;
  g.transfer = null;

  if (!g.members.includes(old)) {
    g.members.push(old);
  }

  if (g.account) {
    g.account.verifyStatus = 'REVERIFY_REQUIRED';
  }

  D.groupLog(
      g,
      `${D.memberName(old)} → ${D.memberName(g.ownerId)} 권한 승계 · 기존 모임장은 Member 유지 · 계좌 재인증 필요`
  );

  g.members.forEach(x =>
      D.notice(
          x,
          '모임장 변경',
          `${g.title} 모임장이 변경되었습니다. 신규 계좌 재인증 전 송금하지 마세요.`
      )
  );

  D.audit(
      '모임장 승계',
      id,
      `${D.memberName(old)} → ${D.memberName(g.ownerId)}`
  );

  save();
}

/* ---- 규칙 · 경고 · 해산 (F24 F25 F31) ---- */

/* F31 운영 규칙 — 서버 연동 (MOA-76)
   GET /api/v1/groups/{groupId}/rules → { groupId, rulesTitle, rulesBody, leader, groupStatus }
   PUT /api/v1/groups/{groupId}/rules  { rulesTitle, rulesBody } → 같은 모양 (저장 후 규칙)
   오류는 apiFetch 가 서버의 한국어 문장(error.message)으로 throw 한다. */

/** 운영 규칙 제목 최대 바이트 (GYE_GROUP.RULES_TITLE VARCHAR2(100), 한글 1자 = 3바이트) */
export const RULES_TITLE_MAX_BYTES = 100;

/** UTF-8 바이트 수 (서버와 같은 기준으로 앞뒤 공백 제외) */
export const utf8Bytes = text => new TextEncoder().encode(String(text ?? '').trim()).length;

export async function fetchRules(groupId) {
  return apiFetch(`/api/v1/groups/${encodeURIComponent(groupId)}/rules`);
}

export async function updateRules(groupId, { rulesTitle, rulesBody }) {
  return apiFetch(`/api/v1/groups/${encodeURIComponent(groupId)}/rules`, {
    method: 'PUT',
    body: JSON.stringify({ rulesTitle, rulesBody }),
  });
}

// TODO(MOA-76): 목업 전용. 화면은 fetchRules / updateRules 를 쓴다. 다른 목업 화면 정리 때 함께 삭제 예정.
export async function editRule(id, title, body) {
  const g = D.group(id);

  if (!D.leader(g)) {
    fail('모임장만 수정할 수 있습니다.');
  }

  if (!title?.trim() || !body?.trim()) {
    fail('제목과 내용을 입력해 주세요.');
  }

  g.rulesTitle = title.trim();
  g.rules = body.trim();

  D.audit('운영 규칙 등록', id, g.rulesTitle);

  g.members.forEach(x =>
      D.notice(
          x,
          '운영 규칙 업데이트',
          `${g.title}: ${g.rulesTitle}`
      )
  );

  save();
}

export async function warnMember(id, x, reason) {
  const g = D.group(id);

  if (!D.leader(g)) {
    fail('권한이 없습니다.');
  }

  if (!x || !reason?.trim()) {
    fail('대상과 사유를 입력해 주세요.');
  }

  if (!g.members.includes(x)) {
    fail('현재 참여 회원만 경고할 수 있습니다.');
  }

  if ((g.warnings[x] || 0) >= 2) {
    fail('경고는 모임별 최대 2회입니다. 강제 탈퇴 검토 절차를 진행해 주세요.');
  }

  g.warnings[x] = (g.warnings[x] || 0) + 1;

  D.notice(
      x,
      '모임 경고 알림',
      `${g.title}: 경고 ${g.warnings[x]}회 · ${reason}`
  );

  D.audit(
      '경고',
      id,
      `${D.memberName(x)} · ${reason}`
  );

  save();
}

export async function forceMember(id, x, reason) {
  const g = D.group(id);

  if (!D.leader(g)) {
    fail('권한이 없습니다.');
  }

  if (!x || !reason?.trim()) {
    fail('사유를 입력해 주세요.');
  }

  if (!g.members.includes(x)) {
    fail('현재 참여 중인 회원만 검토할 수 있습니다.');
  }

  if ((g.warnings[x] || 0) < 2) {
    fail('누적 경고 2회 이상부터 강제 탈퇴 검토가 가능합니다.');
  }

  // F16 - 강제 탈퇴 회원의 재가입 차단을 위한 이력 보존
  g.formerMemberIds ??= [];

  if (
    !g.formerMemberIds.some(
      id => String(id) === String(x)
    )
  ) {
    g.formerMemberIds.push(x);
  }

  g.members = g.members.filter(a => a !== x);

  D.notice(
      x,
      '강제 탈퇴 처리',
      `${g.title}: ${reason}. 이의 신청은 고객센터로 문의하세요.`
  );

  D.audit(
      '강제 탈퇴',
      id,
      `${D.memberName(x)} · ${reason}`
  );

  save();
}

export async function requestDissolve(id, reason) {
  const g = D.group(id);

  if (!D.leader(g)) {
    fail('해산 신청은 모임장만 가능합니다.');
  }

  if (g.dissolution) {
    fail('이미 신청했습니다.');
  }

  if (!reason?.trim()) {
    fail('사유를 입력해 주세요.');
  }

  g.dissolution = {
    reason,
    status: '관리자 검토 대기',
    requestedAt: D.today()
  };

  D.noticeAdmin(
      '해산 승인 검토 필요',
      `${g.title} 해산 신청이 접수되었습니다.`
  );

  D.audit('해산 신청', id, reason);
  save();
}

/* ==================================================
   F18 — 비공개 모임 인증코드 발급 및 검증 (Spring Boot)
   ================================================== */

/**
 * 모임장 전용
 * POST /api/groups/{groupId}/join-code
 *
 * 호출할 때마다 새 인증코드가 발급되므로
 * 사용자가 버튼을 클릭했을 때만 호출한다.
 */
export async function issueJoinCode(groupId) {
  if (!getAccessToken()) {
    throw new Error('로그인이 필요해요.');
  }

  if (!Number.isSafeInteger(Number(groupId)) || Number(groupId) <= 0) {
    throw new Error('올바르지 않은 모임 ID입니다.');
  }

  return apiFetch(`/api/groups/${groupId}/join-code`, {
    method: 'POST'
  });
}


/**
 * [F18 추가] 비공개 모임 초대 링크 토큰 조회
 *
 * 모임장 전용
 * GET /api/groups/{groupId}/invite
 *
 * 서버에서 기존 초대 토큰을 조회한다.
 * 인증코드를 새로 발급하거나 변경하지 않는다.
 */
export async function fetchInviteLink(groupId) {
  if (!getAccessToken()) {
    throw new Error('로그인이 필요해요.');
  }

  if (
    !Number.isSafeInteger(Number(groupId)) ||
    Number(groupId) <= 0
  ) {
    throw new Error('올바르지 않은 모임 ID입니다.');
  }

  return apiFetch(`/api/groups/${groupId}/invite`, {
    method: 'GET'
  });
}


/**
 * 참여 회원 전용
 * POST /api/groups/{groupId}/join-code/verify
 *
 * 인증 성공: VERIFIED / ALREADY_VERIFIED
 * 인증 실패: INVALID_CODE / LOCKED 등
 */
export async function verifyJoinCode(groupId, joinCode) {
  if (!getAccessToken()) {
    throw new Error('로그인이 필요해요.');
  }

  if (!Number.isSafeInteger(Number(groupId)) || Number(groupId) <= 0) {
    throw new Error('올바르지 않은 모임 ID입니다.');
  }

  const code = String(joinCode ?? '').trim();

  if (!/^\d{6}$/.test(code)) {
    throw new Error('6자리 숫자 인증코드를 입력해 주세요.');
  }

  return apiFetch(`/api/groups/${groupId}/join-code/verify`, {
    method: 'POST',
    body: JSON.stringify({ joinCode: code })
  });
}


/**
 * F18 - 현재 회원의 비공개 모임 인증 상태 조회
 *
 * GET /api/groups/{groupId}/join-code/verification-state
 */
export async function fetchJoinCodeVerificationState(groupId) {
  if (!getAccessToken()) {
    throw new Error('로그인이 필요해요.');
  }

  const id = Number(groupId);

  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error('올바르지 않은 모임 ID입니다.');
  }

  return apiFetch(
    `/api/groups/${id}/join-code/verification-state`,
    { method: 'GET' }
  );
}


/* ==================================================
   F16 — 모임 가입 신청 (Spring Boot)
   ================================================== */

/**
 * POST /api/groups/{groupId}/join-requests
 *
 * 비공개 초대 가입: INVITE
 * 공개 모임 검색 가입: SEARCH
 *
 * 가입 신청 성공 시 PENDING 반환
 */
export async function requestJoinGroup(groupId, via = 'INVITE') {
  if (!getAccessToken()) {
    throw new Error('로그인이 필요해요.');
  }

  const id = Number(groupId);

  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error('올바르지 않은 모임 ID입니다.');
  }

  if (!['INVITE', 'SEARCH'].includes(via)) {
    throw new Error('올바르지 않은 가입 경로입니다.');
  }

  return apiFetch(`/api/groups/${id}/join-requests`, {
    method: 'POST',
    body: JSON.stringify({ via })
  });
}


/* ==================================================
   F16 — 모임장 가입 신청 관리 (Spring Boot)
   ================================================== */

// 모임 ID 유효성 검사
function validateGroupId(groupId) {
  const id = Number(groupId);

  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error('올바르지 않은 모임 ID입니다.');
  }

  return id;
}

// 가입 신청 ID 유효성 검사
function validateRequestId(requestId) {
  const id = Number(requestId);

  if (!Number.isSafeInteger(id) || id <= 0) {
    throw new Error('올바르지 않은 가입 신청 ID입니다.');
  }

  return id;
}


/**
 * F16: 모임장 전용 참여 회원 목록 조회
 * GET /api/groups/{groupId}/members
 */
export async function fetchGroupMembers(groupId) {
  if (!getAccessToken()) {
    throw new Error('로그인이 필요해요.');
  }

  const id = validateGroupId(groupId);

  const result = await apiFetch(
    `/api/groups/${id}/members`
  );

  if (!Array.isArray(result)) {
    throw new Error(
      '참여 회원 목록 응답 형식이 올바르지 않아요.'
    );
  }

  return result;
}


/**
 * 모임장 전용: 가입 신청 목록 조회
 *
 * GET /api/groups/{groupId}/join-requests
 *
 * 반환:
 * [
 *   {
 *     joinRequestId: 4,
 *     groupId: 13,
 *     memberId: 12,
 *     via: 'INVITE',
 *     status: 'PENDING',
 *     createdAt: '2026-11-23T00:29:15'
 *   }
 * ]
 */
export async function fetchJoinRequests(groupId) {
  if (!getAccessToken()) {
    throw new Error('로그인이 필요해요.');
  }

  const id = validateGroupId(groupId);

  const result = await apiFetch(
    `/api/groups/${id}/join-requests`
  );

  // 서버에서 배열로 반환
  if (Array.isArray(result)) {
    return result;
  }

  // 신청이 한 건이고 객체로 반환된 경우
  if (
    result &&
    typeof result === 'object' &&
    Number.isSafeInteger(Number(result.joinRequestId)) &&
    Number(result.joinRequestId) > 0
  ) {
    return [result];
  }

  // 빈 응답
  if (result == null) {
    return [];
  }

  throw new Error(
    '가입 신청 목록 응답 형식이 올바르지 않아요.'
  );
}

/**
 * 모임장 전용: 가입 신청 승인
 *
 * PATCH /api/groups/{groupId}/join-requests/{requestId}/approve
 */
export async function approveJoinRequest(groupId, requestId) {
  if (!getAccessToken()) {
    throw new Error('로그인이 필요해요.');
  }

  const id = validateGroupId(groupId);
  const rid = validateRequestId(requestId);

  return apiFetch(
    `/api/groups/${id}/join-requests/${rid}/approve`,
    {
      method: 'PATCH'
    }
  );
}

/**
 * 모임장 전용: 가입 신청 거절
 *
 * PATCH /api/groups/{groupId}/join-requests/{requestId}/reject
 */
export async function rejectJoinRequest(groupId, requestId) {
  if (!getAccessToken()) {
    throw new Error('로그인이 필요해요.');
  }

  const id = validateGroupId(groupId);
  const rid = validateRequestId(requestId);

  return apiFetch(
    `/api/groups/${id}/join-requests/${rid}/reject`,
    {
      method: 'PATCH'
    }
  );
}
