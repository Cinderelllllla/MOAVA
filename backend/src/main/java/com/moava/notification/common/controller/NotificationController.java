package com.moava.notification.common.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moava.common.response.ApiResponse;
import com.moava.notification.common.dto.NotificationHideRequest;
import com.moava.notification.common.dto.NotificationListResponse;
import com.moava.notification.common.dto.NotificationReceiver;
import com.moava.notification.common.service.NotificationException;
import com.moava.notification.common.service.NotificationInboxService;

/**
 * F35 인앱 알림함 API (API 명세서 v0.5). 회원(S13)·관리자(A07) 공용.
 * 받는 사람은 로그인 토큰(JWT)으로 정한다. 요청에 회원·관리자 ID를 따로 받지 않는다.
 * 토큰 권한이 ROLE_ADMIN 이면 관리자 알림함, 아니면 회원 알림함.
 *
 * <pre>
 * GET   /api/v1/notifications?read=N&page=0&size=20   목록
 * GET   /api/v1/notifications/unread-count            안 읽은 개수 (배지)
 * PATCH /api/v1/notifications/{id}/read               하나 읽음
 * POST  /api/v1/notifications/read-all                모두 읽음
 * POST  /api/v1/notifications/hide   {"ids":[1,2]}     선택 삭제(숨김)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationInboxService inboxService;

    public NotificationController(NotificationInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @RequestParam(required = false) String read,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        NotificationReceiver r = resolveReceiver(authentication);
        return ApiResponse.ok(inboxService.list(r, read, page, size), requestId());
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Integer>> unreadCount(Authentication authentication) {
        NotificationReceiver r = resolveReceiver(authentication);
        return ApiResponse.ok(Map.of("count", inboxService.unreadCount(r)), requestId());
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Map<String, Object>> markRead(
            @PathVariable long notificationId,
            Authentication authentication) {
        NotificationReceiver r = resolveReceiver(authentication);
        inboxService.markRead(r, notificationId);
        return ApiResponse.ok(Map.of("id", notificationId, "read", true), requestId());
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(Authentication authentication) {
        NotificationReceiver r = resolveReceiver(authentication);
        return ApiResponse.ok(Map.of("updated", inboxService.markAllRead(r)), requestId());
    }

    @PostMapping("/hide")
    public ApiResponse<Map<String, Integer>> hide(
            @RequestBody(required = false) NotificationHideRequest body,
            Authentication authentication) {
        NotificationReceiver r = resolveReceiver(authentication);
        int hidden = inboxService.hide(r, body == null ? null : body.ids());
        return ApiResponse.ok(Map.of("hidden", hidden), requestId());
    }

    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<ApiResponse.Error> handle(NotificationException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.Error.of(e.getCode(), e.getMessage(), requestId()));
    }

    /**
     * 받는 사람 결정: JWT 필터가 넣어 둔 로그인 정보(Authentication)를 파라미터로 받아 쓴다.
     * - principal 이름 = 토큰 subject (회원 토큰이면 MEMBER_ID, 관리자 토큰이면 ADMIN_ID)
     * - 회원·관리자 구분은 토큰의 role claim → JwtAuthenticationFilter가 붙인 권한으로 한다.
     *   ROLE_ADMIN 이면 관리자 알림함(ADMIN_ID), 그 외(ROLE_MEMBER)는 회원 알림함(MEMBER_ID).
     *   두 ID 체계는 번호가 겹칠 수 있으므로 반드시 권한으로 나눈다.
     * - 익명 사용자, 이름이 양의 정수가 아닌 경우는 명시적으로 401.
     */
    static NotificationReceiver resolveReceiver(Authentication auth) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            throw NotificationException.unauthorized();
        }
        String name = auth.getName();
        if (name == null || !name.matches("[1-9][0-9]{0,18}")) {
            throw NotificationException.unauthorized();
        }
        long id;
        try {
            id = Long.parseLong(name);
        } catch (NumberFormatException e) { // 19자리 long 범위 초과
            throw NotificationException.unauthorized();
        }
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return admin ? NotificationReceiver.admin(id) : NotificationReceiver.member(id);
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
