MOAVA — F15 모임 생성 백엔드

작성 기준: MOAVA_CODE_REVIEW.zip에 포함된 Java 소스 10개 및 MyBatis XML 1개. 팀 프로젝트 전체 기능의 구현 완료를 의미하지 않습니다.

1. 구현 개요
인증된 회원이 모임 정보를 입력하면 입력값을 검증하고, Oracle DB에 모임 기본 정보, 모임장 회원 정보, 암호화된 계좌 정보 및 감사 로그를 저장합니다. 성공하면 생성된 모임 ID와 모임장 ID를 반환합니다.

주요 구현 내용
- POST /api/groups 모임 생성 REST API
- Spring Security의 Authentication을 이용한 요청자 확인
- 모임명, 목적, 지역, 납입액, 인원, 기간, 납입일, 운영 규칙 등 입력값 검증
- 생성자를 GYE_GROUP.OWNER_ID로 지정하고 GYE_MEMBER에 ACTIVE 상태로 등록
- SecureRandom 기반 32자리 16진수 초대 코드 생성
- AES-256-GCM 기반 예금주 이름·계좌번호 암호화 및 HMAC-SHA256 계좌번호 식별 해시
//HMAC-SHA-256: 계좌번호 식별용 해시 생성
- @Transactional을 이용한 모임·모임장·계좌·감사 로그의 원자적 저장
- MyBatis Mapper와 Oracle 시퀀스를 이용한 DB 처리

2. 기술 스택
영역	기술
Backend	Java 17, Spring Boot 4.1.0
인증 연동	Spring Security (Authentication)
영속성	MyBatis 4.0.1, Oracle Database 21c XE
빌드	Maven Wrapper
암호화	Java Cryptography Architecture, AES/GCM/NoPadding, HmacSHA256
Frontend 연동 대상	React (별도 구현·연동 필요)

3. 주요 코드 구조
backend/
└── src/main/
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
XML 파일의 정확한 하위 경로는 팀 저장소의 실제 리소스 구조를 따릅니다.

파일 역할
GroupCreateController	HTTP 요청 수신, 인증 확인, 회원 ID 추출, 201 응답
GroupCreateService	전체 모임 생성 절차 및 트랜잭션 관리
GroupCreateValidator	요청값의 필수 여부·범위·Oracle UTF-8 BYTE 길이 검증
GroupCreateMapper + XML	회원 조회, 시퀀스 조회, 네 테이블 INSERT
GroupCreateRequest	요청 데이터 정의 (record)
GroupCreateResponse	응답 데이터 정의 (record)
GroupAccountCrypto	암호화·해시·키 버전 인터페이스
GroupAccountCryptoImpl	AES-GCM 암호화 및 HMAC-SHA256 구현
GroupInviteCodeGenerator	암호학적 난수 기반 초대 코드 생성
GroupCreateExceptionHandler	잘못된 요청·인증·서버 오류 응답 처리


4. 모임 생성 처리 흐름
React 클라이언트
  │ POST /api/groups (GroupCreateRequest)
  ▼
GroupCreateController
  ├─ Authentication 확인
  └─ authentication.getName() → Long MEMBER_ID 변환
  ▼
GroupCreateService.createGroup()  [@Transactional]
  ├─ GroupCreateValidator.validate() : 입력값 검증
  ├─ MEMBER : 활성 회원 이름 조회
  ├─ SEQ_GYE_GROUP.NEXTVAL : 모임 ID 발급
  ├─ GroupInviteCodeGenerator : 초대 코드 생성
  ├─ GYE_GROUP : 모임 기본 정보 INSERT
  ├─ GYE_MEMBER : 생성자 ACTIVE 회원 INSERT
  ├─ GroupAccountCryptoImpl : 예금주·계좌번호 암호화 / 해시
  ├─ GYE_ACCOUNT : 계좌 인증 대기 정보 INSERT
  └─ AUDIT_LOG : GROUP_CREATE 기록
  ▼
GroupCreateResponse → HTTP 201 Created
트랜잭션 내부에서 일반적인 런타임 예외가 발생하면 DB 변경을 롤백하도록 구성했습니다. 실제 DB 저장 및 롤백 동작은 통합 테스트가 필요합니다.

5. API 명세 — 모임 생성
POST /api/groups
인증된 회원이 모임을 생성합니다. 요청 본문은 JSON이며, memberId는 요청 본문으로 받지 않고 서버의 인증 정보에서 가져옵니다.

요청 예시
POST /api/groups HTTP/1.1
Content-Type: application/json

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
성공 응답 예시 — 201 Created
{
  "groupId": 1,
  "ownerId": 10,
  "status": "RECRUITING",
  "message": "모임이 생성되었습니다."
}
groupId, ownerId는 설명을 위한 예시 값입니다. 실제 값은 인증 회원 및 DB 시퀀스에 따라 달라집니다.

주요 입력 검증
필드	검증 기준
title	필수, UTF-8 기준 100 BYTE 이하
purpose	SAVINGS, INVEST, SOCIAL, STARTUP, TRAVEL
regionSido, regionSigungu	각각 필수, 30 BYTE 이하
amount	50,000원 이상, 50,000원 단위, 최대 999,999,999,999원
maxMembers	2~30명
months	2~36개월
payDay	1~28일
assumedRate	0~20, 소수 둘째 자리까지
visibility	PUBLIC, PRIVATE
rulesTitle	필수, 100 BYTE 이하
rulesBody	필수, CLOB 저장
description	필수, 1,000 BYTE 이하
overduePolicy	ALERT_ONLY, ALERT_RESTRICT
bankName	필수, 50 BYTE 이하
accountNumber	숫자 8~30자리


현재 구현의 주요 응답 코드
HTTP 상태	상황
201 Created	생성 성공
400 Bad Request	유효하지 않은 입력 또는 JSON 형식
401 Unauthorized	인증 정보 없음, 인증 이름을 숫자 회원 ID로 변환 불가 등
403 Forbidden	Service에서 모임 생성 불가 회원으로 판단한 경우
500 Internal Server Error	기타 처리되지 않은 서버 오류


인증 필터의 실제 응답, DB 제약조건 충돌의 오류 응답은 팀의 Spring Security 설정 및 공통 예외 정책에 따라 달라질 수 있습니다.

6. Oracle DB 저장 구조
테이블	처리	핵심 컬럼
MEMBER	SELECT	MEMBER_ID, NAME, STATUS, BANNED_YN
GYE_GROUP	INSERT	GROUP_ID, OWNER_ID, INVITE_CODE, STATUS 등
GYE_MEMBER	INSERT	GYE_MEMBER_ID, GROUP_ID, MEMBER_ID, STATUS='ACTIVE'
GYE_ACCOUNT	INSERT	암호문, 계좌번호 뒤 4자리, HMAC 해시, 키 버전, 인증 상태
AUDIT_LOG	INSERT	GROUP_ID, ACTOR_MEMBER_ID, EVENT_TYPE='GROUP_CREATE'

사용 시퀀스: SEQ_GYE_GROUP, SEQ_GYE_MEMBER, SEQ_GYE_ACCOUNT, SEQ_AUDIT_LOG.

생성된 모임의 상태는 DB 기본값 및 응답 기준 RECRUITING입니다. 모임 계좌는 VERIFY_STATUS='PENDING', ACCOUNT_STATUS='CURRENT'로 저장됩니다. 이는 현재 등록된 계좌이지만 인증은 대기 중이라는 의미이며, 실제 계좌 인증 성공을 뜻하지 않습니다.

7. 보안 설계
인증과 권한
- 인증 여부를 확인한 뒤 모임 생성 서비스를 호출합니다.
- 클라이언트가 보내는 회원 ID 대신 인증 정보에서 추출한 ID를 사용합니다.
- MEMBER.STATUS='ACTIVE' 및 BANNED_YN='N' 조건으로 생성 가능 회원을 조회합니다.
- 연동 전제: Authentication.getName()이 숫자형 MEMBER_ID여야 합니다. 팀의 로그인/JWT principal 설계와 일치시켜야 합니다.
계좌 정보 암호화
- AES-256-GCM (AES/GCM/NoPadding), 매 암호화마다 무작위 12바이트 IV 사용
- 암호문과 IV를 Base64 형태로 저장
- 계좌번호 식별용 HMAC-SHA256 해시 별도 저장
- 화면 표시 등에 사용할 계좌번호 뒤 4자리 별도 저장
- KEY_VER를 저장해 키 버전을 식별
- 암호화된 예금주 이름 80 BYTE, 계좌번호 100 BYTE 저장 길이 검증
보안 주의: 실제 AES 키, HMAC 키 및 application-secret.properties 내용은 README, Git 커밋, PR 설명에 포함하지 않습니다.
설정값
GroupAccountCryptoImpl은 아래 Spring 프로퍼티를 읽습니다.
# 값은 예시 이름만 기재합니다. 실제 키를 README에 입력하지 마세요.
moava.crypto.account.key=<Base64 인코딩된 32바이트 AES 키>
moava.crypto.account.hmac-key=<Base64 인코딩된 32바이트 이상 HMAC 키>
moava.crypto.account.key-version=<CRYPTO_KEY에 등록된 버전 번호>
설정값은 팀의 비밀정보 관리 방식으로 주입해야 합니다. 암호화 키 버전은 GYE_ACCOUNT.KEY_VER의 외래 키 및 GYE_GROUP.JOIN_CODE_KEY_VER의 기본값과 일치하는지 확인해야 합니다.

8. 실행 및 확인
Windows PowerShell에서 프로젝트의 backend 폴더로 이동한 뒤:
cd C:\Users\user1\MOAVA\backend
.\mvnw.cmd clean compile
.\mvnw.cmd spring-boot:run
Oracle 접속 설정, Spring Security 설정, 암호화 프로퍼티가 준비되어 있어야 정상 실행됩니다. 실제 실행 명령과 프로파일은 팀 환경에 따라 조정하세요.

현재 확인된 내용
- Maven clean compile: 성공
- Spring Boot 애플리케이션 기동: 성공
- 비인증 POST /api/groups 요청: HTTP 401 확인
- Java Mapper와 MyBatis XML 매개변수 대조: 일치
- Oracle의 주요 NOT NULL / CHECK / PK / FK / UNIQUE 제약조건: 1차 검토

아직 확인하지 않은 내용
- 팀 로그인/JWT를 이용한 인증 요청 성공
- 실제 Oracle INSERT 성공 및 응답 데이터 확인
- 예외 발생 시 네 테이블의 트랜잭션 롤백
- 계좌 암호화 데이터의 실제 복호화 및 키 버전 정합성
- React 모임 생성 화면과 API 연동

9. 팀원 연동 체크리스트
- [ ] 로그인/JWT 담당: 인증 principal에서 숫자 MEMBER_ID를 제공하는지 합의
- [ ] 프론트엔드 담당: POST /api/groups의 16개 요청 필드와 코드값 일치 확인
- [ ] DB/보안 담당: CRYPTO_KEY에 계좌 암호화 키 버전 및 초대 코드 키 버전 존재 확인
- [ ] 계좌 인증 담당: 계좌 등록 시 PENDING 상태 이후 인증 흐름 연결
- [ ] 모임 가입 담당: GYE_GROUP.OWNER_ID와 GYE_MEMBER.ACTIVE 자동 등록 구조 공유
- [ ] 공통 예외 처리 담당: 400/401/403/500 응답 형식 통일

10. 코드 리뷰 및 후속 작업
1. 인증 연동: 팀의 실제 Spring Security/JWT 구현과 Authentication.getName() 계약 맞추기
2. DB 연동: CRYPTO_KEY 참조 버전 및 외래 키 데이터 검증
3. 문자열 처리: 입력값 검증과 DB 저장 시 동일한 공백 정규화 적용
4. 예금주 정책: 현재는 활성 회원의 이름을 예금주 이름으로 암호화함. 실제 계좌 명의 인증과 구분 필요
5. 예외 처리: UNIQUE 충돌, DB 오류 및 공통 API 오류 형식 정리
6. 테스트: 인증된 생성 성공, 입력 오류, 중복 초대 코드, 롤백, React 연동 검증

11. 핵심 학습 포인트
- Controller: 요청 수신 및 인증 확인
- DTO: React ↔ Spring Boot 사이의 요청·응답 데이터 구조
- Validator: 잘못된 데이터가 DB로 들어가지 않도록 검증
- Service: 비즈니스 로직을 조합하고 @Transactional로 원자성 관리
- Mapper + XML: Java 메서드와 SQL을 연결해 Oracle 조회·저장
- Crypto: 계좌정보 암호화와 키 기반 식별 해시 처리
- InviteCodeGenerator: 추측하기 어려운 초대 코드 생성

한 문장 요약: Spring Boot와 MyBatis를 이용해 모임 생성 시 생성자를 모임장으로 자동 등록하고, 계좌정보 암호화와 감사 로그 기록까지 하나의 트랜잭션으로 처리하도록 구현한 F15 백엔드 기능입니다.

진행 상태: 기본 구현 및 컴파일 검증 완료 · 팀원 PR 병합 이후 인증/DB/API 통합 테스트 예정