package com.moava.group.create.service;

import com.moava.group.create.dto.GroupJoinRequestResponse;
import com.moava.group.create.dto.GroupPendingJoinRequestResponse;
import com.moava.group.create.mapper.GroupJoinRequestMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;

import java.util.List;
import java.util.Map;

@Service
public class GroupJoinRequestService {

    private final GroupJoinRequestMapper mapper;
    private final NotificationService notificationService;

    public GroupJoinRequestService(
            GroupJoinRequestMapper mapper,
            NotificationService notificationService
    ) {
        this.mapper = mapper;
        this.notificationService = notificationService;
    }

    // ==================================================
    // F16 - 모임 가입 신청
    // ==================================================

    @Transactional
    public GroupJoinRequestResponse requestJoin(
            Long groupId,
            Long memberId,
            String via
    ) {

        // 1. 요청값 검증
        if (groupId == null || groupId <= 0
                || memberId == null || memberId <= 0) {
            throw new IllegalArgumentException(
                    "모임 또는 회원 정보가 올바르지 않습니다."
            );
        }

        if (!"INVITE".equals(via) && !"SEARCH".equals(via)) {
            throw new IllegalArgumentException(
                    "가입 신청 경로가 올바르지 않습니다."
            );
        }

        // 2. 동시 신청 처리 중 모임 단위 잠금
        Map<String, Object> group = mapper.lockGroup(groupId);

        if (group == null) {
            throw new IllegalArgumentException(
                    "존재하지 않는 모임입니다."
            );
        }

        Long ownerId =
                ((Number) group.get("OWNER_ID")).longValue();

        String status =
                String.valueOf(group.get("STATUS"));

        String visibility =
                String.valueOf(group.get("VISIBILITY"));

        int maxMembers =
                ((Number) group.get("MAX_MEMBERS")).intValue();

        // 3. 모임 모집 상태 확인
        if (!"RECRUITING".equals(status)) {
            throw new IllegalStateException(
                    "현재 가입 신청을 받지 않는 모임입니다."
            );
        }

        // 4. 모임장 본인의 가입 신청 차단
        if (ownerId.equals(memberId)) {
            throw new IllegalStateException(
                    "모임장은 자신의 모임에 가입 신청할 수 없습니다."
            );
        }

        // 5. 기존 참여 이력 확인
        // ACTIVE, WITHDRAWN, EXPELLED 모두 가입 신청 불가
        // 탈퇴 완료 회원은 동일 모임에 재가입할 수 없음
        if (mapper.countExistingMembership(
                groupId, memberId) > 0) {
            throw new IllegalStateException(
                    "이미 참여 중이거나 탈퇴 이력이 있는 모임입니다."
            );
        }

        // 6. 유효한 승인 대기 신청 중복 확인
        // 7일이 경과한 신청은 중복 신청 검사에서 제외
        // 거절 및 만료된 신청은 실제 가입 이력이 없으면 재신청 가능
        if (mapper.countPendingRequest(
                groupId, memberId) > 0) {
            throw new IllegalStateException(
                    "이미 가입 승인 대기 중입니다."
            );
        }

        // 7. 모임 정원 확인
        if (mapper.countCurrentMembers(groupId)
                >= maxMembers) {
            throw new IllegalStateException(
                    "모임 정원이 가득 찼습니다."
            );
        }

        // 8. 비공개 모임 인증코드 확인
        String codeVerifiedYn = "N";

        if ("PRIVATE".equals(visibility)) {

            if (!"INVITE".equals(via)) {
                throw new IllegalArgumentException(
                        "비공개 모임은 초대 경로로만 신청할 수 있습니다."
                );
            }

            if (mapper.countValidVerification(
                    groupId, memberId) == 0) {
                throw new IllegalStateException(
                        "비공개 모임 인증코드를 먼저 확인해 주세요."
                );
            }

            codeVerifiedYn = "Y";
        }

        // 9. 가입 신청 ID 생성
        Long requestId = mapper.nextJoinRequestId();

        // 10. 가입 신청 저장
        int inserted = mapper.insertJoinRequest(
                requestId,
                groupId,
                memberId,
                via,
                codeVerifiedYn
        );

        if (inserted != 1) {
            throw new IllegalStateException(
                    "가입 신청 저장에 실패했습니다."
            );
        }

        // 11. 가입 신청 성공 시 모임장에게 알림
        String applicantName =
                mapper.findMemberName(memberId);

        notificationService.notifyMember(
                ownerId,
                NotifType.JOIN_REQUEST,
                requestId,
                Map.of(
                        "groupId", groupId,
                        "group", String.valueOf(group.get("TITLE")),
                        "member", applicantName != null
                                ? applicantName
                                : "회원"
                )
        );

        return new GroupJoinRequestResponse(
                requestId,
                groupId,
                "PENDING",
                "가입 신청이 완료되었습니다."
        );
    }

    // ==================================================
    // F16 - 모임장 가입 신청 목록 조회
    // ==================================================

    @Transactional(readOnly = true)
    public List<GroupPendingJoinRequestResponse> getPendingRequests(
            Long groupId,
            Long ownerId
    ) {

        // 1. 요청값 검증
        if (groupId == null || groupId <= 0
                || ownerId == null || ownerId <= 0) {
            throw new IllegalArgumentException(
                    "모임 또는 회원 정보가 올바르지 않습니다."
            );
        }

        // 2. 모임 정보 조회
        // 읽기 전용 조회이므로 FOR UPDATE 사용하지 않음
        Map<String, Object> group =
                mapper.findGroup(groupId);

        if (group == null) {
            throw new IllegalArgumentException(
                    "존재하지 않는 모임입니다."
            );
        }

        // 3. 로그인 회원이 실제 모임장인지 확인
        Long actualOwnerId =
                ((Number) group.get("OWNER_ID")).longValue();

        if (!actualOwnerId.equals(ownerId)) {
            throw new SecurityException(
                    "모임장만 가입 신청 목록을 조회할 수 있습니다."
            );
        }

        // 4. 7일이 경과하지 않은 승인 대기 신청만 조회
        return mapper.findPendingRequests(groupId);
    }

    // ==================================================
    // F16 - 모임장 가입 신청 승인
    // ==================================================

    @Transactional
    public void approveJoinRequest(
            Long groupId,
            Long requestId,
            Long ownerId
    ) {

        // 1. 요청값 검증
        if (groupId == null || groupId <= 0
                || requestId == null || requestId <= 0
                || ownerId == null || ownerId <= 0) {
            throw new IllegalArgumentException(
                    "모임, 가입 신청 또는 회원 정보가 올바르지 않습니다."
            );
        }

        // 2. 모임 잠금
        // 동시 승인 시 정원 초과 방지
        Map<String, Object> group =
                mapper.lockGroup(groupId);

        if (group == null) {
            throw new IllegalArgumentException(
                    "존재하지 않는 모임입니다."
            );
        }

        // 3. 모임장 권한 확인
        Long actualOwnerId =
                ((Number) group.get("OWNER_ID")).longValue();

        if (!actualOwnerId.equals(ownerId)) {
            throw new SecurityException(
                    "모임장만 가입 신청을 승인할 수 있습니다."
            );
        }

        // 4. 모임 상태 확인
        String groupStatus =
                String.valueOf(group.get("STATUS"));

        if (!"RECRUITING".equals(groupStatus)) {
            throw new IllegalStateException(
                    "현재 가입 승인을 진행할 수 없는 모임입니다."
            );
        }

        // 5. 가입 신청 조회
        Map<String, Object> joinRequest =
                mapper.findJoinRequestForDecision(
                        groupId,
                        requestId
                );

        if (joinRequest == null) {
            throw new IllegalArgumentException(
                    "존재하지 않는 가입 신청입니다."
            );
        }

        // 6. 가입 신청 7일 만료 여부 확인
        if ("Y".equals(
                String.valueOf(joinRequest.get("EXPIRED_YN"))
        )) {
            throw new IllegalStateException(
                    "가입 신청 후 7일이 경과하여 만료되었습니다."
            );
        }

        // 7. PENDING 상태 확인
        String requestStatus =
                String.valueOf(joinRequest.get("STATUS"));

        if (!"PENDING".equals(requestStatus)) {
            throw new IllegalStateException(
                    "이미 처리된 가입 신청입니다."
            );
        }

        // 8. 신청 회원 ID 확인
        Long memberId =
                ((Number) joinRequest.get("MEMBER_ID"))
                        .longValue();

        if (actualOwnerId.equals(memberId)) {
            throw new IllegalStateException(
                    "모임장은 가입 신청 대상이 될 수 없습니다."
            );
        }

        // 9. 기존 참여 이력 재확인
        // 신청 이후 회원 상태가 변경되었을 수 있으므로 재검사
        // 기존 가입 이력이 있는 회원은 승인 불가
        if (mapper.countExistingMembership(
                groupId, memberId) > 0) {
            throw new IllegalStateException(
                    "이미 참여 중이거나 탈퇴 이력이 있는 모임입니다."
            );
        }

        // 10. 모임 정원 재확인
        int maxMembers =
                ((Number) group.get("MAX_MEMBERS")).intValue();

        int currentMembers =
                mapper.countCurrentMembers(groupId);

        if (currentMembers >= maxMembers) {
            throw new IllegalStateException(
                    "모임 정원이 가득 찼습니다."
            );
        }

        // 11. 비공개 모임 인증 여부 확인
        String visibility =
                String.valueOf(group.get("VISIBILITY"));

        if ("PRIVATE".equals(visibility)) {

            String verifiedYn =
                    String.valueOf(
                            joinRequest.get("CODE_VERIFIED_YN")
                    );

            if (!"Y".equals(verifiedYn)) {
                throw new IllegalStateException(
                        "비공개 모임 인증이 완료되지 않은 신청입니다."
                );
            }
        }

        // 12. 가입 신청 상태 APPROVED 변경
        // Mapper에서도 PENDING 및 7일 만료 조건 재확인
        int updated = mapper.approveJoinRequest(
                groupId,
                requestId,
                ownerId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "가입 신청 승인 처리에 실패했습니다."
            );
        }

        // 13. 신규 회원 참여 관계 생성
        // 기존 탈퇴 회원의 재활성화는 허용하지 않음
        int inserted = mapper.insertApprovedMember(
                groupId,
                memberId
        );

        if (inserted != 1) {
            throw new IllegalStateException(
                    "모임원 가입 처리에 실패했습니다."
            );
        }

        // 14. 가입 승인 성공 시 신청자에게 알림
        notificationService.notifyMember(
                memberId,
                NotifType.JOIN_RESULT,
                requestId,
                Map.of(
                        "group", String.valueOf(group.get("TITLE")),
                        "result", "승인"
                )
        );
    }

    // ==================================================
    // F16 - 모임장 가입 신청 거절
    // ==================================================

    @Transactional
    public void rejectJoinRequest(
            Long groupId,
            Long requestId,
            Long ownerId
    ) {

        // 1. 요청값 검증
        if (groupId == null || groupId <= 0
                || requestId == null || requestId <= 0
                || ownerId == null || ownerId <= 0) {
            throw new IllegalArgumentException(
                    "모임, 가입 신청 또는 회원 정보가 올바르지 않습니다."
            );
        }

        // 2. 모임 조회 및 잠금
        Map<String, Object> group =
                mapper.lockGroup(groupId);

        if (group == null) {
            throw new IllegalArgumentException(
                    "존재하지 않는 모임입니다."
            );
        }

        // 3. 모임장 권한 확인
        Long actualOwnerId =
                ((Number) group.get("OWNER_ID")).longValue();

        if (!actualOwnerId.equals(ownerId)) {
            throw new SecurityException(
                    "모임장만 가입 신청을 거절할 수 있습니다."
            );
        }

        // 4. 가입 신청 조회
        Map<String, Object> joinRequest =
                mapper.findJoinRequestForDecision(
                        groupId,
                        requestId
                );

        if (joinRequest == null) {
            throw new IllegalArgumentException(
                    "존재하지 않는 가입 신청입니다."
            );
        }

        // 5. 가입 신청 7일 만료 여부 확인
        if ("Y".equals(
                String.valueOf(joinRequest.get("EXPIRED_YN"))
        )) {
            throw new IllegalStateException(
                    "가입 신청 후 7일이 경과하여 만료되었습니다."
            );
        }

        // 6. PENDING 상태만 거절 가능
        String status =
                String.valueOf(joinRequest.get("STATUS"));

        if (!"PENDING".equals(status)) {
            throw new IllegalStateException(
                    "이미 처리된 가입 신청입니다."
            );
        }

        // 7. 가입 신청 거절
        int updated = mapper.rejectJoinRequest(
                groupId,
                requestId,
                ownerId
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "가입 신청 거절 처리에 실패했습니다."
            );
        }

        // 8. 가입 거절 성공 시 신청자에게 알림
        Long applicantId =
                ((Number) joinRequest.get("MEMBER_ID"))
                        .longValue();

        notificationService.notifyMember(
                applicantId,
                NotifType.JOIN_RESULT,
                requestId,
                Map.of(
                        "group", String.valueOf(group.get("TITLE")),
                        "result", "거절"
                )
        );
    }
}