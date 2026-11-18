package com.moava.proofs.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** F22 증빙 등록 응답 */
@Getter
@AllArgsConstructor
public class ProofRegisterResponse {
    private Long proofId;
    private String status;
    private int fileCount;
}