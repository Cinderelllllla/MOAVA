package com.moava.openbanking.service;

import com.moava.common.util.NameMasker;
import com.moava.crypto.service.CryptoKeyService;
import com.moava.group.create.service.GroupAccountCrypto;
import com.moava.openbanking.client.OpenBankingProperties;
import com.moava.openbanking.codef.CodefAccountMapper;
import com.moava.openbanking.codef.CodefBankCode;
import com.moava.openbanking.codef.CodefBankService;
import com.moava.openbanking.codef.CodefDto;
import com.moava.openbanking.codef.CodefException;
import com.moava.openbanking.dto.KftcRecordDto;
import com.moava.openbanking.dto.KftcTransactionDto;
import com.moava.openbanking.dto.KftcTransferDto;
import com.moava.openbanking.dto.MockBankDto;
import com.moava.openbanking.exception.KftcOpenBankingException;
import com.moava.openbanking.exception.KftcResponseCode;
import com.moava.openbanking.mapper.KftcOpenBankingMapper;
import com.moava.openbanking.mapper.MockBankLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 프론트엔드 회원 화면용 오픈뱅킹 통합 서비스 (/api/openbanking/accounts):
 * - CODEF 실계좌인증 상태 + 모의 은행(MOCK_KFTC_ACCOUNT / MOCK_KFTC_TRANSACTION) 원장 통합 조회
 * - 인증 완료 계좌의 모의 은행 (재)적재/동기화
 * - 모의 계좌 잔액·거래내역·월별 집계 조회
 * - 시연용 자금 충전 및 모임장 계좌 회비 이체 (KftcOpenBankingService 원자적 이체 엔진 재사용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MockBankAccountService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");
    private static final int OVERVIEW_RECENT_TX_LIMIT = 30;

    private final MockBankLedgerMapper ledgerMapper;
    private final KftcOpenBankingMapper kftcMapper;
    private final CodefAccountMapper codefAccountMapper;
    private final CryptoKeyService cryptoKeyService;
    private final KftcOpenBankingService kftcOpenBankingService;
    private final CodefBankService codefBankService;
    private final MockBankProvisionService provisionService;
    private final KftcSecurityGuard securityGuard;
    private final OpenBankingProperties openBankingProperties;
    private final GroupAccountCrypto groupAccountCrypto;

    /**
     * 프론트 오픈뱅킹 화면 초기 로딩용 통합 조회
     */
    @Transactional(readOnly = true)
    public MockBankDto.OverviewResponse getOverview(Long memberId) {
        String memberName = requireActiveMemberName(memberId);
        String userSeqNo = provisionService.deriveUserSeqNo(memberId);

        List<CodefDto.ConnectionSummary> connections = codefBankService.getMyConnections(memberId);
        int verifiedCount = (int) connections.stream().filter(CodefDto.ConnectionSummary::isVerified).count();

        List<KftcRecordDto.AccountRecord> rawAccounts = ledgerMapper.findAccountsByUserSeqNo(userSeqNo);
        Map<String, KftcRecordDto.AccountRecord> accountByFinNum = new HashMap<>();
        List<MockBankDto.MockAccountView> accountViews = new ArrayList<>(rawAccounts.size());
        long totalBalance = 0L;
        for (KftcRecordDto.AccountRecord acc : rawAccounts) {
            accountByFinNum.put(acc.getFintechUseNum(), acc);
            accountViews.add(toAccountView(acc));
            if (acc.getBalance() != null) {
                totalBalance += acc.getBalance();
            }
        }

        List<KftcRecordDto.TransactionRecord> rawRecentTx =
                ledgerMapper.findRecentTransactionsByUserSeqNo(userSeqNo, OVERVIEW_RECENT_TX_LIMIT);
        List<MockBankDto.MockTransactionView> recentViews = new ArrayList<>(rawRecentTx.size());
        for (KftcRecordDto.TransactionRecord tx : rawRecentTx) {
            recentViews.add(toTransactionView(tx, accountByFinNum.get(tx.getFintechUseNum())));
        }

        List<MockBankDto.MonthlyAggregate> sqlMonthly = ledgerMapper.findMonthlySummaryByUserSeqNo(userSeqNo);
        List<MockBankDto.MonthlyAggregate> monthlySummary;
        if (sqlMonthly != null && !sqlMonthly.isEmpty()) {
            monthlySummary = new ArrayList<>(sqlMonthly.size());
            for (MockBankDto.MonthlyAggregate row : sqlMonthly) {
                long dep = row.getTotalDepositAmt() != null ? row.getTotalDepositAmt() : 0L;
                long wd = row.getTotalWithdrawAmt() != null ? row.getTotalWithdrawAmt() : 0L;
                row.setTotalDepositAmt(dep);
                row.setTotalWithdrawAmt(wd);
                row.setNetAmt(dep - wd);
                monthlySummary.add(row);
            }
        } else {
            monthlySummary = aggregateMonthly(recentViews);
        }
        List<MockBankDto.LeaderTargetView> leaderTargets = getLeaderTargetsInternal(memberId);

        MockBankDto.OverviewResponse res = new MockBankDto.OverviewResponse();
        res.setMemberId(memberId);
        res.setMemberName(securityGuard.sanitizeOutputText(memberName));
        res.setUserSeqNo(userSeqNo);
        res.setCodefVerifiedCount(verifiedCount);
        res.setMockAccountCount(accountViews.size());
        res.setTotalBalanceAmt(totalBalance);
        res.setConnections(connections);
        res.setAccounts(accountViews);
        res.setRecentTransactions(recentViews);
        res.setMonthlySummary(monthlySummary);
        res.setLeaderTargets(leaderTargets);
        return res;
    }

    /**
     * 로그인 회원의 모의 은행 계좌 목록 조회
     */
    @Transactional(readOnly = true)
    public List<MockBankDto.MockAccountView> getMyAccounts(Long memberId) {
        requireActiveMemberName(memberId);
        String userSeqNo = provisionService.deriveUserSeqNo(memberId);
        List<KftcRecordDto.AccountRecord> rows = ledgerMapper.findAccountsByUserSeqNo(userSeqNo);
        List<MockBankDto.MockAccountView> views = new ArrayList<>(rows.size());
        for (KftcRecordDto.AccountRecord row : rows) {
            views.add(toAccountView(row));
        }
        return views;
    }

    /**
     * 이미 CODEF 실계좌인증이 완료된 계좌를 모의 은행 원장에 (재)적재/동기화한다.
     * - refreshFromCodef=true  : CODEF API 재조회 후 최신 잔액·실거래내역을 모의 원장에 적재
     * - refreshFromCodef=false : CODEF API 호출 없이(일 100건 한도 절약) 저장된 인증 정보로 모의 계좌 생성/갱신
     */
    @Transactional
    public MockBankDto.ProvisionResult syncFromCodef(
            Long memberId,
            MockBankDto.SyncFromCodefRequest req,
            String clientIp
    ) {
        String memberName = requireActiveMemberName(memberId);
        if (req == null) {
            throw CodefException.badRequest("MOCK_BANK_EMPTY_REQUEST", "요청 본문이 비어 있습니다.");
        }
        CodefBankCode bank = CodefBankCode.resolve(req.getOrganization());

        if (Boolean.TRUE.equals(req.getRefreshFromCodef())) {
            return codefBankService.refreshVerifiedAccountToMockBank(
                    memberId,
                    bank,
                    req.getAccountPassword(),
                    req.getBirthDate(),
                    req.getStartDate(),
                    req.getEndDate(),
                    req.getInitialMockBalance(),
                    req.getLeaderAccount(),
                    clientIp
            );
        }

        CodefDto.ConnectionRecord conn = codefAccountMapper.findConnection(memberId, bank.getCodefOrgCode());
        if (conn == null || !"Y".equals(conn.getVerifiedYn())
                || conn.getVerifiedAccountEnc() == null || conn.getVerifiedAccountEnc().isBlank()) {
            throw CodefException.badRequest(
                    "CODEF_NOT_VERIFIED",
                    bank.getBankName() + " 실계좌 인증 이력이 없습니다. 먼저 CODEF 실계좌 인증을 완료해 주세요."
            );
        }

        String accountDigits = cryptoKeyService.decrypt(conn.getVerifiedAccountEnc(), conn.getKeyVer());
        String holderName = (conn.getVerifiedHolderName() != null && !conn.getVerifiedHolderName().isBlank())
                ? conn.getVerifiedHolderName()
                : memberName;

        return provisionService.provisionFromCodef(
                memberId,
                bank,
                accountDigits,
                holderName,
                null,
                Collections.emptyList(),
                req.getInitialMockBalance(),
                req.getLeaderAccount(),
                false
        );
    }

    /**
     * 본인 모의 계좌 잔액 실시간 조회
     */
    @Transactional(readOnly = true)
    public MockBankDto.MockAccountView getAccountBalance(Long memberId, String fintechUseNum, String clientIp) {
        requireActiveMemberName(memberId);
        kftcOpenBankingService.inquireBalance(
                fintechUseNum, null, buildMemberToken(memberId, "inquiry"), clientIp
        );
        KftcRecordDto.AccountRecord acc = kftcMapper.findAccountByFintechUseNum(fintechUseNum.trim());
        if (acc == null) {
            throw new KftcOpenBankingException(KftcResponseCode.ACCOUNT_NOT_FOUND);
        }
        return toAccountView(acc);
    }

    /**
     * 본인 모의 계좌 거래내역 및 기간·월별 집계 조회
     */
    @Transactional(readOnly = true)
    public MockBankDto.AccountTransactionsResponse getAccountTransactions(
            Long memberId,
            String fintechUseNum,
            String inquiryType,
            String fromDate,
            String toDate,
            String sortOrder,
            Integer limit,
            String clientIp
    ) {
        requireActiveMemberName(memberId);
        KftcTransactionDto.TransactionListResponse kftcRes = kftcOpenBankingService.inquireTransactionList(
                fintechUseNum,
                inquiryType,
                fromDate,
                toDate,
                sortOrder,
                limit,
                null,
                buildMemberToken(memberId, "inquiry"),
                clientIp
        );

        KftcRecordDto.AccountRecord acc = kftcMapper.findAccountByFintechUseNum(kftcRes.getFintechUseNum());
        MockBankDto.MockAccountView accView = acc != null ? toAccountView(acc) : null;
        String maskedAcc = accView != null ? accView.getAccountMasked() : null;

        long totalDeposit = 0L;
        long totalWithdraw = 0L;
        List<MockBankDto.MockTransactionView> views = new ArrayList<>();
        if (kftcRes.getResList() != null) {
            for (KftcTransactionDto.TransactionItem item : kftcRes.getResList()) {
                MockBankDto.MockTransactionView v = new MockBankDto.MockTransactionView();
                v.setBankTranId(item.getBankTranId());
                v.setFintechUseNum(kftcRes.getFintechUseNum());
                v.setBankName(kftcRes.getBankName());
                v.setAccountMasked(maskedAcc);
                v.setTranDate(item.getTranDate());
                v.setTranTime(item.getTranTime());
                v.setInoutType(item.getInoutType());
                v.setTranAmt(item.getTranAmt());
                v.setAfterBalanceAmt(item.getAfterBalanceAmt());
                // KftcOpenBankingService에서 이미 출력 XSS 이스케이프 완료
                v.setPrintContent(item.getPrintContent());
                v.setCounterpartyName(item.getCounterpartyName());
                v.setBranchName(item.getBranchName());
                views.add(v);

                if (item.getTranAmt() != null) {
                    if ("입금".equals(item.getInoutType())) {
                        totalDeposit += item.getTranAmt();
                    } else if ("출금".equals(item.getInoutType())) {
                        totalWithdraw += item.getTranAmt();
                    }
                }
            }
        }

        LocalDate today = LocalDate.now();
        String resolvedFrom = (fromDate != null && !fromDate.isBlank()) ? fromDate.trim() : today.minusMonths(1).format(YYYYMMDD);
        String resolvedTo = (toDate != null && !toDate.isBlank()) ? toDate.trim() : today.format(YYYYMMDD);

        MockBankDto.AccountTransactionsResponse res = new MockBankDto.AccountTransactionsResponse();
        res.setAccount(accView);
        res.setFromDate(resolvedFrom);
        res.setToDate(resolvedTo);
        res.setInquiryType((inquiryType == null || inquiryType.isBlank()) ? "A" : inquiryType.trim().toUpperCase());
        res.setSortOrder((sortOrder == null || sortOrder.isBlank()) ? "D" : sortOrder.trim().toUpperCase());
        res.setCount(views.size());
        res.setTotalDepositAmt(totalDeposit);
        res.setTotalWithdrawAmt(totalWithdraw);
        res.setMonthlySummary(aggregateMonthly(views));
        res.setTransactions(views);
        return res;
    }

    /**
     * 본인 모의 계좌의 모임장 수취 계좌(LEADER_YN) 설정 변경
     */
    @Transactional
    public MockBankDto.MockAccountView updateLeaderFlag(Long memberId, String rawFintechUseNum, boolean leader) {
        requireActiveMemberName(memberId);
        String finNum = securityGuard.validateFintechUseNum(rawFintechUseNum, "fintechUseNum");
        String userSeqNo = provisionService.deriveUserSeqNo(memberId);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        int updated = ledgerMapper.updateAccountLeaderYn(finNum, userSeqNo, leader ? "Y" : "N", now);
        if (updated != 1) {
            throw CodefException.notFound("MOCK_BANK_ACCOUNT_NOT_FOUND", "본인 소유의 활성 모의 계좌를 찾을 수 없습니다.");
        }
        KftcRecordDto.AccountRecord acc = kftcMapper.findAccountByFintechUseNum(finNum);
        return toAccountView(acc);
    }

    /**
     * 시연용 모의 자금 충전 (실계좌 잔액이 부족할 때 모의 은행 원장에 충전 + 입금 거래내역 기록)
     */
    @Transactional
    public MockBankDto.DemoDepositResponse depositDemoFunds(
            Long memberId,
            String rawFintechUseNum,
            MockBankDto.DemoDepositRequest req,
            String clientIp
    ) {
        requireActiveMemberName(memberId);
        securityGuard.checkRateLimit("MOCK_DEMO_DEPOSIT", memberId + ":" + safeIp(clientIp), 20);
        if (req == null) {
            throw CodefException.badRequest("MOCK_BANK_EMPTY_REQUEST", "요청 본문이 비어 있습니다.");
        }

        String finNum = securityGuard.validateFintechUseNum(rawFintechUseNum, "fintechUseNum");
        long amount = securityGuard.validateTransferAmount(req.getAmount());
        String printContent = MockBankProvisionService.sanitizePlainText(
                securityGuard.validatePrintContent(req.getPrintContent(), "모의은행 시연 충전"),
                90,
                "모의은행 시연 충전"
        );
        String userSeqNo = provisionService.deriveUserSeqNo(memberId);

        KftcRecordDto.AccountRecord locked = kftcMapper.findAccountForUpdate(finNum);
        if (locked == null || !"ACTIVE".equals(locked.getStatus())) {
            throw new KftcOpenBankingException(KftcResponseCode.ACCOUNT_NOT_FOUND);
        }
        if (!userSeqNo.equals(locked.getUserSeqNo())) {
            throw new KftcOpenBankingException(KftcResponseCode.INSUFFICIENT_SCOPE, "본인 소유의 모의 계좌만 충전할 수 있습니다.");
        }

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        int credited = kftcMapper.creditAccountBalance(finNum, amount, now);
        if (credited != 1) {
            throw new KftcOpenBankingException(KftcResponseCode.ACCOUNT_NOT_FOUND, "모의 계좌 잔액 충전에 실패했습니다.");
        }

        long afterBalance = (locked.getBalance() != null ? locked.getBalance() : 0L) + amount;
        String bankTranId = securityGuard.generateBankTranId(openBankingProperties.getOrgCode());

        KftcRecordDto.TransactionRecord tx = new KftcRecordDto.TransactionRecord();
        tx.setBankTranId(bankTranId);
        tx.setFintechUseNum(finNum);
        tx.setInoutType("입금");
        tx.setTranAmt(amount);
        tx.setAfterBalanceAmt(afterBalance);
        tx.setPrintContent(printContent);
        tx.setCounterpartyName("모아바시연충전");
        tx.setCounterpartyFinNum(null);
        tx.setBranchName("모아바모의점");
        tx.setTranAt(now);
        kftcMapper.insertTransaction(tx);

        log.info("[MOCK-BANK] 시연용 모의 자금 충전 완료: memberId={}, finNum={}, amt={}, afterBal={}",
                memberId, securityGuard.maskFintechNum(finNum), amount, afterBalance);

        MockBankDto.DemoDepositResponse res = new MockBankDto.DemoDepositResponse();
        res.setFintechUseNum(finNum);
        res.setBankTranId(bankTranId);
        res.setDepositAmt(amount);
        res.setAfterBalanceAmt(afterBalance);
        res.setPrintContent(securityGuard.sanitizeOutputText(printContent));
        res.setTranAt(now);
        return res;
    }

    /**
     * 프론트에서 호출하는 모의 회비 이체 (본인 모의계좌 -> 참여 모임의 현재 모임 계좌)
     * 내부적으로 KftcOpenBankingService.withdrawTransfer를 호출해 원자성·락순서·멱등성을 그대로 보장한다.
     */
    @Transactional
    public MockBankDto.MemberTransferResponse transferToLeader(
            Long memberId,
            MockBankDto.MemberTransferRequest req,
            String clientIp
    ) {
        String memberName = requireActiveMemberName(memberId);
        if (req == null) {
            throw CodefException.badRequest("MOCK_BANK_EMPTY_REQUEST", "이체 요청 본문이 비어 있습니다.");
        }

        String toFinNum = req.getToFintechUseNum() != null ? req.getToFintechUseNum().trim() : "";
        boolean allowedTarget = !toFinNum.isEmpty() && getLeaderTargetsInternal(memberId).stream()
                .anyMatch(t -> toFinNum.equals(t.getFintechUseNum()));
        if (!allowedTarget) {
            throw CodefException.forbidden(
                    "MOCK_BANK_TARGET_NOT_ALLOWED",
                    "참여 중인 모임의 현재 모임 계좌로만 회비를 이체할 수 있습니다."
            );
        }

        KftcTransferDto.WithdrawRequest kftcReq = new KftcTransferDto.WithdrawRequest();
        kftcReq.setBankTranId(req.getBankTranId());
        kftcReq.setFintechUseNum(req.getFromFintechUseNum());
        kftcReq.setRecvFintechUseNum(toFinNum);
        kftcReq.setTranAmt(req.getAmount());
        kftcReq.setWdPrintContent(req.getWdPrintContent());
        kftcReq.setDpsPrintContent(req.getDpsPrintContent());
        kftcReq.setReqClientName(memberName);

        KftcTransferDto.TransferResponse kftcRes = kftcOpenBankingService.withdrawTransfer(
                kftcReq,
                buildMemberToken(memberId, "transfer"),
                safeIp(clientIp)
        );

        MockBankDto.MemberTransferResponse res = new MockBankDto.MemberTransferResponse();
        res.setBankTranId(kftcRes.getBankTranId());
        res.setBankTranDate(kftcRes.getBankTranDate());
        res.setFromFintechUseNum(kftcRes.getWdFintechUseNum());
        res.setFromBankName(kftcRes.getWdBankName());
        res.setFromHolderName(kftcRes.getWdAccountHolderName());
        res.setFromAfterBalanceAmt(kftcRes.getWdAfterBalanceAmt());
        res.setToFintechUseNum(kftcRes.getDpsFintechUseNum());
        res.setToBankName(kftcRes.getDpsBankName());
        res.setToHolderNameMasked(NameMasker.mask(kftcRes.getDpsAccountHolderName()));
        res.setTranAmt(kftcRes.getTranAmt());
        res.setPrintContent(kftcRes.getPrintContent());
        res.setMessage("모의 은행 회비 이체가 완료되었습니다.");
        return res;
    }

    /**
     * 이체 가능한 모임장 수취 계좌 목록 조회 (내가 참여 중인 모임의 현재 모임 계좌만 매칭)
     */
    @Transactional(readOnly = true)
    public List<MockBankDto.LeaderTargetView> getLeaderTargets(Long memberId) {
        requireActiveMemberName(memberId);
        return getLeaderTargetsInternal(memberId);
    }

    // ==================== 내부 헬퍼 ====================

    private List<MockBankDto.LeaderTargetView> getLeaderTargetsInternal(Long memberId) {
        String myUserSeqNo = provisionService.deriveUserSeqNo(memberId);
        List<MockBankDto.MemberGroupInfo> myGroups = ledgerMapper.findMemberActiveGroups(memberId);
        if (myGroups == null || myGroups.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<KftcRecordDto.AccountRecord>> candidateCache = new HashMap<>();
        Map<String, MockBankDto.LeaderTargetView> byFinNum = new LinkedHashMap<>();
        Map<String, Integer> extraGroupCounts = new HashMap<>();

        // 내가 참여 중인 모임의 CURRENT 모임 계좌만 은행코드+뒤4자리 후보 조회 후 HMAC 해시로 정확히 매칭
        for (MockBankDto.MemberGroupInfo g : myGroups) {
            if (g.getAccountBankName() == null || g.getAccountBankName().isBlank()
                    || g.getAccountLast4() == null || g.getAccountLast4().isBlank()
                    || g.getAccountNoHash() == null || g.getAccountNoHash().isBlank()) {
                continue;
            }

            CodefBankCode bank;
            try {
                bank = CodefBankCode.resolve(g.getAccountBankName());
            } catch (CodefException e) {
                log.debug("[MOCK-BANK] 미지원 은행으로 모임 계좌 매칭 생략: groupId={}", g.getGroupId());
                continue;
            }

            String last4 = g.getAccountLast4().trim();
            String expectedHash = g.getAccountNoHash().trim();
            String cacheKey = bank.getKftcCode() + ":" + last4;
            List<KftcRecordDto.AccountRecord> candidates = candidateCache.computeIfAbsent(
                    cacheKey,
                    k -> {
                        List<KftcRecordDto.AccountRecord> found =
                                ledgerMapper.findActiveLeaderAccountsByBankAndLast4(bank.getKftcCode(), last4);
                        return found != null ? found : Collections.emptyList();
                    }
            );

            KftcRecordDto.AccountRecord matched = matchAccountByHash(candidates, expectedHash);
            if (matched == null || myUserSeqNo.equals(matched.getUserSeqNo())) {
                continue; // 본인 계좌로의 자가 이체(SAME_ACCOUNT_TRANSFER) 방지
            }

            String safeTitle = securityGuard.sanitizeOutputText(g.getTitle());
            String verifyStatus = g.getAccountVerifyStatus();
            boolean verified = "VERIFIED".equalsIgnoreCase(verifyStatus);

            MockBankDto.LeaderTargetView existing = byFinNum.get(matched.getFintechUseNum());
            if (existing == null) {
                MockBankDto.LeaderTargetView target = toLeaderTargetView(matched);
                target.setGroupId(g.getGroupId());
                target.setGroupTitle(safeTitle);
                target.setMonthlyDuesAmt(g.getAmount());
                target.setMyGroup(true);
                target.setAccountVerifyStatus(verifyStatus);
                target.setAccountVerified(verified);
                byFinNum.put(matched.getFintechUseNum(), target);
            } else {
                int extra = extraGroupCounts.merge(matched.getFintechUseNum(), 1, Integer::sum);
                String baseTitle = existing.getGroupTitle();
                int suffixIdx = baseTitle != null ? baseTitle.indexOf(" 외 ") : -1;
                if (suffixIdx >= 0) {
                    baseTitle = baseTitle.substring(0, suffixIdx);
                }
                if (baseTitle != null && !baseTitle.isBlank()) {
                    existing.setGroupTitle(baseTitle + " 외 " + extra + "개 모임");
                }
                if (!existing.isAccountVerified() && verified) {
                    existing.setAccountVerifyStatus(verifyStatus);
                    existing.setAccountVerified(true);
                }
            }
        }

        return new ArrayList<>(byFinNum.values());
    }

    private KftcRecordDto.AccountRecord matchAccountByHash(
            List<KftcRecordDto.AccountRecord> candidates,
            String expectedHash
    ) {
        if (candidates == null || candidates.isEmpty() || expectedHash == null || expectedHash.isBlank()) {
            return null;
        }
        for (KftcRecordDto.AccountRecord candidate : candidates) {
            if (candidate == null || candidate.getAccountNo() == null) {
                continue;
            }
            String digits = candidate.getAccountNo().replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                continue;
            }
            try {
                String hashed = groupAccountCrypto.hashAccountNumber(digits);
                if (hashed != null && expectedHash.equalsIgnoreCase(hashed.trim())) {
                    return candidate;
                }
            } catch (IllegalArgumentException e) {
                log.debug("[MOCK-BANK] 모의 계좌 해시 비교 생략: finNum={}",
                        securityGuard.maskFintechNum(candidate.getFintechUseNum()));
            }
        }
        return null;
    }

    private MockBankDto.LeaderTargetView toLeaderTargetView(KftcRecordDto.AccountRecord acc) {
        MockBankDto.LeaderTargetView t = new MockBankDto.LeaderTargetView();
        t.setFintechUseNum(acc.getFintechUseNum());
        t.setBankCode(acc.getBankCode());
        t.setBankName(securityGuard.sanitizeOutputText(acc.getBankName()));
        t.setAccountMasked("****" + extractLast4(acc.getAccountNo()));
        t.setHolderNameMasked(NameMasker.mask(acc.getHolderName()));
        return t;
    }

    private MockBankDto.MockAccountView toAccountView(KftcRecordDto.AccountRecord acc) {
        String last4 = extractLast4(acc.getAccountNo());
        MockBankDto.MockAccountView v = new MockBankDto.MockAccountView();
        v.setFintechUseNum(acc.getFintechUseNum());
        v.setBankCode(acc.getBankCode());
        v.setBankName(securityGuard.sanitizeOutputText(acc.getBankName()));
        v.setAccountMasked("****" + last4);
        v.setAccountLast4(last4);
        v.setHolderName(securityGuard.sanitizeOutputText(acc.getHolderName()));
        v.setUserSeqNo(acc.getUserSeqNo());
        v.setBalanceAmt(acc.getBalance());
        v.setLeaderYn(acc.getLeaderYn());
        v.setLeaderAccount("Y".equals(acc.getLeaderYn()));
        v.setStatus(acc.getStatus());
        v.setCreatedAt(acc.getCreatedAt());
        v.setUpdatedAt(acc.getUpdatedAt());
        return v;
    }

    private MockBankDto.MockTransactionView toTransactionView(
            KftcRecordDto.TransactionRecord tx,
            KftcRecordDto.AccountRecord account
    ) {
        MockBankDto.MockTransactionView v = new MockBankDto.MockTransactionView();
        v.setTxId(tx.getTxId());
        v.setBankTranId(tx.getBankTranId());
        v.setFintechUseNum(tx.getFintechUseNum());
        if (account != null) {
            v.setBankName(securityGuard.sanitizeOutputText(account.getBankName()));
            v.setAccountMasked("****" + extractLast4(account.getAccountNo()));
        }
        if (tx.getTranAt() != null) {
            v.setTranDate(tx.getTranAt().format(YYYYMMDD));
            v.setTranTime(tx.getTranAt().format(HHMMSS));
            v.setTranAt(tx.getTranAt());
        }
        v.setInoutType(tx.getInoutType());
        v.setTranAmt(tx.getTranAmt());
        v.setAfterBalanceAmt(tx.getAfterBalanceAmt());
        v.setPrintContent(securityGuard.sanitizeOutputText(tx.getPrintContent()));
        v.setCounterpartyName(securityGuard.sanitizeOutputText(tx.getCounterpartyName()));
        v.setBranchName(securityGuard.sanitizeOutputText(tx.getBranchName()));
        return v;
    }

    private List<MockBankDto.MonthlyAggregate> aggregateMonthly(List<MockBankDto.MockTransactionView> txs) {
        TreeMap<String, long[]> buckets = new TreeMap<>();
        for (MockBankDto.MockTransactionView tx : txs) {
            if (tx.getTranDate() == null || tx.getTranDate().length() < 6 || tx.getTranAmt() == null) {
                continue;
            }
            String ym = tx.getTranDate().substring(0, 6);
            long[] acc = buckets.computeIfAbsent(ym, k -> new long[3]); // [deposit, withdraw, count]
            if ("입금".equals(tx.getInoutType())) {
                acc[0] += tx.getTranAmt();
            } else if ("출금".equals(tx.getInoutType())) {
                acc[1] += tx.getTranAmt();
            }
            acc[2]++;
        }

        List<MockBankDto.MonthlyAggregate> list = new ArrayList<>(buckets.size());
        for (Map.Entry<String, long[]> e : buckets.entrySet()) {
            long dep = e.getValue()[0];
            long wd = e.getValue()[1];
            MockBankDto.MonthlyAggregate m = new MockBankDto.MonthlyAggregate();
            m.setYearMonth(e.getKey());
            m.setTotalDepositAmt(dep);
            m.setTotalWithdrawAmt(wd);
            m.setNetAmt(dep - wd);
            m.setTxCount((int) e.getValue()[2]);
            list.add(m);
        }
        return list;
    }

    private KftcRecordDto.OAuthTokenRecord buildMemberToken(Long memberId, String scope) {
        KftcRecordDto.OAuthTokenRecord token = new KftcRecordDto.OAuthTokenRecord();
        token.setUserSeqNo(provisionService.deriveUserSeqNo(memberId));
        token.setScope(scope);
        token.setRevokedYn("N");
        token.setExpiresAt(LocalDateTime.now().plusHours(1));
        return token;
    }

    private String requireActiveMemberName(Long memberId) {
        if (memberId == null || memberId <= 0) {
            throw CodefException.unauthorized("OPENBANKING_AUTH_REQUIRED", "로그인이 필요합니다.");
        }
        String memberName = codefAccountMapper.findActiveMemberName(memberId);
        if (memberName == null || memberName.isBlank()) {
            throw CodefException.forbidden("OPENBANKING_MEMBER_NOT_FOUND", "활성 회원 정보를 찾을 수 없습니다.");
        }
        return memberName;
    }

    private static String extractLast4(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) {
            return "0000";
        }
        String digits = accountNo.replace("-", "").trim();
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }

    private static String safeIp(String ip) {
        return (ip == null || ip.isBlank()) ? "unknown" : ip;
    }
}
