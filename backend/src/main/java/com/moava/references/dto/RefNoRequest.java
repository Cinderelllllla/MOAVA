package com.moava.references.dto;

import lombok.Getter;
import lombok.Setter;

// F24 참조번호 지정·정정 요청 (JSON 본문)
// - 지정: refNo만 사용. null이면 서버가 다음 번호(SEQ_REF_NO)로 지정
// - 정정: refNo(새 번호)와 reason(정정 사유) 모두 필수
@Getter
@Setter
public class RefNoRequest {
    private String refNo;
    private String reason;
}