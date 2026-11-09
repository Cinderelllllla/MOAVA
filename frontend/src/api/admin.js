/* F26 F32 F37 F39 F40 F41 — 관리자: 해산 승인 · 제재 · 참조번호 · 미납 조치 · 계좌 확인 · 신고 처리 · 알림 */
import * as D from './mock/db';
const { db, save, fail } = D;
const needAdmin = () => { if (!D.isAdmin()) fail('관리자만 처리할 수 있습니다.'); };

export const members = () => db.members;
export const history = () => db.history;
export const sanctions = () => db.sanctions;
export const reports = () => db.reports;

/* ---- 해산 (F32) ---- */
export async function approveDissolve(id) {
  needAdmin(); const g = D.group(id); if (g?.dissolution?.status !== '관리자 검토 대기') fail('검토 대기 상태가 아닙니다.');
  if (db.proofs.some(x => x.groupId === id && x.status !== '납입 확인')) fail('확인 대기·보완 요청 증빙이 있어 승인할 수 없습니다.');
  g.dissolution.status = '승인 완료'; g.dissolution.approvedAt = D.nowText(); g.status = '해산 완료';
  g.members.forEach(x => D.notice(x, '계모임 해산 완료', `${g.title}의 해산이 승인되었습니다.`)); D.audit('해산 승인', id, 'ADMIN 최종 승인 · 종료 기록 보관'); save();
}
export async function rejectDissolve(id, reason) { needAdmin(); const g = D.group(id); if (!g?.dissolution) fail('신청이 없습니다.'); if (!reason?.trim()) fail('반려 사유를 입력해 주세요.'); g.dissolution.status = '반려'; g.dissolution.rejectReason = reason.trim(); D.notice(g.ownerId, '해산 신청 반려', `${g.title}: ${reason.trim()}`); D.audit('해산 반려', id, reason.trim()); save(); }
/* ---- 제재 (F39) ---- */
export async function setSanction(id, reason) {
  needAdmin(); if (!id) fail('가입 제한할 회원을 선택해 주세요.'); if (!reason?.trim()) fail('제재 사유를 입력해 주세요.'); if (D.sanctioned(id)) fail('이미 가입 제한 중인 회원입니다.');
  db.sanctions.push({ id: D.uid(), userId: id, type: 'BAN', reason, active: true, date: D.today(), adminId: db.session.id }); D.notice(id, '플랫폼 가입 제한 안내', reason); D.audit('플랫폼 가입 제한', null, `${D.memberName(id)} · ${reason}`); save();
}
export async function releaseSanction(id) { needAdmin(); const s = db.sanctions.find(x => x.userId === id && x.active); if (!s) fail('현재 가입 제한 중인 회원이 아닙니다.'); s.active = false; s.releasedAt = D.nowText(); D.notice(id, '가입 제한 해제', '관리자가 가입 제한을 해제했습니다.'); D.audit('가입 제한 해제', null, `${D.memberName(id)} · 기존 사유: ${s.reason}`); save(); }
/* ---- 참조번호 (F24) ---- */
export const nextRef = () => `MOAVA-${String(db.refs + 1).padStart(5, '0')}`;
export async function assignRef(id, ref) {
  needAdmin(); const p = db.proofs.find(x => x.id === id); if (!p || p.ref) fail('이미 지정된 참조번호는 정정 기능을 이용하세요.'); if (p.status !== '납입 확인') fail('모임장의 납입 확인 후 지정할 수 있습니다.');
  if (!ref?.trim()) fail('참조번호를 입력해 주세요.'); if (db.proofs.some(x => x.id !== id && x.ref === ref.trim())) fail('이미 사용 중인 참조번호입니다.');
  p.ref = ref.trim(); db.refs++; D.audit('참조번호 지정', p.groupId, p.ref); D.notice(p.memberId, '거래 참조번호 지정', `${D.group(p.groupId)?.title}: ${p.ref}`); save();
}
export async function correctRef(id, ref, reason) {
  needAdmin(); const p = db.proofs.find(x => x.id === id); if (!p?.ref) fail('지정된 참조번호가 없습니다.'); if (!ref?.trim() || ref.trim() === p.ref) fail('변경할 참조번호를 입력해 주세요.');
  if (db.proofs.some(x => x.id !== id && x.ref === ref.trim())) fail('이미 사용 중인 참조번호입니다.'); if (!reason?.trim()) fail('정정 사유가 필요합니다.');
  (p.refHistory ||= []).push({ from: p.ref, to: ref.trim(), reason: reason.trim(), at: D.nowText(), adminId: db.session.id }); p.ref = ref.trim(); D.audit('참조번호 정정', p.groupId, `${p.refHistory.at(-1).from} → ${p.ref} · ${reason.trim()}`); save();
}
/* ---- 미납 조치 (F26) ---- */
export async function overdueAction(cid, act) {
  needAdmin(); const c = db.overdueCases.find(x => x.id === cid), g = D.group(c?.groupId); if (!c || !g) fail('감지 건을 찾을 수 없습니다.');
  const id = c.memberId, name = D.memberName(id); g.restrictions ||= {};
  if (act === 'warn') { if (c.status !== 'RESTRICTED') c.status = 'WARNED'; D.notice(id, '미납 5일차 경고', `${g.title}의 ${c.period}월분 미납 상태를 확인해 주세요.`); D.audit('미납 경고 알림', g.id, `${name} · ${c.period}`); D.groupLog(g, `${name} 미납 5일차 경고 알림`); }
  if (act === 'restrict' && !(g.overduePolicy || '').includes('채팅 제한')) fail("이 모임의 미납 대응 규칙은 '5일차 알림'만 선택되어 채팅 제한을 적용할 수 없습니다.");
  if (act === 'restrict') { g.restrictions[id] = true; c.status = 'RESTRICTED'; D.notice(id, '채팅 이용 제한', `${g.title}에서 미납 관련 채팅 제한이 적용되었습니다.`); D.audit('미납 채팅 제한', g.id, name); D.groupLog(g, `${name} 채팅 제한`); }
  if (act === 'release') { delete g.restrictions[id]; c.status = 'WARNED'; D.notice(id, '채팅 제한 해제', `${g.title}의 채팅 제한이 해제되었습니다.`); D.audit('미납 채팅 제한 해제', g.id, name); D.groupLog(g, `${name} 채팅 제한 해제`); }
  if (act === 'order') { if (c.status !== 'RESTRICTED') c.status = 'REVIEW'; D.notice(id, '수령 순서 조정 검토', `${g.title}의 수령 순서 후순위 조정이 검토 중입니다.`); D.notice(g.ownerId, '수령 후순위 검토 요청', `${name}님의 미납으로 관리자가 수령 후순위 검토를 기록했습니다.`); D.audit('미납 수령 후순위 검토', g.id, `${name} (자동 변경 없음)`); }
  c.actions.push({ act, at: D.nowText(), adminId: db.session.id }); save();
}
/* ---- 계좌 소유 확인 (F33, 1원 인증 미연동 → 관리자 처리) ---- */
export async function verifyAccount(id, which) {
  needAdmin(); const g = D.group(id); const target = which === 'pending' ? g?.pendingAccount : g?.account; if (!target) fail('계좌 정보가 없습니다.');
  target.verifyStatus = 'VERIFIED'; target.verifiedAt = D.nowText(); D.groupLog(g, `관리자 계좌 소유 확인 처리 (${which === 'pending' ? '변경 계좌' : '등록 계좌'})`);
  D.notice(g.ownerId, '계좌 소유 확인 완료', `${g.title}의 ${which === 'pending' ? '변경 신청 계좌' : '모임 계좌'} 소유 확인이 완료되었습니다.`); D.audit('계좌 소유 확인', g.id, 'ADMIN 처리'); save();
}
/* ---- 시연용 회원 (F41) ---- */
export async function seedDemoMembers() {
  needAdmin(); const names = ['테스트가온', '테스트나래', '테스트다온', '테스트라온', '테스트마루']; let n = 0;
  names.forEach((name, i) => { const email = `demo.member${i + 3}@moava.test`; if (db.members.some(m => m.email === email || m.name === name)) return; db.members.push({ id: D.uid() + i, name, email, password: 'demo1234', phone: '010-0000-000' + i, regionSido: '서울', regionSigungu: '마포구', status: 'ACTIVE', privacyAgreedAt: D.today() }); n++; });
  save(); return n;
}
/* ---- 고객센터 신고 (F38 F40) ---- */
export async function submitReport(groupId, category, detail) {
  const g = D.group(groupId); if (!detail?.trim()) fail('상세 내용을 입력해 주세요.');
  db.reports.push({ id: D.uid(), groupId: g?.id || null, memberId: D.me(), category, detail: detail.trim(), status: '접수', created: D.nowText(), history: [] });
  if (g) D.groupLog(g, '고객센터 신고 접수 (관리자 열람)'); D.noticeAdmin('고객센터 신고 접수', `${category} · ${g?.title || '모임 미지정'}`); D.audit('고객센터 신고', g?.id || null, category); save();
}
export async function changeReportStatus(id, next, reason) {
  needAdmin(); const r = db.reports.find(x => x.id === id); if (!r || r.status === next) return; if (!reason?.trim()) fail('처리 메모가 필요합니다.');
  (r.history ||= []).push({ from: r.status, to: next, reason: reason.trim(), date: D.nowText(), adminId: db.session.id }); r.status = next; r.updated = D.nowText();
  if (r.memberId) D.notice(r.memberId, '신고 처리 상태 변경', `${r.category}: ${next}`); D.audit('고객센터 신고 처리', r.groupId, `${r.category} · ${next} · ${reason.trim()}`); save();
}
/* ---- 알림 (F29 F35) ---- */
export const myNotices = () => db.notices.filter(n => !n.deleted && (D.isAdmin() ? n.adminId === db.session.id : n.memberId === D.me()));
export async function readNotice(id) { const n = myNotices().find(x => x.id === id); if (n) n.read = true; save(); }
export async function readAllNotices() { myNotices().forEach(x => x.read = true); save(); }
export async function deleteNotices(ids) { if (!ids.length) fail('삭제할 알림을 선택해 주세요.'); myNotices().filter(n => ids.includes(n.id)).forEach(n => n.deleted = true); save(); }
