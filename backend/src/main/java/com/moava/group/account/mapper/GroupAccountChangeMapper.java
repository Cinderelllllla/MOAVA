package com.moava.group.account.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moava.group.account.dto.GroupAccountConsentTargetInfo;


@Mapper
public interface GroupAccountChangeMapper {

    Long lockGroup(@Param("groupId") Long groupId);

    String findActiveOwnerName(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    int countCurrentAccount(
            @Param("groupId") Long groupId
    );

    int countPendingChange(
            @Param("groupId") Long groupId
    );

    int countDuplicateAccount(
            @Param("accountHash") String accountHash
    );

    Long nextAccountId();

    int insertPendingAccount(
            @Param("accountId") Long accountId,
            @Param("groupId") Long groupId,
            @Param("bankName") String bankName,
            @Param("holderNameEnc") String holderNameEnc,
            @Param("accountNoEnc") String accountNoEnc,
            @Param("last4") String last4,
            @Param("accountHash") String accountHash,
            @Param("keyVer") Integer keyVer,
            @Param("memberId") Long memberId
    );

    int insertChangeAudit(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("description") String description
    );


    // F20 - 조회 권한: 해당 모임의 활성 회원
    int countActiveGroupMember(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    // 진행 중인 계좌 변경 신청 ID
    Long findPendingAccountId(
            @Param("groupId") Long groupId
    );

    // 활성 모임원 수
    int countActiveGroupMembers(
            @Param("groupId") Long groupId
    );

    // 계좌 변경 동의 회원 수
    int countAgreedMembers(
            @Param("groupId") Long groupId,
            @Param("accountId") Long accountId
    );

    // 계좌 변경 거절 회원 수
    int countRejectedMembers(
            @Param("groupId") Long groupId,
            @Param("accountId") Long accountId
    );


    // F20 - 해당 신청에 이미 응답했는지 확인
    int countExistingAccountConsent(
            @Param("groupId") Long groupId,
            @Param("accountId") Long accountId,
            @Param("memberId") Long memberId
    );

    // F20 - 동의·거절 저장
    int insertAccountConsent(
            @Param("groupId") Long groupId,
            @Param("accountId") Long accountId,
            @Param("memberId") Long memberId,
            @Param("agreedYn") String agreedYn
    );

    // F20 - 동의·거절 감사 로그
    int insertConsentAudit(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("description") String description
    );


    // F20 - 진행 중인 계좌 변경 신청 종료
    int retirePendingAccount(
            @Param("groupId") Long groupId,
            @Param("accountId") Long accountId
    );


    // F20 - 변경 신청 계좌 인증 대상 조회
    com.moava.group.account.dto.GroupAccountVerificationTarget
    findPendingVerificationTarget(
            @Param("groupId") Long groupId,
            @Param("accountId") Long accountId
    );

    // F20 - 변경 신청 계좌 인증 성공 처리
    int updatePendingAccountVerified(
            @Param("groupId") Long groupId,
            @Param("accountId") Long accountId,
            @Param("accountNoEnc") String accountNoEnc,
            @Param("bankName") String bankName,
            @Param("keyVer") Integer keyVer
    );


    GroupAccountConsentTargetInfo findConsentTargetInfo(
            @Param("groupId") Long groupId,
            @Param("accountId") Long accountId,
            @Param("memberId") Long memberId
    );


}
