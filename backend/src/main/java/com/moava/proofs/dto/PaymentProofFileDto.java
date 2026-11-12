package com.moava.proofs.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PaymentProofFileDto {
    private Long fileId;
    private Long paymentProofId;
    private Integer sortNo;         // 1~5
    private String filePath;        // UUID 파일명
    private String originName;
    private String mimeType;
    private Long fileSize;          // 바이트, 1MB 이하
    private String fileHash;        // SHA-256 hex 64자
    private String encryptedYn;
    private Integer keyVer;
    private LocalDateTime createdAt;
}
