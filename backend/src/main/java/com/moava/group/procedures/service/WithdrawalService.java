package com.moava.group.procedures.service;

import com.moava.group.procedures.dto.WithdrawalDTO;
import com.moava.group.procedures.mapper.WithdrawalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private static final Set<String> VALID_REASON_TYPES = Set.of("ECONOMIC", "PERSONAL", "OTHER");

    private final WithdrawalMapper withdrawalMapper;

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
        if (form == null || form.getEffectiveMemberId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "탈퇴 신청 정보를 입력해 주세요.");
        }
        Map<String, Object> groupInfo = withdrawalMapper.selectGroupInfo(groupId);
        if (groupInfo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다.");
        }
        if (!"ACTIVE".equals(groupInfo.get("STATUS"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "운영 중인 모임에서만 탈퇴를 신청할 수 있습니다.");
        }

        Long ownerId = ((Number) groupInfo.get("OWNER_ID")).longValue();
        Long memberId = form.getEffectiveMemberId();
        // [예외 1] 모임장 신청 차단 -> '일반 참여 회원만 신청할 수 있어요'(승계 후 가능)
        if (ownerId.equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "일반 참여 회원만 신청할 수 있어요.");
        }

        // 모임원 상태 및 곗돈 수령 여부 확인
        Map<String, Object> memberInfo = withdrawalMapper.selectGyeMemberInfo(groupId, memberId);
        if (memberInfo == null || !"ACTIVE".equals(memberInfo.get("STATUS"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "현재 모임의 활성(ACTIVE) 참여 회원이 아닙니다.");
        }

        // [예외 2] 진행 중 중복 신청 차단
        int activeCount = withdrawalMapper.countActiveWithdrawalByMember(groupId, memberId);
        if (activeCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 진행 중인 중도 탈퇴 신청 건이 있습니다.");
        }

        // 사유 분류 및 상세 사유 검증
        String reasonType = form.getEffectiveReasonType();
        String detail = form.getEffectiveDetail();
        String specialYn = form.getEffectiveSpecialYn();
        if (!VALID_REASON_TYPES.contains(reasonType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "탈퇴 사유 분류(ECONOMIC/PERSONAL/OTHER)를 올바르게 선택해 주세요.");
        }
        if (specialYn == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "특별 정산 여부는 Y 또는 N으로 입력해 주세요.");
        }
        if (detail == null || detail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상세 탈퇴 사유를 입력해 주세요.");
        }
        detail = detail.trim();
        if (detail.getBytes(StandardCharsets.UTF_8).length > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "상세 탈퇴 사유는 1000바이트 이하여야 합니다.");
        }
        // [예외 3] 수령 완료자(PAYOUT_RECEIVED_YN='Y')는 특별 정산 예외 요청(SPECIAL_YN='Y')만 가능
        String payoutReceivedYn = String.valueOf(memberInfo.get("PAYOUT_RECEIVED_YN"));
        if ("Y".equals(payoutReceivedYn) && !"Y".equals(specialYn)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "곗돈 수령 완료자는 일반 탈퇴가 불가하며, 특별 정산 예외 요청만 가능합니다.");
        }

        withdrawalMapper.insertWithdrawalRequest(
                groupId,
                memberId,
                reasonType,
                detail,
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
    public void registerSettlementPlan(Long groupId, Long withdrawalRequestId, WithdrawalDTO.SettlementForm form) {
        if (form == null || form.getEffectiveLeaderId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정산안 정보를 입력해 주세요.");
        }
        Map<String, Object> groupInfo = withdrawalMapper.selectGroupInfo(groupId);
        if (groupInfo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다.");
        }
        if (!"ACTIVE".equals(groupInfo.get("STATUS"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "운영 중인 모임에서만 정산안을 변경할 수 있습니다.");
        }

        Long ownerId = ((Number) groupInfo.get("OWNER_ID")).longValue();
        Long leaderId = form.getEffectiveLeaderId();
        if (!ownerId.equals(leaderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "모임장만 정산안을 등록할 수 있습니다.");
        }

        // [제약조건] 정산 금액 없이 동의 요청 불가
        Long settlementAmount = form.getEffectiveAmount();
        String settlementMemo = form.getEffectiveMemo();
        if (settlementAmount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "정산 금액을 반드시 입력해야 정산안 동의 요청이 가능합니다.");
        }
        if (settlementAmount < -999_999_999_999L || settlementAmount > 999_999_999_999L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정산 금액은 ±999,999,999,999원 이하여야 합니다.");
        }
        if (settlementMemo == null || settlementMemo.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정산 산출 근거(메모)를 입력해 주세요.");
        }
        settlementMemo = settlementMemo.trim();
        if (settlementMemo.getBytes(StandardCharsets.UTF_8).length > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "정산 산출 근거는 500바이트 이하여야 합니다.");
        }

        Map<String, Object> req = withdrawalMapper.selectWithdrawalById(groupId, withdrawalRequestId);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "탈퇴 신청 내역을 찾을 수 없습니다.");
        }

        Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();

        // 정산안 금액·근거 업데이트 및 CONSENT_PENDING 전환
        if (withdrawalMapper.updateSettlementPlan(
                groupId,
                withdrawalRequestId,
                settlementAmount,
                settlementMemo,
                leaderId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "정산안을 제출할 수 없는 상태입니다. 신청 상태를 새로고침해 주세요.");
        }

        // 기존 동의 기록 초기화 후 나머지 ACTIVE 회원들에게 '정산안 동의 요청' 알림 발송
        withdrawalMapper.deleteSettlementConsents(withdrawalRequestId);
        List<Long> remainingMembers = withdrawalMapper.selectOtherActiveMemberIds(groupId, applicantId);

        String amountDesc = (settlementAmount >= 0 ? "+" : "") + settlementAmount + "원";
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
    public String submitSettlementConsent(Long groupId, Long withdrawalRequestId, WithdrawalDTO.ConsentForm form) {
        if (form == null || form.getEffectiveMemberId() == null
                || form.getAgreedYn() == null
                || !("Y".equalsIgnoreCase(form.getAgreedYn()) || "N".equalsIgnoreCase(form.getAgreedYn()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동의 여부는 Y 또는 N으로 입력해 주세요.");
        }
        Long memberId = form.getEffectiveMemberId();
        Map<String, Object> req = withdrawalMapper.selectWithdrawalById(groupId, withdrawalRequestId);
        if (req == null || !"CONSENT_PENDING".equals(req.get("STATUS"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "현재 정산안 동의 진행 중인 신청 건이 아닙니다.");
        }

        Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();
        if (applicantId.equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "탈퇴 신청자 본인은 동의 대상이 아닙니다.");
        }

        String yn = form.getAgreedYn().toUpperCase(java.util.Locale.ROOT);
        if (withdrawalMapper.upsertSettlementConsent(groupId, withdrawalRequestId, memberId, yn) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 모임의 ACTIVE 동의 대상 회원만 응답할 수 있습니다.");
        }

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
                throw new ResponseStatusException(HttpStatus.CONFLICT, "정산안 상태가 변경되어 반려할 수 없습니다.");
            }
            return "정산안에 반대(N) 처리되어 재조정 상태가 되었습니다.";
        }

        return "중도 탈퇴 정산안에 공식 동의하셨습니다.";
    }

    /**
     * 4. [재확인] 신청자가 '탈퇴 재확인 요청' -> RECONFIRM_COUNT+1, 모임장 알림
     */
    @Transactional
    public void reconfirmWithdrawal(Long groupId, Long withdrawalRequestId, Long memberId) {
        if (memberId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원 정보가 필요합니다.");
        }
        Map<String, Object> req = withdrawalMapper.selectWithdrawalById(groupId, withdrawalRequestId);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "탈퇴 신청 내역을 찾을 수 없습니다.");
        }

        Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();
        if (!applicantId.equals(memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "탈퇴 신청자 본인만 재확인 요청을 보낼 수 있습니다.");
        }

        if (withdrawalMapper.incrementReconfirmCount(groupId, withdrawalRequestId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "현재 상태에서는 재확인을 요청할 수 없습니다.");
        }

        Map<String, Object> groupInfo = withdrawalMapper.selectGroupInfo(groupId);
        if (groupInfo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다.");
        }
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
        if (leaderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "모임장 정보가 필요합니다.");
        }
        Map<String, Object> groupInfo = withdrawalMapper.selectGroupInfo(groupId);
        if (groupInfo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다.");
        }

        Long ownerId = ((Number) groupInfo.get("OWNER_ID")).longValue();
        if (!ownerId.equals(leaderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "모임장만 탈퇴를 최종 승인할 수 있습니다.");
        }

        Map<String, Object> req = withdrawalMapper.selectWithdrawalById(groupId, withdrawalRequestId);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "탈퇴 신청 내역을 찾을 수 없습니다.");
        }
        if (!"CONSENT_PENDING".equals(req.get("STATUS"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "전원 동의 대기 상태의 정산안만 승인할 수 있습니다.");
        }

        // 정산 금액이 등록되어 있는지 확인
        if (req.get("SETTLEMENT_AMOUNT") == null || req.get("SETTLEMENT_AT") == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "정산 금액 등록 없이는 탈퇴를 승인할 수 없습니다.");
        }

        Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();

        // [예외 4] 미동의 승인 차단 -> '필요한 공식 동의가 완료되지 않았어요'
        int targetCount = withdrawalMapper.countRemainingActiveMembers(groupId, applicantId);
        int agreedCount = withdrawalMapper.countAgreedSettlementConsents(groupId, withdrawalRequestId);

        if (agreedCount < targetCount) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "필요한 공식 동의가 완료되지 않았어요.");
        }

        // 1) WITHDRAWAL_REQUEST.STATUS = 'APPROVED'
        if (withdrawalMapper.updateWithdrawalStatus(
                groupId, withdrawalRequestId, "CONSENT_PENDING", "APPROVED") == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "탈퇴 신청 상태가 변경되어 승인할 수 없습니다.");
        }

        // 2) GYE_MEMBER.STATUS = 'WITHDRAWN', LEFT_AT = SYSDATE
        if (withdrawalMapper.updateGyeMemberWithdrawn(groupId, applicantId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "탈퇴 신청 회원이 현재 활성 모임원이 아닙니다.");
        }

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