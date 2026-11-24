package com.moava.overdue.mapper;

import com.moava.overdue.dto.OverdueCaseDto;
import com.moava.overdue.dto.OverdueTargetDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface OverdueMapper {

    // 새로 감지할 미납: 경과일 ≥ minDays + 그 달 CONFIRMED 증빙 없음 + 아직 OVERDUE_CASE에 없음
    List<OverdueTargetDto> findNewOverdueTargets(@Param("today") LocalDate today, @Param("minDays") int minDays);

    // 회원 납입 안내 대상: 경과일이 딱 days일 + 그 달 CONFIRMED 증빙 없음 (1일 차·5일 차 안내용)
    List<OverdueTargetDto> findUnpaidOnDay(@Param("today") LocalDate today, @Param("days") int days);

    // 미납 건 저장 (DETECTED, 관리자 알림 시각 기록) — 저장 후 target.overdueCaseId가 채워짐
    int insertOverdueCase(OverdueTargetDto target);

    // 감사 로그 — 배치라 행위자(ACTOR_*) 없음
    int insertAuditLog(@Param("groupId") Long groupId,
                       @Param("targetMemberId") Long targetMemberId,
                       @Param("eventType") String eventType,
                       @Param("description") String description);
    // A06 관리자 미납 목록 — status: OPEN(미해소) / RESOLVED(해소) / ALL
    List<OverdueCaseDto> findOverdueCases(@Param("status") String status);

    // ACTIVE 관리자인지 (1이면 관리자)
    int countActiveAdmin(@Param("adminId") Long adminId);

    // 조치 대상 미납 건 1건 (모임 미납 정책·회원 채팅 제한 여부 포함)
    OverdueCaseDto findOverdueCaseById(@Param("overdueCaseId") Long overdueCaseId);

    // 경고: DETECTED일 때만 WARNED로 (0건이면 이미 경고했거나 상태가 바뀜)
    int updateCaseWarned(@Param("overdueCaseId") Long overdueCaseId);

    // 채팅 제한: DETECTED·WARNED → RESTRICTED (이미 RESTRICTED면 0건, 그대로 둠)
    int updateCaseRestricted(@Param("overdueCaseId") Long overdueCaseId);

    // 회원 채팅 제한 여부 변경 — 지금 값이 from일 때만 to로 (0건이면 이미 그 상태이거나 모임 회원이 아님)
    int updateChatRestricted(@Param("groupId") Long groupId,
                             @Param("memberId") Long memberId,
                             @Param("from") String from,
                             @Param("to") String to);

    // 제재 이력 번호 미리 받기 (감사 로그에 'SANCTION #번호'로 남기려고)
    Long nextSanctionId();

    // 관리자 제재 이력 (CHAT_RESTRICT / CHAT_RELEASE / ORDER_REVIEW)
    int insertSanction(@Param("sanctionId") Long sanctionId,
                       @Param("sanctionType") String sanctionType,
                       @Param("memberId") Long memberId,
                       @Param("groupId") Long groupId,
                       @Param("adminId") Long adminId,
                       @Param("reason") String reason);

    // 관리자 행위 감사 로그 (ACTOR_ADMIN_ID 기록)
    int insertAdminAuditLog(@Param("groupId") Long groupId,
                            @Param("adminId") Long adminId,
                            @Param("targetMemberId") Long targetMemberId,
                            @Param("eventType") String eventType,
                            @Param("description") String description);
}