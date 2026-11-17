package com.moava.proofs.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentProofListDto {
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
    private Integer fileCount;
    private LocalDateTime createdAt;
}