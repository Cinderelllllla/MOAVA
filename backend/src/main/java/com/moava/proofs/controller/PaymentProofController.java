package com.moava.proofs.controller;

import com.moava.common.response.ApiResponse;
import com.moava.proofs.dto.PaymentProofCreateRequest;
import com.moava.proofs.dto.PaymentProofListDto;
import com.moava.proofs.dto.PaymentProofDetailDto;
import com.moava.proofs.dto.ProofRegisterResponse;
import com.moava.proofs.dto.ProofResubmitResponse;
import com.moava.proofs.dto.ProofReviewResponse;
import com.moava.proofs.dto.PaymentProofResubmitRequest;
import com.moava.proofs.dto.PaymentProofReviewRequest;
import com.moava.proofs.service.PaymentProofService;
import lombok.RequiredArgsConstructor;
import com.moava.proofs.dto.ProofFileContent;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;
import java.util.UUID;

/**
 * F22·F23 납입 증빙 API. 응답은 API 명세서 v0.5 §2 공통 형식(ApiResponse)
 * 성공: {"data": ..., "meta": {"requestId": "..."}} / 실패: {"error": {"code", "message"}, "requestId"}
 * 로그인 회원은 JWT 필터가 넣어 둔 principal(MEMBER_ID)로 확인 — 모임 생성 컨트롤러와 같은 방식
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
            Authentication authentication,
            @ModelAttribute PaymentProofCreateRequest req,
            @RequestPart("files") List<MultipartFile> files) {

        Long memberId = loginMemberId(authentication);
        req.setGroupId(groupId);   // 모임 ID는 URL 값을 기준으로 (요청 본문 값 무시)
        Long id = paymentProofService.registerProof(memberId, req, files);
        ProofRegisterResponse data = new ProofRegisterResponse(id, "PENDING", files.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data, requestId()));
    }

    /** F22-01 증빙 목록 (모임장: 모임 전체 / 회원: 본인 것만) */
    @GetMapping
    public ApiResponse<List<PaymentProofListDto>> getProofList(
            @PathVariable Long groupId,
            Authentication authentication) {
        Long memberId = loginMemberId(authentication);
        return ApiResponse.ok(paymentProofService.getProofList(groupId, memberId), requestId());
    }

    /** F22 증빙 상세 (모임장: 모임 전체 / 회원: 본인 것만) */
    @GetMapping("/{proofId}")
    public ApiResponse<PaymentProofDetailDto> getProofDetail(
            @PathVariable Long groupId,
            @PathVariable Long proofId,
            Authentication authentication) {
        Long memberId = loginMemberId(authentication);
        return ApiResponse.ok(paymentProofService.getProofDetail(groupId, proofId, memberId), requestId());
    }

    /**
     * F22 증빙 파일 미리보기·다운로드 (작성자·모임장만 — 팀 결정 대기, A안)
     * - ?download=true 이면 다운로드, 없으면 브라우저에서 바로 보기
     */
    @GetMapping("/{proofId}/files/{fileId}")
    public ResponseEntity<byte[]> getProofFile(
            @PathVariable Long groupId,
            @PathVariable Long proofId,
            @PathVariable Long fileId,
            @RequestParam(defaultValue = "false") boolean download,
            Authentication authentication) {

        Long memberId = loginMemberId(authentication);
        ProofFileContent file = paymentProofService.getProofFile(groupId, proofId, fileId, memberId);
        String disposition = (download ? "attachment" : "inline") + "; filename=\"" + file.getFileName() + "\"";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")   // 민감 파일이라 캐시 금지 (명세 권장)
                .body(file.getBytes());
    }
    /**
     * F23 증빙 승인·보완 요청 (모임장)
     * - 요청 본문(JSON): {"decision": "CONFIRMED" | "REVISION", "reviewMemo": "사유"}
     */
    @PatchMapping("/{proofId}/review")
    public ApiResponse<ProofReviewResponse> reviewProof(
            @PathVariable Long groupId,
            @PathVariable Long proofId,
            Authentication authentication,
            @RequestBody PaymentProofReviewRequest req) {

        Long memberId = loginMemberId(authentication);
        return ApiResponse.ok(paymentProofService.reviewProof(memberId, groupId, proofId, req), requestId());
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
            Authentication authentication,
            @ModelAttribute PaymentProofResubmitRequest req,
            @RequestPart("files") List<MultipartFile> files) {

        Long memberId = loginMemberId(authentication);
        int resubmitCount = paymentProofService.resubmitProof(memberId, groupId, proofId, req, files);
        ProofResubmitResponse data = new ProofResubmitResponse(proofId, "PENDING", resubmitCount);
        return ApiResponse.ok(data, requestId());
    }

    // ---------- 로그인 회원 ----------

    /** JWT 필터가 principal에 넣어 둔 MEMBER_ID(문자열)를 Long으로 변환. 없거나 형식이 틀리면 401 */
    private static Long loginMemberId(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
        }
    }

    // ---------- 오류 응답 (이 컨트롤러에서 난 예외만 처리 — 다른 기능에는 영향 없음) ----------

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

    /** 요청 값을 파라미터로 바꾸다 실패한 경우 (Controller 실행 전) → 400, 명세 §2 오류 형식 */
    @ExceptionHandler({
            MissingServletRequestPartException.class,   // files 파트 없음
            BindException.class,                        // 폼 값 형식 오류 (payDate, amount, roundNo 등)
            MethodArgumentTypeMismatchException.class,  // 경로 값이 숫자가 아님 (groupId=abc 등)
            HttpMessageNotReadableException.class       // 심사 요청 JSON 형식 오류
    })
    public ResponseEntity<ApiResponse.Error> handleBadRequest(Exception e) {
        String message;
        if (e instanceof MissingServletRequestPartException) {
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