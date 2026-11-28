package com.moava.member.service;

import com.moava.member.dto.MemberResponse;
import com.moava.member.dto.MemberView;
import com.moava.member.mapper.MemberMapper;
import com.moava.member.mapper.SocialAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberWithdrawalSocialTest {

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private SocialAccountMapper socialAccountMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private PhoneCryptoService phoneCryptoService;

    private MemberService memberService;
    private MemberWithdrawalScheduler withdrawalScheduler;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberMapper, passwordEncoder, jwtTokenProvider, phoneCryptoService);
        withdrawalScheduler = new MemberWithdrawalScheduler(memberMapper, socialAccountMapper);
    }

    @Test
    @DisplayName("소셜 전용 회원(PASSWORD_HASH=NULL)은 비밀번호 없이 정상 탈퇴할 수 있다")
    void withdraw_allowsSocialOnlyMemberWithoutPassword() {
        MemberResponse socialMember = new MemberResponse(
                10L, "카카오회원", "kakao@example.com", "ENC_PHONE",
                "서울", "마포구", "서교동", "ACTIVE", "N", 1, "KAKAO"
        );
        when(memberMapper.findById(10L)).thenReturn(socialMember);
        when(memberMapper.findPasswordHashById(10L)).thenReturn(null);
        when(memberMapper.countActiveGroupMemberships(10L)).thenReturn(0);
        when(memberMapper.countOwnedActiveGroups(10L)).thenReturn(0);
        when(memberMapper.withdraw(10L)).thenReturn(1);

        memberService.withdraw(10L, null);

        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(memberMapper).revokeOptionalConsents(10L);
        verify(memberMapper).withdraw(10L);
    }

    @Test
    @DisplayName("일반(EMAIL) 회원은 비밀번호가 없거나 틀리면 탈퇴가 400으로 거부된다")
    void withdraw_requiresMatchingPasswordForEmailMember() {
        MemberResponse emailMember = new MemberResponse(
                20L, "일반회원", "user@example.com", "ENC_PHONE",
                "서울", "강남구", "역삼동", "ACTIVE", "N", 1, "EMAIL"
        );
        when(memberMapper.findById(20L)).thenReturn(emailMember);
        when(memberMapper.findPasswordHashById(20L)).thenReturn("$2a$hashed");
        when(passwordEncoder.matches("WrongPass1!", "$2a$hashed")).thenReturn(false);

        assertThatThrownBy(() -> memberService.withdraw(20L, ""))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> memberService.withdraw(20L, "WrongPass1!"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(memberMapper, never()).withdraw(20L);
    }

    @Test
    @DisplayName("getActiveMember는 MemberView에 signupType을 포함해 반환한다")
    void getActiveMember_exposesSignupType() {
        MemberResponse naverMember = new MemberResponse(
                30L, "네이버회원", "naver@example.com", "ENC_PHONE",
                "서울", "성동구", "성수동", "ACTIVE", "N", 1, "NAVER"
        );
        when(memberMapper.findById(30L)).thenReturn(naverMember);
        when(phoneCryptoService.decrypt("ENC_PHONE", 1)).thenReturn("01012345678");

        MemberView view = memberService.getActiveMember(30L);

        assertThat(view.signupType()).isEqualTo("NAVER");
        assertThat(view.phone()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("30일 경과 탈퇴 회원 파기 시 MEMBER_SOCIAL_ACCOUNT 매핑도 함께 삭제한다")
    void deleteExpireMember_deletesSocialAccountsWhenPurged() {
        when(memberMapper.findExpiredWithdrawnMember()).thenReturn(List.of(100L, 101L));
        when(memberMapper.deleteWithdrawnMember(100L)).thenReturn(1);
        when(memberMapper.deleteWithdrawnMember(101L)).thenReturn(0);

        withdrawalScheduler.deleteExpireMember();

        verify(memberMapper).rejectPendingJoinRequests(100L);
        verify(socialAccountMapper).deleteAllSocialAccounts(100L);
        verify(memberMapper, never()).rejectPendingJoinRequests(101L);
        verify(socialAccountMapper, never()).deleteAllSocialAccounts(101L);
    }
}
