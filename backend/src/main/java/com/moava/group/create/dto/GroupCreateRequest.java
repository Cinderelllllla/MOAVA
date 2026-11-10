
package com.moava.group.create.dto;

import java.math.BigDecimal;

public record GroupCreateRequest(
    String title,
    String purpose,
    String regionSido,
    String regionSigungu,   //지역을 처음부터 시·도와 시·군·구로 나누어 전달하는 방식
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