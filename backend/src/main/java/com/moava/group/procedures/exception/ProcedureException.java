package com.moava.group.procedures.exception;

import org.springframework.http.HttpStatus;

public class ProcedureException extends RuntimeException {
    private final HttpStatus status;

    private ProcedureException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return switch (status) {
            case BAD_REQUEST -> "INVALID_REQUEST";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            default -> "PROCEDURE_ERROR";
        };
    }

    public static ProcedureException badRequest(String message) {
        return new ProcedureException(HttpStatus.BAD_REQUEST, message);
    }

    public static ProcedureException forbidden(String message) {
        return new ProcedureException(HttpStatus.FORBIDDEN, message);
    }

    public static ProcedureException notFound(String message) {
        return new ProcedureException(HttpStatus.NOT_FOUND, message);
    }

    public static ProcedureException conflict(String message) {
        return new ProcedureException(HttpStatus.CONFLICT, message);
    }
}
