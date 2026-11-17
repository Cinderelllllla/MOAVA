package com.moava.member.service;

import org.springframework.stereotype.Service;

import com.moava.crypto.service.CryptoKeyService;

import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class PhoneCryptoService {
    private final CryptoKeyService cryptoKeyService;
    
    public int currentKeyVersion() {
        return cryptoKeyService.getCurrentKeyVersion();
    }

    public String encrypt(String phone, int keyVersion) {
        return cryptoKeyService.encrypt(phone, keyVersion);
    }

    public String decrypt(String encryptedPhone, int keyVersion) {
        return cryptoKeyService.decrypt(encryptedPhone, keyVersion);
    }
}
