package com.moava.notification.common.service;

import com.moava.notification.common.domain.NotifType;
import com.moava.notification.common.dto.NotificationInsert;
import com.moava.notification.common.dto.NotificationSmsEvent;
import com.moava.notification.common.mapper.NotificationMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 공통 함수. 다른 기능은 이 세 메서드만 호출한다.
 *
 * <ul>
 *   <li>notifyMember : 회원 1명</li>
 *   <li>notifyGroup  : 모임의 ACTIVE 회원 전원 (행위자 등 제외 가능)</li>
 *   <li>notifyAdmins : ACTIVE 관리자 전원 (NOTIFICATION.ADMIN_ID)</li>
 * </ul>
 *
 * <p>호출한 쪽의 트랜잭션에 함께 묶인다. 업무가 롤백되면 알림도 저장되지 않는다.
 * SMS 대상 코드는 커밋 이후에만 발송 이벤트가 처리된다(NotificationSmsListener).
 */
@Service
public class NotificationService {

    /** Oracle VARCHAR2 기본 길이 단위는 바이트. 한글은 UTF-8에서 글자당 3바이트. */
    private static final int TITLE_MAX_BYTES = 100;
    private static final int BODY_MAX_BYTES = 500;
    private static final int LINK_MAX_BYTES = 100;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9]+)}");

    private final NotificationMapper mapper;
    private final ApplicationEventPublisher events;

    public NotificationService(NotificationMapper mapper, ApplicationEventPublisher events) {
        this.mapper = mapper;
        this.events = events;
    }

    /** 회원 1명에게 알림 1건. 저장된 NOTIFICATION_ID를 돌려준다. */
    @Transactional
    public long notifyMember(long memberId, NotifType type, Long refId, Map<String, ?> params) {
        NotificationInsert row = build(type, refId, params);
        row.setMemberId(memberId);
        mapper.insertForMember(row);
        if (type.sms()) {
            events.publishEvent(new NotificationSmsEvent(row.getNotificationId(), memberId, type, row.getBody()));
        }
        return row.getNotificationId();
    }

    /** 모임의 ACTIVE 회원 전원에게 1건씩. excludeMemberIds(행위자 본인 등)는 제외. 저장 건수를 돌려준다. */
    @Transactional
    public int notifyGroup(long groupId, NotifType type, Long refId, Map<String, ?> params,
                           Collection<Long> excludeMemberIds) {
        NotificationInsert row = build(type, refId, params);
        List<Long> exclude = excludeMemberIds == null ? List.of() : List.copyOf(excludeMemberIds);
        return mapper.insertForGroup(groupId, exclude, row);
    }

    /** ACTIVE 관리자 전원에게 1건씩 (미납 5일차·해산 신청·신고 접수·계좌 확인 요청). */
    @Transactional
    public int notifyAdmins(NotifType type, Long refId, Map<String, ?> params) {
        NotificationInsert row = build(type, refId, params);
        return mapper.insertForAdmins(row);
    }

    // ── 내부 ──────────────────────────────────────────────────

    private NotificationInsert build(NotifType type, Long refId, Map<String, ?> params) {
        NotificationInsert row = new NotificationInsert();
        row.setNotifType(type.name());
        row.setTitle(cutBytes(render(type.titleTemplate(), params, type), TITLE_MAX_BYTES));
        row.setBody(cutBytes(render(type.bodyTemplate(), params, type), BODY_MAX_BYTES));
        row.setLinkPage(cutBytes(render(type.linkTemplate(), params, type), LINK_MAX_BYTES));
        row.setRefId(refId);
        return row;
    }

    /** {키}를 params 값으로 바꾼다. 빠진 키가 있으면 개발 중에 바로 알 수 있게 예외를 던진다. */
    static String render(String template, Map<String, ?> params, NotifType type) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            Object value = params == null ? null : params.get(key);
            if (value == null) {
                throw new IllegalArgumentException("알림 " + type + "에 필요한 값 '" + key + "'이(가) 없습니다.");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** UTF-8 바이트 기준으로 자르고, 잘렸으면 끝에 '…'를 붙인다. */
    static String cutBytes(String s, int maxBytes) {
        if (s.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return s;
        int limit = maxBytes - "…".getBytes(StandardCharsets.UTF_8).length;
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (used + len > limit) break;
            sb.appendCodePoint(cp);
            used += len;
            i += Character.charCount(cp);
        }
        return sb.append("…").toString();
    }
}
