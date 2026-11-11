package com.moava.proofs.controller;


import com.moava.proofs.dto.PaymentProofCreateRequest;
import com.moava.proofs.service.PaymentProofService;
import lombok.RequiredArgsConstructor;
import com.moava.proofs.dto.PaymentProofListDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;
import java.util.Map;

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
    public ResponseEntity<Map<String, Long>> register(
            @PathVariable Long groupId,
            @RequestHeader("X-Member-Id") Long memberId,   // TODO: JWT 연동 후 로그인 정보로 교체 (임시)
            @ModelAttribute PaymentProofCreateRequest req,
            @RequestPart("files") List<MultipartFile> files) {

        req.setGroupId(groupId);   // 모임 ID는 URL 값을 기준으로 (요청 본문 값 무시)
        Long id = paymentProofService.registerProof(memberId, req, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("paymentProofId", id));
    }

    @GetMapping
    public ResponseEntity<List<PaymentProofListDto>> getProofList(
            @PathVariable Long groupId,
            @RequestHeader("X-Member-Id") Long memberId) {   // JWT 연동 전 임시 헤더
        return ResponseEntity.ok(paymentProofService.getProofList(groupId, memberId));
    }

}