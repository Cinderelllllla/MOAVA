package com.moava.mockbank;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mock-bank/v1")
@RequiredArgsConstructor
public class MockBankController {

    private final MockBankService mockBankService;

    // 1. 인증서 발급 (공개키 등록)
    @PostMapping("/certificates")
    public ResponseEntity<MockBankDto.CertIssueResponse> registerCertificate(
            @RequestBody MockBankDto.CertRegisterRequest req) {
        return ResponseEntity.ok(mockBankService.registerCertificate(req));
    }

    // 2. 전자서명용 Challenge 발급
    @PostMapping("/auth/challenge")
    public ResponseEntity<MockBankDto.ChallengeResponse> issueChallenge(
            @RequestBody MockBankDto.ChallengeRequest req) {
        return ResponseEntity.ok(mockBankService.issueChallenge(req));
    }

    // 3. 전자서명 검증 및 토큰 발급
    @PostMapping("/auth/verify")
    public ResponseEntity<MockBankDto.TokenResponse> verifySignature(
            @RequestBody MockBankDto.VerifySignatureRequest req) {
        return ResponseEntity.ok(mockBankService.verifyAndIssueToken(req));
    }

    // 4. 거래내역 조회
    @GetMapping("/transactions")
    public ResponseEntity<List<MockBankDto.TransactionDto>> getTransactions(
            @RequestHeader("X-Mock-Bank-Token") String tokenId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(mockBankService.getTransactions(tokenId, from, to));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler({IllegalStateException.class, SecurityException.class})
    public ResponseEntity<Map<String, String>> handleUnauthorized(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
    }
}