
package com.moava.group.create.dto;

import java.math.BigDecimal;

public record GroupCreateRequest(
    String title,
    String purpose,
    String region,
    Long amount,
    Integer maxMembers,
    Integer months,
    Integer payDay,
    BigDecimal assumedRate,
    String visibility,
    String rulesTitle,
    String rulesBody,
    String description,
    String overduePolicy,
    String bankName,
    String accountNumber
) {
}
//React에서 Spring Boot로 전달할 모임 생성 데이터를 담는 역할