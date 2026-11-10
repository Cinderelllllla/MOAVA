package com.moava.proofs.mapper;

import com.moava.proofs.dto.PaymentProofDto;
import com.moava.proofs.dto.PaymentProofFileDto;
import com.moava.proofs.dto.ProofGroupDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentProofMapper {

    // 모임 기본 정보 (없으면 null)
    ProofGroupDto findGroupForProof(@Param("groupId") Long groupId);

    // 해당 모임의 ACTIVE 구성원인지 (1이면 구성원)
    int countActiveMember(@Param("groupId") Long groupId,
                          @Param("memberId") Long memberId);

    // 같은 모임·회원·귀속 월·회차 증빙 존재 여부 (UK_PAYMENT_PROOF_1)
    int countDuplicateProof(@Param("groupId") Long groupId,
                            @Param("memberId") Long memberId,
                            @Param("payMonth") String payMonth,
                            @Param("roundNo") Integer roundNo);

    // 같은 파일 해시가 이미 등록됐는지 (REQ-SEC-02)
    int countFileHash(@Param("fileHash") String fileHash);

    // 증빙 저장 → 실행 후 dto.paymentProofId에 시퀀스 값이 채워짐
    int insertProof(PaymentProofDto dto);

    // 증빙 파일 메타 저장
    int insertProofFile(PaymentProofFileDto dto);
}