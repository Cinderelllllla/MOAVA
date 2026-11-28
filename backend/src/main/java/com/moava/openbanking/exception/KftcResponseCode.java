package com.moava.openbanking.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 금융결제원 오픈뱅킹 v2.0 모의 표준 응답 코드
 */
@Getter
public enum KftcResponseCode {

    SUCCESS("A0000", HttpStatus.OK, "정상 처리되었습니다."),
    ACCOUNT_NOT_FOUND("A0001", HttpStatus.NOT_FOUND, "존재하지 않거나 거래 불가능한 계좌입니다."),
    INSUFFICIENT_BALANCE("A0002", HttpStatus.BAD_REQUEST, "출금 계좌의 잔액이 부족합니다."),
    HOLDER_NAME_MISMATCH("A0003", HttpStatus.BAD_REQUEST, "예금주 실명이 계좌 정보와 일치하지 않습니다."),
    INVALID_AMOUNT("A0004", HttpStatus.BAD_REQUEST, "거래 금액은 1원 이상이어야 합니다."),
    UNAUTHORIZED_TARGET_ACCOUNT("A0005", HttpStatus.FORBIDDEN, "허용된 모임장 계좌 외 타 계좌로는 이체할 수 없습니다."),
    DUPLICATE_BANK_TRAN_ID("A0006", HttpStatus.CONFLICT, "이미 처리된 은행거래고유번호(bank_tran_id)입니다."),
    UNAUTHORIZED_TOKEN("A0007", HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 오픈뱅킹 인증 토큰입니다."),
    INSUFFICIENT_SCOPE("A0008", HttpStatus.FORBIDDEN, "요청한 API에 필요한 권한(scope)이 부족합니다."),
    INVALID_PARAMETER("A0009", HttpStatus.BAD_REQUEST, "요청 파라미터 형식이 올바르지 않습니다."),
    RATE_LIMIT_EXCEEDED("A0010", HttpStatus.TOO_MANY_REQUESTS, "요청 횟수가 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    SAME_ACCOUNT_TRANSFER("A0011", HttpStatus.BAD_REQUEST, "출금 계좌와 입금 계좌가 동일할 수 없습니다."),
    INTERNAL_ERROR("A9999", HttpStatus.INTERNAL_SERVER_ERROR, "오픈뱅킹 처리 중 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    KftcResponseCode(String code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
