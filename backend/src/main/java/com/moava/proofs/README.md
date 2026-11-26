# 납입 증빙 · 납입 현황 (Proofs) — MOA-38 · MOA-45

> 관련 기능: F22 증빙 등록, F23 증빙 승인·보완 요청, F25 납입 현황 공개
> 기준 문서: 기능명세서 v3.3, DDL v1.7, API 명세서 v0.5, 알림 코드표 v3.1

회원이 매달 납입한 증거(입금 내역 캡처 등)를 올리고, 모임장이 확인하고, 모임원끼리 그 달 납입 현황을 보는 기능입니다.
여기서 정한 **"납입 = 그 달 승인(CONFIRMED)된 증빙이 있음"** 기준을 F26 미납 감지(`com.moava.overdue`)도 그대로 씁니다.

## 구성

| 경로 | 역할 |
|---|---|
| `controller/PaymentProofController` | F22·F23 증빙 API (`/api/v1/groups/{groupId}/payment-proofs`) |
| `controller/PaymentStatusController` | F25 납입 현황 API (`/api/v1/groups/{groupId}/payments`) |
| `controller/ProofExceptionHandler` | 증빙·참조번호·납입 현황·미납 컨트롤러 공통 오류 응답 (`assignableTypes`) |
| `service/PaymentProofService` | 등록 · 재제출 |
| `service/ProofReviewService` | 모임장 심사 (승인 / 보완 요청) |
| `service/ProofQueryService` | 목록 · 상세 · 파일 · 납입 현황 조회 |
| `service/ProofFileStorage` | 파일 검사 · 암호화 저장 · 복호화 |
| `service/ProofAccessChecker` | 모임 존재 · 모임장 · ACTIVE 회원 확인 (조회·변경 공통) |
| `resources/mapper/proofs/PaymentProofMapper.xml` | Oracle SQL |
| `test/.../proofs/` | `PaymentProofServiceTest`, `PaymentProofControllerTest` |

## 권한

- 로그인 회원 ID는 요청 값이 아니라 JWT(`Authentication.getName()`)에서 꺼냅니다.
- 모임장 판단은 `GYE_GROUP.OWNER_ID`(현재 모임장, 승계 시 바뀜) 기준입니다.
- 모든 서비스에서 모임 존재 · 모임장 · ACTIVE 회원(`GYE_MEMBER.STATUS='ACTIVE'`)을 서버에서 다시 확인합니다.

## API

| 메서드 | 경로 | 권한 | 동작 |
|---|---|---|---|
| POST | `/api/v1/groups/{groupId}/payment-proofs` (multipart) | ACTIVE 회원 | 증빙 등록 |
| GET | `/api/v1/groups/{groupId}/payment-proofs` | 모임장(전체) / 회원(본인 것만) | 증빙 목록 |
| GET | `/api/v1/groups/{groupId}/payment-proofs/{proofId}` | 모임장 / 작성자 | 증빙 상세 |
| GET | `/api/v1/groups/{groupId}/payment-proofs/{proofId}/files/{fileId}` | 모임장 / 작성자 | 파일 보기 (복호화) |
| PATCH | `/api/v1/groups/{groupId}/payment-proofs/{proofId}/review` | 모임장 | 승인(`CONFIRMED`) / 보완 요청(`REVISION`) |
| PUT | `/api/v1/groups/{groupId}/payment-proofs/{proofId}` (multipart) | 작성자 | 보완 요청 건 재제출 |
| GET | `/api/v1/groups/{groupId}/payments?payMonth=YYYYMM` | 모임장 / ACTIVE 회원 | 그 달 회원별 납입 현황 |

## 처리 흐름

### F22 증빙 등록 · 재제출
1. 필수값 · 귀속 월(`YYYYMM`) 형식 확인
2. 해산 완료(`DISSOLVED`) 모임 차단, ACTIVE 회원만
3. 회차 `1 ~ MONTHS`, 금액 = 모임 약정액
4. 파일 검사: 1~5개, 1MB 이하, JPG·PNG·WebP·PDF, 매직 넘버(실제 형식) 확인, SHA-256 해시 중복 차단
5. 같은 모임·회원·월·회차 중복 제출 차단 (409, `UK_PAYMENT_PROOF_1`로 동시 요청도 막음)
6. `PENDING`으로 저장, 파일은 AES-256-GCM 암호화 후 UUID 이름으로 저장
7. 모임장 알림 `PROOF_PENDING` (같은 모임 하루 1건, 모임장 본인 증빙이면 생략) + 감사 로그 `PROOF_UPLOAD`

재제출은 작성자 본인 · `REVISION` 상태만 가능하고, 같은 검사를 거쳐 `PENDING`으로 돌아갑니다 (`RESUBMIT_COUNT +1`, 감사 로그 `PROOF_RESUBMIT`).

### F23 모임장 심사
1. 모임장만, 해산된 모임은 불가 (모임 행 잠금으로 해산 처리와 동시 실행 방지)
2. `PENDING`만 심사 가능, 조건부 UPDATE로 중복 심사 시 409
3. 보완 요청은 사유 필수(500바이트) → 회원 알림 `PROOF_REVISION`
4. **승인되면 그 달 미납 건(`OVERDUE_CASE`)을 `RESOLVED`로 해소** (F26 연계). 승인 알림은 보내지 않음 (알림 축소 결정)
5. 감사 로그 `PROOF_CONFIRM` / `PROOF_REVISION`

### F25 납입 현황
1. 모임장 또는 ACTIVE 회원만, `payMonth` 생략 시 한국 시간 이번 달
2. 회원별 그 달 상태 1개: `CONFIRMED` > `PENDING` > `REVISION` > `NONE`(미제출)
3. **잠금:** 모임장이 아니고 본인 그 달 승인 증빙이 없으면 `locked=true`, 본인 행만 반환
4. 회원 이름은 실명 표시(팀 결정, PR #55 — F22 증빙 목록·관리자 화면은 마스킹 유지), 경고 횟수는 모임장에게만
5. 화면: 운영 홈(`/operations`) "모임 정보" 카드의 `PaymentStatusPanel`

## 정책 메모

- 감사 로그 `DESCRIPTION`에는 자유 입력값(사유 등)을 넣지 않습니다 (보완 사유는 `PAYMENT_PROOF.REVIEW_MEMO`에만 저장).
- 증빙을 올려도 모임장이 승인하기 전까지는 "납입"이 아닙니다. F26 미납 안내·미납 건도 이 기준을 따릅니다.
