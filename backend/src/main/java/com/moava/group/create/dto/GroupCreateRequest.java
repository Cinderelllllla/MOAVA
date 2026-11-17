
package com.moava.group.create.dto;

import java.math.BigDecimal;

public record GroupCreateRequest(
    String title,
    String purpose,
    String regionSido,
    String regionSigungu,
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
