package com.moava.member.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.moava.member.service.JwtAuthenticationFilter;

import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s ->
                s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .exceptionHandling(e ->
                e.authenticationEntryPoint((request, response, authException) ->
                    response.sendError(401)))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                .requestMatchers(
                    "/",
                    "/error",
                    "/index.html",
                    "/assets/**",
                    "/favicon.ico",

                    // 테스트용 API
                    "/api/test",

                    // F34 채팅 웹소켓 연결
                    // 브라우저는 여기에 토큰 헤더를 못 붙이므로
                    // 로그인 확인은 ChatChannelInterceptor에서
                    // STOMP CONNECT 시 수행한다.
                    "/ws/**"
                ).permitAll()

                // 인증 관련 공개 API
                .requestMatchers(
                    "/api/auth/signup",
                    "/api/auth/login",
                    "/api/auth/admin/login",
                    "/api/auth/check-email",
                    "/api/auth/social/*/authorize",
                    "/api/auth/social/*/callback",
                    "/api/auth/social/exchange",
                    "/api/auth/social/signup-info",
                    "/api/auth/social/signup",
                    "/api/auth/social/terms"
                ).permitAll()

                // 관리자 전용 API
                .requestMatchers(
                    "/api/admin/**",
                    "/api/v1/admin/**"
                ).hasRole("ADMIN")

                // 회원 및 관리자 접근 API
                .requestMatchers(
                    "/api/member/**"
                ).hasAnyRole("ADMIN", "MEMBER")

                // F18 비공개 모임 초대 링크
                // 비로그인 사용자도 GET 조회만 가능
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/invites/*"
                ).permitAll()

                // F29 관리자 면제 처리
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/groups/*/withdrawals/*/consent-targets/*/exemption"
                ).hasRole("ADMIN")

                // F29 동의 대상 조회
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/groups/*/withdrawals/*/consent-targets"
                ).hasAnyRole("ADMIN", "MEMBER")

                // 회원 전용 API
                .requestMatchers(
                    "/api/auth/me",
                    "/api/auth/password",
                    "/api/v1/groups/**",
                    "/api/groups/**"
                ).hasRole("MEMBER")

                // 나머지 API는 인증 필요
                .requestMatchers("/api/**").authenticated()

                .anyRequest().authenticated()
            )
            .addFilterBefore(
                jwtFilter,
                UsernamePasswordAuthenticationFilter.class
            )
            .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
            java.util.List.of("*")
        );

        configuration.setAllowedMethods(
            java.util.List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
            )
        );

        configuration.setAllowedHeaders(
            java.util.List.of("*")
        );

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}