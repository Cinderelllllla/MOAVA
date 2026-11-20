# 모임 절차 기능

`com.moava.group.procedures` 패키지 기능 정리.  
비교 기준은 `MOAVA_요구사항정의서_기능명세서_v3.1.xlsx`의 기능명세서  
F27(수령 순서), F29(중도 탈퇴), F32(해산) 및 관련 기능이며,  
계좌·승계(F19~F21), 규칙·경고(F30~F31) 등 다른 기능은 포함하지 않는다.

## 구성

| 경로 | 역할 |
|---|---|
| `controller/` | 절차별 REST API 진입점 및 공통 예외 처리(`ProcedureExceptionHandler`) |
| `dto/` | 요청·응답 모델 및 화면 호환 필드 |
| `exception/` | 절차 도메인 예외(`ProcedureException`) |
| `service/` | 권한·상태·동의·입력 검증, 행 잠금(`FOR UPDATE`) 기반 트랜잭션 흐름 |
| `mapper/` | MyBatis 인터페이스 |
| `backend/src/main/resources/mapper/procedures/` | Oracle SQL, 집계 및 상태 변경 |
| `backend/src/test/java/com/moava/group/procedures/service/` | 절차 단위 테스트(`DissolutionServiceTest`, `WithdrawalServiceTest`, `ProceduresWorkflowTest`) |

## 인증·권한 및 예외 처리

- 모든 절차 컨트롤러는 요청 DTO/헤더의 임의 ID가 아닌 Spring Security `Authentication`(JWT)의 로그인 주체 ID와 `ROLE_ADMIN` 권한을 사용한다.
- `SecurityConfig` 기준 접근 제어:
  - `/api/v1/admin/**`, `POST /api/v1/groups/*/withdrawals/*/consent-targets/*/exemption` → `ROLE_ADMIN`
  - `GET /api/v1/groups/*/withdrawals/*/consent-targets` → `ROLE_ADMIN` 또는 `ROLE_MEMBER`
  - 그 외 `/api/v1/groups/**` → `ROLE_MEMBER`
- 조회 API를 포함한 각 서비스 메서드에서 모임장(`OWNER_ID`), 활성 참여 회원(`GYE_MEMBER.STATUS = 'ACTIVE'`), 활성 관리자(`ADMIN.STATUS = 'ACTIVE'`) 여부를 서버 측에서 재검증한다.
- `ProcedureExceptionHandler`가 `ProcedureException`, `ResponseStatusException`, `HttpMessageNotReadableException`을 `ApiResponse.Error` 형식과 HTTP 상태 코드로 변환해 응답한다.

## 기능 및 API

### 수령 순서 — F27

기본 상태 흐름은 `REQUESTED → CONSENT_PENDING → CONFIRMED`이며, 동의 거부 또는 동일 회차 타 회원 확정 시 `REJECTED`가 된다. 모집 중(`RECRUITING`) 또는 운영 중(`ACTIVE`) 모임에서 처리 가능하며(`DISSOLVE_PENDING`/`DISSOLVED` 차단), `GYE_GROUP` 행 잠금으로 동시 상태 변경을 직렬화한다.

| 메서드 | 경로 | 권한 | 동작 |
|---|---|---|---|
| GET | `/api/v1/groups/{groupId}/payout-requests` | 모임장 또는 ACTIVE 회원 | 신청 및 동의 집계 조회 |
| POST | `/api/v1/groups/{groupId}/payout-requests` | ACTIVE 회원 | 신청 또는 `REQUESTED` 신청 수정 |
| POST | `/api/v1/groups/{groupId}/payout-requests/{requestId}/request-consent` | 모임장 | 신청자 제외 ACTIVE 회원에게 동의 요청 |
| POST | `/api/v1/groups/{groupId}/payout-requests/{requestId}/consents` | ACTIVE 동의 대상 회원 | 동의(`Y`) 또는 거부(`N`) 제출 |
| POST | `/api/v1/groups/{groupId}/payout-requests/{requestId}/confirm` | 모임장 | 동의·중복 확인 후 확정 |

- 신청 시 희망 회차 범위(`1 ~ MAX_MEMBERS`), 사유 필수 및 UTF-8 500바이트 이하, 기확정 회원 및 타 회원 기확정 회차 중복 여부를 검사하며 `CONSENT_PENDING` 상태에서는 수정할 수 없다.
- AI 추천 회차(`AI_SUGGESTED_ROUND`)는 회원별 최신 `RISK_SCORE.PROB`를 내림차순 정렬해 산출한다(동률이면 `MEMBER_ID` 오름차순).
- 동의 요청·확정 시 신청자의 현재 `ACTIVE` 상태와 기확정 회차 중복 여부를 재검증한다. 동의 응답은 대상 회원 여부를 먼저 확인해 비대상은 `403`, 이미 응답 완료(`AGREED_YN != 'P'`)는 `409`를 반환하며, 1명이라도 거부(`N`)하면 즉시 `REJECTED`로 전환하고 신청자에게 `CONSENT_RESULT` 알림을 발송한다.
- 확정 시 회차 중복, 회원 중복 확정, 동의 대상 스냅샷 존재 여부 및 ACTIVE 대상 전원 `'Y'` 동의를 확인한다. 확정 직후 동일 회차로 경합 중이던 타 회원의 미확정 신청(`REQUESTED`/`CONSENT_PENDING`)을 자동 `REJECTED` 처리하고 반려 알림(`CONSENT_RESULT`)을 발송한다. 모든 ACTIVE 회원의 신청이 확정되면 `GYE_GROUP.ORDER_CONFIRMED_YN`을 `'Y'`로 변경한다.

### 중도 탈퇴·정산 — F29

기본 상태 흐름은 `SETTLEMENT_PENDING → CONSENT_PENDING → APPROVED`이며, 정산안에 거부가 들어오면 `REJECTED`가 된다. 운영 중(`ACTIVE`) 모임에서만 진행 가능하며, 진행 중인 탈퇴 정산은 해산 신청 전에 완료해야 한다.

| 메서드 | 경로 | 권한 | 동작 |
|---|---|---|---|
| GET | `/api/v1/groups/{groupId}/withdrawals` | 모임장 또는 ACTIVE 회원 | 탈퇴 신청 및 동의 집계 조회 |
| POST | `/api/v1/groups/{groupId}/withdrawals` | 일반 ACTIVE 회원(모임장 제외) | 탈퇴 신청 |
| POST | `/api/v1/groups/{groupId}/withdrawals/{withdrawalId}/settlement` | 모임장 | 정산안 등록 및 동의 요청 |
| POST | `/api/v1/groups/{groupId}/withdrawals/{withdrawalId}/consents` | 신청자 제외 ACTIVE 대상 회원 | 정산안 동의(`Y`) 또는 거부(`N`) |
| POST | `/api/v1/groups/{groupId}/withdrawals/{withdrawalId}/reconfirm` | 신청자 본인(ACTIVE) | 탈퇴 재확인 요청 |
| POST | `/api/v1/groups/{groupId}/withdrawals/{withdrawalId}/approve` | 모임장 | 전원 동의/면제 확인 후 승인(`WITHDRAWN`) |
| GET | `/api/v1/groups/{groupId}/withdrawals/{withdrawalId}/consent-targets` | 모임장·ACTIVE 회원·ADMIN | 고정 동의 대상 및 응답/면제 현황 조회 |
| POST | `/api/v1/groups/{groupId}/withdrawals/{withdrawalId}/consent-targets/{memberId}/exemption` | 활성 ADMIN | 미응답 ACTIVE 대상의 동의 의무 면제 |

- 모임장은 신청할 수 없고(승계 후 가능), 곗돈 수령 완료자(`PAYOUT_RECEIVED_YN = 'Y'`)는 특별 정산 예외 신청(`SPECIAL_YN = 'Y'`)만 가능하다.
- 상세 사유(UTF-8 1000바이트 이하)와 정산 산출 근거(UTF-8 500바이트 이하)를 검증하며, 정산 금액은 Oracle `NUMBER(12)` 범위(`-999,999,999,999 ~ 999,999,999,999`)의 원 단위 `Long`으로 저장한다.
- 모임장이 정산안을 등록하거나(`SETTLEMENT_PENDING`/`CONSENT_PENDING`/`REJECTED` → `CONSENT_PENDING`) 재등록하면 신청자의 `ACTIVE` 상태와(반려 건 재활성화 시) 타 진행 중 탈퇴 신청 부재를 검증하고, `DECIDED_AT`을 초기화한 뒤 `WITHDRAWAL_CONSENT_TARGET`에 신청자 제외 ACTIVE 회원을 스냅샷으로 고정하고 `CONSENT_REQ` 알림을 발송한다. 정산안 거부(`N`) 시 신청자와(거부자가 모임장이 아니면) 모임장에게 `CONSENT_RESULT` 알림을 발송한다.
- 재확인 요청은 `SETTLEMENT_PENDING` 및 `CONSENT_PENDING` 상태에서 신청자 본인(ACTIVE)이 호출할 수 있으며 `RECONFIRM_COUNT`는 최대 99회로 제한된다.
- 승인 시 신청자의 미종결 증빙(`PAYMENT_PROOF.STATUS != 'CONFIRMED'`)이 없어야 하며, 고정 동의 대상 중 현재 `ACTIVE`인 회원 전원이 `AGREED` 또는 관리자 `EXEMPT`(면제 사유 1~200자 + `WITHDRAW_CONSENT_EXEMPT` 감사 로그) 상태일 때 `APPROVED` 및 `GYE_MEMBER.STATUS = 'WITHDRAWN'`으로 전환하고, 신청자의 미확정 수령 순서 신청을 자동 반려한 뒤 `WITHDRAW_DONE` 알림을 발송한다.

### 해산 — F32

기본 상태 흐름은 `DISSOLUTION_REQUEST: PENDING → APPROVED / REJECTED`이며, 모임 상태는 `ACTIVE → DISSOLVE_PENDING → DISSOLVED` 또는 반려 시 `ACTIVE`로 복귀한다.

| 메서드 | 경로 | 권한 | 동작 |
|---|---|---|---|
| GET | `/api/v1/groups/{groupId}/dissolution` | 모임장 또는 ACTIVE 회원 | 모임의 최신 해산 상태 및 정산 참고 정보 조회 |
| POST | `/api/v1/groups/{groupId}/dissolution` | 모임장 | 계모임 해산 신청 |
| GET | `/api/v1/admin/dissolutions` | 활성 ADMIN | 관리자 해산 목록 조회(`status`: `PENDING`/`APPROVED`/`REJECTED` 선택) |
| POST | `/api/v1/admin/groups/{groupId}/dissolution/approve` | 활성 ADMIN | 해산 최종 승인 |
| POST | `/api/v1/admin/groups/{groupId}/dissolution/reject` | 활성 ADMIN | 해산 신청 반려 |

- 신청 이력이 없는 모임의 상태 조회(`GET /api/v1/groups/{groupId}/dissolution`)는 `null` 대신 `status = "NONE"`, `statusBadge = "운영 중"` 구조화 응답을 반환한다.
- 관리자 해산 목록 조회(`GET /api/v1/admin/dissolutions`)는 활성 관리자(`ADMIN.STATUS = 'ACTIVE'`) 여부와 `status` 필터 값을 검증한다.
- 신청 시 `ACTIVE` 모임 여부, 진행 중 해산·(ACTIVE 회원의) 탈퇴·수령 순서 신청 부재, 해산 사유(UTF-8 800바이트 이하), 활성 관리자 존재 여부를 검증한 뒤 활성 관리자 전원에게 `DISSOLVE_REQUEST` 알림과 감사 로그를 기록한다.
- 승인 시 미종결 증빙(`PAYMENT_PROOF.STATUS != 'CONFIRMED'`), 진행 중 탈퇴(ACTIVE 회원 기준), 진행 중 수령 순서 신청(ACTIVE 회원 기준)이 모두 0건인지 확인한다.
- 승인 완료 시 전 회원에게 `DISSOLVED` 알림을 발송하되, 이미 지급된 곗돈과 중도 탈퇴 정산 내역이 반영되지 않은 단순 1/N 금액을 실제 환급액처럼 오해하지 않도록 알림 본문에서는 1/N 금액을 노출하지 않고 정산 안내 확인을 안내한다. 조회 응답의 `perMemberRefundAmount` 역시 단순 참고치임을 `settlementNotice`로 함께 안내한다.
- 반려 시 반려 사유(미입력 시 기본 문구 적용, UTF-8 300바이트 이하)를 담아 모임장에게 `DISSOLVE_REJECTED` 알림과 감사 로그를 남기며, 응답 DTO의 `approvedAt`은 `APPROVED` 상태일 때만 반환한다.

## 기능명세 대조

판정은 현재 백엔드 코드에서 확인되는 동작 기준이다. UI 표시만으로 서버 기능이 완료된 것으로 간주하지 않았다.

| 기능 | 판정 | 코드 근거 및 차이 |
|---|---|---|
| F27 수령 순서 신청·수정·동의·확정 | 구현 | `RECRUITING`/`ACTIVE` 상태 허용, 상태 전이, 신청 수정, 신청자·대상 ACTIVE 재검증, 1회 동의 응답 제한(비대상 403/중복 409), 거부 처리, 회차·회원 중복 확정 방지, 경합 신청 자동 반려, 전체 확정 플래그가 구현되어 있다. AI 추천 회차의 실제 운영 모델 입력·신선도 정합성은 ML 파이프라인 측 확인이 필요하다. |
| F29 중도 탈퇴·정산·전원 동의·면제 | 구현 | 정산안 등록(반려 재활성화 중복 방지, `NUMBER(12)` 범위 검증), 고정 동의 대상 스냅샷(`WITHDRAWAL_CONSENT_TARGET`), `CONSENT_PENDING` 포함 재확인 요청(상한 99회), 관리자 동의 면제 및 감사 로그, 미종결 증빙 사전 차단, 전원 동의/면제 검증 후 `WITHDRAWN` 처리 및 미확정 수령 순서 자동 정리가 구현되어 있다. |
| F29 필수 신청 입력 기본값 | 확인 필요 | `WithdrawalDTO.ApplyForm`이 사유 유형 누락 시 `PERSONAL`, 특별 정산 여부 누락 시 `N`을 기본 적용한다. 명세상 필수 입력으로 차단할지 제품 기준 확인이 필요하다. |
| F32 미종결 증빙 차단 | 구현 | `PAYMENT_PROOF.STATUS != 'CONFIRMED'`인 증빙이 1건이라도 있으면 승인을 차단한다. |
| F32 해산 사전조건 | 명세보다 추가 제한 | 미종결 증빙 검사 외에 ACTIVE 회원의 진행 중 탈퇴·수령 순서 신청도 해산 신청·승인을 차단한다. |
| F32 해산 정산 안내 | 참고치 안내 분리 | 조회 DTO의 `perMemberRefundAmount`는 확인 납입액 합계 ÷ 현재 ACTIVE 회원 수 단순 참고치이며 `settlementNotice`로 한계를 명시한다. 해산 승인 알림에는 단순 1/N 금액을 포함하지 않고 실제 정산 내역 확인을 안내한다. |
| F32 검색 인덱스 정리 | 미완료 | `DissolutionService.approveDissolutionByAdmin`에 Elasticsearch 문서 삭제가 TODO로 남아 있다(현재 프로젝트에 ES 연동 인프라 미구축). |
| 인증 주체·조회 권한 검증 | 구현 | 컨트롤러 전반이 JWT `Authentication` 주체·역할을 사용하고, 조회·변경 서비스 모두 모임장·ACTIVE 회원·활성 관리자 권한을 서버 측에서 재검증한다. |
| 프런트엔드 연동 | 미연동 | `frontend/src/api/procedures.js`가 아직 로컬 `./mock/db`를 사용하므로 화면 연동 작업이 남아 있다. |

## 확인 및 제한 사항

- 절차 단위 테스트는 `DissolutionServiceTest`, `WithdrawalServiceTest`, `ProceduresWorkflowTest`로 구성되어 있으며 Mockito 기반 단위 검증을 수행한다(실제 Oracle DB 통합 테스트는 별도).
- SQL은 Oracle 문법(`ROWNUM`, `NVL`, `SYSDATE`, `DUAL`, `FOR UPDATE`)을 사용한다.
- `PayoutOrderService`·`DissolutionService`는 `ResponseStatusException`을, `WithdrawalService`는 `ProcedureException`을 사용하며 둘 다 `ProcedureExceptionHandler`에서 동일한 `ApiResponse.Error` 형식으로 처리된다.
- 운영 AI 점수 파이프라인, 실제 Elasticsearch 검색 인덱스 연동, 프런트엔드 API 연결은 이 패키지 범위 밖이다.
