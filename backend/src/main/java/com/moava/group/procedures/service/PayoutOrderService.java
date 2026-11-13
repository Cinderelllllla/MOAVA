package com.moava.group.procedures.service;

import com.moava.group.procedures.dto.PayoutOrderDTO;
import com.moava.group.procedures.mapper.PayoutOrderMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class PayoutOrderService {

    private final PayoutOrderMapper payoutOrderMapper;

    public PayoutOrderService(PayoutOrderMapper payoutOrderMapper) {
        this.payoutOrderMapper = payoutOrderMapper;
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
        Map<String, Object> groupInfo = getGroupInfo(groupId);
        requireActiveMember(groupId, memberId);

        // [예외 1] 사유 미입력 -> 차단
        if (form.getReason() == null || form.getReason().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "신청 사유를 반드시 입력해야 합니다.");
        }
        // 희망 회차 범위 검증 (1 ~ 최대 인원)
        // 희망 회차 범위 검증 (1 ~ 최대 인원)
        int maxMembers = ((Number) groupInfo.get("MAX_MEMBERS")).intValue();
        Integer roundNo = form.getEffectiveRoundNo();
        if (roundNo == null || roundNo < 1 || roundNo > maxMembers) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "희망 회차는 1회차부터 " + maxMembers + "회차 사이로 신청해야 합니다.");
        }

        // 2. [AI 제안] 모임원 연체 확률 내림차순 추천 회차 조회 (AI_SUGGESTED_ROUND)
        Integer aiSuggestedRound = payoutOrderMapper.selectAiSuggestedRound(groupId, memberId);

        // 회원별 진행 중(REQUESTED / CONSENT_PENDING) 신청 건 확인 (1건으로 제한)
        Map<String, Object> existingReq = payoutOrderMapper.selectActiveRequestByMember(groupId, memberId);

        if (existingReq != null) {
            String currentStatus = (String) existingReq.get("STATUS");
            Long existingReqId = ((Number) existingReq.get("PAYOUT_REQUEST_ID")).longValue();

            // [예외 2] CONSENT_PENDING 중엔 수정 불가
            if ("CONSENT_PENDING".equals(currentStatus)) {
                throw new IllegalStateException("이미 동의 요청 중입니다.");
            }

            // REQUESTED 건이 있으면 수정 (회차·사유 갱신, 기존 CONSENT 삭제 후 재동의)
            if ("REQUESTED".equals(currentStatus)) {
                payoutOrderMapper.updatePayoutRequest(existingReqId, roundNo, form.getReason(), aiSuggestedRound);
                payoutOrderMapper.deleteConsentsByRequestId(existingReqId);
                payoutOrderMapper.insertAuditLog(groupId, memberId, "ORDER_REQUEST",
                        "수령 순서 신청 수정: " + roundNo + "회차");
                return "기존 신청 건이 수정되었습니다.";
            }
        }

        // 신규 신청 (REQUESTED)
        payoutOrderMapper.insertPayoutRequest(groupId, memberId, roundNo, form.getReason(), aiSuggestedRound);
        payoutOrderMapper.insertAuditLog(groupId, memberId, "ORDER_REQUEST",
                "수령 순서 신규 신청: " + roundNo + "회차");
        return "수령 순서 신청이 완료되었습니다.";
    }

    /**
     * 3. [동의 요청] 모임장 -> CONSENT_PENDING 전환 및 신청자 제외 ACTIVE 회원 전원에게 알림
     */
    @Transactional
    public void requestConsentByLeader(Long groupId, Long payoutRequestId, Long leaderId) {
        Map<String, Object> groupInfo = getGroupInfo(groupId);
        requireLeader(groupInfo, leaderId);

        Map<String, Object> req = payoutOrderMapper.selectPayoutRequestById(groupId, payoutRequestId);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "신청 내역을 찾을 수 없습니다.");
        }
        if (!"REQUESTED".equals(req.get("STATUS"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신청 상태에서만 동의 요청을 보낼 수 있습니다.");
        }

        Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();
        Integer roundNo = ((Number) req.get("ROUND_NO")).intValue();

        // 상태를 CONSENT_PENDING으로 변경
        if (payoutOrderMapper.updatePayoutRequestStatus(
                groupId, payoutRequestId, "REQUESTED", "CONSENT_PENDING") == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신청 상태가 변경되어 동의 요청을 보낼 수 없습니다.");
        }

        // 기존 동의 데이터 초기화 후 신청자 제외 ACTIVE 회원 전원에게 동의 요청 생성 및 알림
        payoutOrderMapper.deleteConsentsByRequestId(payoutRequestId);
        List<Long> otherMembers = payoutOrderMapper.selectOtherActiveMemberIds(groupId, applicantId);

        for (Long targetMemberId : otherMembers) {
            payoutOrderMapper.insertInitialConsent(groupId, payoutRequestId, targetMemberId);
            payoutOrderMapper.insertNotification(targetMemberId,
                    "CONSENT_REQUEST",
                    "수령 순서 변경 동의 요청",
                    roundNo + "회차 수령 순서 신청에 대한 동의를 진행해 주세요.");
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
        requireActiveMember(groupId, memberId);

        Map<String, Object> req = payoutOrderMapper.selectPayoutRequestById(groupId, payoutRequestId);
        if (req == null || !"CONSENT_PENDING".equals(req.get("STATUS"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "현재 동의 진행 중인 신청 건이 아닙니다.");
        }

        String yn = "Y".equalsIgnoreCase(form.getAgreedYn()) ? "Y" : "N";
        if (payoutOrderMapper.updateMemberConsent(groupId, payoutRequestId, memberId, yn) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "동의 대상 회원이 아닙니다.");
        }

        // [예외 3] 1명 거부 -> REJECTED, 재조정 안내
        if ("N".equals(yn)) {
            if (payoutOrderMapper.updatePayoutRequestStatus(
                    groupId, payoutRequestId, "CONSENT_PENDING", "REJECTED") == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "신청 상태가 변경되어 반대 처리를 완료할 수 없습니다.");
            }
            Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();
            payoutOrderMapper.insertNotification(applicantId,"CONSENT_REQUEST",
                    "수령 순서 신청 반려(재조정)",
                    "모임원 중 반대가 발생하여 수령 순서 신청이 반려되었습니다. 회차를 재조정해 주세요.");
            return "반대 처리되어 해당 신청 건이 재조정(REJECTED) 상태로 변경되었습니다.";
        }

        return "수령 순서 변경에 동의하셨습니다.";
    }

    /**
     * 5. [확정] 모임장: 전원 'Y' 검증 -> CONFIRMED, 회차 중복 검사, 전 회원 알림,
     * 모든 회원 확정 시 GYE_GROUP.ORDER_CONFIRMED_YN='Y' (운영 시작 가능)
     */
    @Transactional
    public void confirmPayoutOrder(Long groupId, Long payoutRequestId, Long leaderId) {
        Map<String, Object> groupInfo = getGroupInfo(groupId);
        requireLeader(groupInfo, leaderId);

        Map<String, Object> req = payoutOrderMapper.selectPayoutRequestById(groupId, payoutRequestId);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "신청 내역을 찾을 수 없습니다.");
        }
        if (!"CONSENT_PENDING".equals(req.get("STATUS"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "동의 진행 중인 신청만 확정할 수 있습니다.");
        }

        Long applicantId = ((Number) req.get("MEMBER_ID")).longValue();
        Integer roundNo = ((Number) req.get("ROUND_NO")).intValue();

        // [검증 1] 회차 중복 확정 불가 검사
        int duplicateCount = payoutOrderMapper.countConfirmedRoundDuplicate(groupId, roundNo, payoutRequestId);
        if (duplicateCount > 0) {
            throw new IllegalStateException("이미 다른 회원에게 확정된 회차입니다. 회차 중복 확정은 불가합니다.");
        }

        // [검증 2] 신청자 제외 ACTIVE 회원 전원 'Y' 동의 검증
        int totalTargetMembers = payoutOrderMapper.countTotalActiveOtherMembers(groupId, applicantId);
        int agreedMembers = payoutOrderMapper.countAgreedConsents(payoutRequestId);

        if (agreedMembers < totalTargetMembers) {
            throw new IllegalStateException("영향받는 회원 전원의 동의가 필요해요.");
        }

        // 신청 건 CONFIRMED 처리 및 모임원 확정 회차 반영
        if (payoutOrderMapper.updatePayoutRequestStatus(
                groupId, payoutRequestId, "CONSENT_PENDING", "CONFIRMED") == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "신청 상태가 변경되어 확정할 수 없습니다.");
        }

        // 전 회원 확정 안내 알림 발송
        List<Long> allMembers = payoutOrderMapper.selectOtherActiveMemberIds(groupId, -1L);
        for (Long memberId : allMembers) {
            payoutOrderMapper.insertNotification(memberId,
                    "ORDER_CONFIRMED",
                    "수령 순서 확정 안내",
                    roundNo + "회차 수령 순서가 최종 확정되었습니다.");
        }

        // 모든 ACTIVE 회원의 회차가 확정되었는지 확인 후 GYE_GROUP.ORDER_CONFIRMED_YN = 'Y' 갱신
        int unconfirmedCount = payoutOrderMapper.countUnconfirmedActiveMembers(groupId);
        if (unconfirmedCount == 0) {
            payoutOrderMapper.updateGroupOrderConfirmedYn(groupId, "Y");
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

    private void requireActiveMember(Long groupId, Long memberId) {
        if (memberId == null || payoutOrderMapper.countActiveMember(groupId, memberId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 모임의 ACTIVE 회원만 이용할 수 있습니다.");
        }
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