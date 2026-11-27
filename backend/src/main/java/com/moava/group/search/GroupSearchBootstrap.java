package com.moava.group.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 기동 완료 후 비동기로 {@code moava_group} 인덱스 생성 및 초기 벌크 색인을 수행한다.
 * ES가 내려가 있어도 기동을 지연시키거나 실패시키지 않는다.
 */
@Component
public class GroupSearchBootstrap {

    private final GroupSearchSyncService syncService;
    private final boolean syncOnStartup;

    public GroupSearchBootstrap(
            GroupSearchSyncService syncService,
            @Value("${moava.elasticsearch.sync-on-startup:true}") boolean syncOnStartup
    ) {
        this.syncService = syncService;
        this.syncOnStartup = syncOnStartup;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!syncOnStartup) {
            return;
        }
        syncService.reindexAll();
    }
}
