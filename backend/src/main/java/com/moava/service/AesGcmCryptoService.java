package com.moava.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import org.springframework.stereotype.Service;

@Service 
public class AesGcmCryptoService {
    
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = 16;

    private final SecureRandom random = new SecureRandom();


    //암호화
    public String encrypt(String plaintext, SecretKey key) {
        if(plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("암호화할 값이 비어있습니다.");
        }
        try{
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("암호화 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            throw new RuntimeException("암호화 중 오류가 발생했습니다.", e);
        } 
    }

    //복호화
    public String decrypt(String encoded, SecretKey key){
        if(encoded == null || encoded.isBlank()){
            return null;
        }
        try{
            byte[] all = Base64.getDecoder().decode(encoded);

            if(all.length < IV_LENGTH + TAG_LENGTH_BYTES) {
                throw new IllegalArgumentException("암호화된 데이터가 올바르지 않습니다.");
            }
            byte[] iv = new byte[IV_LENGTH];

            byte[] encrypted = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            System.arraycopy(all, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("AES 복호화에 실패했습니다. 키 설정을 확인하세요.", e);
        } catch (Exception e) {
            throw new RuntimeException("복호화 중 오류가 발생했습니다.", e);
        }
    }

    public String hashAccountNumber(String accountNumber, SecretKey hmacKey) {
        if(accountNumber == null || accountNumber.isBlank()) {
            throw new IllegalArgumentException("해시할 계좌번호가 비어있습니다.");
        }
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            byte[] hash = mac.doFinal(accountNumber.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("계좌번호 해시 생성 중 오류가 발생했습니다.", e);
        }
    }
}
