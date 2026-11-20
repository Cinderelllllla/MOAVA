-- Existing Oracle databases: distinguish an unanswered order consent from an explicit rejection.
-- Run once after deploying the updated application and before resuming payout-order workflows.

ALTER TABLE CONSENT DROP CONSTRAINT CK_CONSENT_2;

ALTER TABLE CONSENT MODIFY (AGREED_AT NULL);
ALTER TABLE CONSENT MODIFY (AGREED_AT DEFAULT NULL);

-- Initial N rows in an active consent round were created by the old invitation flow.
-- Rejected/confirmed requests are deliberately left unchanged to preserve their history.
UPDATE CONSENT c
SET AGREED_YN = 'P',
    AGREED_AT = NULL
WHERE c.CONSENT_TYPE = 'ORDER'
  AND c.AGREED_YN = 'N'
  AND EXISTS (
      SELECT 1
      FROM PAYOUT_REQUEST pr
      WHERE pr.PAYOUT_REQUEST_ID = c.TARGET_ID
        AND pr.GROUP_ID = c.GROUP_ID
        AND pr.STATUS = 'CONSENT_PENDING'
  );

ALTER TABLE CONSENT
    ADD CONSTRAINT CK_CONSENT_2 CHECK (
        (CONSENT_TYPE = 'ORDER' AND AGREED_YN IN ('Y','N','P'))
        OR (CONSENT_TYPE IN ('SETTLEMENT','ACCOUNT','TRANSFER') AND AGREED_YN IN ('Y','N'))
    );

COMMENT ON COLUMN CONSENT.AGREED_YN IS 'P 미응답 / Y 동의 / N 거부';
COMMENT ON COLUMN CONSENT.AGREED_AT IS '실제 동의·거부 응답 시각. 미응답은 NULL';

COMMIT;
