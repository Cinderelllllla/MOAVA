package com.moava.admin.dashboard.service;

import com.moava.admin.dashboard.dto.AdminDashboardDTO;
import com.moava.admin.dashboard.mapper.AdminDashboardMapper;
import com.moava.crypto.service.CryptoKeyService;
import com.moava.group.search.GroupSearchSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDemoServiceTest {

    @Mock
    private AdminDashboardMapper dashboardMapper;

    @Mock
    private CryptoKeyService cryptoKeyService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GroupSearchSyncService groupSearchSyncService;

    private AdminDemoService service;

    @BeforeEach
    void setUp() {
        service = new AdminDemoService(dashboardMapper, cryptoKeyService, passwordEncoder, groupSearchSyncService);
    }

    @Test
    @DisplayName("테스트 회원 5명 생성: 접미 번호 중복 건너뜀 + SYN_ID 매핑 + 필수 약관 동의 + 감사 로그")
    void createFiveTestMembers_createsFiveAndSkipsDuplicateEmail() {
        when(dashboardMapper.countActiveAdmin(1L)).thenReturn(1);
        when(dashboardMapper.selectUnusedSynIds(5)).thenReturn(List.of("SYN_01", "SYN_02", "SYN_03"));
        when(dashboardMapper.selectActiveRequiredTermIds()).thenReturn(List.of(10L, 20L));
        when(cryptoKeyService.getCurrentKeyVersion()).thenReturn(1);
        when(passwordEncoder.encode("Test1234!")).thenReturn("$2a$encoded");
        when(cryptoKeyService.encrypt(anyString(), eq(1))).thenReturn("ENC_VAL");
        when(dashboardMapper.selectMaxTestMemberSuffix()).thenReturn(10);
        when(dashboardMapper.countMemberByEmail("testuser11@moava.test")).thenReturn(1);
        when(dashboardMapper.countMemberByEmail("testuser12@moava.test")).thenReturn(0);
        when(dashboardMapper.countMemberByEmail("testuser13@moava.test")).thenReturn(0);
        when(dashboardMapper.countMemberByEmail("testuser14@moava.test")).thenReturn(0);
        when(dashboardMapper.countMemberByEmail("testuser15@moava.test")).thenReturn(0);
        when(dashboardMapper.countMemberByEmail("testuser16@moava.test")).thenReturn(0);

        AtomicLong seq = new AtomicLong(1000L);
        doAnswer(invocation -> {
            Map<String, Object> param = invocation.getArgument(0);
            param.put("memberId", seq.incrementAndGet());
            return null;
        }).when(dashboardMapper).insertTestMember(any());

        AdminDashboardDTO.TestMembersResponse res = service.createFiveTestMembers(1L);

        assertThat(res.getCreatedCount()).isEqualTo(5);
        assertThat(res.getCreatedEmails()).containsExactly(
                "testuser12@moava.test",
                "testuser13@moava.test",
                "testuser14@moava.test",
                "testuser15@moava.test",
                "testuser16@moava.test"
        );
        verify(dashboardMapper, times(5)).insertTestMember(any());
        verify(dashboardMapper, times(10)).insertMemberConsent(any(), any(), eq("ENC_VAL"), eq(1));
        verify(dashboardMapper).insertAdminAuditLog(eq(1L), eq("DEMO_TEST_MEMBERS"), contains("testuser12@moava.test"));
    }

    @Test
    @DisplayName("시연 데이터 초기화: FK 역순으로 30개 운영·오픈뱅킹·소셜 테이블 삭제 후 감사 로그 및 ES 인덱스 초기화")
    void resetAllDemoData_deletesTablesInFkOrderAndLogsAudit() {
        when(dashboardMapper.countActiveAdmin(1L)).thenReturn(1);

        service.resetAllDemoData(1L);

        InOrder order = inOrder(dashboardMapper, groupSearchSyncService);
        order.verify(dashboardMapper).deleteAllNotifications();
        order.verify(dashboardMapper).deleteAllChatMessages();
        order.verify(dashboardMapper).deleteAllSupportReports();
        order.verify(dashboardMapper).deleteAllAuditLogs();
        order.verify(dashboardMapper).deleteAllOverdueCases();
        order.verify(dashboardMapper).deleteAllSanctions();
        order.verify(dashboardMapper).deleteAllDissolutionRequests();
        order.verify(dashboardMapper).deleteAllConsents();
        order.verify(dashboardMapper).deleteAllWithdrawalConsentTargets();
        order.verify(dashboardMapper).deleteAllWithdrawalRequests();
        order.verify(dashboardMapper).deleteAllPayoutRequests();
        order.verify(dashboardMapper).deleteAllPaymentProofFiles();
        order.verify(dashboardMapper).deleteAllPaymentProofs();
        order.verify(dashboardMapper).deleteAllJoinRequests();
        order.verify(dashboardMapper).deleteAllJoinCodeAttempts();
        order.verify(dashboardMapper).deleteAllLeaderTransferRequests();
        order.verify(dashboardMapper).deleteAllGyeAccounts();
        order.verify(dashboardMapper).deleteAllGyeMembers();
        order.verify(dashboardMapper).deleteAllGyeGroups();
        order.verify(dashboardMapper).deleteAllMockKftcTransactions();
        order.verify(dashboardMapper).deleteAllMockKftcAccounts();
        order.verify(dashboardMapper).deleteAllMockKftcOauthCodes();
        order.verify(dashboardMapper).deleteAllMockKftcOauthTokens();
        order.verify(dashboardMapper).deleteAllCodefConnections();
        order.verify(dashboardMapper).deleteAllRiskScores();
        order.verify(dashboardMapper).deleteAllFinanceMonthly();
        order.verify(dashboardMapper).deleteAllFinanceProfiles();
        order.verify(dashboardMapper).deleteAllMemberConsents();
        order.verify(dashboardMapper).deleteAllMemberSocialAccounts();
        order.verify(dashboardMapper).deleteAllMembers();
        order.verify(dashboardMapper).insertAdminAuditLog(eq(1L), eq("DEMO_RESET"), anyString());
        order.verify(groupSearchSyncService).resetIndex();
    }

    @Test
    @DisplayName("시연 도구: 비활성 관리자는 403 차단")
    void demoActions_rejectInactiveAdmin() {
        when(dashboardMapper.countActiveAdmin(99L)).thenReturn(0);

        assertThatThrownBy(() -> service.createFiveTestMembers(99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThatThrownBy(() -> service.resetAllDemoData(99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
