package com.moava.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface AdminAuthMapper {
    Map<String, Object> findByEmail(@Param("email") String email);
}
