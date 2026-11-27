/* 가상은행 브랜드 로그인 · 1원 인증번호 4자리 확인 전용 페이지 (/openbanking/bank-auth?session=...) */
import { useCallback, useEffect, useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { useApp } from '../common/store';
import { openbanking } from '../api';
import Icon from '../components/Icon';

/** 은행 기관코드별 브랜드 컬러 및 공식명 매핑 */
const BANK_THEME = {
  '0004': { bg: 'linear-gradient(135deg, #544F4B, #756B62)', accent: '#FFBC00', badge: 'KB', label: 'KB국민은행', textOnAccent: '#2B241B' },
  '0088': { bg: 'linear-gradient(135deg, #0039C6, #2B6BFF)', accent: '#0046FF', badge: 'SH', label: '신한은행', textOnAccent: '#FFFFFF' },
  '0020': { bg: 'linear-gradient(135deg, #00589E, #2088D8)', accent: '#0067AC', badge: 'WR', label: '우리은행', textOnAccent: '#FFFFFF' },
  '0081': { bg: 'linear-gradient(135deg, #007E7A, #00A8A2)', accent: '#009591', badge: 'HN', label: '하나은행', textOnAccent: '#FFFFFF' },
  '0011': { bg: 'linear-gradient(135deg, #007E43, #00B05B)', accent: '#00A859', badge: 'NH', label: 'NH농협은행', textOnAccent: '#FFFFFF' },
  '0003': { bg: 'linear-gradient(135deg, #003A7B, #0060C7)', accent: '#004A98', badge: 'IBK', label: 'IBK기업은행', textOnAccent: '#FFFFFF' },
  '0090': { bg: 'linear-gradient(135deg, #222222, #3A3A3A)', accent: '#FFEB00', badge: 'KK', label: '카카오뱅크', textOnAccent: '#1E1E1E' },
  '0092': { bg: 'linear-gradient(135deg, #0050DB, #3182F6)', accent: '#0064FF', badge: 'TS', label: '토스뱅크', textOnAccent: '#FFFFFF' },
  '0089': { bg: 'linear-gradient(135deg, #0B1B48, #1C3785)', accent: '#1E3A8A', badge: 'K', label: '케이뱅크', textOnAccent: '#FFFFFF' },
  '0023': { bg: 'linear-gradient(135deg, #005A87, #0089CC)', accent: '#0072AA', badge: 'SC', label: 'SC제일은행', textOnAccent: '#FFFFFF' },
  '0007': { bg: 'linear-gradient(135deg, #0059A8, #1C88E5)', accent: '#0066BE', badge: 'SH', label: 'Sh수협은행', textOnAccent: '#FFFFFF' },
  '0002': { bg: 'linear-gradient(135deg, #004785, #0072CE)', accent: '#005BAC', badge: 'KDB', label: 'KDB산업은행', textOnAccent: '#FFFFFF' },
  '0027': { bg: 'linear-gradient(135deg, #003B70, #0066B2)', accent: '#004B8D', badge: 'CITI', label: '한국씨티은행', textOnAccent: '#FFFFFF' },
  '0031': { bg: 'linear-gradient(135deg, #008D72, #00B894)', accent: '#00A182', badge: 'iM', label: 'iM뱅크', textOnAccent: '#FFFFFF' },
  '0032': { bg: 'linear-gradient(135deg, #B71C2B, #DF2D3E)', accent: '#CB2030', badge: 'BNK', label: 'BNK부산은행', textOnAccent: '#FFFFFF' },
  '0039': { bg: 'linear-gradient(135deg, #A91927, #D42C3C)', accent: '#C01E2E', badge: 'BNK', label: 'BNK경남은행', textOnAccent: '#FFFFFF' },
  '0034': { bg: 'linear-gradient(135deg, #0055A5, #1D7BD2)', accent: '#0062BC', badge: 'KJ', label: '광주은행', textOnAccent: '#FFFFFF' },
  '0037': { bg: 'linear-gradient(135deg, #00509B, #1D75C8)', accent: '#005DB5', badge: 'JB', label: '전북은행', textOnAccent: '#FFFFFF' },
  '0035': { bg: 'linear-gradient(135deg, #004EA2, #247CE0)', accent: '#005CC5', badge: 'JJ', label: '제주은행', textOnAccent: '#FFFFFF' },
  '0045': { bg: 'linear-gradient(135deg, #005AA7, #1A82D8)', accent: '#0068C1', badge: 'MG', label: 'MG새마을금고', textOnAccent: '#FFFFFF' },
  '0048': { bg: 'linear-gradient(135deg, #005C9E, #1682CE)', accent: '#006AB5', badge: 'CU', label: '신협', textOnAccent: '#FFFFFF' },
  '0071': { bg: 'linear-gradient(135deg, #C92A2A, #E84545)', accent: '#D63031', badge: 'POST', label: '우체국예금', textOnAccent: '#FFFFFF' },
};

const DEFAULT_THEME = {
  bg: 'linear-gradient(135deg, #1269B9, #328FDB)',
  accent: '#237CCC',
  badge: 'BK',
  label: '모의은행',
  textOnAccent: '#FFFFFF',
};

function fmtCountdown(sec) {
  const s = Math.max(0, Number(sec) || 0);
  const m = Math.floor(s / 60);
  const r = s % 60;
  return `${String(m).padStart(2, '0')}:${String(r).padStart(2, '0')}`;
}

export default function OpenBankingBankAuth() {
  const { session: appSession, isAdmin, run, toast } = useApp();
  const [params] = useSearchParams();
  const nav = useNavigate();
  const sessionToken = params.get('session') || '';

  const [authSession, setAuthSession] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [remainingSec, setRemainingSec] = useState(0);

  const [authCode, setAuthCode] = useState('');
  const [agreed, setAgreed] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);

  const loadSession = useCallback(async () => {
    if (!sessionToken) {
      setErrorMsg('인증 세션 정보가 없습니다. 오픈뱅킹 화면에서 다시 시작해 주세요.');
      setLoading(false);
      return;
    }
    setLoading(true);
    setErrorMsg('');
    try {
      const data = await openbanking.fetchQuickConnectSession(sessionToken);
      setAuthSession(data);
      setRemainingSec(data?.expiresInSeconds ?? 0);
    } catch (e) {
      setErrorMsg(e?.message || '인증 세션을 불러오지 못했어요.');
    } finally {
      setLoading(false);
    }
  }, [sessionToken]);

  useEffect(() => {
    if (appSession && !isAdmin) {
      loadSession();
    }
  }, [appSession, isAdmin, loadSession]);

  useEffect(() => {
    if (remainingSec <= 0) return undefined;
    const timer = setInterval(() => {
      setRemainingSec(prev => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, [remainingSec > 0]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!appSession) return <Navigate to="/login" replace />;
  if (isAdmin) return <Navigate to="/admin" replace />;

  const theme = BANK_THEME[authSession?.organization] || DEFAULT_THEME;
  const bankTitle = authSession?.bankName || theme.label;

  const handleResend = async () => {
    if (!sessionToken) return;
    setResending(true);
    const r = await run(
      () => openbanking.resendQuickConnectCode(sessionToken),
      '1원 인증번호 4자리를 새로 발송했어요.',
    );
    setResending(false);
    if (r.ok) {
      setAuthSession(r.value);
      setRemainingSec(r.value?.expiresInSeconds ?? 300);
      setAuthCode('');
    }
  };

  const handleSubmit = async e => {
    e.preventDefault();
    const cleaned = authCode.replace(/[^0-9]/g, '');
    if (cleaned.length !== 4) {
      toast('1원 입금자명 뒤 숫자 4자리를 입력해 주세요.');
      return;
    }
    if (!agreed) {
      toast('오픈뱅킹 계좌조회 및 출금이체 정보제공 동의에 체크해 주세요.');
      return;
    }
    setSubmitting(true);
    const r = await run(
      () => openbanking.verifyQuickConnect(sessionToken, { authCode: cleaned, agreed: true }),
      res => res.message || `${bankTitle} 계좌 연결이 완료되었어요!`,
    );
    setSubmitting(false);
    if (r.ok) {
      nav('/openbanking?tab=ledger', { replace: true });
    }
  };

  return (
    <div
      className="auth"
      style={{
        minHeight: '100vh',
        padding: '28px 16px',
        background: 'linear-gradient(160deg, #edf5fc 0%, #f8fbff 100%)',
      }}
    >
      <div style={{ width: 'min(520px, 100%)' }}>
        {/* 은행 전용 헤더 배너 */}
        <div
          style={{
            background: theme.bg,
            color: '#fff',
            borderRadius: '24px 24px 0 0',
            padding: '24px 26px',
            boxShadow: '0 14px 32px rgba(24, 57, 87, 0.14)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <span
                style={{
                  display: 'inline-grid',
                  placeItems: 'center',
                  width: 44,
                  height: 44,
                  borderRadius: 14,
                  background: theme.accent,
                  color: theme.textOnAccent,
                  fontWeight: 900,
                  fontSize: 15,
                  letterSpacing: '-0.5px',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.2)',
                }}
              >
                {theme.badge}
              </span>
              <div>
                <div style={{ fontSize: 11, opacity: 0.85, letterSpacing: '1.2px', fontWeight: 700 }}>
                  OPEN BANKING · 1원 본인인증
                </div>
                <div style={{ fontSize: 21, fontWeight: 800, marginTop: 2 }}>
                  {bankTitle} 계좌 인증
                </div>
              </div>
            </div>
            <span
              style={{
                background: 'rgba(255,255,255,0.18)',
                border: '1px solid rgba(255,255,255,0.32)',
                borderRadius: 999,
                padding: '5px 10px',
                fontSize: 11,
                fontWeight: 800,
              }}
            >
              <Icon name="lock" size={12} /> 보안 연결
            </span>
          </div>
          <p style={{ margin: '12px 0 0', fontSize: 12.5, opacity: 0.9, lineHeight: 1.6 }}>
            <strong>MOAVA</strong>의 오픈뱅킹 계좌 연결 요청입니다. 아래 계좌로 입금된 1원 입금자명 뒤 <strong>숫자 4자리</strong>를 확인해 입력해 주세요.
          </p>
        </div>

        {/* 본문 카드 */}
        <div
          style={{
            background: '#fff',
            borderRadius: '0 0 24px 24px',
            border: '1px solid #d7e7f6',
            borderTop: 0,
            padding: '26px',
            boxShadow: '0 18px 44px rgba(33, 93, 149, 0.12)',
          }}
        >
          {loading ? (
            <div className="empty">은행 인증 세션을 확인하는 중이에요…</div>
          ) : errorMsg || !authSession ? (
            <div>
              <div className="warning" style={{ marginBottom: 16 }}>
                {errorMsg || '유효하지 않은 인증 요청입니다.'}
              </div>
              <button
                type="button"
                className="btn primary"
                style={{ width: '100%' }}
                onClick={() => nav('/openbanking')}
              >
                MOAVA 오픈뱅킹으로 돌아가기
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit}>
              {/* 요청 계좌 정보 */}
              <div
                style={{
                  background: '#f7fbff',
                  border: '1px solid #dcebf7',
                  borderRadius: 16,
                  padding: '14px 16px',
                  marginBottom: 16,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                  <span className="muted mini">연결 요청 은행 / 계좌</span>
                  <strong>{authSession.bankName} {authSession.accountMasked}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                  <span className="muted mini">예금주(소유주)</span>
                  <strong>{authSession.holderName}</strong>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span className="muted mini">본인 확인 전화번호</span>
                  <strong>{authSession.phoneMasked}</strong>
                </div>
              </div>

              {/* 가상은행 실시간 1원 입금 알림 푸시 연출 */}
              <div
                style={{
                  background: '#fffbeb',
                  border: '1px solid #f7dc8d',
                  borderRadius: 16,
                  padding: '14px 16px',
                  marginBottom: 18,
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                  <span style={{ fontSize: 12, fontWeight: 800, color: '#8a5a00' }}>
                    <Icon name="bell" size={14} /> {authSession.bankName} 가상 입금 알림 (방금 전)
                  </span>
                  <span style={{ fontSize: 11, fontWeight: 700, color: '#a16b00' }}>+1원 입금</span>
                </div>
                <div style={{ fontSize: 14, color: '#3b2c0a', lineHeight: 1.6 }}>
                  계좌(<strong>{authSession.accountMasked}</strong>)로 <strong>1원</strong>이 입금되었습니다.
                  <br />
                  입금자명:{' '}
                  <span
                    style={{
                      display: 'inline-block',
                      background: '#fff',
                      border: '1px dashed #d69e2e',
                      borderRadius: 8,
                      padding: '2px 10px',
                      fontWeight: 900,
                      fontSize: 16,
                      letterSpacing: '1px',
                      color: '#183957',
                    }}
                  >
                    {authSession.depositMemo}
                  </span>
                </div>
              </div>

              {/* 인증번호 4자리 입력 */}
              <div className="field" style={{ marginBottom: 14 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <label htmlFor="quickAuthCode">입금자명 뒤 숫자 4자리 입력</label>
                  <span
                    style={{
                      fontSize: 12,
                      fontWeight: 800,
                      color: remainingSec <= 30 ? '#b24d68' : '#287cbf',
                    }}
                  >
                    남은 시간 {fmtCountdown(remainingSec)}
                  </span>
                </div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <input
                    id="quickAuthCode"
                    name="authCode"
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    maxLength={4}
                    placeholder="4자리 숫자 (예: 1234)"
                    value={authCode}
                    onChange={e => setAuthCode(e.target.value.replace(/[^0-9]/g, '').slice(0, 4))}
                    style={{
                      fontSize: 22,
                      fontWeight: 800,
                      letterSpacing: '8px',
                      textAlign: 'center',
                      flex: 1,
                    }}
                    required
                  />
                  <button
                    type="button"
                    className="btn soft"
                    disabled={resending}
                    onClick={handleResend}
                    style={{ whiteSpace: 'nowrap' }}
                  >
                    {resending ? '발송 중…' : '재발송'}
                  </button>
                </div>
              </div>

              {/* 확인 정보 · 비저장 안내 */}
              <div
                style={{
                  background: '#f8fafc',
                  border: '1px solid #e2ecf5',
                  borderRadius: 12,
                  padding: '10px 12px',
                  marginBottom: 14,
                  fontSize: 12,
                  lineHeight: 1.65,
                  color: '#415a72',
                }}
              >
                <div>
                  <strong>MOAVA가 확인하는 것:</strong> 은행명 · 계좌번호 · 예금주명 · 1원 인증번호
                </div>
                <div>
                  <strong>MOAVA가 하지 않는 것:</strong> 은행 비밀번호 저장 · 실제 금융 출금 (시연용 가상은행)
                </div>
              </div>

              {/* 오픈뱅킹 동의 */}
              <label className="checkline" style={{ margin: '12px 0 18px', fontSize: 13 }}>
                <input
                  type="checkbox"
                  checked={agreed}
                  onChange={e => setAgreed(e.target.checked)}
                />
                <span>
                  <strong>[필수]</strong> MOAVA 가상은행 계좌 조회·모임 계좌 소유 인증 및 모임 회비 출금이체(<code>login inquiry transfer</code>)에 동의합니다.
                </span>
              </label>

              {/* 제출 / 취소 */}
              <div style={{ display: 'grid', gap: 10 }}>
                <button
                  type="submit"
                  className="btn primary"
                  disabled={submitting || remainingSec <= 0 || authCode.length !== 4}
                  style={{ width: '100%', padding: '13px 16px', fontSize: 15 }}
                >
                  {submitting
                    ? '인증 및 계좌 연결 중…'
                    : remainingSec <= 0
                      ? '유효시간 만료 — 재발송을 눌러주세요'
                      : '인증번호 확인 및 MOAVA 계좌 연결 완료'}
                </button>
                <button
                  type="button"
                  className="btn"
                  style={{ width: '100%' }}
                  onClick={() => nav('/openbanking')}
                >
                  취소하고 MOAVA로 돌아가기
                </button>
              </div>

              <p className="muted mini" style={{ textAlign: 'center', marginTop: 14, marginBottom: 0 }}>
                본 화면은 KFTC v2.0 모의 오픈뱅킹 시연용 가상은행 인증 페이지이며 실제 출금은 발생하지 않습니다.
              </p>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
