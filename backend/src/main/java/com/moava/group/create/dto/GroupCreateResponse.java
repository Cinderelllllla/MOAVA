
package com.moava.group.create.dto;

public record GroupCreateResponse(
    Long groupId, //생성된 모임 ID
    Long ownerId, //모임장 회원 ID
    String status, //모임 상태 (RECRUITING)
    String message  //생성 성공 안내
) {
}
