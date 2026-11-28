
package com.moava.group.create.service;

import com.moava.crypto.service.CryptoKeyService;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GroupAccountCryptoImpl implements GroupAccountCrypto {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final CryptoKeyService cryptoKeyService;
    private final SecretKey legacyAesKey;
    private final SecretKeySpec hmacKey;

    @Autowired
    public GroupAccountCryptoImpl(
            CryptoKeyService cryptoKeyService,
            @Value("${moava.crypto.account.key:}") String legacyAesKeyBase64,
            @Value("${moava.crypto.account.hmac-key}") String hmacKeyBase64
    ) {
        if (cryptoKeyService == null) {
            throw new IllegalArgumentException("CryptoKeyService가 필요합니다.");
        }
        byte[] hmacBytes = Base64.getDecoder().decode(hmacKeyBase64);
        if (hmacBytes.length < 32) {
            throw new IllegalArgumentException(
                    "HMAC 키는 32바이트 이상이어야 합니다."
            );
        }

        SecretKey parsedLegacyKey = null;
        if (legacyAesKeyBase64 != null && !legacyAesKeyBase64.isBlank()) {
            byte[] aesBytes = Base64.getDecoder().decode(legacyAesKeyBase64.trim());
            if (aesBytes.length != 32) {
                throw new IllegalArgumentException(
                        "레거시 암호화 키는 32바이트여야 합니다."
                );
            }
            parsedLegacyKey = new SecretKeySpec(aesBytes, "AES");
        }

        this.cryptoKeyService = cryptoKeyService;
        this.legacyAesKey = parsedLegacyKey;
        this.hmacKey = new SecretKeySpec(hmacBytes, "HmacSHA256");
    }

    @Override
    public String encrypt(String plainText) {
        return encrypt(plainText, keyVersion());
    }

    @Override
    public String encrypt(String plainText, int keyVer) {
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException(
                    "암호화할 값이 비어 있습니다."
            );
        }
        return cryptoKeyService.encrypt(plainText, keyVer);
    }

    @Override
    public String decrypt(String encryptedText) {
        return decrypt(encryptedText, keyVersion());
    }

    @Override
    public String decrypt(String encryptedText, int keyVer) {
        if (encryptedText == null || encryptedText.isBlank()) {
            throw new IllegalArgumentException(
                    "복호화할 값이 비어 있습니다."
            );
        }

        try {
            return cryptoKeyService.decrypt(encryptedText, keyVer);
        } catch (RuntimeException primaryFailure) {
            if (legacyAesKey != null) {
                try {
                    return decryptWithLegacyKey(encryptedText);
                } catch (GeneralSecurityException | IllegalArgumentException ignored) {
                    // 폴백 실패 시 아래 공통 예외 반환
                }
            }
            // 내부 예외나 암호문 내용을 외부 응답에 노출하지 않음
            throw new IllegalStateException(
                    "암호문 복호화에 실패했습니다."
            );
        }
    }

    private String decryptWithLegacyKey(String encryptedText) throws GeneralSecurityException {
        byte[] combined = Base64.getDecoder().decode(encryptedText);

        // IV(12바이트) + GCM 인증 태그(16바이트) 이상 필요
        if (combined.length < IV_LENGTH + 16) {
            throw new IllegalArgumentException(
                    "암호문 형식이 올바르지 않습니다."
            );
        }

        byte[] iv = new byte[IV_LENGTH];
        byte[] cipherText = new byte[combined.length - IV_LENGTH];

        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
        System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                legacyAesKey,
                new GCMParameterSpec(TAG_LENGTH, iv)
        );

        byte[] decrypted = cipher.doFinal(cipherText);
        return new String(decrypted, StandardCharsets.UTF_8);
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
        return cryptoKeyService.getCurrentKeyVersion();
    }
}
