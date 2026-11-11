package com.moava.proofs.mapper;

import com.moava.proofs.dto.PaymentProofDto;
import com.moava.proofs.dto.PaymentProofFileDto;
import com.moava.proofs.dto.PaymentProofListDto;
import com.moava.proofs.dto.ProofGroupDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
    // excludeProofId: 재제출 시 본인 증빙의 기존 파일은 제외 (등록 시 null)
    int countFileHash(@Param("fileHash") String fileHash,
                      @Param("excludeProofId") Long excludeProofId);

    // 증빙 저장 → 실행 후 dto.paymentProofId에 시퀀스 값이 채워짐
    int insertProof(PaymentProofDto dto);

    // 증빙 파일 메타 저장
    int insertProofFile(PaymentProofFileDto dto);

    List<PaymentProofListDto> findProofList(@Param("groupId") Long groupId,
                                            @Param("memberId") Long memberId);

    // F23: 심사할 증빙 1건 조회 (경로의 groupId와 일치하는 것만, 없으면 null)
    PaymentProofDto findProofForReview(@Param("groupId") Long groupId,
                                       @Param("proofId") Long proofId);

    // F23: 심사 결과 반영 (PENDING일 때만 변경 → 반환값 0이면 이미 처리된 것)
    int updateProofReview(@Param("proofId") Long proofId,
                          @Param("status") String status,
                          @Param("reviewedBy") Long reviewedBy,
                          @Param("reviewMemo") String reviewMemo);

    // F22-05: 재제출 시 지울 기존 파일 경로 목록
    List<String> findProofFilePaths(@Param("proofId") Long proofId);

    // F22-05: 기존 파일 메타 삭제 (전체 교체 방식)
    int deleteProofFiles(@Param("proofId") Long proofId);

    // F22-05: REVISION → PENDING, 납입일 갱신, 재제출 횟수 +1 (REVISION일 때만 → 0이면 이미 처리됨)
    int updateProofResubmit(@Param("proofId") Long proofId,
                            @Param("payDate") java.time.LocalDate payDate);
}
