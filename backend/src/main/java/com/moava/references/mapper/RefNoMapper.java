package com.moava.references.mapper;

import com.moava.references.dto.RefProofDto;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefNoMapper {

    // ACTIVE 관리자인지 (1이면 관리자)
    int countActiveAdmin(@Param("adminId") Long adminId);

    // A05 목록: assigned=false → 참조번호 미지정, true → 지정 완료
    List<RefProofDto> findProofs(@Param("assigned") boolean assigned);

    // 증빙 1건 (없으면 null)
    RefProofDto findProof(@Param("proofId") Long proofId);

    // 다음 참조번호 미리 보기 (시퀀스를 소모하지 않음)
    String peekNextRefNo();

    // 다음 참조번호 발급 (SEQ_REF_NO.NEXTVAL 소모)
    String nextRefNo();

    // 같은 참조번호가 이미 있는지 (UK_PAYMENT_PROOF_REF_NO)
    int countRefNo(@Param("refNo") String refNo);

    // 지정: CONFIRMED이고 아직 번호가 없을 때만 → 0이면 상태가 바뀌었거나 이미 지정된 것
    int assignRefNo(@Param("proofId") Long proofId,
                    @Param("refNo") String refNo,
                    @Param("assignedAt") LocalDateTime assignedAt);

    // 정정: 현재 번호가 oldRefNo일 때만 바꾸고 정정 횟수 +1 → 0이면 다른 요청이 먼저 바꾼 것
    int correctRefNo(@Param("proofId") Long proofId,
                     @Param("oldRefNo") String oldRefNo,
                     @Param("newRefNo") String newRefNo);

    // 관리자 행위 감사 로그 (ACTOR_ADMIN_ID)
    int insertAdminAuditLog(@Param("groupId") Long groupId,
                            @Param("actorAdminId") Long actorAdminId,
                            @Param("targetMemberId") Long targetMemberId,
                            @Param("eventType") String eventType,
                            @Param("description") String description);
}