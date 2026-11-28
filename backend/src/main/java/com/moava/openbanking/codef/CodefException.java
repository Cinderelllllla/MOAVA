package com.moava.openbanking.codef;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CodefException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus httpStatus;
    private final String code;

    public CodefException(HttpStatus httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public static CodefException badRequest(String code, String message) {
        return new CodefException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static CodefException unauthorized(String code, String message) {
        return new CodefException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static CodefException forbidden(String code, String message) {
        return new CodefException(HttpStatus.FORBIDDEN, code, message);
    }

    public static CodefException notFound(String code, String message) {
        return new CodefException(HttpStatus.NOT_FOUND, code, message);
    }

    public static CodefException tooManyRequests(String code, String message) {
        return new CodefException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }

    public static CodefException badGateway(String code, String message) {
        return new CodefException(HttpStatus.BAD_GATEWAY, code, message);
    }

    public static CodefException serviceUnavailable(String code, String message) {
        return new CodefException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
