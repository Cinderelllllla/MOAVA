
package com.moava.group.create.service;

import java.security.SecureRandom;

public final class GroupJoinCodeGenerator {

    // 보안용 난수 생성기 (SecureRandom 예측하기 어려운 난수 생성)
    private static final SecureRandom RANDOM = new SecureRandom();

    // 불필요 객체 생성 방지
    private GroupJoinCodeGenerator() {
    }

    // 6자리 숫자 인증코드 생성
    public static String generate() {
        int number = RANDOM.nextInt(1_000_000); 
        // nextInt(1_000_000)  	0~999999 사이의 정수 생성

        // 앞에 0을 채워 항상 6자리로 변환
        return String.format("%06d", number);
    }
}
