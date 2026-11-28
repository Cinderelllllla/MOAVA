package com.moava.admin.dashboard.mapper;

import com.moava.admin.dashboard.dto.AdminDashboardDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminDashboardMapper {

    // 0. 활성 관리자 검증
    int countActiveAdmin(@Param("adminId") Long adminId);

    // 1. 대시보드 4대 지표 및 최근 이력 7건
    AdminDashboardDTO.SummaryStats selectDashboardStats();
    List<AdminDashboardDTO.RecentAuditRow> selectRecentAuditLogs();

    // 2. 시연용 테스트 회원 5명 생성 관련
    int selectMaxTestMemberSuffix();
    int countMemberByEmail(@Param("email") String email);
    List<String> selectUnusedSynIds(@Param("count") int count);
    List<Long> selectActiveRequiredTermIds();

    // selectKey로 생성된 memberId가 param Map의 "memberId" 키에 자동 주입됨
    void insertTestMember(Map<String, Object> param);

    void insertMemberConsent(
            @Param("memberId") Long memberId,
            @Param("termsId") Long termsId,
            @Param("ipAddrEnc") String ipAddrEnc,
            @Param("keyVer") int keyVer
    );

    void insertAdminAuditLog(
            @Param("adminId") Long adminId,
            @Param("eventType") String eventType,
            @Param("description") String description
    );

    // 3. 데모 데이터 초기화 (FK 자식 → 부모 역순 삭제)
    void deleteAllNotifications();
    void deleteAllChatMessages();
    void deleteAllSupportReports();
    void deleteAllAuditLogs();

    void deleteAllOverdueCases();
    void deleteAllSanctions();
    void deleteAllDissolutionRequests();
    void deleteAllConsents();

    void deleteAllWithdrawalConsentTargets();
    void deleteAllWithdrawalRequests();
    void deleteAllPayoutRequests();
    void deleteAllPaymentProofFiles();
    void deleteAllPaymentProofs();
    void deleteAllJoinRequests();
    void deleteAllJoinCodeAttempts();

    void deleteAllLeaderTransferRequests();
    void deleteAllGyeAccounts();
    void deleteAllGyeMembers();
    void deleteAllGyeGroups();

    void deleteAllMockKftcTransactions();
    void deleteAllMockKftcAccounts();
    void deleteAllMockKftcOauthCodes();
    void deleteAllMockKftcOauthTokens();
    void deleteAllCodefConnections();

    void deleteAllRiskScores();
    void deleteAllFinanceMonthly();
    void deleteAllFinanceProfiles();
    void deleteAllMemberConsents();
    void deleteAllMemberSocialAccounts();
    void deleteAllMembers();
}