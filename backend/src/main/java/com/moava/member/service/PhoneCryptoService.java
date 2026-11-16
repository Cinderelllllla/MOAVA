package com.moava.member.service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.moava.service.AesGcmCryptoService;

@Service 
public class PhoneCryptoService {
    private static final int KEY_VERSION = 1;

    private final SecretKey key;
    private final AesGcmCryptoService cryptoService;
    
    public PhoneCryptoService(@Value("${moava.cryto.aes-key-base64}") String aesKeyBase64, AesGcmCryptoService cryptoService) {
        byte[] keyBytes;
        try{
            keyBytes = java.util.Base64.getDecoder().decode(aesKeyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("전화번호 AES 키는 Base64 값이여야 합니다.", e);
        }
        if(keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("전화번호 AES 키는 16, 24, 32 바이트 길이여야 합니다.");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
        this.cryptoService = cryptoService;
    }

    public int currentKeyVersion() {
        return KEY_VERSION;
    }

    public String encrypt(String plainText) {
        return cryptoService.encrypt(plainText, key);
    }

    public String decrypt(String encoded, int keyVersion) {
        if(keyVersion != KEY_VERSION) {
            throw new IllegalStateException("전화번호 AES 키 버전이 일치하지 않습니다." + keyVersion);
        }
        return cryptoService.decrypt(encoded, key);
    }
}
