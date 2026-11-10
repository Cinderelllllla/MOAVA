package com.moava.proofs.dto;

import lombok.Data;
import java.time.LocalDate;

// F22 증빙 등록 요청 값 (파일은 MultipartFile로 따로 받음)
@Data
public class PaymentProofCreateRequest {
    private Long groupId;
    private String payMonth;    // 귀속 월 'YYYYMM' (예: 202611)
    private Integer roundNo;    // 납입 회차 1 ~ 운영 기간
    private Long amount;        // 납입액 (원 단위)
    private LocalDate payDate;  // 실제 납입일
}