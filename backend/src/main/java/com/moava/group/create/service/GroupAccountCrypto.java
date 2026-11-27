
package com.moava.group.create.service;

public interface GroupAccountCrypto {

    // 예금주 및 계좌번호 암호화
    String encrypt(String plainText);

    // 계좌번호 중복 식별용 키 기반 해시
    String hashAccountNumber(String accountNumber);

    // 현재 암호화 키 버전
    int keyVersion();
    
    // 복호화 기능 추가
    String decrypt(String encryptedText);

    // 키 버전 지정 암호화 (기본 구현은 현재 키 버전 사용)
    default String encrypt(String plainText, int keyVersion) {
        return encrypt(plainText);
    }

    // 키 버전 지정 복호화 (기본 구현은 기본 복호화 위임)
    default String decrypt(String encryptedText, int keyVersion) {
        return decrypt(encryptedText);
    }
}