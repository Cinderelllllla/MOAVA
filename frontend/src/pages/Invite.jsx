/* S01b 초대 — /invite?t=토큰 · F18. 비로그인: 환영 화면 / 회원: 초대 카드(인증코드·가입 신청) */
import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useApp } from '../common/store';
import { groups, joined, me, db } from '../api';
import { PageHead, Card } from '../components/ui';
import { won } from '../common/format';

export default function Invite() {
  const { session, isAdmin, run, setUi } = useApp(); const [sp] = useSearchParams(); const nav = useNavigate();
  const t = sp.get('t') || '', g = groups.byToken(t); const [code, setCode] = useState('');
  const q = `?t=${encodeURIComponent(t)}`;
  if (!session) {
    if (!g) return <div className="moava-guest-wrap"><div className="moava-guest-card"><h2>초대 링크를 확인해 주세요</h2><p>유효하지 않거나 만료된 초대 링크입니다.</p><Link className="btn soft" to="/">서비스 소개로</Link></div></div>;
    return <div className="moava-guest-wrap"><div className="moava-guest-card"><span className="pill">{g.visibility === 'PRIVATE' ? '🔒 비공개 모임' : '공개 모임'}</span><h1>MOAVA 모임에 초대되었어요!</h1><h2>{g.title}</h2><p>{g.region} · 월 {won(g.amount)} · {g.months}개월 · 매월 {g.payDay}일 납입</p><p className="muted">가입하려면 MOAVA 계정이 필요합니다. 로그인 또는 회원가입 후 이 모임으로 돌아옵니다.</p><div className="actions"><Link className="btn primary" to={'/login' + q}>기존 회원 · 로그인</Link><Link className="btn soft" to={'/signup' + q}>처음이에요 · 회원가입</Link></div><p className="muted mini">{g.visibility === 'PRIVATE' ? '로그인 후 6자리 초대코드를 입력하고 가입을 신청합니다.' : '로그인 후 가입 신청을 진행합니다.'} 채팅은 모임장 승인 후 이용할 수 있습니다.</p></div></div>;
  }
  if (isAdmin) return <Navigate to="/admin" replace />;
  if (!g) return <>{<PageHead kicker="INVITE" title="초대 링크가 유효하지 않습니다." sub="공유받은 주소를 다시 확인해 주세요."><Link className="btn soft" to="/dashboard">마이 홈</Link></PageHead>}</>;
  const isMember = joined(g), req = g.joinRequests.find(r => r.userId === me() && r.status === '대기'), priv = g.visibility === 'PRIVATE';
  const blocked = db.sanctions.some(s => s.userId === me() && s.active), closed = g.status !== '모집 중' || g.dissolution || g.started || g.members.length >= g.max;
  let action;
  if (isMember) action = <button className="btn primary" onClick={() => { setUi({ selected: g.id }); nav('/operations'); }}>운영 홈으로 이동</button>;
  else if (req) action = <div className="moava-invite-success"><h3>✓ 가입 신청이 접수되었어요</h3><p>모임장 승인 대기 중입니다. 승인 후 모임과 채팅을 이용할 수 있어요.</p><span className="pill">승인 대기</span></div>;
  else if (blocked || closed) action = <div className="policy-lock">{blocked ? '가입 제한 중인 계정은 신청할 수 없습니다.' : '모집이 종료되었거나 해산된 모임입니다.'}</div>;
  else if (priv) action = <div className="moava-code-box"><h3>🔐 비공개 초대 인증</h3><p className="muted mini">모임장에게 받은 6자리 공통 코드를 입력하면 가입 신청이 자동 접수됩니다.</p><input className="moava-code-field" inputMode="numeric" maxLength={6} placeholder="6자리 인증코드" value={code} onChange={e => setCode(e.target.value)} /><div className="actions"><button className="btn primary" onClick={() => run(() => groups.verifyInviteCode(g.id, code.trim()), '가입 신청이 접수되었습니다.')}>인증하고 가입 신청</button></div><p className="moava-invite-help">5회 실패 시 15분 제한 · 가입 확정은 모임장 승인 후</p></div>;
  else action = <button className="btn primary" onClick={() => run(() => groups.joinRequest(g.id), '가입 신청이 접수되었습니다.')}>가입 신청</button>;
  return <><PageHead kicker="GROUP INVITATION" title="초대받은 계모임" sub="가입 승인 전에는 채팅과 납입 정보에 접근할 수 없습니다." />
    <Card><div className="policy-invite-card"><span className="pill">{priv ? '🔒 비공개 모임' : '공개 모임'}</span><h2>{g.title}</h2><p>{g.description}</p><p>{g.region} · 월 {won(g.amount)} · {g.months}개월 · 매월 {g.payDay}일 납입 · {g.members.length}/{g.max}명</p><p><b>운영 규칙</b> · {g.rules}</p><div className="actions">{action}<Link className="btn soft" to="/dashboard">마이 홈</Link></div></div></Card></>;
}
