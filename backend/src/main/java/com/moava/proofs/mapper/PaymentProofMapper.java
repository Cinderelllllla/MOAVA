package com.moava.proofs.mapper;

import com.moava.proofs.dto.PaymentStatusResponse;
import com.moava.proofs.dto.PaymentProofDto;
import com.moava.proofs.dto.PaymentProofFileDto;
import com.moava.proofs.dto.PaymentProofListDto;
import com.moava.proofs.dto.ProofGroupDto;
import com.moava.proofs.dto.PaymentProofDetailDto;
import com.moava.proofs.dto.ProofFileSummaryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentProofMapper {

    // 모임 기본 정보 (없으면 null)
    ProofGroupDto findGroupForProof(@Param("groupId") Long groupId);
    Long lockGroupForProofUpdate(@Param("groupId") Long groupId);

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
                          @Param("reviewMemo") String reviewMemo,
                          @Param("reviewedAt") LocalDateTime reviewedAt);

    // F22-05: 재제출 시 지울 기존 파일 경로 목록
    List<String> findProofFilePaths(@Param("proofId") Long proofId);

    // F22-05: 기존 파일 메타 삭제 (전체 교체 방식)
    int deleteProofFiles(@Param("proofId") Long proofId);

    // F22-05: REVISION → PENDING, 납입일 갱신, 재제출 횟수 +1 (REVISION일 때만 → 0이면 이미 처리됨)
    int updateProofResubmit(@Param("proofId") Long proofId,
                            @Param("payDate") java.time.LocalDate payDate);

    // 알림: 오늘 이 모임 증빙으로 모임장에게 PROOF_PENDING을 보낸 건수 (같은 모임 하루 1건 규칙)
    int countTodayProofPending(@Param("ownerId") Long ownerId,
                               @Param("groupId") Long groupId);

    // F23: 승인 시 같은 회원·귀속 월 미납 건 해소 (이미 RESOLVED인 건은 건드리지 않음)
    int resolveOverdueCase(@Param("groupId") Long groupId,
                           @Param("memberId") Long memberId,
                           @Param("payMonth") String payMonth);


    // 감사 로그 1건 저장 (AUDIT_LOG는 append-only — INSERT만)
    int insertAuditLog(@Param("groupId") Long groupId,
                       @Param("actorMemberId") Long actorMemberId,
                       @Param("targetMemberId") Long targetMemberId,
                       @Param("eventType") String eventType,
                       @Param("description") String description);


    // F22 상세: 증빙 1건 (경로의 groupId 소속인 것만, 없으면 null)
    PaymentProofDetailDto findProofDetail(@Param("groupId") Long groupId,
                                          @Param("proofId") Long proofId);

    // F22 상세: 첨부 파일 요약 목록 (SORT_NO 순)
    List<ProofFileSummaryDto> findProofFileSummaries(@Param("proofId") Long proofId);

    // F22 파일 미리보기: 이 증빙에 속한 파일 1건 (없으면 null)
    PaymentProofFileDto findProofFile(@Param("proofId") Long proofId,
                                      @Param("fileId") Long fileId);
    // F25: 귀속 월 기준 모임 ACTIVE 회원별 납입 상태 (CONFIRMED / PENDING / REVISION / NONE) + 경고 횟수
    List<PaymentStatusResponse.MemberStatus> findMemberPaymentStatus(@Param("groupId") Long groupId,
                                                                     @Param("payMonth") String payMonth);
}
