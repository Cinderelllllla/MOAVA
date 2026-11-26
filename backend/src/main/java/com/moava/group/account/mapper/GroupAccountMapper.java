package com.moava.group.account.mapper;

import com.moava.group.account.dto.GroupAccountResponse;
import com.moava.group.account.dto.GroupAccountVerificationTarget;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupAccountMapper {

    // F19 - 활성 회원 여부 확인
    int countActiveMember(
            @Param("memberId") Long memberId
    );

    // F19 - 모임 존재 여부 확인
    int countGroup(
            @Param("groupId") Long groupId
    );

    // F19 - 현재 모임장 여부 확인
    int countGroupOwner(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    // F19 - 인증 완료 알림에 사용할 모임명 조회
    String findGroupTitle(
            @Param("groupId") Long groupId
    );

    // F19 - 현재 모임 계좌 조회
    GroupAccountResponse findCurrentAccount(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    // F19 - 서버 내부 계좌 소유 인증 대상 조회
    GroupAccountVerificationTarget findVerificationTarget(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    // F19 - 인증 결과 반영 전 모임 행 잠금
    Long lockGroup(
            @Param("groupId") Long groupId
    );

    // F19 - CODEF 자동 인증 완료 처리
    int updateAccountVerified(
            @Param("gyeAccountId") Long gyeAccountId,
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("accountNoEnc") String accountNoEnc,
            @Param("bankName") String bankName,
            @Param("keyVer") Integer keyVer
    );

    // F19 - 계좌 인증 완료 감사 로그
    int insertAccountVerifyAudit(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("description") String description
    );
}
