package com.moava.group.create.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        name = "moava.group.join-request.expire.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class GroupJoinRequestExpireScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    GroupJoinRequestExpireScheduler.class
            );

    private final GroupJoinRequestExpireService expireService;

    public GroupJoinRequestExpireScheduler(
            GroupJoinRequestExpireService expireService
    ) {
        this.expireService = expireService;
    }

    /**
     * F16 - 가입 신청 7일 자동 만료
     *
     * 기본 실행 주기: 1분
     * 기존 FinanceSchedulingConfig 설정 사용
     */
    @Scheduled(
            cron = "${moava.group.join-request.expire.cron:0 * * * * *}",
            zone = "Asia/Seoul"
    )
    public void expirePendingRequests() {

        List<Long> requestIds =
                expireService.findExpiredRequestIds();

        if (requestIds.isEmpty()) {
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        for (Long requestId : requestIds) {

            try {

                if (expireService.expireRequest(requestId)) {
                    successCount++;
                }

            } catch (Exception e) {

                failureCount++;

                log.error(
                        "가입 신청 자동 만료 실패. requestId={}",
                        requestId,
                        e
                );
            }
        }

        log.info(
                "가입 신청 자동 만료 처리 완료. 성공={}, 실패={}",
                successCount,
                failureCount
        );
    }
}