package com.moava.group.procedures.service;

import com.moava.group.procedures.dto.PayoutOrderDTO;
import com.moava.group.procedures.mapper.PayoutOrderMapper;
import com.moava.group.search.event.GroupSearchSyncEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class PayoutOrderService {

    private final PayoutOrderMapper payoutOrderMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public PayoutOrderService(PayoutOrderMapper payoutOrderMapper, ApplicationEventPublisher eventPublisher) {
        this.payoutOrderMapper = payoutOrderMapper;
        this.eventPublisher = eventPublisher;
    }

    public PayoutOrderService(PayoutOrderMapper payoutOrderMapper) {
        this(payoutOrderMapper, null);
    }
    /**
     * 신청·동의 현황 목록 조회 (AI 제안 회차 포함)
     */
    @Transactional(readOnly = true)
    public List<PayoutOrderDTO.StatusResponse> getPayoutOrderList(Long groupId, Long memberId) {
        Map<String, Object> groupInfo = getGroupInfo(groupId);
        if (!isLeader(groupInfo, memberId)) {
            requireActiveMember(groupId, memberId);
        }
        return payoutOrderMapper.selectPayoutRequestList(groupId);
    }

    /**
     * 1 & 2. [신청 및 수정 + AI 제안] 참여 회원의 수령 순서 신청 또는 갱신
     */
    @Transactional
    public String requestOrUpdatePayoutOrder(Long groupId, Long memberId, PayoutOrderDTO.RequestForm form) {
        if (form == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신청 정보를 확인해 주세요.");
        }
        Map<String, Object> groupInfo = getOrderConfigurableGroupForUpdate(groupId);
        requireActiveMember(groupId, memberId);

        // [예외 1] 사유 미입력 -> 차단
        if (form.getReason() == null || form.getReason().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신청 사유를 반드시 입력해야 합니다.");
        }
        String cleanReason = form.getReason().trim();
        if (cleanReason.getBytes(StandardCharsets.UTF_8).length > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신청 사유는 500바이트 이하여야 합니다.");
        }
        // 월 납입 증빙 승인(CONFIRMED) 여부 확인
        if (payoutOrderMapper.countConfirmedPaymentProof(groupId, memberId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "월 납입액 증빙이 승인된 회원만 수령 순서를 신청할 수 있습니다. 먼저 납입 증빙을 등록·승인받아 주세요.");
        }
        // 희망 회차 범위 검증 (1 ~ 실제 확정 참여 인원 수)
        int maxOrderRound = resolveMaxOrderRound(groupInfo);
        Integer roundNo = form.getEffectiveRoundNo();
        if (roundNo == null || roundNo < 1 || roundNo > maxOrderRound) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "희망 회차는 1회차부터 " + maxOrderRound + "회차 사이로 신청해야 합니다.");
        }
        if (payoutOrderMapper.countConfirmedRequestByMember(groupId, memberId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 수령 회차가 확정된 회원은 추가 신청할 수 없습니다.");
        }

        // 회원별 진행 중(REQUESTED / CONSENT_PENDING) 신청 건 확인 (1건으로 제한)
        Map<String, Object> existingReq = payoutOrderMapper.selectActiveRequestByMember(groupId, memberId);
        Long excludeRequestId = existingReq != null
                ? ((Number) existingReq.get("PAYOUT_REQUEST_ID")).longValue()
                : -1L;

        if (existingReq != null && "CONSENT_PENDING".equals(existingReq.get("STATUS"))) {
            // [예외 2] CONSENT_PENDING 중엔 수정 불가
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 동의 요청 중입니다.");
        }
        if (payoutOrderMapper.countConfirmedRoundDuplicate(groupId, roundNo, excludeRequestId) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "이미 다른 회원에게 확정된 회차입니다. 다른 회차를 선택해 주세요.");
        }

        // 2. [AI 제안] 모임원 연체 확률 내림차순 추천 회차 조회 (AI_SUGGESTED_ROUND)
        Integer aiSuggestedRound = payoutOrderMapper.selectAiSuggestedRound(groupId, memberId);

        if (existingReq != null) {
            String currentStatus = (String) existingReq.get("STATUS");

            // REQUESTED 건이 있으면 수정 (회차·사유 갱신, 기존 CONSENT 삭제 후 재동의)
            if ("REQUESTED".equals(currentStatus)) {
                payoutOrderMapper.updatePayoutRequest(excludeRequestId, roundNo, cleanReason, aiSuggestedRound);
                payoutOrderMapper.deleteConsentsByRequestId(excludeRequestId);
                payoutOrderMapper.insertAuditLog(groupId, memberId, "ORDER_REQUEST",
                        "수령 순서 신청 수정: " + roundNo + "회차");
                return "기존 신청 건이 수정되었습니다.";
            }
        }

        // 신규 신청 (REQUESTED)
        payoutOrderMapper.insertPayoutRequest(groupId, memberId, roundNo, cleanReason, aiSuggestedRound);
        payoutOrderMapper.insertAuditLog(groupId, memberId, "ORDER_REQUEST",
                "수령 순서 신규 신청: " + roundNo + "회차");
        return "수령 순서 신청이 완료되었습니다.";
    }

    /**
     * 3. [동의 요청] 모임장 -> CONSENT_PENDING 전환 및 신청자 제외 ACTIVE 회원 전원에게 알림
     */
    @Transactional
    public void requestConsentByLeader(Long groupId, Long payoutRequestId, Long leaderId) {
        Map<String, Object> groupInfo = getOrderConfigurableGroupForUpdate(groupId);
        requireLeader(groupInfo, leaderId);

        Map<String, Object> req = payoutOrderMapper.selectPayoutRequestById(groupId, payoutRequestId);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "신청 내역을 찾을 수 없습니다.");
        }
        if (!"REQUESTED".equals(req.get("STATUS"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신청 상태에서만 동의 요청을 보낼 수 있습니다.");
        }

        Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();
        if (payoutOrderMapper.countActiveMember(groupId, applicantId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신청 회원이 현재 모임의 활성 회원이 아닙니다.");
        }
        Integer roundNo = ((Number) req.get("ROUND_NO")).intValue();
        if (payoutOrderMapper.countConfirmedRoundDuplicate(groupId, roundNo, payoutRequestId) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "이미 다른 회원에게 확정된 회차입니다. 회차를 재조정한 뒤 진행해 주세요.");
        }

        // 상태를 CONSENT_PENDING으로 변경
        if (payoutOrderMapper.updatePayoutRequestStatus(
                groupId, payoutRequestId, "REQUESTED", "CONSENT_PENDING") == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신청 상태가 변경되어 동의 요청을 보낼 수 없습니다.");
        }

        // 기존 동의 데이터 초기화 후 신청자 제외 ACTIVE 회원 전원에게 동의 요청 생성 및 알림
        payoutOrderMapper.deleteConsentsByRequestId(payoutRequestId);
        List<Long> otherMembers = payoutOrderMapper.selectOtherActiveMemberIds(groupId, applicantId);

        String linkPage = "/procedures?groupId=" + groupId;
        for (Long targetMemberId : otherMembers) {
            payoutOrderMapper.insertInitialConsent(groupId, payoutRequestId, targetMemberId);
            payoutOrderMapper.insertNotification(
                    targetMemberId,
                    "CONSENT_REQ",
                    "수령 순서 변경 동의 요청",
                    roundNo + "회차 수령 순서 신청에 대한 동의를 진행해 주세요.",
                    linkPage,
                    payoutRequestId
            );
        }

        // 6. AUDIT_LOG 기록
        payoutOrderMapper.insertAuditLog(groupId, leaderId, "ORDER_CONSENT_REQ",
                "신청 건(" + payoutRequestId + ") 전원 동의 요청 발송");
    }

    /**
     * 4. [동의] 대상 회원 -> CONSENT(type=ORDER, AGREED_YN) 처리
     * 1명이라도 거부('N') 시 즉시 REJECTED 및 재조정 처리
     */
    @Transactional
    public String submitMemberConsent(Long groupId, Long payoutRequestId, Long memberId,
                                      PayoutOrderDTO.ConsentForm form) {
        getOrderConfigurableGroupForUpdate(groupId);
        requireActiveMember(groupId, memberId);

        Map<String, Object> req = payoutOrderMapper.selectPayoutRequestForUpdate(groupId, payoutRequestId);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "신청 내역을 찾을 수 없습니다.");
        }
        if (!"CONSENT_PENDING".equals(req.get("STATUS"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "현재 동의 진행 중인 신청 건이 아닙니다.");
        }
        if (form == null || !("Y".equalsIgnoreCase(form.getAgreedYn())
                || "N".equalsIgnoreCase(form.getAgreedYn()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "동의 여부는 Y 또는 N으로 입력해야 합니다.");
        }

        String currentConsent = payoutOrderMapper.selectMemberConsentStatus(groupId, payoutRequestId, memberId);
        if (currentConsent == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "동의 대상 회원이 아닙니다.");
        }
        if (!"P".equals(currentConsent)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 동의 응답을 완료했습니다.");
        }

        String yn = "Y".equalsIgnoreCase(form.getAgreedYn()) ? "Y" : "N";
        if (payoutOrderMapper.updateMemberConsent(groupId, payoutRequestId, memberId, yn) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 동의 응답을 완료했거나 동의 진행 중이 아닙니다.");
        }

        // AUDIT_LOG 기록 (ORDER_CONSENT)
        payoutOrderMapper.insertAuditLog(groupId, memberId, "ORDER_CONSENT",
                "수령 순서 변경 동의 응답: " + yn + " (신청번호: " + payoutRequestId + ")");

        // [예외 3] 1명 거부 -> REJECTED, 재조정 안내
        if ("N".equals(yn)) {
            if (payoutOrderMapper.updatePayoutRequestStatus(
                    groupId, payoutRequestId, "CONSENT_PENDING", "REJECTED") == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "신청 상태가 변경되어 반대 처리를 완료할 수 없습니다.");
            }
            Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();
            payoutOrderMapper.insertNotification(
                    applicantId,
                    "CONSENT_RESULT",
                    "수령 순서 신청 반려(재조정)",
                    "모임원 중 반대가 발생하여 수령 순서 신청이 반려되었습니다. 회차를 재조정해 주세요.",
                    "/procedures?groupId=" + groupId,
                    payoutRequestId
            );
            return "반대 처리되어 해당 신청 건이 재조정(REJECTED) 상태로 변경되었습니다.";
        }

        return "수령 순서 변경에 동의하셨습니다.";
    }

    /**
     * 5. [확정] 모임장: 전원 'Y' 검증 -> CONFIRMED, 회차 중복 검사, 경합 신청 자동 반려, 전 회원 알림,
     * 모든 회원 확정 시 GYE_GROUP.ORDER_CONFIRMED_YN='Y' (운영 시작 가능)
     */
    @Transactional
    public void confirmPayoutOrder(Long groupId, Long payoutRequestId, Long leaderId) {
        Map<String, Object> groupInfo = getOrderConfigurableGroupForUpdate(groupId);
        requireLeader(groupInfo, leaderId);
        Map<String, Object> req = payoutOrderMapper.selectPayoutRequestForUpdate(groupId, payoutRequestId);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "신청 내역을 찾을 수 없습니다.");
        }
        if (!"CONSENT_PENDING".equals(req.get("STATUS"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "동의 진행 중인 신청만 확정할 수 있습니다.");
        }

        Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();
        if (payoutOrderMapper.countActiveMember(groupId, applicantId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신청 회원이 현재 모임의 활성 회원이 아닙니다.");
        }
        if (payoutOrderMapper.countConfirmedPaymentProof(groupId, applicantId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "신청 회원의 월 납입 증빙이 승인되지 않아 수령 순서를 확정할 수 없습니다.");
        }
        Integer roundNo = ((Number) req.get("ROUND_NO")).intValue();

        // [검증 1] 회차 중복 확정 불가 검사
        int duplicateCount = payoutOrderMapper.countConfirmedRoundDuplicate(groupId, roundNo, payoutRequestId);
        if (duplicateCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "이미 다른 회원에게 확정된 회차입니다. 회차 중복 확정은 불가합니다.");
        }
        if (payoutOrderMapper.countConfirmedRequestByMember(groupId, applicantId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 수령 회차가 확정된 회원입니다.");
        }

        // [검증 2] 신청자 제외 ACTIVE 회원 전원 'Y' 동의 검증
        List<Long> activeTargetMembers =
                payoutOrderMapper.lockActiveConsentTargets(groupId, payoutRequestId, applicantId);
        if (activeTargetMembers.isEmpty()
                && !payoutOrderMapper.selectOtherActiveMemberIds(groupId, applicantId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "저장된 동의 대상이 없습니다. 동의 요청을 다시 진행해 주세요.");
        }
        int agreedMembers = payoutOrderMapper.countAgreedConsents(groupId, payoutRequestId, applicantId);

        if (agreedMembers < activeTargetMembers.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "영향받는 회원 전원의 동의가 필요해요.");
        }

        // 신청 건 CONFIRMED 처리
        if (payoutOrderMapper.updatePayoutRequestStatus(
                groupId, payoutRequestId, "CONSENT_PENDING", "CONFIRMED") == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신청 상태가 변경되어 확정할 수 없습니다.");
        }

        // 같은 회차로 경합 중이던 타 회원의 미확정 신청 건은 자동 REJECTED 처리해 교착을 방지
        String linkPage = "/procedures?groupId=" + groupId;
        List<Map<String, Object>> competingRequests =
                payoutOrderMapper.selectCompetingOpenRequests(groupId, roundNo, payoutRequestId);
        if (competingRequests != null && !competingRequests.isEmpty()) {
            payoutOrderMapper.rejectCompetingOpenRequests(groupId, roundNo, payoutRequestId);
            for (Map<String, Object> competing : competingRequests) {
                Long competingReqId = ((Number) competing.get("PAYOUT_REQUEST_ID")).longValue();
                Long competingMemberId = ((Number) competing.get("MEMBER_ID")).longValue();
                payoutOrderMapper.insertNotification(
                        competingMemberId,
                        "CONSENT_RESULT",
                        "수령 순서 신청 반려(회차 확정)",
                        roundNo + "회차가 다른 회원으로 확정되어 신청이 반려되었습니다. 다른 회차로 다시 신청해 주세요.",
                        linkPage,
                        competingReqId
                );
            }
        }

        // 전 회원 확정 안내 알림 발송
        List<Long> allMembers = payoutOrderMapper.selectOtherActiveMemberIds(groupId, -1L);
        for (Long memberId : allMembers) {
            payoutOrderMapper.insertNotification(
                    memberId,
                    "CONSENT_RESULT",
                    "수령 순서 확정 안내",
                    roundNo + "회차 수령 순서가 최종 확정되었습니다.",
                    linkPage,
                    payoutRequestId
            );
        }

        // 모든 ACTIVE 회원의 회차가 확정되었는지 확인 후 GYE_GROUP.ORDER_CONFIRMED_YN = 'Y' 갱신
        int unconfirmedCount = payoutOrderMapper.countUnconfirmedActiveMembers(groupId);
        if (unconfirmedCount == 0) {
            payoutOrderMapper.updateGroupOrderConfirmedYn(groupId, "Y");
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new GroupSearchSyncEvent(groupId));
            }
        }

        // 6. AUDIT_LOG (ORDER_CONFIRM) 기록
        payoutOrderMapper.insertAuditLog(groupId, leaderId, "ORDER_CONFIRM",
                roundNo + "회차 수령 순서 최종 확정 (신청번호: " + payoutRequestId + ")");
    }

    private Map<String, Object> getGroupInfo(Long groupId) {
        Map<String, Object> groupInfo = payoutOrderMapper.selectGroupInfo(groupId);
        if (groupInfo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다.");
        }
        return groupInfo;
    }

    private Map<String, Object> getOrderConfigurableGroupForUpdate(Long groupId) {
        if (payoutOrderMapper.lockGroupForOrderUpdate(groupId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다.");
        }
        Map<String, Object> groupInfo = getGroupInfo(groupId);
        requireOrderConfigurableGroup(groupInfo);
        return groupInfo;
    }

    private void requireActiveMember(Long groupId, Long memberId) {
        if (memberId == null || payoutOrderMapper.countActiveMember(groupId, memberId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 모임의 ACTIVE 회원만 이용할 수 있습니다.");
        }
    }

    private void requireOrderConfigurableGroup(Map<String, Object> groupInfo) {
        Object status = groupInfo.get("STATUS");
        if ("RECRUITING".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "모집이 마감되어 운영이 시작된 모임에서만 수령 순서 절차를 진행할 수 있습니다.");
        }
        if (!"ACTIVE".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "운영 중인 모임에서만 수령 순서 절차를 처리할 수 있습니다.");
        }
    }

    private int resolveMaxOrderRound(Map<String, Object> groupInfo) {
        Object activeMembersObj = groupInfo.get("ACTIVE_MEMBERS");
        if (activeMembersObj instanceof Number && ((Number) activeMembersObj).intValue() > 0) {
            return ((Number) activeMembersObj).intValue();
        }
        Object maxMembersObj = groupInfo.get("MAX_MEMBERS");
        return maxMembersObj instanceof Number ? ((Number) maxMembersObj).intValue() : 1;
    }

    private void requireLeader(Map<String, Object> groupInfo, Long memberId) {
        Object ownerId = groupInfo.get("OWNER_ID");
        if (!(ownerId instanceof Number) || memberId == null
                || ((Number) ownerId).longValue() != memberId.longValue()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 모임장만 처리할 수 있습니다.");
        }
    }

    private boolean isLeader(Map<String, Object> groupInfo, Long memberId) {
        Object ownerId = groupInfo.get("OWNER_ID");
        return ownerId instanceof Number && memberId != null
                && ((Number) ownerId).longValue() == memberId.longValue();
    }
}