package com.moava.openbanking.controller;

import com.moava.openbanking.dto.KftcAccountDto;
import com.moava.openbanking.dto.KftcRecordDto;
import com.moava.openbanking.dto.KftcTransactionDto;
import com.moava.openbanking.dto.KftcTransferDto;
import com.moava.openbanking.service.KftcOAuthService;
import com.moava.openbanking.service.KftcOpenBankingService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 금융결제원 오픈뱅킹 v2.0 모의 원장 API 컨트롤러
 */
@RestController
@RequestMapping("/v2.0")
@RequiredArgsConstructor
public class KftcOpenBankingController {

    private final KftcOAuthService oauthService;
    private final KftcOpenBankingService openBankingService;

    // 1. 계좌 유효성 및 예금주 실명 조회
    @PostMapping("/inquiry/real_name")
    public ResponseEntity<KftcAccountDto.RealNameResponse> inquireRealName(
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) KftcAccountDto.RealNameRequest req) {
        String ip = resolveClientIp(request);
        oauthService.validateBearerToken(authorization, "inquiry", ip);
        return secureOk(openBankingService.inquireRealName(req, ip));
    }

    // 2. 잔액 조회 (핀테크이용번호 기준)
    @GetMapping("/account/balance/fin_num")
    public ResponseEntity<KftcAccountDto.BalanceResponse> inquireBalance(
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam("fintech_use_num") String fintechUseNum,
            @RequestParam(value = "bank_tran_id", required = false) String bankTranId) {
        String ip = resolveClientIp(request);
        KftcRecordDto.OAuthTokenRecord token = oauthService.validateBearerToken(authorization, "inquiry", ip);
        return secureOk(openBankingService.inquireBalance(fintechUseNum, bankTranId, token, ip));
    }

    // 3. 잔액 및 거래내역 조회 (핀테크이용번호 기준)
    @GetMapping("/account/transaction_list/fin_num")
    public ResponseEntity<KftcTransactionDto.TransactionListResponse> inquireTransactionList(
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam("fintech_use_num") String fintechUseNum,
            @RequestParam(value = "inquiry_type", required = false, defaultValue = "A") String inquiryType,
            @RequestParam(value = "from_date", required = false) String fromDate,
            @RequestParam(value = "to_date", required = false) String toDate,
            @RequestParam(value = "sort_order", required = false, defaultValue = "D") String sortOrder,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "bank_tran_id", required = false) String bankTranId) {
        String ip = resolveClientIp(request);
        KftcRecordDto.OAuthTokenRecord token = oauthService.validateBearerToken(authorization, "inquiry", ip);
        return secureOk(openBankingService.inquireTransactionList(
                fintechUseNum, inquiryType, fromDate, toDate, sortOrder, limit, bankTranId, token, ip));
    }

    // 4. 출금이체 (원자적 출금 + 모임장 입금 + 양쪽 거래내역 기록)
    @PostMapping("/transfer/withdraw/fin_num")
    public ResponseEntity<KftcTransferDto.TransferResponse> withdrawTransfer(
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) KftcTransferDto.WithdrawRequest req) {
        String ip = resolveClientIp(request);
        KftcRecordDto.OAuthTokenRecord token = oauthService.validateBearerToken(authorization, "transfer", ip);
        return secureOk(openBankingService.withdrawTransfer(req, token, ip));
    }

    // 5. 입금이체 (원자적 출금 + 모임장 입금 + 양쪽 거래내역 기록)
    @PostMapping("/transfer/deposit/fin_num")
    public ResponseEntity<KftcTransferDto.TransferResponse> depositTransfer(
            HttpServletRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) KftcTransferDto.DepositRequest req) {
        String ip = resolveClientIp(request);
        KftcRecordDto.OAuthTokenRecord token = oauthService.validateBearerToken(authorization, "transfer", ip);
        return secureOk(openBankingService.depositTransfer(req, token, ip));
    }

    private static String resolveClientIp(HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()) {
            return "unknown";
        }
        return request.getRemoteAddr();
    }

    private static <T> ResponseEntity<T> secureOk(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}
