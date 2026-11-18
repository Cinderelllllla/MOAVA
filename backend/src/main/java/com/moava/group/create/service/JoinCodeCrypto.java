
package com.moava.group.create.service;

public interface JoinCodeCrypto {

    // 6자리 인증코드 암호화
    String encrypt(String plainCode, int keyVersion);

    // 저장된 인증코드 복호화
    String decrypt(String encryptedCode, int keyVersion);
}
