package com.moava.member.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .exceptionHandling(e -> e.authenticationEntryPoint((request, response, authException) ->
                response.sendError(401)))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(
                    "/",
                    "/error",
                    "/index.html",
                    "/assets/**",
                    "/favicon.ico",
                    //test용 API
                    "/api/test",
                    // F34 채팅 웹소켓 연결. 브라우저는 여기에 토큰 헤더를 못 붙이므로
                    // 로그인 확인은 com.moava.chat.config.ChatChannelInterceptor 가 STOMP CONNECT 때 한다.
                    "/ws/**"
                ).permitAll()
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
                .requestMatchers(
                    "/api/admin/**"
                ).hasRole("ADMIN")
                .requestMatchers(
                    "/api/member/**"
                ).hasAnyRole("ADMIN", "MEMBER")
                .requestMatchers(
                    "/api/auth/me",
                    "/api/auth/password"
                ).hasRole("MEMBER")
                .requestMatchers("/api/**")
                .authenticated()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(java.util.List.of("*"));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    
}
