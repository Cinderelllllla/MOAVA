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

}