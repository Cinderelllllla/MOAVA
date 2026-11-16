package com.moava.member.controller;

import org.springframework.web.bind.annotation.*;

import com.moava.member.dto.AuthResponse;
import com.moava.member.dto.LoginRequest;
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

    @GetMapping("/me")
    public Object me(Authentication authentication) {
        return memberService.getActiveMember(authentication.getName());
    }
}
