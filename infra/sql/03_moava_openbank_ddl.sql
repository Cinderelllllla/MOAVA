-- =====================================================================
-- 1. 가상 오픈뱅킹 계좌 원장 테이블 (CODEF 연동 적재 및 잔액 관리용)
-- =====================================================================
CREATE SEQUENCE SEQ_MOCK_BANK_ACCOUNT START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE SEQ_MOCK_BANK_TX START WITH 1 INCREMENT BY 1 NOCACHE;

-- 만약 PAYMENT_PROOF 시퀀스가 아직 없다면 생성 (이미 있다면 생략 가능)
-- CREATE SEQUENCE SEQ_PAYMENT_PROOF START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE TABLE MOCK_BANK_ACCOUNT (
                                   MOCK_ACCOUNT_ID   NUMBER(10)     NOT NULL,
                                   MEMBER_ID         NUMBER(10),                        -- MOAVA 회원 ID (모임원/모임장 매핑용)
                                   FINTECH_USE_NUM   VARCHAR2(30)   NOT NULL,           -- 금결원 표준 핀테크이용번호 (24자리)
                                   BANK_CODE         VARCHAR2(10)   NOT NULL,           -- 은행 표준코드 (예: 004 국민, 088 신한, 090 카카오)
                                   BANK_NAME         VARCHAR2(50)   NOT NULL,           -- 은행명 (GYE_ACCOUNT.BANK_NAME과 매칭)
                                   ACCOUNT_NO        VARCHAR2(30)   NOT NULL,           -- 하이픈 제외한 평문 계좌번호 (가상 은행 원장용)
                                   HOLDER_NAME       VARCHAR2(50)   NOT NULL,           -- 예금주 실명 (실명 인증 대조용)
                                   BALANCE_AMT       NUMBER(15)     DEFAULT 1000000 NOT NULL, -- 현재 잔액 (토이 프로젝트에서 자유 수정 가능)
                                   SOURCE_TYPE       VARCHAR2(20)   DEFAULT 'CODEF' NOT NULL, -- 데이터 출처 (CODEF / MANUAL)
                                   ACCOUNT_STATE     VARCHAR2(10)   DEFAULT 'ACTIVE' NOT NULL,-- 계좌 상태 (ACTIVE / CLOSED)
                                   CREATED_AT        DATE           DEFAULT SYSDATE NOT NULL,
                                   UPDATED_AT        DATE           DEFAULT SYSDATE NOT NULL,
                                   CONSTRAINT PK_MOCK_BANK_ACCOUNT PRIMARY KEY (MOCK_ACCOUNT_ID),
                                   CONSTRAINT UK_MOCK_BANK_FINTECH UNIQUE (FINTECH_USE_NUM),
                                   CONSTRAINT UK_MOCK_BANK_ACCT_NO UNIQUE (BANK_NAME, ACCOUNT_NO),
                                   CONSTRAINT CK_MOCK_BANK_STATE CHECK (ACCOUNT_STATE IN ('ACTIVE', 'CLOSED'))
);

COMMENT ON TABLE MOCK_BANK_ACCOUNT IS '가상 오픈뱅킹 은행 계좌 원장 (CODEF 계좌 적재 및 잔액 관리)';
COMMENT ON COLUMN MOCK_BANK_ACCOUNT.BALANCE_AMT IS '가상 계좌 잔액 (시연 및 테스트 시 자유 변경 가능)';

-- =====================================================================
-- 2. 가상 오픈뱅킹 거래내역 테이블 (실시간 입출금 원장 기록)
-- =====================================================================
CREATE TABLE MOCK_BANK_TRANSACTION (
                                       TRAN_ID            NUMBER(12)     NOT NULL,
                                       BANK_TRAN_ID       VARCHAR2(40)   NOT NULL,          -- 오픈뱅킹 고유 거래고유번호 (예: M202601116U000000001)
                                       FINTECH_USE_NUM    VARCHAR2(30)   NOT NULL,          -- 대상 계좌 핀테크이용번호
                                       INOUT_TYPE         VARCHAR2(10)   NOT NULL,          -- 'WITHDRAW'(출금) / 'DEPOSIT'(입금)
                                       TRAN_AMT           NUMBER(12)     NOT NULL,          -- 이체 금액 (원)
                                       AFTER_BALANCE_AMT  NUMBER(15)     NOT NULL,          -- 거래 직후 잔액 (원)
                                       COUNTERPARTY_NAME  VARCHAR2(50)   NOT NULL,          -- 상대방(입금자/수취인) 실명
                                       COUNTERPARTY_ACCT  VARCHAR2(30)   NOT NULL,          -- 상대방 계좌번호
                                       PRINT_CONTENT      VARCHAR2(100)  NOT NULL,          -- 통장 인자 내역 (예: 이모아_202611_1회차)
                                       GROUP_ID           NUMBER(10),                       -- 연관 모임 ID
                                       PAYMENT_PROOF_ID   NUMBER(10),                       -- 연결된 납입증빙 ID (이중 확인용)
                                       TRAN_DATE          CHAR(8)        DEFAULT TO_CHAR(SYSDATE, 'YYYYMMDD') NOT NULL,
                                       TRAN_TIME          CHAR(6)        DEFAULT TO_CHAR(SYSDATE, 'HH24MISS') NOT NULL,
                                       CREATED_AT         DATE           DEFAULT SYSDATE NOT NULL,
                                       CONSTRAINT PK_MOCK_BANK_TRANSACTION PRIMARY KEY (TRAN_ID),
                                       CONSTRAINT FK_MOCK_TX_FINTECH FOREIGN KEY (FINTECH_USE_NUM) REFERENCES MOCK_BANK_ACCOUNT (FINTECH_USE_NUM),
                                       CONSTRAINT CK_MOCK_TX_INOUT CHECK (INOUT_TYPE IN ('WITHDRAW', 'DEPOSIT'))
);

COMMENT ON TABLE MOCK_BANK_TRANSACTION IS '가상 오픈뱅킹 입출금 거래내역 원장';

-- =====================================================================
-- 3. 시연용 초기 더미 데이터 (일반 모임원 1명 + 모임장 1명)
-- =====================================================================
-- ① 일반 모임원 '이모아' 계좌 (잔액 3,000,000원)
INSERT INTO MOCK_BANK_ACCOUNT (
    MOCK_ACCOUNT_ID, MEMBER_ID, FINTECH_USE_NUM, BANK_CODE, BANK_NAME,
    ACCOUNT_NO, HOLDER_NAME, BALANCE_AMT, SOURCE_TYPE
) VALUES (
             SEQ_MOCK_BANK_ACCOUNT.NEXTVAL, 1, '199004000000000000000001', '004', '국민은행',
             '110123456789', '이모아', 3000000, 'CODEF'
         );

-- ② 모임장 '김모장'의 모임 전용 계좌 (초기 잔액 0원 - GYE_ACCOUNT에 암호화되어 등록된 계좌와 동일한 번호로 세팅)
INSERT INTO MOCK_BANK_ACCOUNT (
    MOCK_ACCOUNT_ID, MEMBER_ID, FINTECH_USE_NUM, BANK_CODE, BANK_NAME,
    ACCOUNT_NO, HOLDER_NAME, BALANCE_AMT, SOURCE_TYPE
) VALUES (
             SEQ_MOCK_BANK_ACCOUNT.NEXTVAL, 2, '199088000000000000000002', '088', '신한은행',
             '110999888777', '김모장', 0, 'CODEF'
         );

COMMIT;