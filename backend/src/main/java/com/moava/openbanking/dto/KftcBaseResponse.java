package com.moava.openbanking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moava.openbanking.exception.KftcResponseCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 금융결제원 오픈뱅킹 v2.0 표준 공통 응답 헤더 필드
 */
@Getter
@Setter
public class KftcBaseResponse {

    private static final DateTimeFormatter DTM_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @JsonProperty("api_tran_id")
    private String apiTranId;

    @JsonProperty("api_tran_dtm")
    private String apiTranDtm;

    @JsonProperty("rsp_code")
    private String rspCode;

    @JsonProperty("rsp_message")
    private String rspMessage;

    public void fillSuccess() {
        fill(KftcResponseCode.SUCCESS, KftcResponseCode.SUCCESS.getDefaultMessage());
    }

    public void fill(KftcResponseCode code, String message) {
        this.apiTranId = UUID.randomUUID().toString();
        this.apiTranDtm = LocalDateTime.now().format(DTM_FORMATTER);
        this.rspCode = code.getCode();
        this.rspMessage = (message != null && !message.isBlank()) ? message : code.getDefaultMessage();
    }

    public static KftcBaseResponse errorOf(KftcResponseCode code, String message) {
        KftcBaseResponse res = new KftcBaseResponse();
        res.fill(code, message);
        return res;
    }
}
