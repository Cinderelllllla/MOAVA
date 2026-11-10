/* F27 수령 순서(신청 건별 동의 · AI 제안) · F21 F30 탈퇴·정산(정산 금액 입력 → 전원 동의) */
import * as D from './mock/db';
const { db, save, fail } = D;

/* ---- AI 제안 회차 (가설 H2): 연체 위험 높은 회원을 앞 회차에. 모델 연동 전에는 경고·미납·납입 이력 점수 ---- */
export function aiRiskRank(g) {
  return g.members.map(id => ({ id, score: (g.warnings?.[id] || 0) * 3 + db.overdueCases.filter(c => c.groupId === g.id && c.memberId === id).length * 2 + (D.paidAny(g, id) ? 0 : 1) + (id === g.ownerId ? -1 : 0) })).sort((a, b) => b.score - a.score);
}
export const aiSuggestRound = (g, memberId) => Math.max(1, aiRiskRank(g).findIndex(x => x.id === memberId) + 1);
export const orderRequests = g => db.orderRequests.filter(x => x.groupId === g.id);

export async function submitOrder(id, round, reason) {
  const g = D.group(id); if (!g || !D.joined(g)) fail('참여 회원만 신청할 수 있습니다.'); if (!reason?.trim()) fail('신청 사유를 입력해 주세요.');
  const active = db.orderRequests.find(x => x.groupId === id && x.userId === D.me() && ['신청', '동의 요청'].includes(x.status));
  if (active) {
    if (active.status === '동의 요청') fail('이미 동의 요청 중입니다. 변경이 필요하면 모임장에게 문의하세요.');
    active.round = round; active.reason = reason; active.consents = []; active.affected = [];
    D.audit('수령 순서 신청 수정', id, `${round}회차 · ${reason}`); D.notice(g.ownerId, '수령 순서 신청 수정', `${D.currentUser().name}님이 신청 내용을 수정했습니다.`); save(); return '기존 신청을 수정했어요.';
  }
  db.orderRequests.push({ id: D.uid(), groupId: id, userId: D.me(), round, reason, status: '신청', consents: [], affected: [], aiSuggestedRound: aiSuggestRound(g, D.me()) });
  D.notice(g.ownerId, '수령 순서 검토 필요', `${D.currentUser().name}님이 ${round}회차를 신청했습니다.`); D.audit('수령 순서 신청', id, `${round}회차 · ${reason}`); save(); return '모임장에게 순서 신청을 전송했어요.';
}
export async function requestOrderConsent(rid) {
  const r = db.orderRequests.find(x => x.id === rid), g = D.group(r?.groupId); if (!D.leader(g)) fail('모임장만 요청할 수 있어요.');
  r.status = '동의 요청'; r.affected = g.members.filter(x => x !== r.userId); r.consents = [];
  r.affected.forEach(x => D.notice(x, '수령 순서 변경 동의 요청', `${g.title}: ${r.round}회차 신청의 변경 동의가 필요합니다.`)); D.audit('수령 순서 동의 요청', g.id, `${r.round}회차`); save();
}
export async function consentOrder(rid) { const r = db.orderRequests.find(x => x.id === rid); if (!r?.affected.includes(D.me())) fail('동의 대상자가 아닙니다.'); if (!r.consents.includes(D.me())) r.consents.push(D.me()); D.audit('수령 순서 동의', r.groupId, D.currentUser().name); save(); }
export async function confirmOrder(rid) {
  const r = db.orderRequests.find(x => x.id === rid), g = D.group(r?.groupId); if (!D.leader(g)) fail('모임장만 확정할 수 있어요.');
  if (r.affected.some(x => !r.consents.includes(x))) fail('영향받는 회원 전원의 동의가 필요해요.');
  r.status = '확정'; g.orderConfirmed = true; g.order = g.order.filter(o => o.userId !== r.userId); g.order.push({ userId: r.userId, round: r.round });
  g.members.forEach(x => D.notice(x, '수령 순서 확정', `${g.title}: ${r.round}회차 신청이 확정되었습니다.`)); D.audit('수령 순서 확정', g.id, `${r.round}회차 · 동의 확인`); save();
}

/* ---- 탈퇴 · 정산 ---- */
export async function submitWithdrawal(id, reason, special = false) {
  const g = D.group(id); if (!D.joined(g) || D.leader(g)) fail('일반 참여 회원만 신청할 수 있어요. 모임장은 먼저 승계를 진행하세요.');
  if (g.payoutReceived.includes(D.me()) && !special) fail('곗돈 수령 완료자는 일반 탈퇴가 제한됩니다.');
  if (g.withdrawals.some(w => w.userId === D.me() && !['완료', '반려'].includes(w.status))) fail('이미 진행 중인 신청이 있습니다.');
  if (!reason?.trim()) fail('사유를 입력해 주세요.');
  g.withdrawals.push({ id: D.uid(), userId: D.me(), reason, status: '정산안 작성 대기', special, consents: [], settlementAmount: null, settlementMemo: '', reconfirm: 0, requestedAt: D.nowText() });
  D.notice(g.ownerId, '탈퇴 검토 필요', `${D.currentUser().name}님의 탈퇴 신청이 접수되었습니다. 정산안을 작성해 주세요.`); D.audit('탈퇴 신청', id, reason); save();
}
export async function reconfirmWithdrawal(id) { const g = D.group(id), w = g.withdrawals.find(x => x.userId === D.me() && !['완료', '반려'].includes(x.status)); if (!w) fail('신청 내역이 없습니다.'); w.reconfirm++; D.notice(g.ownerId, '탈퇴 재확인 요청', `${D.currentUser().name}님이 탈퇴 신청 재확인을 요청했습니다.`); save(); }
export const paidTotal = (g, memberId) => db.proofs.filter(p => p.groupId === g.id && p.memberId === memberId && p.status === '납입 확인').reduce((a, p) => a + p.amount, 0);
export async function writeSettlement(id, wid, amount, memo) {
  const g = D.group(id), w = g?.withdrawals.find(x => x.id === wid); if (!D.leader(g) || !w || w.status !== '정산안 작성 대기') fail('정산안 작성 대기 상태에서만 입력할 수 있어요.');
  const n = Number(amount); if (!Number.isFinite(n)) fail('금액을 확인해 주세요.');
  w.settlementAmount = n; w.settlementMemo = (memo || '').trim(); w.status = '정산 동의 대기'; w.consents = [];
  g.members.filter(x => x !== w.userId).forEach(x => D.notice(x, '정산안 동의 요청', `${g.title}: ${D.memberName(w.userId)}님 탈퇴 정산안(${n}만 원)에 대한 공식 동의가 필요합니다.`));
  D.notice(w.userId, '정산안 안내', `${g.title}: 정산 금액 ${n}만 원으로 정산안이 작성되었습니다.`); D.audit('정산안 작성', id, `${D.memberName(w.userId)} · ${n}만 원`); save();
}
export async function consentWithdrawal(id, wid) { const g = D.group(id), w = g.withdrawals.find(x => x.id === wid); if (!D.joined(g) || w.userId === D.me() || w.status !== '정산 동의 대기') fail('동의 대상이 아닙니다.'); if (!w.consents.includes(D.me())) w.consents.push(D.me()); D.audit('정산안 동의', id, `${D.currentUser().name} 공식 동의`); save(); }
export async function approveWithdrawal(id, wid) {
  const g = D.group(id), w = g.withdrawals.find(x => x.id === wid); if (!D.leader(g)) fail('모임장만 승인할 수 있어요.'); if (w.status !== '정산 동의 대기') fail('정산안을 먼저 작성해 주세요.');
  if (g.members.filter(x => x !== w.userId).some(x => !w.consents.includes(x))) fail('필요한 공식 동의가 완료되지 않았어요.');
  w.status = '완료'; w.completedAt = D.nowText(); g.members = g.members.filter(x => x !== w.userId);
  D.notice(w.userId, '탈퇴 처리 완료', `${g.title}: 정산 확인 후 탈퇴가 처리되었습니다.`); D.audit('탈퇴 승인', id, `정산 ${w.settlementAmount}만 원 · 동의 확인`); save();
}
