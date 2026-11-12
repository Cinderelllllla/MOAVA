package com.moava.notification.common.service;

import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moava.notification.common.dto.NotificationItem;
import com.moava.notification.common.dto.NotificationListResponse;
import com.moava.notification.common.dto.NotificationReceiver;
import com.moava.notification.common.mapper.NotificationInboxMapper;

/**
 * F35 인앱 알림함 (회원·관리자 공용). 받는 사람은 컨트롤러가 로그인 정보로 정해서 넘긴다.
 */
@Service
public class NotificationInboxService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    static final int MAX_HIDE_IDS = 100;

    private final NotificationInboxMapper mapper;

    public NotificationInboxService(NotificationInboxMapper mapper) {
        this.mapper = mapper;
    }

    /** read: "Y" / "N" / null(전체). page는 0부터. */
    @Transactional(readOnly = true)
    public NotificationListResponse list(NotificationReceiver r, String read, int page, int size) {
        String readFilter = normalizeRead(read);
        int p = Math.max(page, 0);
        int s = (size <= 0) ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        // 한 건 더 읽어서 다음 페이지가 있는지 판단
        List<NotificationItem> rows = mapper.selectList(r, readFilter, p * s, s + 1);
        boolean hasNext = rows.size() > s;
        List<NotificationItem> items = hasNext ? rows.subList(0, s) : rows;
        return new NotificationListResponse(List.copyOf(items), p, s, hasNext);
    }

    @Transactional(readOnly = true)
    public int unreadCount(NotificationReceiver r) {
        return mapper.countUnread(r);
    }

    /** 내 알림이 아니거나 이미 숨긴 알림이면 404 */
    @Transactional
    public void markRead(NotificationReceiver r, long notificationId) {
        if (mapper.markRead(r, notificationId) == 0) {
            throw NotificationException.notFound();
        }
    }

    @Transactional
    public int markAllRead(NotificationReceiver r) {
        return mapper.markAllRead(r);
    }

    /** 선택 삭제 = 숨김(DELETED_YN='Y'). 남의 알림 ID가 섞여 있으면 그 건은 무시된다. */
    @Transactional
    public int hide(NotificationReceiver r, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw NotificationException.badRequest("삭제할 알림을 선택해 주세요.");
        }
        List<Long> unique = List.copyOf(new LinkedHashSet<>(ids.stream().filter(id -> id != null).toList()));
        if (unique.isEmpty()) {
            throw NotificationException.badRequest("삭제할 알림을 선택해 주세요.");
        }
        if (unique.size() > MAX_HIDE_IDS) {
            throw NotificationException.badRequest("한 번에 " + MAX_HIDE_IDS + "개까지 삭제할 수 있습니다.");
        }
        return mapper.hide(r, unique);
    }

    private static String normalizeRead(String read) {
        if (read == null || read.isBlank()) return null;
        String v = read.trim().toUpperCase();
        if (!v.equals("Y") && !v.equals("N")) {
            throw NotificationException.badRequest("read는 Y 또는 N만 가능합니다.");
        }
        return v;
    }
}
