package com.moava.support.service;

import com.moava.crypto.service.CryptoKeyService;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;
import com.moava.support.dto.SupportReportDTO;
import com.moava.support.mapper.SupportReportMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F40 SupportReportService 단위 테스트.
 * - [접수] AES-256-GCM 암호화 저장(평문 저장 없음), 활성 관리자 전원 알림(REPORT_RECEIVED),
 *         AUDIT_LOG(REPORT_RECEIVED)에 유형·대상 모임만 기록 + '신고 접수'
 * - [조회] 본인 건만 조회, 타인 신고 열람 불가(복호화 없이 403), 마스킹, 관리자 전체+상태 필터
 * - [처리] 메모 필수('처리 메모가 필요합니다'), STATUS·ADMIN_MEMO·HANDLED_BY 갱신,
 *         AUDIT_LOG(REPORT_STATUS) 이력, 신고자 알림(REPORT_STATUS)
 */
@ExtendWith(MockitoExtension.class)
class SupportReportServiceTest {

    @Mock
    SupportReportMapper mapper;

    @Mock
    CryptoKeyService cryptoKeyService;

    @Mock
    NotificationService notificationService;

    @InjectMocks
    SupportReportService service;

    @Test
    @DisplayName("신고 접수 성공: 상세 AES-256-GCM 암호화 저장(평문 금지), AUDIT_LOG(REPORT_RECEIVED) 유형·모임만, 활성 관리자 전원 알림")
    void createReport_success_encryptsDetailAndNotifiesAdmins() {
        String plainDetail = "모임장이 110-123-456789 계좌로 무단 변경을 시도했습니다.";
        String encryptedDetail = "ENC_AES256GCM_BASE64_CIPHERTEXT==";

        when(mapper.countActiveMember(10L)).thenReturn(1);
        when(mapper.selectGroupById(101L)).thenReturn(groupRow(101L, "마포 저축 모임", "ACTIVE"));
        when(cryptoKeyService.getCurrentKeyVersion()).thenReturn(2);
        when(cryptoKeyService.encrypt(plainDetail, 2)).thenReturn(encryptedDetail);
        when(mapper.nextSupportReportId()).thenReturn(701L);

        SupportReportDTO.SupportReportRow savedRow = reportRow(
                701L, 10L, "김하늘", 101L, "마포 저축 모임",
                "ACCOUNT_CHANGE", encryptedDetail, 2, "RECEIVED", null, null, null,
                "2026-11-25 13:00", null
        );
        when(mapper.selectReportById(701L)).thenReturn(savedRow);
        when(cryptoKeyService.decrypt(encryptedDetail, 2)).thenReturn(plainDetail);

        SupportReportDTO.CreateReportResponse response = service.createReport(
                10L,
                new SupportReportDTO.CreateReportRequest(101L, "계좌 변경 의심", plainDetail)
        );

        assertEquals(701L, response.getReport().getReportId());
        assertEquals("김○늘", response.getReport().getReporterName());
        assertEquals("ACCOUNT_CHANGE", response.getReport().getCategory());
        assertEquals("계좌 변경 의심", response.getReport().getCategoryLabel());
        assertEquals("RECEIVED", response.getReport().getStatus());
        assertEquals("접수", response.getReport().getStatusLabel());
        assertEquals(plainDetail, response.getReport().getDetail());
        assertTrue(response.getGuidanceNotice().contains("실제 계좌 동결"));
        assertTrue(response.getGuidanceNotice().contains("은행"));
        assertTrue(response.getGuidanceNotice().contains("경찰"));

        // DB에는 반드시 암호문만 저장 (평문 저장 없음)
        ArgumentCaptor<String> encCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertSupportReport(eq(701L), eq(10L), eq(101L), eq("ACCOUNT_CHANGE"), encCaptor.capture(), eq(2));
        assertEquals(encryptedDetail, encCaptor.getValue());
        assertNotEquals(plainDetail, encCaptor.getValue());

        // AUDIT_LOG(REPORT_RECEIVED)에는 '신고 접수' + 유형·대상 모임만, 상세 평문 절대 금지
        ArgumentCaptor<String> auditDescCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).insertMemberAuditLog(eq(101L), eq(10L), eq("REPORT_RECEIVED"), auditDescCaptor.capture());
        String auditDesc = auditDescCaptor.getValue();
        assertTrue(auditDesc.contains("신고 접수"));
        assertTrue(auditDesc.contains("계좌 변경 의심"));
        assertTrue(auditDesc.contains("마포 저축 모임"));
        assertFalse(auditDesc.contains("110-123-456789"));
        assertFalse(auditDesc.contains(plainDetail));

        // 활성 관리자 전원 알림
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).notifyAdmins(eq(NotifType.REPORT_RECEIVED), eq(701L), paramsCaptor.capture());
        assertEquals("계좌 변경 의심", paramsCaptor.getValue().get("category"));
        assertEquals("마포 저축 모임", paramsCaptor.getValue().get("group"));
    }

    @Test
    @DisplayName("신고 접수: 대상 모임 미지정(groupId=null) 기타 문의도 정상 접수")
    void createReport_withoutGroup_success() {
        when(mapper.countActiveMember(10L)).thenReturn(1);
        when(cryptoKeyService.getCurrentKeyVersion()).thenReturn(1);
        when(cryptoKeyService.encrypt("기타 문의 내용", 1)).thenReturn("ENC_OTHER==");
        when(mapper.nextSupportReportId()).thenReturn(702L);
        when(mapper.selectReportById(702L)).thenReturn(reportRow(
                702L, 10L, "이솔", null, null,
                "OTHER", "ENC_OTHER==", 1, "RECEIVED", null, null, null,
                "2026-11-25 13:05", null
        ));
        when(cryptoKeyService.decrypt("ENC_OTHER==", 1)).thenReturn("기타 문의 내용");

        SupportReportDTO.CreateReportResponse response = service.createReport(
                10L,
                new SupportReportDTO.CreateReportRequest(null, "OTHER", "기타 문의 내용")
        );

        assertEquals("모임 미지정", response.getReport().getGroupTitle());
        assertEquals("이○", response.getReport().getReporterName());
        verify(mapper).insertSupportReport(702L, 10L, null, "OTHER", "ENC_OTHER==", 1);
        verify(mapper).insertMemberAuditLog(isNull(), eq(10L), eq("REPORT_RECEIVED"), anyString());
    }

    @Test
    @DisplayName("신고 접수: 상세 내용 공백이면 400 BAD_REQUEST")
    void createReport_blankDetail_throws400() {
        when(mapper.countActiveMember(10L)).thenReturn(1);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createReport(10L, new SupportReportDTO.CreateReportRequest(101L, "FUND_MISUSE", "   "))
        );

        assertEquals(HttpStatus.BAD_REQUEST, HttpStatus.valueOf(ex.getStatusCode().value()));
        verify(cryptoKeyService, never()).encrypt(anyString(), anyInt());
        verify(mapper, never()).insertSupportReport(anyLong(), anyLong(), any(), anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("타인 신고 열람 불가: 본인 신고가 아니면 복호화 없이 403 FORBIDDEN 차단")
    void getMyReportDetail_otherMemberReport_throws403WithoutDecrypt() {
        when(mapper.countActiveMember(20L)).thenReturn(1);
        when(mapper.selectReportById(701L)).thenReturn(reportRow(
                701L, 10L, "김하늘", 101L, "마포 저축 모임",
                "FUND_MISUSE", "ENC_SECRET==", 1, "RECEIVED", null, null, null,
                "2026-11-25 13:00", null
        ));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.getMyReportDetail(20L, 701L)
        );

        assertEquals(HttpStatus.FORBIDDEN, HttpStatus.valueOf(ex.getStatusCode().value()));
        verify(cryptoKeyService, never()).decrypt(anyString(), anyInt());
    }

    @Test
    @DisplayName("관리자 전체 조회 + 상태 필터: 마스킹, 복호화, 상태별 건수, 처리 이력 파싱 검증")
    void getAdminReports_withFilter_returnsDecryptedAndMaskedList() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        when(mapper.selectAdminReports("REVIEWING")).thenReturn(List.of(
                reportRow(
                        701L, 10L, "김하늘", 101L, "마포 저축 모임",
                        "PROOF_MISMATCH", "ENC_1==", 1, "REVIEWING", "증빙 대조 중", 1L, "운영팀",
                        "2026-11-25 13:00", "2026-11-25 13:10"
                )
        ));
        when(mapper.selectAdminReportStatusHistory("REVIEWING")).thenReturn(List.of(
                historyRow(901L, 1L, "운영팀", "REPORT #701 · 접수 → 검토 중 · 사유: 증빙 대조 중", "2026-11-25 13:10")
        ));
        when(mapper.selectStatusCounts()).thenReturn(List.of(
                countRow("RECEIVED", 2),
                countRow("REVIEWING", 1)
        ));
        when(cryptoKeyService.decrypt("ENC_1==", 1)).thenReturn("10월 입금 증빙 금액이 불일치합니다.");

        SupportReportDTO.AdminReportListResponse res = service.getAdminReports(1L, "검토 중");

        assertEquals("REVIEWING", res.getStatusFilter());
        assertEquals(1, res.getTotalCount());
        assertEquals(3, res.getStatusCounts().get("ALL"));
        assertEquals(2, res.getStatusCounts().get("RECEIVED"));
        assertEquals(1, res.getStatusCounts().get("REVIEWING"));

        SupportReportDTO.ReportItem item = res.getItems().get(0);
        assertEquals("김○늘", item.getReporterName());
        assertEquals("10월 입금 증빙 금액이 불일치합니다.", item.getDetail());
        assertEquals("관리자 운영팀", item.getHandledByLabel());
        assertEquals(1, item.getHistory().size());
        assertEquals("접수", item.getHistory().get(0).getFromStatusLabel());
        assertEquals("검토 중", item.getHistory().get(0).getToStatusLabel());
        assertEquals("증빙 대조 중", item.getHistory().get(0).getMemo());
    }

    @Test
    @DisplayName("관리자 조회: 조건에 맞는 신고가 0건이면 상태 변경 이력 조회를 생략")
    void getAdminReports_emptyResultSkipsHistoryQuery() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        when(mapper.selectAdminReports("REJECTED")).thenReturn(List.of());
        when(mapper.selectStatusCounts()).thenReturn(List.of(countRow("RECEIVED", 2)));

        SupportReportDTO.AdminReportListResponse res = service.getAdminReports(1L, "REJECTED");

        assertEquals("REJECTED", res.getStatusFilter());
        assertEquals(0, res.getTotalCount());
        assertTrue(res.getItems().isEmpty());
        verify(mapper, never()).selectAdminReportStatusHistory(any());
    }

    @Test
    @DisplayName("복호화 1건 실패 시 전체 조회가 실패하지 않고 해당 건만 '복호화 오류'로 반환")
    void getAdminReports_decryptFailureReturnsFallbackWithoutFailingList() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        when(mapper.selectAdminReports(null)).thenReturn(List.of(
                reportRow(
                        701L, 10L, "김하늘", 101L, "마포 저축 모임",
                        "ACCOUNT_CHANGE", "BROKEN_ENC==", 3, "RECEIVED", null, null, null,
                        "2026-11-25 13:00", null
                ),
                reportRow(
                        702L, 11L, "이솔", 102L, "서초 저축 모임",
                        "OTHER", "VALID_ENC==", 1, "RECEIVED", null, null, null,
                        "2026-11-25 13:05", null
                )
        ));
        when(mapper.selectAdminReportStatusHistory(null)).thenReturn(List.of());
        when(mapper.selectStatusCounts()).thenReturn(List.of(countRow("RECEIVED", 2)));
        when(cryptoKeyService.decrypt("BROKEN_ENC==", 3))
                .thenThrow(new IllegalStateException("암호화 키를 찾을 수 없습니다. KEY_VER=3"));
        when(cryptoKeyService.decrypt("VALID_ENC==", 1)).thenReturn("정상 복호화 내용");

        SupportReportDTO.AdminReportListResponse res = service.getAdminReports(1L, "ALL");

        assertEquals(2, res.getTotalCount());
        assertEquals("복호화 오류", res.getItems().get(0).getDetail());
        assertEquals("정상 복호화 내용", res.getItems().get(1).getDetail());
    }

    @Test
    @DisplayName("관리자 상태 변경 성공: STATUS·ADMIN_MEMO·HANDLED_BY 갱신, AUDIT_LOG(REPORT_STATUS) 이력, 신고자 알림")
    void updateReportStatus_success() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        when(mapper.selectReportForUpdate(701L)).thenReturn(reportRow(
                701L, 10L, null, 101L, null,
                "ACCOUNT_CHANGE", "ENC_1==", 1, "RECEIVED", null, null, null,
                "2026-11-25 13:00", null
        ));
        when(mapper.selectReportById(701L)).thenReturn(reportRow(
                701L, 10L, "김하늘", 101L, "마포 저축 모임",
                "ACCOUNT_CHANGE", "ENC_1==", 1, "REVIEWING", "계좌 변경 이력 확인 착수", 1L, "관리자",
                "2026-11-25 13:00", "2026-11-25 13:20"
        ));
        when(mapper.selectReportStatusHistoryByReportId(701L)).thenReturn(List.of(
                historyRow(902L, 1L, "관리자", "REPORT #701 · 접수 → 검토 중 · 사유: 계좌 변경 이력 확인 착수", "2026-11-25 13:20")
        ));
        when(cryptoKeyService.decrypt("ENC_1==", 1)).thenReturn("상세 내용");

        SupportReportDTO.StatusUpdateResponse res = service.updateReportStatus(
                1L,
                701L,
                new SupportReportDTO.StatusUpdateRequest("REVIEWING", "계좌 변경 이력 확인 착수")
        );

        assertEquals("RECEIVED", res.getPreviousStatus());
        assertEquals("접수", res.getPreviousStatusLabel());
        assertEquals("REVIEWING", res.getCurrentStatus());
        assertEquals("검토 중", res.getCurrentStatusLabel());
        assertEquals("계좌 변경 이력 확인 착수", res.getReport().getAdminMemo());
        assertEquals(1L, res.getReport().getHandledBy());

        verify(mapper).updateReportStatus(701L, "REVIEWING", "계좌 변경 이력 확인 착수", 1L);
        verify(mapper).insertAdminAuditLog(
                101L,
                1L,
                10L,
                "REPORT_STATUS",
                "REPORT #701 · 접수 → 검토 중 · 사유: 계좌 변경 이력 확인 착수"
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> notifCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).notifyMember(eq(10L), eq(NotifType.REPORT_STATUS), eq(701L), notifCaptor.capture());
        assertEquals("검토 중", notifCaptor.getValue().get("status"));
    }

    @Test
    @DisplayName("관리자 상태 변경 실패: 처리 메모 누락 시 400 BAD_REQUEST 및 '처리 메모가 필요합니다'")
    void updateReportStatus_blankMemo_throws400WithSpecMessage() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.updateReportStatus(
                        1L,
                        701L,
                        new SupportReportDTO.StatusUpdateRequest("CLOSED", "   ")
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, HttpStatus.valueOf(ex.getStatusCode().value()));
        assertEquals("처리 메모가 필요합니다", ex.getReason());
        verify(mapper, never()).updateReportStatus(anyLong(), anyString(), anyString(), anyLong());
        verify(mapper, never()).insertAdminAuditLog(any(), anyLong(), any(), anyString(), anyString());
        verify(notificationService, never()).notifyMember(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("관리자 상태 변경 실패: 동일 상태로 변경 시도 시 409 CONFLICT")
    void updateReportStatus_sameStatus_throws409() {
        when(mapper.countActiveAdmin(1L)).thenReturn(1);
        when(mapper.selectReportForUpdate(701L)).thenReturn(reportRow(
                701L, 10L, null, 101L, null,
                "ACCOUNT_CHANGE", "ENC_1==", 1, "REVIEWING", "이전 메모", 1L, null,
                "2026-11-25 13:00", "2026-11-25 13:10"
        ));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.updateReportStatus(
                        1L,
                        701L,
                        new SupportReportDTO.StatusUpdateRequest("REVIEWING", "동일 상태 변경 시도")
                )
        );

        assertEquals(HttpStatus.CONFLICT, HttpStatus.valueOf(ex.getStatusCode().value()));
        verify(mapper, never()).updateReportStatus(anyLong(), anyString(), anyString(), anyLong());
    }

    // ---------- 픽스처 ----------

    private static SupportReportDTO.GroupBasicRow groupRow(Long groupId, String title, String status) {
        SupportReportDTO.GroupBasicRow row = new SupportReportDTO.GroupBasicRow();
        row.setGroupId(groupId);
        row.setTitle(title);
        row.setStatus(status);
        return row;
    }

    private static SupportReportDTO.SupportReportRow reportRow(
            Long reportId,
            Long memberId,
            String reporterName,
            Long groupId,
            String groupTitle,
            String category,
            String detailEnc,
            Integer keyVer,
            String status,
            String adminMemo,
            Long handledBy,
            String handledByName,
            String createdAt,
            String updatedAt
    ) {
        SupportReportDTO.SupportReportRow row = new SupportReportDTO.SupportReportRow();
        row.setSupportReportId(reportId);
        row.setMemberId(memberId);
        row.setReporterName(reporterName);
        row.setGroupId(groupId);
        row.setGroupTitle(groupTitle);
        row.setCategory(category);
        row.setDetailEnc(detailEnc);
        row.setKeyVer(keyVer);
        row.setStatus(status);
        row.setAdminMemo(adminMemo);
        row.setHandledBy(handledBy);
        row.setHandledByName(handledByName);
        row.setCreatedAt(createdAt);
        row.setUpdatedAt(updatedAt);
        return row;
    }

    private static SupportReportDTO.StatusHistoryRow historyRow(
            Long auditLogId,
            Long actorAdminId,
            String actorAdminName,
            String description,
            String createdAt
    ) {
        SupportReportDTO.StatusHistoryRow row = new SupportReportDTO.StatusHistoryRow();
        row.setAuditLogId(auditLogId);
        row.setActorAdminId(actorAdminId);
        row.setActorAdminName(actorAdminName);
        row.setDescription(description);
        row.setCreatedAt(createdAt);
        return row;
    }

    private static SupportReportDTO.StatusCountRow countRow(String status, int count) {
        SupportReportDTO.StatusCountRow row = new SupportReportDTO.StatusCountRow();
        row.setStatus(status);
        row.setCount(count);
        return row;
    }
}
