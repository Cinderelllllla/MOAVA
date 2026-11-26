# 미납 자동 감지 · 관리자 조치 (Overdue) — MOA-45

> 관련 기능: F26 미납 자동 감지 및 관리자 조치 · 화면: A06 `/admin/overdue`
> 기준 문서: 기능명세서 v3.3, DDL v1.7 (`OVERDUE_CASE`), 알림 코드표 v3.1

매일 새벽 배치가 납입 안내를 보내고 미납을 찾아 관리자에게 알리면, 관리자가 경고·채팅 제한 등 조치를 하는 기능입니다.
**미납 판단 기준은 F22~F25와 같은 "그 달 승인(CONFIRMED)된 증빙이 없음"**입니다.

## 구성

| 경로 | 역할 |
|---|---|
| `service/OverdueScheduler` | 매일 02:00(Asia/Seoul) 실행, 결과 요약 로그 |
| `service/OverdueDetectService` | 안내 대상 조회 · 회원 알림 · 새 미납 건 처리 (대상별로 실패 격리) |
| `service/OverdueCaseRegistrar` | 새 미납 건 1건 등록 (저장 + 관리자 알림 + 감사 로그를 한 트랜잭션) |
| `service/OverdueAdminService` | 관리자 목록 · 조치 |
| `controller/OverdueController` | 관리자 API (`/api/v1/admin/overdue-cases`) |
| `resources/mapper/overdue/OverdueMapper.xml` | 미납 판단 SQL, 조건부 UPDATE, 행 잠금 |
| `test/.../overdue/` | `OverdueDetectServiceTest`, `OverdueCaseRegistrarTest`, `OverdueAdminServiceTest`, `OverdueControllerTest` |

## 1. 배치 (매일 02:00)

### 미납 판단 SQL (`unpaidDues`)
운영 중(`ACTIVE`) 모임의 ACTIVE 회원 × 각 달 납입일(`PAY_DAY`)에 대해 아래를 모두 만족하면 "미납 후보"입니다.
- 납입일 ≥ 모임 시작일, 납입일 ≥ 회원 가입일, 납입일 < 시작일 + 운영 개월 수
- 그 달 승인(`CONFIRMED`) 증빙이 없음
- 경과일 = 오늘 − 납입일 (음수면 아직 납입일 전)

### 하는 일

| 시점 | 대상 | 처리 |
|---|---|---|
| 납입일 3일 전 (경과일 −3) | 그 달 증빙을 아직 안 올린 회원 (승인·대기 제외, 보완 요청은 포함) | 회원 알림 `PAY_DUE_3D` (SMS) |
| 미납 1일 차 · 5일 차 (경과일 1, 5) | 승인 증빙 없는 회원 | 회원 알림 `PAY_OVERDUE` (인앱) |
| 미납 5일 차 이상 (경과일 ≥ 5) + 아직 미납 건 없음 | 승인 증빙 없는 회원 | `OVERDUE_CASE`(`DETECTED`) 저장 → 관리자 전원 `OVERDUE_5D`(회원 이름 마스킹) → 감사 로그 `OVERDUE_DETECT` |

예) 납입일 11/5 → 11/2 3일 전 안내, 11/6 1일 차 안내, 11/10 5일 차 안내 + 미납 건 생성

- 3일 전 안내는 알림 담당자와 합의해 이 배치에서 같이 보냅니다 (월말 → 다음 달 1~3일 납입일도 잡히도록 다음 달까지 조회).
- 납입일 1~3일, 월말 경계(11/28 → 12/1)는 로컬 DB에서 확인했습니다.

### 실패 처리 · 중복 방지
- 배치 전체를 트랜잭션 하나로 묶지 않습니다. **한 명의 알림이 실패해도 나머지는 계속 진행**하고, 실패 건은 ID만 로그로 남깁니다(이름 미기록).
- 새 미납 건 등록은 `OverdueCaseRegistrar`(별도 클래스)에서 건별 트랜잭션으로 처리합니다.
  - 같은 클래스 안에서 `@Transactional` 메서드를 부르면 프록시를 거치지 않아 트랜잭션이 걸리지 않기 때문에 클래스를 나눴습니다.
  - 등록 중 실패하면 그 건만 롤백 → DB에 남지 않으므로 **다음 날 배치에서 다시 대상**이 됩니다.
- 같은 건이 두 번 저장되려 하면 `UK_OVERDUE_CASE_1`(모임·회원·월) 위반 → 건너뜀 (관리자 알림도 다시 안 감)
- 해소: 모임장이 증빙을 승인하면 F23(`ProofReviewService`)에서 `RESOLVED`로 바꿉니다.

### 알려진 한계
- 안내 알림(3일 전 · 1일 · 5일 차)은 "그날 하루만" 보냅니다. 서버가 꺼져 있던 날의 안내는 다시 보내지 않습니다 (미납 건은 `>= 5`라 다음 날 잡힘).
- 회원 안내 중복 방지는 "하루 1회 실행 · 서버 1대"를 전제로 합니다.
- 미납 건 생성에 경과일 상한이 없습니다(명세 기준). **시연 데이터를 넣을 때 운영 중 모임의 지난달 증빙을 승인 상태로 같이 넣어야** 첫 배치 때 미납 건·관리자 알림이 몰리지 않습니다.

## 2. 관리자 API

권한: 관리자 토큰(`ROLE_ADMIN`, `subject = ADMIN_ID`) + 서비스에서 ACTIVE 관리자 재확인. 회원 토큰은 같은 번호여도 403.

| 메서드 | 경로 | 동작 |
|---|---|---|
| GET | `/api/v1/admin/overdue-cases?status=OPEN\|RESOLVED\|ALL` | 미납 목록 (기본 OPEN, 회원 이름 마스킹, 경과일·채팅 제한 여부 포함) |
| POST | `/api/v1/admin/overdue-cases/{overdueCaseId}/actions` | 조치 (`{"action": "...", "reason": "..."}`) |

### 조치 4종

| action | 처리 | 회원 알림 | 감사 로그 |
|---|---|---|---|
| `WARN` | 미납 건 `WARNED` · `WARNED_AT` (SANCTION·경고 횟수는 안 건드림, F30 모임장 경고와 별개) | `OVERDUE_WARNED` (SMS) | `OVERDUE_WARN` |
| `CHAT_RESTRICT` | `GYE_MEMBER.CHAT_RESTRICTED_YN` N→Y, 미납 건 `RESTRICTED`, `SANCTION` 기록 | 없음 | `CHAT_RESTRICT` |
| `CHAT_RELEASE` | `CHAT_RESTRICTED_YN` Y→N, `SANCTION` 기록 (미납 건 상태는 유지) | 없음 | `CHAT_RELEASE` |
| `ORDER_REVIEW` | `SANCTION`만 기록 (**수령 순서 자동 변경 없음**) | 없음 | `ORDER_REVIEW` |

- 채팅 제한·해제·후순위 검토는 사유 필수(500바이트). 사유는 `SANCTION.REASON`에만 저장하고 감사 로그에는 `SANCTION #번호`만 남깁니다.
- 채팅 제한·해제 알림 없음 (알림 코드표 v3.1: 채팅 시스템 메시지·화면 표시로 대체)
- 실제 채팅 전송 차단은 채팅 기능(F34 `ChatService`)이 `CHAT_RESTRICTED_YN`과 최근 `SANCTION`(`CHAT_RESTRICT`/`CHAT_RELEASE`)을 읽어서 처리합니다.

### 상태 · 동시성 규칙
- 모임 규칙이 알림만(`OVERDUE_POLICY = ALERT_ONLY`)이면 채팅 제한 409
- 해소(`RESOLVED`)된 건은 `CHAT_RELEASE`만 가능 (납입 후 제한 풀기)
- 조건부 UPDATE로 이미 경고함 · 이미 제한 중 · 제한 중 아님을 409로 막음
- 조치 대상 미납 건을 `SELECT ... FOR UPDATE OF o.OVERDUE_CASE_ID`로 잠가서, 증빙 승인(RESOLVED 처리)과 동시에 실행돼도 상태가 어긋나지 않게 함 (두 세션 잠금 동작 로컬 DB 확인)
