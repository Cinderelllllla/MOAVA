package com.moava.member.service;

import com.moava.member.dto.AuthResponse;
import com.moava.member.dto.MemberResponse;
import com.moava.member.dto.MemberUpdateRequest;
import com.moava.member.dto.MemberView;
import com.moava.member.dto.SignupRequest;
import com.moava.member.mapper.MemberMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 회원 가입/수정 시 regionDong(읍/면/동) 저장·조회·검증 단위 테스트 */
@ExtendWith(MockitoExtension.class)
class MemberServiceRegionDongTest {

    @Mock
    MemberMapper memberMapper;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtTokenProvider jwtTokenProvider;
    @Mock
    PhoneCryptoService phoneCryptoService;

    MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberMapper, passwordEncoder, jwtTokenProvider, phoneCryptoService);
    }

    @Test
    void 회원가입_시_regionDong을_저장하고_MemberView로_반환한다() {
        when(memberMapper.existsByEmail("hong@example.com")).thenReturn(0);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(phoneCryptoService.currentKeyVersion()).thenReturn(1);
        when(phoneCryptoService.encrypt("01012345678", 1)).thenReturn("enc-phone");
        when(memberMapper.insertUser("홍길동", "hong@example.com", "hash", "enc-phone", 1, "서울특별시", "마포구", "합정동"))
                .thenReturn(1);
        when(memberMapper.findByEmail("hong@example.com"))
                .thenReturn(new MemberResponse(1L, "홍길동", "hong@example.com", "enc-phone", "서울특별시", "마포구", "합정동", "ACTIVE", "N", 1));
        when(phoneCryptoService.decrypt("enc-phone", 1)).thenReturn("01012345678");
        when(jwtTokenProvider.createToken(1L)).thenReturn("jwt-token");

        SignupRequest req = new SignupRequest(
                "홍길동", "hong@example.com", "password123", "010-1234-5678",
                "서울특별시", "마포구", " 합정동 ", true, true
        );

        AuthResponse res = memberService.signup(req);

        assertEquals("합정동", res.user().regionDong());
        verify(memberMapper).insertUser("홍길동", "hong@example.com", "hash", "enc-phone", 1, "서울특별시", "마포구", "합정동");
    }

    @Test
    void 회원가입_시_regionDong이_null이거나_공백이면_null로_저장한다() {
        when(memberMapper.existsByEmail("hong@example.com")).thenReturn(0);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(phoneCryptoService.currentKeyVersion()).thenReturn(1);
        when(phoneCryptoService.encrypt("01012345678", 1)).thenReturn("enc-phone");
        when(memberMapper.insertUser("홍길동", "hong@example.com", "hash", "enc-phone", 1, "서울특별시", "마포구", null))
                .thenReturn(1);
        when(memberMapper.findByEmail("hong@example.com"))
                .thenReturn(new MemberResponse(1L, "홍길동", "hong@example.com", "enc-phone", "서울특별시", "마포구", null, "ACTIVE", "N", 1));
        when(phoneCryptoService.decrypt("enc-phone", 1)).thenReturn("01012345678");
        when(jwtTokenProvider.createToken(1L)).thenReturn("jwt-token");

        SignupRequest req = new SignupRequest(
                "홍길동", "hong@example.com", "password123", "010-1234-5678",
                "서울특별시", "마포구", "   ", true, true
        );

        AuthResponse res = memberService.signup(req);

        assertNull(res.user().regionDong());
        verify(memberMapper).insertUser("홍길동", "hong@example.com", "hash", "enc-phone", 1, "서울특별시", "마포구", null);
    }

    @Test
    void 회원정보_수정_시_regionDong을_갱신한다() {
        MemberResponse before = new MemberResponse(1L, "홍길동", "hong@example.com", "enc-old", "서울특별시", "마포구", null, "ACTIVE", "N", 1);
        MemberResponse after = new MemberResponse(1L, "홍길동", "hong@example.com", "enc-new", "경기도", "고양시 일산동구", "장항동", "ACTIVE", "N", 1);
        when(memberMapper.findById(1L)).thenReturn(before, after);
        when(memberMapper.existsByEmailExceptMember("hong@example.com", 1L)).thenReturn(0);
        when(phoneCryptoService.currentKeyVersion()).thenReturn(1);
        when(phoneCryptoService.encrypt("01012345678", 1)).thenReturn("enc-new");
        when(memberMapper.updateProfile(1L, "홍길동", "hong@example.com", "enc-new", 1, "경기도", "고양시 일산동구", "장항동"))
                .thenReturn(1);

        MemberView view = memberService.updateProfile(
                1L,
                new MemberUpdateRequest("홍길동", "hong@example.com", "01012345678", "경기도", "고양시 일산동구", "장항동")
        );

        assertEquals("장항동", view.regionDong());
        verify(memberMapper).updateProfile(1L, "홍길동", "hong@example.com", "enc-new", 1, "경기도", "고양시 일산동구", "장항동");
    }

    @Test
    void regionDong이_40바이트를_초과하면_400_예외를_던진다() {
        // UTF-8 기준 한글 14자 = 42바이트 > 40바이트
        SignupRequest req = new SignupRequest(
                "홍길동", "hong@example.com", "password123", "01012345678",
                "서울특별시", "마포구", "가나다라마바사아자차카타파하", true, true
        );

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> memberService.signup(req));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
