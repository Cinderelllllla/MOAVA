/* S01b 초대 — /invite?t=토큰 · F18. 비로그인: 환영 화면 / 회원: 서버에서 초대 모임 조회(GET /api/v1/invites/{token}) */
import { useEffect, useState } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { useApp } from '../common/store';
import { groups } from '../api';
import { PageHead, Card } from '../components/ui';
import { LoadState } from '../components/shared';

export default function Invite() {
  const { session, isAdmin } = useApp(); const [sp] = useSearchParams();
  const t = sp.get('t') || '';
  const q = `?t=${encodeURIComponent(t)}`;
  const [invite, setInvite] = useState({ status: 'loading', group: null, error: '' });
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    if (!session || isAdmin) return undefined;
    let alive = true;
    setInvite({ status: 'loading', group: null, error: '' });
    groups.fetchInvite(t)
      .then(group => { if (alive) setInvite({ status: 'ready', group, error: '' }); })
      .catch(e => { if (alive) setInvite({ status: 'error', group: null, error: e.message }); });
    return () => { alive = false; };
  }, [session, isAdmin, t, retry]);

  // 비로그인: 초대 정보 조회는 로그인이 필요하므로 로그인·회원가입으로 안내한 뒤 이 화면으로 돌아온다
  if (!session) {
    if (!t) return <div className="moava-guest-wrap"><div className="moava-guest-card"><h2>초대 링크를 확인해 주세요</h2><p>초대 주소가 올바르지 않아요.</p><Link className="btn soft" to="/">서비스 소개로</Link></div></div>;
    return <div className="moava-guest-wrap"><div className="moava-guest-card"><h1>MOAVA 모임에 초대되었어요!</h1><p className="muted">초대받은 모임을 확인하려면 MOAVA 계정이 필요합니다. 로그인 또는 회원가입 후 이 모임으로 돌아옵니다.</p><div className="actions"><Link className="btn primary" to={'/login' + q}>기존 회원 · 로그인</Link><Link className="btn soft" to={'/signup' + q}>처음이에요 · 회원가입</Link></div></div></div>;
  }
  if (isAdmin) return <Navigate to="/admin" replace />;

  const g = invite.group;
  return <><PageHead kicker="GROUP INVITATION" title="초대받은 계모임" sub="가입 승인 전에는 채팅과 납입 정보에 접근할 수 없습니다." />
    <Card><LoadState status={invite.status} error={invite.error} onRetry={() => setRetry(x => x + 1)} loadingText="초대받은 모임을 확인하는 중이에요.">
      {g && <div className="policy-invite-card"><span className="pill">{g.visibility === 'PRIVATE' ? '🔒 비공개 모임' : '공개 모임'}</span><h2>{g.title}</h2>
        <p>{[g.regionSido, g.regionSigungu].filter(Boolean).join(' ')} · {groups.purposeLabel(g.purpose)}</p>
        {/* TODO(MOA-69): 서버 API 연결 시 준비 중 안내 제거 — 가입 신청·초대코드 인증(groups.joinRequest / verifyInviteCode 목업 참고) */}
        <div className="policy-lock">가입 신청은 아직 준비 중이에요. 모임장에게 직접 문의해 주세요.</div>
        <div className="actions" style={{ marginTop: 12 }}><Link className="btn soft" to="/dashboard">마이 홈</Link></div></div>}
    </LoadState></Card></>;
}
