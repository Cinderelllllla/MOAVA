package com.moava.member.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moava.member.dto.MemberResponse;

@Mapper 
public interface MemberMapper {
    MemberResponse findByEmail(@Param("email") String email);
    MemberResponse findById(Long memberId);
    String findPasswordHashByEmail(@Param ("email") String email);
    int existsByEmail(@Param("email") String email);
    int existsByEmailExceptMember(@Param("email") String email, @Param("memberId") Long memberId);
    int insertUser(
        @Param("name") String name, 
        @Param("email") String email, 
        @Param("passwordHash") String passwordHash, 
        @Param("phoneEnc") String phoneEnc, 
        @Param("keyVer") int keyVer, 
        @Param("regionSido") String regionSido, 
        @Param("regionSigungu") String regionSigungu);
    
    String findPasswordHashById(@Param("memberId") Long memberId);
    int updateProfile(
        @Param("memberId") Long memberId,
        @Param("name") String name,
        @Param("email") String email,
        @Param("phoneEnc") String phoneEnc,
        @Param("keyVer") int keyVer,
        @Param("regionSido") String regionSido,
        @Param("regionSigungu") String regionSigungu);
    int updatePassword(@Param("memberId") Long memberId, @Param("passwordHash") String passwordHash);
    int withdraw(@Param("memberId") Long memberId, @Param("passwordHash") String passwordHash);

    //운영 중인 모임의 ACTIVE 구성원인지 확인
    int countActiveGroupMemberships(@Param("memberId") Long memberId);

    //운영 중인 모임의 모임장인지 확인
    int countOwnedActiveGroups(@Param("memberId") Long memberId);
}
