package com.moava.references.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// F24 참조번호 관리 화면(A05)의 증빙 1건 — 목록 응답과 단건 조회(지정·정정 검증)에 함께 사용
@Getter
@Setter
public class RefProofDto {
    private Long paymentProofId;
    private Long groupId;
    private String groupTitle;          // 모임명
    private Long memberId;
    private String memberName;          // 응답 전에 마스킹 (F03)
    private String payMonth;            // YYYYMM
    private Integer roundNo;
    private Long amount;
    private LocalDate payDate;
    private String status;              // PENDING / CONFIRMED / REVISION
    private String refNo;               // 미지정이면 null
    private LocalDateTime refAssignedAt;
    private Integer refCorrectCount;    // 정정 횟수 ('정정 이력 n회' 배지)
    private Integer fileCount;
}