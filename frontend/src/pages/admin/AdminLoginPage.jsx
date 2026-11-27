/* 관리자 전용 로그인 화면 (/admin/login · POST /api/auth/admin/login) */
import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { useApp } from '../../common/store';
import { auth } from '../../api';
import { Input } from '../../components/ui';
import { Logo } from '../../components/Icon';

export default function AdminLoginPage() {
  const { session, isAdmin, run } = useApp();
  const navigate = useNavigate();

  const [form, setForm] = useState({ email: '', password: '' });
  const [submitting, setSubmitting] = useState(false);

  if (session) {
    return <Navigate to={isAdmin ? '/admin' : '/dashboard'} replace />;
  }

  const handleSubmit = async e => {
    e.preventDefault();
    if (submitting) return;
    setSubmitting(true);
    try {
      const r = await run(() =>
        auth.loginAdminWithServer(form.email.trim(), form.password)
      );
      if (r.ok) {
        navigate('/admin', { replace: true });
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="auth-logo"><Logo size={34} /></span>MOAVA
          <span>ADMIN CONSOLE</span>
        </div>

        <div className="auth-back">
          <Link to="/">← 서비스 소개로 돌아가기</Link>
        </div>

        <h1>관리자 콘솔 로그인</h1>
        <p className="muted">플랫폼 관리자 전용 계정으로 로그인합니다.</p>

        <form onSubmit={handleSubmit}>
          <Input
            label="관리자 이메일"
            name="adminEmail"
            type="email"
            required
            autoComplete="username"
            placeholder="admin@moava.com"
            value={form.email}
            onChange={e => setForm(prev => ({ ...prev, email: e.target.value }))}
          />

          <Input
            label="비밀번호"
            name="adminPassword"
            type="password"
            required
            autoComplete="current-password"
            value={form.password}
            onChange={e => setForm(prev => ({ ...prev, password: e.target.value }))}
          />

          <button className="btn primary" type="submit" disabled={submitting}>
            {submitting ? '로그인 중...' : '관리자 로그인'}
          </button>
        </form>

        <p className="info mini" style={{ marginTop: 16 }}>
          플랫폼 운영 권한이 부여된 활성 관리자 계정만 접근할 수 있습니다.
        </p>

        <div className="auth-link">
          일반 회원이신가요?{' '}
          <Link className="toggle" to="/login">
            회원 로그인
          </Link>
        </div>
      </div>
    </div>
  );
}
