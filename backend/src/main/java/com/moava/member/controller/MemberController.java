package com.moava.member.controller;

import org.springframework.web.bind.annotation.*;

import com.moava.member.dto.AuthResponse;
import com.moava.member.dto.LoginRequest;
import com.moava.member.dto.MemberUpdateRequest;
import com.moava.member.dto.MemberView;
import com.moava.member.dto.PasswordChangeRequest;
import com.moava.member.dto.SignupRequest;
import com.moava.member.service.MemberService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;


@RestController
@RequestMapping("/api/auth")
public class MemberController {
    private final MemberService memberService;
    public MemberController(MemberService authService) {
        this.memberService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@RequestBody SignupRequest request) {
        return memberService.signup(request);
    }

    @PostMapping ("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return memberService.login(request);
    }

    //정보 조회
    @GetMapping("/me")
    public MemberView me(Authentication authentication) {
        Long memberId = Long.valueOf(authentication.getName());
        return memberService.getActiveMember(memberId);
    }

    @GetMapping("/check-email")
    public EmailCheckResponse checkEmail(@RequestParam String email){
        return new EmailCheckResponse(memberService.checkEmail(email));
    }

    //정보 수정
    @PutMapping("/me")
    public MemberView updateMe(Authentication authentication, @RequestBody MemberUpdateRequest request){
        return memberService.updateProfile(getMemberId(authentication), request);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(Authentication authentication, @RequestBody PasswordChangeRequest request){
        memberService.changePassword(getMemberId(authentication), request);
    }

    //탈퇴
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(Authentication authentication, @RequestBody(required = false) WithdrawRequest request){
        memberService.withdraw(getMemberId(authentication), request == null ? null : request.password());
    }

    private Long getMemberId(Authentication authentication){
        if(authentication == null || authentication.getName() == null){
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        try{
            return Long.valueOf(authentication.getName());
        }catch(NumberFormatException e){
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다.");
        }
    }

    public record EmailCheckResponse(boolean available) {
    }

    public record WithdrawRequest(String password) {
    }
}
