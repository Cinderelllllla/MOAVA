
package com.moava.group.create.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface GroupCreateMapper {

    int countActiveMember(
        @Param("memberId") Long memberId
    );

    // 활성 회원 이름 조회 (예금주명 암호화에 사용)
    String findActiveMemberName(
            @Param("memberId") Long memberId
    );

    int insertOwnerMember(
    @Param("groupId") Long groupId,
    @Param("memberId") Long memberId
    );

    Long nextGroupId();

    int insertGroup(
        @Param("groupId") Long groupId,
        @Param("ownerId") Long ownerId,
        @Param("title") String title,
        @Param("purpose") String purpose,
        @Param("regionSido") String regionSido,
        @Param("regionSigungu") String regionSigungu,
        @Param("amount") Long amount,
        @Param("maxMembers") Integer maxMembers,
        @Param("months") Integer months,
        @Param("payDay") Integer payDay,
        @Param("assumedRate") BigDecimal assumedRate,
        @Param("visibility") String visibility,
        @Param("inviteCode") String inviteCode,
        @Param("rulesTitle") String rulesTitle,
        @Param("rulesBody") String rulesBody,
        @Param("description") String description,
        @Param("overduePolicy") String overduePolicy
    );


    int insertGroupAccount(
        @Param("groupId") Long groupId,
        @Param("bankName") String bankName,
        @Param("holderNameEnc") String holderNameEnc,
        @Param("accountNoEnc") String accountNoEnc,
        @Param("accountNoLast4") String accountNoLast4,
        @Param("accountNoHash") String accountNoHash,
        @Param("keyVer") Integer keyVer,
        @Param("registeredBy") Long registeredBy
);


    int insertGroupCreateAudit(
        @Param("groupId") Long groupId,
        @Param("actorMemberId") Long actorMemberId,
        @Param("description") String description
);


}
