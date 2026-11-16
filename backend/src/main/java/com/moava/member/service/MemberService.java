package com.moava.member.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.moava.member.dto.AuthResponse;
import com.moava.member.dto.LoginRequest;
import com.moava.member.dto.MemberResponse;
import com.moava.member.dto.MemberView;
import com.moava.member.dto.SignupRequest;
import com.moava.member.mapper.MemberMapper;

@Service 
public class MemberService {
    
    private static final Pattern  EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PHONE = Pattern.compile("^01([0|1|6|7|8|9]?)-?([0-9]{3,4})-?([0-9]{4})$");
    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PhoneCryptoService phoneCryptoService;

    public MemberService(MemberMapper memberMapper, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, PhoneCryptoService phoneCryptoService) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.phoneCryptoService = phoneCryptoService;
    }

    @Transactional 
    public AuthResponse signup(SignupRequest request) {
        if(request == null) throw bad("회원가입 정보를 입력해주세요.");

        String name = clean(request.name());
        String email = clean(request.email());
        String phone = clean(request.phone()).replace("-", "");
        String sido = clean(request.regionSido());
        String sigungu = clean(request.regionSigungu());
        String password = request.password() == null ? "" : request.password();
        if (name.length() < 2 || name.length() > 50) {
            throw bad("이름은 2~50자 사이여야 합니다.");
        }
        if (!EMAIL.matcher(email).matches() || email.length() > 100 || email.isBlank()) {
            throw bad("올바른 이메일 주소를 입력해주세요.");
        }
        if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw bad("비밀번호를 올바르게 입력해주세요.");
        }
        if (!PHONE.matcher(phone).matches()) {
            throw bad("올바른 전화번호를 입력해주세요.");
        }
        if (sido.isBlank() || sido.length() > 30 || sigungu.isBlank() || sigungu.length() > 30) {
            throw bad("활동 지역을 올바르게 입력해주세요.");
        }
        if(!request.termsAgreed() || !request.privacyAgreed()) {
            throw bad("약관 및 개인정보 처리방침에 동의해야 회원가입이 가능합니다.");
        }
        if(memberMapper.existsByEmail(email) > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 존재하는 이메일입니다.");
        
        String passwordHash = passwordEncoder.encode(password);
        String phoneEnc = phoneCryptoService.encrypt(phone);
        int keyVer = phoneCryptoService.currentKeyVersion();

        memberMapper.insertUser(name, email, passwordHash, phoneEnc, keyVer, sido, sigungu);
        MemberResponse member = memberMapper.findByEmail(email);
        if(member == null) throw new IllegalStateException("회원가입 후 회원 정보를 조회할 수 없습니다.");
        return response(member);
    }

    public AuthResponse login(LoginRequest request) {
        if(request == null || request.email() == null || request.password() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"이메일과 비밀번호를 입력해주세요.");
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String hash = memberMapper.findPasswordHashByEmail(email);
        MemberResponse member = memberMapper.findByEmail(email);
        if(hash == null || member == null || !passwordEncoder.matches(request.password(), hash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다.");      
        return response(member);
    }

    
    private AuthResponse response(MemberResponse member) {
        String phone = member.phoneEnc() == null ? null : phoneCryptoService.decrypt(member.phoneEnc(), member.keyVer() == null ? 1 : member.keyVer());
        MemberView view = toView(member, phone);
        return new AuthResponse(jwtTokenProvider.createToken(member.email()), "Bearer", view);
    }

    private MemberView toView(MemberResponse member, String phone) {
        return new MemberView(member.id(), member.name(), member.email(), phone, member.regionSido(), member.regionSigungu(), member.status());
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private String clean(String input) {
        return input == null ? null : input.trim();
    }

        public MemberView getActiveMember(String email) {
        MemberResponse user = memberMapper.findAuthStateByEmail(email);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 정보를 찾을 수 없습니다.");
        ensureCanUse(user);
        return toView(user, null);
    }

    private void ensureCanUse(MemberResponse user) {
        if (!"ACTIVE".equals(user.status()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "탈퇴했거나 이용할 수 없는 회원입니다.");
        if (!"N".equals(user.bannedYn()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이용 정지된 회원입니다.");
    }
}
