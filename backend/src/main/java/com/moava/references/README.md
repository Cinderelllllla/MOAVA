# 거래 참조번호 (References) — MOA-45

> 관련 기능: F24 거래 참조번호 지정 · 화면: A05 `/admin/references`
> 기준 문서: 기능명세서 v3.3, DDL v1.7 (`PAYMENT_PROOF.REF_NO`, `SEQ_REF_NO`)

모임장이 승인한 납입 증빙에 관리자가 **거래 참조번호(`MOAVA-00001` 형식)**를 붙여, 나중에 분쟁·정산 때 거래를 하나로 찾을 수 있게 하는 기능입니다.
(팀 확인 11/19: 화면목록의 "변경 불가" 대신 기능명세서대로 **사유를 적고 정정 가능**)

## 구성

| 경로 | 역할 |
|---|---|
| `controller/RefNoController` | 관리자 API (`/api/v1/admin/payment-proofs`), 관리자 토큰 확인 |
| `service/RefNoService` | 목록 · 번호 제안 · 지정 · 정정 |
| `resources/mapper/references/RefNoMapper.xml` | Oracle SQL (조건부 UPDATE, 시퀀스 미리 보기) |
| `test/.../references/` | `RefNoServiceTest`, `RefNoControllerTest` |

오류 응답은 `proofs/controller/ProofExceptionHandler`를 같이 씁니다.

## 권한 (관리자)

- 관리자 로그인 토큰: `subject = ADMIN_ID`, 권한 `ROLE_ADMIN`
- **회원 ID와 관리자 ID는 같은 숫자일 수 있어서**, 숫자만 보지 않고 아래 두 단계로 확인합니다.
  1. 컨트롤러: `ROLE_ADMIN`이 없으면 403, 로그인 정보가 없으면 401
  2. 서비스: `ADMIN.STATUS = 'ACTIVE'`인 관리자인지 DB에서 다시 확인 (아니면 403)
- `SecurityConfig` URL 규칙에 기대지 않고 위 두 곳에서 직접 막습니다.

## API

| 메서드 | 경로 | 동작 |
|---|---|---|
| GET | `/api/v1/admin/payment-proofs?ref=UNASSIGNED\|ASSIGNED` | 참조번호 미지정(기본) / 지정 완료 증빙 목록 (회원 이름 마스킹) |
| GET | `/api/v1/admin/payment-proofs/ref-no/suggestion` | 다음 번호 미리 보기 (시퀀스 소모 없음) |
| PATCH | `/api/v1/admin/payment-proofs/{proofId}/ref-no` | 지정 (`refNo` 생략 시 서버가 다음 번호로) |
| PATCH | `/api/v1/admin/payment-proofs/{proofId}/ref-no/correction` | 정정 (새 번호 + 사유 필수) |

## 처리 흐름

### 지정
1. 승인(`CONFIRMED`)된 증빙만, 이미 번호가 있으면 409 (→ 정정 사용)
2. 번호를 직접 넣으면 형식(`MOAVA-00001`)·중복 확인, 생략하면 시퀀스에서 다음 번호 (수동 입력 번호와 겹치면 최대 10번 다음 번호로)
3. 조건부 UPDATE(번호가 비어 있을 때만) → 0건이면 다른 관리자가 먼저 처리한 것이라 409
4. 같은 번호가 동시에 들어오면 `UK_PAYMENT_PROOF_REF_NO` 위반 → 409
5. 감사 로그 `REF_ASSIGN`

### 정정
1. 새 번호 · 사유 필수, 사유는 600바이트(한글 약 200자) 이하
2. **사유에 계좌번호·연락처 같은 10자리 이상 숫자가 있으면 400** (감사 로그에 개인정보가 남지 않게)
   - 날짜(`2026-11-24`)는 검사 전에 빼서, "2026-11-24 10시 입금 건" 같은 사유는 통과
   - 완벽한 차단이 아니라 실수로 적는 것을 막는 장치입니다 (관리자 전용 입력칸)
3. 번호가 있는 증빙만, 현재 번호와 다른 번호만
4. 조건부 UPDATE(현재 번호가 그대로일 때만) → `REF_CORRECT_COUNT +1`
5. 감사 로그 `REF_CORRECT` (이전 번호 · 새 번호 · 사유)

## 참고

- 번호 제안은 `USER_SEQUENCES.LAST_NUMBER`를 읽습니다. DDL이 `NOCACHE`라 정확하지만, 시퀀스를 CACHE로 바꾸면 값이 어긋날 수 있습니다. 제안값일 뿐이라 저장은 항상 안전합니다.
