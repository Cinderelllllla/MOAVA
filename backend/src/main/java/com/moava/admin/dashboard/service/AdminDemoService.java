package com.moava.admin.dashboard.service;

import com.moava.admin.dashboard.dto.AdminDashboardDTO;
import com.moava.admin.dashboard.mapper.AdminDashboardMapper;
import com.moava.crypto.service.CryptoKeyService;
import com.moava.group.search.GroupSearchSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Profile("!prod")
@Service
public class AdminDemoService {

    private static final int TEST_MEMBER_COUNT = 5;
    private static final String[][] SAMPLE_REGIONS = {
            {"서울", "마포구"},
            {"서울", "강남구"},
            {"서울", "성동구"},
            {"서울", "송파구"},
            {"서울", "영등포구"}
    };

    private final AdminDashboardMapper dashboardMapper;
    private final CryptoKeyService cryptoKeyService;
    private final PasswordEncoder passwordEncoder;
    private final GroupSearchSyncService groupSearchSyncService;

    @Autowired
    public AdminDemoService(
            AdminDashboardMapper dashboardMapper,
            CryptoKeyService cryptoKeyService,
            PasswordEncoder passwordEncoder,
            GroupSearchSyncService groupSearchSyncService) {
        this.dashboardMapper = dashboardMapper;
        this.cryptoKeyService = cryptoKeyService;
        this.passwordEncoder = passwordEncoder;
        this.groupSearchSyncService = groupSearchSyncService;
    }

    public AdminDemoService(
            AdminDashboardMapper dashboardMapper,
            CryptoKeyService cryptoKeyService,
            PasswordEncoder passwordEncoder) {
        this(dashboardMapper, cryptoKeyService, passwordEncoder, null);
    }

    /**
     * F37-3: 시연용 테스트 회원 5명 생성 (이름·이메일 접미 번호 중복 방지 + SYN_ID 매핑 + 필수 약관 동의)
     */
    @Transactional
    public AdminDashboardDTO.TestMembersResponse createFiveTestMembers(Long adminId) {
        requireActiveAdmin(adminId);

        List<String> unusedSynIds = dashboardMapper.selectUnusedSynIds(TEST_MEMBER_COUNT);
        List<Long> requiredTermIds = dashboardMapper.selectActiveRequiredTermIds();
        int activeKeyVer = cryptoKeyService.getCurrentKeyVersion();
        String encodedPassword = passwordEncoder.encode("Test1234!");
        String demoIpEnc = cryptoKeyService.encrypt("127.0.0.1", activeKeyVer);

        int suffix = dashboardMapper.selectMaxTestMemberSuffix();
        List<String> createdEmails = new ArrayList<>();

        for (int i = 0; i < TEST_MEMBER_COUNT; i++) {
            String email;
            do {
                suffix++;
                email = "testuser" + suffix + "@moava.test";
            } while (dashboardMapper.countMemberByEmail(email) > 0);

            String name = "테스트회원" + suffix;
            String rawPhone = String.format("0109000%04d", suffix % 10000);
            String phoneEnc = cryptoKeyService.encrypt(rawPhone, activeKeyVer);
            String[] region = SAMPLE_REGIONS[i % SAMPLE_REGIONS.length];
            String synId = i < unusedSynIds.size() ? unusedSynIds.get(i) : null;

            Map<String, Object> memberParam = new HashMap<>();
            memberParam.put("email", email);
            memberParam.put("passwordHash", encodedPassword);
            memberParam.put("name", name);
            memberParam.put("phoneEnc", phoneEnc);
            memberParam.put("regionSido", region[0]);
            memberParam.put("regionSigungu", region[1]);
            memberParam.put("synId", synId);
            memberParam.put("keyVer", activeKeyVer);

            // INSERT 후 selectKey로 생성된 MEMBER_ID 확보
            dashboardMapper.insertTestMember(memberParam);
            Long newMemberId = ((Number) memberParam.get("memberId")).longValue();

            // 활성 필수 약관(TERMS_POLICY) 일괄 동의 적재
            for (Long termsId : requiredTermIds) {
                dashboardMapper.insertMemberConsent(newMemberId, termsId, demoIpEnc, activeKeyVer);
            }

            createdEmails.add(email);
        }

        dashboardMapper.insertAdminAuditLog(
                adminId,
                "DEMO_TEST_MEMBERS",
                "시연용 테스트 회원 " + createdEmails.size() + "명 생성 (" + String.join(", ", createdEmails) + ")"
        );

        return AdminDashboardDTO.TestMembersResponse.builder()
                .createdCount(createdEmails.size())
                .createdEmails(createdEmails)
                .message("시연용 테스트 회원 5명을 생성했습니다. (초기 비밀번호: Test1234!)")
                .build();
    }

    /**
     * F37-4: 데모 데이터 일괄 초기화 (회원·모임·계좌·승계·제재·신고·오픈뱅킹·ES 전체 정리)
     * ※ 마스터 테이블(ADMIN, CRYPTO_KEY, TERMS_POLICY, CREDIT_SNAPSHOT, KOSIS_HOUSEHOLD, PRODUCTS_SAVING)은 보존
     */
    @Transactional
    public void resetAllDemoData(Long adminId) {
        requireActiveAdmin(adminId);

        // 1. 알림 · 채팅 · 신고 · 감사 로그
        dashboardMapper.deleteAllNotifications();
        dashboardMapper.deleteAllChatMessages();
        dashboardMapper.deleteAllSupportReports();
        dashboardMapper.deleteAllAuditLogs();

        // 2. 미납 · 제재 · 해산 · 합의
        dashboardMapper.deleteAllOverdueCases();
        dashboardMapper.deleteAllSanctions();
        dashboardMapper.deleteAllDissolutionRequests();
        dashboardMapper.deleteAllConsents();

        // 3. 탈퇴 동의 대상 · 탈퇴 · 순서 · 증빙 · 가입 · 초대 시도
        dashboardMapper.deleteAllWithdrawalConsentTargets();
        dashboardMapper.deleteAllWithdrawalRequests();
        dashboardMapper.deleteAllPayoutRequests();
        dashboardMapper.deleteAllPaymentProofFiles();
        dashboardMapper.deleteAllPaymentProofs();
        dashboardMapper.deleteAllJoinRequests();
        dashboardMapper.deleteAllJoinCodeAttempts();

        // 4. 승계 · 계좌 · 모임원 · 모임
        dashboardMapper.deleteAllLeaderTransferRequests();
        dashboardMapper.deleteAllGyeAccounts();
        dashboardMapper.deleteAllGyeMembers();
        dashboardMapper.deleteAllGyeGroups();

        // 5. 오픈뱅킹 · 모의 가상은행 원장 (거래내역 → 계좌 FK 역순) · CODEF 연결
        dashboardMapper.deleteAllMockKftcTransactions();
        dashboardMapper.deleteAllMockKftcAccounts();
        dashboardMapper.deleteAllMockKftcOauthCodes();
        dashboardMapper.deleteAllMockKftcOauthTokens();
        dashboardMapper.deleteAllCodefConnections();

        // 6. 위험도 · 금융 데이터 · 법적 동의 · 소셜 계정 · 일반 회원 (MEMBER_SOCIAL_ACCOUNT 선삭제)
        dashboardMapper.deleteAllRiskScores();
        dashboardMapper.deleteAllFinanceMonthly();
        dashboardMapper.deleteAllFinanceProfiles();
        dashboardMapper.deleteAllMemberConsents();
        dashboardMapper.deleteAllMemberSocialAccounts();
        dashboardMapper.deleteAllMembers();

        dashboardMapper.insertAdminAuditLog(
                adminId,
                "DEMO_RESET",
                "관리자 시연 데이터 일괄 초기화 실행"
        );

        // 7. Elasticsearch(moava_group) 인덱스 초기화 (트랜잭션 커밋 이후 실행)
        if (groupSearchSyncService != null) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        groupSearchSyncService.resetIndex();
                    }
                });
            } else {
                groupSearchSyncService.resetIndex();
            }
        }
    }

    private void requireActiveAdmin(Long adminId) {
        if (adminId == null || dashboardMapper.countActiveAdmin(adminId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "활성화된 플랫폼 관리자 계정만 이용할 수 있습니다.");
        }
    }
}