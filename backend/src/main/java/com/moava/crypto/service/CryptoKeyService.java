package com.moava.crypto.service;

import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.moava.crypto.config.CryptoProperties;
import com.moava.crypto.dto.CryptoKeyRecord;
import com.moava.crypto.mapper.CryptoKeyMapper;
import com.moava.service.AesGcmCryptoService;

//암호화 : encrypt, 복호화 : decrypt만 사용
@Service 
public class CryptoKeyService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final int DEK_LENGTH = 32;

    private final CryptoKeyMapper mapper;
    private final AesGcmCryptoService aesService;
    private final SecretKey kek;

    //CryptoKetInitializer에도 KEK를 사용하여 동일한 KEK를 사용해야함
    public CryptoKeyService(CryptoKeyMapper mapper, AesGcmCryptoService aesService, CryptoProperties properties){
        this.mapper = mapper;
        this.aesService = aesService;

        try{
            byte[] kekBytes = Base64.getDecoder()
                    .decode(properties.getKekBase64());

            if (kekBytes.length != 32) {
                throw new IllegalArgumentException(
                    "KEK는 32바이트 AES-256 키여야 합니다."
                );
            }

            this.kek = new SecretKeySpec(kekBytes, "AES");

        }catch(IllegalArgumentException e){
            throw new IllegalArgumentException(
                "KEK 환경변수 설정을 확인하세요.", e
            );

        }
        
    }

    //현재 사용할 수 있는 ACTIVE키의 버전을 조회
    public int getCurrentKeyVersion(){
        CryptoKeyRecord record = mapper.findActiveKey();
        if (record == null){
            throw new IllegalStateException("활성 암호화 키가 없습니다.");
        }
        return record.getKeyVer();
    }

    //실제 데이터를 암호화하는 과정
    public String encrypt(String plainText, int keyVersion){
        CryptoKeyRecord record = mapper.findByVersion(keyVersion);

        if(record == null || !"ACTIVE".equals(record.getStatus())){
            throw new IllegalStateException("암호화에 사용할 수 있는 키가 아닙니다.");
        }
        SecretKey dek = decryptDek(record);
        return aesService.encrypt(plainText, dek);
    }

    //복호화
    public String decrypt(String encryptedText, int keyVersion){
        CryptoKeyRecord record = mapper.findByVersion(keyVersion);
        if(record == null){
            throw new IllegalStateException("암호화 키를 찾을 수 없습니다. KEY_VER=" + keyVersion);
        }
        if(!"ACTIVE".equals(record.getStatus()) && !"RETIRED".equals(record.getStatus())){
            throw new IllegalStateException("유효하지 않은 키 상태입니다.");
        }
        SecretKey dek = decryptDek(record);
        
        return aesService.decrypt(encryptedText, dek);

    }

    //복호화된 DEK의 해시와 DB에 저장된 해시를 비교
    private SecretKey decryptDek(CryptoKeyRecord record){
        try {
            byte[] combined = Base64.getDecoder()
                    .decode(record.getEncryptedDek());

            if (combined.length <= IV_LENGTH + 16) {
                throw new IllegalStateException("ENCRYPTED_DEK 형식 오류");
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];

            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(
                combined, IV_LENGTH,
                encrypted, 0,
                encrypted.length
            );

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            cipher.init(
                Cipher.DECRYPT_MODE,
                kek,
                new GCMParameterSpec(TAG_LENGTH, iv)
            );

            byte[] dek = cipher.doFinal(encrypted);

            if (dek.length != DEK_LENGTH) {
                throw new IllegalStateException("DEK 길이가 올바르지 않습니다.");
            }

            byte[] actualHash = MessageDigest.getInstance("SHA-256")
                    .digest(dek);

            byte[] expectedHash = hexToBytes(record.getKeyHash());

            if (!MessageDigest.isEqual(actualHash, expectedHash)) {
                throw new IllegalStateException("DEK 무결성 검증 실패");
            }

            return new SecretKeySpec(dek, "AES");

        } catch (Exception e) {
            throw new IllegalStateException("DEK 복호화 실패", e);
        }
    }

    private byte[] hexToBytes(String hex) {

        if (hex == null || hex.length() != 64) {
            throw new IllegalStateException("KEY_HASH 형식 오류");
        }

        byte[] bytes = new byte[32];

        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(
                hex.substring(i * 2, i * 2 + 2),
                16
            );
        }

        return bytes;
    }
    
}
