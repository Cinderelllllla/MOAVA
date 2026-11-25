package com.moava.crypto.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.moava.crypto.config.CryptoProperties;
import com.moava.crypto.dto.CryptoKeyRecord;
import com.moava.crypto.mapper.CryptoKeyMapper;

@Component 
public class CryptoKeyInitializer implements ApplicationRunner{

    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private static final int DEK_LENGTH = 32;
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final CryptoKeyMapper mapper;
    private final CryptoKeyService cryptoKeyService;
    private final byte[] kek;

    //애플리케이션 실행시 KEK 준비
    public CryptoKeyInitializer(CryptoKeyMapper mapper, CryptoProperties properties, CryptoKeyService cryptoKeyService){
        this.mapper = mapper;
        this.cryptoKeyService = cryptoKeyService;
        String kekValue = properties.getKekBase64();

        // System.out.println("========== KEK 설정 진단 ==========");
        // System.out.println("KEK 값 존재 여부: " + (kekValue != null));
        // System.out.println("KEK 길이: " + (kekValue == null ? 0 : kekValue.length()));
        // System.out.println("환경변수 존재 여부: "
        //         + (System.getenv("MOAVA_CRYPTO_KEK_BASE64") != null));

        // if (kekValue != null) {
        //     for (int i = 0; i < kekValue.length(); i++) {
        //         char c = kekValue.charAt(i);

        //         boolean valid = (c >= 'A' && c <= 'Z')
        //                 || (c >= 'a' && c <= 'z')
        //                 || (c >= '0' && c <= '9')
        //                 || c == '+'
        //                 || c == '/'
        //                 || c == '=';

        //         if (!valid) {
        //             System.out.println(
        //                     "잘못된 문자 발견 - 위치: " + i
        //                     + ", 문자 코드: " + (int) c
        //             );
        //         }
        //     }
        // }

        // System.out.println("===================================");

        if (kekValue == null || kekValue.isBlank()) {
            throw new IllegalArgumentException("KEK 설정값이 비어 있습니다.");
        }
        try{
            this.kek = Base64.getDecoder().decode(properties.getKekBase64());
        }catch(IllegalArgumentException e){
            throw new IllegalArgumentException("KEK Bases64 설정 오류", e);
        }

        if(this.kek.length != 32){
            throw new IllegalArgumentException("KEK는 32바이트여야 합니다.");
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception{
        CryptoKeyRecord activeKey = mapper.findActiveKey();

        if(activeKey != null){
            cryptoKeyService.validateActiveKey();
            return;
        }
        Integer maxVersion = mapper.findMaxKeyVersion();
        //DB의 가장 높은 KEY_VER에 1을 더함
        int nextVersion = (maxVersion == null ? 0 : maxVersion) + 1;

        if(nextVersion > 99){
            throw new IllegalStateException("키 버전은 99를 초과할 수 없습니다.");
        }

        //실제 개인정보를 암호화할 DEK 생성(생성되는 DEK는 매번 달라짐)
        byte[] dek = new byte[DEK_LENGTH];
        new SecureRandom().nextBytes(dek);

        //생성한 DEK를 KEK로 암호화
        String encryptedDek = encryptDek(dek);

        //DEK를 SHA-256으로 해시 처리
        String keyHash = sha256(dek);

        //DB 저장
        CryptoKeyRecord record = new CryptoKeyRecord();

        record.setKeyVer(nextVersion);
        record.setAlgorithm(ALGORITHM);
        record.setEncryptedDek(encryptedDek);
        record.setKeyHash(keyHash);
        record.setStatus("ACTIVE");

        mapper.insertCryptoKey(record);
    }

    private String encryptDek(byte[] dek) throws Exception{
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);

        cipher.init(
            Cipher.ENCRYPT_MODE,
            new SecretKeySpec(kek, "AES"),
            new GCMParameterSpec(TAG_LENGTH, iv)
        );

        byte[] encrypted = cipher.doFinal(dek);

        // IV + 암호문 + GCM 인증 태그
        byte[] combined = new byte[iv.length + encrypted.length];

        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(
            encrypted, 0,
            combined, iv.length,
            encrypted.length
        );

        return Base64.getEncoder().encodeToString(combined);
    }

    private String sha256(byte[] data) throws Exception {

        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(data);

        StringBuilder result = new StringBuilder();

        for (byte b : hash) {
            result.append(String.format("%02x", b & 0xff));
        }

        return result.toString();
    }
}
