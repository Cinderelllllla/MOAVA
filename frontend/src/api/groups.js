/* F11~F19 F20 F28 F33 F36 — 모임 조회·생성·가입·운영 */
import * as D from './mock/db';
const { db, save, fail } = D;

/* ---- 조회 ---- */
export const listGroups = () => db.groups;
export const getGroup = id => D.group(id);
export const myGroups = () => db.groups.filter(D.activeJoined);
export const endedGroups = () => db.groups.filter(g => D.joined(g) && D.isEnded(g));
export const byToken = t => db.groups.find(g => g.inviteToken === t) || null;
export const recommended = (rec) => db.groups.filter(g => D.groupVisible(g) && g.status === '모집 중' && g.amount <= Math.max(rec + 10, 20)).slice(0, 3);
export function healthScore(g) {
  const warnings = Object.values(g.warnings || {}).reduce((a, b) => a + b, 0);
  const rejects = db.proofs.filter(x => x.groupId === g.id && x.status === '보완 요청').length;
  const overdue = db.overdueCases.filter(x => x.groupId === g.id && x.status !== 'RESOLVED').length;
  return Math.max(0, Math.min(100, Math.round(95 - warnings * 9 - rejects * 6 - overdue * 5 - (g.dissolution ? 12 : 0))));
}
export function inviteLink(g) { if (!g.inviteToken) { g.inviteToken = D.inviteToken(); save(); } return `${location.origin}/invite?t=${encodeURIComponent(g.inviteToken)}`; }
export function opsTodos(g) {
  const l = D.leader(g), m = D.me(), t = { joins: 0, proofs: 0, consents: 0, orders: 0 };
  if (l) { t.joins = g.joinRequests.filter(r => r.status === '대기').length; t.proofs = db.proofs.filter(p => p.groupId === g.id && p.status === '확인 대기').length; t.orders = db.orderRequests.filter(r => r.groupId === g.id && ['신청', '동의 요청'].includes(r.status)).length; }
  db.orderRequests.filter(r => r.groupId === g.id && r.status === '동의 요청' && r.affected?.includes(m) && !r.consents.includes(m)).forEach(() => t.consents++);
  g.withdrawals.filter(w => w.status === '정산 동의 대기' && w.userId !== m && !w.consents.includes(m)).forEach(() => t.consents++);
  if (g.transfer && !g.transfer.consents.includes(m)) t.consents++;
  if (g.pendingAccount && !g.pendingAccount.consents.includes(m)) t.consents++;
  return t;
}
export const groupLogs = g => [...db.history.filter(h => h.groupId === g.id).map(h => ({ when: h.date, type: h.type, desc: h.description })), ...(g.log || []).map(x => ({ when: x.at, type: '정책', desc: x.msg }))].sort((a, b) => String(b.when).localeCompare(String(a.when)));

/* ---- 생성 (F15 F19) ---- */
export async function createGroup(f) {
  const amount = Number(f.amount), payDay = Number(f.payDay), max = Number(f.max), months = Number(f.months);
  if (!f.title?.trim() || !f.rules?.trim() || !f.description?.trim() || !f.rulesTitle?.trim()) fail('모임 필수 정보를 입력해 주세요.');
  if (amount < 5 || amount % 5) fail('월 납입액은 5만 원 단위입니다.');
  if (payDay < 1 || payDay > 28) fail('납입일은 1~28일 사이여야 합니다.');
  if (max < 2 || max > 30) fail('인원은 2~30명입니다.');
  if (months < 2 || months > 36) fail('기간은 2~36개월입니다.');
  const accountNo = String(f.accountNo || '').replace(/\D/g, ''); if (!f.bank?.trim() || accountNo.length < 8) fail('모임 계좌를 확인해 주세요.');
  const u = D.currentUser();
  const g = D.groupDefaults({ id: D.uid(), title: f.title.trim(), region: f.region.trim(), purpose: f.purpose, amount, payDay, max, months, rate: Number(f.rate || 3), ownerId: D.me(), members: [D.me()], rulesTitle: f.rulesTitle.trim(), rules: f.rules.trim(), description: f.description.trim(), visibility: f.visibility, overduePolicy: f.overduePolicy, inviteToken: D.inviteToken(), account: { bank: f.bank.trim(), holder: u.name, accountNo, verifyStatus: 'PENDING', keyVer: 1 } });
  if (g.visibility === 'PRIVATE') D.ensureJoinCode(g);
  db.groups.unshift(g); D.groupLog(g, '모임장 계좌 최초 등록 (인증 대기)'); D.audit('모임 생성', g.id, `${u.name} 모임장 권한 부여 · 납입일 ${payDay}일`);
  D.noticeAdmin('계좌 인증 확인 필요', `${g.title}의 모임 계좌가 등록되었습니다. 소유 확인 처리가 필요합니다.`); save(); return g;
}

/* ---- 가입 (F16 F18 F20) ---- */
export async function joinRequest(id) {
  const g = D.group(id); if (!g) fail('모임을 찾을 수 없어요.');
  if (D.sanctioned(D.me())) fail('플랫폼 가입 제한 상태입니다. 관리자에게 문의하세요.');
  if (g.status !== '모집 중' || g.started || g.dissolution || g.members.length >= g.max) fail('가입이 마감된 모임입니다.');
  if (D.joined(g)) fail('이미 참여 중인 모임입니다.');
  if (g.joinRequests.some(x => x.userId === D.me() && x.status === '대기')) fail('이미 신청했어요.');
  if (g.visibility === 'PRIVATE' && !D.ensureJoinCode(g).verified[String(D.me())]) fail('비공개 모임은 초대 링크와 인증코드로만 가입 신청할 수 있어요.');
  g.joinRequests.push({ id: D.uid(), userId: D.me(), status: '대기', codeVerified: g.visibility === 'PRIVATE' });
  D.notice(g.ownerId, '가입 검토 필요', `${g.title}에 가입 신청이 접수되었습니다.`); D.audit('가입 신청', g.id, `${D.currentUser().name} 신청`); save();
}
export async function verifyInviteCode(id, value) {
  const g = D.group(id); if (!g || g.visibility !== 'PRIVATE') fail('비공개 모임이 아닙니다.');
  const d = D.ensureJoinCode(g), key = String(D.me()), a = d.attempts[key] || { count: 0, until: 0 };
  if (Date.now() < a.until) fail('인증 시도 제한 중입니다. 잠시 후 다시 시도해 주세요.');
  if (!/^\d{6}$/.test(value)) fail('6자리 숫자 인증코드를 입력해 주세요.');
  if (value !== String(d.code)) { a.count++; if (a.count >= 5) { a.count = 0; a.until = Date.now() + 15 * 60000; } d.attempts[key] = a; save(); fail(a.until > Date.now() ? '5회 실패하여 15분 동안 인증이 제한됩니다.' : '인증코드가 올바르지 않습니다.'); }
  d.verified[key] = true; delete d.attempts[key]; D.groupLog(g, '인증코드 확인'); save();
  await joinRequest(id);
}
export async function approveJoin(id, rid) {
  const g = D.group(id), r = g?.joinRequests.find(x => x.id === rid);
  if (!D.leader(g) || !r || r.status !== '대기') fail('승인 권한이 없습니다.');
  if (g.started || g.members.length >= g.max || g.status !== '모집 중') fail('가입이 마감되었습니다.');
  if (D.sanctioned(r.userId)) fail('플랫폼 가입 제한 회원입니다.');
  r.status = '승인'; if (!g.members.includes(r.userId)) g.members.push(r.userId);
  D.notice(r.userId, '계모임 가입 승인', `${g.title} 가입이 승인되었습니다.`); D.audit('가입 승인', id, D.memberName(r.userId)); save();
}
export async function rejectJoin(id, rid) {
  const g = D.group(id), r = g?.joinRequests.find(x => x.id === rid); if (!D.leader(g) || !r || r.status !== '대기') fail('처리할 수 없습니다.');
  r.status = '거절'; D.notice(r.userId, '계모임 가입 거절', `${g.title} 가입 신청이 거절되었습니다.`); D.audit('가입 거절', id, '모임장 처리'); save();
}
/* ---- 수령 완료 (F28) ---- */
export async function markReceived(id, memberId) {
  const g = D.group(id); if (!D.leader(g) || !g.members.includes(memberId)) fail('권한이 없습니다.');
  if (!g.orderConfirmed) fail('수령 순서가 확정된 후에 표시할 수 있어요.');
  g.payoutReceived.push(memberId); D.notice(memberId, '곗돈 수령 완료 표시', `${g.title}: 수령 완료로 기록되었습니다.`); D.audit('수령 완료 기록', id, D.memberName(memberId)); save();
}
/* ---- 공개 전환 · 초대 (F18) ---- */
export async function togglePrivate(id) {
  const g = D.group(id); if (!D.leader(g)) fail('모임장만 공개 설정을 바꿀 수 있어요.'); if (D.isEnded(g)) fail('종료된 모임은 설정을 바꿀 수 없어요.');
  const toPrivate = g.visibility !== 'PRIVATE'; g.visibility = toPrivate ? 'PRIVATE' : 'PUBLIC'; if (toPrivate) D.ensureJoinCode(g);
  D.groupLog(g, toPrivate ? '비공개 모임으로 전환' : '공개 모임으로 전환'); D.audit(toPrivate ? '비공개 전환' : '공개 전환', g.id, `${D.currentUser().name} 처리`);
  g.members.forEach(x => D.notice(x, '모임 공개 설정 변경', `${g.title}: ${toPrivate ? '비공개' : '공개'} 모임으로 전환되었습니다.`)); save(); return toPrivate;
}
export async function rotateInviteCode(id) { const g = D.group(id); if (!D.leader(g) || g.visibility !== 'PRIVATE') fail('비공개 모임장만 변경할 수 있습니다.'); g.joinCode = { code: D.inviteDigits(), attempts: {}, verified: {} }; D.groupLog(g, '비공개 초대 인증코드 변경'); save(); }
/* ---- 모임 계좌 변경 (F33) ---- */
export async function accountChange(id, bank, accountNo) {
  const g = D.group(id); if (!D.leader(g)) fail('모임장만 계좌를 변경할 수 있습니다.'); if (g.pendingAccount) fail('이미 검토 중인 계좌 변경 신청이 있습니다.');
  if (!bank?.trim() || !accountNo?.trim()) fail('은행과 계좌번호를 입력해 주세요.');
  g.pendingAccount = { bank: bank.trim(), accountNo: accountNo.replace(/\D/g, ''), holder: D.currentUser().name, requestedAt: D.nowText(), consents: [], verifyStatus: 'PENDING' };
  D.groupLog(g, '계좌 변경 신청 · 재인증 및 회원 동의 대기'); g.members.forEach(x => D.notice(x, '송금 보류 안내', `${g.title}에서 계좌 변경 신청이 접수됐습니다. 확인 전 송금하지 마세요.`)); D.noticeAdmin('계좌 변경 확인 필요', `${g.title}의 모임 계좌 변경이 신청되었습니다.`); save();
}
export async function consentAccount(id) { const g = D.group(id), p = g?.pendingAccount; if (!D.joined(g) || !p) fail('동의 대상이 아닙니다.'); if (!p.consents.includes(D.me())) p.consents.push(D.me()); D.groupLog(g, '계좌 변경 검토 동의'); save(); }
export async function confirmAccount(id) {
  const g = D.group(id), p = g?.pendingAccount; if (!D.leader(g) || !p) fail('권한이 없습니다.');
  if (p.verifyStatus !== 'VERIFIED') fail('관리자의 계좌 소유 확인이 먼저 필요합니다.'); if (g.members.some(x => !p.consents.includes(x))) fail('모든 참여 회원의 동의가 필요합니다.');
  g.account = { bank: p.bank, holder: p.holder, accountNo: p.accountNo, verifyStatus: 'VERIFIED', keyVer: 1 }; g.pendingAccount = null;
  D.groupLog(g, '모든 회원 동의 후 계좌 변경 확정'); g.members.forEach(x => D.notice(x, '모임 계좌 변경 확정', `${g.title}의 계좌 변경이 확정되었습니다. 송금 전 직접 재확인하세요.`)); save();
}
/* ---- 승계 (F36: 승계 대상 지정 → 수락 + 전원 동의 → 확정) ---- */
export async function setSuccessor(id, to) { const g = D.group(id); if (!D.leader(g)) fail('모임장만 지정할 수 있습니다.'); if (!to || to === g.ownerId || !g.members.includes(to)) fail('다른 참여 회원을 선택해 주세요.'); g.successor = to; D.groupLog(g, `${D.memberName(to)} 승계 대상 지정`); save(); }
export async function requestTransfer(id) {
  const g = D.group(id); if (!D.leader(g)) fail('현재 모임장만 승계를 요청할 수 있습니다.'); if (!g.successor || !g.members.includes(g.successor)) fail('먼저 승계 대상 회원을 지정해 주세요.'); if (g.transfer) fail('승계 동의가 이미 진행 중입니다.');
  g.transfer = { from: g.ownerId, to: g.successor, consents: [], accepted: false }; D.groupLog(g, '모임장 권한 승계 동의 요청'); g.members.forEach(x => D.notice(x, '모임장 승계 동의 요청', `${g.title}의 모임장 변경에 대한 동의를 요청합니다.`)); save();
}
export async function acceptTransfer(id) { const g = D.group(id); if (!g?.transfer || g.transfer.to !== D.me()) fail('승계 대상이 아닙니다.'); g.transfer.accepted = true; D.groupLog(g, '승계 대상 수락'); save(); }
export async function consentTransfer(id) { const g = D.group(id); if (!g?.transfer || !D.joined(g)) fail('동의 대상이 아닙니다.'); if (!g.transfer.consents.includes(D.me())) g.transfer.consents.push(D.me()); D.groupLog(g, '승계 동의'); save(); }
export async function confirmTransfer(id) {
  const g = D.group(id), t = g?.transfer; if (!t || !D.leader(g)) fail('권한이 없습니다.');
  if (!t.accepted || g.members.some(x => !t.consents.includes(x))) fail('승계 대상 수락과 모든 회원 동의가 필요합니다.');
  const old = g.ownerId; g.ownerId = t.to; g.successor = null; g.transfer = null; if (!g.members.includes(old)) g.members.push(old); if (g.account) g.account.verifyStatus = 'REVERIFY_REQUIRED';
  D.groupLog(g, `${D.memberName(old)} → ${D.memberName(g.ownerId)} 권한 승계 · 기존 모임장은 Member 유지 · 계좌 재인증 필요`);
  g.members.forEach(x => D.notice(x, '모임장 변경', `${g.title} 모임장이 변경되었습니다. 신규 계좌 재인증 전 송금하지 마세요.`)); D.audit('모임장 승계', id, `${D.memberName(old)} → ${D.memberName(g.ownerId)}`); save();
}
/* ---- 규칙 · 경고 · 해산 (F24 F25 F31) ---- */
export async function editRule(id, title, body) { const g = D.group(id); if (!D.leader(g)) fail('모임장만 수정할 수 있습니다.'); if (!title?.trim() || !body?.trim()) fail('제목과 내용을 입력해 주세요.'); g.rulesTitle = title.trim(); g.rules = body.trim(); D.audit('운영 규칙 등록', id, g.rulesTitle); g.members.forEach(x => D.notice(x, '운영 규칙 업데이트', `${g.title}: ${g.rulesTitle}`)); save(); }
export async function warnMember(id, x, reason) {
  const g = D.group(id); if (!D.leader(g)) fail('권한이 없습니다.'); if (!x || !reason?.trim()) fail('대상과 사유를 입력해 주세요.'); if (!g.members.includes(x)) fail('현재 참여 회원만 경고할 수 있습니다.');
  if ((g.warnings[x] || 0) >= 2) fail('경고는 모임별 최대 2회입니다. 강제 탈퇴 검토 절차를 진행해 주세요.');
  g.warnings[x] = (g.warnings[x] || 0) + 1; D.notice(x, '모임 경고 알림', `${g.title}: 경고 ${g.warnings[x]}회 · ${reason}`); D.audit('경고', id, `${D.memberName(x)} · ${reason}`); save();
}
export async function forceMember(id, x, reason) {
  const g = D.group(id); if (!D.leader(g)) fail('권한이 없습니다.'); if (!x || !reason?.trim()) fail('사유를 입력해 주세요.'); if (!g.members.includes(x)) fail('현재 참여 중인 회원만 검토할 수 있습니다.');
  if ((g.warnings[x] || 0) < 2) fail('누적 경고 2회 이상부터 강제 탈퇴 검토가 가능합니다.');
  g.members = g.members.filter(a => a !== x); D.notice(x, '강제 탈퇴 처리', `${g.title}: ${reason}. 이의 신청은 고객센터로 문의하세요.`); D.audit('강제 탈퇴', id, `${D.memberName(x)} · ${reason}`); save();
}
export async function requestDissolve(id, reason) { const g = D.group(id); if (!D.leader(g)) fail('해산 신청은 모임장만 가능합니다.'); if (g.dissolution) fail('이미 신청했습니다.'); if (!reason?.trim()) fail('사유를 입력해 주세요.'); g.dissolution = { reason, status: '관리자 검토 대기', requestedAt: D.today() }; D.noticeAdmin('해산 승인 검토 필요', `${g.title} 해산 신청이 접수되었습니다.`); D.audit('해산 신청', id, reason); save(); }
