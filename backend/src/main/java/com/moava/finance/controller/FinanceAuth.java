package com.moava.finance.controller;

import com.moava.finance.service.FinanceException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/** 금융 분석 컨트롤러들이 함께 쓰는 "로그인한 회원 번호" 꺼내기. */
final class FinanceAuth {

    private FinanceAuth() {
    }

    /**
     * 로그인한 회원 번호. JWT subject 는 회원이면 MEMBER_ID, 관리자면 ADMIN_ID 라서
     * 관리자 토큰으로 남의 금융 정보를 보지 못하게 ROLE_MEMBER 만 허용한다.
     */
    static long memberId(Authentication authentication) {
        boolean member = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MEMBER".equals(a.getAuthority()));
        if (!member) {
            throw new FinanceException(HttpStatus.FORBIDDEN, "MEMBER_ONLY", "회원만 이용할 수 있어요.");
        }
        return Long.parseLong(authentication.getName()); // JwtAuthenticationFilter 가 subject 를 이름으로 넣어 준다
    }
}
