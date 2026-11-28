package com.moava.openbanking.client;

import com.moava.openbanking.dto.KftcAccountDto;
import com.moava.openbanking.dto.KftcOAuthDto;
import com.moava.openbanking.dto.KftcTransactionDto;
import com.moava.openbanking.dto.KftcTransferDto;

/**
 * 금융결제원 오픈뱅킹 v2.0 연동 클라이언트 인터페이스.
 * 구현체(HttpOpenBankingClient)는 application.properties의 moava.openbanking.base-url을 사용하므로
 * 모의은행(localhost) ↔ KFTC 테스트베드 ↔ 상용 서버 전환 시 비즈니스 코드 수정이 필요 없다.
 */
public interface OpenBankingClient {

    /** OAuth 2.0 인가 코드 발급 (POST /oauth/2.0/authorize) */
    KftcOAuthDto.AuthorizeResponse issueAuthorizationCode(KftcOAuthDto.AuthorizeRequest req);

    /** OAuth 2.0 기관 토큰 발급 (grant_type=client_credentials, scope=oob) */
    KftcOAuthDto.TokenResponse issueOobToken();

    /** OAuth 2.0 사용자 토큰 발급 (grant_type=authorization_code) */
    KftcOAuthDto.TokenResponse issueUserToken(String code, String redirectUri);

    /** OAuth 2.0 Access Token 철회 (POST /oauth/2.0/revoke) */
    KftcOAuthDto.RevokeResponse revokeToken(String accessToken);

    /** 계좌 유효성 및 예금주 실명 조회 (POST /v2.0/inquiry/real_name) */
    KftcAccountDto.RealNameResponse inquireRealName(String accessToken, KftcAccountDto.RealNameRequest req);

    /** 잔액 조회 (GET /v2.0/account/balance/fin_num) */
    KftcAccountDto.BalanceResponse inquireBalance(String accessToken, String fintechUseNum, String bankTranId);

    /** 잔액 및 거래내역 조회 (GET /v2.0/account/transaction_list/fin_num) */
    KftcTransactionDto.TransactionListResponse inquireTransactionList(
            String accessToken,
            String fintechUseNum,
            String inquiryType,
            String fromDate,
            String toDate,
            String sortOrder,
            Integer limit,
            String bankTranId
    );

    /** 출금이체 (POST /v2.0/transfer/withdraw/fin_num) */
    KftcTransferDto.TransferResponse withdrawTransfer(String accessToken, KftcTransferDto.WithdrawRequest req);

    /** 입금이체 (POST /v2.0/transfer/deposit/fin_num) */
    KftcTransferDto.TransferResponse depositTransfer(String accessToken, KftcTransferDto.DepositRequest req);
}
