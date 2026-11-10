/* 공통 껍데기: 사이드바(접이식 메뉴) · 탑바(알림 배지) · 푸터 · 권한 가드 */
import { useEffect, useState } from 'react';
import { NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { auth, admin as adminApi, proofs } from '../api';

export const MENU = [
  ['/dashboard', '◈', '마이 홈'], ['/journey', '✧', '맞춤 추천 시작'], ['/finance', '◉', 'AI 금융 분석'], ['/finance/monthly', '▤', '월별 금융 데이터'],
  ['/groups', '⌖', '지역 계모임 찾기 · 지도'], ['/simulator', '◷', '계모임 시뮬레이터'], ['/my-groups', '☁', '내 계모임'], ['/groups/new', '✦', '계모임 만들기'],
  ['/operations', '⚙', '운영 홈'], ['/procedures', '≡', '모임 절차'], ['/chat', '☏', '모임별 채팅'], ['/evidence', '▣', '납입 증빙'], ['/support', '☏', '고객센터 · 신고'],
];
export const ADMIN_MENU = [
  ['/admin', '◈', '관리자 대시보드'], ['/admin/groups', '▤', '계모임 관리 · 이력'], ['/admin/sanctions', '⚑', '신고 · 제재 관리'], ['/admin/dissolve', '◇', '해산 승인'],
  ['/admin/refs', '▣', '거래 참조번호 관리'], ['/admin/overdue', '◷', '미납 관리'], ['/admin/support', '☏', '고객센터 · 신고 처리'], ['/admin/notifications', '♧', '관리자 알림'],
];
const GROUPS = [{ key: 'ai', title: 'AI', icon: '✧', pages: ['/finance', '/simulator'] }, { key: 'group', title: '계모임', icon: '☁', pages: ['/my-groups', '/groups/new', '/operations', '/procedures', '/chat', '/groups'] }];
const TITLES = Object.fromEntries([...MENU, ...ADMIN_MENU].map(([p, , t]) => [p, t]));
const QUOTES = ['서로의 목표를 존중하며 안전하게 모아봐요.', '작은 저축이 모여 더 큰 내일을 만들어요.', '함께 정한 약속이 든든한 신뢰가 돼요.', '오늘의 꾸준함이 내일의 여유를 선물해요.', '혼자보다 함께라서 목표에 더 가까워져요.'];

export function ThemeSwitcher() {
  const { theme, setTheme } = useApp();
  return <div className="sidebar-theme" aria-label="화면 테마 변경"><span className="sidebar-theme-title">◐ 테마 변경</span><div className="sidebar-theme-actions">
    <button type="button" className={`sidebar-theme-btn ${theme === 'v9' ? 'selected' : ''}`} onClick={() => setTheme('v9')}>☀ 밝음</button>
    <button type="button" className={`sidebar-theme-btn ${theme === 'v9meeting' ? 'selected' : ''}`} onClick={() => setTheme('v9meeting')}>☾ 어두움</button></div></div>;
}
function Quote() {
  const [i, setI] = useState(0), [fade, setFade] = useState(false);
  useEffect(() => { const t = setInterval(() => { setFade(true); setTimeout(() => { setI(x => (x + 1) % QUOTES.length); setFade(false); }, 1200); }, 7500); return () => clearInterval(t); }, []);
  return <span id="moava-daily-quote" className={fade ? 'quote-changing' : ''}>{QUOTES[i]}</span>;
}
function MemberMenu({ pathname }) {
  const [open, setOpen] = useState(() => JSON.parse(sessionStorage.getItem('moava-menu') || '{}'));
  const toggle = k => setOpen(o => { const n = { ...o, [k]: !o[k] }; sessionStorage.setItem('moava-menu', JSON.stringify(n)); return n; });
  const Item = ({ p, icon, title, child }) => <NavLink to={p} end className={({ isActive }) => `nav-btn ${child ? 'compact-subnav' : ''} ${isActive ? 'active' : ''}`}><span className="nav-ico">{icon}</span><span>{title}</span></NavLink>;
  const find = p => MENU.find(m => m[0] === p);
  const grouped = new Set(['/dashboard', ...GROUPS.flatMap(g => g.pages)]);
  return <>
    <Item p="/dashboard" icon="◈" title="마이 홈" />
    {GROUPS.map(g => { const active = g.pages.includes(pathname), expanded = open[g.key] || active; return <div key={g.key}>
      <button type="button" className={`nav-btn compact-group ${active ? 'compact-parent-active' : ''}`} aria-expanded={expanded} onClick={() => toggle(g.key)}><span className="nav-ico">{g.icon}</span><span>{g.title}</span><span className="compact-chevron">{expanded ? '⌄' : '›'}</span></button>
      {expanded && <div className="compact-children">{g.pages.map(p => { const [, icon, title] = find(p); return <Item key={p} p={p} icon={icon} title={title} child />; })}</div>}
    </div>; })}
    {MENU.filter(m => !grouped.has(m[0])).map(([p, icon, title]) => <Item key={p} p={p} icon={icon} title={title} />)}
  </>;
}

/** 로그인 필요 레이아웃. role: 'MEMBER' | 'ADMIN' */
export default function Shell({ role }) {
  const { session, isAdmin, user, run } = useApp();
  const { pathname } = useLocation(); const nav = useNavigate();
  const [mobile, setMobile] = useState(false);
  useEffect(() => { if (isAdmin) proofs.detectOverdue(); }, [isAdmin, pathname]);
  if (!session || !user) return <Navigate to="/login" replace />;
  if (role === 'ADMIN' && !isAdmin) return <Navigate to="/dashboard" replace />;
  if (role === 'MEMBER' && isAdmin) return <Navigate to="/admin" replace />;
  const unread = adminApi.myNotices().filter(n => !n.read).length;
  const logout = async () => { await run(() => auth.logout()); nav('/'); };
  return <div className="app">
    <aside className={`sidebar ${mobile ? 'open' : ''}`}>
      <div className="brand" onClick={() => nav(isAdmin ? '/admin' : '/dashboard')}><span className="brand-logo">☁</span><div><b>모아바</b><small>MOAVA</small></div></div>
      <div className="nav-label">{isAdmin ? 'ADMIN CONSOLE' : 'MY SKY WORKSPACE'}</div>
      {isAdmin ? ADMIN_MENU.map(([p, icon, t]) => <NavLink key={p} to={p} end className={({ isActive }) => `nav-btn ${isActive ? 'active' : ''}`}><span className="nav-ico">{icon}</span>{t}</NavLink>) : <MemberMenu pathname={pathname} />}
      <div className="side-bottom"><div className="side-foot">☁ <b>함께 모으는 내일</b><br /><Quote /></div><ThemeSwitcher /></div>
    </aside>
    <div className="main">
      <header className="topbar">
        <div className="actions"><button className="top-icon mobile-menu" onClick={() => setMobile(m => !m)}>☰</button><span className="muted">MOAVA / </span><strong>{TITLES[pathname] || (pathname === '/account' ? '내 정보' : pathname === '/invite' ? '초대받은 계모임' : '내 공간')}</strong></div>
        <div className="top-actions">
          <button className="pill profile-link" onClick={() => isAdmin ? null : nav('/account')} title={isAdmin ? '관리자' : '내 정보 보기'}>{isAdmin ? 'ADMIN' : '👤'} · {user.name}</button>
          <button className="top-icon notification-trigger" onClick={() => nav(isAdmin ? '/admin/notifications' : '/notifications')} aria-label={`알림함, 읽지 않은 알림 ${unread}개`}>♧{unread > 0 && <span className="notification-count">{unread > 99 ? '99+' : unread}</span>}</button>
          <button className="btn small" onClick={logout}>로그아웃</button>
        </div>
      </header>
      <main className="content"><Outlet /></main>
      <footer className="footer">© 2026 MOAVA · KDT 팀 프로젝트 · 브라우저 시연용 · 실제 금융 거래 및 인증 없음</footer>
    </div>
  </div>;
}
