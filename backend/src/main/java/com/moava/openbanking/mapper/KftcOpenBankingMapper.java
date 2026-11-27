package com.moava.openbanking.mapper;

import com.moava.openbanking.dto.KftcRecordDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface KftcOpenBankingMapper {

    // ===== OAuth 2.0 =====
    int insertAuthCode(KftcRecordDto.OAuthCodeRecord record);

    KftcRecordDto.OAuthCodeRecord findAuthCode(@Param("authCode") String authCode);

    int consumeAuthCode(@Param("authCode") String authCode, @Param("now") LocalDateTime now);

    int insertOAuthToken(KftcRecordDto.OAuthTokenRecord record);

    KftcRecordDto.OAuthTokenRecord findOAuthTokenByHash(@Param("tokenHash") String tokenHash);

    int revokeOAuthToken(@Param("tokenHash") String tokenHash);

    int revokeOAuthTokensByUserSeqNo(@Param("userSeqNo") String userSeqNo);

    // ===== 가상 원장 계좌 (MOCK_KFTC_ACCOUNT) =====
    KftcRecordDto.AccountRecord findAccountByBankAndNo(
            @Param("bankCode") String bankCode,
            @Param("accountNo") String accountNo
    );

    KftcRecordDto.AccountRecord findAccountByFintechUseNum(@Param("fintechUseNum") String fintechUseNum);

    KftcRecordDto.AccountRecord findAccountForUpdate(@Param("fintechUseNum") String fintechUseNum);

    int debitAccountBalance(
            @Param("fintechUseNum") String fintechUseNum,
            @Param("amount") long amount,
            @Param("now") LocalDateTime now
    );

    int creditAccountBalance(
            @Param("fintechUseNum") String fintechUseNum,
            @Param("amount") long amount,
            @Param("now") LocalDateTime now
    );

    // ===== 가상 원장 거래내역 (MOCK_KFTC_TRANSACTION) =====
    int countByBankTranId(@Param("bankTranId") String bankTranId);

    int insertTransaction(KftcRecordDto.TransactionRecord record);

    List<KftcRecordDto.TransactionRecord> findTransactions(
            @Param("fintechUseNum") String fintechUseNum,
            @Param("inoutType") String inoutType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("sortOrder") String sortOrder,
            @Param("limit") int limit
    );
}
