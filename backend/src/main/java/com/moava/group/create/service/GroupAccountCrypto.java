
package com.moava.group.create.service;

public interface GroupAccountCrypto {

    // 예금주 및 계좌번호 암호화
    String encrypt(String plainText);

    // 계좌번호 중복 식별용 키 기반 해시
    String hashAccountNumber(String accountNumber);

    // 현재 암호화 키 버전
    int keyVersion();
}
//팀 공통 암호화 구현이 아직 없으므로, 
// 암호화 알고리즘이나 비밀키를 임의로 정하지 않고 인터페이스만 준비