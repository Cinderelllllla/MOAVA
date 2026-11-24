package com.moava.proofs.controller;

import com.moava.proofs.dto.PaymentProofListDto;
import com.moava.proofs.service.PaymentProofService;
import com.moava.proofs.service.ProofQueryService;
import com.moava.proofs.service.ProofReviewService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PaymentProofController 테스트 — 컨트롤러만 띄워서 요청·응답 형식 확인 (Security·DB 없음)
 * 로그인 정보는 JWT 필터 대신 .principal(...)로 직접 넣음 (principal 이름 = MEMBER_ID 문자열)
 */
@ExtendWith(MockitoExtension.class)
class PaymentProofControllerTest {

    @Mock
    PaymentProofService paymentProofService;     // 가짜 Service

    @Mock
    ProofQueryService proofQueryService;         // 가짜 Service (F22 목록·상세·파일 조회)

    @Mock
    ProofReviewService proofReviewService;       // 가짜 Service (F23 승인·보완 요청)
    @InjectMocks
    PaymentProofController paymentProofController;

    MockMvc mockMvc;                             // 가짜 요청을 보내는 도구

    static final String LIST_URL = "/api/v1/groups/1/payment-proofs";
    static final String REVIEW_URL = "/api/v1/groups/1/payment-proofs/100/review";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentProofController)
                .setControllerAdvice(new ProofExceptionHandler())             // 밖으로 뺀 오류 처리기 등록
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)   // 한글 메시지가 깨지지 않게
                .build();
    }

    @Test
    @DisplayName("목록: 로그인 정보가 없으면 401 공통 오류 형식")
    void list_notLoggedIn_401() throws Exception {
        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("목록: 로그인 정보의 고유번호가 숫자가 아니면 401")
    void list_principalNotNumber_401() throws Exception {
        mockMvc.perform(get(LIST_URL).principal(login("abc")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("로그인 정보가 올바르지 않습니다."));
    }

    @Test
    @DisplayName("목록: 경로의 모임 ID가 숫자가 아니면 400")
    void list_groupIdNotNumber_400() throws Exception {
        mockMvc.perform(get("/api/v1/groups/abc/payment-proofs").principal(login("20")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("목록: 로그인한 회원이면 200 + data 배열 + meta.requestId")
    void list_ok_200() throws Exception {
        PaymentProofListDto item = new PaymentProofListDto();
        item.setPaymentProofId(100L);
        item.setPayMonth("202611");
        item.setStatus("PENDING");
        when(proofQueryService.getProofList(1L, 20L)).thenReturn(List.of(item));   // 로그인 고유번호 20이 그대로 전달되는지

        mockMvc.perform(get(LIST_URL).principal(login("20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].paymentProofId").value(100))
                .andExpect(jsonPath("$.data[0].payMonth").value("202611"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    @DisplayName("심사: Service가 403을 던지면 403 + FORBIDDEN")
    void review_forbidden_403() throws Exception {
        when(proofReviewService.reviewProof(any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 모임장만 승인할 수 있어요."));

        mockMvc.perform(patch(REVIEW_URL)
                        .principal(login("20"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("해당 모임장만 승인할 수 있어요."));
    }

    @Test
    @DisplayName("심사: JSON 형식이 깨지면 400")
    void review_brokenJson_400() throws Exception {
        mockMvc.perform(patch(REVIEW_URL)
                        .principal(login("10"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":"))           // 일부러 끝을 잘라 깨진 JSON
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("요청 형식이 올바르지 않습니다."));
    }

    // JWT 필터가 만드는 것과 같은 로그인 정보 (principal 이름 = MEMBER_ID 문자열)
    private static UsernamePasswordAuthenticationToken login(String memberId) {
        return new UsernamePasswordAuthenticationToken(memberId, null, List.of());
    }
}