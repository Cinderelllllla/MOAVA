
package com.moava.group.create.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GroupAccountCryptoImpl implements GroupAccountCrypto {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKey aesKey;
    private final SecretKeySpec hmacKey;
    private final int keyVersion;

    private final SecureRandom random = new SecureRandom();

    public GroupAccountCryptoImpl(
            @Value("${moava.crypto.account.key}") String aesKeyBase64,
            @Value("${moava.crypto.account.hmac-key}") String hmacKeyBase64,
            @Value("${moava.crypto.account.key-version}") int keyVersion
    ) {
        byte[] aesBytes = Base64.getDecoder().decode(aesKeyBase64);
        byte[] hmacBytes = Base64.getDecoder().decode(hmacKeyBase64);
        //AES-256-GCM + HMAC-SHA256 
        // 계좌번호를 안전하게 저장하고 식별하기 위한 암호화·해시 처리
        if (aesBytes.length != 32 || hmacBytes.length < 32) {
            throw new IllegalArgumentException(
                    "암호화 키와 HMAC 키는 각각 32바이트 이상이어야 합니다."
            );
        }

        if (keyVersion < 1 || keyVersion > 99) {
            throw new IllegalArgumentException(
                    "암호화 키 버전이 올바르지 않습니다."
            );
        }

        this.aesKey = new SecretKeySpec(aesBytes, "AES");
        this.hmacKey = new SecretKeySpec(hmacBytes, "HmacSHA256");
        this.keyVersion = keyVersion;
    }

    @Override
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException(
                    "암호화할 값이 비어 있습니다."
            );
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    aesKey,
                    new GCMParameterSpec(TAG_LENGTH, iv)
            );

            byte[] encrypted = cipher.doFinal(
                    plainText.getBytes(StandardCharsets.UTF_8)
            );

            byte[] result = new byte[iv.length + encrypted.length];

            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(
                    encrypted, 0,
                    result, iv.length,
                    encrypted.length
            );

            return Base64.getEncoder().encodeToString(result);

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "계좌 정보 암호화에 실패했습니다.", e
            );
        }
    }

    @Override
    public String decrypt(String encryptedText) {

        if (encryptedText == null || encryptedText.isBlank()) {
            throw new IllegalArgumentException(
                    "복호화할 값이 비어 있습니다."
            );
        }

        try {
            byte[] combined =
                    Base64.getDecoder().decode(encryptedText);

            // IV(12바이트) + GCM 인증 태그(16바이트) 이상 필요
            if (combined.length < IV_LENGTH + 16) {
                throw new IllegalArgumentException(
                        "암호문 형식이 올바르지 않습니다."
                );
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText =
                    new byte[combined.length - IV_LENGTH];

            System.arraycopy(
                    combined, 0,
                    iv, 0,
                    IV_LENGTH
            );

            System.arraycopy(
                    combined, IV_LENGTH,
                    cipherText, 0,
                    cipherText.length
            );

            Cipher cipher =
                    Cipher.getInstance("AES/GCM/NoPadding");

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    aesKey,
                    new GCMParameterSpec(TAG_LENGTH, iv)
            );

            byte[] decrypted =
                    cipher.doFinal(cipherText);

            return new String(
                    decrypted,
                    StandardCharsets.UTF_8
            );

        } catch (GeneralSecurityException
                 | IllegalArgumentException e) {

            // 내부 예외나 암호문 내용을 외부 응답에 노출하지 않음
            throw new IllegalStateException(
                    "암호문 복호화에 실패했습니다."
            );
        }
    }


    @Override
    public String hashAccountNumber(String accountNumber) {
        if (accountNumber == null
                || !accountNumber.matches("[0-9]{8,30}")) {
            throw new IllegalArgumentException(
                    "계좌번호 형식이 올바르지 않습니다."
            );
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256"); //HMAC-SHA-256: 계좌번호 식별용 해시 생성
            mac.init(hmacKey);

            byte[] hash = mac.doFinal(
                    accountNumber.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "계좌번호 해시 생성에 실패했습니다.", e
            );
        }
    }

    @Override
    public int keyVersion() {
        return keyVersion;
    }
}
