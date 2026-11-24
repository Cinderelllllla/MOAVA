package com.moava.openbanking.exception;

import lombok.Getter;

@Getter
public class KftcOpenBankingException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final KftcResponseCode responseCode;

    public KftcOpenBankingException(KftcResponseCode responseCode) {
        super(responseCode.getDefaultMessage());
        this.responseCode = responseCode;
    }

    public KftcOpenBankingException(KftcResponseCode responseCode, String customMessage) {
        super(customMessage != null && !customMessage.isBlank() ? customMessage : responseCode.getDefaultMessage());
        this.responseCode = responseCode;
    }
}
