package com.moava.proofs.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

/** F23 증빙 승인·보완 요청 응답 (명세 v0.5 §5.5: proofId, status, reviewedAt) */
@Getter
@AllArgsConstructor
public class ProofReviewResponse {
    private Long proofId;
    private String status;
    private OffsetDateTime reviewedAt;   // 예: 2026-11-09T16:00:00+09:00
}