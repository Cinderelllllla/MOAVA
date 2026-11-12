package com.moava.notification.common.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    /**
     * 로그인(JWT)이 붙기 전 개발용 임시 스위치. true일 때만 X-Member-Id / X-Admin-Id 헤더로 받는 사람을 정한다.
     * 기본값 false. 본인 PC의 application-secret.properties 등에서만 true로 켤 것 (팀 공통 설정에 넣지 않음).
     */
    @Value("${moava.dev.receiver-header-enabled:false}")
    private boolean devHeaderEnabled;

    public NotificationController(NotificationInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping
    public ApiResponse<NotificationListResponse> list(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId,
            @RequestParam(required = false) String read,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        NotificationReceiver r = currentReceiver(memberId, adminId);
        return ApiResponse.ok(inboxService.list(r, read, page, size), requestId());
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Integer>> unreadCount(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId) {
        NotificationReceiver r = currentReceiver(memberId, adminId);
        return ApiResponse.ok(Map.of("count", inboxService.unreadCount(r)), requestId());
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Map<String, Object>> markRead(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId,
            @PathVariable long notificationId) {
        NotificationReceiver r = currentReceiver(memberId, adminId);
        inboxService.markRead(r, notificationId);
        return ApiResponse.ok(Map.of("id", notificationId, "read", true), requestId());
    }

    @PostMapping("/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId) {
        NotificationReceiver r = currentReceiver(memberId, adminId);
        return ApiResponse.ok(Map.of("updated", inboxService.markAllRead(r)), requestId());
    }

    @PostMapping("/hide")
    public ApiResponse<Map<String, Integer>> hide(
            @RequestHeader(value = "X-Member-Id", required = false) Long memberId,
            @RequestHeader(value = "X-Admin-Id", required = false) Long adminId,
            @RequestBody(required = false) NotificationHideRequest body) {
        NotificationReceiver r = currentReceiver(memberId, adminId);
        int hidden = inboxService.hide(r, body == null ? null : body.ids());
        return ApiResponse.ok(Map.of("hidden", hidden), requestId());
    }

    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<ApiResponse.Error> handle(NotificationException e) {
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.Error.of(e.getCode(), e.getMessage(), requestId()));
    }

    /**
     * 받는 사람 결정.
     * TODO(JWT): 로그인 담당의 JWT 필터가 생기면 SecurityContext에서 role(MEMBER|ADMIN)과 subject(ID)를 꺼내
     *            NotificationReceiver.member(id) / admin(id)로 바꾸고, 헤더 방식과 devHeaderEnabled는 삭제한다.
     */
    private NotificationReceiver currentReceiver(Long memberId, Long adminId) {
        if (!devHeaderEnabled) {
            throw NotificationException.unauthorized();
        }
        if (memberId != null && adminId == null) return NotificationReceiver.member(memberId);
        if (adminId != null && memberId == null) return NotificationReceiver.admin(adminId);
        throw NotificationException.badRequest("X-Member-Id 또는 X-Admin-Id 중 하나만 보내 주세요. (개발용)");
    }

    private static String requestId() {
        return "req-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
