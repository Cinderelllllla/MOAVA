package com.moava.proofs.dto;

import lombok.Data;

// 증빙 등록 검증용으로 GYE_GROUP에서 필요한 값만 담는 DTO
@Data
public class ProofGroupDto {
    private Long groupId;
    private Long ownerId;     // 모임장 (F23 승인 권한 확인에도 사용)
    private Long amount;      // 월 약정 납입액 (원)
    private Integer months;   // 운영 기간 → 회차 최대값
    private String status;    // RECRUITING / ACTIVE / DISSOLVE_PENDING / DISSOLVED
}