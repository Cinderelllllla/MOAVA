/* F01 F02 F03 F04 — 인증·회원. 서버 연동 시 각 함수 본문을 fetch('/api/...')로 교체 */
import { db, save, uid, today, nowText, fail, audit, isAdmin, currentUser, activeJoined, leader, isEnded, reset } from './mock/db';

export async function login(email, password) {
  const m = db.members.find(x => x.email === email && x.password === password && x.status !== 'WITHDRAWN');
  const a = !m && db.admins.find(x => x.email === email && x.password === password);
  if (!m && !a) fail('이메일 또는 비밀번호를 확인해 주세요.');
  db.session = m ? { type: 'MEMBER', id: m.id } : { type: 'ADMIN', id: a.id };
  audit('로그인', null, `${currentUser().name} (${db.session.type})`); save();
  return db.session;
}
export async function logout() { db.session = null; save(); }
export async function signup(f) {
  if (db.members.some(x => x.email === f.email) || db.admins.some(x => x.email === f.email)) fail('이미 등록된 이메일이에요.');
  const m = { id: uid(), name: f.name.trim(), email: f.email.trim(), password: f.password, phone: f.phone.trim(), regionSido: f.regionSido.trim(), regionSigungu: f.regionSigungu.trim(), status: 'ACTIVE', privacyAgreedAt: today(), termsAgreedAt: today() };
  db.members.push(m); db.session = { type: 'MEMBER', id: m.id }; audit('회원가입', null, `${m.name} · 약관·개인정보 동의 ${today()}`); save();
  return db.session;
}
export async function updateAccount(f) {
  const u = currentUser();
  if (db.members.some(x => x.id !== u.id && x.email === f.email)) fail('이미 사용 중인 이메일입니다.');
  for (const k of ['name', 'email', 'phone', 'regionSido', 'regionSigungu']) u[k] = String(f[k]).trim();
  audit('내 정보 수정', null, u.name); save();
}
/** 탈퇴 = 익명화(STATUS WITHDRAWN · EMAIL NULL). 모임장·참여 중 모임 있으면 불가 */
export async function withdrawAccount() {
  if (db.groups.some(g => leader(g) && !isEnded(g))) fail('모임장인 모임이 있습니다. 승계 또는 해산 후 탈퇴할 수 있어요.');
  if (db.groups.some(activeJoined)) fail('참여 중인 모임이 있습니다. 중도 탈퇴 절차를 먼저 완료해 주세요.');
  Object.assign(currentUser(), { status: 'WITHDRAWN', withdrawnAt: nowText(), email: null, phone: '', name: '탈퇴회원' });
  audit('회원 탈퇴', null, '익명화 처리'); db.session = null; save();
}
export async function resetDemo() { reset(); }
export const session = () => db.session;
export { isAdmin, currentUser };
