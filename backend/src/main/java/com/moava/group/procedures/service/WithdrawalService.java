package com.moava.group.procedures.service;

import com.moava.group.procedures.dto.WithdrawalDTO;
import com.moava.group.procedures.exception.ProcedureException;
import com.moava.group.procedures.mapper.WithdrawalMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WithdrawalService {

    private static final Set<String> VALID_REASON_TYPES = Set.of("ECONOMIC", "PERSONAL", "OTHER");

    private final WithdrawalMapper withdrawalMapper;

    public WithdrawalService(WithdrawalMapper withdrawalMapper) {
        this.withdrawalMapper = withdrawalMapper;
    }

    /**
     * 탈퇴 신청 및 정산안 동의 현황 목록 조회
     */
    @Transactional(readOnly = true)
    public List<WithdrawalDTO.StatusResponse> getWithdrawalList(Long groupId, Long memberId) {
        Map<String, Object> groupInfo = getGroupInfo(groupId);
        if (!isLeader(groupInfo, memberId)) {
            requireActiveMember(groupId, memberId);
        }
        return withdrawalMapper.selectWithdrawalList(groupId);
    }

    /**
     * 1. [신청] 일반 회원(모임장 제외)이 사유 입력 -> WITHDRAWAL_REQUEST(SETTLEMENT_PENDING)
     */
    @Transactional
    public String requestWithdrawal(Long groupId, Long memberId, WithdrawalDTO.ApplyForm form) {
        if (form == null || memberId == null) {
            throw ProcedureException.badRequest("탈퇴 신청 정보를 확인해 주세요.");
        }
        Map<String, Object> groupInfo = getActiveGroupForUpdate(groupId);
        requireActiveGroup(groupInfo);

        Long ownerId = getLong(groupInfo, "OWNER_ID");
        // [예외 1] 모임장 신청 차단 -> '일반 참여 회원만 신청할 수 있어요'(승계 후 가능)
        if (ownerId.equals(memberId)) {
            throw ProcedureException.forbidden("일반 참여 회원만 신청할 수 있어요");
        }

        // 모임원 상태 및 곗돈 수령 여부 확인
        Map<String, Object> memberInfo = withdrawalMapper.selectGyeMemberInfo(groupId, memberId);
        if (memberInfo == null || !"ACTIVE".equals(memberInfo.get("STATUS"))) {
            throw ProcedureException.forbidden("현재 모임의 활성(ACTIVE) 참여 회원이 아닙니다.");
        }

        // [예외 2] 진행 중 중복 신청 차단
        int activeCount = withdrawalMapper.countActiveWithdrawalByMember(groupId, memberId);
        if (activeCount > 0) {
            throw ProcedureException.conflict("이미 진행 중인 중도 탈퇴 신청 건이 있습니다.");
        }

        // 사유 분류 및 상세 사유 검증
        String reasonType = form.getEffectiveReasonType();
        String detail = form.getEffectiveDetail();
        String specialYn = form.getEffectiveSpecialYn();
        if (!VALID_REASON_TYPES.contains(reasonType)) {
            throw ProcedureException.badRequest("탈퇴 사유 분류(ECONOMIC/PERSONAL/OTHER)를 올바르게 선택해 주세요.");
        }
        if (detail == null || detail.trim().isEmpty()) {
            throw ProcedureException.badRequest("상세 탈퇴 사유를 입력해 주세요.");
        }
        String cleanDetail = detail.trim();
        if (cleanDetail.getBytes(StandardCharsets.UTF_8).length > 1000) {
            throw ProcedureException.badRequest("상세 탈퇴 사유는 1000바이트 이하여야 합니다.");
        }

        // [예외 3] 수령 완료자(PAYOUT_RECEIVED_YN='Y')는 특별 정산 예외 요청(SPECIAL_YN='Y')만 가능
        String payoutReceivedYn = String.valueOf(memberInfo.get("PAYOUT_RECEIVED_YN"));
        if ("Y".equals(payoutReceivedYn) && !"Y".equals(specialYn)) {
            throw ProcedureException.conflict("곗돈 수령 완료자는 일반 탈퇴가 불가하며, 특별 정산 예외 요청만 가능합니다.");
        }

        withdrawalMapper.insertWithdrawalRequest(
                groupId,
                memberId,
                reasonType,
                cleanDetail,
                specialYn
        );

        // 모임장에게 탈퇴 신청 알림 발송
        withdrawalMapper.insertNotification(
                ownerId,
                "WITHDRAW_REQUEST",
                "회원 중도 탈퇴 신청 접수",
                "모임원의 중도 탈퇴 신청이 접수되었습니다. 정산안을 등록해 주세요.",
                "/procedures?groupId=" + groupId,
                null
        );

        // 감사 로그 기록 (WITHDRAW_REQUEST)
        withdrawalMapper.insertAuditLog(
                groupId,
                memberId,
                memberId,
                "WITHDRAW_REQUEST",
                "중도 탈퇴 신청 (사유: " + reasonType + ", 특별정산여부: " + specialYn + ")"
        );

        return "중도 탈퇴 신청이 접수되었습니다. 모임장의 정산안 등록 후 회원 동의가 진행됩니다.";
    }

    /**
     * 2. [정산안] 모임장이 SETTLEMENT_AMOUNT(+환급/-추가 납부)·MEMO 등록 -> CONSENT_PENDING 전환 및 알림
     */
    @Transactional
    public void registerSettlementPlan(
            Long groupId, Long withdrawalRequestId, Long leaderId, WithdrawalDTO.SettlementForm form) {
        if (form == null || leaderId == null) {
            throw ProcedureException.badRequest("정산안 정보를 확인해 주세요.");
        }
        Map<String, Object> groupInfo = getActiveGroupForUpdate(groupId);
        requireActiveGroup(groupInfo);

        Long ownerId = getLong(groupInfo, "OWNER_ID");
        if (!ownerId.equals(leaderId)) {
            throw ProcedureException.forbidden("모임장만 정산안을 등록할 수 있습니다.");
        }

        Long settlementAmount = form.getEffectiveAmount();
        String settlementMemo = form.getEffectiveMemo();
        // [제약조건] 정산 금액 없이 동의 요청 불가 + Oracle NUMBER(12) 범위 검증
        if (settlementAmount == null) {
            throw ProcedureException.badRequest("정산 금액을 반드시 입력해야 정산안 동의 요청이 가능합니다.");
        }
        if (settlementAmount < -999_999_999_999L || settlementAmount > 999_999_999_999L) {
            throw ProcedureException.badRequest("정산 금액 입력 범위를 초과했습니다.");
        }
        if (settlementMemo == null || settlementMemo.trim().isEmpty()) {
            throw ProcedureException.badRequest("정산 산출 근거(메모)를 입력해 주세요.");
        }
        String cleanMemo = settlementMemo.trim();
        if (cleanMemo.getBytes(StandardCharsets.UTF_8).length > 500) {
            throw ProcedureException.badRequest("정산 산출 근거(메모)는 500바이트 이하여야 합니다.");
        }

        Map<String, Object> req = getWithdrawalRequestForUpdate(groupId, withdrawalRequestId);
        requireStatus(req, "SETTLEMENT_PENDING", "CONSENT_PENDING", "REJECTED");

        Long applicantId = getLong(req, "MEMBER_ID");
        String expectedStatus = String.valueOf(req.get("STATUS"));

        Map<String, Object> applicantInfo = withdrawalMapper.selectGyeMemberInfoForUpdate(groupId, applicantId);
        if (applicantInfo == null || !"ACTIVE".equals(applicantInfo.get("STATUS"))) {
            throw ProcedureException.conflict("탈퇴 신청자가 현재 모임의 활성 회원이 아닙니다.");
        }
        if ("REJECTED".equals(expectedStatus)
                && withdrawalMapper.countActiveWithdrawalByMember(groupId, applicantId) > 0) {
            throw ProcedureException.conflict("이미 진행 중인 다른 중도 탈퇴 신청 건이 있습니다.");
        }

        // 정산안 금액·근거 업데이트 및 CONSENT_PENDING 전환
        if (withdrawalMapper.updateSettlementPlan(
                groupId,
                withdrawalRequestId,
                expectedStatus,
                settlementAmount,
                cleanMemo,
                leaderId
        ) == 0) {
            throw ProcedureException.conflict("정산안 상태가 변경되어 다시 확인해 주세요.");
        }

        // 기존 기록을 초기화하고 이 정산안의 동의 대상을 고정
        withdrawalMapper.deleteSettlementConsents(withdrawalRequestId);
        withdrawalMapper.deleteSettlementConsentTargets(withdrawalRequestId);
        withdrawalMapper.insertSettlementConsentTargets(groupId, withdrawalRequestId, applicantId);
        List<WithdrawalDTO.ConsentTargetResponse> targets =
                withdrawalMapper.selectSettlementConsentTargets(groupId, withdrawalRequestId);

        String amountDesc = (settlementAmount >= 0 ? "+" : "") + settlementAmount + "원";
        for (WithdrawalDTO.ConsentTargetResponse target : targets) {
            withdrawalMapper.insertNotification(
                    target.getMemberId(),
                    "CONSENT_REQ",
                    "중도 탈퇴 정산안 동의 요청",
                    "중도 탈퇴 정산안(" + amountDesc + ")이 등록되었습니다. 확인 후 동의 여부를 선택해 주세요.",
                    "/procedures?groupId=" + groupId,
                    withdrawalRequestId
            );
        }

        // 감사 로그 기록 (SETTLEMENT_SET)
        withdrawalMapper.insertAuditLog(
                groupId,
                leaderId,
                applicantId,
                "SETTLEMENT_SET",
                "중도 탈퇴 정산안 등록 및 전원 동의 요청 (정산금액: " + amountDesc + ")"
        );
    }

    /**
     * 3. [동의] 나머지 회원 -> CONSENT(type=SETTLEMENT)
     */
    @Transactional
    public String submitSettlementConsent(
            Long groupId, Long withdrawalRequestId, Long memberId, WithdrawalDTO.ConsentForm form) {
        if (memberId == null || form == null) {
            throw ProcedureException.badRequest("동의 정보를 확인해 주세요.");
        }
        Map<String, Object> groupInfo = getActiveGroupForUpdate(groupId);
        requireActiveGroup(groupInfo);
        Map<String, Object> req = getWithdrawalRequestForUpdate(groupId, withdrawalRequestId);
        requireStatus(req, "CONSENT_PENDING");

        Long applicantId = getLong(req, "MEMBER_ID");
        if (applicantId.equals(memberId)) {
            throw ProcedureException.forbidden("탈퇴 신청자 본인은 동의 대상이 아닙니다.");
        }
        Map<String, Object> memberInfo = withdrawalMapper.selectGyeMemberInfoForUpdate(groupId, memberId);
        if (memberInfo == null || !"ACTIVE".equals(memberInfo.get("STATUS"))) {
            throw ProcedureException.forbidden("현재 모임의 활성(ACTIVE) 참여 회원이 아닙니다.");
        }

        String agreedYn = form.getAgreedYn();
        if (!"Y".equalsIgnoreCase(agreedYn) && !"N".equalsIgnoreCase(agreedYn)) {
            throw ProcedureException.badRequest("동의 여부는 Y 또는 N으로 입력해야 합니다.");
        }
        String yn = "Y".equalsIgnoreCase(agreedYn) ? "Y" : "N";
        String targetStatus = "Y".equals(yn) ? "AGREED" : "REJECTED";
        if (withdrawalMapper.updateSettlementConsentTarget(
                groupId, withdrawalRequestId, memberId, targetStatus) == 0) {
            throw ProcedureException.conflict("이 정산안의 동의 대상이 아니거나 이미 응답을 완료했습니다.");
        }
        withdrawalMapper.upsertSettlementConsent(groupId, withdrawalRequestId, memberId, yn);

        // 감사 로그 기록 (SETTLEMENT_CONSENT)
        withdrawalMapper.insertAuditLog(
                groupId,
                memberId,
                applicantId,
                "SETTLEMENT_CONSENT",
                "중도 탈퇴 정산안 동의 제출: " + yn + " (신청번호: " + withdrawalRequestId + ")"
        );

        if ("N".equals(yn)) {
            if (withdrawalMapper.updateWithdrawalStatus(
                    groupId, withdrawalRequestId, "CONSENT_PENDING", "REJECTED") == 0) {
                throw ProcedureException.conflict("신청 상태가 변경되어 반대 처리를 완료할 수 없습니다.");
            }
            String linkPage = "/procedures?groupId=" + groupId;
            withdrawalMapper.insertNotification(
                    applicantId,
                    "CONSENT_RESULT",
                    "탈퇴 정산안 결과 안내",
                    "탈퇴 정산안에 반대가 발생하여 정산안 재조정이 필요합니다.",
                    linkPage,
                    withdrawalRequestId
            );
            Long ownerId = getLong(groupInfo, "OWNER_ID");
            if (!ownerId.equals(memberId)) {
                withdrawalMapper.insertNotification(
                        ownerId,
                        "CONSENT_RESULT",
                        "탈퇴 정산안 반대 발생",
                        "중도 탈퇴 정산안에 반대가 발생했습니다. 정산안을 재조정해 주세요.",
                        linkPage,
                        withdrawalRequestId
                );
            }
            return "정산안에 반대(N) 처리되어 재조정 상태가 되었습니다.";
        }

        return "중도 탈퇴 정산안에 공식 동의하셨습니다.";
    }

    @Transactional(readOnly = true)
    public List<WithdrawalDTO.ConsentTargetResponse> getSettlementConsentTargets(
            Long groupId, Long withdrawalRequestId, Long requesterId) {
        Map<String, Object> groupInfo = getGroupInfo(groupId);
        if (!isLeader(groupInfo, requesterId)) {
            requireActiveMember(groupId, requesterId);
        }
        getWithdrawalRequest(groupId, withdrawalRequestId);
        return withdrawalMapper.selectSettlementConsentTargets(groupId, withdrawalRequestId);
    }

    @Transactional(readOnly = true)
    public List<WithdrawalDTO.ConsentTargetResponse> getSettlementConsentTargetsForAdmin(
            Long groupId, Long withdrawalRequestId, Long adminId) {
        if (withdrawalMapper.isActiveAdmin(adminId) == 0) {
            throw ProcedureException.forbidden("활성 관리자만 동의 대상 목록을 조회할 수 있습니다.");
        }
        getWithdrawalRequest(groupId, withdrawalRequestId);
        return withdrawalMapper.selectSettlementConsentTargets(groupId, withdrawalRequestId);
    }

    @Transactional
    public void exemptSettlementConsentTarget(
            Long groupId, Long withdrawalRequestId, Long targetMemberId, Long adminId, String reason) {
        if (adminId == null) {
            throw ProcedureException.forbidden("관리자 정보를 확인해 주세요.");
        }
        if (reason == null || reason.trim().isEmpty() || reason.trim().length() > 200) {
            throw ProcedureException.badRequest("면제 사유는 1~200자 이내로 입력해 주세요.");
        }
        if (withdrawalMapper.isActiveAdmin(adminId) == 0) {
            throw ProcedureException.forbidden("활성 관리자만 동의 의무를 면제할 수 있습니다.");
        }

        requireActiveGroup(getActiveGroupForUpdate(groupId));
        Map<String, Object> req = getWithdrawalRequestForUpdate(groupId, withdrawalRequestId);
        requireStatus(req, "CONSENT_PENDING");
        if (withdrawalMapper.exemptSettlementConsentTarget(
                groupId, withdrawalRequestId, targetMemberId, adminId, reason.trim()) == 0) {
            throw ProcedureException.conflict("현재 ACTIVE 상태인 미응답 대상만 면제할 수 있습니다.");
        }

        withdrawalMapper.insertAdminAuditLog(
                groupId,
                adminId,
                targetMemberId,
                "WITHDRAW_CONSENT_EXEMPT",
                "탈퇴 정산 동의 의무 면제 (신청번호: " + withdrawalRequestId + ", 사유: " + reason.trim() + ")"
        );
    }

    /**
     * 4. [재확인] 신청자가 '탈퇴 재확인 요청' -> RECONFIRM_COUNT+1, 모임장 알림
     */
    @Transactional
    public void reconfirmWithdrawal(Long groupId, Long withdrawalRequestId, Long memberId) {
        if (memberId == null) {
            throw ProcedureException.forbidden("로그인 정보를 확인해 주세요.");
        }
        Map<String, Object> groupInfo = getActiveGroupForUpdate(groupId);
        requireActiveGroup(groupInfo);
        Map<String, Object> req = getWithdrawalRequestForUpdate(groupId, withdrawalRequestId);
        requireStatus(req, "SETTLEMENT_PENDING", "CONSENT_PENDING");

        Long applicantId = getLong(req, "MEMBER_ID");
        if (!applicantId.equals(memberId)) {
            throw ProcedureException.forbidden("탈퇴 신청자 본인만 재확인 요청을 보낼 수 있습니다.");
        }
        requireActiveMember(groupId, memberId);
        Object reconfirmCount = req.get("RECONFIRM_COUNT");
        if (reconfirmCount instanceof Number && ((Number) reconfirmCount).intValue() >= 99) {
            throw ProcedureException.conflict("재확인 요청 가능 횟수를 초과했습니다.");
        }

        withdrawalMapper.incrementReconfirmCount(withdrawalRequestId);

        Long ownerId = getLong(groupInfo, "OWNER_ID");

        withdrawalMapper.insertNotification(
                ownerId,
                "WITHDRAW_REQUEST",
                "중도 탈퇴 재확인 요청",
                "탈퇴 신청 회원이 정산 및 탈퇴 처리 재확인을 요청했습니다.",
                "/procedures?groupId=" + groupId,
                withdrawalRequestId
        );
    }

    /**
     * 5. [승인] 모임장: 전원 동의 검증 -> APPROVED, GYE_MEMBER.STATUS=WITHDRAWN, 알림, AUDIT_LOG(WITHDRAW_APPROVE)
     */
    @Transactional
    public void approveWithdrawal(Long groupId, Long withdrawalRequestId, Long leaderId) {
        if (leaderId == null) {
            throw ProcedureException.forbidden("로그인 정보를 확인해 주세요.");
        }
        Map<String, Object> groupInfo = getActiveGroupForUpdate(groupId);
        requireActiveGroup(groupInfo);

        Long ownerId = getLong(groupInfo, "OWNER_ID");
        if (!ownerId.equals(leaderId)) {
            throw ProcedureException.forbidden("모임장만 탈퇴를 최종 승인할 수 있습니다.");
        }

        Map<String, Object> req = getWithdrawalRequestForUpdate(groupId, withdrawalRequestId);
        requireStatus(req, "CONSENT_PENDING");

        // 정산 금액이 등록되어 있는지 확인
        if (req.get("SETTLEMENT_AMOUNT") == null || req.get("SETTLEMENT_AT") == null) {
            throw ProcedureException.conflict("정산 금액 등록 없이는 탈퇴를 승인할 수 없습니다.");
        }

        Long applicantId = getLong(req, "MEMBER_ID");
        Map<String, Object> applicantInfo = withdrawalMapper.selectGyeMemberInfoForUpdate(groupId, applicantId);
        if (applicantInfo == null || !"ACTIVE".equals(applicantInfo.get("STATUS"))) {
            throw ProcedureException.conflict("탈퇴 신청자가 현재 모임의 활성 회원이 아닙니다.");
        }
        // 탈퇴 후 재제출·심사 불가로 남아 해산 승인까지 영구 차단되는 미종결 증빙을 사전에 차단
        if (withdrawalMapper.countUnconfirmedProofsByMember(groupId, applicantId) > 0) {
            throw ProcedureException.conflict("신청자의 확인 대기 또는 보완 요청 증빙을 먼저 처리해 주세요.");
        }

        // 고정 대상 중 현재 ACTIVE인 회원만 확인하며, 이미 탈퇴한 회원은 동의 의무에서 제외
        withdrawalMapper.lockActiveSettlementConsentTargets(groupId, withdrawalRequestId);
        if (withdrawalMapper.countSettlementConsentTargets(withdrawalRequestId) == 0) {
            throw ProcedureException.conflict("저장된 동의 대상이 없습니다. 정산안을 다시 등록해 주세요.");
        }
        if (withdrawalMapper.countUnresolvedActiveSettlementConsentTargets(groupId, withdrawalRequestId) > 0) {
            throw ProcedureException.conflict("필요한 공식 동의가 완료되지 않았어요");
        }

        // 1) WITHDRAWAL_REQUEST.STATUS = 'APPROVED'
        if (withdrawalMapper.updateWithdrawalStatus(
                groupId, withdrawalRequestId, "CONSENT_PENDING", "APPROVED") == 0) {
            throw ProcedureException.conflict("신청 상태가 변경되어 승인할 수 없습니다.");
        }

        // 2) GYE_MEMBER.STATUS = 'WITHDRAWN', LEFT_AT = SYSDATE
        if (withdrawalMapper.updateGyeMemberWithdrawn(groupId, applicantId) == 0) {
            throw ProcedureException.conflict("탈퇴 신청자의 회원 상태가 변경되어 승인할 수 없습니다.");
        }

        // 2-1) 탈퇴 회원의 미확정 수령 순서 신청 정리
        withdrawalMapper.rejectOpenPayoutRequestsByMember(groupId, applicantId);

        // 3) 신청자에게 탈퇴 최종 승인 알림 발송
        withdrawalMapper.insertNotification(
                applicantId,
                "WITHDRAW_DONE",
                "중도 탈퇴 최종 승인 안내",
                "전원 정산안 동의가 완료되어 모임 중도 탈퇴가 최종 승인되었습니다.",
                "/my-groups",
                withdrawalRequestId
        );

        // 4) AUDIT_LOG (WITHDRAW_APPROVE) 기록
        withdrawalMapper.insertAuditLog(
                groupId,
                leaderId,
                applicantId,
                "WITHDRAW_APPROVE",
                "전원 동의 확인 및 중도 탈퇴 최종 승인 완료 (신청번호: " + withdrawalRequestId + ")"
        );
    }

    private Map<String, Object> getGroupInfo(Long groupId) {
        Map<String, Object> groupInfo = withdrawalMapper.selectGroupInfo(groupId);
        if (groupInfo == null) {
            throw ProcedureException.notFound("존재하지 않는 모임입니다.");
        }
        return groupInfo;
    }

    private Map<String, Object> getActiveGroupForUpdate(Long groupId) {
        Map<String, Object> groupInfo = withdrawalMapper.selectGroupInfoForUpdate(groupId);
        if (groupInfo == null) {
            throw ProcedureException.notFound("존재하지 않는 모임입니다.");
        }
        return groupInfo;
    }

    private Map<String, Object> getWithdrawalRequest(Long groupId, Long withdrawalRequestId) {
        Map<String, Object> req = withdrawalMapper.selectWithdrawalById(withdrawalRequestId);
        if (req == null) {
            throw ProcedureException.notFound("탈퇴 신청 내역을 찾을 수 없습니다.");
        }
        if (!groupId.equals(getLong(req, "GROUP_ID"))) {
            throw ProcedureException.notFound("해당 모임의 탈퇴 신청 내역이 아닙니다.");
        }
        return req;
    }

    private Map<String, Object> getWithdrawalRequestForUpdate(Long groupId, Long withdrawalRequestId) {
        Map<String, Object> req = withdrawalMapper.selectWithdrawalByIdForUpdate(groupId, withdrawalRequestId);
        if (req == null) {
            throw ProcedureException.notFound("탈퇴 신청 내역을 찾을 수 없습니다.");
        }
        if (!groupId.equals(getLong(req, "GROUP_ID"))) {
            throw ProcedureException.notFound("해당 모임의 탈퇴 신청 내역이 아닙니다.");
        }
        return req;
    }

    private void requireStatus(Map<String, Object> req, String... allowedStatuses) {
        String status = String.valueOf(req.get("STATUS"));
        for (String allowedStatus : allowedStatuses) {
            if (allowedStatus.equals(status)) {
                return;
            }
        }
        throw ProcedureException.conflict("현재 상태에서는 요청을 처리할 수 없습니다.");
    }

    private void requireActiveMember(Long groupId, Long memberId) {
        Map<String, Object> memberInfo = withdrawalMapper.selectGyeMemberInfo(groupId, memberId);
        if (memberInfo == null || !"ACTIVE".equals(memberInfo.get("STATUS"))) {
            throw ProcedureException.forbidden("현재 모임의 활성(ACTIVE) 참여 회원이 아닙니다.");
        }
    }

    private void requireActiveGroup(Map<String, Object> groupInfo) {
        if (!"ACTIVE".equals(groupInfo.get("STATUS"))) {
            throw ProcedureException.conflict("운영 중인 모임에서만 탈퇴 절차를 진행할 수 있습니다.");
        }
    }

    private boolean isLeader(Map<String, Object> groupInfo, Long memberId) {
        return memberId != null && getLong(groupInfo, "OWNER_ID").equals(memberId);
    }

    private Long getLong(Map<String, Object> record, String column) {
        Object value = record.get(column);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("필수 데이터가 올바르지 않습니다: " + column);
        }
        return ((Number) value).longValue();
    }
}