/* S13 알림함(회원) · S14 내 정보 · S15 고객센터(회원) · A07 신고 처리(관리자) */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../common/store';
import { auth, admin, groups, memberName, fetchNotifications, fetchUnreadNotificationCount, markNotificationRead, markAllNotificationsRead, hideNotifications } from '../api';
import { PageHead, Card, Input, Empty } from '../components/ui';
import { ThemeSwitcher } from '../components/Shell';
import { notifyNotificationsChanged, isInternalLink } from '../common/notificationEvents';

/* ---- 알림 (F35) — 서버 알림함 API(/api/v1/notifications) 연동 ----
 * 안 읽은 알림(read=N)과 읽은 알림(read=Y)을 서버에서 따로 받아 온다.
 * "읽지 않은 알림 N건"은 상단 배지와 같은 서버 unread-count 값을 쓴다. */
const PAGE_SIZE = 20; // 서버 최대 50
const EMPTY_LIST = { items: [], page: -1, hasNext: false };
/** 최신순(createdAt, id 내림차순) 정렬 + id 중복 제거 */
const mergeById = (...lists) => {
  const map = new Map();
  lists.flat().forEach(n => { if (!map.has(n.id)) map.set(n.id, n); });
  return [...map.values()].sort((a, b) => String(b.createdAt).localeCompare(String(a.createdAt)) || b.id - a.id);
};
/** 서버 시각("2026-11-19T10:24:00") → "11.19 10:24" (올해가 아니면 연도 포함) */
const fmtTime = v => {
  if (!v) return '';
  const d = new Date(v); if (Number.isNaN(d.getTime())) return String(v);
  const p = n => String(n).padStart(2, '0');
  const md = `${p(d.getMonth() + 1)}.${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
  return d.getFullYear() === new Date().getFullYear() ? md : `${d.getFullYear()}.${md}`;
};
/** read('N'|'Y') 목록을 0 ~ lastPage 페이지까지 다시 불러온다 (더 보기로 열어 둔 페이지 유지) */
const fetchPages = async (read, lastPage) => {
  const pages = await Promise.all(Array.from({ length: lastPage + 1 }, (_, page) => fetchNotifications({ read, page, size: PAGE_SIZE })));
  return { items: mergeById(pages.map(d => d?.items || [])), page: lastPage, hasNext: Boolean(pages[lastPage]?.hasNext) };
};

/** 알림 한 줄. 링크가 있는 알림만 제목·본문 클릭(제목은 키보드 Enter/Space)으로 이동, 링크 없는 알림은 '읽음' 버튼 */
function NotificationItem({ n, checked, onToggle, onOpen, onRead }) {
  const linked = isInternalLink(n.linkPage);
  const onKey = e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onOpen(n); } };
  return (
    <div className={`list-item notice-row ${n.read ? 'notice-read' : ''}`}>
      <div className="item-head">
        <div className="actions" style={{ margin: 0 }}>
          <input type="checkbox" className="notice-check" checked={checked} onChange={() => onToggle(n.id)} aria-label={`${n.title} 선택`} />
          {linked
            ? <b role="link" tabIndex={0} style={{ cursor: 'pointer' }} onClick={() => onOpen(n)} onKeyDown={onKey}>{n.title}</b>
            : <b>{n.title}</b>}
          <span className="pill">{n.read ? '읽음' : 'NEW'}</span>
        </div>
        <small className="muted">{fmtTime(n.createdAt)}</small>
      </div>
      <p className="muted" style={linked ? { cursor: 'pointer' } : undefined} onClick={linked ? () => onOpen(n) : undefined}>{n.body}</p>
      {!n.read && !linked && <button className="btn small soft" onClick={() => onRead(n)}>읽음</button>}
    </div>
  );
}

export function Notifications() {
  const { isAdmin } = useApp();
  return isAdmin ? <AdminNotificationsPending /> : <MemberNotifications />;
}

/** 관리자 알림함: 관리자 JWT 로그인(관리자 전용 claim)이 생기기 전까지 안내만 표시 */
function AdminNotificationsPending() {
  return <><PageHead kicker="NOTIFICATION CENTER" title="관리자 알림" sub="관리자 알림함은 준비 중입니다." />
    <Card><Empty>관리자 알림은 관리자 로그인(서버 인증)이 연결된 뒤 제공됩니다.</Empty></Card></>;
}

function MemberNotifications() {
  const { run, setUi } = useApp();
  const nav = useNavigate();
  const [unread, setUnread] = useState(EMPTY_LIST), [read, setRead] = useState(EMPTY_LIST);
  const [unreadTotal, setUnreadTotal] = useState(0);
  const [loading, setLoading] = useState(true), [more, setMore] = useState(null), [error, setError] = useState('');
  const [sel, setSel] = useState([]);
  const pagesRef = useRef({ N: 0, Y: 0 }); // 더 보기로 열어 둔 마지막 페이지
  const busyRef = useRef(new Set());       // 읽음 요청 중인 알림 id (중복 클릭 방지)

  const reload = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const [u, r, count] = await Promise.all([fetchPages('N', pagesRef.current.N), fetchPages('Y', pagesRef.current.Y), fetchUnreadNotificationCount()]);
      setUnread(u); setRead(r); setUnreadTotal(count);
    } catch (e) {
      setError(e.message || '알림을 불러오지 못했어요.');
    } finally { setLoading(false); }
  }, []);
  useEffect(() => { reload(); }, [reload]);

  const loadMore = async key => {
    const cur = key === 'N' ? unread : read, setter = key === 'N' ? setUnread : setRead;
    const next = cur.page + 1;
    setMore(key);
    try {
      const data = await fetchNotifications({ read: key, page: next, size: PAGE_SIZE });
      setter(prev => ({ items: mergeById(prev.items, data?.items || []), page: next, hasNext: Boolean(data?.hasNext) }));
      pagesRef.current[key] = next;
    } catch (e) {
      setError(e.message || '알림을 불러오지 못했어요.');
    } finally { setMore(null); }
  };

  const afterChange = async () => { setSel([]); await reload(); notifyNotificationsChanged(); };

  /** 읽음 처리 후 화면에서 '읽음' 목록으로 옮긴다. 이미 요청 중이면 무시 */
  const markRead = async n => {
    if (n.read) return true;
    if (busyRef.current.has(n.id)) return false;
    busyRef.current.add(n.id);
    try {
      const r = await run(() => markNotificationRead(n.id));
      if (!r.ok) return false;
      setUnread(prev => ({ ...prev, items: prev.items.filter(x => x.id !== n.id) }));
      setRead(prev => ({ ...prev, items: mergeById([{ ...n, read: true }], prev.items) }));
      setUnreadTotal(c => Math.max(0, c - 1));
      notifyNotificationsChanged();
      return true;
    } finally { busyRef.current.delete(n.id); }
  };

  /** 링크 알림 클릭: 읽음 처리 → 앱 내부 경로로만 이동. ?groupId= 가 있으면 그 모임을 선택해 둔다 */
  const open = async n => {
    if (!isInternalLink(n.linkPage)) return;
    if (!(await markRead(n))) return;
    const gid = Number(new URLSearchParams(n.linkPage.split('?')[1] || '').get('groupId'));
    if (Number.isSafeInteger(gid) && gid > 0) setUi({ selected: gid, focus: gid });
    nav(n.linkPage);
  };
  const readAll = async () => { const r = await run(() => markAllNotificationsRead(), '모두 읽음으로 표시했어요.'); if (r.ok) afterChange(); };
  const hide = async () => {
    const r = await run(() => hideNotifications(sel), `${sel.length}개 알림을 삭제했어요.`);
    if (r.ok) afterChange();
  };
  const toggle = useCallback(id => setSel(prev => (prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id])), []);
  const row = n => <NotificationItem key={n.id} n={n} checked={sel.includes(n.id)} onToggle={toggle} onOpen={open} onRead={markRead} />;
  const moreBtn = (key, list) => list.hasNext && <div className="actions"><button className="btn soft" onClick={() => loadMore(key)} disabled={more === key}>{more === key ? '불러오는 중…' : '이전 알림 더 보기'}</button></div>;
  const empty = !unread.items.length && !read.items.length;

  return <><PageHead kicker="NOTIFICATION CENTER" title="내 알림함" sub="알림을 누르면 읽음 처리되고 관련 화면으로 이동합니다.">
      <button className="btn soft" onClick={readAll} disabled={!unreadTotal}>모두 읽기</button>
      <button className="btn danger" onClick={hide} disabled={!sel.length}>선택 삭제{sel.length ? ` (${sel.length})` : ''}</button>
    </PageHead>
    <Card>
      {error && <p className="muted" role="alert">{error} <button className="btn small soft" onClick={reload}>다시 시도</button></p>}
      {loading && empty ? <Empty>알림을 불러오는 중이에요…</Empty> : <>
        <h2>읽지 않은 알림 <small className="muted">{unreadTotal}건</small></h2>
        <div className="list">{unread.items.length ? unread.items.map(row) : <Empty>아직 읽을 알림이 없습니다.</Empty>}</div>
        {moreBtn('N', unread)}
        <div className="divider" />
        <h2>읽음</h2>
        <div className="list">{read.items.length ? read.items.map(row) : <Empty>읽은 알림이 없습니다.</Empty>}</div>
        {moreBtn('Y', read)}
      </>}
      <p className="muted mini">선택 삭제는 현재 계정의 알림함에서만 숨기며(DELETED_YN) 운영 감사 이력은 유지됩니다.</p>
    </Card></>;
}

/* ---- 내 정보 (F03 F04) ---- */
export function Account() {
  const { user, run } = useApp(); const nav = useNavigate();
  const [f, setF] = useState({ name: user.name, email: user.email, phone: user.phone || '', regionSido: user.regionSido || '', regionSigungu: user.regionSigungu || '' });
  const on = k => e => setF({ ...f, [k]: e.target.value });
  return <><PageHead kicker="MY ACCOUNT" title="내 정보 · 개인정보" sub="연락처와 활동 지역 등 계정 정보를 관리합니다." />
    <Card><form className="form-grid" onSubmit={e => { e.preventDefault(); run(() => auth.updateAccount(f), '내 정보를 저장했어요.'); }}><Input label="이름" name="name" required value={f.name} onChange={on('name')} /><Input label="이메일" name="email" type="email" required value={f.email} onChange={on('email')} /><Input label="연락처 (암호화 저장)" name="phone" type="tel" required value={f.phone} onChange={on('phone')} /><Input label="활동 지역 · 시/도" name="regionSido" required value={f.regionSido} onChange={on('regionSido')} /><Input label="활동 지역 · 시/군/구" name="regionSigungu" required value={f.regionSigungu} onChange={on('regionSigungu')} /><div className="field"><label>계정 유형</label><div className="info">일반 회원 (모임 생성 시 해당 모임장 권한 부여) · 개인정보 동의 {user.privacyAgreedAt || '-'}</div></div><div className="field"><label>변경사항 저장</label><button className="btn primary">내 정보 저장</button></div></form>
      <div className="theme-settings"><h2>🎨 화면 테마 설정</h2><p className="muted">밝은 스카이 브리즈 또는 어두운 네이비·민트 테마를 선택할 수 있어요. 설정은 새로고침 후에도 유지됩니다.</p><ThemeSwitcher /></div>
      <div className="divider" /><p className="warning mini">이 화면은 브라우저 데모로 비밀번호·개인정보를 안전하게 저장하지 않습니다. 실제 서비스에서는 서버 인증, 비밀번호 해시(PASSWORD_HASH), 연락처 AES 암호화(PHONE_ENC)가 적용됩니다.</p>
      <div className="actions"><button className="btn danger" onClick={() => confirm('회원 탈퇴 시 개인정보는 익명화되고 복구할 수 없습니다. 진행할까요?') && run(() => auth.withdrawAccount(), '탈퇴 처리되었습니다.').then(r => r.ok && nav('/'))}>회원 탈퇴 (익명화)</button><button className="btn soft" onClick={() => confirm('모든 데모 데이터를 초기화할까요?') && run(() => auth.resetDemo()).then(() => { sessionStorage.removeItem('moava-ui'); nav('/'); })}>데모 데이터 초기화</button></div></Card></>;
}

/* ---- 고객센터 (F38 회원 / F40 관리자) ---- */
const REPORT_STATUS = ['접수', '검토 중', '추가 자료 요청', '처리 완료', '반려'];
export function Support() {
  const { run, isAdmin, ui, setUi } = useApp();
  if (isAdmin) {
    const list = admin.reports().filter(r => ui.reportFilter === 'all' || r.status === ui.reportFilter).slice().reverse();
    const change = (r, next) => { if (r.status === next) return; const reason = prompt('처리 사유 또는 메모를 입력해 주세요. (필수)'); run(() => admin.changeReportStatus(r.id, next, reason), '신고 처리 상태를 저장했습니다.'); };
    return <><PageHead kicker="CUSTOMER CENTER" title="관리자 고객센터 · 신고 처리" sub="접수된 신고를 검토하고 처리 상태와 이력을 관리합니다." />
      <Card><div className="field" style={{ maxWidth: 300 }}><label>처리 상태 필터</label><select value={ui.reportFilter} onChange={e => setUi({ reportFilter: e.target.value })}><option value="all">전체</option>{REPORT_STATUS.map(v => <option key={v}>{v}</option>)}</select></div><div className="divider" /><h2>신고 접수 내역 · {list.length}건</h2>
        {list.length ? list.map(r => <div className="list-item" key={r.id} style={{ marginTop: 12 }}><div className="item-head"><b>{r.category}</b><span className="pill">{r.status}</span></div><p className="muted mini">신고자: {memberName(r.memberId)} · 대상 모임: {groups.getGroup(r.groupId)?.title || '미지정'} · 접수: {r.created}</p><p>{r.detail}</p><div className="field" style={{ maxWidth: 300 }}><label>처리 상태 변경 (사유 필수)</label><select value={r.status} onChange={e => change(r, e.target.value)}>{REPORT_STATUS.map(v => <option key={v}>{v}</option>)}</select></div>{r.history?.length > 0 && <details style={{ marginTop: 10 }}><summary>처리 이력 {r.history.length}건</summary>{r.history.map((h, i) => <p className="muted mini" key={i}>{h.date} · {h.from} → {h.to} · {h.reason}</p>)}</details>}</div>) : <p className="muted">접수된 신고가 없습니다.</p>}</Card></>;
  }
  return <MemberSupport run={run} />;
}
function MemberSupport({ run }) {
  const mine = groups.myGroups(); const [f, setF] = useState({ group: mine[0]?.id || '', category: '계좌 변경 의심', detail: '' });
  const reports = admin.reports().filter(r => r.memberId === auth.currentUser()?.id).slice().reverse();
  return <><PageHead kicker="CUSTOMER CENTER" title="고객센터 · 자금 안전 신고" sub="의심 거래 및 계좌 변경 관련 신고를 접수합니다." />
    <Card><h2>신고 접수</h2><p className="muted mini">실제 계좌 동결·피해 구제는 MOAVA가 수행할 수 없습니다. 사기 피해가 의심되면 해당 은행 및 경찰 등 공식 기관에 즉시 연락하세요.</p>
      <form onSubmit={e => { e.preventDefault(); run(() => admin.submitReport(Number(f.group), f.category, f.detail), '신고가 접수되었습니다. 실제 금융기관 신고는 별도로 진행해야 합니다.').then(r => r.ok && setF({ ...f, detail: '' })); }}><div className="form-grid"><div className="field"><label>대상 모임</label><select value={f.group} onChange={e => setF({ ...f, group: e.target.value })}>{mine.map(g => <option key={g.id} value={g.id}>{g.title}</option>)}{!mine.length && <option value="">참여 모임 없음</option>}</select></div><div className="field"><label>신고 유형</label><select value={f.category} onChange={e => setF({ ...f, category: e.target.value })}>{['계좌 변경 의심', '자금 유용 의심', '거래 증빙 불일치', '제재 이의 신청', '기타 문의'].map(v => <option key={v}>{v}</option>)}</select></div></div><div className="field"><label>상세 내용 (암호화 저장)</label><textarea required value={f.detail} onChange={e => setF({ ...f, detail: e.target.value })} placeholder="상황, 날짜, 관련 내역을 작성하세요." /></div><button className="btn primary">신고 접수</button></form></Card>
    <div className="section"><Card><h2>나의 신고 내역</h2>{reports.length ? reports.map(r => <div className="list-item" key={r.id}><b>{groups.getGroup(r.groupId)?.title || '모임'} · {r.category}</b> <span className="pill">{r.status}</span><p className="muted mini">{r.created} · {r.detail}</p></div>) : <p className="muted">신고 내역이 없습니다.</p>}</Card></div></>;
}
