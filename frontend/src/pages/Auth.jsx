/* S01 로그인 · 회원가입 — /login /signup · F01 F02. 초대 링크(?t=)로 들어오면 인증 후 /invite로 복귀 */
import { useState } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useApp } from '../common/store';
import { auth } from '../api';
import { Input } from '../components/ui';

function AuthCard({ children, invite }) {
  return <div className="auth"><div className="auth-card"><div className="auth-brand">☁ MOAVA<span>TOGETHER, BRIGHTER</span></div><div className="auth-back"><Link to="/">← 서비스 소개로 돌아가기</Link></div>{invite && <p className="info mini">초대받은 모임이 있어요. 인증 후 초대 화면으로 돌아갑니다.</p>}{children}</div></div>;
}
function useAfterAuth() { const nav = useNavigate(); const [sp] = useSearchParams(); const t = sp.get('t'); return s => nav(t && s.type === 'MEMBER' ? `/invite?t=${encodeURIComponent(t)}` : s.type === 'ADMIN' ? '/admin' : '/dashboard', { replace: true }); }

export function Login() {
  const { session, isAdmin, run } = useApp(); const after = useAfterAuth(); const [sp] = useSearchParams();
  const [f, setF] = useState({ email: '', password: '' });
  if (session) return <Navigate to={isAdmin ? '/admin' : '/dashboard'} replace />;
  const submit = async e => { e.preventDefault(); const r = await run(() => auth.login(f.email.trim(), f.password)); if (r.ok) after(r.value); };
  const q = sp.get('t') ? `?t=${encodeURIComponent(sp.get('t'))}` : '';
  return <AuthCard invite={!!sp.get('t')}><h1>다시 만나 반가워요</h1><p className="muted">모아바와 함께 내일을 모아보세요.</p>
    <form onSubmit={submit}><Input label="이메일" name="email" type="email" required autoComplete="username" value={f.email} onChange={e => setF({ ...f, email: e.target.value })} /><Input label="비밀번호" name="password" type="password" required autoComplete="current-password" value={f.password} onChange={e => setF({ ...f, password: e.target.value })} /><button className="btn primary" type="submit">로그인</button></form>
    <div className="info mini" style={{ marginTop: 18 }}>체험 계정<br />일반 회원: moa@example.com / demo1234<br />모임장: leader@example.com / demo1234<br />ADMIN: admin@moava.test / admin1234<br /><b>데모 비밀번호이며 실제 인증 시스템이 아닙니다.</b></div>
    <div className="auth-link">아직 계정이 없나요? <Link className="toggle" to={'/signup' + q}>회원가입</Link></div></AuthCard>;
}

export function Signup() {
  const { session, isAdmin, run } = useApp(); const after = useAfterAuth(); const [sp] = useSearchParams();
  const [f, setF] = useState({ name: '', email: '', password: '', phone: '', regionSido: '서울', regionSigungu: '마포구' });
  if (session) return <Navigate to={isAdmin ? '/admin' : '/dashboard'} replace />;
  const on = k => e => setF({ ...f, [k]: e.target.value });
  const submit = async e => { e.preventDefault(); const r = await run(() => auth.signup(f)); if (r.ok) after(r.value); };
  const q = sp.get('t') ? `?t=${encodeURIComponent(sp.get('t'))}` : '';
  return <AuthCard invite={!!sp.get('t')}><h1>함께 시작해 볼까요?</h1><p className="muted">회원가입 후 누구나 모임을 만들 수 있어요.</p>
    <form onSubmit={submit}><Input label="이름" name="name" required minLength={2} value={f.name} onChange={on('name')} /><Input label="이메일" name="email" type="email" required value={f.email} onChange={on('email')} /><Input label="비밀번호 (8자 이상)" name="password" type="password" required minLength={8} autoComplete="new-password" value={f.password} onChange={on('password')} /><Input label="연락처" name="phone" type="tel" required placeholder="010-0000-0000" pattern="01[016789]-?\d{3,4}-?\d{4}" value={f.phone} onChange={on('phone')} />
      <div className="form-grid"><Input label="활동 지역 · 시/도" name="regionSido" required value={f.regionSido} onChange={on('regionSido')} /><Input label="활동 지역 · 시/군/구" name="regionSigungu" required value={f.regionSigungu} onChange={on('regionSigungu')} /></div>
      <label className="checkline"><input type="checkbox" required /> <span>서비스 이용약관에 동의합니다. (필수)</span></label><label className="checkline"><input type="checkbox" required /> <span>개인정보 수집·이용에 동의합니다. (필수 · 연락처는 암호화 저장)</span></label>
      <button className="btn primary">회원가입</button></form>
    <div className="auth-link">이미 계정이 있나요? <Link className="toggle" to={'/login' + q}>로그인</Link></div></AuthCard>;
}
