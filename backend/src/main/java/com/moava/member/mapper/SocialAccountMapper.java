package com.moava.member.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper 
public interface SocialAccountMapper {

    //소셜 고유 Id로 회원 조화
    Long findMemberId(@Param("provider") String provider, @Param("providerUserId") String providerUserId);

    //소셜 계정 등록
    int insertSocialAccount(@Param("memberId") Long memberId, @Param("provider") String provider, @Param("providerUserId") String providerUserId, @Param("providerEmail") String providerEmail);

    //마지막 로그인 시간 수정
    int updateLastLogin(@Param("memberId") Long memberId, @Param("provider") String provider);

    //연결된 소셜 계정 조회
    List<Map<String, Object>> findSocialAccounts(@Param("memberId") Long memberId);

    //연결된 소셜 계정 개수
    int countSocialAccounts(@Param("memberId") Long memberId);

    //소셜 계정 연결 해제
    int deleteSocialAccount(@Param("memberId") Long memberId, @Param("provider") String provider);

    //개인정보 파기 시 전체 연결 정보 삭제
    int deleteAllSocialAccounts(@Param("memberId") Long memberId);
    
}