# 모임 절차 기능

`com.moava.group.procedures` 패키지 기능 정리.  
비교 기준은 `MOAVA_요구사항정의서_기능명세서_v3.1.xlsx`의 기능명세서  
F27(수령 순서), F29(중도 탈퇴), F32(해산) 및 관련 기능  
계좌·승계(F19~F21), 규칙·경고(F30~F31) 등 다른 기능은 포함하지 않는다.

## 구성

| 경로 | 역할 |
|---|---|
| `controller/` | 절차별 REST API 진입점 |
| `dto/` | 요청·응답 모델 및 화면 호환 필드 |
| `service/` | 권한·상태·동의 검증, 트랜잭션 흐름 |
| `mapper/` | MyBatis 인터페이스 |
| `backend/src/main/resources/mapper/procedures/` | SQL, 집계 및 상태 변경 |

## 기능 및 API

### 수령 순서 — F27

기본 상태 흐름은 `REQUESTED → CONSENT_PENDING → CONFIRMED`이며, 동의 거부 시 `REJECTED`가 됩니다. `REQUESTED` 신청은 신청자 본인이 수정할 수 있고, `CONSENT_PENDING` 상태에서는 수정할 수 없습니다.

| 메서드 | 경로 | 동작 |
|---|---|---|
| GET | `/api/v1/groups/{groupId}/payout-requests` | 신청 및 동의 집계 조회 |
| POST | `/api/v1/groups/{groupId}/payout-requests` | 신청 또는 `REQUESTED` 신청 수정 |
| POST | `/api/v1/groups/{groupId}/payout-requests/{requestId}/request-consent` | 모임장이 신청자 이외의 ACTIVE 회원에게 동의 요청 |
| POST | `/api/v1/groups/{groupId}/payout-requests/{requestId}/consents` | 동의 또는 거부 제출 |
| POST | `/api/v1/groups/{groupId}/payout-requests/{requestId}/confirm` | 모임장이 동의 및 회차 중복을 확인하고 확정 |

신청 시 회차 범위와 사유를 검사하고, AI 추천 회차는 회원별 최신 `RISK_SCORE.PROB`를 내림차순 정렬해 산출합니다(동률이면 회원 ID 오름차순). 확정 후 모든 ACTIVE 회원의 신청이 확정되면 `GYE_GROUP.ORDER_CONFIRMED_YN`을 `Y`로 변경합니다.

### 중도 탈퇴·정산 — F29

기본 상태 흐름은 `SETTLEMENT_PENDING → CONSENT_PENDING → APPROVED`이며, 정산안에 거부가 들어오면 `REJECTED`가 됩니다. 모임장은 신청할 수 없고, 곗돈 수령 완료자는 특별 정산 예외 신청만 할 수 있습니다. 모임장이 정산 금액과 근거를 등록하면 신청자를 제외한 ACTIVE 회원의 동의가 필요합니다.

| 메서드 | 경로 | 동작 |
|---|---|---|
| GET | `/api/v1/groups/{groupId}/withdrawals` | 탈퇴 신청 및 동의 집계 조회 |
| POST | `/api/v1/groups/{groupId}/withdrawals` | 탈퇴 신청 |
| POST | `/api/v1/groups/{groupId}/withdrawals/{withdrawalId}/settlement` | 모임장이 정산안 등록 및 동의 요청 |
| POST | `/api/v1/groups/{groupId}/withdrawals/{withdrawalId}/consents` | 정산안 동의 또는 거부 |
| POST | `/api/v1/groups/{groupId}/withdrawals/{withdrawalId}/reconfirm` | 신청자 재확인 요청 |
| POST | `/api/v1/groups/{groupId}/withdrawals/{withdrawalId}/approve` | 모임장이 동의 완료 후 승인, 회원 상태를 `WITHDRAWN`으로 변경 |

탈퇴 신청과 주요 상태 변경에는 알림 및 감사 로그가 기록됩니다. 정산 금액은 원 단위로 저장하며, 작은 정수 입력은 기존 화면 호환을 위해 만 원 단위로 변환하는 DTO 로직이 있습니다.

### 해산 — F32

기본 상태 흐름은 `DISSOLUTION_REQUEST: PENDING → APPROVED/REJECTED`이며 모임 상태는 `ACTIVE → DISSOLVE_PENDING → DISSOLVED` 또는 반려 시 `ACTIVE`로 복귀합니다.

| 메서드 | 경로 | 동작 |
|---|---|---|
| GET | `/api/v1/groups/{groupId}/dissolution` | 모임의 최신 해산 상태와 정산 예상액 조회 |
| POST | `/api/v1/groups/{groupId}/dissolution` | 모임장의 해산 신청 |
| GET | `/api/v1/admin/dissolutions` | 관리자 해산 목록 조회(`status` 필터 선택) |
| POST | `/api/v1/admin/groups/{groupId}/dissolution/approve` | 관리자가 승인 |
| POST | `/api/v1/admin/groups/{groupId}/dissolution/reject` | 관리자가 반려 |

신청 시 활성 관리자에게 알림을 보내고, 승인 시 미확정 증빙·진행 중 탈퇴·진행 중 수령 순서 신청을 확인합니다. 승인 알림의 1인 예상액은 확인된 납입액 합계를 현재 ACTIVE 회원 수로 나눈 값이며 실제 송금이나 확정된 환급액이 아닙니다.

## 기능명세 대조

판정은 현재 백엔드 코드에서 확인되는 동작 기준입니다. UI 표시만으로 서버 기능이 완료된 것으로 간주하지 않았습니다.

| 기능 | 판정 | 코드 근거 및 차이 |
|---|---|---|
| F27 수령 순서 신청·수정·동의·확정 | 대부분 구현 | 상태 전이, 신청 수정, 신청자 제외 동의, 거부 처리, 동의 수 집계, 회차 중복 방지, 전체 확정 플래그가 있습니다. AI 제안은 최신 `RISK_SCORE.PROB` 기반 순위이며, 실제 운영 모델의 입력·신선도·연체확률 의미가 요구사항과 일치하는지는 이 코드만으로 확인할 수 없습니다. |
| F29 정산안·전원 동의·탈퇴 승인 | 부분 구현 | 정산 금액·근거, ACTIVE 회원 동의, 전원 동의 검증 및 `WITHDRAWN` 처리가 있습니다. 명세는 동의 단계 뒤 신청자의 재확인 요청을 정의하지만, 현재 재확인 SQL은 `SETTLEMENT_PENDING` 또는 `REJECTED`만 허용하고 동의 요청 중인 `CONSENT_PENDING`은 허용하지 않습니다. |
| F29 필수 신청 입력 | 확인 필요 | DTO가 사유 유형이 누락되면 `PERSONAL`, 특별 정산 여부가 누락되면 `N`으로 기본 처리합니다. 명세의 사유 분류 및 특별 정산 입력을 필수로 다룰지 제품 기준 확인이 필요합니다. |
| F32 미종결 증빙 차단 | 구현 | `STATUS != CONFIRMED`인 증빙이 하나라도 있으면 승인을 차단합니다. |
| F32 해산 승인 사전조건 | 명세보다 추가 제한 | 요구사항에 명시된 미종결 증빙 검사 외에, 진행 중 탈퇴와 수령 순서 신청도 해산 승인을 막습니다. 의도된 업무 규칙인지 확인이 필요합니다. |
| F32 1/N 안내 | 제한된 예상값 | 확인 납입액 합계/현재 ACTIVE 회원 수를 안내합니다. 실제 잔액, 이미 지급된 수령액 및 별도 정산 내역을 반영하는 정산 원장으로 확인되지는 않습니다. |
| F32 검색 인덱스 정리 | 미완료 | Elasticsearch 문서 삭제가 `DissolutionService`에 TODO로 남아 있습니다. 실제 검색 인덱스 연동 여부와 무관하게 이 서비스에서 완료됐다는 근거는 없습니다. |
| 서버 인증 주체와 행위자 검증 | 운영 전 확인 필요 | 수령 순서 API는 `X-Member-Id`, 탈퇴·해산 API는 요청 DTO의 회원/관리자 ID를 받습니다. 서비스는 그 ID와 모임장·관리자 ID 비교를 하지만, 이 패키지에는 그 ID를 로그인 사용자(JWT 등)와 결속하는 처리가 보이지 않습니다. 별도 보안 계층이 있더라도 각 요청자가 다른 ID를 제출할 수 없도록 연결돼 있는지 검증해야 합니다. 이는 REQ-SEC-01/04의 인증 및 서버 측 권한 재검증과 관련됩니다. |
| 조회 권한·개인정보 범위 | 확인 필요 | 탈퇴 목록과 해산 상세·관리자 목록 API는 컨트롤러에서 사용자 인증/모임 참여 여부를 확인하지 않습니다. 응답에는 신청 사유, 회원 이름, 정산 정보 또는 집계 정보가 포함됩니다. 외부 보안 설정에서 목록·상세 조회 권한과 데이터 범위를 제한하는지 확인해야 합니다. |
| 프런트엔드 연동 | 미연동 | 현재 `frontend/src/api/procedures.js`는 `./mock/db`를 사용하고, 절차 화면은 이 mock 기능을 호출합니다. 따라서 화면에서의 성공 동작은 이 백엔드 API가 실제 연결·동작한다는 근거가 아닙니다. |

## 코드 변경 제안 — 미적용

| 우선도 | Before | After 제안 |
|---|---|---|
| 운영 전 필수 확인 | 요청 헤더/DTO에서 받은 회원·관리자 ID를 서비스가 검증 주체로 사용 | 로그인 인증 주체에서 ID와 역할을 가져오도록 연결하고, ID 위조가 불가능한지 보안 설정 및 API 통합 테스트로 확인 |
| F29 흐름 정합성 | 재확인 요청 SQL이 `SETTLEMENT_PENDING`, `REJECTED`만 허용 | 명세 순서대로 정산안 동의 단계에서 재확인을 할 수 있도록 `CONSENT_PENDING` 허용 여부를 확정하고 상태 조건을 맞춤 |
| F32 완료 처리 | 해산 승인 후 검색 인덱스 정리가 TODO | 검색 인덱스 연동이 존재하는 경우 승인 완료 시 해당 모임 문서를 제거하고 실패 처리·재시도 정책을 정함 |
| 화면 연동 | 절차 화면이 로컬 mock DB에 읽고 씀 | 백엔드 API와 연결하고 실제 권한/오류/상태 흐름으로 검증. 프런트엔드 변경이 필요하므로 이 패키지 문서 범위 밖의 작업임 |

## 확인 및 제한 사항

- 이 디렉터리와 `backend/src/test/`에서 절차 전용 자동화 테스트를 확인하지 못했습니다. 테스트 부재가 전체 프로젝트 테스트 부재를 의미하지는 않습니다.
- SQL은 Oracle 문법(`ROWNUM`, `NVL`, `SYSDATE`, `DUAL`)을 사용합니다.
- 인증 설정, 운영 AI 점수 파이프라인 및 실제 검색 인덱스 구성은 이 패키지 밖이므로 본 문서만으로 완료 여부를 판정하지 않았습니다.
- 위 Before/After는 검토 제안이며, 코드에는 반영하지 않았습니다.
