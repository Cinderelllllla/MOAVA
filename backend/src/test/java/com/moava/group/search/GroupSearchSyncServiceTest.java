package com.moava.group.search;

import com.moava.group.query.mapper.GroupQueryMapper;
import com.moava.group.search.dto.GroupSearchDocument;
import com.moava.group.search.dto.GroupSearchIndexRow;
import com.moava.group.search.event.GroupSearchSyncEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** F12 Oracle -> Elasticsearch 색인 동기화 서비스 단위 테스트 */
@ExtendWith(MockitoExtension.class)
class GroupSearchSyncServiceTest {

    @Mock
    GroupQueryMapper groupQueryMapper;

    @Mock
    GroupElasticsearchClient elasticsearchClient;

    GroupSearchSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new GroupSearchSyncService(groupQueryMapper, elasticsearchClient);
    }

    private static GroupSearchIndexRow sampleRow(Long groupId, String visibility, String status) {
        return new GroupSearchIndexRow(
                groupId,
                "마포 목돈 모임",
                1L,
                status,
                "SAVINGS",
                visibility,
                "서울특별시",
                "마포구",
                "합정동",
                200_000L,
                6,
                2,
                new BigDecimal("37.5495072"),
                new BigDecimal("126.9139405"),
                "직장인 저축 모임"
        );
    }

    @Test
    @DisplayName("PUBLIC 이고 해산되지 않은 모임은 ES 문서로 색인한다")
    void syncGroupIndexesPublicActiveGroup() {
        when(elasticsearchClient.isEnabled()).thenReturn(true);
        when(groupQueryMapper.findGroupIndexRowById(101L))
                .thenReturn(sampleRow(101L, "PUBLIC", "RECRUITING"));
        when(elasticsearchClient.indexDocument(any())).thenReturn(true);

        assertTrue(syncService.syncGroup(101L));

        ArgumentCaptor<GroupSearchDocument> captor = ArgumentCaptor.forClass(GroupSearchDocument.class);
        verify(elasticsearchClient).indexDocument(captor.capture());
        assertEquals(101L, captor.getValue().groupId());
        assertEquals("서울특별시 마포구 합정동", captor.getValue().regionFull());
        verify(elasticsearchClient, never()).deleteDocument(any());
    }

    @Test
    @DisplayName("PRIVATE 모임이나 DISSOLVED(해산 완료) 모임은 ES 인덱스에서 삭제한다")
    void syncGroupDeletesPrivateOrDissolvedGroup() {
        when(elasticsearchClient.isEnabled()).thenReturn(true);
        when(groupQueryMapper.findGroupIndexRowById(102L))
                .thenReturn(sampleRow(102L, "PRIVATE", "RECRUITING"));
        when(groupQueryMapper.findGroupIndexRowById(103L))
                .thenReturn(sampleRow(103L, "PUBLIC", "DISSOLVED"));
        when(groupQueryMapper.findGroupIndexRowById(104L))
                .thenReturn(null);
        when(elasticsearchClient.deleteDocument(any())).thenReturn(true);

        assertTrue(syncService.syncGroup(102L));
        assertTrue(syncService.syncGroup(103L));
        assertTrue(syncService.syncGroup(104L));

        verify(elasticsearchClient).deleteDocument(102L);
        verify(elasticsearchClient).deleteDocument(103L);
        verify(elasticsearchClient).deleteDocument(104L);
        verify(elasticsearchClient, never()).indexDocument(any());
    }

    @Test
    @DisplayName("이벤트 리스너는 groupId가 유효할 때 단건 동기화를 수행한다")
    void onGroupChangedTriggersSync() {
        when(elasticsearchClient.isEnabled()).thenReturn(true);
        when(groupQueryMapper.findGroupIndexRowById(201L))
                .thenReturn(sampleRow(201L, "PUBLIC", "ACTIVE"));
        when(elasticsearchClient.indexDocument(any())).thenReturn(true);

        syncService.onGroupChanged(new GroupSearchSyncEvent(201L));

        verify(elasticsearchClient).indexDocument(any(GroupSearchDocument.class));
    }

    @Test
    @DisplayName("전체 재색인은 인덱스 보장 후 PUBLIC·비해산 모임만 벌크 적재한다")
    void reindexAllBulkIndexesPublicNonDissolvedGroupsOnly() {
        when(elasticsearchClient.isEnabled()).thenReturn(true);
        when(elasticsearchClient.ensureIndex()).thenReturn(true);
        when(groupQueryMapper.findPublicGroupIndexRows()).thenReturn(List.of(
                sampleRow(1L, "PUBLIC", "RECRUITING"),
                sampleRow(2L, "PRIVATE", "ACTIVE"),
                sampleRow(3L, "PUBLIC", "DISSOLVED")
        ));
        when(elasticsearchClient.bulkIndex(anyList())).thenReturn(1);

        int indexed = syncService.reindexAll();

        assertEquals(1, indexed);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GroupSearchDocument>> captor = ArgumentCaptor.forClass(List.class);
        verify(elasticsearchClient).bulkIndex(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(1L, captor.getValue().get(0).groupId());
    }

    @Test
    @DisplayName("인덱스 연결 불가 시 DB 조회 없이 0을 반환하며 예외를 전파하지 않는다")
    void reindexAllSkipsWhenEnsureIndexFails() {
        when(elasticsearchClient.isEnabled()).thenReturn(true);
        when(elasticsearchClient.ensureIndex()).thenReturn(false);

        assertEquals(0, syncService.reindexAll());
        verifyNoInteractions(groupQueryMapper);
    }
}
