package com.moava.group.search;

import com.moava.group.query.mapper.GroupQueryMapper;
import com.moava.group.search.dto.GroupSearchDocument;
import com.moava.group.search.dto.GroupSearchIndexRow;
import com.moava.group.search.event.GroupSearchSyncEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * F11/F12 — Oracle DB({@code GYE_GROUP})와 Elasticsearch({@code moava_group}) 간 색인 동기화 서비스.
 * <ul>
 *   <li>{@code PUBLIC} 이고 {@code DISSOLVED} 가 아닌 모임만 색인한다.</li>
 *   <li>{@code PRIVATE} 전환 또는 {@code DISSOLVED} 해산 시 인덱스에서 즉시 제거한다.</li>
 *   <li>{@link TransactionalEventListener} + {@link Async} 로 본 트랜잭션 커밋 이후 격리 실행된다.</li>
 * </ul>
 */
@Service
public class GroupSearchSyncService {

    private static final Logger log = LoggerFactory.getLogger(GroupSearchSyncService.class);

    private final GroupQueryMapper groupQueryMapper;
    private final GroupElasticsearchClient elasticsearchClient;

    public GroupSearchSyncService(
            GroupQueryMapper groupQueryMapper,
            GroupElasticsearchClient elasticsearchClient
    ) {
        this.groupQueryMapper = groupQueryMapper;
        this.elasticsearchClient = elasticsearchClient;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGroupChanged(GroupSearchSyncEvent event) {
        if (event == null || event.groupId() == null) {
            return;
        }
        syncGroup(event.groupId());
    }

    /** 단건 모임을 DB 기준으로 조회해 ES 에 색인하거나 삭제한다. */
    public boolean syncGroup(Long groupId) {
        if (!elasticsearchClient.isEnabled() || groupId == null) {
            return false;
        }
        try {
            GroupSearchIndexRow row = groupQueryMapper.findGroupIndexRowById(groupId);
            if (row == null || !"PUBLIC".equals(row.visibility()) || "DISSOLVED".equals(row.status())) {
                return elasticsearchClient.deleteDocument(groupId);
            }
            return elasticsearchClient.indexDocument(GroupSearchDocument.fromRow(row));
        } catch (RuntimeException e) {
            log.warn("모임 ES 동기화 실패 groupId={} cause={}", groupId, e.getClass().getSimpleName());
            return false;
        }
    }

    /** 기동 시 또는 재색인 요청 시 전체 공개 모임을 {@code moava_group} 인덱스에 일괄 적재한다. */
    public int reindexAll() {
        if (!elasticsearchClient.isEnabled()) {
            return 0;
        }
        if (!elasticsearchClient.ensureIndex()) {
            log.warn("Elasticsearch({}) 연결 불가 — DB LIKE 폴백으로 동작합니다.", elasticsearchClient.indexName());
            return 0;
        }
        List<GroupSearchIndexRow> rows = groupQueryMapper.findPublicGroupIndexRows();
        if (rows == null || rows.isEmpty()) {
            log.info("Elasticsearch 초기 색인 대상 공개 모임 없음 index={}", elasticsearchClient.indexName());
            return 0;
        }
        List<GroupSearchDocument> docs = rows.stream()
                .filter(row -> "PUBLIC".equals(row.visibility()) && !"DISSOLVED".equals(row.status()))
                .map(GroupSearchDocument::fromRow)
                .toList();
        int indexed = elasticsearchClient.bulkIndex(docs);
        log.info("Elasticsearch 공개 모임 일괄 색인 완료 index={} count={}", elasticsearchClient.indexName(), indexed);
        return indexed;
    }
}
