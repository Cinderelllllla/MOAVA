/* F22 F23 F24 F26 — 납입 증빙(승인·보완 요청·재제출), 참조번호, 미납 감지, 채팅 */
import * as D from './mock/db';
const { db, save, fail } = D;

export const listProofs = groupId => db.proofs.filter(x => x.groupId === groupId);
export const allProofs = () => db.proofs;
export const overdueCases = () => db.overdueCases;

export async function addProof(groupId, f, images, resubmitId = null) {
  const g = D.group(groupId); if (!g || !D.joined(g)) fail('참여 회원만 증빙을 등록할 수 있습니다.');
  if (!images?.length) fail('이미지 또는 PDF를 먼저 선택해 주세요.');
  const period = String(f.period), round = Number(f.round), amount = Number(f.amount), date = String(f.date);
  if (!/^\d{4}-\d{2}$/.test(period) || !Number.isInteger(round) || round < 1 || round > (g.months || 36)) fail('납입 귀속 월과 회차를 확인해 주세요.');
  if (amount !== g.amount) fail(`모임 약정 납입액 ${g.amount}만 원과 일치해야 합니다.`);
  if (images.some(x => db.proofs.some(p => (p.images || []).includes(x)))) fail('이미 등록된 증빙 파일입니다.');
  const redo = resubmitId ? db.proofs.find(p => p.id === resubmitId && p.status === '보완 요청') : null;
  if (resubmitId && !redo) fail('보완 요청 상태의 증빙만 재제출할 수 있습니다.');
  if (redo) {
    // 재제출: 귀속 월·회차·약정액은 원래 값으로 고정, 파일과 실제 납입일만 갱신 (F23)
    Object.assign(redo, { date, images, status: '확인 대기', resubmitCount: (redo.resubmitCount || 0) + 1, resubmittedAt: D.nowText() });
    D.audit('증빙 재제출', groupId, `${D.proofPeriod(redo)} ${redo.round}회차`); D.notice(g.ownerId, '보완 증빙 재제출', `${D.currentUser().name}님이 보완 증빙을 다시 제출했습니다.`);
  } else {
    if (db.proofs.some(x => x.groupId === groupId && x.memberId === D.me() && D.proofPeriod(x) === period && Number(x.round) === round && x.status !== '반려')) fail('해당 모임·귀속 월·회차에 이미 제출한 증빙이 있습니다.');
    db.proofs.push({ id: D.uid(), groupId, memberId: D.me(), amount, date, period, round, images, status: '확인 대기', reviewMemo: '', resubmitCount: 0, ref: null, refHistory: [], createdAt: D.nowText() });
    D.notice(g.ownerId, '증빙 확인 필요', `${D.currentUser().name}님이 거래 증빙을 등록했습니다.`); D.audit('증빙 등록', groupId, `${period} ${round}회차 · 납입 확인 대기`);
  }
  try { save(); } catch (e) { fail('브라우저 저장 용량이 부족해요. 더 작은 이미지를 사용해 주세요.'); }
}
export async function approveProof(id) {
  const x = db.proofs.find(p => p.id === id), g = D.group(x?.groupId); if (!D.leader(g)) fail('해당 모임장만 승인할 수 있어요.');
  x.status = '납입 확인'; x.reviewedBy = D.me(); x.reviewedAt = D.nowText();
  db.overdueCases.filter(c => c.groupId === g.id && c.memberId === x.memberId && c.period === D.proofPeriod(x) && c.status !== 'RESOLVED').forEach(c => { c.status = 'RESOLVED'; c.resolvedAt = D.nowText(); });
  D.notice(x.memberId, '납입 증빙 승인', `${g.title}: ${D.proofPeriod(x)}월분 납입이 확인됐습니다.`); D.audit('납입 승인', g.id, `${D.memberName(x.memberId)} · ${D.proofPeriod(x)}`); save();
}
export async function requestRevision(id, memo) {
  const x = db.proofs.find(p => p.id === id), g = D.group(x?.groupId); if (!D.leader(g)) fail('해당 모임장만 처리할 수 있어요.'); if (!memo?.trim()) fail('보완 사유를 입력해 주세요.');
  x.status = '보완 요청'; x.reviewMemo = memo.trim(); x.reviewedBy = D.me(); x.reviewedAt = D.nowText();
  D.notice(x.memberId, '증빙 보완 요청', `${g.title}: ${D.proofPeriod(x)}월분 증빙 보완이 필요합니다 — ${memo.trim()}`); D.audit('증빙 보완 요청', g.id, `${D.memberName(x.memberId)} · ${memo.trim()}`); save();
}

/* ---- 채팅 (F34) ---- */
export const messages = groupId => db.messages.filter(x => x.groupId === groupId);
export async function sendChat(groupId, text) {
  const g = D.group(groupId); if (!text?.trim() || !D.joined(g)) fail('전송할 수 없습니다.'); if (g.restrictions?.[D.me()]) fail('미납 제재로 채팅 전송이 제한되었습니다.');
  db.messages.push({ id: D.uid(), groupId, memberId: D.me(), text: text.trim(), time: new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' }) }); save();
}

/* ---- 미납 자동 감지 (배치 대체): 납입일 + 5일 경과 & 납입 확인 없음 ---- */
export async function detectOverdue() {
  const now = new Date(), period = D.currentPeriod(); let added = 0;
  db.groups.filter(g => !D.isEnded(g) && g.members.length > 1).forEach(g => {
    const due = new Date(now.getFullYear(), now.getMonth(), Math.min(g.payDay || 5, 28)), days = Math.floor((now - due) / 86400000);
    if (days < 5) return;
    g.members.forEach(id => {
      if (D.paidThisPeriod(g, id, period) || db.overdueCases.some(c => c.groupId === g.id && c.memberId === id && c.period === period)) return;
      db.overdueCases.push({ id: D.uid(), groupId: g.id, memberId: id, period, days, detectedAt: D.nowText(), status: 'DETECTED', actions: [] }); added++;
    });
  });
  if (added) { D.noticeAdmin('미납 5일차 감지', `${added}건의 미납이 감지되었습니다. 미납 관리에서 조치해 주세요.`); save(); }
  return added;
}
