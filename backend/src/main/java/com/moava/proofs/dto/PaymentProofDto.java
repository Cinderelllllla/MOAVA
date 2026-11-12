package com.moava.proofs.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter   // MyBatis가 setter로 값을 채움
@Setter
public class PaymentProofDto {
    private Long paymentProofId;
    private Long groupId;
    private Long memberId;
    private String payMonth;        // CHAR(6) 'YYYYMM'
    private Integer roundNo;
    private Long amount;            // 원 단위
    private LocalDate payDate;
    private String status;          // PENDING / CONFIRMED / REVISION
    private Integer resubmitCount;
    private String refNo;
    private LocalDateTime refAssignedAt;
    private Integer refCorrectCount;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewMemo;
    private LocalDateTime createdAt;
}