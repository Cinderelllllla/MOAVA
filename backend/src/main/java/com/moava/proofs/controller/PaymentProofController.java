package com.moava.proofs.controller;

import com.moava.common.response.ApiResponse;
import com.moava.proofs.dto.PaymentProofCreateRequest;
import com.moava.proofs.dto.PaymentProofListDto;
import com.moava.proofs.dto.ProofRegisterResponse;
import com.moava.proofs.dto.ProofResubmitResponse;
import com.moava.proofs.dto.ProofReviewResponse;
import com.moava.proofs.dto.PaymentProofResubmitRequest;
import com.moava.proofs.dto.PaymentProofReviewRequest;
import com.moava.proofs.service.PaymentProofService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;
import java.util.UUID;

/**
 * F22·F23 납입 증빙 API. 응답은 API 명세서 v0.5 §2 공통 형식(ApiResponse)
 * 성공: {"data": ..., "meta": {"requestId": "..."}} / 실패: {"error": {"code", "message"}, "requestId"}
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/groups/{groupId}/payment-proofs")   // 화면 경로 S12 /groups/{id}/proofs 기준
public class PaymentProofController {

    private final PaymentProofService paymentProofService;

    /**
     * F22 납입 증빙 등록 (multipart/form-data)
     * - 폼 필드: payMonth, roundNo, amount, payDate
     * - 파일: files (1~5개)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProofRegisterResponse>> register(
            @PathVariable Long groupId,
            @RequestHeader("X-Member-Id") Long memberId,   // TODO: JWT 연동 후 로그인 정보로 교체 (임시)
            @ModelAttribute PaymentProofCreateRequest req,
            @RequestPart("files") List<MultipartFile> files) {

        req.setGroupId(groupId);   // 모임 ID는 URL 값을 기준으로 (요청 본문 값 무시)
        Long id = paymentProofService.registerProof(memberId, req, files);
        ProofRegisterResponse data = new ProofRegisterResponse(id, "PENDING", files.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data, requestId()));
    }

    /** F22-01 증빙 목록 (모임장: 모임 전체 / 회원: 본인 것만) */
    @GetMapping
    public ApiResponse<List<PaymentProofListDto>> getProofList(
            @PathVariable Long groupId,
            @RequestHeader("X-Member-Id") Long memberId) {   // TODO: JWT 연동 후 로그인 정보로 교체 (임시)
        return ApiResponse.ok(paymentProofService.getProofList(groupId, memberId), requestId());
    }

    /**
     * F23 증빙 승인·보완 요청 (모임장)
     * - 요청 본문(JSON): {"decision": "CONFIRMED" | "REVISION", "reviewMemo": "사유"}
     */
    @PatchMapping("/{proofId}/review")
    public ApiResponse<ProofReviewResponse> reviewProof(
            @PathVariable Long groupId,
            @PathVariable Long proofId,
            @RequestHeader("X-Member-Id") Long memberId,   // TODO: JWT 연동 후 로그인 정보로 교체 (임시)
            @RequestBody PaymentProofReviewRequest req) {

        String status = paymentProofService.reviewProof(memberId, groupId, proofId, req);
        return ApiResponse.ok(new ProofReviewResponse(proofId, status), requestId());
    }

    /**
     * F22-05 보완 재제출 (multipart/form-data)
     * - 폼 필드: payDate
     * - 파일: files (1~5개, 전체 교체)
     */
    @PutMapping(value = "/{proofId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProofResubmitResponse> resubmitProof(
            @PathVariable Long groupId,
            @PathVariable Long proofId,
            @RequestHeader("X-Member-Id") Long memberId,   // TODO: JWT 연동 후 로그인 정보로 교체 (임시)
            @ModelAttribute PaymentProofResubmitRequest req,
            @RequestPart("files") List<MultipartFile> files) {

        int resubmitCount = paymentProofService.resubmitProof(memberId, groupId, proofId, req, files);
        ProofResubmitResponse data = new ProofResubmitResponse(proofId, "PENDING", resubmitCount);
        return ApiResponse.ok(data, requestId());
    }

    // ---------- 오류 응답 (이 컨트롤러에서 난 예외만 처리 — 다른 기능에는 영향 없음) ----------

    /** Service의 ResponseStatusException → 명세 §2 오류 형식 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse.Error> handle(ResponseStatusException e) {
        String code = switch (e.getStatusCode().value()) {
            case 400 -> "INVALID_REQUEST";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "INVALID_STATE";
            default  -> "INTERNAL_ERROR";
        };
        String message = e.getReason() != null ? e.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(e.getStatusCode())
                .body(ApiResponse.Error.of(code, message, requestId()));
    }
    /** 요청 값을 파라미터로 바꾸다 실패한 경우 (Controller 실행 전) → 400, 명세 §2 오류 형식 */
    @ExceptionHandler({
            MissingRequestHeaderException.class,        // X-Member-Id 헤더 없음
            MissingServletRequestPartException.class,   // files 파트 없음
            BindException.class,                        // 폼 값 형식 오류 (payDate, amount, roundNo 등)
            MethodArgumentTypeMismatchException.class,  // 경로·헤더 값이 숫자가 아님 (groupId=abc 등)
            HttpMessageNotReadableException.class       // 심사 요청 JSON 형식 오류
    })

    public ResponseEntity<ApiResponse.Error> handleBadRequest(Exception e) {
        String message;
        if (e instanceof MissingRequestHeaderException) {
            message = "로그인 정보가 없습니다.";
        } else if (e instanceof MissingServletRequestPartException) {
            message = "증빙 파일을 첨부해 주세요.";
        } else if (e instanceof BindException) {
            message = "입력값 형식이 올바르지 않습니다. (납입일 YYYY-MM-DD, 금액·회차는 숫자)";
        } else {
            message = "요청 형식이 올바르지 않습니다.";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.Error.of("INVALID_REQUEST", message, requestId()));
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}