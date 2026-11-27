package com.moava.member.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.moava.member.dto.AuthResponse;
import com.moava.member.dto.SocialSignupRequest;
import com.moava.member.mapper.MemberMapper;
import com.moava.member.mapper.SocialAccountMapper;
import com.moava.member.service.SocialAuthService.SocialUser;

@Service 
public class SocialFlowService {
    private final MemberService memberService;
    private  final MemberMapper memberMapper;
    private final SocialAccountMapper socialAccountMapper;
    private final PhoneCryptoService phoneCryptoService;
    private final JdbcTemplate jdbcTemplate;

    public SocialFlowService(MemberService memberService, MemberMapper memberMapper, SocialAccountMapper socialAccountMapper, PhoneCryptoService phoneCryptoService, JdbcTemplate jdbcTemplate){
        this.memberService = memberService;
        this.memberMapper = memberMapper;
        this.socialAccountMapper = socialAccountMapper;
        this.phoneCryptoService = phoneCryptoService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record Terms(Long termsId, String termsCode, String title, String body, String version, String requiredYn) {
    }

    public Long findSocialMember(SocialUser user){
        return socialAccountMapper.findMemberId(user.provider(), user.providerUserId());
    }

    @Transactional 
    public AuthResponse login(SocialUser user){
        Long memberId = findSocialMember(user);

        if(memberId == null){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "연결된 회원이 없습니다.");
        }

        memberMapper.lockMember(memberId);
        
        Long linkedId = findSocialMember(user);

        if(!memberId.equals(linkedId)){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "소셜 계정 연결 상태를 확인해주세요.");
        }

        AuthResponse result = memberService.loginSocialMember(memberId);

        socialAccountMapper.updateLastLogin(memberId, user.provider());

        return result;
    }

    public List<Terms> getTerms() {

        return jdbcTemplate.query("""
            SELECT TERMS_ID, TERMS_CODE, TITLE,
                   BODY, VERSION, REQUIRED_YN
            FROM TERMS_POLICY
            WHERE ACTIVE_YN = 'Y'
                AND EFFECTIVE_AT <= SYSDATE
            ORDER BY TERMS_ID
            """,
            (rs, rowNum) -> new Terms(
                rs.getLong("TERMS_ID"),
                rs.getString("TERMS_CODE"),
                rs.getString("TITLE"),
                rs.getString("BODY"),
                rs.getString("VERSION"),
                rs.getString("REQUIRED_YN")
            )
        );
    }

    @Transactional
    public AuthResponse signup(SocialUser user, SocialSignupRequest request, String ip){
        if(findSocialMember(user) != null){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 연결된 소셜 계정입니다.");
        }
        List<Terms> policies = getTerms();

        Set<Long> agreed = new HashSet<>(request.agreedTermsIds() == null ? List.of() : request.agreedTermsIds());

        Set<String> requiredCodes = Set.of("TOS_SERVICE",
            "PRIVACY_COLLECT",
            "CREDIT_AI_PROFI",
            "E_FIN_SETTLE");
        
        Set<String> optionalCodes = Set.of("LOC_KAKAOMAP", "MKT_SMS_OPT");

        Set<String> actualRequired = new HashSet<>();
        Set<String> actualOptional = new HashSet<>();
        Set<Long> validIds = new HashSet<>();

        for(Terms terms : policies){
            validIds.add(terms.termsId());

            if("Y".equals(terms.requiredYn())){
                actualRequired.add(terms.termsCode());
            }else{
                actualOptional.add(terms.termsCode());
            }
        }

        if(policies.size() != 6 || !actualRequired.equals(requiredCodes) || !actualOptional.equals(optionalCodes)){
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "활성 약관 6종을 확인해주세요.");
        }
        if(!validIds.containsAll(agreed)){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "최신 약관을 다시 확인해주세요.");
        }

        for(Terms terms : policies){
            if("Y".equals(terms.requiredYn()) && !agreed.contains(terms.termsId())){
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "필수 약관에 동의해주세요.");
            }
        }
        String email = user.email();

        if(email == null || email.isBlank()){
            email = request.email();
        }

        SocialSignupRequest signupRequest = new SocialSignupRequest(request.signupToken(), request.name(), email, request.phone(), request.regionSido(), request.regionSigungu(), request.regionDong(), request.agreedTermsIds());

        Long memberId = memberService.signupSocial(signupRequest, user.provider());

        socialAccountMapper.insertSocialAccount(memberId, user.provider(), user.providerUserId(), user.email());

        int keyVer = phoneCryptoService.currentKeyVersion();
        String ipEnc = phoneCryptoService.encrypt(ip, keyVer);

        for(Terms terms : policies){
            if(!agreed.contains(terms.termsId())){
                continue;
            }
            jdbcTemplate.update("""
                INSERT INTO MEMBER_CONSENT (
                    MEMBER_CONSENT_ID,
                    MEMBER_ID,
                    TERMS_ID,
                    AGREED_YN,
                    CONSENT_CHANNEL,
                    IP_ADDR_ENC,
                    KEY_VER,
                    AGREED_AT
                )
                VALUES (
                    SEQ_MEMBER_CONSENT.NEXTVAL,
                    ?, ?,
                    'Y',
                    'SIGNUP',
                    ?, ?,
                    SYSDATE
                )
                """,
                memberId,
                terms.termsId(),
                ipEnc,
                keyVer
            );
        }
        socialAccountMapper.updateLastLogin(memberId, user.provider());
        return memberService.loginSocialMember(memberId);
    }
}
