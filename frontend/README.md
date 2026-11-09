# MOAVA Frontend (React 19 · react-router 7 · Vite 6 · JavaScript)

기능명세서 v3.2 · 테이블 정의서 v1.5 기준 27개 화면. 팀 스캐폴드 의존성(React 19, react-router-dom 7, Vite 6, axios, **zustand**) + **Tailwind CSS v4(보조)** 로 빌드·동작 확인. styled-components는 스타일 방식 통일을 위해 제거(11/09 결정).

## 시작
```bash
cd frontend
npm install
npm run dev        # http://localhost:3000 (vite.config.js 포트)
```
체험 계정 — 회원 `moa@example.com` / 모임장 `leader@example.com` (demo1234) / 관리자 `admin@moava.test` (admin1234)
데이터는 아직 브라우저 localStorage(mock)에 저장됩니다. 백엔드 연동 전까지 화면 개발·시연용.

## 폴더 구조
```
src/
├ main.jsx            진입점 · CSS 로드 순서(base → themes → layout → pages → spec)
├ App.jsx             라우팅 (화면 ↔ 경로)
├ styles/             CSS 5개 + tailwind.css (디자인은 기존 클래스, Tailwind는 레이아웃 유틸리티만)
├ api/                ★ 백엔드 연동 지점
│  ├ index.js         화면이 import 하는 유일한 진입점
│  ├ auth.js finance.js groups.js procedures.js proofs.js admin.js
│  │                  지금: mock/db.js(localStorage)를 읽고 쓰는 async 함수
│  │                  연동: 함수 본문만 axios/fetch('/api/...')로 교체 → 화면 코드 수정 없음
│  └ mock/db.js       데모 DB(시드·저장·구독) — 연동 후 삭제
├ common/
│  ├ store.js         ★ zustand 전역 상태 — 세션 · 테마 · 화면 상태(ui) · 토스트 · run(). 파일 상단 주석이 zustand 사용법 요약
│  └ format.js        won(만 원) · krw(원) · 마스킹
├ components/
│  ├ Shell.jsx        사이드바·탑바·푸터 + 권한 가드 (MEMBER / ADMIN)
│  ├ shared.jsx       BarChart · GroupCard · GroupInfo · InlineSimulator · RiskPanel · GroupSelect
│  ├ ui.jsx           PageHead · Card · Input · Select · Flow · HistoryTable
│  ├ map/             (담당자) Kakao Maps 컴포넌트 → pages/Groups.jsx의 고정 핀 지도 교체
│  └ chat/            (담당자) STOMP 클라이언트 → pages/Chat.jsx의 mock 메시지 교체
└ pages/              ★ 화면 1개 = 파일 1개 (담당자별 분배)
```

## 화면 ↔ 파일 ↔ 기능 ID
| 경로 | 파일 | 기능 ID |
|---|---|---|
| `/` 랜딩 | pages/Landing.jsx | F02 |
| `/login` `/signup` | pages/Auth.jsx | F01 F02 |
| `/invite?t=토큰` | pages/Invite.jsx | F18 |
| `/dashboard` | pages/Dashboard.jsx | F08 F17 F35 |
| `/journey` | pages/Journey.jsx | F05~F08 F11 F14 |
| `/finance` `/finance/monthly` | pages/Finance.jsx | F06 F08 F09 F10 |
| `/groups` 지도 | pages/Groups.jsx | F11~F14 F16 |
| `/simulator` | pages/Simulator.jsx | F14 |
| `/my-groups` | pages/MyGroups.jsx | F17 |
| `/groups/new` | pages/GroupNew.jsx | F15 F19 |
| `/operations` 운영 홈 | pages/Operations.jsx | F20 F28 F33 F18 |
| `/procedures` 모임 절차 | pages/Procedures.jsx | F21 F24 F25 F27 F30 F31 F36 |
| `/chat` | pages/Chat.jsx | F34 |
| `/evidence` | pages/Evidence.jsx | F22 F23 |
| `/notifications` `/account` `/support` | pages/Misc.jsx | F03 F04 F29 F38 |
| `/admin/*` 8개 | pages/admin/AdminPages.jsx + Misc.jsx | F26 F32 F37 F39 F40 F41 |

## 화면 코드 작성 규칙
- 데이터 읽기: `import { groups, finance, ... } from '../api'` 후 동기 조회 (`groups.myGroups()` 등). mock DB가 바뀌면 AppContext가 다시 그립니다.
- 데이터 변경: `const { run } = useApp(); await run(() => groups.approveJoin(id, rid), '승인했어요.')` — 실패 시 API가 던진 메시지가 토스트로 표시.
- 화면 간 상태(선택 모임, 탭, 시뮬레이터 값): `ui` / `setUi({ selected: id })` (sessionStorage 유지)
- 이동: `useNavigate()` → `nav('/operations')`
- **스타일 규칙 (CSS + Tailwind 보조)**
  - 카드·버튼·배지·색상 같은 '디자인'은 기존 클래스 사용: `card`, `btn primary`, `pill warn`, `policy-panel`, `info` … (밝음/어두움 테마가 이 클래스들에 걸려 있음)
  - 여백·정렬·숨김 같은 '배치'는 Tailwind 유틸리티 사용: `flex gap-3 items-center`, `mt-4`, `hidden md:block`, `text-center` …
  - 색이 꼭 필요하면 디자인 토큰 색상 사용: `bg-moava-pale text-moava-blue border-moava-line` (테마 따라 자동 전환). `bg-blue-500` 같은 Tailwind 기본 팔레트는 테마와 안 맞으니 피하기
  - Tailwind preflight(기본 스타일 초기화)는 끈 상태라 기존 CSS가 그대로 유지됨. `tailwind.css`의 import 두 줄을 `@import "tailwindcss"`로 바꾸면 전체 디자인이 깨지니 주의
  - 예시: `pages/Dashboard.jsx` 하단 "외 N개 모임" 문단 (`className="muted mini mt-3 mb-0"`)
- **상태관리 = zustand** (`common/store.js`, 11/09 결정). 화면에서는 `const { user, ui, setUi, run, toast } = useApp();` 로 쓰고, 특정 값 하나만 구독하려면 `useAppStore(s => s.theme)`. 컴포넌트 밖(api 파일)에서는 `useAppStore.getState().toast('…')`.
  새 전역 상태가 필요하면 `store.js`의 `create()` 안에 상태 + 바꾸는 함수를 한 쌍으로 추가. 화면마다 따로 쓰는 값(폼 입력 등)은 그냥 `useState`.
- **axios**는 `api/*.js`를 백엔드에 연결할 때 사용(JWT 인터셉터).

## 백엔드 연동 순서
1. `vite.config.js` proxy `/api` → Spring Boot 8080, `/ws` → STOMP (이미 설정됨)
2. `src/api/auth.js`부터 함수 본문을 axios 호출로 교체 (반환 형태 유지: 성공은 값, 실패는 `throw new Error(메시지)`)
3. `mock/db.js`의 조회 헬퍼(`group`, `joined`, `leader`…)는 API 응답 캐시를 읽도록 변경
4. 지도 → Kakao Maps(`components/map`), 채팅 → STOMP(`components/chat`), 추천·검색 → ES `moava_group`, 미납 감지 → `@Scheduled` 02:00 배치, 위험 등급 → FastAPI RISK_SCORE
5. 금액: 화면 `won()` 만 원, API·DB는 원 단위(`krw()`)

## 명세 기준 반영 사항
MEMBER/ADMIN 분리 · 알림 수신자 memberId/adminId 분리 · 부계모임장 폐지(승계 대상 지정) · 미납 5일차 자동 감지 목록 · 모임 생성 납입일(PAY_DAY) 필수 · 증빙 보완 요청/재제출 · 정산 금액 입력 → 전원 동의 · AI 제안 회차 · 참조번호 정정(사유·이력) · 초대 토큰 · 계좌 인증 상태(관리자 확인) · 탈퇴 익명화 · 지역 시/도·시/군/구 분리
