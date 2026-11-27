package com.moava.member.dto;

import java.util.List;

public record SocialSignupRequest(String signupToken, String name, String email, String phone, String regionSido, String regionSigungu, List<Long> agreedTermsIds) {
    
}
