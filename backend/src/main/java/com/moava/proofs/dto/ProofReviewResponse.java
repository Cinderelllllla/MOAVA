package com.moava.proofs.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** F23 증빙 승인·보완 요청 응답 */
@Getter
@AllArgsConstructor
public class ProofReviewResponse {
    private Long proofId;
    private String status;
}