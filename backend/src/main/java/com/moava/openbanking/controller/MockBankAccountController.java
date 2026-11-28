package com.moava.openbanking.controller;

import com.moava.openbanking.codef.CodefException;
import com.moava.openbanking.dto.MockBankDto;
import com.moava.openbanking.service.MockBankAccountService;
import com.moava.openbanking.service.MockBankQuickConnectService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 프론트엔드 연동용 모의 오픈뱅킹 계좌·거래내역·회비이체 컨트롤러 (로그인한 MOAVA MEMBER 전용).
 * /api/** 경로로 노출되어 기존 Spring Security JWT 인증 및 Vite /api 프록시와 즉시 연동된다.
 */
@RestController
@RequestMapping("/api/openbanking/accounts")
@RequiredArgsConstructor
public class MockBankAccountController {

    private final MockBankAccountService mockBankAccountService;
    private final MockBankQuickConnectService quickConnectService;

    // 0-1. 간편 은행 연결 시작 (은행명·계좌번호·소유주·전화번호 -> 가상은행 1원 인증 세션 발급)
    @PostMapping("/quick-connect/start")
    public ResponseEntity<MockBankDto.QuickConnectSessionResponse> quickConnectStart(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody(required = false) MockBankDto.QuickConnectStartRequest body) {
        return secureOk(quickConnectService.startQuickConnect(
                resolveMemberId(authentication), body, resolveClientIp(request)));
    }

    // 0-2. 가상은행 인증 화면 세션 조회
    @GetMapping("/quick-connect/{sessionToken}")
    public ResponseEntity<MockBankDto.QuickConnectSessionResponse> quickConnectSession(
            Authentication authentication,
            @PathVariable("sessionToken") String sessionToken) {
        return secureOk(quickConnectService.getSession(resolveMemberId(authentication), sessionToken));
    }

    // 0-3. 가상은행 1원 인증번호 4자리 재발송
    @PostMapping("/quick-connect/{sessionToken}/resend")
    public ResponseEntity<MockBankDto.QuickConnectSessionResponse> quickConnectResend(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable("sessionToken") String sessionToken) {
        return secureOk(quickConnectService.resendAuthCode(
                resolveMemberId(authentication), sessionToken, resolveClientIp(request)));
    }

    // 0-4. 가상은행 1원 인증번호 4자리 검증 + 오픈뱅킹 동의 완료
    @PostMapping("/quick-connect/{sessionToken}/verify")
    public ResponseEntity<MockBankDto.QuickConnectVerifyResponse> quickConnectVerify(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable("sessionToken") String sessionToken,
            @RequestBody(required = false) MockBankDto.QuickConnectVerifyRequest body) {
        return secureOk(quickConnectService.verifyAndComplete(
                resolveMemberId(authentication), sessionToken, body, resolveClientIp(request)));
    }

    // 1. 프론트 오픈뱅킹 화면 통합 초기 로딩 (CODEF 인증 현황 + 모의 계좌 + 최근 거래 + 월별 집계 + 모임장 수취 계좌)
    @GetMapping("/overview")
    public ResponseEntity<MockBankDto.OverviewResponse> overview(Authentication authentication) {
        return secureOk(mockBankAccountService.getOverview(resolveMemberId(authentication)));
    }

    // 2. 내 모의 은행 계좌 목록 조회
    @GetMapping
    public ResponseEntity<List<MockBankDto.MockAccountView>> myAccounts(Authentication authentication) {
        return secureOk(mockBankAccountService.getMyAccounts(resolveMemberId(authentication)));
    }

    // 3. 이미 인증된 CODEF 실계좌를 모의 은행 원장에 (재)적재/동기화
    @PostMapping("/sync")
    public ResponseEntity<MockBankDto.ProvisionResult> syncFromCodef(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody(required = false) MockBankDto.SyncFromCodefRequest body) {
        return secureOk(mockBankAccountService.syncFromCodef(
                resolveMemberId(authentication), body, resolveClientIp(request)));
    }

    // 4. 내 모의 계좌 잔액 조회
    @GetMapping("/{fintechUseNum}/balance")
    public ResponseEntity<MockBankDto.MockAccountView> balance(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable("fintechUseNum") String fintechUseNum) {
        return secureOk(mockBankAccountService.getAccountBalance(
                resolveMemberId(authentication), fintechUseNum, resolveClientIp(request)));
    }

    // 5. 내 모의 계좌 거래내역 및 월별 집계 조회
    @GetMapping("/{fintechUseNum}/transactions")
    public ResponseEntity<MockBankDto.AccountTransactionsResponse> transactions(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable("fintechUseNum") String fintechUseNum,
            @RequestParam(value = "inquiryType", required = false, defaultValue = "A") String inquiryType,
            @RequestParam(value = "fromDate", required = false) String fromDate,
            @RequestParam(value = "toDate", required = false) String toDate,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "D") String sortOrder,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return secureOk(mockBankAccountService.getAccountTransactions(
                resolveMemberId(authentication),
                fintechUseNum,
                inquiryType,
                fromDate,
                toDate,
                sortOrder,
                limit,
                resolveClientIp(request)
        ));
    }

    // 6. 본인 모의 계좌의 모임장 수취 계좌 여부(LEADER_YN) 설정
    @PatchMapping("/{fintechUseNum}/leader")
    public ResponseEntity<MockBankDto.MockAccountView> updateLeader(
            Authentication authentication,
            @PathVariable("fintechUseNum") String fintechUseNum,
            @RequestBody(required = false) MockBankDto.UpdateLeaderRequest body) {
        if (body == null || body.getLeader() == null) {
            throw CodefException.badRequest("MOCK_BANK_LEADER_REQUIRED", "leader(true/false) 값은 필수입니다.");
        }
        return secureOk(mockBankAccountService.updateLeaderFlag(
                resolveMemberId(authentication), fintechUseNum, body.getLeader()));
    }

    // 7. 시연용 모의 자금 충전
    @PostMapping("/{fintechUseNum}/deposit-demo")
    public ResponseEntity<MockBankDto.DemoDepositResponse> depositDemo(
            Authentication authentication,
            HttpServletRequest request,
            @PathVariable("fintechUseNum") String fintechUseNum,
            @RequestBody(required = false) MockBankDto.DemoDepositRequest body) {
        return secureOk(mockBankAccountService.depositDemoFunds(
                resolveMemberId(authentication), fintechUseNum, body, resolveClientIp(request)));
    }

    // 8. 모의 회비 이체 (본인 모의계좌 -> 모임장 수취 계좌)
    @PostMapping("/transfer")
    public ResponseEntity<MockBankDto.MemberTransferResponse> transfer(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody(required = false) MockBankDto.MemberTransferRequest body) {
        return secureOk(mockBankAccountService.transferToLeader(
                resolveMemberId(authentication), body, resolveClientIp(request)));
    }

    // 9. 이체 가능한 모임장 수취 계좌 목록 조회 (내 참여 모임 우선)
    @GetMapping("/leader-targets")
    public ResponseEntity<List<MockBankDto.LeaderTargetView>> leaderTargets(Authentication authentication) {
        return secureOk(mockBankAccountService.getLeaderTargets(resolveMemberId(authentication)));
    }

    private static Long resolveMemberId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw CodefException.unauthorized("OPENBANKING_AUTH_REQUIRED", "로그인이 필요합니다.");
        }
        boolean isMember = authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_MEMBER".equals(a.getAuthority()));
        if (!isMember) {
            throw CodefException.forbidden("OPENBANKING_MEMBER_ONLY", "일반 회원 계정으로 로그인한 경우에만 이용할 수 있습니다.");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw CodefException.unauthorized("OPENBANKING_AUTH_INVALID", "유효하지 않은 회원 인증 정보입니다.");
        }
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
