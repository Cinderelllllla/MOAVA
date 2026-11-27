package com.moava.group.search.event;

/**
 * 모임 생성·수정·상태변경·회원변동 커밋 후 Elasticsearch {@code moava_group} 인덱스를
 * 비동기 동기화하기 위한 도메인 이벤트.
 */
public record GroupSearchSyncEvent(Long groupId) {}
