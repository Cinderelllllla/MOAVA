/* 공통 껍데기: 사이드바(접이식 메뉴) · 탑바(알림 배지) · 푸터 · 권한 가드 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { auth, finance, fetchUnreadNotificationCount } from '../api';
import { NOTIFICATIONS_CHANGED } from '../common/notificationEvents';
import Icon, { Logo } from './Icon';

export const MENU = [
  ['/dashboard', '◈', '마이 홈'], ['/journey', '✧', '맞춤 추천 시작'], ['/finance', '◉', 'AI 금융 분석'], ['/finance/monthly', '▤', '월별 금융 데이터'], ['/openbanking', '₩', '오픈뱅킹 · 계좌'],
  ['/groups', '⌖', '지역 계모임 찾기 · 지도'], ['/simulator', '◷', '계모임 시뮬레이터'], ['/my-groups', '☁', '내 계모임'], ['/groups/new', '✦', '계모임 만들기'],
  ['/operations', '⚙', '운영 홈'], ['/procedures', '≡', '모임 절차'], ['/chat', '☏', '모임별 채팅'], ['/evidence', '▣', '납입 증빙'], ['/support', '☏', '고객센터 · 신고'],
];
export const ADMIN_MENU = [
  ['/admin', '◈', '관리자 대시보드'], ['/admin/groups', '▤', '계모임 관리 · 이력'], ['/admin/sanctions', '⚑', '신고 · 제재 관리'], ['/admin/dissolve', '◇', '해산 승인'],
  ['/admin/refs', '▣', '거래 참조번호 관리'], ['/admin/overdue', '◷', '미납 관리'], ['/admin/support', '☏', '고객센터 · 신고 처리'], ['/admin/notifications', '♧', '관리자 알림'],
];
const GROUPS = [{ key: 'ai', title: 'AI', icon: '✧', pages: ['/finance', '/simulator'] }, { key: 'group', title: '계모임', icon: '☁', pages: ['/my-groups', '/groups/new', '/operations', '/procedures', '/chat', '/groups'] }];
const TITLES = Object.fromEntries([...MENU, ...ADMIN_MENU].map(([p, , t]) => [p, t]));
/* 같은 특수문자를 쓰는 메뉴(채팅·고객센터 등)를 구분하기 위한 경로별 아이콘 */
const ROUTE_ICON = { '/openbanking': 'wallet', '/support': 'headset', '/admin/support': 'headset', '/admin/groups': 'users', '/admin/overdue': 'bell', '/admin/notifications': 'bell', '/admin/refs': 'file' };
const NavIco = ({ p, icon }) => <span className="nav-ico"><Icon name={ROUTE_ICON[p]} g={icon} /></span>;
const QUOTES = ['서로의 목표를 존중하며 안전하게 모아봐요.', '작은 저축이 모여 더 큰 내일을 만들어요.', '함께 정한 약속이 든든한 신뢰가 돼요.', '오늘의 꾸준함이 내일의 여유를 선물해요.', '혼자보다 함께라서 목표에 더 가까워져요.'];

export function ThemeSwitcher() {
  const { theme, setTheme } = useApp();
  return <div className="sidebar-theme" aria-label="화면 테마 변경"><span className="sidebar-theme-title">화면 테마</span><div className="sidebar-theme-actions">
    <button type="button" className={`sidebar-theme-btn ${theme === 'v9' ? 'selected' : ''}`} onClick={() => setTheme('v9')}><Icon name="sun" size={15} /> 밝음</button>
    <button type="button" className={`sidebar-theme-btn ${theme === 'v9meeting' ? 'selected' : ''}`} onClick={() => setTheme('v9meeting')}><Icon name="moon" size={15} /> 어두움</button></div></div>;
}
function Quote() {
  const [i, setI] = useState(0), [fade, setFade] = useState(false);
  useEffect(() => { const t = setInterval(() => { setFade(true); setTimeout(() => { setI(x => (x + 1) % QUOTES.length); setFade(false); }, 1200); }, 7500); return () => clearInterval(t); }, []);
  return <span id="moava-daily-quote" className={fade ? 'quote-changing' : ''}>{QUOTES[i]}</span>;
}
function MemberMenu({ pathname }) {
  const [open, setOpen] = useState(() => JSON.parse(sessionStorage.getItem('moava-menu') || '{}'));
  const toggle = k => setOpen(o => { const n = { ...o, [k]: !o[k] }; sessionStorage.setItem('moava-menu', JSON.stringify(n)); return n; });
  const Item = ({ p, icon, title, child }) => <NavLink to={p} end className={({ isActive }) => `nav-btn ${child ? 'compact-subnav' : ''} ${isActive ? 'active' : ''}`}><NavIco p={p} icon={icon} /><span>{title}</span></NavLink>;
  const find = p => MENU.find(m => m[0] === p);
  const grouped = new Set(['/dashboard', ...GROUPS.flatMap(g => g.pages)]);
  return <>
    <Item p="/dashboard" icon="◈" title="마이 홈" />
    {GROUPS.map(g => { const active = g.pages.includes(pathname), expanded = open[g.key] || active; return <div key={g.key}>
      <button type="button" className={`nav-btn compact-group ${active ? 'compact-parent-active' : ''}`} aria-expanded={expanded} onClick={() => toggle(g.key)}><span className="nav-ico"><Icon g={g.icon} /></span><span>{g.title}</span><span className="compact-chevron"><Icon name={expanded ? 'down' : 'right'} size={16} /></span></button>
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
  // 좁은 화면 서랍 메뉴: 다른 화면으로 이동하면 자동으로 닫는다
  useEffect(() => { setMobile(false); }, [pathname]);
  // 서랍 메뉴가 열린 채로 화면을 넓히면(761px 이상) 닫는다
  useEffect(() => {
    const wide = window.matchMedia('(min-width: 761px)');
    const close = e => { if (e.matches) setMobile(false); };
    wide.addEventListener('change', close);
    return () => wide.removeEventListener('change', close);
  }, []);
  // 로그인 회원 정보(/api/auth/me)와 저장된 금융 프로필(/api/v1/finance/profile)을 서버에서 다시 받아 온다.
  const sessionId = session?.id;
  useEffect(() => {
    if (sessionId != null && !isAdmin) {
      auth.refreshMe().catch(() => {});
      finance.refreshFinanceProfile().catch(() => {});
    }
  }, [sessionId, isAdmin]);
  // 상단 알림 배지: 서버의 안 읽은 개수 (회원·관리자 공통 /api/v1/notifications/unread-count)
  const [unread, setUnread] = useState(0);
  const canNotify = Boolean(session);
  const canNotifyRef = useRef(canNotify);
  useEffect(() => { canNotifyRef.current = canNotify; }, [canNotify]);
  const loadUnread = useCallback(() => {
    if (!canNotifyRef.current) return;
    fetchUnreadNotificationCount()
      .then(c => { if (canNotifyRef.current) setUnread(c); })
      .catch(e => console.warn('[알림] 안 읽은 개수 조회 실패:', e?.message || e));
  }, []);
  // 구독: 60초 타이머 + 알림함 변경 이벤트 (로그인 상태가 바뀔 때만 다시 등록). 로그아웃하면 0으로
  useEffect(() => {
    if (!canNotify) { setUnread(0); return undefined; }
    const timer = setInterval(loadUnread, 60000);
    window.addEventListener(NOTIFICATIONS_CHANGED, loadUnread);
    return () => { clearInterval(timer); window.removeEventListener(NOTIFICATIONS_CHANGED, loadUnread); };
  }, [canNotify, loadUnread]);
  // 화면 이동·로그인 시 한 번 갱신
  useEffect(() => { if (canNotify) loadUnread(); }, [canNotify, pathname, loadUnread]);
  if (!session || !user) return <Navigate to={role === 'ADMIN' ? '/admin/login' : '/login'} replace />;
  if (role === 'ADMIN' && !isAdmin) return <Navigate to="/dashboard" replace />;
  if (role === 'MEMBER' && isAdmin) return <Navigate to="/admin" replace />;
  const logout = async () => { await run(() => auth.logout()); nav('/'); };
  return <div className="app">
    {mobile && <div className="sidebar-dim" onClick={() => setMobile(false)} aria-hidden="true" />}
    <aside className={`sidebar ${mobile ? 'open' : ''}`}>
      <div className="brand" onClick={() => nav(isAdmin ? '/admin' : '/dashboard')}><span className="brand-logo"><Logo /></span><div><b>모아바</b><small>MOAVA</small></div></div>
      <div className="nav-label">{isAdmin ? 'ADMIN CONSOLE' : 'MY WORKSPACE'}</div>
      {isAdmin ? ADMIN_MENU.map(([p, icon, t]) => <NavLink key={p} to={p} end className={({ isActive }) => `nav-btn ${isActive ? 'active' : ''}`}><NavIco p={p} icon={icon} />{t}</NavLink>) : <MemberMenu pathname={pathname} />}
      <div className="side-bottom"><div className="side-foot"><b>함께 모으는 내일</b><br /><Quote /></div><ThemeSwitcher /></div>
    </aside>
    <div className="main">
      <header className="topbar">
        <div className="actions"><button className="top-icon mobile-menu" onClick={() => setMobile(m => !m)} aria-label="메뉴 열기" aria-expanded={mobile}><Icon name="menu" /></button><span className="muted">MOAVA / </span><strong>{TITLES[pathname] || (pathname === '/account' ? '내 정보' : pathname === '/invite' ? '초대받은 계모임' : '내 공간')}</strong></div>
        <div className="top-actions">
          <button className="pill profile-link" onClick={() => isAdmin ? null : nav('/account')} title={isAdmin ? '관리자' : '내 정보 보기'}>{isAdmin ? 'ADMIN' : <Icon name="user" size={16} />} {user.name}</button>
          <button className="top-icon notification-trigger" onClick={() => nav(isAdmin ? '/admin/notifications' : '/notifications')} aria-label={`알림함, 읽지 않은 알림 ${unread}개`}><Icon name="bell" />{unread > 0 && <span className="notification-count">{unread > 99 ? '99+' : unread}</span>}</button>
          <button className="btn small" onClick={logout}>로그아웃</button>
        </div>
      </header>
      <main className="content"><Outlet /></main>
      <footer className="footer">© 2026 MOAVA · KDT 팀 프로젝트 · 실제 금융 거래는 일어나지 않아요</footer>
    </div>
  </div>;
}
