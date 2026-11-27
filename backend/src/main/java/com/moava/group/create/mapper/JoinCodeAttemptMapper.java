package com.moava.group.create.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface JoinCodeAttemptMapper {

    // 회원별 인증 시도 기록 조회
    Map<String, Object> findAttempt(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    // 최초 인증 시도 기록 생성
    int insertAttempt(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("codeVersion") Integer codeVersion
    );

    // 회원별 인증 실패 횟수 증가 (최대 3회)
    int increaseFailCount(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    // 인증 성공 처리
    int markVerified(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    // 인증코드 재발급 시 실패 횟수 및 인증 상태 초기화
    int resetForNewVersion(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("codeVersion") Integer codeVersion
    );

    // 인증 시도 기록 생성 전에 모임 행 잠금
    Long lockGroupForAttempt(
            @Param("groupId") Long groupId
    );

    // 현재 인증코드 버전 조회
    Integer findCurrentCodeVersion(
            @Param("groupId") Long groupId
    );

    // 암호화된 인증코드 조회
    Map<String, Object> findEncryptedJoinCode(
            @Param("groupId") Long groupId
    );
}
