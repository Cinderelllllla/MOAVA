package com.moava.group.create.service;

import com.moava.group.create.mapper.GroupJoinRequestMapper;
import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.service.NotificationService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class GroupJoinRequestExpireService {

    private final GroupJoinRequestMapper mapper;
    private final NotificationService notificationService;

    public GroupJoinRequestExpireService(
            GroupJoinRequestMapper mapper,
            NotificationService notificationService
    ) {
        this.mapper = mapper;
        this.notificationService = notificationService;
    }

    /**
     * 신청 후 7일이 경과한 PENDING 신청 ID 조회
     */
    @Transactional(readOnly = true)
    public List<Long> findExpiredRequestIds() {

        return mapper.findExpiredPendingRequestIds();
    }

    /**
     * 가입 신청 단건 자동 만료 처리
     *
     * 상태 변경에 성공한 경우에만 알림을 발송한다.
     */
    @Transactional
    public boolean expireRequest(Long requestId) {

        if (requestId == null || requestId <= 0) {
            return false;
        }

        // 1. 만료 대상 신청 정보 조회
        Map<String, Object> request =
                mapper.findExpiredPendingRequest(requestId);

        if (request == null) {
            return false;
        }

        // 2. PENDING 상태인 만료 대상만 EXPIRED 변경
        int updated =
                mapper.expireJoinRequest(requestId);

        // 다른 요청 또는 배치에서 이미 처리한 경우
        if (updated == 0) {
            return false;
        }

        if (updated != 1) {
            throw new IllegalStateException(
                    "가입 신청 자동 만료 처리에 실패했습니다."
            );
        }

        // 3. 신청 회원 정보 확인
        Long memberId =
                ((Number) request.get("MEMBER_ID"))
                        .longValue();

        String groupTitle =
                String.valueOf(request.get("TITLE"));

        // 4. 기존 공통 알림 서비스 사용
        notificationService.notifyMember(
                memberId,
                NotifType.JOIN_RESULT,
                requestId,
                Map.of(
                        "group", groupTitle,
                        "result", "만료"
                )
        );

        return true;
    }
}