package com.moava.references.controller;

import com.moava.common.response.ApiResponse;
import com.moava.references.dto.RefNoRequest;
import com.moava.references.dto.RefNoResponse;
import com.moava.references.dto.RefProofDto;
import com.moava.references.service.RefNoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * F24 거래 참조번호 관리 API (관리자, 화면 A05 /admin/references)
 * 응답은 API 명세서 v0.5 §2 공통 형식(ApiResponse)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/payment-proofs")
public class RefNoController {

    private final RefNoService refNoService;

    /** 목록 — ?ref=UNASSIGNED(미지정, 기본값) / ASSIGNED(지정 완료) */
    @GetMapping
    public ApiResponse<List<RefProofDto>> getProofs(
            @RequestParam(defaultValue = "UNASSIGNED") String ref,
            Authentication authentication) {
        return ApiResponse.ok(refNoService.getProofs(loginAdminId(authentication), ref), requestId());
    }

    /** 다음 참조번호 제안 (시퀀스 소모 없음) */
    @GetMapping("/ref-no/suggestion")
    public ApiResponse<RefNoResponse> suggestRefNo(Authentication authentication) {
        return ApiResponse.ok(refNoService.suggestRefNo(loginAdminId(authentication)), requestId());
    }

    /**
     * 참조번호 지정
     * - 요청 본문(JSON): {"refNo": "MOAVA-00012"} 또는 {"refNo": null}(서버가 다음 번호로 지정)
     */
    @PatchMapping("/{proofId}/ref-no")
    public ApiResponse<RefNoResponse> assignRefNo(
            @PathVariable Long proofId,
            @RequestBody(required = false) RefNoRequest req,
            Authentication authentication) {
        return ApiResponse.ok(refNoService.assignRefNo(loginAdminId(authentication), proofId, req), requestId());
    }

    /**
     * 참조번호 정정
     * - 요청 본문(JSON): {"refNo": "MOAVA-00015", "reason": "정정 사유"}
     */
    @PatchMapping("/{proofId}/ref-no/correction")
    public ApiResponse<RefNoResponse> correctRefNo(
            @PathVariable Long proofId,
            @RequestBody RefNoRequest req,
            Authentication authentication) {
        return ApiResponse.ok(refNoService.correctRefNo(loginAdminId(authentication), proofId, req), requestId());
    }

    // ---------- 로그인 관리자 ----------

    /**
     * JWT에서 ADMIN_ID 꺼내기 — 관리자 로그인(F41, Jira MOA-43) 연동 전이라 지금은 항상 403
     * 연동되면 이 메서드만 'role=ADMIN 확인 → subject(ADMIN_ID)를 Long으로 변환'으로 교체하면 됨
     */
    private static Long loginAdminId(Authentication authentication) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 로그인 연동 전입니다.");
    }

    // ---------- 오류 응답 (이 컨트롤러에서 난 예외만 처리) ----------

    /** Service의 ResponseStatusException → 명세 §2 오류 형식 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse.Error> handle(ResponseStatusException e) {
        String code = switch (e.getStatusCode().value()) {
            case 400 -> "INVALID_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "INVALID_STATE";
            default  -> "INTERNAL_ERROR";
        };
        String message = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(e.getStatusCode())
                .body(ApiResponse.Error.of(code, message, requestId()));
    }

    /** 경로 값이 숫자가 아니거나 JSON 형식이 깨진 경우 → 400 */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse.Error> handleBadRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.Error.of("INVALID_REQUEST", "요청 형식이 올바르지 않습니다.", requestId()));
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}