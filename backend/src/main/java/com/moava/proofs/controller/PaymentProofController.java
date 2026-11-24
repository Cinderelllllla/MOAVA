package com.moava.proofs.controller;

import com.moava.common.response.ApiResponse;
import com.moava.proofs.dto.*;
import com.moava.proofs.service.PaymentProofService;
import com.moava.proofs.service.ProofQueryService;
import com.moava.proofs.service.ProofReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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
    private final ProofQueryService proofQueryService;       // F22 목록·상세·파일 조회
    private final ProofReviewService proofReviewService;     // F23 승인·보완 요청

    /**
     * F22 납입 증빙 등록 (multipart/form-data)
     * - 폼 필드: payMonth, roundNo, amount, payDate
     * - 파일: files (1~5개)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProofActionResponse>> register(
            @PathVariable Long groupId,
            Authentication authentication,
            @ModelAttribute PaymentProofCreateRequest req,
            @RequestPart("files") List<MultipartFile> files) {

        Long memberId = loginMemberId(authentication);
        req.setGroupId(groupId);   // 모임 ID는 URL 값을 기준으로 (요청 본문 값 무시)
        Long id = paymentProofService.registerProof(memberId, req, files);
        ProofActionResponse data = ProofActionResponse.registered(id, files.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data, requestId()));
    }

    /** F22-01 증빙 목록 (모임장: 모임 전체 / 회원: 본인 것만) */
    @GetMapping
    public ApiResponse<List<PaymentProofListDto>> getProofList(
            @PathVariable Long groupId,
            Authentication authentication) {
        Long memberId = loginMemberId(authentication);
        return ApiResponse.ok(proofQueryService.getProofList(groupId, memberId), requestId());
    }

    /** F22 증빙 상세 (모임장: 모임 전체 / 회원: 본인 것만) */
    @GetMapping("/{proofId}")
    public ApiResponse<PaymentProofDetailDto> getProofDetail(
            @PathVariable Long groupId,
            @PathVariable Long proofId,
            Authentication authentication) {
        Long memberId = loginMemberId(authentication);
        return ApiResponse.ok(proofQueryService.getProofDetail(groupId, proofId, memberId), requestId());
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
        ProofFileContent file = proofQueryService.getProofFile(groupId, proofId, fileId, memberId);
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
    public ApiResponse<ProofActionResponse> reviewProof(
            @PathVariable Long groupId,
            @PathVariable Long proofId,
            Authentication authentication,
            @RequestBody PaymentProofReviewRequest req) {

        Long memberId = loginMemberId(authentication);
        return ApiResponse.ok(proofReviewService.reviewProof(memberId, groupId, proofId, req), requestId());
    }

    /**
     * F22-05 보완 재제출 (multipart/form-data)
     * - 폼 필드: payDate
     * - 파일: files (1~5개, 전체 교체)
     */
    @PutMapping(value = "/{proofId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProofActionResponse> resubmitProof(
            @PathVariable Long groupId,
            @PathVariable Long proofId,
            Authentication authentication,
            @RequestParam(value = "payDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payDate,
            @RequestPart("files") List<MultipartFile> files) {

        Long memberId = loginMemberId(authentication);
        int resubmitCount = paymentProofService.resubmitProof(memberId, groupId, proofId, payDate, files);
        return ApiResponse.ok(ProofActionResponse.resubmitted(proofId, resubmitCount), requestId());
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

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}