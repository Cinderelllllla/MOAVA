# MOAVA | F15 모임 생성 백엔드

> **인증된 회원이 모임을 생성하고, 생성자를 모임장으로 등록하는 API**  
> 작성 범위: `MOAVA_CODE_REVIEW.zip`에 포함된 Java 소스 10개 및 MyBatis XML 1개 기준  
> **안내:** 이 문서는 F15 구현 범위를 설명하며, MOAVA 전체 기능의 구현 완료를 의미하지 않습니다.

## ✨ 구현 개요

인증된 회원이 모임 정보를 입력하면 요청값을 검증하고 Oracle DB에 모임 기본 정보, 모임장 회원 정보, 암호화된 계좌 정보 및 감사 로그를 저장합니다. 성공 시 생성된 **모임 ID**와 **모임장 ID**를 반환합니다.

## ✅ 주요 구현 기능

- `POST /api/groups` — 모임 생성 REST API
- Spring Security `Authentication`을 이용한 요청자 확인
- 모임명·목적·지역·납입액·인원·기간·납입일·운영 규칙 등 입력값 검증
- 생성자를 `GYE_GROUP_OWNER`에 모임장으로, `GYE_MEMBER`에 `ACTIVE` 상태로 등록
- `SecureRandom` 기반 32자리 16진수 초대 코드 생성
- **AES-256-GCM** 기반 예금주명·계좌번호 암호화 및 **HMAC-SHA256** 계좌번호 식별 해시 생성
- `@Transactional`을 이용한 모임·모임장·계좌·감사 로그의 원자적 저장
- MyBatis Mapper와 Oracle 시퀀스를 이용한 DB 처리

## 🛠 기술 스택

| 구분 | 기술 |
| --- | --- |
| Backend | Java 17, Spring Boot 4.1.0 |
| 인증 | Spring Security (`Authentication`) |
| 영속성 | MyBatis 4.0.1 |
| 데이터베이스 | Oracle Database 21c XE |
| 빌드 | Maven Wrapper |
| 암호화 | Java Cryptography Architecture, AES/GCM/NoPadding, HmacSHA256 |
| 프론트엔드 연동 대상 | React (별도 구현·연동 필요) |

## 📁 주요 코드 구조

```text
backend/src/main/
├── java/com/moava/group/create/
│   ├── controller/
│   │   ├── GroupCreateController.java
│   │   └── GroupCreateExceptionHandler.java
│   ├── dto/
│   │   ├── GroupCreateRequest.java
│   │   └── GroupCreateResponse.java
│   ├── mapper/
│   │   └── GroupCreateMapper.java
│   └── service/
│       ├── GroupAccountCrypto.java
│       ├── GroupAccountCryptoImpl.java
│       ├── GroupCreateService.java
│       ├── GroupCreateValidator.java
│       └── GroupInviteCodeGenerator.java
└── resources/mapper/.../
    └── GroupCreateMapper.xml
```

> XML의 정확한 하위 경로는 저장소의 실제 리소스 구조를 따릅니다.

<details>
<summary><strong>클래스별 역할 보기</strong></summary>

| 클래스 | 역할 |
| --- | --- |
| `GroupCreateController` | HTTP 요청 수신, 인증 확인, 회원 ID 추출, 201 응답 |
| `GroupCreateExceptionHandler` | 잘못된 요청·인증·서버 오류 응답 처리 |
| `GroupCreateRequest` | 요청 데이터 정의 (`record`) |
| `GroupCreateResponse` | 응답 데이터 정의 (`record`) |
| `GroupCreateService` | 모임 생성 절차 및 트랜잭션 관리 |
| `GroupCreateValidator` | 필수값·범위·Oracle UTF-8 BYTE 길이 검증 |
| `GroupCreateMapper` + XML | 회원 조회, 시퀀스 조회, 관련 테이블 INSERT |
| `GroupAccountCrypto` | 암호화·해시 인터페이스 |
| `GroupAccountCryptoImpl` | AES-GCM 암호화 및 HMAC-SHA256 구현 |
| `GroupInviteCodeGenerator` | 암호학적 난수 기반 초대 코드 생성 |

</details>

## 🔄 모임 생성 처리 흐름

```text
React 클라이언트
  └─ POST /api/groups
      └─ GroupCreateController
          ├─ Authentication 확인 → MEMBER_ID 추출
          └─ GroupCreateService.createGroup() [@Transactional]
              ├─ GroupCreateValidator.validate()
              ├─ MEMBER 활성 회원 확인
              ├─ SEQ_GYE_GROUP.NEXTVAL → 모임 ID 발급
              ├─ GroupInviteCodeGenerator → 초대 코드 생성
              ├─ GYE_GROUP INSERT
              ├─ GYE_MEMBER INSERT (생성자, ACTIVE)
              ├─ GYE_GROUP_OWNER INSERT (모임장)
              ├─ GroupAccountCryptoImpl → 계좌 암호화·해시
              ├─ GYE_ACCOUNT INSERT
              └─ AUDIT_LOG INSERT (GROUP_CREATE)
                  └─ GroupCreateResponse → HTTP 201 Created
```

일반적인 런타임 예외가 트랜잭션 내부에서 발생하면 DB 변경을 롤백하도록 구성했습니다. **실제 DB 저장 및 롤백 동작은 통합 테스트로 확인해야 합니다.**

## 📡 API 명세

### `POST /api/groups` — 모임 생성

인증된 회원이 모임을 생성합니다. `memberId`는 요청 본문에서 받지 않고 **서버 인증 정보**에서 추출합니다.

**요청 예시**

```http
POST /api/groups HTTP/1.1
Content-Type: application/json
```

```json
{
  "title": "우리 동네 목돈 모임",
  "purpose": "SAVINGS",
  "regionSido": "서울특별시",
  "regionSigungu": "마포구",
  "amount": 100000,
  "maxMembers": 6,
  "months": 6,
  "payDay": 5,
  "assumedRate": 3.0,
  "visibility": "PUBLIC",
  "rulesTitle": "우리 모임 운영 규칙",
  "rulesBody": "매월 5일까지 납입합니다.",
  "description": "함께 목돈을 모으는 모임입니다.",
  "overduePolicy": "ALERT_ONLY",
  "bankName": "예시은행",
  "accountNumber": "123456789012"
}
```

**성공 응답**

```http
HTTP/1.1 201 Created
Content-Type: application/json
```

응답 필드와 오류 코드의 정확한 형식은 `GroupCreateResponse.java` 및 예외 처리 코드 기준으로 확인해 주세요.

## 🔐 보안 및 데이터 일관성

- **인증:** 요청 본문의 회원 ID를 신뢰하지 않고 `Authentication`에서 확인
- **민감정보:** 예금주명·계좌번호는 AES-256-GCM으로 암호화해 저장
- **계좌 식별:** HMAC-SHA256으로 별도 식별 해시 생성
- **원자성:** 모임·회원·모임장·계좌·감사 로그 저장을 하나의 트랜잭션으로 처리

## 🧪 검증 및 연동 참고

- Spring Security 인증 설정 및 실제 로그인 연동 확인 필요
- Oracle 테이블·시퀀스·MyBatis XML 경로 정합성 확인 필요
- 정상 생성·입력값 오류·미인증 요청·트랜잭션 롤백 통합 테스트 필요
- React 화면과 API 연결은 별도 구현·검증 필요

- 진행 상태: 기본 구현 및 컴파일 검증 완료 · 팀원 PR 병합 이후 인증/DB/API 통합 테스트 예정
---

<sub>MOAVA · F15 모임 생성 백엔드 구현 범위 문서</sub>
