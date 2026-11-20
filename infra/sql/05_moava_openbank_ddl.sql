-- 1. 모의 은행: 사용자 인증서 (공개키만 저장)
CREATE TABLE MOCK_CERTIFICATES (
                                   CERT_ID        NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                   USER_ID        NUMBER        NOT NULL,
                                   SERIAL_NO      VARCHAR2(64)  NOT NULL UNIQUE,
                                   PUBLIC_KEY     CLOB          NOT NULL, -- SPKI Base64
                                   STATUS         VARCHAR2(20)  DEFAULT 'ACTIVE' NOT NULL, -- ACTIVE, REVOKED
                                   ISSUED_AT      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
                                   EXPIRES_AT     TIMESTAMP     NOT NULL
);

-- 2. 모의 은행: 1회용 서명 Challenge (재전송 공격 방지)
CREATE TABLE MOCK_CHALLENGES (
                                 NONCE          VARCHAR2(64)  PRIMARY KEY,
                                 USER_ID        NUMBER        NOT NULL,
                                 ACCOUNT_NO     VARCHAR2(30)  NOT NULL,
                                 SCOPE          VARCHAR2(30)  NOT NULL, -- CREDIT_ONCE, GROUP_LEDGER_READ
                                 CONSENT_TEXT   VARCHAR2(500) NOT NULL,
                                 USED_YN        CHAR(1)       DEFAULT 'N' NOT NULL,
                                 EXPIRES_AT     TIMESTAMP     NOT NULL,
                                 CONSTRAINT CK_MOCK_CHALLENGE_SCOPE CHECK (SCOPE IN ('CREDIT_ONCE', 'GROUP_LEDGER_READ')),
                                 CONSTRAINT CK_MOCK_CHALLENGE_USED CHECK (USED_YN IN ('Y', 'N'))
);

-- 3. 모의 은행: 용도별 접근 토큰 (핵심 제어 테이블)
CREATE TABLE MOCK_CONSENT_TOKENS (
                                     TOKEN_ID        VARCHAR2(64)  PRIMARY KEY,
                                     USER_ID         NUMBER        NOT NULL,
                                     ACCOUNT_NO      VARCHAR2(30)  NOT NULL,
                                     SCOPE           VARCHAR2(30)  NOT NULL, -- CREDIT_ONCE, GROUP_LEDGER_READ
                                     VALID_FROM      TIMESTAMP     NULL,     -- 모임장 계좌일 때만 세팅 (활동 시작 시점)
                                     USED_YN         CHAR(1)       DEFAULT 'N' NOT NULL, -- CREDIT_ONCE 소진 여부
                                     SIGNATURE_HASH  VARCHAR2(128) NOT NULL, -- 감사 추적용 전자서명 해시
                                     CREATED_AT      TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
                                     EXPIRES_AT      TIMESTAMP     NOT NULL,
                                     CONSTRAINT CK_MOCK_TOKEN_SCOPE CHECK (SCOPE IN ('CREDIT_ONCE', 'GROUP_LEDGER_READ')),
                                     CONSTRAINT CK_MOCK_TOKEN_USED CHECK (USED_YN IN ('Y', 'N'))
);

-- 4. 모의 은행: 원장 거래내역 (더미 데이터 적재용)
CREATE TABLE MOCK_TRANSACTIONS (
                                   BANK_TX_ID     VARCHAR2(64)  PRIMARY KEY,
                                   ACCOUNT_NO     VARCHAR2(30)  NOT NULL,
                                   TX_TYPE        VARCHAR2(10)  NOT NULL, -- DEPOSIT, WITHDRAW
                                   AMOUNT         NUMBER(15)    NOT NULL,
                                   BALANCE_AFTER  NUMBER(15)    NOT NULL,
                                   COUNTERPARTY   VARCHAR2(100) NOT NULL,
                                   TX_AT          TIMESTAMP     NOT NULL,
                                   CONSTRAINT CK_MOCK_TX_TYPE CHECK (TX_TYPE IN ('DEPOSIT', 'WITHDRAW')),
                                   CONSTRAINT CK_MOCK_TX_AMOUNT CHECK (AMOUNT > 0),
                                   CONSTRAINT CK_MOCK_TX_BALANCE CHECK (BALANCE_AFTER >= 0)
);

-- 5. 서비스 DB: 모임장 계좌 연동 및 증분 동기화 관리
CREATE TABLE GROUP_ACCOUNT_SYNC (
                                    GROUP_ID            NUMBER       PRIMARY KEY,
                                    LEADER_ACCOUNT_NO   VARCHAR2(30) NOT NULL,
                                    CONSENT_TOKEN       VARCHAR2(64) NOT NULL,
                                    ACTIVITY_STARTED_AT TIMESTAMP    NOT NULL,
                                    LAST_SYNCED_AT      TIMESTAMP    NULL,
                                    OPENING_BALANCE     NUMBER(15)   DEFAULT 0 NOT NULL
);

-- 6. 서비스 DB: 모임 장부 (모임 활동 시작 이후 거래내역만 저장)
CREATE TABLE GROUP_LEDGER (
                              LEDGER_ID      NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                              GROUP_ID       NUMBER        NOT NULL,
                              BANK_TX_ID     VARCHAR2(64)  NOT NULL,
                              TX_TYPE        VARCHAR2(10)  NOT NULL,
                              AMOUNT         NUMBER(15)    NOT NULL,
                              COUNTERPARTY   VARCHAR2(100) NOT NULL,
                              TX_AT          TIMESTAMP     NOT NULL,
                               CONSTRAINT UK_GROUP_LEDGER_TX UNIQUE (GROUP_ID, BANK_TX_ID), -- 중복 적재 방지
                               CONSTRAINT CK_GROUP_LEDGER_TYPE CHECK (TX_TYPE IN ('DEPOSIT', 'WITHDRAW')),
                               CONSTRAINT CK_GROUP_LEDGER_AMOUNT CHECK (AMOUNT > 0)
);

CREATE INDEX IDX_MOCK_CHALLENGES_USER ON MOCK_CHALLENGES (USER_ID);
CREATE INDEX IDX_MOCK_TOKENS_USER ON MOCK_CONSENT_TOKENS (USER_ID);
CREATE INDEX IDX_MOCK_TOKENS_ACCOUNT ON MOCK_CONSENT_TOKENS (ACCOUNT_NO);
CREATE INDEX IDX_MOCK_TX_ACCOUNT_AT ON MOCK_TRANSACTIONS (ACCOUNT_NO, TX_AT);