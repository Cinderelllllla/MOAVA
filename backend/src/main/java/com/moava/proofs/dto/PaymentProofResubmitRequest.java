package com.moava.proofs.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

// F22-05 보완 재제출 요청 값 (파일은 MultipartFile로 따로 받음)
// 귀속 월·회차·금액은 기존 값 유지 → 실제 납입일만 다시 받음
@Data
public class PaymentProofResubmitRequest {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate payDate;  // 실제 납입일
}