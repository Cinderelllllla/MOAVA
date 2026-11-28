package com.moava.openbanking.service;

import com.moava.openbanking.client.OpenBankingProperties;
import com.moava.openbanking.dto.KftcAccountDto;
import com.moava.openbanking.dto.KftcRecordDto;
import com.moava.openbanking.dto.KftcTransactionDto;
import com.moava.openbanking.dto.KftcTransferDto;
import com.moava.openbanking.exception.KftcOpenBankingException;
import com.moava.openbanking.exception.KftcResponseCode;
import com.moava.openbanking.mapper.KftcOpenBankingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 금융결제원 오픈뱅킹 v2.0 모의 핵심 원장 서비스
 * - 계좌 유효성 / 예금주 실명 인증
 * - 핀테크이용번호 기준 잔액 및 거래내역 조회
 * - 출금이체 / 입금이체 (@Transactional + SELECT ... FOR UPDATE 데드락 방지 락 + bank_tran_id 멱등성)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KftcOpenBankingService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");

    private final KftcOpenBankingMapper mapper;
    private final KftcSecurityGuard guard;
    private final OpenBankingProperties properties;

    /**
     * 3. 계좌 유효성 및 예금주 실명 인증 (POST /v2.0/inquiry/real_name)
     */
    @Transactional(readOnly = true)
    public KftcAccountDto.RealNameResponse inquireRealName(
            KftcAccountDto.RealNameRequest req,
            String clientIp
    ) {
        guard.checkRateLimit("REAL_NAME", clientIp, 30);
        if (req == null) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "요청 본문이 비어 있습니다.");
        }
        String bankCode = guard.validateBankCode(req.getBankCodeStd());
        String accountNo = guard.validateAccountNo(req.getAccountNum());
        String holderName = guard.validateHolderName(req.getAccountHolderName());
        String bankTranId = guard.validateOrGenerateBankTranId(req.getBankTranId(), properties.getOrgCode());

        KftcRecordDto.AccountRecord account = mapper.findAccountByBankAndNo(bankCode, accountNo);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new KftcOpenBankingException(KftcResponseCode.ACCOUNT_NOT_FOUND);
        }
        if (!account.getHolderName().trim().equals(holderName)) {
            throw new KftcOpenBankingException(KftcResponseCode.HOLDER_NAME_MISMATCH);
        }

        KftcAccountDto.RealNameResponse res = new KftcAccountDto.RealNameResponse();
        res.fillSuccess();
        res.setBankTranId(bankTranId);
        res.setBankCodeStd(account.getBankCode());
        res.setBankName(guard.sanitizeOutputText(account.getBankName()));
        res.setAccountNum(account.getAccountNo());
        res.setAccountHolderName(guard.sanitizeOutputText(account.getHolderName()));
        res.setFintechUseNum(account.getFintechUseNum());
        res.setLeaderAccountYn(account.getLeaderYn());
        res.setVerified(true);
        return res;
    }

    /**
     * 4-1. 계좌 잔액 조회 (GET /v2.0/account/balance/fin_num)
     */
    @Transactional(readOnly = true)
    public KftcAccountDto.BalanceResponse inquireBalance(
            String rawFintechUseNum,
            String rawBankTranId,
            KftcRecordDto.OAuthTokenRecord token,
            String clientIp
    ) {
        guard.checkRateLimit("BALANCE", clientIp, 60);
        String fintechUseNum = guard.validateFintechUseNum(rawFintechUseNum, "fintech_use_num");
        String bankTranId = guard.validateOrGenerateBankTranId(rawBankTranId, properties.getOrgCode());

        KftcRecordDto.AccountRecord account = requireActiveAccount(fintechUseNum);
        verifyTokenAccountAccess(token, account);

        KftcAccountDto.BalanceResponse res = new KftcAccountDto.BalanceResponse();
        res.fillSuccess();
        res.setBankTranId(bankTranId);
        res.setBankCodeStd(account.getBankCode());
        res.setBankName(guard.sanitizeOutputText(account.getBankName()));
        res.setFintechUseNum(account.getFintechUseNum());
        res.setAccountHolderName(guard.sanitizeOutputText(account.getHolderName()));
        res.setBalanceAmt(account.getBalance());
        res.setAvailableAmt(account.getBalance());
        return res;
    }

    /**
     * 4-2. 잔액 및 거래내역 조회 (GET /v2.0/account/transaction_list/fin_num)
     */
    @Transactional(readOnly = true)
    public KftcTransactionDto.TransactionListResponse inquireTransactionList(
            String rawFintechUseNum,
            String rawInquiryType,
            String rawFromDate,
            String rawToDate,
            String rawSortOrder,
            Integer rawLimit,
            String rawBankTranId,
            KftcRecordDto.OAuthTokenRecord token,
            String clientIp
    ) {
        guard.checkRateLimit("TX_LIST", clientIp, 60);
        String fintechUseNum = guard.validateFintechUseNum(rawFintechUseNum, "fintech_use_num");
        String bankTranId = guard.validateOrGenerateBankTranId(rawBankTranId, properties.getOrgCode());
        int limit = guard.validateLimit(rawLimit);

        String inoutFilter = resolveInoutFilter(rawInquiryType);
        String sortOrder = resolveSortOrder(rawSortOrder);

        LocalDate today = LocalDate.now();
        String fromStr = guard.validateDateYyyyMmDd(rawFromDate, "from_date");
        String toStr = guard.validateDateYyyyMmDd(rawToDate, "to_date");

        LocalDate fromDate = (fromStr != null) ? parseDateStrict(fromStr, "from_date") : today.minusMonths(1);
        LocalDate toDate = (toStr != null) ? parseDateStrict(toStr, "to_date") : today;
        if (fromDate.isAfter(toDate)) {
            throw new KftcOpenBankingException(
                    KftcResponseCode.INVALID_PARAMETER,
                    "조회 시작일(from_date)은 종료일(to_date)보다 늦을 수 없습니다."
            );
        }

        KftcRecordDto.AccountRecord account = requireActiveAccount(fintechUseNum);
        verifyTokenAccountAccess(token, account);

        List<KftcRecordDto.TransactionRecord> rows = mapper.findTransactions(
                fintechUseNum,
                inoutFilter,
                fromDate.atStartOfDay(),
                toDate.plusDays(1).atStartOfDay(),
                sortOrder,
                limit
        );

        List<KftcTransactionDto.TransactionItem> items = new ArrayList<>(rows.size());
        for (KftcRecordDto.TransactionRecord row : rows) {
            if (row.getTranAmt() == null || row.getTranAmt() <= 0
                    || row.getAfterBalanceAmt() == null || row.getAfterBalanceAmt() < 0) {
                log.error("[KFTC-SECURITY] 원장 거래 무결성 위반 탐지: bankTranId={}", row.getBankTranId());
                throw new KftcOpenBankingException(KftcResponseCode.INTERNAL_ERROR, "거래 원장 무결성 오류가 발생했습니다.");
            }
            KftcTransactionDto.TransactionItem item = new KftcTransactionDto.TransactionItem();
            item.setBankTranId(row.getBankTranId());
            item.setTranDate(row.getTranAt().format(YYYYMMDD));
            item.setTranTime(row.getTranAt().format(HHMMSS));
            item.setInoutType(row.getInoutType());
            item.setTranType("계좌이체");
            item.setPrintContent(guard.sanitizeOutputText(row.getPrintContent()));
            item.setTranAmt(row.getTranAmt());
            item.setAfterBalanceAmt(row.getAfterBalanceAmt());
            item.setBranchName(guard.sanitizeOutputText(row.getBranchName()));
            item.setCounterpartyName(guard.sanitizeOutputText(row.getCounterpartyName()));
            items.add(item);
        }

        KftcTransactionDto.TransactionListResponse res = new KftcTransactionDto.TransactionListResponse();
        res.fillSuccess();
        res.setBankTranId(bankTranId);
        res.setBankCodeStd(account.getBankCode());
        res.setBankName(guard.sanitizeOutputText(account.getBankName()));
        res.setFintechUseNum(account.getFintechUseNum());
        res.setAccountHolderName(guard.sanitizeOutputText(account.getHolderName()));
        res.setBalanceAmt(account.getBalance());
        res.setPageRecordCnt(items.size());
        res.setResList(items);
        return res;
    }

    /**
     * 5-1. 출금이체 (POST /v2.0/transfer/withdraw/fin_num)
     */
    @Transactional
    public KftcTransferDto.TransferResponse withdrawTransfer(
            KftcTransferDto.WithdrawRequest req,
            KftcRecordDto.OAuthTokenRecord token,
            String clientIp
    ) {
        if (req == null) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "요청 본문이 비어 있습니다.");
        }
        return executeAtomicTransfer(
                req.getFintechUseNum(),
                req.getRecvFintechUseNum(),
                req.getTranAmt(),
                req.getBankTranId(),
                req.getWdPrintContent(),
                req.getDpsPrintContent(),
                token,
                clientIp
        );
    }

    /**
     * 5-2. 입금이체 (POST /v2.0/transfer/deposit/fin_num)
     */
    @Transactional
    public KftcTransferDto.TransferResponse depositTransfer(
            KftcTransferDto.DepositRequest req,
            KftcRecordDto.OAuthTokenRecord token,
            String clientIp
    ) {
        if (req == null) {
            throw new KftcOpenBankingException(KftcResponseCode.INVALID_PARAMETER, "요청 본문이 비어 있습니다.");
        }
        return executeAtomicTransfer(
                req.getWdFintechUseNum(),
                req.getFintechUseNum(),
                req.getTranAmt(),
                req.getBankTranId(),
                req.getWdPrintContent(),
                req.getPrintContent(),
                token,
                clientIp
        );
    }

    /**
     * 이체 원자성 보장 핵심 로직:
     * 0) 입력·금액·멱등성(bank_tran_id) 검증
     * 1) 두 계좌를 핀테크이용번호 오름차순으로 SELECT ... FOR UPDATE (교착상태/데드락 방지)
     * 2) 계좌 상태·모임장 수취 계좌 여부·출금 잔액 검증
     * 3) 출금 계좌 차감 -> 입금 계좌 증액 -> 양쪽 거래내역 INSERT (중간 예외 시 전체 롤백)
     */
    private KftcTransferDto.TransferResponse executeAtomicTransfer(
            String rawWdFinNum,
            String rawDpsFinNum,
            Long rawTranAmt,
            String rawBankTranId,
            String rawWdPrint,
            String rawDpsPrint,
            KftcRecordDto.OAuthTokenRecord token,
            String clientIp
    ) {
        guard.checkRateLimit("TRANSFER", clientIp, 30);

        String wdFinNum = guard.validateFintechUseNum(rawWdFinNum, "출금 계좌 핀테크이용번호");
        String dpsFinNum = guard.validateFintechUseNum(rawDpsFinNum, "입금 계좌 핀테크이용번호");
        if (wdFinNum.equals(dpsFinNum)) {
            throw new KftcOpenBankingException(KftcResponseCode.SAME_ACCOUNT_TRANSFER);
        }
        long tranAmt = guard.validateTransferAmount(rawTranAmt);
        String bankTranId = guard.validateOrGenerateBankTranId(rawBankTranId, properties.getOrgCode());
        String wdPrint = guard.validatePrintContent(rawWdPrint, "모아바 회비 출금");
        String dpsPrint = guard.validatePrintContent(rawDpsPrint, "모아바 회비 입금");

        // 멱등성 1차 체크 (동일 bank_tran_id 중복 이체 차단)
        if (mapper.countByBankTranId(bankTranId) > 0) {
            throw new KftcOpenBankingException(KftcResponseCode.DUPLICATE_BANK_TRAN_ID);
        }

        // 데드락 방지를 위해 항상 FINTECH_USE_NUM 오름차순으로 비관적 락 획득
        String firstFin = wdFinNum.compareTo(dpsFinNum) < 0 ? wdFinNum : dpsFinNum;
        String secondFin = wdFinNum.compareTo(dpsFinNum) < 0 ? dpsFinNum : wdFinNum;

        KftcRecordDto.AccountRecord firstAcc = mapper.findAccountForUpdate(firstFin);
        KftcRecordDto.AccountRecord secondAcc = mapper.findAccountForUpdate(secondFin);

        KftcRecordDto.AccountRecord wdAccount = wdFinNum.equals(firstFin) ? firstAcc : secondAcc;
        KftcRecordDto.AccountRecord dpsAccount = dpsFinNum.equals(firstFin) ? firstAcc : secondAcc;

        if (wdAccount == null || !"ACTIVE".equals(wdAccount.getStatus())) {
            throw new KftcOpenBankingException(KftcResponseCode.ACCOUNT_NOT_FOUND, "출금 계좌를 찾을 수 없거나 거래 정지 상태입니다.");
        }
        if (dpsAccount == null || !"ACTIVE".equals(dpsAccount.getStatus())) {
            throw new KftcOpenBankingException(KftcResponseCode.ACCOUNT_NOT_FOUND, "입금 계좌를 찾을 수 없거나 거래 정지 상태입니다.");
        }

        verifyTokenAccountAccess(token, wdAccount);

        // 모임장 계좌 외 타 계좌 이체 차단
        if (!"Y".equals(dpsAccount.getLeaderYn())) {
            log.warn("[KFTC-SECURITY] 비허용 타 계좌 이체 차단: dpsFinNum={}", guard.maskFintechNum(dpsFinNum));
            throw new KftcOpenBankingException(KftcResponseCode.UNAUTHORIZED_TARGET_ACCOUNT);
        }

        // ① 출금 계좌 잔액 검증
        if (wdAccount.getBalance() == null || wdAccount.getBalance() < tranAmt) {
            throw new KftcOpenBankingException(KftcResponseCode.INSUFFICIENT_BALANCE);
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        // ② 출금 계좌 잔액 차감
        int debited = mapper.debitAccountBalance(wdFinNum, tranAmt, now);
        if (debited != 1) {
            throw new KftcOpenBankingException(KftcResponseCode.INSUFFICIENT_BALANCE);
        }

        // ③ 입금 계좌(모임장) 잔액 증액
        int credited = mapper.creditAccountBalance(dpsFinNum, tranAmt, now);
        if (credited != 1) {
            throw new KftcOpenBankingException(KftcResponseCode.ACCOUNT_NOT_FOUND, "입금 계좌 잔액 갱신에 실패했습니다.");
        }

        long wdAfterBalance = wdAccount.getBalance() - tranAmt;
        long dpsAfterBalance = dpsAccount.getBalance() + tranAmt;

        // ④ 양쪽 거래내역 INSERT (동시 중복 bank_tran_id 경합 시 DB UNIQUE 제약으로 2차 방어 + 전체 롤백)
        try {
            KftcRecordDto.TransactionRecord wdTx = new KftcRecordDto.TransactionRecord();
            wdTx.setBankTranId(bankTranId);
            wdTx.setFintechUseNum(wdFinNum);
            wdTx.setInoutType("출금");
            wdTx.setTranAmt(tranAmt);
            wdTx.setAfterBalanceAmt(wdAfterBalance);
            wdTx.setPrintContent(wdPrint);
            wdTx.setCounterpartyName(dpsAccount.getHolderName());
            wdTx.setCounterpartyFinNum(dpsFinNum);
            wdTx.setBranchName("모아바모의점");
            wdTx.setTranAt(now);
            mapper.insertTransaction(wdTx);

            KftcRecordDto.TransactionRecord dpsTx = new KftcRecordDto.TransactionRecord();
            dpsTx.setBankTranId(bankTranId);
            dpsTx.setFintechUseNum(dpsFinNum);
            dpsTx.setInoutType("입금");
            dpsTx.setTranAmt(tranAmt);
            dpsTx.setAfterBalanceAmt(dpsAfterBalance);
            dpsTx.setPrintContent(dpsPrint);
            dpsTx.setCounterpartyName(wdAccount.getHolderName());
            dpsTx.setCounterpartyFinNum(wdFinNum);
            dpsTx.setBranchName("모아바모의점");
            dpsTx.setTranAt(now);
            mapper.insertTransaction(dpsTx);
        } catch (DuplicateKeyException e) {
            throw new KftcOpenBankingException(KftcResponseCode.DUPLICATE_BANK_TRAN_ID);
        }

        log.info("[KFTC-AUDIT] 이체 완료: bankTranId={}, wd={}, dps={}, amt={}",
                bankTranId, guard.maskFintechNum(wdFinNum), guard.maskFintechNum(dpsFinNum), tranAmt);

        KftcTransferDto.TransferResponse res = new KftcTransferDto.TransferResponse();
        res.fillSuccess();
        res.setBankTranId(bankTranId);
        res.setBankTranDate(now.format(YYYYMMDD));
        res.setWdFintechUseNum(wdFinNum);
        res.setWdBankName(guard.sanitizeOutputText(wdAccount.getBankName()));
        res.setWdAccountHolderName(guard.sanitizeOutputText(wdAccount.getHolderName()));
        res.setWdAfterBalanceAmt(wdAfterBalance);
        res.setDpsFintechUseNum(dpsFinNum);
        res.setDpsBankName(guard.sanitizeOutputText(dpsAccount.getBankName()));
        res.setDpsAccountHolderName(guard.sanitizeOutputText(dpsAccount.getHolderName()));
        res.setDpsAfterBalanceAmt(dpsAfterBalance);
        res.setTranAmt(tranAmt);
        res.setPrintContent(guard.sanitizeOutputText(dpsPrint));
        return res;
    }

    private KftcRecordDto.AccountRecord requireActiveAccount(String fintechUseNum) {
        KftcRecordDto.AccountRecord account = mapper.findAccountByFintechUseNum(fintechUseNum);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new KftcOpenBankingException(KftcResponseCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    private void verifyTokenAccountAccess(
            KftcRecordDto.OAuthTokenRecord token,
            KftcRecordDto.AccountRecord account
    ) {
        if (token != null && token.getUserSeqNo() != null && !token.getUserSeqNo().isBlank()) {
            if (!token.getUserSeqNo().equals(account.getUserSeqNo())) {
                log.warn("[KFTC-SECURITY] 타 사용자 계좌 접근 차단: tokenUser={}, accountUser={}",
                        token.getUserSeqNo(), account.getUserSeqNo());
                throw new KftcOpenBankingException(
                        KftcResponseCode.INSUFFICIENT_SCOPE,
                        "토큰 소유자의 계좌만 조회/출금할 수 있습니다."
                );
            }
        }
    }

    private String resolveInoutFilter(String rawInquiryType) {
        if (rawInquiryType == null || rawInquiryType.isBlank() || "A".equalsIgnoreCase(rawInquiryType.trim())) {
            return null;
        }
        String upper = rawInquiryType.trim().toUpperCase();
        if ("I".equals(upper)) {
            return "입금";
        }
        if ("O".equals(upper)) {
            return "출금";
        }
        throw new KftcOpenBankingException(
                KftcResponseCode.INVALID_PARAMETER,
                "inquiry_type은 A(전체), I(입금), O(출금) 중 하나여야 합니다."
        );
    }

    private String resolveSortOrder(String rawSortOrder) {
        if (rawSortOrder == null || rawSortOrder.isBlank() || "D".equalsIgnoreCase(rawSortOrder.trim())) {
            return "D";
        }
        if ("A".equalsIgnoreCase(rawSortOrder.trim())) {
            return "A";
        }
        throw new KftcOpenBankingException(
                KftcResponseCode.INVALID_PARAMETER,
                "sort_order는 A(오름차순) 또는 D(내림차순)여야 합니다."
        );
    }

    private LocalDate parseDateStrict(String yyyyMmDd, String fieldName) {
        try {
            return LocalDate.parse(yyyyMmDd, YYYYMMDD);
        } catch (DateTimeParseException e) {
            throw new KftcOpenBankingException(
                    KftcResponseCode.INVALID_PARAMETER,
                    fieldName + " 날짜 값이 올바르지 않습니다."
            );
        }
    }
}
