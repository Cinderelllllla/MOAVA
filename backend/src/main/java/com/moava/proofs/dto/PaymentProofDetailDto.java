package com.moava.proofs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

// F22 증빙 상세 조회 응답 (목록 항목 + 심사 시각 + 첨부 파일 목록)
@Getter
@Setter
public class PaymentProofDetailDto {
    private Long paymentProofId;
    private Long groupId;
    private String groupTitle;      // 모임명
    private Long memberId;
    private String memberName;      // 본인 외에는 마스킹
    private String payMonth;        // YYYYMM
    private Integer roundNo;
    private Long amount;
    private LocalDate payDate;
    private String status;          // PENDING / CONFIRMED / REVISION
    private String refNo;           // CONFIRMED 전에는 null
    private String reviewMemo;      // 보완 사유
    private Integer resubmitCount;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private List<ProofFileSummaryDto> files;   // 저장 경로·원본 파일명은 응답에 넣지 않음
}