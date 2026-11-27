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
import com.moava.member.dto.MemberUpdateRequest;
import com.moava.member.dto.MemberView;
import com.moava.member.dto.PasswordChangeRequest;
import com.moava.member.dto.SignupRequest;
import com.moava.member.dto.SocialSignupRequest;
import com.moava.member.mapper.MemberMapper;

@Service 
public class MemberService {
    
    private static final Pattern  EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PHONE = Pattern.compile("^01[016789][0-9]{7,8}$");
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

        MemberProfile profile = prepareProfile(request.name(), request.email(), request.phone(), request.regionSido(), request.regionSigungu(), request.regionDong());

        validatePassword((request.password()));
        if(memberMapper.existsByEmail(profile.email()) > 0){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미 존재하는 이메일입니다.");
        }
        if(request.termsAgreed() == null || !request.termsAgreed()) {
            throw bad("이용약관에 동의해주세요.");
        }
        if(request.privacyAgreed() == null || !request.privacyAgreed()) {
            throw bad("개인정보 처리방침에 동의해주세요.");
        }
        
        
        String passwordHash = passwordEncoder.encode(request.password());
        int keyVer = phoneCryptoService.currentKeyVersion();
        String phoneEnc = phoneCryptoService.encrypt(profile.phone(), keyVer);

        int inserted = memberMapper.insertUser(profile.name(), profile.email(), passwordHash, phoneEnc, keyVer, profile.regionSido(), profile.regionSigungu(), profile.regionDong());
        if(inserted != 1){
            throw new IllegalStateException("회원가입 처리에 실패했습니다.");
        }
        MemberResponse member = memberMapper.findByEmail(profile.email());
        if(member == null) throw new IllegalStateException("회원가입 후 회원 정보를 조회할 수 없습니다.");
        ensureCanUse(member);
        return response(member);
    }

    @Transactional
    public Long signupSocial(SocialSignupRequest request, String provider){
        MemberProfile profile = prepareProfile(request.name(), request.email(), request.phone(), request.regionSido(), request.regionSigungu(), request.regionDong());

        if(memberMapper.existsByEmail(profile.email()) > 0){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다. 기존 계정으로 로그인해주세요.");
        }

        int keyVer = phoneCryptoService.currentKeyVersion();

        String phoneEnc = phoneCryptoService.encrypt(profile.phone(), keyVer);

        int result = memberMapper.insertSocialUser(profile.name(), profile.email(), phoneEnc, keyVer, profile.regionSido(), profile.regionSigungu(), profile.regionDong(), provider);

        if(result != 1){
            throw new IllegalStateException("소셜 회원가입에 실패했습니다.");
        }

        MemberResponse member = memberMapper.findByEmail(profile.email());

        if(member == null){
            throw new IllegalStateException("회원 정보 조회에 실패했습니다.");
        }
        return member.id();
    }

    public AuthResponse login(LoginRequest request) {
        if(request == null || request.email() == null || request.email().isBlank() || request.password() == null || request.password().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"이메일과 비밀번호를 입력해주세요.");
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String hash = memberMapper.findPasswordHashByEmail(email);
        MemberResponse member = memberMapper.findByEmail(email);
        if(hash == null || member == null || !passwordEncoder.matches(request.password(), hash) || !"ACTIVE".equals(member.status()) || !"N".equals(member.bannedYn())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다.");      
        return response(member);
    }

    @Transactional
    public AuthResponse loginSocialMember(Long memberId){
        memberMapper.lockMember(memberId);

        MemberResponse member = memberMapper.findById(memberId);
        if(member == null || !"ACTIVE".equals(member.status()) || !"N".equals(member.bannedYn())){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인할 수 없는 회원입니다.");
        }
        return response(member);
    }

    public MemberView getActiveMember(Long memberId) {
        MemberResponse user = memberMapper.findById(memberId);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 정보를 찾을 수 없습니다.");
        ensureCanUse(user);
        String phone = phoneCryptoService.decrypt(user.phoneEnc(), user.keyVer());
        return toView(user, phone);
    }

    public boolean checkEmail(String email){
        String normalizedEmail = clean(email).toLowerCase(Locale.ROOT);

        if(!EMAIL.matcher(normalizedEmail).matches() || normalizedEmail.length() > 100){
            throw bad("올바른 이메일 주소를 입력해주세요.");
        }
        return memberMapper.existsByEmail(normalizedEmail) == 0;
    }

    @Transactional
    public MemberView updateProfile(Long memberId, MemberUpdateRequest request){
        if(request == null){
            throw bad("수정할 회원 정보를 입력해주세요.");
        }

        MemberResponse member = memberMapper.findById(memberId);

        if(member == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
        ensureCanUse(member);

        MemberProfile profile = prepareProfile(request.name(), request.email(), request.phone(), request.regionSido(), request.regionSigungu(), request.regionDong());

        if(memberMapper.existsByEmailExceptMember(profile.email(), memberId) > 0){
            throw bad("이미 사용중인 이메일 입니다.");
        }
        
        int keyVer = phoneCryptoService.currentKeyVersion();

        String phoneEnc = phoneCryptoService.encrypt(profile.phone(), keyVer);

        int update = memberMapper.updateProfile(memberId, profile.name(), profile.email(), phoneEnc, keyVer, profile.regionSido(), profile.regionSigungu(), profile.regionDong());
        if(update != 1){
            throw new IllegalStateException("회원 정보 수정에 실패했습니다.");
        }

        MemberResponse updateMember = memberMapper.findById(memberId);
        return toView(updateMember, profile.phone());
    }

    @Transactional 
    public void changePassword(Long memberId, PasswordChangeRequest request){
        if(request == null || request.currentPassword() == null || request.newPassword() == null){
            throw bad("현재 비밀번호와 새 비밀번호를 입력해주세요.");
        }
        MemberResponse member = memberMapper.findById(memberId);

        if(member == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원정보를 찾을 수 없습니다.");
        }
        ensureCanUse(member);

        String currentHash = memberMapper.findPasswordHashById(memberId);

        if(currentHash == null || !passwordEncoder.matches(request.currentPassword(), currentHash)){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }
        validatePassword(request.newPassword());

        if(passwordEncoder.matches(request.newPassword(), currentHash)){
            throw bad("현재 비밀번호와 다른 비밀번호를 입력해주세요.");
        }

        String newHash = passwordEncoder.encode(request.newPassword());
        memberMapper.updatePassword(memberId, newHash);
    }

    @Transactional 
    public void withdraw(Long memberId, String password){
        if(password == null || password.isBlank()){
            throw bad("탈퇴를 위해 비밀번호를 입력해주세요.");
        }
        MemberResponse member = memberMapper.findById(memberId);

        if(member == null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");
        }
        ensureCanUse(member);
        String currentHash = memberMapper.findPasswordHashById(memberId);

        if(currentHash == null || !passwordEncoder.matches(password, currentHash)){
            throw bad("비밀번호가 일치하지 않습니다.");
        }

        int activeMemberships = memberMapper.countActiveGroupMemberships(memberId);
        int ownedGroups = memberMapper.countOwnedActiveGroups(memberId);
        if(activeMemberships > 0 || ownedGroups > 0){
            throw bad("참여 중인 모임을 먼저 정리해 주세요.");
        }

        memberMapper.revokeOptionalConsents(memberId);

        int result = memberMapper.withdraw(memberId);

        if(result != 1){
            throw new IllegalStateException("회원 탈퇴 처리에 실패했습니다.");
        }
    }

    private AuthResponse response(MemberResponse member) {
        String phone = null;
        if(member.phoneEnc() != null){
            if(member.keyVer() == null){
                throw new IllegalStateException("전화번호 암호화 키 버전이 없습니다.");
            }   
            phone = phoneCryptoService.decrypt(member.phoneEnc(), member.keyVer());
        }
        MemberView view = toView(member, phone);
        return new AuthResponse(jwtTokenProvider.createToken(member.id()), "Bearer", view);
    }

    private MemberView toView(MemberResponse member, String phone) {
        return new MemberView(member.name(), member.email(), phone, member.regionSido(), member.regionSigungu(), member.regionDong(), member.status());
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private String clean(String input) {
        return input == null ? "" : input.trim();
    }

    private void ensureCanUse(MemberResponse user) {
        if (!"ACTIVE".equals(user.status()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "탈퇴했거나 이용할 수 없는 회원입니다.");
        if (!"N".equals(user.bannedYn()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이용 정지된 회원입니다.");
    }

    private record MemberProfile(String name, String email, String phone, String regionSido, String regionSigungu, String regionDong) {
    }

    private MemberProfile prepareProfile(String name, String email, String phone, String regionSido, String regionSigungu, String regionDong){
        name = clean(name);
        email = clean(email).toLowerCase(Locale.ROOT);
        phone = clean(phone).replace("-", "");
        regionSido = clean(regionSido);
        regionSigungu = clean(regionSigungu);
        String cleanedDong = clean(regionDong);
        regionDong = cleanedDong.isBlank() ? null : cleanedDong;
        
        if (name.length() < 2 || name.length() > 50) {
            throw bad("이름은 2~50자 사이여야 합니다.");
        }
        if (!EMAIL.matcher(email).matches() || email.length() > 100 || email.isBlank()) {
            throw bad("올바른 이메일 주소를 입력해주세요.");
        }
        if (!PHONE.matcher(phone).matches()) {
            throw bad("올바른 전화번호를 입력해주세요.");
        }
        if (regionSido.isBlank() || regionSido.length() > 30 || regionSigungu.isBlank() || regionSigungu.length() > 30) {
            throw bad("활동 지역을 올바르게 입력해주세요.");
        }
        if (regionDong != null && regionDong.getBytes(StandardCharsets.UTF_8).length > 40) {
            throw bad("활동 지역(읍/면/동)을 올바르게 입력해주세요.");
        }
        return new MemberProfile(name, email, phone, regionSido, regionSigungu, regionDong);
    }

    private void validatePassword(String password){
        if(password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72){
            throw bad("비밀번호는 8자 이상 입력해주세요.");
        }
    }
}
