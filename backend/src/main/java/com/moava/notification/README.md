# 알림 (Notification) — MOA-40

> 담당: 박기태 · 관련 기능: F35 인앱 알림, F36 SMS · 기준 문서: 알림코드표 v3, API 명세서 v0.5

MOAVA의 모든 기능이 **같은 방법으로 알림을 보내고**, 회원·관리자가 **알림함에서 확인**할 수 있게 하는 모듈입니다.

| 구분 | 내용 | 상태 |
|---|---|---|
| 1. 알림 공통 함수 | 다른 기능이 호출해서 알림을 저장 (`notifyMember` / `notifyGroup` / `notifyAdmins`) | PR 올림 (#11 revert 후 재반영) |
| 2. 알림함 API | 목록 · 안 읽은 개수 · 읽음 · 모두 읽음 · 숨김 (`/api/v1/notifications`) | 다음 PR |
| 3. SMS 발송 | 커밋 후 비동기 발송 (SOLAPI) | 2차 — 지금은 로그만 |

---

## 1. 알림 공통 함수

### 왜 필요한가
알림은 가입·증빙·수령 순서·탈퇴·해산 등 거의 모든 기능에서 생깁니다. 각자 `NOTIFICATION`에 직접 INSERT하면 알림 종류·문구·이동 경로가 제각각이 되고, 필수 컬럼 누락으로 오류가 납니다.
**→ 알림은 `NotificationService`로만 보냅니다. NOTIFICATION 테이블에 직접 INSERT하지 마세요.**

### 함수 3개

| 함수 | 받는 사람 | 사용 예 |
|---|---|---|
| `notifyMember(memberId, type, refId, params)` | 회원 1명 | 증빙 보완 요청 → 제출자 |
| `notifyGroup(groupId, type, refId, params, excludeMemberIds)` | 모임의 **ACTIVE** 회원 전원 (탈퇴·강퇴 제외), `excludeMemberIds`는 빼고 | 수령 순서 동의 요청 → 신청자 빼고 전원 |
| `notifyAdmins(type, refId, params)` | **ACTIVE** 관리자 전원 | 해산 신청 접수 |

- `type` : 알림 코드 (`NotifType`, 아래 표)
- `refId` : 관련 업무 행의 ID (증빙 ID, 신청 ID 등). 없으면 `null`
- `params` : 제목·본문·이동 경로의 `{빈칸}`에 들어갈 값

### 사용 예시

```java
@Service
@RequiredArgsConstructor
public class ProofService {

    private final NotificationService notificationService;

    @Transactional
    public void requestRevision(long proofId, String reason) {
        // ... 증빙 상태 변경 ...

        notificationService.notifyMember(
                proof.getMemberId(), NotifType.PROOF_REVISION, proofId,
                Map.of("groupId", groupId, "group", groupName, "round", round, "reason", reason));
    }
}
```

저장 결과:

| 컬럼 | 값 |
|---|---|
| NOTIF_TYPE | `PROOF_REVISION` |
| TITLE | 증빙 보완이 필요해요 |
| BODY | 햇살계 3회차 증빙 보완 요청: 금액이 안 보여요 |
| LINK_PAGE | `/evidence?groupId=12` |
| REF_ID | 증빙 ID |

모임 전원 / 관리자 예시:

```java
// 수령 순서 동의 요청 → 신청자 빼고 모임 전원
notificationService.notifyGroup(groupId, NotifType.CONSENT_REQ, requestId,
        Map.of("groupId", groupId, "group", groupName, "item", "수령 순서 변경", "deadline", "11월 19일"),
        List.of(applicantId));

// 미납 5일차 → 관리자 전원
notificationService.notifyAdmins(NotifType.OVERDUE_5D, overdueCaseId,
        Map.of("group", groupName, "member", NameMasker.mask(memberName), "month", "11월", "days", 5));
```

### 알림 코드 21개 (`NotifType`)

`params`에 아래 **필요한 값**을 모두 넣어야 합니다. 빠지면 `IllegalArgumentException`이 납니다 (개발 중 바로 발견용).

**유저 (11)**

| 코드 | 언제 | 필요한 값 | 이동 경로 |
|---|---|---|---|
| `JOIN_RESULT` | 가입 승인·거절·만료 | group, result(승인/거절/만료) | /my-groups |
| `CONSENT_REQ` | 계좌 변경·모임장 승계·수령 순서·탈퇴 정산 동의 요청 | groupId, group, item, deadline | /procedures?groupId= |
| `CONSENT_RESULT` | 위 절차 확정·부결·만료 | groupId, item, group, result, detail(없으면 "") | /procedures?groupId= |
| `PROOF_REVISION` | 증빙 보완 요청 | groupId, group, round, reason | /evidence?groupId= |
| `PAY_DUE_3D` | 납입일 3일 전 (배치) | groupId, group, month, payDate, amount | /evidence?groupId= |
| `PAY_OVERDUE` | 미납 1일 차·5일 차 (배치) | groupId, group, month, days, guide | /evidence?groupId= |
| `SANCTION` | 모임 경고·강제 탈퇴, 가입 제한·해제 | scope(모임명 또는 MOAVA), action | /support |
| `WITHDRAW_DONE` | 탈퇴 처리 완료 | group | /my-groups |
| `DISSOLVED` | 해산 승인 (모임 전원) | group | /my-groups |
| `REPORT_STATUS` | 신고 처리 상태 변경 | status | /support |
| `RISK_UP` | AI 위험 등급 RISK 전환 | (없음) | /finance |

**유저(모임장) (6)**

| 코드 | 언제 | 필요한 값 | 이동 경로 |
|---|---|---|---|
| `JOIN_REQUEST` | 가입 신청 접수 | groupId, member, group | /operations?groupId= |
| `PROOF_PENDING` | 증빙 등록·재제출 | groupId, group | /evidence?groupId= |
| `ORDER_REQUEST` | 수령 순서 신청·수정 | groupId, member, group, round, stage(신청/수정) | /procedures?groupId= |
| `ACCOUNT_VERIFIED` | 관리자 계좌 소유 확인 완료 | groupId, group, bank, last4 | /operations?groupId= |
| `DISSOLVE_REJECTED` | 해산 신청 반려 | groupId, group, reason | /operations?groupId= |
| `WITHDRAW_REQUEST` | 탈퇴 신청·재확인 요청 | groupId, member, group, stage(신청/재확인 요청) | /procedures?groupId= |

**관리자 (4)**

| 코드 | 언제 | 필요한 값 | 이동 경로 |
|---|---|---|---|
| `ACCOUNT_VERIFY_REQ` | 모임 계좌 등록·변경 | group, bank, last4 | /admin/groups |
| `OVERDUE_5D` | 미납 5일 차 감지 | group, member, month, days | /admin/overdue |
| `DISSOLVE_REQUEST` | 해산 신청 | group, reason | /admin/dissolve |
| `REPORT_RECEIVED` | 신고 접수 | category, group | /admin/support |

> 코드표에 없는 알림이 필요하면 담당자(박기태)에게 요청해 주세요. `NotifType`에 추가합니다.
> 공개 설정 변경 · 운영 규칙 변경 · 곗돈 수령 기록 · 채팅 제한/해제 · 증빙 승인은 **알림을 보내지 않습니다** (채팅방 시스템 메시지 / 화면 표시로 대체, 알림 축소 결정).

### 지켜 주세요

1. **NOTIFICATION에 직접 INSERT 금지** — 반드시 `NotificationService` 사용
2. **다른 회원 이름은 가려서** — `NameMasker.mask("홍길동")` → `홍○동`
3. **본문에 민감 정보 금지** — 계좌는 끝 4자리(`last4`)만, 위험 확률·신고 내용·채팅 원문 넣지 않기
4. **모임 화면으로 가는 알림은 `groupId` 필수** — 이동 경로에 들어갑니다
5. **업무 `@Transactional` 메서드 안에서 호출** — 업무가 롤백되면 알림도 저장되지 않습니다

### 안에서 자동으로 처리하는 것

| 항목 | 내용 |
|---|---|
| 빈칸 검사 | 필요한 값이 없으면 예외 (`알림 PROOF_REVISION에 필요한 값 'reason'이(가) 없습니다.`) |
| 길이 제한 | 제목 100바이트 · 본문 500바이트 초과 시 자르고 `…` (한글 1자 = 3바이트) |
| 트랜잭션 | 호출한 쪽 트랜잭션에 참여 → 함께 커밋/롤백 |
| 전원 발송 | `INSERT … SELECT` 한 번으로 저장 (회원 목록을 불러와 반복하지 않음) |
| 회원/관리자 구분 | 회원 알림은 `MEMBER_ID`, 관리자 알림은 `ADMIN_ID`에 저장 (`CK_NOTIFICATION_RECEIVER` 규칙) |
| SMS | `NotifType`의 SMS 대상이면 커밋 후 `NotificationSmsListener`가 비동기 처리 (지금은 로그만) |

### 이 함수가 하지 않는 것
- **중복 방지** (증빙 알림 하루 1건, 미납 안내 1일·5일 차만 등) — 호출하는 쪽(배치)에서 조건을 걸어야 합니다
- **실제 SMS 발송** — 2차 작업

---

## 2. 알림함 API (다음 PR)

API 명세서 v0.5 F35 기준. **회원(S13)·관리자(A07) 공용** — 로그인 role이 MEMBER면 내 `MEMBER_ID`, ADMIN이면 내 `ADMIN_ID` 알림만 다룹니다.

| 기능 | 요청 | 응답 `data` |
|---|---|---|
| 목록 | `GET /api/v1/notifications?read=N&page=0&size=20` | `{items:[...], page, size, hasNext}` |
| 안 읽은 개수 (배지) | `GET /api/v1/notifications/unread-count` | `{count}` |
| 하나 읽음 | `PATCH /api/v1/notifications/{id}/read` | `{id, read:true}` |
| 모두 읽음 | `POST /api/v1/notifications/read-all` | `{updated}` |
| 선택 삭제(숨김) | `POST /api/v1/notifications/hide` + `{"ids":[1,2]}` | `{hidden}` |

- `read` : `N` 안 읽음 / `Y` 읽음 / 생략 시 전체. `size` 최대 50
- 목록 항목: `id, type, title, body, linkPage, refId, read, createdAt` (프론트 필드명 `id·title·body·read` 그대로)
- 응답 형식: `{"data": ..., "meta": {"requestId": "req-..."}}` / 오류: `{"error": {"code", "message"}, "requestId"}`
- 삭제는 실제 DELETE가 아니라 `DELETED_YN='Y'` (숨김)
- 남의 알림 ID → 읽음은 404, 숨김은 해당 건 무시

**로그인 전 임시 방식**: JWT가 붙기 전까지 `moava.dev.receiver-header-enabled=true`일 때만 `X-Member-Id` 또는 `X-Admin-Id` 헤더로 받는 사람을 정합니다. 기본값 false(401). **개인 PC 설정에서만 켜고 팀 공통 설정에 넣지 마세요.** JWT 연동 시 `NotificationController.currentReceiver()`만 교체합니다.

---

## 3. 파일 구조

```
backend/src/main/java/com/moava/
├── notification/common/
│   ├── domain/NotifType.java                  알림 코드 21개 (템플릿·이동 경로·SMS 여부)
│   ├── service/NotificationService.java       공통 함수 3개
│   ├── service/NotificationSmsListener.java   SMS 자리 (커밋 후 @Async, 지금은 로그)
│   ├── mapper/NotificationMapper.java         INSERT 매퍼
│   ├── dto/NotificationInsert.java · NotificationSmsEvent.java
│   │   ── 아래는 알림함 API (다음 PR) ──
│   ├── controller/NotificationController.java
│   ├── service/NotificationInboxService.java · NotificationException.java
│   ├── mapper/NotificationInboxMapper.java
│   └── dto/NotificationItem · NotificationListResponse · NotificationHideRequest · NotificationReceiver
├── common/util/NameMasker.java                이름 마스킹 (F03)
├── common/config/AsyncConfig.java             @EnableAsync
└── common/response/ApiResponse.java           명세서 §2 공통 응답 (알림함 API와 함께)

backend/src/main/resources/mapper/notification/common/
├── NotificationMapper.xml
└── NotificationInboxMapper.xml                (다음 PR)
```

## 4. 남은 작업

- [ ] 알림함 API PR
- [ ] JWT 연동 후 임시 헤더 방식 제거
- [ ] SMS 규칙 v0.5 반영 — 자동 미납 안내 SMS 끄기, 관리자 경고(`OVERDUE_WARNED`) 시 SMS (코드표 v3.1)
- [ ] 배치: 오래된 알림 정리(읽음 30일·안 읽음 90일), 납입 3일 전·미납 안내
- [ ] 2차: SOLAPI SMS 발송 (`PHONE_ENC`가 NULL인 탈퇴 회원은 건너뜀)
