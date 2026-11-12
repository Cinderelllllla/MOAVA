package com.moava.common.response;

import java.util.Map;

/**
 * API 명세서 v0.5 §2 공통 응답 형식.
 * <pre>
 * 성공: {"data": {...}, "meta": {"requestId": "req-..."}}
 * 실패: {"error": {"code": "...", "message": "..."}, "requestId": "req-..."}
 * </pre>
 */
public record ApiResponse<T>(T data, Map<String, Object> meta) {

    public static <T> ApiResponse<T> ok(T data, String requestId) {
        return new ApiResponse<>(data, Map.of("requestId", requestId));
    }

    public record Error(Map<String, String> error, String requestId) {
        public static Error of(String code, String message, String requestId) {
            return new Error(Map.of("code", code, "message", message), requestId);
        }
    }
}
