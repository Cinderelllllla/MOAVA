package com.moava.openbanking.mapper;

import com.moava.openbanking.dto.KftcRecordDto;
import com.moava.openbanking.dto.MockBankDto;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모의 은행 원장(MOCK_KFTC_ACCOUNT / MOCK_KFTC_TRANSACTION) 적재 및 프론트 조회 전용 MyBatis 매퍼.
 * 기존 KftcOpenBankingMapper / XML 계약을 건드리지 않고 독립 운영한다.
 */
@Mapper
public interface MockBankLedgerMapper {

    @Select("""
            SELECT
                FINTECH_USE_NUM AS fintechUseNum,
                BANK_CODE       AS bankCode,
                BANK_NAME       AS bankName,
                ACCOUNT_NO      AS accountNo,
                HOLDER_NAME     AS holderName,
                USER_SEQ_NO     AS userSeqNo,
                BALANCE         AS balance,
                LEADER_YN       AS leaderYn,
                STATUS          AS status,
                CREATED_AT      AS createdAt,
                UPDATED_AT      AS updatedAt
            FROM MOCK_KFTC_ACCOUNT
            WHERE USER_SEQ_NO = #{userSeqNo}
              AND STATUS <> 'CLOSED'
            ORDER BY CASE WHEN LEADER_YN = 'Y' THEN 0 ELSE 1 END,
                     UPDATED_AT DESC,
                     CREATED_AT DESC
            """)
    List<KftcRecordDto.AccountRecord> findAccountsByUserSeqNo(@Param("userSeqNo") String userSeqNo);

    @Select("""
            SELECT
                FINTECH_USE_NUM AS fintechUseNum,
                BANK_CODE       AS bankCode,
                BANK_NAME       AS bankName,
                ACCOUNT_NO      AS accountNo,
                HOLDER_NAME     AS holderName,
                USER_SEQ_NO     AS userSeqNo,
                BALANCE         AS balance,
                LEADER_YN       AS leaderYn,
                STATUS          AS status,
                CREATED_AT      AS createdAt,
                UPDATED_AT      AS updatedAt
            FROM MOCK_KFTC_ACCOUNT
            WHERE BANK_CODE = #{bankCode}
              AND REPLACE(ACCOUNT_NO, '-', '') = #{accountDigits}
            """)
    KftcRecordDto.AccountRecord findAccountByBankAndDigits(
            @Param("bankCode") String bankCode,
            @Param("accountDigits") String accountDigits
    );

    @Select("""
            SELECT COUNT(*)
            FROM MOCK_KFTC_ACCOUNT
            WHERE FINTECH_USE_NUM = #{fintechUseNum}
            """)
    int countByFintechUseNum(@Param("fintechUseNum") String fintechUseNum);

    @Insert("""
            INSERT INTO MOCK_KFTC_ACCOUNT (
                FINTECH_USE_NUM, BANK_CODE, BANK_NAME, ACCOUNT_NO, HOLDER_NAME,
                USER_SEQ_NO, BALANCE, LEADER_YN, STATUS, CREATED_AT, UPDATED_AT
            ) VALUES (
                #{fintechUseNum}, #{bankCode}, #{bankName}, #{accountNo}, #{holderName},
                #{userSeqNo}, #{balance}, #{leaderYn}, #{status},
                #{createdAt, jdbcType=TIMESTAMP}, #{updatedAt, jdbcType=TIMESTAMP}
            )
            """)
    int insertMockAccount(KftcRecordDto.AccountRecord record);

    @Update("""
            UPDATE MOCK_KFTC_ACCOUNT
            SET HOLDER_NAME = #{holderName},
                USER_SEQ_NO = #{userSeqNo},
                BALANCE     = #{balance},
                LEADER_YN   = #{leaderYn},
                STATUS      = CASE WHEN STATUS = 'FROZEN' THEN 'FROZEN' ELSE 'ACTIVE' END,
                UPDATED_AT  = #{updatedAt, jdbcType=TIMESTAMP}
            WHERE FINTECH_USE_NUM = #{fintechUseNum}
            """)
    int updateMockAccountFromSync(
            @Param("fintechUseNum") String fintechUseNum,
            @Param("holderName") String holderName,
            @Param("userSeqNo") String userSeqNo,
            @Param("balance") Long balance,
            @Param("leaderYn") String leaderYn,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Update("""
            UPDATE MOCK_KFTC_ACCOUNT
            SET LEADER_YN  = #{leaderYn},
                UPDATED_AT = #{updatedAt, jdbcType=TIMESTAMP}
            WHERE FINTECH_USE_NUM = #{fintechUseNum}
              AND USER_SEQ_NO = #{userSeqNo}
              AND STATUS = 'ACTIVE'
            """)
    int updateAccountLeaderYn(
            @Param("fintechUseNum") String fintechUseNum,
            @Param("userSeqNo") String userSeqNo,
            @Param("leaderYn") String leaderYn,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Select("""
            SELECT COUNT(*)
            FROM MOCK_KFTC_TRANSACTION
            WHERE FINTECH_USE_NUM = #{fintechUseNum}
            """)
    int countTransactionsByFintechUseNum(@Param("fintechUseNum") String fintechUseNum);

    @Select("""
            SELECT
                t.TX_ID                AS txId,
                t.BANK_TRAN_ID         AS bankTranId,
                t.FINTECH_USE_NUM      AS fintechUseNum,
                t.INOUT_TYPE           AS inoutType,
                t.TRAN_AMT             AS tranAmt,
                t.AFTER_BALANCE_AMT    AS afterBalanceAmt,
                t.PRINT_CONTENT        AS printContent,
                t.COUNTERPARTY_NAME    AS counterpartyName,
                t.COUNTERPARTY_FIN_NUM AS counterpartyFinNum,
                t.BRANCH_NAME          AS branchName,
                t.TRAN_AT              AS tranAt
            FROM MOCK_KFTC_TRANSACTION t
            INNER JOIN MOCK_KFTC_ACCOUNT a
              ON a.FINTECH_USE_NUM = t.FINTECH_USE_NUM
            WHERE a.USER_SEQ_NO = #{userSeqNo}
              AND a.STATUS <> 'CLOSED'
            ORDER BY t.TRAN_AT DESC, t.TX_ID DESC
            FETCH FIRST #{limit} ROWS ONLY
            """)
    List<KftcRecordDto.TransactionRecord> findRecentTransactionsByUserSeqNo(
            @Param("userSeqNo") String userSeqNo,
            @Param("limit") int limit
    );

    @Select("""
            SELECT
                FINTECH_USE_NUM AS fintechUseNum,
                BANK_CODE       AS bankCode,
                BANK_NAME       AS bankName,
                ACCOUNT_NO      AS accountNo,
                HOLDER_NAME     AS holderName,
                USER_SEQ_NO     AS userSeqNo,
                BALANCE         AS balance,
                LEADER_YN       AS leaderYn,
                STATUS          AS status,
                CREATED_AT      AS createdAt,
                UPDATED_AT      AS updatedAt
            FROM MOCK_KFTC_ACCOUNT
            WHERE BANK_CODE = #{bankCode}
              AND LEADER_YN = 'Y'
              AND STATUS = 'ACTIVE'
              AND SUBSTR(REPLACE(ACCOUNT_NO, '-', ''), -4) = #{last4}
            ORDER BY UPDATED_AT DESC
            """)
    List<KftcRecordDto.AccountRecord> findActiveLeaderAccountsByBankAndLast4(
            @Param("bankCode") String bankCode,
            @Param("last4") String last4
    );

    @Select("""
            SELECT COUNT(*)
            FROM GYE_GROUP
            WHERE OWNER_ID = #{memberId}
              AND STATUS IN ('RECRUITING', 'ACTIVE')
            """)
    int countOwnedActiveGroups(@Param("memberId") Long memberId);

    @Update("""
            UPDATE MOCK_KFTC_ACCOUNT
            SET STATUS     = 'CLOSED',
                UPDATED_AT = #{updatedAt, jdbcType=TIMESTAMP}
            WHERE USER_SEQ_NO = #{userSeqNo}
              AND BANK_CODE = #{bankCode}
              AND STATUS <> 'CLOSED'
            """)
    int closeAccountsByUserAndBank(
            @Param("userSeqNo") String userSeqNo,
            @Param("bankCode") String bankCode,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    @Select("""
            SELECT COUNT(*)
            FROM MOCK_KFTC_ACCOUNT
            WHERE USER_SEQ_NO = #{userSeqNo}
              AND STATUS = 'ACTIVE'
            """)
    int countActiveAccountsByUserSeqNo(@Param("userSeqNo") String userSeqNo);

    @Select("""
            SELECT
                TO_CHAR(t.TRAN_AT, 'YYYYMM') AS yearMonth,
                NVL(SUM(CASE WHEN t.INOUT_TYPE = '입금' THEN t.TRAN_AMT ELSE 0 END), 0) AS totalDepositAmt,
                NVL(SUM(CASE WHEN t.INOUT_TYPE = '출금' THEN t.TRAN_AMT ELSE 0 END), 0) AS totalWithdrawAmt,
                COUNT(*) AS txCount
            FROM MOCK_KFTC_TRANSACTION t
            INNER JOIN MOCK_KFTC_ACCOUNT a
              ON a.FINTECH_USE_NUM = t.FINTECH_USE_NUM
            WHERE a.USER_SEQ_NO = #{userSeqNo}
              AND a.STATUS <> 'CLOSED'
            GROUP BY TO_CHAR(t.TRAN_AT, 'YYYYMM')
            ORDER BY TO_CHAR(t.TRAN_AT, 'YYYYMM') ASC
            """)
    List<MockBankDto.MonthlyAggregate> findMonthlySummaryByUserSeqNo(@Param("userSeqNo") String userSeqNo);

    @Select("""
            SELECT
                g.GROUP_ID AS groupId,
                g.TITLE    AS title,
                g.OWNER_ID AS ownerId,
                g.AMOUNT   AS amount,
                g.PAY_DAY  AS payDay,
                g.STATUS   AS status,
                ga.BANK_NAME              AS accountBankName,
                TRIM(ga.ACCOUNT_NO_LAST4) AS accountLast4,
                TRIM(ga.ACCOUNT_NO_HASH)  AS accountNoHash,
                ga.VERIFY_STATUS          AS accountVerifyStatus
            FROM GYE_GROUP g
            INNER JOIN GYE_MEMBER gm
              ON gm.GROUP_ID = g.GROUP_ID
            LEFT JOIN GYE_ACCOUNT ga
              ON ga.GROUP_ID = g.GROUP_ID
             AND ga.ACCOUNT_STATUS = 'CURRENT'
            WHERE gm.MEMBER_ID = #{memberId}
              AND gm.STATUS = 'ACTIVE'
              AND g.STATUS IN ('RECRUITING', 'ACTIVE')
            ORDER BY g.CREATED_AT DESC
            """)
    List<MockBankDto.MemberGroupInfo> findMemberActiveGroups(@Param("memberId") Long memberId);
}
