package com.moava.references.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * F24 참조번호 처리 결과 응답 (제안 / 지정 / 정정 공용). null인 필드는 JSON에서 빠짐
 *  - 제안: {refNo}
 *  - 지정: {proofId, refNo, refAssignedAt}
 *  - 정정: {proofId, refNo, refCorrectCount}
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RefNoResponse {
    private Long proofId;
    private String refNo;
    private OffsetDateTime refAssignedAt;
    private Integer refCorrectCount;

    public static RefNoResponse suggested(String refNo) {
        return new RefNoResponse(null, refNo, null, null);
    }

    public static RefNoResponse assigned(Long proofId, String refNo, OffsetDateTime refAssignedAt) {
        return new RefNoResponse(proofId, refNo, refAssignedAt, null);
    }

    public static RefNoResponse corrected(Long proofId, String refNo, int refCorrectCount) {
        return new RefNoResponse(proofId, refNo, null, refCorrectCount);
    }
}