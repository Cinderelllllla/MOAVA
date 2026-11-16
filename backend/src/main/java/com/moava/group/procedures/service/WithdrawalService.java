package com.moava.group.procedures.service;

import com.moava.group.procedures.dto.WithdrawalDTO;
import com.moava.group.procedures.mapper.WithdrawalMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public List<WithdrawalDTO.StatusResponse> getWithdrawalList(Long groupId) {
        return withdrawalMapper.selectWithdrawalList(groupId);
    }

    /**
     * 1. [신청] 일반 회원(모임장 제외)이 사유 입력 -> WITHDRAWAL_REQUEST(SETTLEMENT_PENDING)
     */
    @Transactional
    public String requestWithdrawal(Long groupId, WithdrawalDTO.ApplyForm form) {
        Map<String, Object> groupInfo = withdrawalMapper.selectGroupInfo(groupId);
        if (groupInfo == null) {
            throw new IllegalArgumentException("존재하지 않는 모임입니다.");
        }

        Long ownerId = ((Number) groupInfo.get("OWNER_ID")).longValue();
        // [예외 1] 모임장 신청 차단 -> '일반 참여 회원만 신청할 수 있어요'(승계 후 가능)
        if (ownerId.equals(form.getMemberId())) {
            throw new IllegalStateException("일반 참여 회원만 신청할 수 있어요");
        }

        // 모임원 상태 및 곗돈 수령 여부 확인
        Map<String, Object> memberInfo = withdrawalMapper.selectGyeMemberInfo(groupId, form.getMemberId());
        if (memberInfo == null || !"ACTIVE".equals(memberInfo.get("STATUS"))) {
            throw new IllegalStateException("현재 모임의 활성(ACTIVE) 참여 회원이 아닙니다.");
        }

        // [예외 2] 진행 중 중복 신청 차단
        int activeCount = withdrawalMapper.countActiveWithdrawalByMember(groupId, form.getMemberId());
        if (activeCount > 0) {
            throw new IllegalStateException("이미 진행 중인 중도 탈퇴 신청 건이 있습니다.");
        }

        // 사유 분류 및 상세 사유 검증
        if (form.getReasonType() == null || !VALID_REASON_TYPES.contains(form.getReasonType())) {
            throw new IllegalArgumentException("탈퇴 사유 분류(ECONOMIC/PERSONAL/OTHER)를 올바르게 선택해 주세요.");
        }
        if (form.getDetail() == null || form.getDetail().trim().isEmpty()) {
            throw new IllegalArgumentException("상세 탈퇴 사유를 입력해 주세요.");
        }

        // [예외 3] 수령 완료자(PAYOUT_RECEIVED_YN='Y')는 특별 정산 예외 요청(SPECIAL_YN='Y')만 가능
        String payoutReceivedYn = String.valueOf(memberInfo.get("PAYOUT_RECEIVED_YN"));
        String specialYn = "Y".equalsIgnoreCase(form.getSpecialYn()) ? "Y" : "N";
        if ("Y".equals(payoutReceivedYn) && !"Y".equals(specialYn)) {
            throw new IllegalStateException("곗돈 수령 완료자는 일반 탈퇴가 불가하며, 특별 정산 예외 요청만 가능합니다.");
        }

        withdrawalMapper.insertWithdrawalRequest(
                groupId,
                form.getMemberId(),
                form.getReasonType(),
                form.getDetail().trim(),
                specialYn
        );

        // 모임장에게 탈퇴 신청 알림 발송
        withdrawalMapper.insertNotification(
                ownerId,
                "CONSENT_REQUEST",
                "회원 중도 탈퇴 신청 접수",
                "모임원의 중도 탈퇴 신청이 접수되었습니다. 정산안을 등록해 주세요.",
                "/operations?groupId=" + groupId,
                null
        );

        // 감사 로그 기록 (WITHDRAW_REQUEST)
        withdrawalMapper.insertAuditLog(
                groupId,
                form.getMemberId(),
                form.getMemberId(),
                "WITHDRAW_REQUEST",
                "중도 탈퇴 신청 (사유: " + form.getReasonType() + ", 특별정산여부: " + specialYn + ")"
        );

        return "중도 탈퇴 신청이 접수되었습니다. 모임장의 정산안 등록 후 회원 동의가 진행됩니다.";
    }

    /**
     * 2. [정산안] 모임장이 SETTLEMENT_AMOUNT(+환급/-추가 납부)·MEMO 등록 -> CONSENT_PENDING 전환 및 알림
     */
    @Transactional
    public void registerSettlementPlan(Long groupId, Long withdrawalRequestId, WithdrawalDTO.SettlementForm form) {
        Map<String, Object> groupInfo = withdrawalMapper.selectGroupInfo(groupId);
        if (groupInfo == null) {
            throw new IllegalArgumentException("존재하지 않는 모임입니다.");
        }

        Long ownerId = ((Number) groupInfo.get("OWNER_ID")).longValue();
        if (!ownerId.equals(form.getLeaderId())) {
            throw new IllegalStateException("모임장만 정산안을 등록할 수 있습니다.");
        }

        // [제약조건] 정산 금액 없이 동의 요청 불가
        if (form.getSettlementAmount() == null) {
            throw new IllegalArgumentException("정산 금액을 반드시 입력해야 정산안 동의 요청이 가능합니다.");
        }
        if (form.getSettlementMemo() == null || form.getSettlementMemo().trim().isEmpty()) {
            throw new IllegalArgumentException("정산 산출 근거(메모)를 입력해 주세요.");
        }

        Map<String, Object> req = withdrawalMapper.selectWithdrawalById(withdrawalRequestId);
        if (req == null) {
            throw new IllegalArgumentException("탈퇴 신청 내역을 찾을 수 없습니다.");
        }
        if ("APPROVED".equals(req.get("STATUS"))) {
            throw new IllegalStateException("이미 승인 완료된 탈퇴 신청 건입니다.");
        }

        Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();

        // 정산안 금액·근거 업데이트 및 CONSENT_PENDING 전환
        withdrawalMapper.updateSettlementPlan(
                withdrawalRequestId,
                form.getSettlementAmount(),
                form.getSettlementMemo().trim(),
                form.getLeaderId()
        );

        // 기존 동의 기록 초기화 후 나머지 ACTIVE 회원들에게 '정산안 동의 요청' 알림 발송
        withdrawalMapper.deleteSettlementConsents(withdrawalRequestId);
        List<Long> remainingMembers = withdrawalMapper.selectOtherActiveMemberIds(groupId, applicantId);

        String amountDesc = (form.getSettlementAmount() >= 0 ? "+" : "") + form.getSettlementAmount() + "원";
        for (Long targetMemberId : remainingMembers) {
            withdrawalMapper.insertNotification(
                    targetMemberId,
                    "CONSENT_REQUEST",
                    "중도 탈퇴 정산안 동의 요청",
                    "중도 탈퇴 정산안(" + amountDesc + ")이 등록되었습니다. 확인 후 동의 여부를 선택해 주세요.",
                    "/operations?groupId=" + groupId,
                    withdrawalRequestId
            );
        }

        // 감사 로그 기록 (SETTLEMENT_SET)
        withdrawalMapper.insertAuditLog(
                groupId,
                form.getLeaderId(),
                applicantId,
                "SETTLEMENT_SET",
                "중도 탈퇴 정산안 등록 및 전원 동의 요청 (정산금액: " + amountDesc + ")"
        );
    }

    /**
     * 3. [동의] 나머지 회원 -> CONSENT(type=SETTLEMENT)
     */
    @Transactional
    public String submitSettlementConsent(Long groupId, Long withdrawalRequestId, WithdrawalDTO.ConsentForm form) {
        Map<String, Object> req = withdrawalMapper.selectWithdrawalById(withdrawalRequestId);
        if (req == null || !"CONSENT_PENDING".equals(req.get("STATUS"))) {
            throw new IllegalStateException("현재 정산안 동의 진행 중인 신청 건이 아닙니다.");
        }

        Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();
        if (applicantId.equals(form.getMemberId())) {
            throw new IllegalStateException("탈퇴 신청자 본인은 동의 대상이 아닙니다.");
        }

        String yn = "Y".equalsIgnoreCase(form.getAgreedYn()) ? "Y" : "N";
        withdrawalMapper.upsertSettlementConsent(groupId, withdrawalRequestId, form.getMemberId(), yn);

        // 감사 로그 기록 (SETTLEMENT_CONSENT)
        withdrawalMapper.insertAuditLog(
                groupId,
                form.getMemberId(),
                applicantId,
                "SETTLEMENT_CONSENT",
                "중도 탈퇴 정산안 동의 제출: " + yn + " (신청번호: " + withdrawalRequestId + ")"
        );

        if ("N".equals(yn)) {
            withdrawalMapper.updateWithdrawalStatus(withdrawalRequestId, "REJECTED");
            return "정산안에 반대(N) 처리되어 재조정 상태가 되었습니다.";
        }

        return "중도 탈퇴 정산안에 공식 동의하셨습니다.";
    }

    /**
     * 4. [재확인] 신청자가 '탈퇴 재확인 요청' -> RECONFIRM_COUNT+1, 모임장 알림
     */
    @Transactional
    public void reconfirmWithdrawal(Long groupId, Long withdrawalRequestId, Long memberId) {
        Map<String, Object> req = withdrawalMapper.selectWithdrawalById(withdrawalRequestId);
        if (req == null) {
            throw new IllegalArgumentException("탈퇴 신청 내역을 찾을 수 없습니다.");
        }

        Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();
        if (!applicantId.equals(memberId)) {
            throw new IllegalStateException("탈퇴 신청자 본인만 재확인 요청을 보낼 수 있습니다.");
        }
        if ("APPROVED".equals(req.get("STATUS"))) {
            throw new IllegalStateException("이미 승인 완료된 신청 건입니다.");
        }

        withdrawalMapper.incrementReconfirmCount(withdrawalRequestId);

        Map<String, Object> groupInfo = withdrawalMapper.selectGroupInfo(groupId);
        Long ownerId = ((Number) groupInfo.get("OWNER_ID")).longValue();

        withdrawalMapper.insertNotification(
                ownerId,
                "CONSENT_REQUEST",
                "중도 탈퇴 재확인 요청",
                "탈퇴 신청 회원이 정산 및 탈퇴 처리 재확인을 요청했습니다.",
                "/operations?groupId=" + groupId,
                withdrawalRequestId
        );
    }

    /**
     * 5. [승인] 모임장: 전원 동의 검증 -> APPROVED, GYE_MEMBER.STATUS=WITHDRAWN, 알림, AUDIT_LOG(WITHDRAW_APPROVE)
     */
    @Transactional
    public void approveWithdrawal(Long groupId, Long withdrawalRequestId, Long leaderId) {
        Map<String, Object> groupInfo = withdrawalMapper.selectGroupInfo(groupId);
        if (groupInfo == null) {
            throw new IllegalArgumentException("존재하지 않는 모임입니다.");
        }

        Long ownerId = ((Number) groupInfo.get("OWNER_ID")).longValue();
        if (!ownerId.equals(leaderId)) {
            throw new IllegalStateException("모임장만 탈퇴를 최종 승인할 수 있습니다.");
        }

        Map<String, Object> req = withdrawalMapper.selectWithdrawalById(withdrawalRequestId);
        if (req == null) {
            throw new IllegalArgumentException("탈퇴 신청 내역을 찾을 수 없습니다.");
        }

        // 정산 금액이 등록되어 있는지 확인
        if (req.get("SETTLEMENT_AMOUNT") == null || req.get("SETTLEMENT_AT") == null) {
            throw new IllegalStateException("정산 금액 등록 없이는 탈퇴를 승인할 수 없습니다.");
        }

        Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();

        // [예외 4] 미동의 승인 차단 -> '필요한 공식 동의가 완료되지 않았어요'
        int targetCount = withdrawalMapper.countRemainingActiveMembers(groupId, applicantId);
        int agreedCount = withdrawalMapper.countAgreedSettlementConsents(withdrawalRequestId);

        if (agreedCount < targetCount) {
            throw new IllegalStateException("필요한 공식 동의가 완료되지 않았어요");
        }

        // 1) WITHDRAWAL_REQUEST.STATUS = 'APPROVED'
        withdrawalMapper.updateWithdrawalStatus(withdrawalRequestId, "APPROVED");

        // 2) GYE_MEMBER.STATUS = 'WITHDRAWN', LEFT_AT = SYSDATE
        withdrawalMapper.updateGyeMemberWithdrawn(groupId, applicantId);

        // 3) 신청자 및 모임원들에게 탈퇴 승인 알림 발송
        withdrawalMapper.insertNotification(
                applicantId,
                "CONSENT_REQUEST",
                "중도 탈퇴 최종 승인 안내",
                "전원 정산안 동의가 완료되어 모임 중도 탈퇴가 최종 승인되었습니다.",
                "/operations?groupId=" + groupId,
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
}