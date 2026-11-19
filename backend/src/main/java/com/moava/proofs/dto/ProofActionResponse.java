package com.moava.proofs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * F22·F23 증빙 처리 결과 응답 (등록 / 재제출 / 심사 공용)
 * 기능마다 쓰는 값만 채우고, null인 필드는 JSON에서 빠짐 → 응답 모양은 명세 v0.5 그대로
 *  - 등록:   {proofId, status, fileCount}
 *  - 재제출: {proofId, status, resubmitCount}
 *  - 심사:   {proofId, status, reviewedAt}
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProofActionResponse {
    private Long proofId;
    private String status;
    private Integer fileCount;
    private Integer resubmitCount;
    private OffsetDateTime reviewedAt;

    public static ProofActionResponse registered(Long proofId, int fileCount) {
        return new ProofActionResponse(proofId, "PENDING", fileCount, null, null);
    }

    public static ProofActionResponse resubmitted(Long proofId, int resubmitCount) {
        return new ProofActionResponse(proofId, "PENDING", null, resubmitCount, null);
    }

    public static ProofActionResponse reviewed(Long proofId, String status, OffsetDateTime reviewedAt) {
        return new ProofActionResponse(proofId, status, null, null, reviewedAt);
    }
}