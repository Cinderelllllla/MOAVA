-- =====================================================================
-- MOAVA (모아바) Oracle DDL  v2.1  2026-11-26 (v2.0 + 11/26 F31·F34·F35 변경분. 기준: develop MOA-76 PR 병합분, 테이블 정의서 v2.1)
-- 11/26 변경(v2.1): CHAT_MESSAGE.MSG_TYPE 추가(USER/SYSTEM), MEMBER_ID NULL 허용(SYSTEM 메시지는 발신자 없음), CK_CHAT_MESSAGE_1·2 추가 (F31·F34)
--           NOTIFICATION.NOTIF_TYPE 알림 코드표 v3.2(23종) 주석: RULE_UPDATED(F31 운영 규칙 수정 알림, 인앱만) 추가 — 코드값만 추가, 제약 변경 없음
--           GYE_GROUP.RULES_TITLE·RULES_BODY, TERMS_POLICY.BODY 주석: 원문 저장·화면 출력 시 이스케이프 / AUDIT_LOG.EVENT_TYPE 주석: RULE_UPDATE 사용 중으로 이동
--           기존 DB 적용: MIGRATION_1126
-- 11/26 변경(v2.0): GYE_ACCOUNT.HOLDER_NAME_ENC VARCHAR2(80) → VARCHAR2(120 BYTE) (예금주 MEMBER.NAME 최대 50바이트 암호화 시 Base64 104자)
--           계좌 중복 방지 유일 인덱스 UX_GYE_ACCOUNT_ACTIVE_HASH 신규 (ACCOUNT_STATUS가 CURRENT·PENDING인 계좌만 대상, RETIRED 제외)
--           ※ DB 제약만 추가. 서비스 코드의 중복 검사(사용자용 오류 문구)는 미구현 — 중복 시 ORA-00001로 생성 실패
--           AUDIT_LOG.EVENT_TYPE은 CHECK 제약 없이 컬럼 설명만 정리 / JOIN_REQUEST EXPIRED·JOIN_CODE_ATTEMPT FAIL_COUNT 0~3·BODY_ENC 2800 유지
--           회원 탈퇴 30일 유예: MEMBER.STATUS에 DELETED 추가, PURGE_DUE_AT·PURGED_AT 추가 (탈퇴 신청 → 30일 데이터 유지·복구 가능 → 개인정보 파기)
--           간편 로그인(구글·네이버·카카오): MEMBER.SIGNUP_TYPE 추가, PASSWORD_HASH NULL 허용(소셜 전용 회원), MEMBER_SOCIAL_ACCOUNT 신규
--           ※ 탈퇴 유예·간편 로그인은 정책 확정(11/26), 코드 미구현. 이 두 항목은 마이그레이션 없이 로컬 DB 재생성으로 반영
-- 기존 DB 적용: MIGRATION_1126. 신규 설치는 이 파일 하나로 충분
-- 11/25 변경: CHAT_MESSAGE.BODY_ENC 2000→2800 확정 / JOIN_REQUEST.STATUS EXPIRED 추가(7일 미처리 자동 만료) / JOIN_CODE_ATTEMPT.FAIL_COUNT 0~3(3회 실패 시 재발급 전까지 차단, LOCKED_UNTIL 미사용)
--           NOTIFICATION.NOTIF_TYPE 알림 코드표 v3.1(22종) 주석 / AUDIT_LOG.EVENT_TYPE 코드 주석 갱신 / 오픈뱅킹 모의 원장·OAuth·CODEF 테이블 5개 신규(8번 섹션)
-- 11/20 변경: WITHDRAWAL_CONSENT_TARGET 신규(F29 정산안 고정 동의 대상 + 관리자 면제 기록) / CONSENT 수령순서(ORDER) 미응답 'P' 허용 + AGREED_AT NULL 허용
-- 11/05 변경: NOTIFICATION 수신자 MEMBER_ID/ADMIN_ID 분리 (관리자 수신 알림 저장 가능)
-- 11/04 변경: ROLE·부계모임장 폐지 / OVERDUE_DAYS 삭제 / 미납 5일차 / 관리자 행위자 ADMIN FK(A안) / MAX_MEMBERS 2~30 / 기본값 정리 / KEY_VER 7건 FK→CRYPTO_KEY
-- 11/03 팀 추가분(TERMS_POLICY·MEMBER_CONSENT·CRYPTO_KEY·ADMIN)은 그대로 두고 정의서와 다른 타입·기본값만 맞춤
-- 금액 단위: 원(KRW), NUMBER(12). 상태값: 영문 코드(VARCHAR2). 일시: DATE
-- 대상 DB: Oracle 21c XE (CDB 루트, SID xe, 유저 C##MOAVA). 실행 순서: 사용자 생성(1회) -> DROP(선택) -> CREATE TABLE -> ALTER FK -> INDEX -> SEQUENCE
-- =====================================================================

-- ---------- 00. 사용자 생성 (SYSTEM 계정으로 1회만 실행) ----------
-- 공용 서버 구성(11/16 확인): Oracle 21c XE, CDB 루트(SID xe)에 공용 유저 C##MOAVA. 로컬도 같은 방식으로 통일
-- 접속: 호스트 112.221.47.69 · 포트 10000 · SID xe · 유저 C##MOAVA   (로컬은 localhost · 1521 · SID xe)
-- SQL Developer: 접속 유형 기본, "SID" 선택 → xe  (서비스 이름 아님)
/*
CREATE USER C##MOAVA IDENTIFIED BY "moava1234"
    DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP QUOTA UNLIMITED ON USERS;
GRANT CONNECT, RESOURCE TO C##MOAVA;
GRANT CREATE VIEW, CREATE SEQUENCE, CREATE PROCEDURE, CREATE JOB TO C##MOAVA;
-- (선택) 비밀번호 만료 방지: ALTER PROFILE DEFAULT LIMIT PASSWORD_LIFE_TIME UNLIMITED;
*/
-- 이후 C##MOAVA로 재접속해서 1번부터 실행
-- Spring Boot (공용): spring.datasource.url=jdbc:oracle:thin:@112.221.47.69:10000:xe   username=C##MOAVA
--             (로컬): spring.datasource.url=jdbc:oracle:thin:@localhost:1521:xe        username=C##MOAVA
-- ※ SID 방식이라 호스트:포트 뒤가 콜론(:xe). PDB(XEPDB1)·슬래시 표기는 쓰지 않음

-- ---------- 0. DROP (재생성 시만 사용) ----------
-- 261125 추가: 오픈뱅킹·CODEF (기존 테이블과 FK 없음, 순서 무관)
-- DROP TABLE MOCK_KFTC_TRANSACTION CASCADE CONSTRAINTS PURGE;
-- DROP TABLE MOCK_KFTC_ACCOUNT CASCADE CONSTRAINTS PURGE;
-- DROP TABLE MOCK_KFTC_OAUTH_CODE CASCADE CONSTRAINTS PURGE;
-- DROP TABLE MOCK_KFTC_OAUTH_TOKEN CASCADE CONSTRAINTS PURGE;
-- DROP TABLE CODEF_CONNECTION CASCADE CONSTRAINTS PURGE;
-- 261120 추가 (WITHDRAWAL_REQUEST 를 참조하므로 먼저 삭제)
-- DROP TABLE WITHDRAWAL_CONSENT_TARGET CASCADE CONSTRAINTS PURGE;
-- DROP TABLE AUDIT_LOG CASCADE CONSTRAINTS PURGE;
-- DROP TABLE SUPPORT_REPORT CASCADE CONSTRAINTS PURGE;
-- DROP TABLE NOTIFICATION CASCADE CONSTRAINTS PURGE;
-- DROP TABLE CHAT_MESSAGE CASCADE CONSTRAINTS PURGE;
-- DROP TABLE SANCTION CASCADE CONSTRAINTS PURGE;
-- DROP TABLE DISSOLUTION_REQUEST CASCADE CONSTRAINTS PURGE;
-- DROP TABLE CONSENT CASCADE CONSTRAINTS PURGE;
-- DROP TABLE WITHDRAWAL_REQUEST CASCADE CONSTRAINTS PURGE;
-- DROP TABLE PAYOUT_REQUEST CASCADE CONSTRAINTS PURGE;
-- DROP TABLE OVERDUE_CASE CASCADE CONSTRAINTS PURGE;
-- DROP TABLE PAYMENT_PROOF_FILE CASCADE CONSTRAINTS PURGE;
-- DROP TABLE PAYMENT_PROOF CASCADE CONSTRAINTS PURGE;
-- DROP TABLE JOIN_REQUEST CASCADE CONSTRAINTS PURGE;
-- DROP TABLE JOIN_CODE_ATTEMPT CASCADE CONSTRAINTS PURGE;
-- DROP TABLE LEADER_TRANSFER_REQUEST CASCADE CONSTRAINTS PURGE;
-- DROP TABLE GYE_ACCOUNT CASCADE CONSTRAINTS PURGE;
-- DROP TABLE GYE_MEMBER CASCADE CONSTRAINTS PURGE;
-- DROP TABLE GYE_GROUP CASCADE CONSTRAINTS PURGE;
-- DROP TABLE RISK_SCORE CASCADE CONSTRAINTS PURGE;
-- DROP TABLE FINANCE_MONTHLY CASCADE CONSTRAINTS PURGE;
-- DROP TABLE FINANCE_PROFILE CASCADE CONSTRAINTS PURGE;
-- DROP TABLE MEMBER_SOCIAL_ACCOUNT CASCADE CONSTRAINTS PURGE;
-- DROP TABLE MEMBER CASCADE CONSTRAINTS PURGE;
-- DROP TABLE PRODUCTS_SAVING CASCADE CONSTRAINTS PURGE;
-- DROP TABLE KOSIS_HOUSEHOLD CASCADE CONSTRAINTS PURGE;
-- DROP TABLE CREDIT_SNAPSHOT CASCADE CONSTRAINTS PURGE;
-- 261103 추가
-- DROP TABLE MEMBER_CONSENT CASCADE CONSTRAINTS PURGE;
-- DROP TABLE CRYPTO_KEY CASCADE CONSTRAINTS PURGE;
-- DROP TABLE TERMS_POLICY CASCADE CONSTRAINTS PURGE;
-- DROP TABLE ADMIN CASCADE CONSTRAINTS PURGE;

-- 테이블을 지워도 시퀀스는 남으므로 재생성 시 반드시 같이 삭제 (ORA-00955 방지)
-- DROP SEQUENCE SEQ_PRODUCTS_SAVING;
-- DROP SEQUENCE SEQ_MEMBER_SOCIAL_ACCOUNT;
-- DROP SEQUENCE SEQ_MEMBER;
-- DROP SEQUENCE SEQ_FINANCE_MONTHLY;
-- DROP SEQUENCE SEQ_RISK_SCORE;
-- DROP SEQUENCE SEQ_GYE_GROUP;
-- DROP SEQUENCE SEQ_GYE_MEMBER;
-- DROP SEQUENCE SEQ_GYE_ACCOUNT;
-- DROP SEQUENCE SEQ_LEADER_TRANSFER_REQUEST;
-- DROP SEQUENCE SEQ_JOIN_CODE_ATTEMPT;
-- DROP SEQUENCE SEQ_JOIN_REQUEST;
-- DROP SEQUENCE SEQ_PAYMENT_PROOF;
-- DROP SEQUENCE SEQ_PAYMENT_PROOF_FILE;
-- DROP SEQUENCE SEQ_OVERDUE_CASE;
-- DROP SEQUENCE SEQ_PAYOUT_REQUEST;
-- DROP SEQUENCE SEQ_WITHDRAWAL_REQUEST;
-- DROP SEQUENCE SEQ_CONSENT;
-- DROP SEQUENCE SEQ_DISSOLUTION_REQUEST;
-- DROP SEQUENCE SEQ_SANCTION;
-- DROP SEQUENCE SEQ_CHAT_MESSAGE;
-- DROP SEQUENCE SEQ_NOTIFICATION;
-- DROP SEQUENCE SEQ_SUPPORT_REPORT;
-- DROP SEQUENCE SEQ_AUDIT_LOG;
-- DROP SEQUENCE SEQ_TERMS_POLICY;
-- DROP SEQUENCE SEQ_MEMBER_CONSENT;
-- DROP SEQUENCE SEQ_CRYPTO_KEY;
-- DROP SEQUENCE SEQ_ADMIN;
-- DROP SEQUENCE SEQ_REF_NO;

-- ---------- 1. CREATE TABLE ----------
-- CREDIT_SNAPSHOT: KH 합성 신용 데이터 전처리본. 회원가입 시 1행을 SYN_ID로 매핑. FastAPI가 읽어 연체 확률 추론
CREATE TABLE CREDIT_SNAPSHOT (
                                 SYN_ID                 VARCHAR2(30)   NOT NULL,
                                 RISK_TIER              NUMBER(1),
                                 DTI_PROXY              NUMBER(10,4),
                                 CARD_UTIL              NUMBER(10,4),
                                 DELINQ_12M             NUMBER(10),
                                 IS_DELINQUENT          NUMBER(1),
    -- (전처리 변수 ×55): REQ-DATA-01 산출 60개 변수 중 나머지. 데이터 담당 컬럼 목록 기준으로 확정
                                 LOADED_AT              DATE           DEFAULT SYSDATE NOT NULL,
                                 CONSTRAINT PK_CREDIT_SNAPSHOT PRIMARY KEY (SYN_ID)
);
COMMENT ON TABLE CREDIT_SNAPSHOT IS 'KH 합성 신용 데이터 전처리본. 회원가입 시 1행을 SYN_ID로 매핑. FastAPI가 읽어 연체 확률 추론';
COMMENT ON COLUMN CREDIT_SNAPSHOT.SYN_ID IS '합성 데이터 식별자';
COMMENT ON COLUMN CREDIT_SNAPSHOT.RISK_TIER IS '파생 위험 티어';
COMMENT ON COLUMN CREDIT_SNAPSHOT.DTI_PROXY IS '상환부담률 파생변수';
COMMENT ON COLUMN CREDIT_SNAPSHOT.CARD_UTIL IS '한도소진율 파생변수';
COMMENT ON COLUMN CREDIT_SNAPSHOT.DELINQ_12M IS '최근 1년 연체 합계';
COMMENT ON COLUMN CREDIT_SNAPSHOT.IS_DELINQUENT IS '정답 레이블 (0/1)';
COMMENT ON COLUMN CREDIT_SNAPSHOT.LOADED_AT IS '적재 시각';

-- KOSIS_HOUSEHOLD: KOSIS 소득 10분위별 가계수지 캐시. 매월 1일 @Scheduled 갱신
CREATE TABLE KOSIS_HOUSEHOLD (
                                 DECILE                 NUMBER(2)      NOT NULL,
                                 INCOME_LOW             NUMBER(12)     NOT NULL,
                                 INCOME_HIGH            NUMBER(12),
                                 AVG_CONSUMPTION        NUMBER(12)     NOT NULL,
                                 AVG_NONCONSUMPTION     NUMBER(12)     NOT NULL,
                                 BASE_YM                CHAR(6)        NOT NULL,
                                 FETCHED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                                 CONSTRAINT PK_KOSIS_HOUSEHOLD PRIMARY KEY (DECILE),
                                 CONSTRAINT CK_KOSIS_HOUSEHOLD_1 CHECK (DECILE BETWEEN 1 AND 10)
);
COMMENT ON TABLE KOSIS_HOUSEHOLD IS 'KOSIS 소득 10분위별 가계수지 캐시. 매월 1일 @Scheduled 갱신';
COMMENT ON COLUMN KOSIS_HOUSEHOLD.DECILE IS '소득 분위 1~10';
COMMENT ON COLUMN KOSIS_HOUSEHOLD.INCOME_LOW IS '분위 하한 월소득 (원)';
COMMENT ON COLUMN KOSIS_HOUSEHOLD.INCOME_HIGH IS '분위 상한 월소득 (원). 10분위는 NULL';
COMMENT ON COLUMN KOSIS_HOUSEHOLD.AVG_CONSUMPTION IS '평균 소비지출 (원)';
COMMENT ON COLUMN KOSIS_HOUSEHOLD.AVG_NONCONSUMPTION IS '평균 비소비지출 (원)';
COMMENT ON COLUMN KOSIS_HOUSEHOLD.BASE_YM IS '통계 기준 연월';
COMMENT ON COLUMN KOSIS_HOUSEHOLD.FETCHED_AT IS '수집 시각';

-- PRODUCTS_SAVING: 금감원 금융상품한눈에 적금 금리 캐시. 시뮬레이터 비교용
CREATE TABLE PRODUCTS_SAVING (
                                 PRODUCT_ID             NUMBER(10)     NOT NULL,
                                 FIN_CO_NO              VARCHAR2(20)   NOT NULL,
                                 BANK_NAME              VARCHAR2(50)   NOT NULL,
                                 PRODUCT_NAME           VARCHAR2(100)  NOT NULL,
                                 TERM_MONTHS            NUMBER(3)      NOT NULL,
                                 RATE_BASIC             NUMBER(5,2)    NOT NULL,
                                 RATE_MAX               NUMBER(5,2),
                                 FETCHED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                                 CONSTRAINT PK_PRODUCTS_SAVING PRIMARY KEY (PRODUCT_ID),
                                 CONSTRAINT UK_PRODUCTS_SAVING_1 UNIQUE (FIN_CO_NO, PRODUCT_NAME, TERM_MONTHS)
);
COMMENT ON TABLE PRODUCTS_SAVING IS '금감원 금융상품한눈에 적금 금리 캐시. 시뮬레이터 비교용';
COMMENT ON COLUMN PRODUCTS_SAVING.FIN_CO_NO IS '금융회사 코드';
COMMENT ON COLUMN PRODUCTS_SAVING.BANK_NAME IS '은행명';
COMMENT ON COLUMN PRODUCTS_SAVING.PRODUCT_NAME IS '상품명';
COMMENT ON COLUMN PRODUCTS_SAVING.TERM_MONTHS IS '저축 기간 (개월)';
COMMENT ON COLUMN PRODUCTS_SAVING.RATE_BASIC IS '기본 금리 (%)';
COMMENT ON COLUMN PRODUCTS_SAVING.RATE_MAX IS '최고 우대 금리 (%)';
COMMENT ON COLUMN PRODUCTS_SAVING.FETCHED_AT IS '수집 시각';

-- MEMBER: 회원 계정. 플랫폼 역할 컬럼 없음(관리자는 ADMIN 테이블). 모임장 여부는 GYE_GROUP.OWNER_ID로 판단
CREATE TABLE MEMBER (
                        MEMBER_ID              NUMBER(10)     NOT NULL,
                        EMAIL                  VARCHAR2(100),
                        PASSWORD_HASH          VARCHAR2(100),
                        SIGNUP_TYPE            VARCHAR2(10)   DEFAULT 'EMAIL' NOT NULL,
                        NAME                   VARCHAR2(50)   NOT NULL,
                        PHONE_ENC              VARCHAR2(80),
                        REGION_SIDO            VARCHAR2(30)   NOT NULL,
                        REGION_SIGUNGU         VARCHAR2(30)   NOT NULL,
                        BANNED_YN              CHAR(1)        DEFAULT 'N' NOT NULL,
                        SYN_ID                 VARCHAR2(30),
                        THEME_CODE             VARCHAR2(20)   DEFAULT 'v9' NOT NULL,
                        STATUS                 VARCHAR2(10)   DEFAULT 'ACTIVE' NOT NULL,
                        KEY_VER                NUMBER(2)      DEFAULT 1 NOT NULL,
                        WITHDRAWN_AT           DATE,
                        PURGE_DUE_AT           DATE,
                        PURGED_AT              DATE,
                        CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                        UPDATED_AT             DATE,
                        CONSTRAINT PK_MEMBER PRIMARY KEY (MEMBER_ID),
                        CONSTRAINT UK_MEMBER_EMAIL UNIQUE (EMAIL),
                        CONSTRAINT CK_MEMBER_1 CHECK (BANNED_YN IN ('Y','N')),
                        CONSTRAINT CK_MEMBER_2 CHECK (THEME_CODE IN ('v9','v9meeting')),
                        CONSTRAINT CK_MEMBER_3 CHECK (STATUS IN ('ACTIVE','WITHDRAWN','DELETED')),
                        CONSTRAINT CK_MEMBER_4 CHECK (SIGNUP_TYPE IN ('EMAIL','GOOGLE','NAVER','KAKAO')),
                        CONSTRAINT CK_MEMBER_5 CHECK (STATUS = 'DELETED' OR SIGNUP_TYPE <> 'EMAIL' OR PASSWORD_HASH IS NOT NULL)
);
COMMENT ON TABLE MEMBER IS '회원 계정. 플랫폼 역할 컬럼 없음(관리자는 ADMIN 테이블). 모임장 여부는 GYE_GROUP.OWNER_ID로 판단';
COMMENT ON COLUMN MEMBER.MEMBER_ID IS '회원 식별자 (시퀀스)';
COMMENT ON COLUMN MEMBER.EMAIL IS '로그인 ID(이메일 가입) 또는 소셜 계정 이메일. 중복 불가. 탈퇴 후 30일 유예 동안 유지 — 같은 이메일로 신규 가입 불가(복구만 가능), 파기 시 NULL';
COMMENT ON COLUMN MEMBER.PASSWORD_HASH IS 'BCrypt 해시. 이메일 가입(SIGNUP_TYPE=EMAIL)은 필수, 소셜 전용 회원은 NULL. 파기 시 NULL';
COMMENT ON COLUMN MEMBER.NAME IS '이름. 타인 조회 시 가운데 마스킹';
COMMENT ON COLUMN MEMBER.PHONE_ENC IS '연락처 AES-256-GCM 암호문(Base64). SMS 발송·본인 조회 시만 복호화, 화면 010-****-5678. 가입 시 필수(앱 검증), 탈퇴 익명화 시 NULL';
COMMENT ON COLUMN MEMBER.REGION_SIDO IS '활동 지역 시/도 (예: 서울)';
COMMENT ON COLUMN MEMBER.REGION_SIGUNGU IS '활동 지역 시/군/구 (예: 마포구)';
COMMENT ON COLUMN MEMBER.BANNED_YN IS '플랫폼 가입 제한 여부 (F32)';
COMMENT ON COLUMN MEMBER.SYN_ID IS '가입 시 매핑된 가상 신용 ID (F01)';
COMMENT ON COLUMN MEMBER.THEME_CODE IS 'v9(밝음) / v9meeting(어두움) (F04)';
COMMENT ON COLUMN MEMBER.STATUS IS 'ACTIVE / WITHDRAWN(탈퇴 신청, 30일 유예 — 데이터 유지·일반 로그인 불가, 본인 확인 후 복구 가능 → ACTIVE) / DELETED(유예 종료 후 개인정보 파기 완료, 복구 불가). 행 삭제 없이 NAME=''탈퇴회원'', EMAIL·PASSWORD_HASH·PHONE_ENC·SYN_ID NULL, 모임 기록 FK 유지';
COMMENT ON COLUMN MEMBER.KEY_VER IS '암호화 키 버전 (키 교체 대비)';
COMMENT ON COLUMN MEMBER.WITHDRAWN_AT IS '탈퇴 신청 시각';
COMMENT ON COLUMN MEMBER.CREATED_AT IS '생성 시각';
COMMENT ON COLUMN MEMBER.UPDATED_AT IS '수정 시각';
COMMENT ON COLUMN MEMBER.PURGED_AT IS '개인정보 파기 완료 시각(STATUS=DELETED 전환 시)';
COMMENT ON COLUMN MEMBER.PURGE_DUE_AT IS '개인정보 파기 예정 시각 = WITHDRAWN_AT + 30일. 파기 배치 대상 조회 기준. 복구 시 WITHDRAWN_AT과 함께 NULL';
COMMENT ON COLUMN MEMBER.SIGNUP_TYPE IS '가입 경로 EMAIL / GOOGLE / NAVER / KAKAO. 연결된 소셜 계정은 MEMBER_SOCIAL_ACCOUNT';

-- 11/26 추가: 간편 로그인(구글·네이버·카카오) 연결 계정. 제공자 토큰은 저장하지 않음(프로필 조회 후 폐기)
CREATE TABLE MEMBER_SOCIAL_ACCOUNT (
                                       SOCIAL_ACCOUNT_ID      NUMBER(10)     NOT NULL,
                                       MEMBER_ID              NUMBER(10)     NOT NULL,
                                       PROVIDER               VARCHAR2(10)   NOT NULL,
                                       PROVIDER_USER_ID       VARCHAR2(100)  NOT NULL,
                                       PROVIDER_EMAIL         VARCHAR2(100),
                                       LINKED_AT              DATE           DEFAULT SYSDATE NOT NULL,
                                       LAST_LOGIN_AT          DATE,
                                       CONSTRAINT PK_MEMBER_SOCIAL_ACCOUNT PRIMARY KEY (SOCIAL_ACCOUNT_ID),
                                       CONSTRAINT UK_MEMBER_SOCIAL_1 UNIQUE (PROVIDER, PROVIDER_USER_ID),
                                       CONSTRAINT UK_MEMBER_SOCIAL_2 UNIQUE (MEMBER_ID, PROVIDER),
                                       CONSTRAINT CK_MEMBER_SOCIAL_1 CHECK (PROVIDER IN ('GOOGLE','NAVER','KAKAO'))
);
COMMENT ON TABLE MEMBER_SOCIAL_ACCOUNT IS '간편 로그인 연결 계정(F01·F02). 회원 1명당 제공자별 1개. 회원 파기(STATUS=DELETED) 시 행 삭제';
COMMENT ON COLUMN MEMBER_SOCIAL_ACCOUNT.SOCIAL_ACCOUNT_ID IS '연결 식별자 (시퀀스)';
COMMENT ON COLUMN MEMBER_SOCIAL_ACCOUNT.MEMBER_ID IS '연결된 회원';
COMMENT ON COLUMN MEMBER_SOCIAL_ACCOUNT.PROVIDER IS 'GOOGLE / NAVER / KAKAO';
COMMENT ON COLUMN MEMBER_SOCIAL_ACCOUNT.PROVIDER_USER_ID IS '제공자 고유 사용자 ID(구글 sub, 네이버 id, 카카오 id). 로그인 매칭 키';
COMMENT ON COLUMN MEMBER_SOCIAL_ACCOUNT.PROVIDER_EMAIL IS '제공자가 준 이메일(없을 수 있음 — 카카오 미동의 등). 참고용, 로그인 매칭에 쓰지 않음';
COMMENT ON COLUMN MEMBER_SOCIAL_ACCOUNT.LINKED_AT IS '연결 시각';
COMMENT ON COLUMN MEMBER_SOCIAL_ACCOUNT.LAST_LOGIN_AT IS '이 제공자로 마지막 로그인한 시각';

-- FINANCE_PROFILE: 회원당 1건의 금융 정보. 맞춤 추천 4단계 진행 상태 포함
CREATE TABLE FINANCE_PROFILE (
                                 MEMBER_ID              NUMBER(10)     NOT NULL,
                                 INCOME                 NUMBER(12)     NOT NULL,
                                 FIXED_EXP              NUMBER(12)     DEFAULT 0 NOT NULL,
                                 VARIABLE_EXP           NUMBER(12)     DEFAULT 0 NOT NULL,
                                 SAVING                 NUMBER(12)     DEFAULT 0 NOT NULL,
                                 RESERVE                NUMBER(12)     DEFAULT 0 NOT NULL,
                                 GOAL_AMOUNT            NUMBER(12)     DEFAULT 0 NOT NULL,
                                 CONSENT_YN             CHAR(1)        DEFAULT 'N' NOT NULL,
                                 CONSENT_AT             DATE,
                                 JOURNEY_STEP           NUMBER(1)      DEFAULT 1 NOT NULL,
                                 UPDATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                                 CONSTRAINT PK_FINANCE_PROFILE PRIMARY KEY (MEMBER_ID),
                                 CONSTRAINT CK_FINANCE_PROFILE_1 CHECK (CONSENT_YN IN ('Y','N')),
                                 CONSTRAINT CK_FINANCE_PROFILE_2 CHECK (JOURNEY_STEP BETWEEN 1 AND 4),
                                 CONSTRAINT CK_FINANCE_PROFILE_3 CHECK (INCOME >= 0 AND FIXED_EXP >= 0 AND VARIABLE_EXP >= 0 AND SAVING >= 0 AND RESERVE >= 0 AND GOAL_AMOUNT >= 0)
);
COMMENT ON TABLE FINANCE_PROFILE IS '회원당 1건의 금융 정보. 맞춤 추천 4단계 진행 상태 포함';
COMMENT ON COLUMN FINANCE_PROFILE.MEMBER_ID IS '회원 (1:1)';
COMMENT ON COLUMN FINANCE_PROFILE.INCOME IS '월수입 (원)';
COMMENT ON COLUMN FINANCE_PROFILE.FIXED_EXP IS '고정지출 (원)';
COMMENT ON COLUMN FINANCE_PROFILE.VARIABLE_EXP IS '변동지출 (원)';
COMMENT ON COLUMN FINANCE_PROFILE.SAVING IS '기존 저축액 (원)';
COMMENT ON COLUMN FINANCE_PROFILE.RESERVE IS '비상예비금 (원)';
COMMENT ON COLUMN FINANCE_PROFILE.GOAL_AMOUNT IS '희망 월 납입액 (원)';
COMMENT ON COLUMN FINANCE_PROFILE.CONSENT_YN IS '데이터 분석 동의. N이면 분석 차단';
COMMENT ON COLUMN FINANCE_PROFILE.CONSENT_AT IS '분석 동의 시각 (법적 증빙)';
COMMENT ON COLUMN FINANCE_PROFILE.JOURNEY_STEP IS '맞춤 추천 진행 단계 1~4';
COMMENT ON COLUMN FINANCE_PROFILE.UPDATED_AT IS '저장 시각 (Upsert)';

-- FINANCE_MONTHLY: 월별 수입·지출 기록 (차트용)
CREATE TABLE FINANCE_MONTHLY (
                                 FINANCE_MONTHLY_ID     NUMBER(10)     NOT NULL,
                                 MEMBER_ID              NUMBER(10)     NOT NULL,
                                 YEAR_MONTH             CHAR(6)        NOT NULL,
                                 INCOME                 NUMBER(12)     DEFAULT 0 NOT NULL,
                                 EXPENSE                NUMBER(12)     DEFAULT 0 NOT NULL,
                                 CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                                 CONSTRAINT PK_FINANCE_MONTHLY PRIMARY KEY (FINANCE_MONTHLY_ID),
                                 CONSTRAINT UK_FINANCE_MONTHLY_1 UNIQUE (MEMBER_ID, YEAR_MONTH),
                                 CONSTRAINT CK_FINANCE_MONTHLY_1 CHECK (INCOME >= 0 AND EXPENSE >= 0)
);
COMMENT ON TABLE FINANCE_MONTHLY IS '월별 수입·지출 기록 (차트용)';
COMMENT ON COLUMN FINANCE_MONTHLY.MEMBER_ID IS '회원';
COMMENT ON COLUMN FINANCE_MONTHLY.YEAR_MONTH IS '연월 YYYYMM';
COMMENT ON COLUMN FINANCE_MONTHLY.INCOME IS '해당 월 수입 (원)';
COMMENT ON COLUMN FINANCE_MONTHLY.EXPENSE IS '해당 월 지출 (원)';

-- RISK_SCORE: FastAPI 연체 위험도 추론 결과 이력. 최신 1건을 사용, 폴백 시 직전 값 재사용
CREATE TABLE RISK_SCORE (
                            RISK_SCORE_ID          NUMBER(10)     NOT NULL,
                            MEMBER_ID              NUMBER(10)     NOT NULL,
                            PROB                   NUMBER(5,4)    NOT NULL,
                            TIER                   VARCHAR2(10)   NOT NULL,
                            SAFETY_K               NUMBER(3,2)    NOT NULL,
                            TOP_FACTORS            CLOB,
                            FALLBACK_YN            CHAR(1)        DEFAULT 'N' NOT NULL,
                            SCORED_AT              DATE           DEFAULT SYSDATE NOT NULL,
                            CONSTRAINT PK_RISK_SCORE PRIMARY KEY (RISK_SCORE_ID),
                            CONSTRAINT CK_RISK_SCORE_1 CHECK (TIER IN ('STABLE','CAUTION','RISK')),
                            CONSTRAINT CK_RISK_SCORE_2 CHECK (PROB BETWEEN 0 AND 1),
                            CONSTRAINT CK_RISK_SCORE_3 CHECK (FALLBACK_YN IN ('Y','N'))
);
COMMENT ON TABLE RISK_SCORE IS 'FastAPI 연체 위험도 추론 결과 이력. 최신 1건을 사용, 폴백 시 직전 값 재사용';
COMMENT ON COLUMN RISK_SCORE.MEMBER_ID IS '회원';
COMMENT ON COLUMN RISK_SCORE.PROB IS '연체 확률 p (0~1)';
COMMENT ON COLUMN RISK_SCORE.TIER IS 'STABLE(<0.10) / CAUTION(<0.25) / RISK';
COMMENT ON COLUMN RISK_SCORE.SAFETY_K IS '안전계수 0.5 / 0.35 / 0.2';
COMMENT ON COLUMN RISK_SCORE.TOP_FACTORS IS '주요 영향 변수 JSON';
COMMENT ON COLUMN RISK_SCORE.FALLBACK_YN IS '타임아웃 폴백으로 생성된 값인지';
COMMENT ON COLUMN RISK_SCORE.SCORED_AT IS '추론 시각';

-- GYE_GROUP: 계모임. 생성자가 OWNER_ID이며 납입일·공개 여부·초대 코드를 가짐
CREATE TABLE GYE_GROUP (
                           GROUP_ID               NUMBER(10)     NOT NULL,
                           OWNER_ID               NUMBER(10)     NOT NULL,
                           TITLE                  VARCHAR2(100)  NOT NULL,
                           PURPOSE                VARCHAR2(20)   NOT NULL,
                           REGION_SIDO            VARCHAR2(30)   NOT NULL,
                           REGION_SIGUNGU         VARCHAR2(30)   NOT NULL,
                           LAT                    NUMBER(10,7),
                           LNG                    NUMBER(10,7),
                           AMOUNT                 NUMBER(12)     NOT NULL,
                           MAX_MEMBERS            NUMBER(2)      NOT NULL,
                           MONTHS                 NUMBER(2)      NOT NULL,
                           PAY_DAY                NUMBER(2)      NOT NULL,
                           ASSUMED_RATE           NUMBER(4,2)    DEFAULT 3 NOT NULL,
                           VISIBILITY             VARCHAR2(10)   DEFAULT 'PUBLIC' NOT NULL,
                           INVITE_CODE            VARCHAR2(32)   NOT NULL,
                           JOIN_CODE_ENC          VARCHAR2(80),
                           JOIN_CODE_KEY_VER      NUMBER(2)      DEFAULT 1 NOT NULL,
                           JOIN_CODE_VERSION      NUMBER(3)      DEFAULT 1 NOT NULL,
                           JOIN_CODE_ISSUED_AT    DATE,
                           STATUS                 VARCHAR2(20)   DEFAULT 'RECRUITING' NOT NULL,
                           RULES_TITLE            VARCHAR2(100)  NOT NULL,
                           RULES_BODY             CLOB           NOT NULL,
                           DESCRIPTION            VARCHAR2(1000) NOT NULL,
                           OVERDUE_POLICY         VARCHAR2(20)   DEFAULT 'ALERT_ONLY' NOT NULL,
                           ORDER_CONFIRMED_YN     CHAR(1)        DEFAULT 'N' NOT NULL,
                           STARTED_AT             DATE,
                           CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                           UPDATED_AT             DATE,
                           CONSTRAINT PK_GYE_GROUP PRIMARY KEY (GROUP_ID),
                           CONSTRAINT UK_GYE_GROUP_INVITE_CODE UNIQUE (INVITE_CODE),
                           CONSTRAINT CK_GYE_GROUP_1 CHECK (PURPOSE IN ('SAVINGS','INVEST','SOCIAL','STARTUP','TRAVEL')),
                           CONSTRAINT CK_GYE_GROUP_2 CHECK (MAX_MEMBERS BETWEEN 2 AND 30),
                           CONSTRAINT CK_GYE_GROUP_3 CHECK (MONTHS BETWEEN 2 AND 36),
                           CONSTRAINT CK_GYE_GROUP_4 CHECK (PAY_DAY BETWEEN 1 AND 28),
                           CONSTRAINT CK_GYE_GROUP_5 CHECK (ASSUMED_RATE BETWEEN 0 AND 20),
                           CONSTRAINT CK_GYE_GROUP_6 CHECK (VISIBILITY IN ('PUBLIC','PRIVATE')),
                           CONSTRAINT CK_GYE_GROUP_7 CHECK (STATUS IN ('RECRUITING','ACTIVE','DISSOLVE_PENDING','DISSOLVED')),
                           CONSTRAINT CK_GYE_GROUP_8 CHECK (OVERDUE_POLICY IN ('ALERT_ONLY','ALERT_RESTRICT')),
                           CONSTRAINT CK_GYE_GROUP_9 CHECK (ORDER_CONFIRMED_YN IN ('Y','N')),
                           CONSTRAINT CK_GYE_GROUP_10 CHECK (AMOUNT > 0)
);
COMMENT ON TABLE GYE_GROUP IS '계모임. 생성자가 OWNER_ID이며 납입일·공개 여부·초대 코드를 가짐';
COMMENT ON COLUMN GYE_GROUP.OWNER_ID IS '현재 모임장. 승계 시 갱신 (F35)';
COMMENT ON COLUMN GYE_GROUP.TITLE IS '모임명';
COMMENT ON COLUMN GYE_GROUP.PURPOSE IS 'SAVINGS(목돈) / INVEST(재테크) / SOCIAL(친목) / STARTUP(창업) / TRAVEL(여행)';
COMMENT ON COLUMN GYE_GROUP.REGION_SIDO IS '시/도';
COMMENT ON COLUMN GYE_GROUP.REGION_SIGUNGU IS '시/군/구. 지도 핀 묶음 단위 (F13)';
COMMENT ON COLUMN GYE_GROUP.LAT IS '위도. 생성 시 Kakao Local 지오코딩';
COMMENT ON COLUMN GYE_GROUP.LNG IS '경도';
COMMENT ON COLUMN GYE_GROUP.AMOUNT IS '월 납입액 (원)';
COMMENT ON COLUMN GYE_GROUP.MAX_MEMBERS IS '최대 인원 2~30';
COMMENT ON COLUMN GYE_GROUP.MONTHS IS '운영 기간 2~36개월';
COMMENT ON COLUMN GYE_GROUP.PAY_DAY IS '매월 납입일 1~28. 미납 자동 계산 기준(PAY_DAY+5일) (F26)';
COMMENT ON COLUMN GYE_GROUP.ASSUMED_RATE IS '가정 연이율(%) 0~20. 시뮬레이터·모임 정보 표시 (F14)';
COMMENT ON COLUMN GYE_GROUP.VISIBILITY IS 'PUBLIC / PRIVATE. PRIVATE는 추천·검색·지도 제외 (F34)';
COMMENT ON COLUMN GYE_GROUP.INVITE_CODE IS '초대 링크 토큰 (랜덤 32자). URL에 GROUP_ID 노출 금지';
COMMENT ON COLUMN GYE_GROUP.JOIN_CODE_ENC IS '비공개 모임 6자리 공통 인증코드 AES-256-GCM 암호문. 모임장 상시 조회·복사를 허용하는 최종 UI에 맞춰 양방향 암호화(대안: SHA-256+salt 해시, 발급 시 1회 표시). PUBLIC은 NULL (F18)';
COMMENT ON COLUMN GYE_GROUP.JOIN_CODE_KEY_VER IS '인증코드 암호화 키 버전';
COMMENT ON COLUMN GYE_GROUP.JOIN_CODE_VERSION IS '인증코드 재발급 버전. 재발급 시 +1, 구 코드 무효';
COMMENT ON COLUMN GYE_GROUP.JOIN_CODE_ISSUED_AT IS '인증코드 (재)발급 시각';
COMMENT ON COLUMN GYE_GROUP.STATUS IS 'RECRUITING(모집 중) / ACTIVE(운영 중) / DISSOLVE_PENDING(해산 검토) / DISSOLVED(해산 완료)';
COMMENT ON COLUMN GYE_GROUP.RULES_TITLE IS '운영 규칙 제목(100 BYTE). 원문 저장, 화면 출력 시 이스케이프(React)';
COMMENT ON COLUMN GYE_GROUP.RULES_BODY IS '운영 규칙 본문. 원문 저장, 화면 출력 시 이스케이프(React)';
COMMENT ON COLUMN GYE_GROUP.DESCRIPTION IS '모임 소개';
COMMENT ON COLUMN GYE_GROUP.OVERDUE_POLICY IS 'ALERT_ONLY(5일차 알림) / ALERT_RESTRICT(알림+채팅 제한·후순위 검토)';
COMMENT ON COLUMN GYE_GROUP.ORDER_CONFIRMED_YN IS '수령 순서 확정 여부. N이면 시작 불가 (F21)';
COMMENT ON COLUMN GYE_GROUP.STARTED_AT IS '운영 시작일. 미납 계산 시작 기준';

-- GYE_MEMBER: 모임 참여 관계. 모임별 역할·수령·경고·채팅 제한 상태
CREATE TABLE GYE_MEMBER (
                            GYE_MEMBER_ID          NUMBER(10)     NOT NULL,
                            GROUP_ID               NUMBER(10)     NOT NULL,
                            MEMBER_ID              NUMBER(10)     NOT NULL,
                            STATUS                 VARCHAR2(10)   DEFAULT 'ACTIVE' NOT NULL,
                            PAYOUT_RECEIVED_YN     CHAR(1)        DEFAULT 'N' NOT NULL,
                            PAYOUT_RECEIVED_AT     DATE,
                            WARNING_COUNT          NUMBER(2)      DEFAULT 0 NOT NULL,
                            CHAT_RESTRICTED_YN     CHAR(1)        DEFAULT 'N' NOT NULL,
                            JOINED_AT              DATE           DEFAULT SYSDATE NOT NULL,
                            LEFT_AT                DATE,
                            CONSTRAINT PK_GYE_MEMBER PRIMARY KEY (GYE_MEMBER_ID),
                            CONSTRAINT UK_GYE_MEMBER_1 UNIQUE (GROUP_ID, MEMBER_ID),
                            CONSTRAINT CK_GYE_MEMBER_1 CHECK (STATUS IN ('ACTIVE','WITHDRAWN','EXPELLED')),
                            CONSTRAINT CK_GYE_MEMBER_2 CHECK (PAYOUT_RECEIVED_YN IN ('Y','N')),
                            CONSTRAINT CK_GYE_MEMBER_3 CHECK (CHAT_RESTRICTED_YN IN ('Y','N')),
                            CONSTRAINT CK_GYE_MEMBER_4 CHECK (WARNING_COUNT BETWEEN 0 AND 2)
);
COMMENT ON TABLE GYE_MEMBER IS '모임 참여 관계. 모임별 수령·경고·채팅 제한 상태 (모임장 = GYE_GROUP.OWNER_ID, 부계모임장 없음)';
COMMENT ON COLUMN GYE_MEMBER.GROUP_ID IS '모임';
COMMENT ON COLUMN GYE_MEMBER.MEMBER_ID IS '회원';
COMMENT ON COLUMN GYE_MEMBER.STATUS IS 'ACTIVE / WITHDRAWN(탈퇴) / EXPELLED(강제 탈퇴)';
COMMENT ON COLUMN GYE_MEMBER.PAYOUT_RECEIVED_YN IS '곗돈 수령 완료 (F22). Y면 일반 탈퇴 차단';
COMMENT ON COLUMN GYE_MEMBER.PAYOUT_RECEIVED_AT IS '수령 완료 표시 시각';
COMMENT ON COLUMN GYE_MEMBER.WARNING_COUNT IS '누적 경고 0~2 (F30). 2이면 강제 탈퇴 검토 가능, 3회째 경고 불가';
COMMENT ON COLUMN GYE_MEMBER.CHAT_RESTRICTED_YN IS '미납 제재 채팅 제한 (F37)';
COMMENT ON COLUMN GYE_MEMBER.JOINED_AT IS '가입 승인 시각';
COMMENT ON COLUMN GYE_MEMBER.LEFT_AT IS '탈퇴·강제탈퇴 시각';

-- GYE_ACCOUNT: 모임 계좌. 변경 신청은 PENDING 행으로 추가하고 회원 전원 동의(CONSENT type=ACCOUNT) + 소유 확인 후 CURRENT로 확정 (이력 겸용)
CREATE TABLE GYE_ACCOUNT (
                             GYE_ACCOUNT_ID         NUMBER(10)     NOT NULL,
                             GROUP_ID               NUMBER(10)     NOT NULL,
                             BANK_NAME              VARCHAR2(50)   NOT NULL,
                             HOLDER_NAME_ENC        VARCHAR2(120 BYTE) NOT NULL,
                             ACCOUNT_NO_ENC         VARCHAR2(100)  NOT NULL,
                             ACCOUNT_NO_LAST4       CHAR(4)        NOT NULL,
                             ACCOUNT_NO_HASH        CHAR(64)       NOT NULL,
                             KEY_VER                NUMBER(2)      DEFAULT 1 NOT NULL,
                             VERIFY_STATUS          VARCHAR2(20)   DEFAULT 'PENDING' NOT NULL,
                             VERIFIED_BY            NUMBER(10),
                             VERIFIED_AT            DATE,
                             REGISTERED_BY          NUMBER(10)     NOT NULL,
                             ACCOUNT_STATUS         VARCHAR2(10)   DEFAULT 'CURRENT' NOT NULL,
                             REQUESTED_AT           DATE,
                             CONFIRMED_AT           DATE,
                             CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                             CONSTRAINT PK_GYE_ACCOUNT PRIMARY KEY (GYE_ACCOUNT_ID),
                             CONSTRAINT CK_GYE_ACCOUNT_1 CHECK (VERIFY_STATUS IN ('PENDING','VERIFIED','REVERIFY_REQUIRED')),
                             CONSTRAINT CK_GYE_ACCOUNT_2 CHECK (ACCOUNT_STATUS IN ('PENDING','CURRENT','RETIRED'))
);
COMMENT ON TABLE GYE_ACCOUNT IS '모임 계좌. 변경 신청은 PENDING 행으로 추가하고 회원 전원 동의(CONSENT type=ACCOUNT) + 소유 확인 후 CURRENT로 확정 (이력 겸용)';
COMMENT ON COLUMN GYE_ACCOUNT.GROUP_ID IS '모임';
COMMENT ON COLUMN GYE_ACCOUNT.BANK_NAME IS '은행명';
COMMENT ON COLUMN GYE_ACCOUNT.HOLDER_NAME_ENC IS '예금주 AES-256-GCM 암호문(Base64, 최대 120바이트 — 원문 MEMBER.NAME 50바이트 기준 104자). 계좌번호와 쌍이면 식별되므로 암호화. 화면 김○늘';
COMMENT ON COLUMN GYE_ACCOUNT.ACCOUNT_NO_ENC IS '계좌번호 AES-256-GCM 암호문(Base64). 모임장·ADMIN만 복호화';
COMMENT ON COLUMN GYE_ACCOUNT.ACCOUNT_NO_LAST4 IS '뒤 4자리 (화면 *****6789 표시용)';
COMMENT ON COLUMN GYE_ACCOUNT.ACCOUNT_NO_HASH IS 'HMAC-SHA256(계좌번호, 서버 비밀키 moava.crypto.account.hmac-key) hex 64자. CURRENT·PENDING 계좌끼리 중복 불가(UX_GYE_ACCOUNT_ACTIVE_HASH), RETIRED 제외. 서비스 코드 중복 검사는 미구현';
COMMENT ON COLUMN GYE_ACCOUNT.KEY_VER IS '암호화 키 버전';
COMMENT ON COLUMN GYE_ACCOUNT.VERIFY_STATUS IS 'PENDING(인증 대기) / VERIFIED(인증 확인) / REVERIFY_REQUIRED(변경·승계 후 재인증 필요)';
COMMENT ON COLUMN GYE_ACCOUNT.VERIFIED_BY IS '인증 확인 처리 관리자 (FK ADMIN, 시연: 수동)';
COMMENT ON COLUMN GYE_ACCOUNT.VERIFIED_AT IS '인증 확인 시각';
COMMENT ON COLUMN GYE_ACCOUNT.REGISTERED_BY IS '등록한 모임장';
COMMENT ON COLUMN GYE_ACCOUNT.ACCOUNT_STATUS IS 'PENDING(변경 검토 중) / CURRENT(현재 계좌) / RETIRED(이전 계좌). 모임당 CURRENT 1건';
COMMENT ON COLUMN GYE_ACCOUNT.REQUESTED_AT IS '변경 신청 시각 (PENDING 생성 시)';
COMMENT ON COLUMN GYE_ACCOUNT.CONFIRMED_AT IS '변경 확정 시각 (전원 동의 + 소유 확인 후)';

-- LEADER_TRANSFER_REQUEST: 모임장 권한 승계 요청. 승계 대상 회원 수락 + 회원 전원 동의(CONSENT type=TRANSFER) 후 확정
CREATE TABLE LEADER_TRANSFER_REQUEST (
                                         TRANSFER_ID            NUMBER(10)     NOT NULL,
                                         GROUP_ID               NUMBER(10)     NOT NULL,
                                         FROM_MEMBER_ID         NUMBER(10)     NOT NULL,
                                         TO_MEMBER_ID           NUMBER(10)     NOT NULL,
                                         ACCEPTED_YN            CHAR(1)        DEFAULT 'N' NOT NULL,
                                         ACCEPTED_AT            DATE,
                                         STATUS                 VARCHAR2(10)   DEFAULT 'PENDING' NOT NULL,
                                         DECIDED_AT             DATE,
                                         CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                                         CONSTRAINT PK_LEADER_TRANSFER_REQUEST PRIMARY KEY (TRANSFER_ID),
                                         CONSTRAINT CK_LEADER_TRANSFER_REQUEST_1 CHECK (ACCEPTED_YN IN ('Y','N')),
                                         CONSTRAINT CK_LEADER_TRANSFER_REQUEST_2 CHECK (STATUS IN ('PENDING','CONFIRMED','CANCELLED'))
);
COMMENT ON TABLE LEADER_TRANSFER_REQUEST IS '모임장 권한 승계 요청. 승계 대상 회원 수락 + 회원 전원 동의(CONSENT type=TRANSFER) 후 확정';
COMMENT ON COLUMN LEADER_TRANSFER_REQUEST.GROUP_ID IS '모임';
COMMENT ON COLUMN LEADER_TRANSFER_REQUEST.FROM_MEMBER_ID IS '현재 모임장';
COMMENT ON COLUMN LEADER_TRANSFER_REQUEST.TO_MEMBER_ID IS '승계 대상 회원 (해당 모임 ACTIVE 구성원, 모임장이 지정)';
COMMENT ON COLUMN LEADER_TRANSFER_REQUEST.ACCEPTED_YN IS '승계 대상 회원 수락 여부';
COMMENT ON COLUMN LEADER_TRANSFER_REQUEST.ACCEPTED_AT IS '수락 시각';
COMMENT ON COLUMN LEADER_TRANSFER_REQUEST.STATUS IS 'PENDING / CONFIRMED / CANCELLED';
COMMENT ON COLUMN LEADER_TRANSFER_REQUEST.DECIDED_AT IS '확정·취소 시각';
COMMENT ON COLUMN LEADER_TRANSFER_REQUEST.CREATED_AT IS '요청 시각';

-- JOIN_CODE_ATTEMPT: 비공개 모임 인증코드 검증 시도·잠금·검증 상태 (회원×모임당 1행)
CREATE TABLE JOIN_CODE_ATTEMPT (
                                   ATTEMPT_ID             NUMBER(10)     NOT NULL,
                                   GROUP_ID               NUMBER(10)     NOT NULL,
                                   MEMBER_ID              NUMBER(10)     NOT NULL,
                                   CODE_VERSION           NUMBER(3)      NOT NULL,
                                   FAIL_COUNT             NUMBER(2)      DEFAULT 0 NOT NULL,
                                   LOCKED_UNTIL           DATE,
                                   VERIFIED_YN            CHAR(1)        DEFAULT 'N' NOT NULL,
                                   VERIFIED_AT            DATE,
                                   LAST_ATTEMPT_AT        DATE           DEFAULT SYSDATE NOT NULL,
                                   CONSTRAINT PK_JOIN_CODE_ATTEMPT PRIMARY KEY (ATTEMPT_ID),
                                   CONSTRAINT UK_JOIN_CODE_ATTEMPT_1 UNIQUE (GROUP_ID, MEMBER_ID),
                                   CONSTRAINT CK_JOIN_CODE_ATTEMPT_1 CHECK (FAIL_COUNT BETWEEN 0 AND 3),
                                   CONSTRAINT CK_JOIN_CODE_ATTEMPT_2 CHECK (VERIFIED_YN IN ('Y','N'))
);
COMMENT ON TABLE JOIN_CODE_ATTEMPT IS '비공개 모임 인증코드 검증 시도·잠금·검증 상태 (회원×모임당 1행)';
COMMENT ON COLUMN JOIN_CODE_ATTEMPT.GROUP_ID IS '모임';
COMMENT ON COLUMN JOIN_CODE_ATTEMPT.MEMBER_ID IS '시도 회원';
COMMENT ON COLUMN JOIN_CODE_ATTEMPT.CODE_VERSION IS '시도 당시 JOIN_CODE_VERSION. 재발급 시 실패 횟수 및 인증 상태 초기화';
COMMENT ON COLUMN JOIN_CODE_ATTEMPT.FAIL_COUNT IS '회원별 인증 실패 횟수. 3회 도달 시 코드 재발급 전까지 인증 차단';
COMMENT ON COLUMN JOIN_CODE_ATTEMPT.LOCKED_UNTIL IS '기존 시간 기반 잠금 정책 호환용 컬럼. 현재 잠금 판단에는 사용하지 않음';
COMMENT ON COLUMN JOIN_CODE_ATTEMPT.VERIFIED_YN IS '검증 성공 여부';
COMMENT ON COLUMN JOIN_CODE_ATTEMPT.VERIFIED_AT IS '검증 성공 시각';
COMMENT ON COLUMN JOIN_CODE_ATTEMPT.LAST_ATTEMPT_AT IS '마지막 시도 시각';

-- JOIN_REQUEST: 가입 신청. 검색/초대 경로 구분
CREATE TABLE JOIN_REQUEST (
                              JOIN_REQUEST_ID        NUMBER(10)     NOT NULL,
                              GROUP_ID               NUMBER(10)     NOT NULL,
                              MEMBER_ID              NUMBER(10)     NOT NULL,
                              VIA                    VARCHAR2(10)   DEFAULT 'SEARCH' NOT NULL,
                              CODE_VERIFIED_YN       CHAR(1)        DEFAULT 'N' NOT NULL,
                              STATUS                 VARCHAR2(10)   DEFAULT 'PENDING' NOT NULL,
                              DECIDED_BY             NUMBER(10),
                              DECIDED_AT             DATE,
                              CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                              CONSTRAINT PK_JOIN_REQUEST PRIMARY KEY (JOIN_REQUEST_ID),
                              CONSTRAINT CK_JOIN_REQUEST_1 CHECK (VIA IN ('SEARCH','INVITE')),
                              CONSTRAINT CK_JOIN_REQUEST_2 CHECK (STATUS IN ('PENDING','APPROVED','REJECTED','EXPIRED')),
                              CONSTRAINT CK_JOIN_REQUEST_3 CHECK (CODE_VERIFIED_YN IN ('Y','N'))
);
COMMENT ON TABLE JOIN_REQUEST IS '가입 신청 및 처리 이력. 신청 후 7일 동안 미처리 시 자동 만료';
COMMENT ON COLUMN JOIN_REQUEST.GROUP_ID IS '모임';
COMMENT ON COLUMN JOIN_REQUEST.MEMBER_ID IS '신청 회원';
COMMENT ON COLUMN JOIN_REQUEST.VIA IS '가입 신청 경로: SEARCH(검색·추천·지도), INVITE(초대 링크 및 인증코드)';
COMMENT ON COLUMN JOIN_REQUEST.CODE_VERIFIED_YN IS '비공개 모임 인증코드 검증 통과 여부. PRIVATE 모임은 Y만 허용';
COMMENT ON COLUMN JOIN_REQUEST.STATUS IS 'PENDING(대기), APPROVED(승인), REJECTED(거절), EXPIRED(7일 미처리 자동 만료)';
COMMENT ON COLUMN JOIN_REQUEST.DECIDED_BY IS '승인 또는 거절 처리 회원 ID. 시스템 자동 만료 시 NULL';
COMMENT ON COLUMN JOIN_REQUEST.DECIDED_AT IS '승인, 거절 또는 자동 만료 처리 시각';
COMMENT ON COLUMN JOIN_REQUEST.CREATED_AT IS '신청 시각';

-- PAYMENT_PROOF: 납입 증빙 (회원·모임·납입월당 1건). 모임장 승인/보완, 관리자 참조번호
CREATE TABLE PAYMENT_PROOF (
                               PAYMENT_PROOF_ID       NUMBER(10)     NOT NULL,
                               GROUP_ID               NUMBER(10)     NOT NULL,
                               MEMBER_ID              NUMBER(10)     NOT NULL,
                               PAY_MONTH              CHAR(6)        NOT NULL,
                               ROUND_NO               NUMBER(2)      NOT NULL,
                               AMOUNT                 NUMBER(12)     NOT NULL,
                               PAY_DATE               DATE           NOT NULL,
                               STATUS                 VARCHAR2(10)   DEFAULT 'PENDING' NOT NULL,
                               RESUBMIT_COUNT         NUMBER(2)      DEFAULT 0 NOT NULL,
                               REF_NO                 VARCHAR2(20),
                               REF_ASSIGNED_AT        DATE,
                               REF_CORRECT_COUNT      NUMBER(2)      DEFAULT 0 NOT NULL,
                               REVIEWED_BY            NUMBER(10),
                               REVIEWED_AT            DATE,
                               REVIEW_MEMO            VARCHAR2(500),
                               CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                               CONSTRAINT PK_PAYMENT_PROOF PRIMARY KEY (PAYMENT_PROOF_ID),
                               CONSTRAINT UK_PAYMENT_PROOF_REF_NO UNIQUE (REF_NO),
                               CONSTRAINT UK_PAYMENT_PROOF_1 UNIQUE (GROUP_ID, MEMBER_ID, PAY_MONTH, ROUND_NO),
                               CONSTRAINT CK_PAYMENT_PROOF_1 CHECK (STATUS IN ('PENDING','CONFIRMED','REVISION')),
                               CONSTRAINT CK_PAYMENT_PROOF_2 CHECK (AMOUNT > 0),
                               CONSTRAINT CK_PAYMENT_PROOF_3 CHECK (ROUND_NO >= 1)
);
COMMENT ON TABLE PAYMENT_PROOF IS '납입 증빙 (모임·회원·귀속 월·회차당 1건, UK_PAYMENT_PROOF_1). 모임장 승인/보완, 관리자 참조번호';
COMMENT ON COLUMN PAYMENT_PROOF.GROUP_ID IS '모임';
COMMENT ON COLUMN PAYMENT_PROOF.MEMBER_ID IS '납입 회원';
COMMENT ON COLUMN PAYMENT_PROOF.PAY_MONTH IS '납입 귀속 연월 YYYYMM. 미납 판정 키';
COMMENT ON COLUMN PAYMENT_PROOF.ROUND_NO IS '납입 회차 1~MONTHS';
COMMENT ON COLUMN PAYMENT_PROOF.AMOUNT IS '납입액 (원)';
COMMENT ON COLUMN PAYMENT_PROOF.PAY_DATE IS '실제 납입일';
COMMENT ON COLUMN PAYMENT_PROOF.STATUS IS 'PENDING(확인 대기) / CONFIRMED(납입 확인) / REVISION(보완 요청). REVISION 건 재제출 시 PENDING 복귀';
COMMENT ON COLUMN PAYMENT_PROOF.RESUBMIT_COUNT IS '보완 재제출 횟수';
COMMENT ON COLUMN PAYMENT_PROOF.REF_NO IS '거래 참조번호 MOAVA-00001. CONFIRMED 건만 지정 (F24)';
COMMENT ON COLUMN PAYMENT_PROOF.REF_ASSIGNED_AT IS '참조번호 최초 지정 시각';
COMMENT ON COLUMN PAYMENT_PROOF.REF_CORRECT_COUNT IS 'ADMIN 정정 횟수. 이전 값·사유는 AUDIT_LOG(REF_CORRECT)';
COMMENT ON COLUMN PAYMENT_PROOF.REVIEWED_BY IS '검토한 모임장';
COMMENT ON COLUMN PAYMENT_PROOF.REVIEWED_AT IS '검토 시각';
COMMENT ON COLUMN PAYMENT_PROOF.REVIEW_MEMO IS '보완 요청 사유';
COMMENT ON COLUMN PAYMENT_PROOF.CREATED_AT IS '업로드 시각';

-- PAYMENT_PROOF_FILE: 증빙 파일 메타 (증빙당 1~5개)
CREATE TABLE PAYMENT_PROOF_FILE (
                                    FILE_ID                NUMBER(10)     NOT NULL,
                                    PAYMENT_PROOF_ID       NUMBER(10)     NOT NULL,
                                    SORT_NO                NUMBER(1)      DEFAULT 1 NOT NULL,
                                    FILE_PATH              VARCHAR2(500)  NOT NULL,
                                    ORIGIN_NAME            VARCHAR2(255)  NOT NULL,
                                    MIME_TYPE              VARCHAR2(50)   NOT NULL,
                                    FILE_SIZE              NUMBER(10)     NOT NULL,
                                    FILE_HASH              CHAR(64)       NOT NULL,
                                    ENCRYPTED_YN           CHAR(1)        DEFAULT 'Y' NOT NULL,
                                    KEY_VER                NUMBER(2)      DEFAULT 1 NOT NULL,
                                    CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                                    CONSTRAINT PK_PAYMENT_PROOF_FILE PRIMARY KEY (FILE_ID),
                                    CONSTRAINT UK_PAYMENT_PROOF_FILE_1 UNIQUE (PAYMENT_PROOF_ID, SORT_NO),
                                    CONSTRAINT CK_PAYMENT_PROOF_FILE_1 CHECK (SORT_NO BETWEEN 1 AND 5),
                                    CONSTRAINT CK_PAYMENT_PROOF_FILE_2 CHECK (FILE_SIZE BETWEEN 1 AND 1048576),
                                    CONSTRAINT CK_PAYMENT_PROOF_FILE_3 CHECK (MIME_TYPE IN ('image/jpeg','image/png','image/webp','application/pdf')),
                                    CONSTRAINT CK_PAYMENT_PROOF_FILE_4 CHECK (ENCRYPTED_YN IN ('Y','N'))
);
COMMENT ON TABLE PAYMENT_PROOF_FILE IS '증빙 파일 메타 (증빙당 1~5개)';
COMMENT ON COLUMN PAYMENT_PROOF_FILE.PAYMENT_PROOF_ID IS '증빙';
COMMENT ON COLUMN PAYMENT_PROOF_FILE.SORT_NO IS '표시 순서 1~5';
COMMENT ON COLUMN PAYMENT_PROOF_FILE.FILE_PATH IS '서버 저장 경로. 파일명은 UUID(원래 파일명에 이름·계좌가 들어오므로)';
COMMENT ON COLUMN PAYMENT_PROOF_FILE.ORIGIN_NAME IS '원본 파일명';
COMMENT ON COLUMN PAYMENT_PROOF_FILE.MIME_TYPE IS 'image/jpeg, image/png, image/webp, application/pdf';
COMMENT ON COLUMN PAYMENT_PROOF_FILE.FILE_SIZE IS '바이트. 1,048,576 이하';
COMMENT ON COLUMN PAYMENT_PROOF_FILE.FILE_HASH IS 'SHA-256 (REQ-SEC-02). 암호화 전 원본 기준, 동일 해시 재사용 차단';
COMMENT ON COLUMN PAYMENT_PROOF_FILE.ENCRYPTED_YN IS '파일 본문 AES-256-GCM 암호화 저장 여부(입금내역서에 계좌·잔액 노출)';
COMMENT ON COLUMN PAYMENT_PROOF_FILE.KEY_VER IS '파일 암호화 키 버전';

-- OVERDUE_CASE: 미납 자동 감지 결과. 일 배치가 PAY_DAY+5일 경과 및 납입 확인 없음을 찾아 생성, 관리자가 조치
CREATE TABLE OVERDUE_CASE (
                              OVERDUE_CASE_ID        NUMBER(10)     NOT NULL,
                              GROUP_ID               NUMBER(10)     NOT NULL,
                              MEMBER_ID              NUMBER(10)     NOT NULL,
                              PAY_MONTH              CHAR(6)        NOT NULL,
                              DUE_DATE               DATE           NOT NULL,
                              STATUS                 VARCHAR2(15)   DEFAULT 'DETECTED' NOT NULL,
                              ADMIN_NOTIFIED_AT      DATE,
                              WARNED_AT              DATE,
                              RESOLVED_AT            DATE,
                              CONSTRAINT PK_OVERDUE_CASE PRIMARY KEY (OVERDUE_CASE_ID),
                              CONSTRAINT UK_OVERDUE_CASE_1 UNIQUE (GROUP_ID, MEMBER_ID, PAY_MONTH),
                              CONSTRAINT CK_OVERDUE_CASE_1 CHECK (STATUS IN ('DETECTED','WARNED','RESTRICTED','RESOLVED'))
);
COMMENT ON TABLE OVERDUE_CASE IS '미납 자동 감지 결과. 일 배치가 PAY_DAY+5일 경과 및 납입 확인 없음을 찾아 생성, 관리자가 조치';
COMMENT ON COLUMN OVERDUE_CASE.GROUP_ID IS '모임';
COMMENT ON COLUMN OVERDUE_CASE.MEMBER_ID IS '미납 회원';
COMMENT ON COLUMN OVERDUE_CASE.PAY_MONTH IS '미납 대상 연월';
COMMENT ON COLUMN OVERDUE_CASE.DUE_DATE IS '납부 기한 = 해당 월 PAY_DAY. 경과일은 조회 시 TRUNC(SYSDATE) - DUE_DATE 로 산출 (OVERDUE_DAYS 컬럼 없음)';
COMMENT ON COLUMN OVERDUE_CASE.STATUS IS 'DETECTED(감지·관리자 알림) / WARNED(회원 경고 발송) / RESTRICTED(채팅 제한) / RESOLVED(납입 확인으로 해소)';
COMMENT ON COLUMN OVERDUE_CASE.ADMIN_NOTIFIED_AT IS '관리자 알림 발송 시각';
COMMENT ON COLUMN OVERDUE_CASE.WARNED_AT IS '회원 경고 발송 시각';
COMMENT ON COLUMN OVERDUE_CASE.RESOLVED_AT IS '해소 시각';

-- PAYOUT_REQUEST: 곗돈 수령 회차 신청. 신청 건별로 동의 요청→동의→확정 (결정 1)
CREATE TABLE PAYOUT_REQUEST (
                                PAYOUT_REQUEST_ID      NUMBER(10)     NOT NULL,
                                GROUP_ID               NUMBER(10)     NOT NULL,
                                MEMBER_ID              NUMBER(10)     NOT NULL,
                                ROUND_NO               NUMBER(2)      NOT NULL,
                                REASON                 VARCHAR2(500)  NOT NULL,
                                AI_SUGGESTED_ROUND     NUMBER(2),
                                STATUS                 VARCHAR2(15)   DEFAULT 'REQUESTED' NOT NULL,
                                DECIDED_AT             DATE,
                                CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                                CONSTRAINT PK_PAYOUT_REQUEST PRIMARY KEY (PAYOUT_REQUEST_ID),
                                CONSTRAINT CK_PAYOUT_REQUEST_1 CHECK (STATUS IN ('REQUESTED','CONSENT_PENDING','CONFIRMED','REJECTED')),
                                CONSTRAINT CK_PAYOUT_REQUEST_2 CHECK (ROUND_NO >= 1)
);
COMMENT ON TABLE PAYOUT_REQUEST IS '곗돈 수령 회차 신청. 신청 건별로 동의 요청→동의→확정 (결정 1)';
COMMENT ON COLUMN PAYOUT_REQUEST.GROUP_ID IS '모임';
COMMENT ON COLUMN PAYOUT_REQUEST.MEMBER_ID IS '신청 회원';
COMMENT ON COLUMN PAYOUT_REQUEST.ROUND_NO IS '희망 회차 1~MAX_MEMBERS';
COMMENT ON COLUMN PAYOUT_REQUEST.REASON IS '신청 사유';
COMMENT ON COLUMN PAYOUT_REQUEST.AI_SUGGESTED_ROUND IS 'AI 제안 회차 (연체 확률 내림차순 배치)';
COMMENT ON COLUMN PAYOUT_REQUEST.STATUS IS 'REQUESTED(신청) / CONSENT_PENDING(동의 요청) / CONFIRMED(확정) / REJECTED(재조정)';
COMMENT ON COLUMN PAYOUT_REQUEST.DECIDED_AT IS '확정·반려 시각';

-- WITHDRAWAL_REQUEST: 중도 탈퇴 신청. 모임장이 정산안(금액)을 등록하고 나머지 회원 전원 동의 후 승인 (결정 4)
CREATE TABLE WITHDRAWAL_REQUEST (
                                    WITHDRAWAL_REQUEST_ID  NUMBER(10)     NOT NULL,
                                    GROUP_ID               NUMBER(10)     NOT NULL,
                                    MEMBER_ID              NUMBER(10)     NOT NULL,
                                    REASON_TYPE            VARCHAR2(10)   NOT NULL,
                                    DETAIL                 VARCHAR2(1000) NOT NULL,
                                    SPECIAL_YN             CHAR(1)        DEFAULT 'N' NOT NULL,
                                    SETTLEMENT_AMOUNT      NUMBER(12),
                                    SETTLEMENT_MEMO        VARCHAR2(500),
                                    SETTLEMENT_BY          NUMBER(10),
                                    SETTLEMENT_AT          DATE,
                                    STATUS                 VARCHAR2(20)   DEFAULT 'SETTLEMENT_PENDING' NOT NULL,
                                    RECONFIRM_COUNT        NUMBER(2)      DEFAULT 0 NOT NULL,
                                    DECIDED_AT             DATE,
                                    CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                                    CONSTRAINT PK_WITHDRAWAL_REQUEST PRIMARY KEY (WITHDRAWAL_REQUEST_ID),
                                    CONSTRAINT CK_WITHDRAWAL_REQUEST_1 CHECK (REASON_TYPE IN ('ECONOMIC','PERSONAL','OTHER')),
                                    CONSTRAINT CK_WITHDRAWAL_REQUEST_2 CHECK (SPECIAL_YN IN ('Y','N')),
                                    CONSTRAINT CK_WITHDRAWAL_REQUEST_3 CHECK (STATUS IN ('SETTLEMENT_PENDING','CONSENT_PENDING','APPROVED','REJECTED'))
);
COMMENT ON TABLE WITHDRAWAL_REQUEST IS '중도 탈퇴 신청. 모임장이 정산안(금액)을 등록하고 나머지 회원 전원 동의 후 승인 (결정 4)';
COMMENT ON COLUMN WITHDRAWAL_REQUEST.GROUP_ID IS '모임';
COMMENT ON COLUMN WITHDRAWAL_REQUEST.MEMBER_ID IS '탈퇴 신청 회원';
COMMENT ON COLUMN WITHDRAWAL_REQUEST.REASON_TYPE IS 'ECONOMIC(경제적) / PERSONAL(개인) / OTHER(기타)';
COMMENT ON COLUMN WITHDRAWAL_REQUEST.DETAIL IS '상세 사유';
COMMENT ON COLUMN WITHDRAWAL_REQUEST.SPECIAL_YN IS '특별 정산 예외 요청 (수령 완료자)';
COMMENT ON COLUMN WITHDRAWAL_REQUEST.SETTLEMENT_AMOUNT IS '정산 금액 (원). 모임장 입력. +는 환급, -는 추가 납부';
COMMENT ON COLUMN WITHDRAWAL_REQUEST.SETTLEMENT_MEMO IS '정산 산출 근거';
COMMENT ON COLUMN WITHDRAWAL_REQUEST.SETTLEMENT_BY IS '정산안 등록 모임장';
COMMENT ON COLUMN WITHDRAWAL_REQUEST.SETTLEMENT_AT IS '정산안 등록 시각. 이후에만 동의 요청 가능';
COMMENT ON COLUMN WITHDRAWAL_REQUEST.STATUS IS 'SETTLEMENT_PENDING(정산안 대기) / CONSENT_PENDING(동의 대기) / APPROVED(탈퇴 완료) / REJECTED';
COMMENT ON COLUMN WITHDRAWAL_REQUEST.RECONFIRM_COUNT IS '탈퇴 재확인 요청 횟수';
COMMENT ON COLUMN WITHDRAWAL_REQUEST.DECIDED_AT IS '승인·반려 시각';

-- CONSENT: 공식 동의 기록 (수령 순서 / 정산안 / 계좌 변경 / 승계 공용). 법적·운영 감사 증거
CREATE TABLE CONSENT (
                         CONSENT_ID             NUMBER(10)     NOT NULL,
                         CONSENT_TYPE           VARCHAR2(15)   NOT NULL,
                         TARGET_ID              NUMBER(10)     NOT NULL,
                         GROUP_ID               NUMBER(10)     NOT NULL,
                         MEMBER_ID              NUMBER(10)     NOT NULL,
                         AGREED_YN              CHAR(1)        NOT NULL,
                         AGREED_AT              DATE           DEFAULT NULL,
                         CONSTRAINT PK_CONSENT PRIMARY KEY (CONSENT_ID),
                         CONSTRAINT UK_CONSENT_1 UNIQUE (CONSENT_TYPE, TARGET_ID, MEMBER_ID),
                         CONSTRAINT CK_CONSENT_1 CHECK (CONSENT_TYPE IN ('ORDER','SETTLEMENT','ACCOUNT','TRANSFER')),
                         CONSTRAINT CK_CONSENT_2 CHECK (
                             (CONSENT_TYPE = 'ORDER' AND AGREED_YN IN ('Y','N','P'))
                                 OR (CONSENT_TYPE IN ('SETTLEMENT','ACCOUNT','TRANSFER') AND AGREED_YN IN ('Y','N'))
                             )
);
COMMENT ON TABLE CONSENT IS '공식 동의 기록 (수령 순서 / 정산안 / 계좌 변경 / 승계 공용). 법적·운영 감사 증거';
COMMENT ON COLUMN CONSENT.CONSENT_TYPE IS 'ORDER(수령 순서) / SETTLEMENT(정산안) / ACCOUNT(계좌 변경) / TRANSFER(모임장 승계)';
COMMENT ON COLUMN CONSENT.TARGET_ID IS 'PAYOUT_REQUEST_ID / WITHDRAWAL_REQUEST_ID / GYE_ACCOUNT_ID / TRANSFER_ID (TYPE으로 구분, FK 없음)';
COMMENT ON COLUMN CONSENT.GROUP_ID IS '모임 (조회 편의)';
COMMENT ON COLUMN CONSENT.MEMBER_ID IS '동의 회원';
COMMENT ON COLUMN CONSENT.AGREED_YN IS 'P 미응답 / Y 동의 / N 거부';
COMMENT ON COLUMN CONSENT.AGREED_AT IS '실제 동의·거부 응답 시각. 미응답은 NULL';

-- DISSOLUTION_REQUEST: 해산 신청 → 관리자 최종 승인
CREATE TABLE DISSOLUTION_REQUEST (
                                     DISSOLUTION_REQUEST_ID NUMBER(10)     NOT NULL,
                                     GROUP_ID               NUMBER(10)     NOT NULL,
                                     REQUESTED_BY           NUMBER(10)     NOT NULL,
                                     REASON                 VARCHAR2(1000) NOT NULL,
                                     STATUS                 VARCHAR2(10)   DEFAULT 'PENDING' NOT NULL,
                                     DECIDED_BY             NUMBER(10),
                                     DECIDED_AT             DATE,
                                     CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                                     CONSTRAINT PK_DISSOLUTION_REQUEST PRIMARY KEY (DISSOLUTION_REQUEST_ID),
                                     CONSTRAINT CK_DISSOLUTION_REQUEST_1 CHECK (STATUS IN ('PENDING','APPROVED','REJECTED'))
);
COMMENT ON TABLE DISSOLUTION_REQUEST IS '해산 신청 → 관리자 최종 승인';
COMMENT ON COLUMN DISSOLUTION_REQUEST.GROUP_ID IS '모임';
COMMENT ON COLUMN DISSOLUTION_REQUEST.REQUESTED_BY IS '신청 모임장';
COMMENT ON COLUMN DISSOLUTION_REQUEST.REASON IS '해산 사유';
COMMENT ON COLUMN DISSOLUTION_REQUEST.STATUS IS 'PENDING(관리자 검토 대기) / APPROVED / REJECTED';
COMMENT ON COLUMN DISSOLUTION_REQUEST.DECIDED_BY IS '승인·반려 처리 관리자 (FK ADMIN)';
-- SANCTION: 제재·조치 이력. 모임 내(모임장)와 플랫폼(관리자)을 TYPE으로 구분
CREATE TABLE SANCTION (
                          SANCTION_ID            NUMBER(10)     NOT NULL,
                          SANCTION_TYPE          VARCHAR2(15)   NOT NULL,
                          MEMBER_ID              NUMBER(10)     NOT NULL,
                          GROUP_ID               NUMBER(10),
                          ISSUED_MEMBER_ID       NUMBER(10),
                          ISSUED_ADMIN_ID        NUMBER(10),
                          REASON                 VARCHAR2(500)  NOT NULL,
                          CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                          CONSTRAINT PK_SANCTION PRIMARY KEY (SANCTION_ID),
                          CONSTRAINT CK_SANCTION_1 CHECK (SANCTION_TYPE IN ('WARN','EXPEL','BAN','BAN_RELEASE','CHAT_RESTRICT','CHAT_RELEASE','ORDER_REVIEW')),
                          CONSTRAINT CK_SANCTION_2 CHECK ((ISSUED_MEMBER_ID IS NOT NULL AND ISSUED_ADMIN_ID IS NULL) OR (ISSUED_MEMBER_ID IS NULL AND ISSUED_ADMIN_ID IS NOT NULL))
);
COMMENT ON TABLE SANCTION IS '제재·조치 이력. 모임 내(모임장)와 플랫폼(관리자)을 TYPE으로 구분';
COMMENT ON COLUMN SANCTION.SANCTION_TYPE IS 'WARN(경고) / EXPEL(강제 탈퇴) / BAN(가입 제한) / BAN_RELEASE / CHAT_RESTRICT / CHAT_RELEASE / ORDER_REVIEW(후순위 검토)';
COMMENT ON COLUMN SANCTION.MEMBER_ID IS '대상 회원';
COMMENT ON COLUMN SANCTION.GROUP_ID IS '모임. BAN/BAN_RELEASE는 NULL';
COMMENT ON COLUMN SANCTION.ISSUED_MEMBER_ID IS '조치한 모임장 (WARN / EXPEL). 관리자 조치는 NULL';
COMMENT ON COLUMN SANCTION.ISSUED_ADMIN_ID IS '조치한 관리자 (BAN / BAN_RELEASE / CHAT_RESTRICT / CHAT_RELEASE / ORDER_REVIEW). 모임장 조치는 NULL';
COMMENT ON COLUMN SANCTION.REASON IS '사유';

-- CHAT_MESSAGE: 모임별 채팅 메시지 (STOMP 브로드캐스트 후 영구 저장)
CREATE TABLE CHAT_MESSAGE (
                            MESSAGE_ID             NUMBER(12)     NOT NULL,
                            GROUP_ID               NUMBER(10)     NOT NULL,
                            MEMBER_ID              NUMBER(10),
                            MSG_TYPE               VARCHAR2(10)   DEFAULT 'USER' NOT NULL,
                            BODY_ENC               VARCHAR2(2800) NOT NULL,
                            KEY_VER                NUMBER(2)      DEFAULT 1 NOT NULL,
                            SENT_AT                DATE           DEFAULT SYSDATE NOT NULL,
                            CONSTRAINT PK_CHAT_MESSAGE PRIMARY KEY (MESSAGE_ID),
                            CONSTRAINT CK_CHAT_MESSAGE_1 CHECK (MSG_TYPE IN ('USER','SYSTEM')),
                            CONSTRAINT CK_CHAT_MESSAGE_2 CHECK ((MSG_TYPE = 'USER' AND MEMBER_ID IS NOT NULL) OR (MSG_TYPE = 'SYSTEM' AND MEMBER_ID IS NULL))
);
COMMENT ON TABLE CHAT_MESSAGE IS '모임별 채팅 메시지 (STOMP 브로드캐스트 후 영구 저장)';
COMMENT ON COLUMN CHAT_MESSAGE.GROUP_ID IS '채팅방 = 모임';
COMMENT ON COLUMN CHAT_MESSAGE.MEMBER_ID IS '발신자. SYSTEM 메시지는 NULL';
COMMENT ON COLUMN CHAT_MESSAGE.BODY_ENC IS '본문(500자) AES-256-GCM 암호문(Base64, 최대 2800바이트: 한글 500자=1500B+IV·태그 후 Base64 확장). 사적 대화에 계좌·연락처가 섞이므로 암호화, 채팅 검색 기능 없음 전제. XSS 이스케이프는 표시 시';
COMMENT ON COLUMN CHAT_MESSAGE.KEY_VER IS '암호화 키 버전';
COMMENT ON COLUMN CHAT_MESSAGE.SENT_AT IS '발신 시각';
COMMENT ON COLUMN CHAT_MESSAGE.MSG_TYPE IS 'USER(회원 메시지) / SYSTEM(운영 안내 — 규칙 변경 등, 발신자 없음) (F31·F34)';

-- NOTIFICATION: 인앱 알림함. 삭제는 숨김 처리, SMS 발송 여부 기록
CREATE TABLE NOTIFICATION (
                              NOTIFICATION_ID        NUMBER(12)     NOT NULL,
                              MEMBER_ID              NUMBER(10),
                              ADMIN_ID               NUMBER(10),
                              NOTIF_TYPE             VARCHAR2(30)   NOT NULL,
                              TITLE                  VARCHAR2(100)  NOT NULL,
                              BODY                   VARCHAR2(500)  NOT NULL,
                              LINK_PAGE              VARCHAR2(100),
                              REF_ID                 NUMBER(10),
                              READ_YN                CHAR(1)        DEFAULT 'N' NOT NULL,
                              DELETED_YN             CHAR(1)        DEFAULT 'N' NOT NULL,
                              SMS_SENT_YN            CHAR(1)        DEFAULT 'N' NOT NULL,
                              CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                              CONSTRAINT PK_NOTIFICATION PRIMARY KEY (NOTIFICATION_ID),
                              CONSTRAINT CK_NOTIFICATION_RECEIVER CHECK ((MEMBER_ID IS NOT NULL AND ADMIN_ID IS NULL) OR (MEMBER_ID IS NULL AND ADMIN_ID IS NOT NULL)),
                              CONSTRAINT CK_NOTIFICATION_1 CHECK (READ_YN IN ('Y','N')),
                              CONSTRAINT CK_NOTIFICATION_2 CHECK (DELETED_YN IN ('Y','N')),
                              CONSTRAINT CK_NOTIFICATION_3 CHECK (SMS_SENT_YN IN ('Y','N'))
);
COMMENT ON TABLE NOTIFICATION IS '인앱 알림함 (회원·관리자 공용, 수신자는 MEMBER_ID/ADMIN_ID 중 하나). 삭제는 숨김 처리, SMS 발송 여부 기록';
COMMENT ON COLUMN NOTIFICATION.MEMBER_ID IS '수신 회원. 관리자 수신 알림은 NULL';
COMMENT ON COLUMN NOTIFICATION.ADMIN_ID IS '수신 관리자 (FK ADMIN). 미납 5일차·해산 신청·신고 접수·계좌 확인 요청 — 활성 관리자 1명당 1행. 회원 수신 알림은 NULL';
COMMENT ON COLUMN NOTIFICATION.NOTIF_TYPE IS '알림 코드표 v3.2(23종, NotifType.java). 회원: JOIN_RESULT / CONSENT_REQ / CONSENT_RESULT / PROOF_REVISION / PAY_DUE_3D(SMS) / PAY_OVERDUE / OVERDUE_WARNED(SMS) / SANCTION / WITHDRAW_DONE / DISSOLVED / REPORT_STATUS / RISK_UP(SMS) / RULE_UPDATED. 모임장: JOIN_REQUEST / PROOF_PENDING / ORDER_REQUEST / ACCOUNT_VERIFIED / DISSOLVE_REJECTED / WITHDRAW_REQUEST. 관리자: ACCOUNT_VERIFY_REQ / OVERDUE_5D / DISSOLVE_REQUEST / REPORT_RECEIVED';
COMMENT ON COLUMN NOTIFICATION.TITLE IS '제목';
COMMENT ON COLUMN NOTIFICATION.BODY IS '본문';
COMMENT ON COLUMN NOTIFICATION.LINK_PAGE IS '클릭 시 이동 화면 (예: /groups/101/operations)';
COMMENT ON COLUMN NOTIFICATION.REF_ID IS '관련 레코드 ID';
COMMENT ON COLUMN NOTIFICATION.READ_YN IS '읽음';
COMMENT ON COLUMN NOTIFICATION.DELETED_YN IS '사용자 삭제(숨김). 실제 DELETE 금지. 정리 배치(매일 03:30)가 오래된 알림도 숨김 처리';
COMMENT ON COLUMN NOTIFICATION.SMS_SENT_YN IS 'SOLAPI 발송 성공 여부 (F36). SMS 대상 PAY_DUE_3D·RISK_UP·OVERDUE_WARNED만';

-- SUPPORT_REPORT: 고객센터 신고·문의. 본인만 조회, ADMIN 전체 조회·상태 변경
CREATE TABLE SUPPORT_REPORT (
                                SUPPORT_REPORT_ID      NUMBER(10)     NOT NULL,
                                MEMBER_ID              NUMBER(10)     NOT NULL,
                                GROUP_ID               NUMBER(10),
                                CATEGORY               VARCHAR2(20)   NOT NULL,
                                DETAIL_ENC             CLOB           NOT NULL,
                                KEY_VER                NUMBER(2)      DEFAULT 1 NOT NULL,
                                STATUS                 VARCHAR2(10)   DEFAULT 'RECEIVED' NOT NULL,
                                ADMIN_MEMO             VARCHAR2(1000),
                                HANDLED_BY             NUMBER(10),
                                CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                                UPDATED_AT             DATE,
                                CONSTRAINT PK_SUPPORT_REPORT PRIMARY KEY (SUPPORT_REPORT_ID),
                                CONSTRAINT CK_SUPPORT_REPORT_1 CHECK (CATEGORY IN ('ACCOUNT_CHANGE','FUND_MISUSE','PROOF_MISMATCH','OTHER')),
                                CONSTRAINT CK_SUPPORT_REPORT_2 CHECK (STATUS IN ('RECEIVED','REVIEWING','MORE_INFO','CLOSED','REJECTED'))
);
COMMENT ON TABLE SUPPORT_REPORT IS '고객센터 신고·문의. 본인만 조회, ADMIN 전체 조회·상태 변경';
COMMENT ON COLUMN SUPPORT_REPORT.MEMBER_ID IS '신고 회원';
COMMENT ON COLUMN SUPPORT_REPORT.GROUP_ID IS '대상 모임 (선택)';
COMMENT ON COLUMN SUPPORT_REPORT.CATEGORY IS 'ACCOUNT_CHANGE(계좌 변경 의심) / FUND_MISUSE(자금 유용) / PROOF_MISMATCH(증빙 불일치) / OTHER';
COMMENT ON COLUMN SUPPORT_REPORT.DETAIL_ENC IS '신고 상세 내용 AES-256-GCM 암호문. 제3자 실명·계좌 포함 가능성 → 신고자·ADMIN만 복호화';
COMMENT ON COLUMN SUPPORT_REPORT.KEY_VER IS '암호화 키 버전';
COMMENT ON COLUMN SUPPORT_REPORT.STATUS IS 'RECEIVED(접수) / REVIEWING(검토 중) / MORE_INFO(추가 자료 요청) / CLOSED(처리 완료) / REJECTED(반려)';
COMMENT ON COLUMN SUPPORT_REPORT.ADMIN_MEMO IS '최근 처리 메모 (상태 변경 시 필수). 전체 이력은 AUDIT_LOG(REPORT_STATUS)';
COMMENT ON COLUMN SUPPORT_REPORT.HANDLED_BY IS '처리 관리자 (FK ADMIN)';

-- AUDIT_LOG: 모임 운영·관리자 조치 감사 로그. Append-only (UPDATE/DELETE 금지)
CREATE TABLE AUDIT_LOG (
                           AUDIT_LOG_ID           NUMBER(12)     NOT NULL,
                           GROUP_ID               NUMBER(10),
                           ACTOR_MEMBER_ID        NUMBER(10),
                           ACTOR_ADMIN_ID         NUMBER(10),
                           TARGET_MEMBER_ID       NUMBER(10),
                           EVENT_TYPE             VARCHAR2(40)   NOT NULL,
                           DESCRIPTION            VARCHAR2(1000) NOT NULL,
                           CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                           CONSTRAINT PK_AUDIT_LOG PRIMARY KEY (AUDIT_LOG_ID),
                           CONSTRAINT CK_AUDIT_LOG_1 CHECK (NOT (ACTOR_MEMBER_ID IS NOT NULL AND ACTOR_ADMIN_ID IS NOT NULL))
);
COMMENT ON TABLE AUDIT_LOG IS '모임 운영·관리자 조치 감사 로그. Append-only (UPDATE/DELETE 금지)';
COMMENT ON COLUMN AUDIT_LOG.GROUP_ID IS '모임. 플랫폼 단위 이벤트는 NULL';
COMMENT ON COLUMN AUDIT_LOG.ACTOR_MEMBER_ID IS '행위 회원·모임장. 관리자·배치 행위는 NULL';
COMMENT ON COLUMN AUDIT_LOG.ACTOR_ADMIN_ID IS '행위 관리자 (FK ADMIN). 회원·배치 행위는 NULL (둘 다 NULL = 배치)';
COMMENT ON COLUMN AUDIT_LOG.TARGET_MEMBER_ID IS '대상 회원 (있을 때)';
COMMENT ON COLUMN AUDIT_LOG.EVENT_TYPE IS 'develop 사용 중(28): GROUP_CREATE / PROOF_UPLOAD / PROOF_RESUBMIT / PROOF_CONFIRM / PROOF_REVISION / REF_ASSIGN / REF_CORRECT / ORDER_REQUEST / ORDER_CONSENT_REQ / ORDER_CONFIRM / WITHDRAW_REQUEST / SETTLEMENT_SET / SETTLEMENT_CONSENT / WITHDRAW_APPROVE / WITHDRAW_CONSENT_EXEMPT / DISSOLVE_REQUEST / DISSOLVE_APPROVE / DISSOLVE_REJECT / OVERDUE_DETECT / OVERDUE_WARN / CHAT_RESTRICT / CHAT_RELEASE / ORDER_REVIEW / BAN / BAN_RELEASE / REPORT_RECEIVED / REPORT_STATUS / RULE_UPDATE. 기능 구현 시 사용 예정: JOIN_REQUEST / JOIN_APPROVE / JOIN_REJECT / JOIN_EXPIRE / PAYOUT_RECEIVED / WARN / EXPEL / ACCOUNT_REGISTER / ACCOUNT_CHANGE / ACCOUNT_VERIFY / LEADER_TRANSFER / VISIBILITY_CHANGE / JOIN_CODE_VERIFY / JOIN_CODE_LOCK / JOIN_CODE_REISSUE';
COMMENT ON COLUMN AUDIT_LOG.DESCRIPTION IS '상세. S·C 등급 값(비밀번호·계좌·연락처·채팅 본문) 기록 금지, 계좌는 뒤 4자리만';
COMMENT ON COLUMN AUDIT_LOG.CREATED_AT IS '발생 시각';


--261103 추가

-- TERMS_POLICY: 법적,금융 규제 약관 마스터(UPDATE/DELETE 금지)
CREATE TABLE TERMS_POLICY (
                              TERMS_ID             NUMBER(10)      NOT NULL,
                              TERMS_CODE           VARCHAR2(30)    NOT NULL,
                              VERSION              VARCHAR2(10)    DEFAULT '1.0' NOT NULL,
                              REGULATION_LAW       VARCHAR2(50)    NOT NULL,
                              TITLE                VARCHAR2(150)   NOT NULL,
                              BODY                 CLOB NOT NULL,
                              REQUIRED_YN          CHAR(1)         DEFAULT 'Y' NOT NULL,
                              ACTIVE_YN            CHAR(1)         DEFAULT 'Y' NOT NULL,
                              RETENTION_MONTHS     NUMBER(3)       DEFAULT 60 NOT NULL,
                              EFFECTIVE_AT         DATE            DEFAULT SYSDATE NOT NULL,
                              CREATED_AT           DATE            DEFAULT SYSDATE NOT NULL,
                              CONSTRAINT PK_TERMS_POLICY PRIMARY KEY (TERMS_ID),
                              CONSTRAINT UK_TERMS_POLICY_1 UNIQUE (TERMS_CODE, VERSION),
                              CONSTRAINT CK_TERMS_POLICY_1 CHECK (REQUIRED_YN IN ('Y','N')),
                              CONSTRAINT CK_TERMS_POLICY_2 CHECK (ACTIVE_YN IN ('Y','N'))
);
COMMENT ON TABLE TERMS_POLICY IS '법적-금융 규제 약관 마스터 (UPDATE/DELETE 금지)';
COMMENT ON COLUMN TERMS_POLICY.TERMS_ID IS '약관 마스터 식별자(시퀸스)';
COMMENT ON COLUMN TERMS_POLICY.TERMS_CODE IS '약관 종류 코드(TOS_SERVICE 서비스이용약관 / PRIVACY_COLLECT 개인정보수집 / CREDIT_AI_PROFI 신용-재무 AI분석 / E_FIN_SETTLE 다자간정산-전자금융 / LOC_KAKAOMAP 위치기반 / MKT_SMS_OPT SMS-마케팅수신)';
COMMENT ON COLUMN TERMS_POLICY.VERSION IS '약관 버전(개정 시 버전업 및 재동의 유도 기준)';
COMMENT ON COLUMN TERMS_POLICY.REGULATION_LAW IS '관련 법령 근거(신용정보법 제32조, 개인정보보호법 제 15조, 정보통신망법 제 50조 등)';
COMMENT ON COLUMN TERMS_POLICY.TITLE IS '화면 표시용 약관 제목';
COMMENT ON COLUMN TERMS_POLICY.BODY IS '약관 전문 본문. 원문 저장, 화면 출력 시 이스케이프(React)';
COMMENT ON COLUMN TERMS_POLICY.REQUIRED_YN IS '필수 동의 여부(Y 필수 / N 선택)';
COMMENT ON COLUMN TERMS_POLICY.ACTIVE_YN IS '현재 시행 중인 활성 버전 여부(Y/N)';
COMMENT ON COLUMN TERMS_POLICY.RETENTION_MONTHS IS '법정 의무 보관 개월 수(금융분쟁 대비 기분 60개월=5년)';
COMMENT ON COLUMN TERMS_POLICY.EFFECTIVE_AT IS '약관 시행일';
COMMENT ON COLUMN TERMS_POLICY.CREATED_AT IS '등록 시각';

-- MEMBER_CONSENT: 회원별 법적-금융 규제 동의 및 철회 이력
CREATE TABLE MEMBER_CONSENT (
                                MEMBER_CONSENT_ID        NUMBER(10)      NOT NULL,
                                MEMBER_ID                NUMBER(10)      NOT NULL,
                                TERMS_ID                 NUMBER(10)      NOT NULL,
                                AGREED_YN                CHAR(1)         DEFAULT 'N' NOT NULL,
                                CONSENT_CHANNEL          VARCHAR2(20)    DEFAULT 'SIGNUP' NOT NULL,
                                IP_ADDR_ENC              VARCHAR2(100),
                                KEY_VER                  NUMBER(2)       DEFAULT 1 NOT NULL,
                                AGREED_AT                DATE            DEFAULT SYSDATE NOT NULL,
                                REVOKED_AT               DATE,
                                CONSTRAINT PK_MEMBER_CONSENT PRIMARY KEY (MEMBER_CONSENT_ID),
                                CONSTRAINT UK_MEMBER_CONSENT_1 UNIQUE (MEMBER_ID, TERMS_ID),
                                CONSTRAINT CK_MEMBER_CONSENT_1 CHECK (AGREED_YN IN ('Y','N')),
                                CONSTRAINT CK_MEMBER_CONSENT_2 CHECK (CONSENT_CHANNEL IN ('SIGNUP','JOURNEY', 'ACCOUNT'))
);
COMMENT ON TABLE MEMBER_CONSENT IS '회원별 법적-금융 규제 동의 및 철회 이력';
COMMENT ON COLUMN MEMBER_CONSENT.MEMBER_CONSENT_ID IS '회원 법적 동의 이력 식별자(시퀸스)';
COMMENT ON COLUMN MEMBER_CONSENT.MEMBER_ID IS '동의 주체 회원';
COMMENT ON COLUMN MEMBER_CONSENT.TERMS_ID IS '동의 대산 약관 마스터 ID';
COMMENT ON COLUMN MEMBER_CONSENT.AGREED_YN IS '현재 동의 상태(Y동의 / N 미동의-철회) CREDIT_AI_PROFI가 N이면 FINANCE_PROFILE 분석 및 FastAPI 추론 차단';
COMMENT ON COLUMN MEMBER_CONSENT.CONSENT_CHANNEL IS '동의 수집 화면(SIGNUP 회원가입 / JOURNEY 맞춤추천 1단계 / ACCOUNT 내 정보 설정)';
COMMENT ON COLUMN MEMBER_CONSENT.IP_ADDR_ENC IS '동의 시점 접속 IP 암호문(Base64), 법적 분쟁 시 본인 동의 입증용';
COMMENT ON COLUMN MEMBER_CONSENT.KEY_VER IS 'IP 암호화 키 버전';
COMMENT ON COLUMN MEMBER_CONSENT.AGREED_AT IS '최초 동의 시각(법적 근거)';
COMMENT ON COLUMN MEMBER_CONSENT.REVOKED_AT IS '선택 약간 동의  철회 시각(철회 시 기록)';

-- CRYPTO_KEY: AES-256-GCM 암호화 키 비전 관리 금고
CREATE TABLE CRYPTO_KEY (
                            KEY_VER            NUMBER(2)        NOT NULL,
                            ALGORITHM          VARCHAR2(30)     DEFAULT 'AES-256-GCM' NOT NULL,
                            ENCRYPTED_DEK      VARCHAR2(255)    NOT NULL,
                            KEY_HASH           CHAR(64)         NOT NULL,
                            STATUS             VARCHAR2(10)     DEFAULT 'ACTIVE' NOT NULL,
                            ACTIVATED_AT       DATE             DEFAULT SYSDATE NOT NULL,
                            RETIRED_AT         DATE,
                            CONSTRAINT PK_CRYPTO_KEY PRIMARY KEY (KEY_VER),
                            CONSTRAINT CK_CRYPTO_KEY_1 CHECK (STATUS IN ('ACTIVE','RETIRED'))
);
COMMENT ON TABLE CRYPTO_KEY IS 'AES-256-GCM 암호화 키 버전 관리 금고';
COMMENT ON COLUMN CRYPTO_KEY.KEY_VER IS '암호화 키 버전 번호 (1, 2...). 7개 테이블의 KEY_VER가 FK로 참조 → 사용 중인 키 행 삭제 불가. KEY_VER=1 시드 후 다른 테이블 적재';
COMMENT ON COLUMN CRYPTO_KEY.ALGORITHM IS '암호화 알고리즘 규격. 앱 자동 생성 키는 ''AES/GCM/NoPadding''(12B IV, 128-bit Tag) 저장, DDL 기본값 ''AES-256-GCM''';
COMMENT ON COLUMN CRYPTO_KEY.ENCRYPTED_DEK IS '서버 환경변수 마스터키(KEK)로 암호화 32바이트 데이터 암호화 키(DEK)Base64 문자열(평문 키 DB 저장 절대 금지)';
COMMENT ON COLUMN CRYPTO_KEY.KEY_HASH IS '복호화된 DEK의 무결성 검증용 SHA-256 해시';
COMMENT ON COLUMN CRYPTO_KEY.STATUS IS 'ACTIVE(신규 암호화 및 복호화 사용) / RETIRED(키 교체 후 과거 데이터 복호화 전용)';
COMMENT ON COLUMN CRYPTO_KEY.ACTIVATED_AT IS '키 버전 활설화 시각';
COMMENT ON COLUMN CRYPTO_KEY.RETIRED_AT IS '키 교체(Rotation)로 인한 비활성화 시각';

-- ADMIN: 관리자 계정
CREATE TABLE ADMIN (
                       ADMIN_ID            NUMBER(10)       NOT NULL,
                       EMAIL               VARCHAR2(100)    NOT NULL,
                       PASSWORD_HASH       VARCHAR2(100)    NOT NULL,
                       NAME                VARCHAR2(50)     NOT NULL,
                       STATUS              VARCHAR2(10)     DEFAULT 'ACTIVE' NOT NULL,
                       CREATED_AT          DATE             DEFAULT SYSDATE NOT NULL,
                       CONSTRAINT PK_ADMIN PRIMARY KEY (ADMIN_ID),
                       CONSTRAINT UK_ADMIN UNIQUE (EMAIL),
                       CONSTRAINT CK_ADMIN_1 CHECK (STATUS IN ('ACTIVE','INACTIVE'))
);
COMMENT ON TABLE ADMIN IS '관리자 계정';
COMMENT ON COLUMN ADMIN.ADMIN_ID IS '관리자 식별자(시퀸스)';
COMMENT ON COLUMN ADMIN.EMAIL IS '관리자 로그인 ID(EX. admin@moava.com)';
COMMENT ON COLUMN ADMIN.PASSWORD_HASH IS 'BCrypt 해시 번호';
COMMENT ON COLUMN ADMIN.NAME IS '관리자 이름/표시명(처리 이력 표시용)';
COMMENT ON COLUMN ADMIN.STATUS IS 'ACTIVE(활성) / INACTIVE(비활성, 로그인 불가)';
COMMENT ON COLUMN ADMIN.CREATED_AT IS '계정 생성 시각';

-- 261120 추가
-- WITHDRAWAL_CONSENT_TARGET: F29 중도 탈퇴 정산안 등록 시점의 고정 동의 대상 + 관리자 면제 기록
-- ※ 기존 CONSENT 행으로는 정산안 등록 당시의 정확한 대상자 목록을 복원할 수 없어 기존 데이터는 백필하지 않음.
--    이미 CONSENT_PENDING 인 정산안은 이 테이블 적용 후 재등록해야 하며, 대상 스냅샷이 생길 때까지 서비스가 승인을 차단함
CREATE TABLE WITHDRAWAL_CONSENT_TARGET (
                                           WITHDRAWAL_REQUEST_ID  NUMBER(10)     NOT NULL,
                                           GROUP_ID               NUMBER(10)     NOT NULL,
                                           MEMBER_ID              NUMBER(10)     NOT NULL,
                                           STATUS                 VARCHAR2(10)   DEFAULT 'PENDING' NOT NULL,
                                           CREATED_AT             DATE           DEFAULT SYSDATE NOT NULL,
                                           RESPONDED_AT           DATE,
                                           EXEMPTED_BY            NUMBER(10),
                                           EXEMPTED_AT            DATE,
                                           EXEMPTION_REASON       VARCHAR2(500 CHAR),
                                           CONSTRAINT PK_WITHDRAWAL_CONSENT_TARGET PRIMARY KEY (WITHDRAWAL_REQUEST_ID, MEMBER_ID),
                                           CONSTRAINT CK_WCT_STATUS CHECK (STATUS IN ('PENDING','AGREED','REJECTED','EXEMPT')),
                                           CONSTRAINT CK_WCT_EXEMPT CHECK (
                                               (STATUS = 'EXEMPT' AND EXEMPTED_BY IS NOT NULL AND EXEMPTED_AT IS NOT NULL AND EXEMPTION_REASON IS NOT NULL)
                                                   OR
                                               (STATUS <> 'EXEMPT' AND EXEMPTED_BY IS NULL AND EXEMPTED_AT IS NULL AND EXEMPTION_REASON IS NULL)
                                               )
);
COMMENT ON TABLE WITHDRAWAL_CONSENT_TARGET IS '중도 탈퇴 정산안 등록 시점의 고정 동의 대상 및 관리자 면제 기록';
COMMENT ON COLUMN WITHDRAWAL_CONSENT_TARGET.WITHDRAWAL_REQUEST_ID IS '중도 탈퇴 신청 (PK 1/2)';
COMMENT ON COLUMN WITHDRAWAL_CONSENT_TARGET.GROUP_ID IS '모임 (조회 편의)';
COMMENT ON COLUMN WITHDRAWAL_CONSENT_TARGET.MEMBER_ID IS '동의 대상 회원 (PK 2/2)';
COMMENT ON COLUMN WITHDRAWAL_CONSENT_TARGET.STATUS IS 'PENDING(미응답) / AGREED(동의) / REJECTED(거부) / EXEMPT(관리자 면제)';
COMMENT ON COLUMN WITHDRAWAL_CONSENT_TARGET.CREATED_AT IS '동의 대상 등록 시각 (정산안 등록 시점)';
COMMENT ON COLUMN WITHDRAWAL_CONSENT_TARGET.RESPONDED_AT IS '동의·거부 응답 시각';
COMMENT ON COLUMN WITHDRAWAL_CONSENT_TARGET.EXEMPTED_BY IS '동의 의무를 면제한 관리자';
COMMENT ON COLUMN WITHDRAWAL_CONSENT_TARGET.EXEMPTED_AT IS '관리자 면제 처리 시각';
COMMENT ON COLUMN WITHDRAWAL_CONSENT_TARGET.EXEMPTION_REASON IS '관리자 동의 의무 면제 사유';

-- ---------- 2. FOREIGN KEY ----------
ALTER TABLE MEMBER ADD CONSTRAINT FK_MEMBER_SYN_ID FOREIGN KEY (SYN_ID) REFERENCES CREDIT_SNAPSHOT (SYN_ID);
ALTER TABLE MEMBER_SOCIAL_ACCOUNT ADD CONSTRAINT FK_MEMBER_SOCIAL_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE FINANCE_PROFILE ADD CONSTRAINT FK_FINANCE_PROFILE_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE FINANCE_MONTHLY ADD CONSTRAINT FK_FINANCE_MONTHLY_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE RISK_SCORE ADD CONSTRAINT FK_RISK_SCORE_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE GYE_GROUP ADD CONSTRAINT FK_GYE_GROUP_OWNER_ID FOREIGN KEY (OWNER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE GYE_MEMBER ADD CONSTRAINT FK_GYE_MEMBER_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE GYE_MEMBER ADD CONSTRAINT FK_GYE_MEMBER_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE GYE_ACCOUNT ADD CONSTRAINT FK_GYE_ACCOUNT_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE GYE_ACCOUNT ADD CONSTRAINT FK_GYE_ACCOUNT_REGISTERED_BY FOREIGN KEY (REGISTERED_BY) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE GYE_ACCOUNT ADD CONSTRAINT FK_GYE_ACCOUNT_VERIFIED_BY FOREIGN KEY (VERIFIED_BY) REFERENCES ADMIN (ADMIN_ID);
ALTER TABLE LEADER_TRANSFER_REQUEST ADD CONSTRAINT FK_LEADER_TRANSFER_REQUEST_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE LEADER_TRANSFER_REQUEST ADD CONSTRAINT FK_LEADER_TRANSFER_REQUEST_FROM_MEMBER_ID FOREIGN KEY (FROM_MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE LEADER_TRANSFER_REQUEST ADD CONSTRAINT FK_LEADER_TRANSFER_REQUEST_TO_MEMBER_ID FOREIGN KEY (TO_MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE JOIN_CODE_ATTEMPT ADD CONSTRAINT FK_JOIN_CODE_ATTEMPT_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE JOIN_CODE_ATTEMPT ADD CONSTRAINT FK_JOIN_CODE_ATTEMPT_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE JOIN_REQUEST ADD CONSTRAINT FK_JOIN_REQUEST_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE JOIN_REQUEST ADD CONSTRAINT FK_JOIN_REQUEST_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE JOIN_REQUEST ADD CONSTRAINT FK_JOIN_REQUEST_DECIDED_BY FOREIGN KEY (DECIDED_BY) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE PAYMENT_PROOF ADD CONSTRAINT FK_PAYMENT_PROOF_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE PAYMENT_PROOF ADD CONSTRAINT FK_PAYMENT_PROOF_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE PAYMENT_PROOF ADD CONSTRAINT FK_PAYMENT_PROOF_REVIEWED_BY FOREIGN KEY (REVIEWED_BY) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE PAYMENT_PROOF_FILE ADD CONSTRAINT FK_PAYMENT_PROOF_FILE_PAYMENT_PROOF_ID FOREIGN KEY (PAYMENT_PROOF_ID) REFERENCES PAYMENT_PROOF (PAYMENT_PROOF_ID);
ALTER TABLE OVERDUE_CASE ADD CONSTRAINT FK_OVERDUE_CASE_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE OVERDUE_CASE ADD CONSTRAINT FK_OVERDUE_CASE_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE PAYOUT_REQUEST ADD CONSTRAINT FK_PAYOUT_REQUEST_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE PAYOUT_REQUEST ADD CONSTRAINT FK_PAYOUT_REQUEST_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE WITHDRAWAL_REQUEST ADD CONSTRAINT FK_WITHDRAWAL_REQUEST_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE WITHDRAWAL_REQUEST ADD CONSTRAINT FK_WITHDRAWAL_REQUEST_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE WITHDRAWAL_REQUEST ADD CONSTRAINT FK_WITHDRAWAL_REQUEST_SETTLEMENT_BY FOREIGN KEY (SETTLEMENT_BY) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE CONSENT ADD CONSTRAINT FK_CONSENT_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE CONSENT ADD CONSTRAINT FK_CONSENT_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE DISSOLUTION_REQUEST ADD CONSTRAINT FK_DISSOLUTION_REQUEST_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE DISSOLUTION_REQUEST ADD CONSTRAINT FK_DISSOLUTION_REQUEST_REQUESTED_BY FOREIGN KEY (REQUESTED_BY) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE DISSOLUTION_REQUEST ADD CONSTRAINT FK_DISSOLUTION_REQUEST_DECIDED_BY FOREIGN KEY (DECIDED_BY) REFERENCES ADMIN (ADMIN_ID);
ALTER TABLE SANCTION ADD CONSTRAINT FK_SANCTION_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE SANCTION ADD CONSTRAINT FK_SANCTION_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE SANCTION ADD CONSTRAINT FK_SANCTION_ISSUED_MEMBER_ID FOREIGN KEY (ISSUED_MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE SANCTION ADD CONSTRAINT FK_SANCTION_ISSUED_ADMIN_ID FOREIGN KEY (ISSUED_ADMIN_ID) REFERENCES ADMIN (ADMIN_ID);
ALTER TABLE CHAT_MESSAGE ADD CONSTRAINT FK_CHAT_MESSAGE_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE CHAT_MESSAGE ADD CONSTRAINT FK_CHAT_MESSAGE_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE NOTIFICATION ADD CONSTRAINT FK_NOTIFICATION_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE NOTIFICATION ADD CONSTRAINT FK_NOTIFICATION_ADMIN_ID FOREIGN KEY (ADMIN_ID) REFERENCES ADMIN (ADMIN_ID);
ALTER TABLE SUPPORT_REPORT ADD CONSTRAINT FK_SUPPORT_REPORT_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE SUPPORT_REPORT ADD CONSTRAINT FK_SUPPORT_REPORT_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE SUPPORT_REPORT ADD CONSTRAINT FK_SUPPORT_REPORT_HANDLED_BY FOREIGN KEY (HANDLED_BY) REFERENCES ADMIN (ADMIN_ID);
ALTER TABLE AUDIT_LOG ADD CONSTRAINT FK_AUDIT_LOG_GROUP_ID FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE AUDIT_LOG ADD CONSTRAINT FK_AUDIT_LOG_ACTOR_MEMBER_ID FOREIGN KEY (ACTOR_MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE AUDIT_LOG ADD CONSTRAINT FK_AUDIT_LOG_ACTOR_ADMIN_ID FOREIGN KEY (ACTOR_ADMIN_ID) REFERENCES ADMIN (ADMIN_ID);
ALTER TABLE AUDIT_LOG ADD CONSTRAINT FK_AUDIT_LOG_TARGET_MEMBER_ID FOREIGN KEY (TARGET_MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);

-- 261103 추가
ALTER TABLE MEMBER_CONSENT ADD CONSTRAINT FK_MEMBER_CONSENT_MEMBER_ID FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE MEMBER_CONSENT ADD CONSTRAINT FK_MEMBER_CONSENT_TERMS_ID FOREIGN KEY (TERMS_ID) REFERENCES TERMS_POLICY (TERMS_ID);
ALTER TABLE MEMBER_CONSENT ADD CONSTRAINT FK_MEMBER_CONSENT_KEY_VER FOREIGN KEY (KEY_VER) REFERENCES CRYPTO_KEY (KEY_VER);

-- 261104 추가: KEY_VER FK 통일 (7개 테이블 → CRYPTO_KEY)
ALTER TABLE MEMBER ADD CONSTRAINT FK_MEMBER_KEY_VER FOREIGN KEY (KEY_VER) REFERENCES CRYPTO_KEY (KEY_VER);
ALTER TABLE GYE_GROUP ADD CONSTRAINT FK_GYE_GROUP_JOIN_CODE_KEY_VER FOREIGN KEY (JOIN_CODE_KEY_VER) REFERENCES CRYPTO_KEY (KEY_VER);
ALTER TABLE GYE_ACCOUNT ADD CONSTRAINT FK_GYE_ACCOUNT_KEY_VER FOREIGN KEY (KEY_VER) REFERENCES CRYPTO_KEY (KEY_VER);
ALTER TABLE PAYMENT_PROOF_FILE ADD CONSTRAINT FK_PAYMENT_PROOF_FILE_KEY_VER FOREIGN KEY (KEY_VER) REFERENCES CRYPTO_KEY (KEY_VER);
ALTER TABLE CHAT_MESSAGE ADD CONSTRAINT FK_CHAT_MESSAGE_KEY_VER FOREIGN KEY (KEY_VER) REFERENCES CRYPTO_KEY (KEY_VER);
ALTER TABLE SUPPORT_REPORT ADD CONSTRAINT FK_SUPPORT_REPORT_KEY_VER FOREIGN KEY (KEY_VER) REFERENCES CRYPTO_KEY (KEY_VER);

-- 261120 추가: WITHDRAWAL_CONSENT_TARGET
ALTER TABLE WITHDRAWAL_CONSENT_TARGET ADD CONSTRAINT FK_WCT_REQUEST FOREIGN KEY (WITHDRAWAL_REQUEST_ID) REFERENCES WITHDRAWAL_REQUEST (WITHDRAWAL_REQUEST_ID);
ALTER TABLE WITHDRAWAL_CONSENT_TARGET ADD CONSTRAINT FK_WCT_GROUP FOREIGN KEY (GROUP_ID) REFERENCES GYE_GROUP (GROUP_ID);
ALTER TABLE WITHDRAWAL_CONSENT_TARGET ADD CONSTRAINT FK_WCT_MEMBER FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (MEMBER_ID);
ALTER TABLE WITHDRAWAL_CONSENT_TARGET ADD CONSTRAINT FK_WCT_ADMIN FOREIGN KEY (EXEMPTED_BY) REFERENCES ADMIN (ADMIN_ID);

-- ---------- 3. INDEX (FK 조회 + 자주 쓰는 필터) ----------
CREATE INDEX IX_MEMBER_1 ON MEMBER (STATUS, PURGE_DUE_AT);
CREATE INDEX IX_GYE_GROUP_1 ON GYE_GROUP (REGION_SIGUNGU, STATUS, VISIBILITY);
CREATE INDEX IX_GYE_GROUP_2 ON GYE_GROUP (OWNER_ID);
CREATE INDEX IX_GYE_MEMBER_1 ON GYE_MEMBER (MEMBER_ID, STATUS);
CREATE INDEX IX_PAYMENT_PROOF_1 ON PAYMENT_PROOF (GROUP_ID, STATUS);
CREATE INDEX IX_PAYMENT_PROOF_2 ON PAYMENT_PROOF (MEMBER_ID);
CREATE INDEX IX_OVERDUE_CASE_1 ON OVERDUE_CASE (STATUS);
CREATE INDEX IX_OVERDUE_CASE_2 ON OVERDUE_CASE (MEMBER_ID);
CREATE INDEX IX_PAYOUT_REQUEST_1 ON PAYOUT_REQUEST (GROUP_ID, STATUS);
CREATE INDEX IX_WITHDRAWAL_REQUEST_1 ON WITHDRAWAL_REQUEST (GROUP_ID, STATUS);
CREATE INDEX IX_CONSENT_1 ON CONSENT (GROUP_ID);
CREATE INDEX IX_CONSENT_2 ON CONSENT (MEMBER_ID);
CREATE INDEX IX_CONSENT_3 ON CONSENT (CONSENT_TYPE, TARGET_ID);
CREATE INDEX IX_GYE_ACCOUNT_1 ON GYE_ACCOUNT (GROUP_ID, ACCOUNT_STATUS);
CREATE INDEX IX_GYE_ACCOUNT_2 ON GYE_ACCOUNT (ACCOUNT_NO_HASH);
-- 11/26: 사용 중(CURRENT)·변경 검토 중(PENDING) 계좌는 모든 모임을 통틀어 같은 계좌번호 1건만. RETIRED(이전 계좌)는 NULL이 되어 제외
CREATE UNIQUE INDEX UX_GYE_ACCOUNT_ACTIVE_HASH ON GYE_ACCOUNT (CASE WHEN ACCOUNT_STATUS IN ('CURRENT','PENDING') THEN ACCOUNT_NO_HASH END);
CREATE INDEX IX_JOIN_CODE_ATTEMPT_1 ON JOIN_CODE_ATTEMPT (MEMBER_ID);
CREATE INDEX IX_SANCTION_1 ON SANCTION (MEMBER_ID);
CREATE INDEX IX_SANCTION_2 ON SANCTION (GROUP_ID);
CREATE INDEX IX_CHAT_MESSAGE_1 ON CHAT_MESSAGE (GROUP_ID, SENT_AT);
CREATE INDEX IX_NOTIFICATION_1 ON NOTIFICATION (MEMBER_ID, READ_YN, DELETED_YN);
CREATE INDEX IX_NOTIFICATION_2 ON NOTIFICATION (ADMIN_ID, READ_YN, DELETED_YN);
CREATE INDEX IX_AUDIT_LOG_1 ON AUDIT_LOG (GROUP_ID, CREATED_AT);
CREATE INDEX IX_AUDIT_LOG_2 ON AUDIT_LOG (EVENT_TYPE);
CREATE INDEX IX_RISK_SCORE_1 ON RISK_SCORE (MEMBER_ID, SCORED_AT);
CREATE INDEX IX_JOIN_REQUEST_1 ON JOIN_REQUEST (GROUP_ID, STATUS);
CREATE INDEX IX_SUPPORT_REPORT_1 ON SUPPORT_REPORT (MEMBER_ID);
CREATE INDEX IX_SUPPORT_REPORT_2 ON SUPPORT_REPORT (STATUS);
-- 261104 추가
CREATE INDEX IX_SANCTION_3 ON SANCTION (ISSUED_ADMIN_ID);
CREATE INDEX IX_AUDIT_LOG_3 ON AUDIT_LOG (ACTOR_ADMIN_ID);
CREATE INDEX IX_SUPPORT_REPORT_3 ON SUPPORT_REPORT (HANDLED_BY);
CREATE INDEX IX_MEMBER_CONSENT_1 ON MEMBER_CONSENT (MEMBER_ID);
CREATE INDEX IX_TERMS_POLICY_1 ON TERMS_POLICY (TERMS_CODE, ACTIVE_YN);
-- 261120 추가
CREATE INDEX IX_WCT_GROUP_REQUEST ON WITHDRAWAL_CONSENT_TARGET (GROUP_ID, WITHDRAWAL_REQUEST_ID, STATUS);

-- ---------- 4. SEQUENCE (PK NUMBER 테이블) ----------
CREATE SEQUENCE SEQ_PRODUCTS_SAVING START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_MEMBER START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_MEMBER_SOCIAL_ACCOUNT START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_FINANCE_MONTHLY START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_RISK_SCORE START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_GYE_GROUP START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_GYE_MEMBER START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_GYE_ACCOUNT START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_LEADER_TRANSFER_REQUEST START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_JOIN_CODE_ATTEMPT START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_JOIN_REQUEST START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_PAYMENT_PROOF START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_PAYMENT_PROOF_FILE START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_OVERDUE_CASE START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_PAYOUT_REQUEST START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_WITHDRAWAL_REQUEST START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_CONSENT START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_DISSOLUTION_REQUEST START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_SANCTION START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_CHAT_MESSAGE START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_NOTIFICATION START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_SUPPORT_REPORT START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_AUDIT_LOG START WITH 1 INCREMENT BY 1 NOCACHE;

-- 261103 추가
CREATE SEQUENCE SEQ_TERMS_POLICY START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_MEMBER_CONSENT START WITH 1 INCREMENT BY 1 NOCACHE;
-- CREATE SEQUENCE SEQ_CRYPTO_KEY ...  (KEY_VER는 키 교체 시 직접 +1 지정, 시퀀스 불필요)
CREATE SEQUENCE SEQ_ADMIN START WITH 1 INCREMENT BY 1 NOCACHE;

-- ---------- 5. 거래 참조번호 시퀀스 (MOAVA-00001) ----------
CREATE SEQUENCE SEQ_REF_NO START WITH 1 INCREMENT BY 1 NOCACHE;
-- 사용: 'MOAVA-' || LPAD(SEQ_REF_NO.NEXTVAL, 5, '0')


-- ---------- 6. 시드 (11/05 팀 결정: 틀만 생성, 시드 미적용 → 전부 주석. 필요 시 MOAVA_SEED.sql 실행) ----------
-- ※ 주의: KEY_VER FK 때문에 CRYPTO_KEY에 KEY_VER=1 행이 없으면 MEMBER·GYE_GROUP·GYE_ACCOUNT·CHAT_MESSAGE·SUPPORT_REPORT·PAYMENT_PROOF_FILE·MEMBER_CONSENT INSERT가 실패함.
--    회원가입 테스트 전에 최소한 CRYPTO_KEY 1행은 넣어야 함 (MOAVA_SEED.sql 1번 항목)
-- 이미 시드가 있는 DB(공용 서버 등)는 이 섹션 대신 MOAVA_SEED.sql(MERGE, 중복 안전)을 실행
-- INSERT INTO CRYPTO_KEY (KEY_VER, ALGORITHM, ENCRYPTED_DEK, KEY_HASH, STATUS)
-- VALUES (1, 'AES-256-GCM', '<KEK로 래핑한 DEK Base64 — 백엔드 키 생성 후 입력>', '<SHA-256(DEK) hex 64자>', 'ACTIVE');
-- INSERT INTO ADMIN (ADMIN_ID, EMAIL, PASSWORD_HASH, NAME, STATUS)
-- VALUES (SEQ_ADMIN.NEXTVAL, 'admin@moava.com', '<bcrypt>', '관리자', 'ACTIVE');
-- INSERT INTO TERMS_POLICY (TERMS_ID, TERMS_CODE, VERSION, REGULATION_LAW, TITLE, BODY, REQUIRED_YN, ACTIVE_YN, RETENTION_MONTHS)
--   SELECT SEQ_TERMS_POLICY.NEXTVAL, c, '1.0', l, t, '<약관 전문>', r, 'Y', 60 FROM (
--     SELECT 'TOS_SERVICE' c, '전자상거래법 제13조' l, '서비스 이용약관' t, 'Y' r FROM DUAL UNION ALL
--     SELECT 'PRIVACY_COLLECT', '개인정보보호법 제15조', '개인정보 수집·이용 동의', 'Y' FROM DUAL UNION ALL
--     SELECT 'CREDIT_AI_PROFI', '신용정보법 제32조', '신용·재무 정보 AI 분석 동의', 'Y' FROM DUAL UNION ALL
--     SELECT 'E_FIN_SETTLE', '전자금융거래법 제24조', '다자간 정산·전자금융 이용 동의', 'Y' FROM DUAL UNION ALL
--     SELECT 'LOC_KAKAOMAP', '위치정보법 제19조', '위치기반 서비스 이용 동의', 'N' FROM DUAL UNION ALL
--     SELECT 'MKT_SMS_OPT', '정보통신망법 제50조', 'SMS·마케팅 정보 수신 동의', 'N' FROM DUAL);
-- COMMIT;


-- ---------- 7. 미납 자동 감지 배치 참고 SQL (Spring @Scheduled 02:00) ----------
/*
MERGE INTO OVERDUE_CASE o
USING (
  SELECT g.GROUP_ID, gm.MEMBER_ID,
         TO_CHAR(m.ym, 'YYYYMM') AS PAY_MONTH,
         TRUNC(m.ym) + (g.PAY_DAY - 1) AS DUE_DATE
  FROM GYE_GROUP g
  JOIN GYE_MEMBER gm ON gm.GROUP_ID = g.GROUP_ID AND gm.STATUS = 'ACTIVE'
  JOIN (SELECT ADD_MONTHS(TRUNC(SYSDATE,'MM'), -LEVEL+1) AS ym FROM DUAL CONNECT BY LEVEL <= 36) m
       ON m.ym >= TRUNC(g.STARTED_AT,'MM')
  WHERE g.STATUS = 'ACTIVE'
    AND TRUNC(SYSDATE) - (TRUNC(m.ym) + (g.PAY_DAY - 1)) >= 5   -- 11/04 결정: 5일차
    AND NOT EXISTS (SELECT 1 FROM PAYMENT_PROOF p
                    WHERE p.GROUP_ID = g.GROUP_ID AND p.MEMBER_ID = gm.MEMBER_ID
                      AND p.PAY_MONTH = TO_CHAR(m.ym,'YYYYMM') AND p.STATUS = 'CONFIRMED')
) s ON (o.GROUP_ID = s.GROUP_ID AND o.MEMBER_ID = s.MEMBER_ID AND o.PAY_MONTH = s.PAY_MONTH)
WHEN NOT MATCHED THEN INSERT (OVERDUE_CASE_ID, GROUP_ID, MEMBER_ID, PAY_MONTH, DUE_DATE, STATUS)
     VALUES (SEQ_OVERDUE_CASE.NEXTVAL, s.GROUP_ID, s.MEMBER_ID, s.PAY_MONTH, s.DUE_DATE, 'DETECTED');
-- 경과일은 조회 시 TRUNC(SYSDATE) - DUE_DATE 로 산출 (OVERDUE_DAYS 컬럼 없음)
-- 신규 DETECTED 건마다 활성 관리자 전원에게 NOTIFICATION(ADMIN_ID=각 관리자, NOTIF_TYPE='OVERDUE_5D') 1행씩 생성 + AUDIT_LOG('OVERDUE_DETECT', ACTOR_* NULL = 배치)
-- PAYMENT_PROOF가 CONFIRMED 되면 해당 키의 OVERDUE_CASE.STATUS='RESOLVED'
*/


-- ---------- 8. 오픈뱅킹(F19 계좌 소유 확인 대체 후보) — 261125 추가 ----------
-- =====================================================================
-- [KFTC v2.0 모의 오픈뱅킹 독립 가상 원장 및 OAuth 2.0 DDL]
-- Oracle 21c XE 기준. 기존 MOAVA 테이블과 FK 없이 분리 (feature/MOA-62-openBanking 브랜치, develop 미병합)
-- PK는 IDENTITY 컬럼(시퀀스 불필요), 일시는 TIMESTAMP — 기존 테이블(DATE·시퀀스)과 규칙이 다름
-- =====================================================================

-- 1. 모의 금결원 계좌 마스터 (핀테크이용번호 기준 원장)
CREATE TABLE MOCK_KFTC_ACCOUNT (
                                   FINTECH_USE_NUM  VARCHAR2(24)  NOT NULL,
                                   BANK_CODE        VARCHAR2(10)  NOT NULL, -- 예: 004(국민), 088(신한), 090(카카오), 099(모의)
                                   BANK_NAME        VARCHAR2(50)  NOT NULL,
                                   ACCOUNT_NO       VARCHAR2(30)  NOT NULL,
                                   HOLDER_NAME      VARCHAR2(50)  NOT NULL, -- 예금주 실명
                                   USER_SEQ_NO      VARCHAR2(20)  NOT NULL, -- 오픈뱅킹 사용자 일련번호
                                   BALANCE          NUMBER(15)    DEFAULT 0 NOT NULL,
                                   LEADER_YN        CHAR(1)       DEFAULT 'N' NOT NULL, -- Y: 모임장 수취 허용 계좌, N: 일반 회원 계좌
                                   STATUS           VARCHAR2(20)  DEFAULT 'ACTIVE' NOT NULL, -- ACTIVE, FROZEN, CLOSED
                                   CREATED_AT       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
                                   UPDATED_AT       TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
                                   CONSTRAINT PK_MOCK_KFTC_ACCOUNT PRIMARY KEY (FINTECH_USE_NUM),
                                   CONSTRAINT UK_MOCK_KFTC_ACC_NO UNIQUE (BANK_CODE, ACCOUNT_NO),
                                   CONSTRAINT CK_MOCK_KFTC_ACC_BAL CHECK (BALANCE >= 0),
                                   CONSTRAINT CK_MOCK_KFTC_ACC_LEADER CHECK (LEADER_YN IN ('Y', 'N')),
                                   CONSTRAINT CK_MOCK_KFTC_ACC_STATUS CHECK (STATUS IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);
COMMENT ON TABLE MOCK_KFTC_ACCOUNT IS '모의 금융결제원 오픈뱅킹 계좌 마스터 원장';
COMMENT ON COLUMN MOCK_KFTC_ACCOUNT.FINTECH_USE_NUM IS '24자리 핀테크이용번호';
COMMENT ON COLUMN MOCK_KFTC_ACCOUNT.BANK_CODE IS '금융결제원 표준 은행코드';
COMMENT ON COLUMN MOCK_KFTC_ACCOUNT.HOLDER_NAME IS '예금주 실명';
COMMENT ON COLUMN MOCK_KFTC_ACCOUNT.BALANCE IS '현재 계좌 잔액 (원, 0 이상)';
COMMENT ON COLUMN MOCK_KFTC_ACCOUNT.LEADER_YN IS '모임장 수취 가능 계좌 여부 (Y/N)';

-- 2. 모의 금결원 거래내역 원장 (은행거래고유번호 멱등성 보장)
CREATE TABLE MOCK_KFTC_TRANSACTION (
                                       TX_ID                 NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                       BANK_TRAN_ID          VARCHAR2(20)  NOT NULL, -- 이용기관코드(10) + U + 고유난수(9)
                                       FINTECH_USE_NUM       VARCHAR2(24)  NOT NULL,
                                       INOUT_TYPE            VARCHAR2(10)  NOT NULL, -- 입금 / 출금
                                       TRAN_AMT              NUMBER(15)    NOT NULL,
                                       AFTER_BALANCE_AMT     NUMBER(15)    NOT NULL,
                                       PRINT_CONTENT         VARCHAR2(100) NOT NULL, -- 통장 적요(인자 내역)
                                       COUNTERPARTY_NAME     VARCHAR2(50)  NOT NULL,
                                       COUNTERPARTY_FIN_NUM  VARCHAR2(24)  NULL,
                                       BRANCH_NAME           VARCHAR2(50)  DEFAULT '모아바모의점' NOT NULL,
                                       TRAN_AT               TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
                                       CONSTRAINT FK_MOCK_KFTC_TX_ACC FOREIGN KEY (FINTECH_USE_NUM) REFERENCES MOCK_KFTC_ACCOUNT (FINTECH_USE_NUM),
                                       CONSTRAINT UK_MOCK_KFTC_TX_IDEMPOTENT UNIQUE (BANK_TRAN_ID, INOUT_TYPE),
                                       CONSTRAINT CK_MOCK_KFTC_TX_INOUT CHECK (INOUT_TYPE IN ('입금', '출금')),
                                       CONSTRAINT CK_MOCK_KFTC_TX_AMT CHECK (TRAN_AMT > 0),
                                       CONSTRAINT CK_MOCK_KFTC_TX_BAL CHECK (AFTER_BALANCE_AMT >= 0)
);
COMMENT ON TABLE MOCK_KFTC_TRANSACTION IS '모의 금융결제원 오픈뱅킹 거래내역 원장';
COMMENT ON COLUMN MOCK_KFTC_TRANSACTION.BANK_TRAN_ID IS '20자리 은행거래고유번호 (이중 출금 방지 멱등성 키)';
COMMENT ON COLUMN MOCK_KFTC_TRANSACTION.INOUT_TYPE IS '입출금 구분 (입금/출금)';
COMMENT ON COLUMN MOCK_KFTC_TRANSACTION.PRINT_CONTENT IS '통장 적요 (인자 내역)';

-- 3. 모의 OAuth 2.0 인가 코드 (1회성, 3분 만료)
CREATE TABLE MOCK_KFTC_OAUTH_CODE (
                                      AUTH_CODE     VARCHAR2(64)  NOT NULL,
                                      CLIENT_ID     VARCHAR2(64)  NOT NULL,
                                      USER_SEQ_NO   VARCHAR2(20)  NOT NULL,
                                      SCOPE         VARCHAR2(100) NOT NULL,
                                      REDIRECT_URI  VARCHAR2(300) NOT NULL,
                                      USED_YN       CHAR(1)       DEFAULT 'N' NOT NULL,
                                      EXPIRES_AT    TIMESTAMP     NOT NULL,
                                      CREATED_AT    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
                                      CONSTRAINT PK_MOCK_KFTC_OAUTH_CODE PRIMARY KEY (AUTH_CODE),
                                      CONSTRAINT CK_MOCK_KFTC_CODE_USED CHECK (USED_YN IN ('Y', 'N'))
);
COMMENT ON TABLE MOCK_KFTC_OAUTH_CODE IS '모의 OAuth 2.0 1회성 인가 코드';

-- 4. 모의 OAuth 2.0 Access Token (SHA-256 해시만 저장)
CREATE TABLE MOCK_KFTC_OAUTH_TOKEN (
                                       TOKEN_HASH    VARCHAR2(64)  NOT NULL,
                                       CLIENT_ID     VARCHAR2(64)  NOT NULL,
                                       USER_SEQ_NO   VARCHAR2(20)  NULL, -- scope=oob일 경우 NULL
                                       SCOPE         VARCHAR2(100) NOT NULL,
                                       REVOKED_YN    CHAR(1)       DEFAULT 'N' NOT NULL,
                                       EXPIRES_AT    TIMESTAMP     NOT NULL,
                                       CREATED_AT    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
                                       CONSTRAINT PK_MOCK_KFTC_OAUTH_TOKEN PRIMARY KEY (TOKEN_HASH),
                                       CONSTRAINT CK_MOCK_KFTC_TOK_REVOKED CHECK (REVOKED_YN IN ('Y', 'N'))
);
COMMENT ON TABLE MOCK_KFTC_OAUTH_TOKEN IS '모의 OAuth 2.0 접근 토큰 (원문 대신 SHA-256 해시 저장)';

CREATE INDEX IDX_MOCK_KFTC_ACC_USER ON MOCK_KFTC_ACCOUNT (USER_SEQ_NO);
CREATE INDEX IDX_MOCK_KFTC_TX_FIN_AT ON MOCK_KFTC_TRANSACTION (FINTECH_USE_NUM, TRAN_AT);
CREATE INDEX IDX_MOCK_KFTC_TOK_USER ON MOCK_KFTC_OAUTH_TOKEN (USER_SEQ_NO);

-- =====================================================================
-- [CODEF 개인 수시입출 실계좌인증 연동 테이블]
-- 회원별 connectedId 및 실계좌 소유 확인 이력 (AES-256-GCM 암호화 저장)
-- =====================================================================

-- 5. CODEF 은행 계정 연결 및 실계좌인증 상태
CREATE TABLE CODEF_CONNECTION (
                                  CONN_ID                 NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                  MEMBER_ID               NUMBER(10)     NOT NULL,
                                  ORGANIZATION            VARCHAR2(10)   NOT NULL, -- CODEF 4자리 기관코드 (예: 0004 국민, 0088 신한)
                                  CONNECTED_ID_ENC        VARCHAR2(300)  NOT NULL, -- AES-256-GCM 암호화된 CODEF connectedId
                                  KEY_VER                 NUMBER(2)      DEFAULT 1 NOT NULL,
                                  VERIFIED_YN             CHAR(1)        DEFAULT 'N' NOT NULL,
                                  VERIFIED_ACCOUNT_ENC    VARCHAR2(300)  NULL,     -- AES-256-GCM 암호화된 인증 계좌번호
                                  VERIFIED_ACCOUNT_LAST4  VARCHAR2(4)    NULL,     -- 마스킹 표시용 뒤 4자리
                                  VERIFIED_HOLDER_NAME    VARCHAR2(50)   NULL,     -- CODEF 실조회 예금주 실명
                                  VERIFIED_AT             TIMESTAMP      NULL,
                                  CREATED_AT              TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                                  UPDATED_AT              TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
                                  CONSTRAINT UK_CODEF_CONN_MEMBER_ORG UNIQUE (MEMBER_ID, ORGANIZATION),
                                  CONSTRAINT CK_CODEF_CONN_VERIFIED CHECK (VERIFIED_YN IN ('Y', 'N'))
);
COMMENT ON TABLE CODEF_CONNECTION IS 'CODEF 개인 은행 connectedId 및 실계좌 인증 이력';
COMMENT ON COLUMN CODEF_CONNECTION.ORGANIZATION IS 'CODEF 4자리 은행 기관코드 (0004 국민, 0088 신한 등)';
COMMENT ON COLUMN CODEF_CONNECTION.CONNECTED_ID_ENC IS 'AES-256-GCM 암호화된 CODEF connectedId';
COMMENT ON COLUMN CODEF_CONNECTION.VERIFIED_YN IS '실계좌 예금주 일치 인증 완료 여부 (Y/N)';

CREATE INDEX IDX_CODEF_CONN_MEMBER ON CODEF_CONNECTION (MEMBER_ID);
