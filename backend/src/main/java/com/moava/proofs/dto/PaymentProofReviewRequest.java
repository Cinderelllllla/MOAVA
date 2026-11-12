package com.moava.proofs.dto;

import lombok.Data;

// F23 증빙 승인·보완 요청 값 (JSON 본문)
@Data
public class PaymentProofReviewRequest {
    private String decision;    // CONFIRMED(승인) 또는 REVISION(보완 요청)
    private String reviewMemo;  // 보완 사유 (REVISION일 때 필수, 승인이면 null)
}