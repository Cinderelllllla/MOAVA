package com.moava.crypto.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moava.crypto.dto.CryptoKeyRecord;

@Mapper 
public interface CryptoKeyMapper {
    CryptoKeyRecord findActiveKey();

    CryptoKeyRecord findByVersion(@Param("keyVer") int keyVer);

    Integer findMaxKeyVersion();

    List<CryptoKeyRecord> findAll();

    int insertCryptoKey(CryptoKeyRecord record);
}
