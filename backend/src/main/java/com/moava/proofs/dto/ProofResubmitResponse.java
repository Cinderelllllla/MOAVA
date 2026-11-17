package com.moava.proofs.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** F22-05 보완 재제출 응답 */
@Getter
@AllArgsConstructor
public class ProofResubmitResponse {
    private Long proofId;
    private String status;
    private int resubmitCount;
}