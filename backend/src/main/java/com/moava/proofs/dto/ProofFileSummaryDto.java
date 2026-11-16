package com.moava.proofs.dto;

import lombok.Getter;
import lombok.Setter;

// 증빙 상세의 첨부 파일 요약 (저장 경로·원본 파일명 제외 — 명세 F22-10)
@Getter
@Setter
public class ProofFileSummaryDto {
    private Long fileId;
    private Integer sortNo;
    private String mimeType;
    private Long fileSize;
}