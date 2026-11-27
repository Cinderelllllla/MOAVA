package com.moava.openbanking.codef;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CodefAccountMapper {

    /** 로그인 회원의 활성 실명 조회 (MEMBER 테이블 읽기 전용) */
    String findActiveMemberName(@Param("memberId") Long memberId);

    /** 회원의 특정 은행 CODEF 연결 정보 조회 */
    CodefDto.ConnectionRecord findConnection(
            @Param("memberId") Long memberId,
            @Param("organization") String organization
    );

    /** 회원의 전체 CODEF 연결/인증 목록 조회 */
    List<CodefDto.ConnectionRecord> findConnectionsByMember(@Param("memberId") Long memberId);

    /** connectedId 저장 또는 갱신 (MERGE) */
    int upsertConnection(CodefDto.ConnectionRecord record);

    /** 실계좌인증 완료 상태 반영 */
    int markAccountVerified(
            @Param("memberId") Long memberId,
            @Param("organization") String organization,
            @Param("verifiedAccountEnc") String verifiedAccountEnc,
            @Param("verifiedAccountLast4") String verifiedAccountLast4,
            @Param("verifiedHolderName") String verifiedHolderName,
            @Param("keyVer") int keyVer,
            @Param("verifiedAt") LocalDateTime verifiedAt
    );

    /** 키 로테이션 시 connectedId 재암호화 반영 */
    int updateConnectedIdEncryption(
            @Param("memberId") Long memberId,
            @Param("organization") String organization,
            @Param("connectedIdEnc") String connectedIdEnc,
            @Param("keyVer") int keyVer,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    /** 특정 은행 연결 삭제 */
    int deleteConnection(
            @Param("memberId") Long memberId,
            @Param("organization") String organization
    );
}
