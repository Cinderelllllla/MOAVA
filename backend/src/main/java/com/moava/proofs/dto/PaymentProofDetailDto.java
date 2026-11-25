package com.moava.proofs.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

// F22 증빙 상세 조회 응답 — 목록 항목(PaymentProofListDto) + 심사 시각 + 첨부 파일 목록
@Getter
@Setter
public class PaymentProofDetailDto extends PaymentProofListDto {
    private LocalDateTime reviewedAt;
    private List<ProofFileSummaryDto> files;   // 저장 경로·원본 파일명은 응답에 넣지 않음
}