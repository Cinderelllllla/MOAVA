# 프론트엔드-백엔드 연동 작업 목록

현재 프로젝트를 확인한 기준으로 정리한 연동 준비 문서입니다. 프론트엔드는 `localStorage` 목업 데이터를 사용하며, 이 문서는 구현된 백엔드 API를 우선 연결하기 위한 작업 순서를 안내합니다. 백엔드에 없는 기능은 API가 구현된 뒤 연동해야 합니다.

## 개발 연결 방식

- 프론트엔드 개발 서버: `http://localhost:3000` (`frontend/vite.config.js`)
- 백엔드 개발 서버: `http://localhost:8080`
- Vite 프록시: `/api/*` 요청을 `http://localhost:8080`으로 전달
- 개발 환경에서는 API 기본 주소를 별도로 붙이지 않고 `/api/...` 상대 경로를 사용할 수 있습니다.
- 인증이 필요한 요청에는 `Authorization: Bearer <accessToken>`을 보냅니다.
- API 모듈 진입점은 `frontend/src/api/index.js`입니다. 화면 코드는 이 진입점을 통해 `auth`, `groups`, `proofs`, `admin` 등의 API 모듈을 가져옵니다.

## 우선 연동 순서

### 1. 공통 HTTP 요청과 인증 토큰 관리

**대상 파일**

- `frontend/src/api/index.js`
- `frontend/src/api/auth.js`
- `frontend/src/common/store.js`

**할 일**

- 기존 모의 데이터 함수와 구분해 백엔드 HTTP 요청을 수행하는 공통 방식을 정합니다.
- 로그인 응답의 `accessToken`을 저장하고 인증이 필요한 요청에 `Authorization: Bearer ...`를 추가합니다.
- 로그아웃 시 토큰과 로그인 상태를 지웁니다.
- 인증 만료(401) 응답을 화면에 전달하고 로그인 상태를 처리합니다.
- 백엔드 응답 DTO와 기존 프론트 화면의 사용자·세션 모델을 연결합니다.

**주의**

- 현재 `store.js`는 목업 DB에서 `session`, `currentUser()`를 읽습니다. 서버 로그인으로 바꾸려면 이 상태도 서버 응답을 기준으로 관리해야 합니다.
- `GET /api/auth/me` 응답의 `MemberView`에는 회원 ID가 포함되지 않습니다. 프론트 화면에서 회원 ID가 필요한 부분은 응답 구조 또는 별도 매핑이 정해져야 합니다.
- 현재 JWT 필터는 인증 토큰을 회원 권한으로 처리합니다. 관리자용 인증 흐름은 확인되지 않았습니다.

### 2. 회원가입 및 로그인

**프론트 연결 위치**

- `frontend/src/api/auth.js`: `login`, `signup`
- `frontend/src/pages/Auth.jsx`: 로그인·회원가입 폼

| 작업 | 메서드 및 경로 | 요청 |
|---|---|---|
| 회원가입 | `POST /api/auth/signup` | `{ name, email, password, phone, regionSido, regionSigungu, termsAgreed, privacyAgreed }` |
| 로그인 | `POST /api/auth/login` | `{ email, password }` |
| 내 정보 조회 | `GET /api/auth/me` | `Authorization: Bearer <accessToken>` |

로그인·회원가입 성공 응답은 `{ accessToken, tokenType, user }`입니다.

**연동 전 필요한 화면 작업**

- 회원가입 약관·개인정보 체크박스 값을 폼 상태에 담아 `termsAgreed`, `privacyAgreed`로 전송합니다. 현재 체크박스는 화면에서 필수 표시만 하며 API 요청 데이터에는 포함되지 않습니다.
- 로그인 후 목업 세션 대신 토큰과 사용자 정보를 보관하도록 연결합니다.
- 화면의 데모 계정 안내는 실제 서버 계정과 구분되도록 정리합니다.

### 3. 모임 생성

**프론트 연결 위치**

- `frontend/src/api/groups.js`: `createGroup`
- `frontend/src/pages/GroupNew.jsx`: 모임 생성 폼

**API**

- `POST /api/groups`
- 헤더: `Authorization: Bearer <accessToken>`
- 성공 시 HTTP 201, 응답: `{ groupId, ownerId, status, message }`

**백엔드 요청 필드**

```json
{
  "title": "모임 이름",
  "purpose": "SAVINGS",
  "regionSido": "서울",
  "regionSigungu": "마포구",
  "amount": 200000,
  "maxMembers": 6,
  "months": 6,
  "payDay": 5,
  "assumedRate": 3,
  "visibility": "PUBLIC",
  "rulesTitle": "운영 규칙 제목",
  "rulesBody": "운영 규칙",
  "description": "모임 소개",
  "overduePolicy": "ALERT_ONLY",
  "bankName": "은행명",
  "accountNumber": "숫자 계좌번호"
}
```

**필드 매핑 및 변환**

- 프론트 `max` → 백엔드 `maxMembers`
- `rate` → `assumedRate`
- `rules` → `rulesBody`
- `bank` → `bankName`
- `accountNo` → `accountNumber`
- 프론트 납입액은 만 원 단위이므로 백엔드 원 단위로 변환합니다. 예: `20` → `200000`
- `purpose`는 `SAVINGS`, `INVEST`, `SOCIAL`, `STARTUP`, `TRAVEL` 중 하나를 보냅니다.
- `overduePolicy`는 `ALERT_ONLY`, `ALERT_RESTRICT` 중 하나를 보냅니다.
- 폼의 지역 문자열을 `regionSido`, `regionSigungu`로 나눠 보냅니다.
- 성공 후 `response.groupId`를 프론트의 모임 ID로 사용합니다. 현재 화면은 응답의 `id`를 기대하므로 연결 시 맞춰야 합니다.

### 4. 납입 증빙

**프론트 연결 위치**

- `frontend/src/api/proofs.js`
- `frontend/src/pages/Evidence.jsx`

**API 목록**

| 기능 | 메서드 및 경로 | 요청 형식 |
|---|---|---|
| 증빙 목록 | `GET /api/v1/groups/{groupId}/payment-proofs` | `Authorization: Bearer ...`, `X-Member-Id` |
| 신규 등록 | `POST /api/v1/groups/{groupId}/payment-proofs` | `multipart/form-data` |
| 승인·보완 요청 | `PATCH /api/v1/groups/{groupId}/payment-proofs/{proofId}/review` | JSON |
| 보완 재제출 | `PUT /api/v1/groups/{groupId}/payment-proofs/{proofId}` | `multipart/form-data` |

**요청 필드**

- 신규 등록: `payMonth`(YYYYMM), `roundNo`, `amount`(원 단위), `payDate`(YYYY-MM-DD), `files`(1~5개)
- 승인·보완 요청: `decision`(`CONFIRMED` 또는 `REVISION`), `reviewMemo`
- 재제출: `payDate`, `files`
- 프론트 필드 매핑: `period` → `payMonth`, `round` → `roundNo`, `date` → `payDate`, `ref` → `refNo`

**연동 전 필요한 처리**

- 현재 화면은 파일을 Data URL로 변환해 목업 DB에 보관합니다. 실제 API에는 Data URL 문자열이 아니라 원본 `File` 객체를 `FormData`로 전송합니다.
- 화면의 월·납입액 단위와 서버 DTO의 형식을 맞춥니다. `payMonth`는 `YYYYMM`, 납입액은 원 단위입니다.
- 목록 DTO의 상태 값(`PENDING`, `CONFIRMED`, `REVISION`)을 화면 표시 상태에 매핑합니다.
- 현재 컨트롤러는 임시 `X-Member-Id` 헤더를 요구합니다. 이 헤더는 인증된 사용자 정보를 서버에서 가져오도록 바꾸는 JWT 연동이 완료되기 전까지의 임시 방식입니다. 사용자 ID를 클라이언트가 임의로 지정하는 방식은 실제 운영 인증으로 사용하지 않습니다.

### 5. 알림함

**프론트 연결 위치**

- `frontend/src/api/admin.js`: 현재 목업 알림 함수
- `frontend/src/pages/Misc.jsx`: `Notifications`

| 기능 | 메서드 및 경로 |
|---|---|
| 목록 | `GET /api/v1/notifications?read=N&page=0&size=20` |
| 안 읽은 개수 | `GET /api/v1/notifications/unread-count` |
| 하나 읽음 | `PATCH /api/v1/notifications/{notificationId}/read` |
| 모두 읽음 | `POST /api/v1/notifications/read-all` |
| 선택 숨김 | `POST /api/v1/notifications/hide` |

숨김 요청 본문은 `{"ids":[1,2]}`입니다. 성공 응답은 `{data, meta}` 구조이며, 목록의 `data`는 `{items, page, size, hasNext}` 형식입니다.

**현재 연결 차단 사항**

- 수신자 결정은 개발용 `X-Member-Id` 또는 `X-Admin-Id` 헤더로만 가능하고, `moava.dev.receiver-header-enabled` 기본값이 `false`입니다. JWT를 사용해 수신자를 결정하는 연동은 아직 완료되지 않았습니다.
- 이 개발 옵션을 사용하려면 개인 로컬 설정에서만 활성화해야 하며, 팀 공통 설정에는 넣지 않습니다.
- 프론트 화면은 현재 `admin.myNotices()` 등 목업 API를 사용합니다.

## 백엔드 경로가 확인되지 않은 기능

아래 기능은 현재 프론트 목업 코드에서 동작하지만 이에 대응하는 백엔드 컨트롤러 경로는 확인되지 않았습니다. API가 구현되기 전까지 서버 연동 완료로 간주하지 않습니다.

- 모임 목록·검색·상세·내 모임·가입 신청·가입 승인
- 모임 운영, 규칙 수정, 공개 설정, 초대 코드
- 금융정보 입력·분석·추천·월별 금융 데이터
- 수령 순서·동의·탈퇴·정산 절차
- 관리자 해산·제재·계좌 확인·참조번호·미납 조치·신고 처리
- 채팅 및 WebSocket/STOMP
- 회원 정보 수정 및 탈퇴

## 연동 전 확인해야 할 백엔드 제약

1. `SecurityConfig`의 CORS 허용 메서드 목록에 `PATCH`가 없습니다. 개발 시 Vite 프록시를 사용하면 보통 CORS를 거치지 않지만, 서로 다른 출처로 배포할 경우 증빙 검토와 알림 읽음 요청이 차단될 수 있습니다.
2. 증빙 API는 `X-Member-Id` 임시 헤더를 요구합니다. 토큰 주체와 헤더 회원 ID의 일치 검증 및 서버 측 사용자 식별 방식이 확정되어야 합니다.
3. 알림 API의 JWT 수신자 연결은 미완료입니다. 기본 설정에서는 개발용 수신자 헤더도 허용되지 않습니다.
4. 관리자 인증 및 권한에 대응하는 백엔드 API는 확인되지 않았습니다. 프론트의 관리자 화면을 실제 운영 데이터와 연결할 수 있는 상태가 아닙니다.
5. 모임 생성 응답은 `groupId`를 반환하지만 현재 프론트 모의 구현은 `id`를 사용하는 등 응답 구조 차이가 있습니다.
6. API 오류 응답 형식이 모든 엔드포인트에서 동일한지 확인하고, 프론트 공통 오류 처리에서 상태 코드와 응답 메시지를 표시하도록 정리해야 합니다.

## 권장 완료 순서

- [ ] 공통 HTTP 호출·JSON 오류 처리 방식 정의
- [ ] 토큰 저장·전송·로그아웃 및 앱 초기 인증 상태 연결
- [ ] 회원가입 동의 값과 로그인·내 정보 API 연결
- [ ] 모임 생성 필드 변환 및 생성 응답 처리 연결
- [ ] 증빙 목록·업로드·승인·재제출 연결
- [ ] JWT 기반 증빙 사용자 식별 방식 확정 및 임시 헤더 제거
- [ ] 알림 API의 JWT 수신자 식별 및 프론트 알림 화면 연결
- [ ] 운영 배포 시 CORS 메서드·출처 정책 확인
- [ ] 백엔드 미구현 기능별 API가 추가될 때 해당 프론트 목업 함수를 순차 교체
