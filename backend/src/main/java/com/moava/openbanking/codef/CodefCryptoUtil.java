package com.moava.openbanking.codef;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * CODEF API 요청 비밀번호·계좌비밀번호 RSA 공개키 암호화 유틸 (RSA/ECB/PKCS1Padding + Base64)
 */
public final class CodefCryptoUtil {

    private CodefCryptoUtil() {
    }

    public static String encryptRsa(String plainText, String rawPublicKey) {
        if (plainText == null || plainText.isEmpty()) {
            throw CodefException.badRequest("CODEF_EMPTY_SECRET", "암호화할 비밀번호 값이 비어 있습니다.");
        }
        if (rawPublicKey == null || rawPublicKey.isBlank()) {
            throw CodefException.serviceUnavailable("CODEF_PUBLIC_KEY_MISSING", "CODEF 공개키가 설정되지 않았습니다.");
        }
        try {
            String normalized = rawPublicKey
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));

            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (CodefException e) {
            throw e;
        } catch (Exception e) {
            throw CodefException.serviceUnavailable("CODEF_RSA_FAILED", "CODEF 공개키 RSA 암호화에 실패했습니다.");
        }
    }
}
