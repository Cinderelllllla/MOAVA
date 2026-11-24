-- ============================================================
-- MOAVA DB Migration 05
-- F16 / F18 모임 가입 및 인증 정책 변경
--
-- 작성일: 2026-11-25
--
-- 변경 사항
-- 1. 가입 신청 7일 자동 만료 상태 추가
-- 2. 인증코드 실패 허용 횟수 5회 -> 3회
-- 3. 시간 기반 잠금 정책 제거
-- 4. 기존 인증 시도 데이터 보정
--
-- 주의:
-- 기존 테이블 및 데이터 삭제 금지
-- 백엔드 구버전과 동시 사용 금지
-- 공용 DB 적용 전 팀원 협의 필수
-- ============================================================


-- ============================================================
-- 1. 적용 전 데이터 및 제약조건 확인
-- ============================================================

SELECT
    TABLE_NAME,
    CONSTRAINT_NAME,
    SEARCH_CONDITION,
    STATUS,
    VALIDATED
FROM USER_CONSTRAINTS
WHERE CONSTRAINT_NAME IN (
    'CK_JOIN_CODE_ATTEMPT_1',
    'CK_JOIN_REQUEST_2'
);

SELECT
    FAIL_COUNT,
    COUNT(*) AS ROW_COUNT
FROM JOIN_CODE_ATTEMPT
GROUP BY FAIL_COUNT
ORDER BY FAIL_COUNT;

SELECT
    STATUS,
    COUNT(*) AS ROW_COUNT
FROM JOIN_REQUEST
GROUP BY STATUS;


-- ============================================================
-- 2. F18 - 기존 인증 시도 데이터 보정
-- ============================================================

-- 기존 4~5회 실패 회원은 3회 실패 상태로 변경
-- 기존 잠금 정보가 남아 있는 회원도 잠금 상태 유지
-- 인증 성공 여부 및 인증 시각은 삭제하지 않음

UPDATE JOIN_CODE_ATTEMPT
SET FAIL_COUNT = 3
WHERE FAIL_COUNT > 3
   OR LOCKED_UNTIL > SYSDATE;


-- 시간 기반 잠금 정책 제거
-- LOCKED_UNTIL 컬럼 자체는 호환성을 위해 유지

UPDATE JOIN_CODE_ATTEMPT
SET LOCKED_UNTIL = NULL
WHERE LOCKED_UNTIL IS NOT NULL;

COMMIT;


-- ============================================================
-- 3. F18 - 인증 실패 횟수 제약조건 변경
-- ============================================================

-- 신규 제약조건을 먼저 추가하여
-- 제약조건이 없는 상태를 방지한다.

ALTER TABLE JOIN_CODE_ATTEMPT
ADD CONSTRAINT CK_JCA_FAIL_COUNT_V2
CHECK (FAIL_COUNT BETWEEN 0 AND 3)
ENABLE VALIDATE;


-- 기존 0~5회 제약조건 제거

ALTER TABLE JOIN_CODE_ATTEMPT
DROP CONSTRAINT CK_JOIN_CODE_ATTEMPT_1;


-- 기존 DDL과 동일한 제약조건 이름 유지

ALTER TABLE JOIN_CODE_ATTEMPT
RENAME CONSTRAINT CK_JCA_FAIL_COUNT_V2
TO CK_JOIN_CODE_ATTEMPT_1;


-- ============================================================
-- 4. F18 - 컬럼 설명 변경
-- ============================================================

COMMENT ON COLUMN JOIN_CODE_ATTEMPT.CODE_VERSION IS
'시도 당시 JOIN_CODE_VERSION. 재발급 시 실패 횟수 및 인증 상태 초기화';

COMMENT ON COLUMN JOIN_CODE_ATTEMPT.FAIL_COUNT IS
'회원별 인증 실패 횟수. 3회 도달 시 코드 재발급 전까지 인증 차단';

COMMENT ON COLUMN JOIN_CODE_ATTEMPT.LOCKED_UNTIL IS
'기존 시간 기반 잠금 정책 호환용 컬럼. 현재 잠금 판단에는 사용하지 않음';


-- ============================================================
-- 5. F16 - 가입 신청 자동 만료 상태 추가
-- ============================================================

-- 신규 상태 제약조건 추가

ALTER TABLE JOIN_REQUEST
ADD CONSTRAINT CK_JR_STATUS_V2
CHECK (
    STATUS IN (
        'PENDING',
        'APPROVED',
        'REJECTED',
        'EXPIRED'
    )
)
ENABLE VALIDATE;


-- 기존 상태 제약조건 제거

ALTER TABLE JOIN_REQUEST
DROP CONSTRAINT CK_JOIN_REQUEST_2;


-- 기존 DDL과 동일한 제약조건 이름 유지

ALTER TABLE JOIN_REQUEST
RENAME CONSTRAINT CK_JR_STATUS_V2
TO CK_JOIN_REQUEST_2;


-- ============================================================
-- 6. F16 - 테이블 및 컬럼 설명 변경
-- ============================================================

COMMENT ON TABLE JOIN_REQUEST IS
'가입 신청 및 처리 이력. 신청 후 7일 동안 미처리 시 자동 만료';

COMMENT ON COLUMN JOIN_REQUEST.VIA IS
'가입 신청 경로: SEARCH(검색), INVITE(초대 링크 및 인증코드)';

COMMENT ON COLUMN JOIN_REQUEST.STATUS IS
'PENDING(대기), APPROVED(승인), REJECTED(거절), EXPIRED(자동 만료)';

COMMENT ON COLUMN JOIN_REQUEST.DECIDED_BY IS
'승인 또는 거절 처리 회원 ID. 시스템 자동 만료 시 NULL';

COMMENT ON COLUMN JOIN_REQUEST.DECIDED_AT IS
'승인, 거절 또는 자동 만료 처리 시각';


-- ============================================================
-- 7. 기존 만료 대상 가입 신청 확인
-- ============================================================

-- 여기서 직접 EXPIRED로 변경하지 않는다.
-- Spring 자동 만료 서비스를 통해 상태 변경 및
-- 신청자 알림 발송을 함께 처리한다.

SELECT
    JOIN_REQUEST_ID,
    GROUP_ID,
    MEMBER_ID,
    STATUS,
    CREATED_AT
FROM JOIN_REQUEST
WHERE STATUS = 'PENDING'
  AND CREATED_AT <= SYSDATE - 7
ORDER BY CREATED_AT ASC;


-- ============================================================
-- 8. 마이그레이션 결과 검증
-- ============================================================

-- 변경된 제약조건 확인

SELECT
    TABLE_NAME,
    CONSTRAINT_NAME,
    SEARCH_CONDITION,
    STATUS,
    VALIDATED
FROM USER_CONSTRAINTS
WHERE CONSTRAINT_NAME IN (
    'CK_JOIN_CODE_ATTEMPT_1',
    'CK_JOIN_REQUEST_2'
);


-- 실패 횟수 범위 검증
-- 기대 결과: 0건

SELECT COUNT(*) AS INVALID_FAIL_COUNT
FROM JOIN_CODE_ATTEMPT
WHERE FAIL_COUNT NOT BETWEEN 0 AND 3;


-- 기존 시간 기반 잠금 제거 검증
-- 기대 결과: 0건

SELECT COUNT(*) AS REMAINING_TIME_LOCK_COUNT
FROM JOIN_CODE_ATTEMPT
WHERE LOCKED_UNTIL IS NOT NULL;


-- 가입 신청 상태 검증
-- 기대 결과: 0건

SELECT COUNT(*) AS INVALID_JOIN_STATUS_COUNT
FROM JOIN_REQUEST
WHERE STATUS NOT IN (
    'PENDING',
    'APPROVED',
    'REJECTED',
    'EXPIRED'
);

COMMIT;

-- ============================================================
-- END OF MIGRATION
-- ============================================================
