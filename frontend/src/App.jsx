/* 라우팅 — 기능명세서 화면목록(S00~S15, A01~A08) ↔ 경로 */
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Toast from './components/Toast';
import Shell from './components/Shell';
import Landing from './pages/Landing';
import { Login, Signup } from './pages/Auth';
import Invite from './pages/Invite';
import Dashboard from './pages/Dashboard';
import Journey from './pages/Journey';
import { Finance, FinanceMonthly } from './pages/Finance';
import Groups from './pages/Groups';
import Simulator from './pages/Simulator';
import MyGroups from './pages/MyGroups';
import GroupNew from './pages/GroupNew';
import Operations from './pages/Operations';
import Procedures from './pages/Procedures';
import Chat from './pages/Chat';
import Evidence from './pages/Evidence';
import { Notifications, Account, Support } from './pages/Misc';
import { AdminDashboard, AdminGroups, AdminSanctions, AdminDissolve, AdminRefs, AdminOverdue } from './pages/admin/AdminPages';

export default function App() {
  return <BrowserRouter><Routes>
    {/* 공용 */}
    <Route path="/" element={<Landing />} />
    <Route path="/login" element={<Login />} />
    <Route path="/signup" element={<Signup />} />
    <Route path="/invite" element={<InviteRoute />} />
    {/* 회원 (S02~S15) */}
    <Route element={<Shell role="MEMBER" />}>
      <Route path="/dashboard" element={<Dashboard />} />
      <Route path="/journey" element={<Journey />} />
      <Route path="/finance" element={<Finance />} />
      <Route path="/finance/monthly" element={<FinanceMonthly />} />
      <Route path="/groups" element={<Groups />} />
      <Route path="/groups/new" element={<GroupNew />} />
      <Route path="/simulator" element={<Simulator />} />
      <Route path="/my-groups" element={<MyGroups />} />
      <Route path="/operations" element={<Operations />} />
      <Route path="/procedures" element={<Procedures />} />
      <Route path="/chat" element={<Chat />} />
      <Route path="/evidence" element={<Evidence />} />
      <Route path="/notifications" element={<Notifications />} />
      <Route path="/account" element={<Account />} />
      <Route path="/support" element={<Support />} />
    </Route>
    {/* 관리자 (A01~A08) */}
    <Route element={<Shell role="ADMIN" />}>
      <Route path="/admin" element={<AdminDashboard />} />
      <Route path="/admin/groups" element={<AdminGroups />} />
      <Route path="/admin/sanctions" element={<AdminSanctions />} />
      <Route path="/admin/dissolve" element={<AdminDissolve />} />
      <Route path="/admin/refs" element={<AdminRefs />} />
      <Route path="/admin/overdue" element={<AdminOverdue />} />
      <Route path="/admin/support" element={<Support />} />
      <Route path="/admin/notifications" element={<Notifications />} />
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes><Toast /></BrowserRouter>;
}

/* 초대 화면: 비로그인은 환영 화면(쉘 없음), 회원은 쉘 안에서 표시 */
import { useApp } from './common/store';
function InviteRoute() { const { session } = useApp(); return session ? <Routes><Route element={<Shell role="MEMBER" />}><Route path="/" element={<Invite />} /></Route></Routes> : <Invite />; }
