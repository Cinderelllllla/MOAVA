package com.moava.proofs.controller;

import com.moava.proofs.dto.PaymentProofListDto;
import com.moava.proofs.service.PaymentProofService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 */
@ExtendWith(MockitoExtension.class)
class PaymentProofControllerTest {

    @Mock
    PaymentProofService paymentProofService;     // 가짜 Service

    @InjectMocks
    PaymentProofController paymentProofController;

    MockMvc mockMvc;                             // 가짜 요청을 보내는 도구

    static final String LIST_URL = "/api/v1/groups/1/payment-proofs";
    static final String REVIEW_URL = "/api/v1/groups/1/payment-proofs/100/review";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentProofController)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)   // 한글 메시지가 깨지지 않게
                .build();
    }

    @Test
    @DisplayName("목록: X-Member-Id 헤더가 없으면 400 공통 오류 형식")
    void list_noHeader_400() throws Exception {
        mockMvc.perform(get(LIST_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("로그인 정보가 없습니다."))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("목록: X-Member-Id가 숫자가 아니면 400")
    void list_headerNotNumber_400() throws Exception {
        mockMvc.perform(get(LIST_URL).header("X-Member-Id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("요청 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("목록: 정상 요청이면 200 + data 배열 + meta.requestId")
    void list_ok_200() throws Exception {
        PaymentProofListDto item = new PaymentProofListDto();
        item.setPaymentProofId(100L);
        item.setPayMonth("202611");
        item.setStatus("PENDING");
        when(paymentProofService.getProofList(1L, 20L)).thenReturn(List.of(item));

        mockMvc.perform(get(LIST_URL).header("X-Member-Id", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].paymentProofId").value(100))
                .andExpect(jsonPath("$.data[0].payMonth").value("202611"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    @DisplayName("심사: Service가 403을 던지면 403 + FORBIDDEN")
    void review_forbidden_403() throws Exception {
        when(paymentProofService.reviewProof(any(), any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 모임장만 승인할 수 있어요."));

        mockMvc.perform(patch(REVIEW_URL)
                        .header("X-Member-Id", "20")
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
                        .header("X-Member-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":"))           // 일부러 끝을 잘라 깨진 JSON
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error.message").value("요청 형식이 올바르지 않습니다."));
    }
}