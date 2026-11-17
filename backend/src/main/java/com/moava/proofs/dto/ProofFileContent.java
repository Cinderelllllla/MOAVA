package com.moava.proofs.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// F22 파일 미리보기·다운로드 결과 (Service → Controller)
@Getter
@AllArgsConstructor
public class ProofFileContent {
    private byte[] bytes;       // 복호화된 원본 파일
    private String mimeType;    // 저장된 MIME → 응답 Content-Type
    private String fileName;    // 응답용 이름 proof-{fileId}.png (원본 파일명·저장 경로는 노출 안 함)
}