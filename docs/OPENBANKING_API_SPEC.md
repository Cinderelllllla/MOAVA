# MOAVA 금융결제원 오픈뱅킹 v2.0 모의 API 명세서

본 문서는 `com.moava.openbanking` 패키지에 독립 구현된 **금융결제원(KFTC) 오픈뱅킹 v2.0 모의 은행 API** 호출 규격을 정리한 팀원 공유용 명세서입니다.

---

## 1. 환경 설정 및 Base URL 전환

### 1-1. 포트 및 연동 설정 (`backend/src/main/resources/application.properties`)

```properties
# MOAVA 메인 백엔드 서버 포트 (프론트엔드 Vite 프록시 8080 유지)
server.port=8080

# 오픈뱅킹 연동 대상 Base URL (별도 모의은행 인스턴스 포트 8081 기준)
# - 단일 서버(8080)에서 로컬 테스트 시: http://localhost:8080 으로 변경
# - 별도 모의은행 프로세스 기동 시:    java -jar moava.jar --server.port=8081
# - KFTC 테스트베드 전환 시:          https://testapi.openbanking.or.kr
moava.openbanking.base-url=http://localhost:8081
moava.openbanking.org-code=M202600001
```

> **민감 정보 관리:** `moava.openbanking.client-id`, `moava.openbanking.client-secret`은 커밋 대상인 `application.properties`에 넣지 않고 `application-secret.properties`에 설정합니다. (미설정 시 로컬 데모 기본값 `moava-demo-client` / `moava-demo-secret` 사용)

### 1-2. 코드 수정 없는 서버 전환 (`OpenBankingClient`)

비즈니스 서비스에서는 컨트롤러/원장 서비스를 직접 참조하지 않고 `OpenBankingClient` 인터페이스를 주입받아 호출합니다.

```java
@RequiredArgsConstructor
public class ExampleConsumerService {
    private final OpenBankingClient openBankingClient;

    public void sample() {
        KftcOAuthDto.TokenResponse token = openBankingClient.issueOobToken();
        KftcAccountDto.BalanceResponse balance =
                openBankingClient.inquireBalance(token.getAccessToken(), "199000000112345678900001", null);
    }
}
```

---

## 2. Oracle 가상 원장 DB 준비 (`infra/sql/05_moava_openbank_ddl.sql`)

| 테이블명 | 역할 | 핵심 제약조건 |
|---|---|---|
| `MOCK_KFTC_ACCOUNT` | 모의 계좌 마스터 | `PK(FINTECH_USE_NUM)`, `UK(BANK_CODE, ACCOUNT_NO)`, `CHECK(BALANCE >= 0)` |
| `MOCK_KFTC_TRANSACTION` | 모의 입출금 거래 원장 | `UK(BANK_TRAN_ID, FINTECH_USE_NUM, INOUT_TYPE)`(이중 이체 방지), `CHECK(TRAN_AMT > 0)` |
| `MOCK_KFTC_OAUTH_CODE` | OAuth 인가 코드 (일회용, 3분 TTL) | `PK(AUTH_CODE)`, `USED_YN IN ('Y','N')` |
| `MOCK_KFTC_OAUTH_TOKEN` | OAuth Access Token (SHA-256 해시 저장) | `PK(TOKEN_HASH)`, `REVOKED_YN IN ('Y','N')` |

### 시연용 샘플 계좌 시드 SQL 예시

```sql
-- 일반 회원 출금 계좌 (USER_SEQ_NO = 1000000001)
INSERT INTO MOCK_KFTC_ACCOUNT (
    FINTECH_USE_NUM, BANK_CODE, BANK_NAME, ACCOUNT_NO, HOLDER_NAME, USER_SEQ_NO, BALANCE, LEADER_YN, STATUS
) VALUES (
    '199000000112345678900001', '088', '신한은행', '110-123-456789', '홍길동', '1000000001', 1500000, 'N', 'ACTIVE'
);

-- 모임장 수취 계좌 (LEADER_YN = 'Y' 인 계좌만 입금 대상으로 허용)
INSERT INTO MOCK_KFTC_ACCOUNT (
    FINTECH_USE_NUM, BANK_CODE, BANK_NAME, ACCOUNT_NO, HOLDER_NAME, USER_SEQ_NO, BALANCE, LEADER_YN, STATUS
) VALUES (
    '199000000112345678900099', '004', '국민은행', '123401-04-987654', '김모임', '1000000099', 500000, 'Y', 'ACTIVE'
);
COMMIT;
```

---

## 3. 공통 응답 규격 및 표준 예외 코드

모든 API 응답(정상 및 오류)은 금융결제원 표준 공통 필드를 포함합니다.

```json
{
  "api_tran_id": "550e8400-e29b-41d4-a716-446655440000",
  "api_tran_dtm": "20261124120000123",
  "rsp_code": "A0000",
  "rsp_message": "정상 처리되었습니다."
}
```

### 표준 응답 코드표 (`KftcResponseCode`)

| `rsp_code` | HTTP 상태 | 의미 |
|---|---:|---|
| `A0000` | 200 | 정상 처리 |
| `A0001` | 404 | 존재하지 않거나 거래 불가능한 계좌 |
| `A0002` | 400 | 출금 계좌 잔액 부족 |
| `A0003` | 400 | 예금주 실명 불일치 |
| `A0004` | 400 | 0원 이하 또는 1회 한도(1억원) 초과 비정상 금액 요청 |
| `A0005` | 403 | 허용된 모임장 계좌(`LEADER_YN='Y'`) 외 타 계좌 이체 차단 |
| `A0006` | 409 | 이미 처리된 은행거래고유번호(`bank_tran_id`) 중복 요청 차단 |
| `A0007` | 401 | 유효하지 않거나 만료/철회된 OAuth 인증 토큰 |
| `A0008` | 403 | 필요한 `scope` 부족 또는 타 사용자 계좌 접근 차단 |
| `A0009` | 400 | 요청 파라미터 형식 오류 (24자리 핀테크번호, 20자리 `bank_tran_id`, 날짜, XSS 특수문자 등) |
| `A0010` | 429 | Rate Limit 초과 또는 반복 인증 실패로 인한 일시 차단 |
| `A0011` | 400 | 출금 계좌와 입금 계좌가 동일함 |
| `A9999` | 500 | 내부 처리 오류 |

---

## 4. OAuth 2.0 인증 API (`/oauth/2.0`)

### 4-1. 인가 코드 발급 — `POST /oauth/2.0/authorize` (또는 `GET /oauth/2.0/authorize`)

**Request Body (JSON)**
```json
{
  "client_id": "moava-demo-client",
  "redirect_uri": "http://localhost:5173/openbanking/callback",
  "scope": "login inquiry transfer",
  "state": "moava-csrf-state-01",
  "user_seq_no": "1000000001"
}
```

**Response (`200 OK`)**
```json
{
  "api_tran_id": "...",
  "api_tran_dtm": "20261124120000123",
  "rsp_code": "A0000",
  "rsp_message": "정상 처리되었습니다.",
  "code": "kftc_code_...",
  "state": "moava-csrf-state-01",
  "scope": "login inquiry transfer",
  "redirect_uri": "http://localhost:5173/openbanking/callback",
  "expires_in": 180
}
```

### 4-2. Access Token 발급 — `POST /oauth/2.0/token`

**방식 A: 사용자 토큰 (`authorization_code`)**
```json
{
  "grant_type": "authorization_code",
  "client_id": "moava-demo-client",
  "client_secret": "moava-demo-secret",
  "code": "kftc_code_...",
  "redirect_uri": "http://localhost:5173/openbanking/callback"
}
```

**방식 B: 기관 토큰 (`client_credentials`, `scope=oob`)**
```json
{
  "grant_type": "client_credentials",
  "client_id": "moava-demo-client",
  "client_secret": "moava-demo-secret",
  "scope": "oob"
}
```

**Response (`200 OK`)**
```json
{
  "api_tran_id": "...",
  "api_tran_dtm": "20261124120001456",
  "rsp_code": "A0000",
  "rsp_message": "정상 처리되었습니다.",
  "access_token": "kftc_tok_...",
  "token_type": "Bearer",
  "expires_in": 7776000,
  "scope": "oob",
  "user_seq_no": null
}
```

### 4-3. Access Token 철회 — `POST /oauth/2.0/revoke`

- **Header:** `Authorization: Bearer <access_token>`
- **Response (`200 OK`):** `rsp_code: "A0000"`, `revoked: true`

---

## 5. 오픈뱅킹 v2.0 핵심 원장 API (`/v2.0`)

모든 `/v2.0/**` 엔드포인트는 `Authorization: Bearer <access_token>` 헤더가 필수입니다.

### 5-1. 계좌 유효성 및 예금주 실명 조회 — `POST /v2.0/inquiry/real_name`

- **필수 Scope:** `inquiry` 또는 `oob`

**Request Body**
```json
{
  "bank_tran_id": "M202600001U000000001",
  "bank_code_std": "088",
  "account_num": "110-123-456789",
  "account_holder_name": "홍길동",
  "tran_dtime": "20261124120000"
}
```
> `bank_tran_id` 생략 시 서버가 `M202600001U + 9자리 난수` 20자리 규격으로 자동 채번합니다.

**Response (`200 OK`)**
```json
{
  "api_tran_id": "...",
  "api_tran_dtm": "20261124120002100",
  "rsp_code": "A0000",
  "rsp_message": "정상 처리되었습니다.",
  "bank_tran_id": "M202600001U000000001",
  "bank_code_std": "088",
  "bank_name": "신한은행",
  "account_num": "110-123-456789",
  "account_holder_name": "홍길동",
  "fintech_use_num": "199000000112345678900001",
  "leader_account_yn": "N",
  "verified": true
}
```

### 5-2. 계좌 잔액 조회 — `GET /v2.0/account/balance/fin_num`

- **필수 Scope:** `inquiry` 또는 `oob`
- **Query Parameters:**
  - `fintech_use_num` (필수, 24자리 숫자)
  - `bank_tran_id` (선택, 20자리)

**Response (`200 OK`)**
```json
{
  "api_tran_id": "...",
  "api_tran_dtm": "20261124120003100",
  "rsp_code": "A0000",
  "rsp_message": "정상 처리되었습니다.",
  "bank_tran_id": "M202600001U9X8Y7Z6W5",
  "bank_code_std": "088",
  "bank_name": "신한은행",
  "fintech_use_num": "199000000112345678900001",
  "account_holder_name": "홍길동",
  "balance_amt": 1500000,
  "available_amt": 1500000
}
```

### 5-3. 잔액 및 거래내역 조회 — `GET /v2.0/account/transaction_list/fin_num`

- **필수 Scope:** `inquiry` 또는 `oob`
- **Query Parameters:**
  - `fintech_use_num` (필수, 24자리 숫자)
  - `inquiry_type` (선택, 기본 `A`): `A`(전체), `I`(입금), `O`(출금)
  - `from_date` (선택, `yyyyMMdd`, 미입력 시 **최근 1달 전**)
  - `to_date` (선택, `yyyyMMdd`, 미입력 시 오늘)
  - `sort_order` (선택, 기본 `D`): `D`(최신순), `A`(과거순)
  - `limit` (선택, 기본 `100`, 최대 `500`)
  - `bank_tran_id` (선택, 20자리)

**Response (`200 OK`)**
```json
{
  "api_tran_id": "...",
  "api_tran_dtm": "20261124120004100",
  "rsp_code": "A0000",
  "rsp_message": "정상 처리되었습니다.",
  "bank_tran_id": "M202600001U1A2B3C4D5",
  "bank_code_std": "088",
  "bank_name": "신한은행",
  "fintech_use_num": "199000000112345678900001",
  "account_holder_name": "홍길동",
  "balance_amt": 1450000,
  "page_record_cnt": 1,
  "res_list": [
    {
      "bank_tran_id": "M202600001U777777777",
      "tran_date": "20261124",
      "tran_time": "120005",
      "inout_type": "출금",
      "tran_type": "계좌이체",
      "print_content": "11월 모임 회비",
      "tran_amt": 50000,
      "after_balance_amt": 1450000,
      "branch_name": "모아바모의점",
      "counterparty_name": "김모임"
    }
  ]
}
```

### 5-4. 출금이체 — `POST /v2.0/transfer/withdraw/fin_num`

- **필수 Scope:** `transfer` 또는 `oob`
- **원자성 및 동시성 보장:** `@Transactional` 내부에서 두 계좌를 `FINTECH_USE_NUM` 오름차순 `SELECT ... FOR UPDATE`로 잠근 뒤, ① 잔액 검증 → ② 출금 차감 → ③ 모임장 계좌 입금 증액 → ④ 양쪽 원장 INSERT를 단일 트랜잭션으로 처리합니다.
- **멱등성 보장:** 동일 `bank_tran_id` 재전송 시 1차 조회 + DB `UNIQUE` 제약으로 `A0006` 반환 및 전체 롤백.

**Request Body**
```json
{
  "bank_tran_id": "M202600001U777777777",
  "fintech_use_num": "199000000112345678900001",
  "recv_fintech_use_num": "199000000112345678900099",
  "wd_print_content": "11월 모임 회비",
  "dps_print_content": "홍길동 11월 회비",
  "tran_amt": 50000,
  "req_client_name": "홍길동",
  "tran_dtime": "20261124120005"
}
```

### 5-5. 입금이체 — `POST /v2.0/transfer/deposit/fin_num`

- **필수 Scope:** `transfer` 또는 `oob`

**Request Body**
```json
{
  "bank_tran_id": "M202600001U888888888",
  "wd_fintech_use_num": "199000000112345678900001",
  "fintech_use_num": "199000000112345678900099",
  "wd_print_content": "11월 모임 회비",
  "print_content": "홍길동 11월 회비",
  "tran_amt": 50000,
  "req_client_name": "홍길동",
  "tran_dtime": "20261124120006"
}
```

**공통 이체 Response (`200 OK`)**
```json
{
  "api_tran_id": "...",
  "api_tran_dtm": "20261124120005120",
  "rsp_code": "A0000",
  "rsp_message": "정상 처리되었습니다.",
  "bank_tran_id": "M202600001U777777777",
  "bank_tran_date": "20261124",
  "wd_fintech_use_num": "199000000112345678900001",
  "wd_bank_name": "신한은행",
  "wd_account_holder_name": "홍길동",
  "wd_after_balance_amt": 1450000,
  "dps_fintech_use_num": "199000000112345678900099",
  "dps_bank_name": "국민은행",
  "dps_account_holder_name": "김모임",
  "dps_after_balance_amt": 550000,
  "tran_amt": 50000,
  "print_content": "홍길동 11월 회비"
}
```

---

## 6. 향후 메인 도메인 연동 가이드 (타 팀원 협의 영역)

현재 구현은 타 팀원 도메인과의 충돌을 방지하기 위해 `com.moava.openbanking` 내부에 완전히 격리되어 있습니다. 추후 메인 서비스와 연동할 때 아래 테이블/컬럼을 연결하면 됩니다.

1. **회원 실명 / 모임 계좌 교차 검증:** `POST /v2.0/inquiry/real_name` 응답의 `account_holder_name`, `fintech_use_num`을 `MEMBER` 및 `GYE_ACCOUNT`와 대조.
2. **납입 증빙 이중 납부 방지:** 이체 완료 응답의 20자리 `bank_tran_id`를 `PAYMENT_PROOF.REF_NO` (`VARCHAR2(20) UNIQUE`)에 그대로 기록하여 DB 레벨 이중 납부 차단.
3. **소비 내역 AI 스크래핑 및 모임 통장 공유:** `GET /v2.0/account/transaction_list/fin_num`의 기본 1개월 기간 조회 결과를 소비 분석(`finance`)과 모임장 거래 공유 화면에 공통 활용.
