package com.moava.openbanking.codef;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CODEF 개인 수시입출 실계좌인증 컨트롤러 (로그인한 MEMBER 전용)
 */
@RestController
@RequestMapping("/api/openbanking/codef")
@RequiredArgsConstructor
public class CodefBankController {

    private final CodefBankService codefBankService;

    // 1. 지원 은행 및 기관코드 목록 조회
    @GetMapping("/banks")
    public ResponseEntity<List<CodefDto.SupportedBank>> getSupportedBanks() {
        return secureOk(codefBankService.getSupportedBanks());
    }

    // 2. 내 은행 연결 및 실계좌인증 상태 목록 조회
    @GetMapping("/connections")
    public ResponseEntity<List<CodefDto.ConnectionSummary>> getMyConnections(Authentication authentication) {
        return secureOk(codefBankService.getMyConnections(resolveMemberId(authentication)));
    }

    // 3. 은행 계정 연결 (공동인증서 loginType=0 또는 은행 ID/PW loginType=1 → connectedId 발급)
    @PostMapping("/connect")
    public ResponseEntity<CodefDto.ConnectResponse> connect(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody(required = false) CodefDto.ConnectRequest body) {
        return secureOk(codefBankService.connectBankAccount(
                resolveMemberId(authentication), body, resolveClientIp(request)));
    }

    // 4. 연결된 은행 보유 계좌 목록 조회
    @PostMapping("/accounts")
    public ResponseEntity<CodefDto.AccountListResponse> accounts(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody(required = false) CodefDto.AccountListRequest body) {
        return secureOk(codefBankService.fetchAccountList(
                resolveMemberId(authentication), body, resolveClientIp(request)));
    }

    // 5. 개인 수시입출 거래내역 API 기반 실계좌 소유 및 예금주 실명 인증
    @PostMapping("/verify-account")
    public ResponseEntity<CodefDto.VerifyAccountResponse> verifyAccount(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody(required = false) CodefDto.VerifyAccountRequest body) {
        return secureOk(codefBankService.verifyRealAccount(
                resolveMemberId(authentication), body, resolveClientIp(request)));
    }

    // 6. 인증된 계좌의 실제 입출금 거래내역 조회
    @PostMapping("/transactions")
    public ResponseEntity<CodefDto.TransactionListResponse> transactions(
            Authentication authentication,
            HttpServletRequest request,
            @RequestBody(required = false) CodefDto.TransactionRequest body) {
        return secureOk(codefBankService.fetchTransactions(
                resolveMemberId(authentication), body, resolveClientIp(request)));
    }

    // 7. 은행 연결 해제
    @DeleteMapping("/connections/{organization}")
    public ResponseEntity<Map<String, Object>> disconnect(
            Authentication authentication,
            @PathVariable("organization") String organization) {
        codefBankService.disconnect(resolveMemberId(authentication), organization);
        return secureOk(Map.of("disconnected", true, "organization", organization));
    }

    private static Long resolveMemberId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw CodefException.unauthorized("CODEF_AUTH_REQUIRED", "로그인이 필요합니다.");
        }
        boolean isMember = authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_MEMBER".equals(a.getAuthority()));
        if (!isMember) {
            throw CodefException.forbidden("CODEF_MEMBER_ONLY", "일반 회원 계정으로 로그인한 경우에만 이용할 수 있습니다.");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw CodefException.unauthorized("CODEF_AUTH_INVALID", "유효하지 않은 회원 인증 정보입니다.");
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
