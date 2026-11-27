/* SVG 아이콘 모음 — 기존 특수문자 아이콘(◈ ✧ ⌖ ☁ …)을 그림 아이콘으로 바꿔 보여 준다.
   사용: <Icon name="home" />  또는  <Icon g="◈" />  (기존 특수문자를 그대로 넘겨도 됨)
   모르는 이름/문자면 넘긴 문자를 그대로 보여 주므로 기능에는 영향이 없다. */

const P = {
  home: <><path d="M3.5 10.2 12 3.5l8.5 6.7V19a1.5 1.5 0 0 1-1.5 1.5h-4v-6h-6v6H5A1.5 1.5 0 0 1 3.5 19z" /></>,
  sparkles: <><path d="M11 3.5l1.9 5 5 1.9-5 1.9-1.9 5-1.9-5-5-1.9 5-1.9z" /><path d="M18.5 14.5l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z" /></>,
  pie: <><path d="M20.5 13.5A8.5 8.5 0 1 1 10.5 3.5v10z" /><path d="M14 3.2a8.5 8.5 0 0 1 6.8 6.8H14z" /></>,
  chart: <><path d="M4 4v16h16" /><path d="M8.5 16v-3" /><path d="M12.5 16V9" /><path d="M16.5 16v-5" /></>,
  pin: <><path d="M12 21s-7-6.1-7-11.3a7 7 0 0 1 14 0C19 14.9 12 21 12 21z" /><circle cx="12" cy="9.7" r="2.6" /></>,
  calc: <><rect x="4.5" y="3" width="15" height="18" rx="3" /><path d="M8 7.5h8" /><path d="M8.5 12h.01M12 12h.01M15.5 12h.01M8.5 16h.01M12 16h.01M15.5 16h.01" /></>,
  users: <><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20a6.5 6.5 0 0 1 13 0" /><path d="M16 4.6a3.5 3.5 0 0 1 0 6.8" /><path d="M18 14.2a6.2 6.2 0 0 1 3.5 5.8" /></>,
  plus: <><circle cx="12" cy="12" r="8.5" /><path d="M12 8.5v7M8.5 12h7" /></>,
  sliders: <><path d="M4 7h9M17 7h3M4 17h3M11 17h9" /><circle cx="15" cy="7" r="2" /><circle cx="9" cy="17" r="2" /></>,
  list: <><path d="M9 6.5h11M9 12h11M9 17.5h11" /><path d="M4.5 6.5h.01M4.5 12h.01M4.5 17.5h.01" /></>,
  chat: <><path d="M20.5 11.5a8 8 0 0 1-11.7 7.1L4 19.7l1.2-4.4A8 8 0 1 1 20.5 11.5z" /></>,
  receipt: <><path d="M6 3h12v18l-3-2-3 2-3-2-3 2z" /><path d="M9 8h6M9 12h6" /></>,
  headset: <><path d="M4 14v-2a8 8 0 0 1 16 0v2" /><path d="M4 14h3v6H5.5A1.5 1.5 0 0 1 4 18.5zM20 14h-3v6h1.5a1.5 1.5 0 0 0 1.5-1.5z" /></>,
  flag: <><path d="M5.5 21V4" /><path d="M5.5 4.5h12l-2.5 4 2.5 4h-12" /></>,
  archive: <><rect x="3" y="4" width="18" height="5" rx="1.5" /><path d="M5 9v9.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V9" /><path d="M10 13h4" /></>,
  bell: <><path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z" /><path d="M10 21h4" /></>,
  check: <><circle cx="12" cy="12" r="8.5" /><path d="M8.3 12.3l2.5 2.5 5-5" /></>,
  user: <><circle cx="12" cy="8" r="4" /><path d="M4.5 20.5a7.5 7.5 0 0 1 15 0" /></>,
  menu: <><path d="M4 6.5h16M4 12h16M4 17.5h16" /></>,
  down: <><path d="M6.5 9.5l5.5 5.5 5.5-5.5" /></>,
  right: <><path d="M9.5 6l6 6-6 6" /></>,
  sun: <><circle cx="12" cy="12" r="4" /><path d="M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4" /></>,
  moon: <><path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z" /></>,
  shield: <><path d="M12 3l7.5 3v5.5c0 4.6-3.2 8.3-7.5 9.5-4.3-1.2-7.5-4.9-7.5-9.5V6z" /><path d="M9 12l2.2 2.2L15.5 10" /></>,
  wallet: <><rect x="3" y="6" width="18" height="14" rx="3" /><path d="M3 10h18" /><path d="M16 15h.01" /><path d="M6 6l9-3 1.5 3" /></>,
  won: <><circle cx="12" cy="12" r="8.5" /><path d="M7.8 8.8l1.7 6.4 1.6-4.6h1.8l1.6 4.6 1.7-6.4" /><path d="M7.2 11.6h9.6" /></>,
  lock: <><rect x="5" y="10.5" width="14" height="10" rx="2.5" /><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" /></>,
  info: <><circle cx="12" cy="12" r="8.5" /><path d="M12 11v5" /><path d="M12 8h.01" /></>,
  file: <><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5" /></>,
};

/** 기존 특수문자 → 아이콘 이름 */
const GLYPH = {
  '◈': 'home', '✧': 'sparkles', '◉': 'pie', '▤': 'chart', '⌖': 'pin', '◷': 'calc', '☁': 'users',
  '✦': 'plus', '⚙': 'sliders', '≡': 'list', '☏': 'chat', '▣': 'receipt', '⚑': 'flag', '◇': 'archive',
  '♧': 'bell', '✓': 'check', '◌': 'user', '☰': 'menu', '👤': 'user', '⌄': 'down', '›': 'right', '☀': 'sun', '☾': 'moon', '₩': 'won', '🔒': 'lock',
};

export default function Icon({ name, g, size = 20, stroke = 1.9, className = '', ...rest }) {
  const key = name || GLYPH[g];
  const body = P[key];
  if (!body) return g ? <span className={className}>{g}</span> : null;
  return <svg className={`moa-icon ${className}`} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
    strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false" {...rest}>{body}</svg>;
}

/** 모아바 로고 마크 — 동전 두 개가 겹친 모양(함께 모은다) */
export function Logo({ size = 28 }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
    <circle cx="8.6" cy="12" r="6.6" fill="#fff" fillOpacity=".55" />
    <circle cx="15.4" cy="12" r="6.6" fill="#fff" />
    <path d="M12.9 12h5M15.4 9.5v5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
  </svg>;
}
