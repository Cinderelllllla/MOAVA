package com.moava.mockbank;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MockBankMapper {

    int revokeActiveCertificatesByUserId(@Param("userId") Long userId);

    int insertCertificate(MockBankDto.CertificateRecord record);

    MockBankDto.CertificateRecord findActiveCertificateBySerialNo(@Param("serialNo") String serialNo);

    int insertChallenge(MockBankDto.ChallengeRecord record);

    MockBankDto.ChallengeRecord findChallengeByNonce(@Param("nonce") String nonce);

    int markChallengeUsed(@Param("nonce") String nonce);

    int insertConsentToken(MockBankDto.ConsentTokenRecord record);

    MockBankDto.ConsentTokenRecord findConsentTokenById(@Param("tokenId") String tokenId);

    int markTokenUsed(@Param("tokenId") String tokenId);

    List<MockBankDto.TransactionDto> findTransactions(
            @Param("accountNo") String accountNo,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}