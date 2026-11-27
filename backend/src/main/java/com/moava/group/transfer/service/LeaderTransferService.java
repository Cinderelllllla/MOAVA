package com.moava.group.transfer.service;

import com.moava.group.transfer.dto.LeaderTransferDtos.*;
import com.moava.group.transfer.mapper.LeaderTransferMapper;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LeaderTransferService {

    private final LeaderTransferMapper mapper;

    public LeaderTransferService(
            LeaderTransferMapper mapper
    ) {
        this.mapper = mapper;
    }

    private static Long number(
            Map<String, Object> row,
            String key
    ) {
        Object value = row.get(key);

        return value == null
                ? null
                : ((Number) value).longValue();
    }

    private static String str(
            Map<String, Object> row,
            String key
    ) {
        Object value = row.get(key);

        return value == null
                ? null
                : value.toString();
    }

    private static ResponseStatusException conflict(
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }

    private static void validId(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 ID가 필요합니다."
            );
        }
    }

    // =========================================
    // 모임 잠금 및 상태 검증
    // =========================================

    private Map<String, Object> group(Long groupId) {
        validId(groupId);

        Map<String, Object> row =
                mapper.lockGroup(groupId);

        if (row == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "모임을 찾을 수 없습니다."
            );
        }

        String status = str(row, "STATUS");

        if (!List.of("RECRUITING", "ACTIVE")
                .contains(status)) {
            throw conflict(
                    "해산 중 또는 종료된 모임에서는 승계할 수 없습니다."
            );
        }

        return row;
    }

    private void requireActiveMember(
            Long groupId,
            Long memberId
    ) {
        validId(memberId);

        if (mapper.countActive(groupId, memberId) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "해당 모임의 활성 회원이 아닙니다."
            );
        }
    }

    private Map<String, Object> pending(
            Long groupId,
            Long transferId
    ) {
        Map<String, Object> row =
                mapper.findPending(groupId);

        if (
                row == null ||
                !transferId.equals(
                        number(row, "TRANSFER_ID")
                )
        ) {
            throw conflict(
                    "진행 중인 승계 신청이 아닙니다."
            );
        }

        return row;
    }

    private void audit(
            Long groupId,
            Long actorId,
            Long targetId,
            String eventType,
            String description
    ) {
        int changed = mapper.audit(
                groupId,
                actorId,
                targetId,
                eventType,
                description
        );

        if (changed != 1) {
            throw conflict(
                    "감사 로그 저장에 실패했습니다."
            );
        }
    }

    // =========================================
    // 승계 현황 응답
    // =========================================

    private StatusResponse statusUnderLock(
            Long groupId,
            Long memberId,
            Map<String, Object> group
    ) {
        requireActiveMember(groupId, memberId);

        Long ownerId = number(group, "OWNER_ID");

        boolean isOwner =
                ownerId.equals(memberId);

        Map<String, Object> request =
                mapper.findPending(groupId);

        List<MemberOption> candidates =
                isOwner
                        ? mapper.candidates(
                                groupId,
                                ownerId
                        )
                        : List.of();

        if (request == null) {
            return new StatusResponse(
                    groupId,
                    null,
                    ownerId,
                    null,
                    "NONE",
                    false,
                    false,
                    isOwner,
                    null,
                    false,
                    0,
                    0,
                    0,
                    0,
                    false,
                    candidates
            );
        }

        Long transferId =
                number(request, "TRANSFER_ID");

        Long targetId =
                number(request, "TO_MEMBER_ID");

        boolean targetAccepted =
                "Y".equals(
                        str(request, "ACCEPTED_YN")
                );

        boolean isTarget =
                memberId.equals(targetId);

        String myConsent =
                mapper.myConsent(
                        groupId,
                        transferId,
                        memberId
                );

        int totalMembers =
                mapper.countActiveAll(groupId);

        int agreedMembers =
                mapper.countAgreed(
                        groupId,
                        transferId
                );

        int rejectedMembers =
                mapper.countRejected(
                        groupId,
                        transferId
                );

        int pendingMembers =
                Math.max(
                        totalMembers
                                - agreedMembers
                                - rejectedMembers,
                        0
                );

        boolean canConfirm =
                targetAccepted
                && totalMembers >= 2
                && agreedMembers == totalMembers
                && rejectedMembers == 0
                && mapper.countActive(
                        groupId,
                        ownerId
                ) == 1
                && mapper.countActive(
                        groupId,
                        targetId
                ) == 1
                && ownerId.equals(
                        number(
                                request,
                                "FROM_MEMBER_ID"
                        )
                );

        boolean canConsent =
                targetAccepted
                && myConsent == null;

        return new StatusResponse(
                groupId,
                transferId,
                ownerId,
                targetId,
                "PENDING",
                targetAccepted,
                isTarget,
                isOwner,
                myConsent,
                canConsent,
                totalMembers,
                agreedMembers,
                rejectedMembers,
                pendingMembers,
                canConfirm,
                candidates
        );
    }

    // =========================================
    // GET 승계 현황
    // =========================================

    @Transactional
    public StatusResponse status(
            Long groupId,
            Long memberId
    ) {
        Map<String, Object> group =
                group(groupId);

        return statusUnderLock(
                groupId,
                memberId,
                group
        );
    }

    // =========================================
    // POST 승계 신청
    // =========================================

    @Transactional
    public StatusResponse request(
            Long groupId,
            Long actorId,
            CreateRequest request
    ) {
        Map<String, Object> group =
                group(groupId);

        requireActiveMember(groupId, actorId);

        Long ownerId =
                number(group, "OWNER_ID");

        if (!actorId.equals(ownerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "모임장만 승계를 신청할 수 있습니다."
            );
        }

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "승계 대상 회원을 지정해 주세요."
            );
        }

        Long targetId = request.toMemberId();

        validId(targetId);

        if (actorId.equals(targetId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "자신에게 승계할 수 없습니다."
            );
        }

        requireActiveMember(groupId, targetId);

        if (mapper.findPending(groupId) != null) {
            throw conflict(
                    "이미 진행 중인 승계 신청이 있습니다."
            );
        }

        Long transferId = mapper.nextId();

        if (
                mapper.create(
                        transferId,
                        groupId,
                        actorId,
                        targetId
                ) != 1
        ) {
            throw conflict(
                    "승계 신청 생성에 실패했습니다."
            );
        }

        audit(
                groupId,
                actorId,
                targetId,
                "LEADER_TRANSFER_REQUEST",
                "승계 신청 ID=" + transferId
        );

        return statusUnderLock(
                groupId,
                actorId,
                group
        );
    }

    // =========================================
    // POST 대상 회원 수락 / 거절
    // =========================================

    @Transactional
    public StatusResponse decide(
            Long groupId,
            Long transferId,
            Long actorId,
            DecisionRequest decision
    ) {
        Map<String, Object> group =
                group(groupId);

        requireActiveMember(groupId, actorId);
        validId(transferId);

        Map<String, Object> request =
                pending(groupId, transferId);

        Long targetId =
                number(request, "TO_MEMBER_ID");

        if (!actorId.equals(targetId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "승계 대상 회원만 수락할 수 있습니다."
            );
        }

        if (
                decision == null ||
                decision.accepted() == null
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "수락 여부가 필요합니다."
            );
        }

        if (
                "Y".equals(
                        str(request, "ACCEPTED_YN")
                )
        ) {
            throw conflict(
                    "이미 승계를 수락했습니다."
            );
        }

        if (decision.accepted()) {

            if (
                    mapper.accept(
                            transferId,
                            groupId
                    ) != 1
            ) {
                throw conflict(
                        "승계 수락 처리에 실패했습니다."
                );
            }

            audit(
                    groupId,
                    actorId,
                    actorId,
                    "LEADER_TRANSFER_ACCEPT",
                    "승계 대상 수락 ID=" + transferId
            );

        } else {

            if (
                    mapper.cancel(
                            transferId,
                            groupId
                    ) != 1
            ) {
                throw conflict(
                        "승계 거절 처리에 실패했습니다."
                );
            }

            audit(
                    groupId,
                    actorId,
                    actorId,
                    "LEADER_TRANSFER_CANCEL",
                    "승계 대상 거절 ID=" + transferId
            );
        }

        return statusUnderLock(
                groupId,
                actorId,
                group
        );
    }

    // =========================================
    // POST 활성 회원 동의 / 거절
    // =========================================

    @Transactional
    public StatusResponse consent(
            Long groupId,
            Long transferId,
            Long actorId,
            ConsentRequest consent
    ) {
        Map<String, Object> group =
                group(groupId);

        requireActiveMember(groupId, actorId);
        validId(transferId);

        Map<String, Object> request =
                pending(groupId, transferId);

        if (
                !"Y".equals(
                        str(request, "ACCEPTED_YN")
                )
        ) {
            throw conflict(
                    "승계 대상 회원의 수락이 먼저 필요합니다."
            );
        }

        if (
                consent == null ||
                consent.agreed() == null
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "동의 여부가 필요합니다."
            );
        }

        if (
                mapper.myConsent(
                        groupId,
                        transferId,
                        actorId
                ) != null
        ) {
            throw conflict(
                    "이미 동의 또는 거절했습니다."
            );
        }

        String agreedYn =
                consent.agreed() ? "Y" : "N";

        if (
                mapper.recordConsent(
                        transferId,
                        groupId,
                        actorId,
                        agreedYn
                ) != 1
        ) {
            throw conflict(
                    "동의 저장에 실패했습니다."
            );
        }

        Long targetId =
                number(request, "TO_MEMBER_ID");

        audit(
                groupId,
                actorId,
                targetId,
                "LEADER_TRANSFER_CONSENT",
                "승계 동의 ID="
                        + transferId
                        + ", 응답="
                        + agreedYn
        );

        // 한 명이라도 거절하면 현재 신청 종료
        if (!consent.agreed()) {

            if (
                    mapper.cancel(
                            transferId,
                            groupId
                    ) != 1
            ) {
                throw conflict(
                        "거절에 따른 신청 종료에 실패했습니다."
                );
            }

            audit(
                    groupId,
                    actorId,
                    targetId,
                    "LEADER_TRANSFER_CANCEL",
                    "회원 거절로 신청 종료 ID="
                            + transferId
            );
        }

        return statusUnderLock(
                groupId,
                actorId,
                group
        );
    }

    // =========================================
    // POST 기존 모임장 신청 취소
    // =========================================

    @Transactional
    public StatusResponse cancel(
            Long groupId,
            Long transferId,
            Long actorId
    ) {
        Map<String, Object> group =
                group(groupId);

        requireActiveMember(groupId, actorId);
        validId(transferId);

        if (
                !actorId.equals(
                        number(group, "OWNER_ID")
                )
        ) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "모임장만 신청을 취소할 수 있습니다."
            );
        }

        pending(groupId, transferId);

        if (
                mapper.cancel(
                        transferId,
                        groupId
                ) != 1
        ) {
            throw conflict(
                    "승계 신청 취소에 실패했습니다."
            );
        }

        audit(
                groupId,
                actorId,
                null,
                "LEADER_TRANSFER_CANCEL",
                "모임장 신청 취소 ID="
                        + transferId
        );

        return statusUnderLock(
                groupId,
                actorId,
                group
        );
    }

    // =========================================
    // POST 승계 최종 확정
    // =========================================

    @Transactional
    public StatusResponse confirm(
            Long groupId,
            Long transferId,
            Long actorId
    ) {
        Map<String, Object> group =
                group(groupId);

        requireActiveMember(groupId, actorId);
        validId(transferId);

        Long ownerId =
                number(group, "OWNER_ID");

        if (!actorId.equals(ownerId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "기존 모임장만 승계를 확정할 수 있습니다."
            );
        }

        Map<String, Object> request =
                pending(groupId, transferId);

        StatusResponse before =
                statusUnderLock(
                        groupId,
                        actorId,
                        group
                );

        if (!before.canConfirm()) {
            throw conflict(
                    "승계 대상 수락과 활성 회원 전원 동의가 필요합니다."
            );
        }

        Long targetId =
                number(request, "TO_MEMBER_ID");

        // 현재 모임장 정보를 조건에 포함하여 변경
        if (
                mapper.changeOwner(
                        groupId,
                        actorId,
                        targetId
                ) != 1
        ) {
            throw conflict(
                    "모임장 정보가 변경되어 승계를 확정할 수 없습니다."
            );
        }

        if (
                mapper.requireCurrentAccountReverification(
                        groupId
                ) != 1
        ) {
            throw conflict(
                    "현재 계좌 재인증 상태 변경에 실패했습니다."
            );
        }

        if (
                mapper.confirm(
                        transferId,
                        groupId
                ) != 1
        ) {
            throw conflict(
                    "승계 확정 상태 변경에 실패했습니다."
            );
        }

        audit(
                groupId,
                actorId,
                targetId,
                "LEADER_TRANSFER_CONFIRM",
                "모임장 권한 승계 확정 ID="
                        + transferId
        );

        // 변경 완료 후 최신 모임장 정보 반환
        return statusUnderLock(
                groupId,
                actorId,
                mapper.lockGroup(groupId)
        );
    }
}
