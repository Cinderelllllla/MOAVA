package com.moava.openbanking;

import com.moava.openbanking.OpenBankingDTO.*;
import com.moava.openbanking.service.OpenBankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/openbanking")
@RequiredArgsConstructor
public class OpenBankingController {

    private final OpenBankingService openBankingService;

    /**
     * 1. CODEF 계좌 정보 적재 API (목업 계좌 등록 및 초기 잔액 세팅)
     * POST /api/v1/openbanking/accounts/sync-codef
     */
    @PostMapping("/accounts/sync-codef")
    public ResponseEntity<MockAccountVO> syncCodefAccount(@RequestBody CodefSyncRequest request) {
        return ResponseEntity.ok(openBankingService.syncCodefAccount(request));
    }

    /**
     * 2. [토이 프로젝트 테스트용] 가상 계좌 잔액 자유 변경 API
     * PATCH /api/v1/openbanking/accounts/balance
     */
    @PatchMapping("/accounts/balance")
    public ResponseEntity<MockAccountVO> updateBalance(@RequestBody BalanceUpdateRequest request) {
        return ResponseEntity.ok(openBankingService.updateAccountBalanceForTest(request));
    }

    /**
     * 3. 계좌 실존 확인 및 사용자 실명 인증 API (금결원 /v2.0/inquiry/real_name 대체)
     * POST /api/v1/openbanking/inquiry/real-name
     */
    @PostMapping("/inquiry/real-name")
    public ResponseEntity<RealNameVerifyResponse> verifyRealName(@RequestBody RealNameVerifyRequest request) {
        return ResponseEntity.ok(openBankingService.verifyAccountAndRealName(request));
    }

    /**
     * 4. 모임장 계좌 전용 회비 이체 API (암호화/해시 검증 후 이체 + 납입내역 '확인중' 등록)
     * POST /api/v1/openbanking/groups/{groupId}/transfer
     */
    @PostMapping("/groups/{groupId}/transfer")
    public ResponseEntity<TransferToLeaderResponse> transferToLeader(
            @PathVariable("groupId") Long groupId,
            @RequestBody TransferToLeaderRequest request) {
        request.setGroupId(groupId);
        return ResponseEntity.ok(openBankingService.transferToGroupLeader(request));
    }

    /**
     * 5. 모임 납입/거래내역 조회 API (납입내역 탭 진입 시 호출)
     * GET /api/v1/openbanking/groups/{groupId}/transactions?payMonth=202611
     */
    @GetMapping("/groups/{groupId}/transactions")
    public ResponseEntity<List<GroupPaymentHistoryItem>> getGroupTransactions(
            @PathVariable("groupId") Long groupId,
            @RequestParam(value = "payMonth", required = false) String payMonth) {
        return ResponseEntity.ok(openBankingService.getGroupPaymentHistory(groupId, payMonth));
    }
}