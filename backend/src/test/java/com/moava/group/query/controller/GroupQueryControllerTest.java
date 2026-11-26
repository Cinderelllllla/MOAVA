package com.moava.group.query.controller;

import com.moava.group.query.dto.PublicGroupResponse;
import com.moava.group.query.service.GroupQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F11~F14 GET /api/groups 컨트롤러 단위 테스트 */
@ExtendWith(MockitoExtension.class)
class GroupQueryControllerTest {

    @Mock
    GroupQueryService groupQueryService;

    @InjectMocks
    GroupQueryController controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("GET /api/groups: 로그인 회원이 필터와 함께 조회하면 200과 공개 모임 목록을 반환한다")
    void getPublicGroups_authenticated_200() throws Exception {
        PublicGroupResponse item = new PublicGroupResponse(
                101L,
                "마포 직장인 목돈 모으기",
                2L,
                "김○늘",
                "RECRUITING",
                "SAVINGS",
                "서울특별시",
                "마포구",
                new BigDecimal("37.5663245"),
                new BigDecimal("126.9018489"),
                200_000L,
                6,
                2,
                6,
                5,
                new BigDecimal("3.0"),
                "PUBLIC",
                "마포구 직장인 저축 모임",
                "안전한 납입과 정산",
                "매월 5일 납입 · 탈퇴 시 정산안 동의",
                false,
                "NONE"
        );
        when(groupQueryService.getPublicGroups(10L, "마포구", "SAVINGS", "목돈"))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/groups")
                        .param("region", "마포구")
                        .param("purpose", "SAVINGS")
                        .param("q", "목돈")
                        .principal(memberToken("10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(101))
                .andExpect(jsonPath("$[0].ownerName").value("김○늘"))
                .andExpect(jsonPath("$[0].lat").value(37.5663245))
                .andExpect(jsonPath("$[0].lng").value(126.9018489))
                .andExpect(jsonPath("$[0].rulesTitle").value("안전한 납입과 정산"))
                .andExpect(jsonPath("$[0].rulesBody").value("매월 5일 납입 · 탈퇴 시 정산안 동의"))
                .andExpect(jsonPath("$[0].orderConfirmed").value(false))
                .andExpect(jsonPath("$[0].currentMembers").value(2))
                .andExpect(jsonPath("$[0].role").value("NONE"));

        verify(groupQueryService).getPublicGroups(10L, "마포구", "SAVINGS", "목돈");
    }

    @Test
    @DisplayName("GET /api/groups: 미인증 요청은 401을 반환한다")
    void getPublicGroups_unauthenticated_401() throws Exception {
        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(groupQueryService);
    }

    private static UsernamePasswordAuthenticationToken memberToken(String memberId) {
        return new UsernamePasswordAuthenticationToken(
                memberId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        );
    }
}
