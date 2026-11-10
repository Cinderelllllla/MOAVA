/* ============================================================
   MOAVA · common/store.js — zustand 전역 상태
   ------------------------------------------------------------
   zustand 핵심 3가지 (이 파일만 읽으면 됩니다)
   1) create((set, get) => ({ ...상태, ...함수 }))  → 스토어(훅) 하나를 만든다.
   2) 컴포넌트에서 useAppStore(s => s.theme) 처럼 "필요한 값만" 꺼낸다.
      → 그 값이 바뀔 때만 그 컴포넌트가 다시 그려진다. (Context보다 리렌더가 적음)
   3) 바꿀 땐 set({ theme: 'v9' }) 또는 set(prev => ({ ...prev.ui, ... })).
      Provider로 감쌀 필요가 없고, 컴포넌트 밖(api 파일 등)에서도
      useAppStore.getState() / useAppStore.setState() 로 읽고 쓸 수 있다.
   ------------------------------------------------------------
   화면에서는 기존처럼  const { user, ui, setUi, run, toast } = useApp();  그대로 쓰면 됩니다.
   ============================================================ */
import { create } from 'zustand';
import { db, subscribe, getVersion, currentUser, isAdmin as adminCheck } from '../api';

const THEME_KEY = 'moava-ui-theme';
const UI_KEY = 'moava-ui';
const UI_DEFAULT = { selected: 101, focus: 101, search: '', region: '전체', purpose: '전체', sim: { amount: 20, members: 6, round: 3, rate: 3 }, procTab: 'order', financeMonth: '11월', inlineSimGroup: null, reportFilter: 'all' };
const load = (key, fallback) => { try { return { ...fallback, ...JSON.parse(sessionStorage.getItem(key) || '{}') }; } catch { return fallback; } };

let toastTimer;

export const useAppStore = create((set, get) => ({
  /* ---- 상태 ---- */
  version: getVersion(),                 // mock DB(또는 API 캐시)가 바뀔 때마다 증가 → 세션·사용자 재계산
  theme: (() => { try { return localStorage.getItem(THEME_KEY) === 'v9meeting' ? 'v9meeting' : 'v9'; } catch { return 'v9'; } })(),
  ui: load(UI_KEY, UI_DEFAULT),          // 화면 간 공유 상태(선택 모임·탭·시뮬레이터 값), 새로고침해도 유지
  toastMsg: '',

  /* ---- 액션 ---- */
  setTheme: t => { document.documentElement.dataset.theme = t; try { localStorage.setItem(THEME_KEY, t); } catch { } set({ theme: t }); },
  setUi: patch => set(s => { const ui = { ...s.ui, ...(typeof patch === 'function' ? patch(s.ui) : patch) }; try { sessionStorage.setItem(UI_KEY, JSON.stringify(ui)); } catch { } return { ui }; }),
  toast: msg => { clearTimeout(toastTimer); set({ toastMsg: msg }); toastTimer = setTimeout(() => set({ toastMsg: '' }), 3300); },
  /** API 호출 + 오류 토스트 공통 처리: const r = await run(() => groups.approveJoin(id, rid), '승인했어요.'); if (r.ok) ... */
  run: async (fn, okMsg) => {
    try { const value = await fn(); if (okMsg) get().toast(typeof okMsg === 'function' ? okMsg(value) : okMsg); return { ok: true, value }; }
    catch (e) { get().toast(e.message || '처리 중 오류가 발생했어요.'); return { ok: false, error: e }; }
  },
}));

/* mock DB가 save()될 때 version을 올려 구독 컴포넌트를 갱신 (백엔드 연동 후엔 API 응답 캐시 갱신 지점으로 교체) */
subscribe(() => useAppStore.setState({ version: getVersion() }));
document.documentElement.dataset.theme = useAppStore.getState().theme;

/** 화면용 편의 훅 — 기존 Context 시절과 같은 모양을 돌려줍니다 */
export function useApp() {
  const version = useAppStore(s => s.version);           // 구독: DB 변경 시 세션·사용자 재계산
  const theme = useAppStore(s => s.theme);
  const ui = useAppStore(s => s.ui);
  const setTheme = useAppStore(s => s.setTheme), setUi = useAppStore(s => s.setUi), toast = useAppStore(s => s.toast), run = useAppStore(s => s.run);
  return { version, session: db.session, isAdmin: adminCheck(), user: currentUser(), theme, setTheme, ui, setUi, toast, run };
}
