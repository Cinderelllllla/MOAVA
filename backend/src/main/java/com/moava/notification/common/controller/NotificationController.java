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
 * F35 인앱 알림함 API (API 명세서 v0.5). 현재는 회원(S13)만 지원한다.
 * 받는 사람은 로그인 토큰(JWT)으로 정한다. 요청에 회원 ID를 따로 받지 않는다.
 * 관리자(A07) 알림함은 관리자 JWT 로그인(관리자 전용 claim)이 생긴 뒤 별도로 연결한다.
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
     * - principal 이름 = 토큰 subject = MEMBER_ID (JwtTokenProvider.createToken)
     * - 지금은 모든 토큰이 회원(ROLE_MEMBER)이므로 항상 회원 알림함으로 본다.
     *   관리자 알림함은 MEMBER_ID와 ADMIN_ID가 섞이지 않도록 관리자 전용 claim이 생긴 뒤에 추가한다.
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
        long memberId;
        try {
            memberId = Long.parseLong(name);
        } catch (NumberFormatException e) { // 19자리 long 범위 초과
            throw NotificationException.unauthorized();
        }
        return NotificationReceiver.member(memberId);
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
