package com.moava.member.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.moava.member.dto.AuthResponse;
import com.moava.member.dto.SocialSignupRequest;
import com.moava.member.service.SocialAuthService;
import com.moava.member.service.SocialFlowService;
import com.moava.member.service.SocialAuthService.SocialUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@RestController 
@RequestMapping("/api/auth/social")
public class SocialAuthController {
    private final SocialAuthService socialAuthService;
    private final SocialFlowService socialFlowService;

    private final SecureRandom random = new SecureRandom();

    @Value("${moava.oauth.frontend-url}")
    private String frontendUrl;

    public SocialAuthController(SocialAuthService socialAuthService, SocialFlowService socialFlowService){
        this.socialAuthService = socialAuthService;
        this.socialFlowService = socialFlowService;
    }

    //외부 인증 요청 정보
    private record Pending(String provider, String state, String verifier, long expiresAt) {
    }

    //임시 로그인 및 가입 코드
    private record Ticket(String token, String type, SocialUser user, long expiresAt) {
    }

    public record LoginExchangeRequest(String loginCode) {
    }

    public record SignupInfoRequest(String signupToken) {
    }

    @GetMapping("/{provider}/authorize")
    public void authorize(@PathVariable String provider, HttpServletRequest request, HttpServletResponse response) 
    throws IOException{
        String state = createToken();
        String verifier = createToken();

        HttpSession session = request.getSession(true);

        session.removeAttribute("SOCIAL_TICKET");

        session.setAttribute("SOCIAL_PENDING", new Pending(provider, state, verifier, System.currentTimeMillis() + 600000));

        String url = socialAuthService.getAuthorizeUrl(provider, state, challenge(verifier));

        response.sendRedirect(url);
        
    }

    @GetMapping("/{provider}/callback")
    public void callback(@PathVariable  String provider, @RequestParam(required = false) String code, @RequestParam(required = false) String state, @RequestParam(required = false) String error, HttpServletRequest request, HttpServletResponse response)
    throws IOException{
        HttpSession session = request.getSession(false);
        if(session == null){
            throw invalid();
        }
        Pending pending;

        synchronized(session){
            Object value = session.getAttribute("SOCIAL_PENDING");

            session.removeAttribute("SOCIAL_PENDING");

            if(!(value instanceof Pending p)){
                throw invalid();
            }
            pending = p;
        }
        if(state == null || !state.equals(pending.state()) || !provider.equals(pending.provider()) || pending.expiresAt() <= System.currentTimeMillis()){
            throw invalid();
        }

        if(error != null || code == null || code.isBlank()){
            response.sendRedirect(frontendUrl + "/oauth/callback#type=error");
            return;
        }

        String accessToken = socialAuthService.getAccessToken(provider, code, state, pending.verifier());

        SocialUser user = socialAuthService.getUserInfo(provider, accessToken);

        Long memberId = socialFlowService.findSocialMember(user);

        String type = memberId == null ? "signup" : "login";

        long expiresAt = System.currentTimeMillis() + ("login".equals(type) ? 60000 : 600000);

        String token = createToken();

        session.setAttribute("SOCIAL_TICKET", new Ticket(token, type, user, expiresAt));

        request.changeSessionId();
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");

        String path = "signup".equals(type) ? "/signup/social" : "/oauth/callback";

        response.sendRedirect(frontendUrl + path + "#type=" + type + "&token=" + token);
    }

    @PostMapping("/exchange")
    public AuthResponse exchange(@RequestBody LoginExchangeRequest request, HttpServletRequest servlet){
        Ticket ticket = getTicket(servlet, request.loginCode(), "login", true);
        return socialFlowService.login(ticket.user());
    }

    @PostMapping("/signup-info")
    public Map<String, String> signupInfo(@RequestBody SignupInfoRequest request, HttpServletRequest servlet){
        Ticket ticket = getTicket(servlet, request.signupToken(), "signup", false);
        SocialUser user = ticket.user();

        return Map.of("name", user.name() == null ? "" : user.name(), "email", user.email() == null ? "" : user.email(), "provider", user.provider());
    }

    @PostMapping("/signup")
    public AuthResponse signup(@RequestBody SocialSignupRequest request, HttpServletRequest servlet){
        Ticket ticket = getTicket(servlet, request.signupToken(), "signup", true);
        return socialFlowService.signup(ticket.user(), request, servlet.getRemoteAddr());
    }

    @GetMapping("/terms")
    public Object terms(){
        return socialFlowService.getTerms();
    }

    private Ticket getTicket(HttpServletRequest request, String token, String type, boolean consume){
        HttpSession session = request.getSession(false);
        if(session == null || token == null){
            throw invalid();
        }
        synchronized(session){
            Object value = session.getAttribute("SOCIAL_TICKET");

            if (!(value instanceof Ticket ticket)
            || !ticket.token().equals(token)
            || !ticket.type().equals(type)
            || ticket.expiresAt() <= System.currentTimeMillis()) {

            throw invalid();
            }

            if(consume){
                session.removeAttribute("SOCIAL_TICKET");
            }

            return ticket;
        }
    }

    private String createToken(){
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String challenge(String verifier){
        try{
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        }catch(Exception e){
            throw new IllegalStateException(e);
        }
    }

    private ResponseStatusException invalid(){
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 정보가 만료되었습니다. 다시 로그인해주세요.");
    }
}
