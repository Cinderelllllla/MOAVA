
package com.moava.group.create.service;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class GroupInviteCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    //SecureRandom: 보안용 난수 생성기
    private GroupInviteCodeGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[16];
        //new byte[16]: 16바이트(128비트)의 랜덤값 생성
        RANDOM.nextBytes(bytes);

        return HexFormat.of().formatHex(bytes);
        //HexFormat: 랜덤값을 32자리 16진수 문자열로 변환
    }
}
