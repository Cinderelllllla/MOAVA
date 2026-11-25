
/*
 * S01 로그인 · 회원가입
 * /login /signup · F01 F02
 * Spring Boot 인증 API 연결
 * 로그인 / 회원가입 성공 후 페이지 이동
 * 기존 MOAVA 디자인 유지
 */

import { useState } from 'react';
import {
  Link,
  Navigate,
  useNavigate,
  useSearchParams
} from 'react-router-dom';

import { useApp } from '../common/store';
import { auth } from '../api';
import { Input } from '../components/ui';
import { Logo } from '../components/Icon';

// ========================================
// 공통 인증 화면
// ========================================

function AuthCard({ children, invite }) {
  return (
    <div className="auth">
      <div className="auth-card">
        <div className="auth-brand">
          <span className="auth-logo"><Logo size={34} /></span>MOAVA
          <span>TOGETHER, BRIGHTER</span>
        </div>

        <div className="auth-back">
          <Link to="/">
            ← 서비스 소개로 돌아가기
          </Link>
        </div>

        {invite && (
          <p className="info mini">
            초대받은 모임이 있어요.
            인증 후 초대 화면으로 돌아갑니다.
          </p>
        )}

        {children}
      </div>
    </div>
  );
}

// ========================================
// 인증 성공 후 이동
// ========================================

function useAfterAuth() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const inviteToken = searchParams.get('t');

  return session => {
    if (inviteToken && session.type === 'MEMBER') {
      navigate(
        `/invite?t=${encodeURIComponent(inviteToken)}`,
        { replace: true }
      );
      return;
    }

    if (session.type === 'ADMIN') {
      navigate('/admin', { replace: true });
      return;
    }

    navigate('/dashboard', { replace: true });
  };
}

// ========================================
// 로그인
// ========================================

export function Login() {
  const { session, isAdmin, run } = useApp();
  const after = useAfterAuth();
  const [searchParams] = useSearchParams();

  const [f, setF] = useState({
    email: '',
    password: ''
  });

  const [submitting, setSubmitting] = useState(false);

  const inviteToken = searchParams.get('t');

  const q = inviteToken
    ? `?t=${encodeURIComponent(inviteToken)}`
    : '';

  const submit = async e => {
    e.preventDefault();

    if (submitting) return;

    setSubmitting(true);

    try {
      const r = await run(() =>
        auth.loginWithServer(f.email.trim(), f.password)
      );

      if (r.ok) {
        // auth.js에서 JWT 저장 및 React 세션 연결 완료
        after(r.value);
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (session) {
    return (
      <Navigate
        to={
          inviteToken && !isAdmin
            ? `/invite?t=${encodeURIComponent(inviteToken)}`
            : isAdmin
              ? '/admin'
              : '/dashboard'
        }
        replace
      />
    );
  }

  return (
    <AuthCard invite={!!inviteToken}>
      <h1>다시 만나 반가워요</h1>

      <p className="muted">
        모아바와 함께 내일을 모아보세요.
      </p>

      <form onSubmit={submit}>
        <Input
          label="이메일"
          name="email"
          type="email"
          required
          autoComplete="username"
          value={f.email}
          onChange={e =>
            setF(prev => ({
              ...prev,
              email: e.target.value
            }))
          }
        />

        <Input
          label="비밀번호"
          name="password"
          type="password"
          required
          autoComplete="current-password"
          value={f.password}
          onChange={e =>
            setF(prev => ({
              ...prev,
              password: e.target.value
            }))
          }
        />

        <button
          className="btn primary"
          type="submit"
          disabled={submitting}
        >
          {submitting ? '로그인 중...' : '로그인'}
        </button>
      </form>

      <div className="auth-link">
        아직 계정이 없나요?{' '}
        <Link
          className="toggle"
          to={'/signup' + q}
        >
          회원가입
        </Link>
      </div>
    </AuthCard>
  );
}

// ========================================
// 회원가입
// ========================================

export function Signup() {
  const { session, isAdmin, run } = useApp();
  const after = useAfterAuth();
  const [searchParams] = useSearchParams();

  const [f, setF] = useState({
    name: '',
    email: '',
    password: '',
    phone: '',
    regionSido: '',
    regionSigungu: '',
    termsAgreed: false,
    privacyAgreed: false
  });

  const [submitting, setSubmitting] = useState(false);

  const inviteToken = searchParams.get('t');

  const q = inviteToken
    ? `?t=${encodeURIComponent(inviteToken)}`
    : '';

  const on = key => e => {
    setF(prev => ({
      ...prev,
      [key]: e.target.value
    }));
  };

  const submit = async e => {
    e.preventDefault();

    if (submitting) return;

    if (!f.termsAgreed || !f.privacyAgreed) {
      return;
    }

    setSubmitting(true);

    try {
      const r = await run(() =>
        auth.signupWithServer(f)
      );

      if (r.ok) {
        // 회원가입 성공 → JWT 저장 → React 세션 연결
        after(r.value);
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (session) {
    return (
      <Navigate
        to={
          inviteToken && !isAdmin
            ? `/invite?t=${encodeURIComponent(inviteToken)}`
            : isAdmin
              ? '/admin'
              : '/dashboard'
        }
        replace
      />
    );
  }

  return (
    <AuthCard invite={!!inviteToken}>
      <h1>함께 시작해 볼까요?</h1>

      <p className="muted">
        회원가입 후 누구나 모임을 만들 수 있어요.
      </p>

      <form onSubmit={submit}>
        <Input
          label="이름"
          name="name"
          required
          minLength={2}
          value={f.name}
          onChange={on('name')}
        />

        <Input
          label="이메일"
          name="email"
          type="email"
          required
          value={f.email}
          onChange={on('email')}
        />

        <Input
          label="비밀번호 (8자 이상)"
          name="password"
          type="password"
          required
          minLength={8}
          autoComplete="new-password"
          value={f.password}
          onChange={on('password')}
        />

        <Input
          label="연락처"
          name="phone"
          type="tel"
          required
          placeholder="010-0000-0000"
          pattern="01[016789]-?\d{3,4}-?\d{4}"
          value={f.phone}
          onChange={on('phone')}
        />

        <div className="form-grid">
          <Input
            label="활동 지역 · 시/도"
            name="regionSido"
            placeholder="예: 서울"
            required
            value={f.regionSido}
            onChange={on('regionSido')}
          />

          <Input
            label="활동 지역 · 시/군/구"
            name="regionSigungu"
            placeholder="예: 마포구"
            required
            value={f.regionSigungu}
            onChange={on('regionSigungu')}
          />
        </div>

        <label className="checkline">
          <input
            type="checkbox"
            required
            checked={f.termsAgreed}
            onChange={e =>
              setF(prev => ({
                ...prev,
                termsAgreed: e.target.checked
              }))
            }
          />

          <span>
            서비스 이용약관에 동의합니다. (필수)
          </span>
        </label>

        <label className="checkline">
          <input
            type="checkbox"
            required
            checked={f.privacyAgreed}
            onChange={e =>
              setF(prev => ({
                ...prev,
                privacyAgreed: e.target.checked
              }))
            }
          />

          <span>
            개인정보 수집·이용에 동의합니다.
            (필수 · 연락처는 암호화 저장)
          </span>
        </label>

        <button
          className="btn primary"
          type="submit"
          disabled={submitting}
        >
          {submitting ? '회원가입 중...' : '회원가입'}
        </button>
      </form>

      <div className="auth-link">
        이미 계정이 있나요?{' '}
        <Link
          className="toggle"
          to={'/login' + q}
        >
          로그인
        </Link>
      </div>
    </AuthCard>
  );
}
