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
    int insertUser(
        @Param("memberId") Long memberId, 
        @Param("name") String name, 
        @Param("email") String email, 
        @Param("passwordHash") String passwordHash, 
        @Param("phoneEnc") String phoneEnc, 
        @Param("keyVer") int keyVer, 
        @Param("regionSido") String regionSido, 
        @Param("regionSigungu") String regionSigungu);

}
