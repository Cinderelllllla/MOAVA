package com.moava.group.procedures.service;

import com.moava.group.procedures.dto.DissolutionDTO;
import com.moava.group.procedures.mapper.DissolutionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DissolutionService {

    private final DissolutionMapper dissolutionMapper;

    public DissolutionService(DissolutionMapper dissolutionMapper) {
        this.dissolutionMapper = dissolutionMapper;
    }

    /**
     * 특정 모임의 해산 신청 현황 조회 (모임 운영 센터 현재 상태 배지 표시용)
     */
    @Transactional(readOnly = true)
    public DissolutionDTO.StatusResponse getGroupDissolutionStatus(Long groupId) {
        DissolutionDTO.StatusResponse status = dissolutionMapper.selectLatestDissolutionByGroup(groupId);
        if (status != null) {
            populateRefundEstimate(status);
        }
        return status;
    }

    /**
     * 관리자 해산 목록 및 종료된 모임 기록 조회 (A04 화면용)
     */
    @Transactional(readOnly = true)
    public List<DissolutionDTO.StatusResponse> getAdminDissolutionList(String statusFilter) {
        List<DissolutionDTO.StatusResponse> list = dissolutionMapper.selectAdminDissolutionList(statusFilter);
        for (DissolutionDTO.StatusResponse item : list) {
            populateRefundEstimate(item);
        }
        return list;
    }

    /**
     * 1. [신청] 모임장 -> DISSOLUTION_REQUEST(PENDING), GYE_GROUP.STATUS=DISSOLVE_PENDING,
     *    알림 발송, 건강도 12점 차감(DISSOLVE_PENDING 상태로 자동 반영), AUDIT_LOG(DISSOLVE_REQUEST)
     */
    @Transactional
    public String requestDissolution(Long groupId, DissolutionDTO.RequestForm form) {
        Map<String, Object> groupInfo = dissolutionMapper.selectGroupInfo(groupId);
        if (groupInfo == null) {
            throw new IllegalArgumentException("존재하지 않는 모임입니다.");
        }

        Long ownerId = ((Number) groupInfo.get("OWNER_ID")).longValue();
        Long leaderId = form.getEffectiveLeaderId();
        if (leaderId == null || !ownerId.equals(leaderId)) {
            throw new IllegalStateException("모임장만 해산을 신청할 수 있습니다.");
        }

        String currentGroupStatus = (String) groupInfo.get("STATUS");
        Map<String, Object> existingPending = dissolutionMapper.selectPendingDissolutionByGroup(groupId);

        // [예외 1] 중복 신청 -> '이미 신청했습니다'
        if (existingPending != null
                || "DISSOLVE_PENDING".equals(currentGroupStatus)
                || "DISSOLVED".equals(currentGroupStatus)) {
            throw new IllegalStateException("이미 신청했습니다");
        }

        if (form.getReason() == null || form.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("해산 사유를 입력해 주세요.");
        }

        String cleanReason = form.getReason().trim();
        String groupTitle = (String) groupInfo.get("TITLE");

        // 1) DISSOLUTION_REQUEST(PENDING) 생성
        dissolutionMapper.insertDissolutionRequest(groupId, leaderId, cleanReason);

        // 2) GYE_GROUP.STATUS = 'DISSOLVE_PENDING' 전환 (건강도 산출 시 -12점 차감 조건 활성화)
        dissolutionMapper.updateGroupStatus(groupId, "DISSOLVE_PENDING");

        // 3) ACTIVE 모임원 전원(모임장 포함)에게 해산 검토 대기 전환 인앱 알림(NOTIFICATION) 발송
        List<Long> activeMembers = dissolutionMapper.selectActiveMemberIds(groupId);
        for (Long memberId : activeMembers) {
            String title = memberId.equals(ownerId)
                    ? "[관리자 검토 접수] 계모임 해산 신청 완료"
                    : "계모임 해산 신청 안내";
            String body = "[" + groupTitle + "] 모임의 해산 신청이 접수되어 관리자 검토 대기 상태로 전환되었습니다. (사유: " + cleanReason + ")";
            dissolutionMapper.insertNotification(
                    memberId,
                    "DISSOLVE",
                    title,
                    body,
                    "/operations?groupId=" + groupId,
                    groupId
            );
        }

        // 4) AUDIT_LOG (DISSOLVE_REQUEST) 기록 (관리자 대시보드 및 해산 검토 알림 연동)
        dissolutionMapper.insertMemberAuditLog(
                groupId,
                leaderId,
                "DISSOLVE_REQUEST",
                "계모임 해산 신청 접수 (사유: " + cleanReason + ")"
        );

        return "관리자에게 해산 승인을 요청했습니다.";
    }

    /**
     * 2. [승인] ADMIN: 해당 모임 PAYMENT_PROOF STATUS != CONFIRMED 0건 검증 -> APPROVED,
     *    GYE_GROUP.STATUS=DISSOLVED, 전 회원 '해산 완료·1/N 정산 안내' 알림,
     *    ES 문서 삭제(TODO), DISSOLUTION_REQUEST.DECIDED_BY & AUDIT_LOG.ACTOR_ADMIN_ID 기록
     */
    @Transactional
    public String approveDissolutionByAdmin(Long groupId, Long adminId) {
        Map<String, Object> groupInfo = dissolutionMapper.selectGroupInfo(groupId);
        if (groupInfo == null) {
            throw new IllegalArgumentException("존재하지 않는 모임입니다.");
        }

        Map<String, Object> pendingReq = dissolutionMapper.selectPendingDissolutionByGroup(groupId);
        if (pendingReq == null) {
            throw new IllegalStateException("관리자 검토 대기 중인 해산 신청 내역이 없습니다.");
        }

        // [예외 2] 미종결 증빙 존재(STATUS != 'CONFIRMED') 1건이라도 있으면 승인 차단
        int unconfirmedProofs = dissolutionMapper.countUnconfirmedProofs(groupId);
        if (unconfirmedProofs > 0) {
            throw new IllegalStateException("확인 대기 증빙이 있어 승인할 수 없습니다");
        }

        Long dissolutionRequestId = ((Number) pendingReq.get("DISSOLUTION_REQUEST_ID")).longValue();
        String groupTitle = (String) groupInfo.get("TITLE");

        // 1) DISSOLUTION_REQUEST: STATUS = 'APPROVED', DECIDED_BY = adminId(FK ADMIN), DECIDED_AT = SYSDATE
        dissolutionMapper.updateDissolutionDecision(dissolutionRequestId, "APPROVED", adminId);

        // 2) GYE_GROUP: STATUS = 'DISSOLVED' 변경
        dissolutionMapper.updateGroupStatus(groupId, "DISSOLVED");

        // 3) 1/N 정산 금액 계산 및 전 회원 '해산 완료·1/N 정산 안내' 인앱 알림 발송
        List<Long> activeMembers = dissolutionMapper.selectActiveMemberIds(groupId);
        Long totalConfirmed = dissolutionMapper.selectTotalConfirmedProofAmount(groupId);
        long safeTotal = totalConfirmed != null ? totalConfirmed : 0L;
        int memberCount = activeMembers.size();
        long perMemberAmount = memberCount > 0 ? (safeTotal / memberCount) : 0L;

        String formattedPerMember = NumberFormat.getNumberInstance(Locale.KOREA).format(perMemberAmount);
        String formattedTotal = NumberFormat.getNumberInstance(Locale.KOREA).format(safeTotal);

        for (Long memberId : activeMembers) {
            dissolutionMapper.insertNotification(
                    memberId,
                    "DISSOLVE",
                    "해산 완료 · 1/N 정산 안내",
                    "[" + groupTitle + "] 모임 해산이 최종 승인되었습니다. 총 확인 납입액 "
                            + formattedTotal + "원 기준 1/N 정산 예상 금액은 1인당 "
                            + formattedPerMember + "원입니다.",
                    "/operations?groupId=" + groupId,
                    dissolutionRequestId
            );
        }

        // 4) [TODO] Elasticsearch(ES) 모임 검색 인덱스 문서 삭제 연동
        // TODO: Elasticsearch 연동 완료 시 아래 코드 활성화 (해산된 모임을 검색 결과에서 즉시 제거)
        // groupSearchRepository.deleteById(groupId);

        // 5) AUDIT_LOG (DISSOLVE_APPROVE) 기록 — ACTOR_ADMIN_ID에 기록 (ACTOR_MEMBER_ID는 NULL)
        dissolutionMapper.insertAdminAuditLog(
                groupId,
                adminId,
                "DISSOLVE_APPROVE",
                "계모임 해산 최종 승인 완료 (1/N 정산 안내: 1인당 " + formattedPerMember + "원)"
        );

        return "해산을 승인했어요.";
    }

    /**
     * 3. [반려] ADMIN: 해산 신청 반려 -> DISSOLUTION_REQUEST(REJECTED), GYE_GROUP(ACTIVE 복구)
     */
    @Transactional
    public String rejectDissolutionByAdmin(Long groupId, Long adminId, String rejectReason) {
        Map<String, Object> groupInfo = dissolutionMapper.selectGroupInfo(groupId);
        if (groupInfo == null) {
            throw new IllegalArgumentException("존재하지 않는 모임입니다.");
        }

        Map<String, Object> pendingReq = dissolutionMapper.selectPendingDissolutionByGroup(groupId);
        if (pendingReq == null) {
            throw new IllegalStateException("관리자 검토 대기 중인 해산 신청 내역이 없습니다.");
        }

        Long dissolutionRequestId = ((Number) pendingReq.get("DISSOLUTION_REQUEST_ID")).longValue();
        String groupTitle = (String) groupInfo.get("TITLE");
        String reasonText = (rejectReason != null && !rejectReason.trim().isEmpty())
                ? rejectReason.trim()
                : "정산 및 증빙 요건 미충족";

        // 1) DISSOLUTION_REQUEST: STATUS = 'REJECTED', DECIDED_BY = adminId
        dissolutionMapper.updateDissolutionDecision(dissolutionRequestId, "REJECTED", adminId);

        // 2) GYE_GROUP: STATUS = 'ACTIVE' (운영 중 상태로 복귀)
        dissolutionMapper.updateGroupStatus(groupId, "ACTIVE");

        // 3) ACTIVE 모임원 전원에게 해산 반려 인앱 알림 발송
        List<Long> activeMembers = dissolutionMapper.selectActiveMemberIds(groupId);
        for (Long memberId : activeMembers) {
            dissolutionMapper.insertNotification(
                    memberId,
                    "DISSOLVE",
                    "계모임 해산 신청 반려 안내",
                    "[" + groupTitle + "] 해산 신청이 반려되어 모임이 운영 중 상태로 유지됩니다. (반려 사유: " + reasonText + ")",
                    "/operations?groupId=" + groupId,
                    dissolutionRequestId
            );
        }

        // 4) AUDIT_LOG 기록 (ACTOR_ADMIN_ID)
        dissolutionMapper.insertAdminAuditLog(
                groupId,
                adminId,
                "DISSOLVE_REQUEST",
                "계모임 해산 신청 반려 (사유: " + reasonText + ")"
        );

        return "해산 신청을 반려했어요.";
    }

    private void populateRefundEstimate(DissolutionDTO.StatusResponse item) {
        long total = item.getTotalConfirmedAmount() != null ? item.getTotalConfirmedAmount() : 0L;
        int count = (item.getMemberCount() != null && item.getMemberCount() > 0) ? item.getMemberCount() : 1;
        item.setPerMemberRefundAmount(total / count);
    }
}