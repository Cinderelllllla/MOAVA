package com.moava.admin.service;

import com.moava.admin.mapper.AdminAuthMapper;
import com.moava.member.service.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Locale;

@Service
public class AdminAuthService {
    private final AdminAuthMapper adminAuthMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AdminAuthService(
            AdminAuthMapper adminAuthMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.adminAuthMapper = adminAuthMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public AdminLoginResponse login(String email, String password) {
        if (email == null || password == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이메일과 비밀번호를 입력해 주세요.");
        }
        Map<String, Object> admin = adminAuthMapper.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        if (admin == null || !"ACTIVE".equals(admin.get("STATUS"))
                || !passwordEncoder.matches(password, String.valueOf(admin.get("PASSWORD_HASH")))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        Long adminId = ((Number) admin.get("ADMIN_ID")).longValue();
        return new AdminLoginResponse(
                jwtTokenProvider.createToken(adminId, "ADMIN"),
                "Bearer",
                adminId,
                String.valueOf(admin.get("NAME")),
                String.valueOf(admin.get("EMAIL"))
        );
    }

    public record AdminLoginResponse(
            String accessToken,
            String tokenType,
            Long adminId,
            String name,
            String email) {
    }
}
