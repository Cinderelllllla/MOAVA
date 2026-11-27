
package com.moava.member.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SocialApiTest implements CommandLineRunner {

    @Value("${moava.oauth.kakao.client-id:}")
    private String kakaoId;

    @Value("${moava.oauth.kakao.client-secret:}")
    private String kakaoSecret;

    @Value("${moava.oauth.naver.client-id:}")
    private String naverId;

    @Value("${moava.oauth.naver.client-secret:}")
    private String naverSecret;

    @Value("${moava.oauth.google.client-id:}")
    private String googleId;

    @Value("${moava.oauth.google.client-secret:}")
    private String googleSecret;

    @Override
    public void run(String... args) {

        System.out.println("==========================");
        System.out.println("소셜 로그인 API 설정 확인");
        System.out.println("==========================");

        check("카카오 Client ID", kakaoId);
        check("카카오 Client Secret", kakaoSecret);

        check("네이버 Client ID", naverId);
        check("네이버 Client Secret", naverSecret);

        check("구글 Client ID", googleId);
        check("구글 Client Secret", googleSecret);

        System.out.println("==========================");
    }

    private void check(String name, String value) {

        if (value != null
                && !value.isBlank()
                && !value.contains("★")
                && !value.contains("${")) {

            System.out.println(name + " : 설정값 로드 성공");

        } else {

            System.out.println(name + " : 설정값 로드 실패");
        }
    }
}
