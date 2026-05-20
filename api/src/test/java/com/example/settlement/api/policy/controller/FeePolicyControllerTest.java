package com.example.settlement.api.policy.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeePolicyControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("정책 등록 성공")
    void createPolicySuccess() throws Exception {
        Map<String, Object> request =
                Map.of(
                        "pgCompany", "PG1",
                        "merchantId", "merchant1",
                        "paymentMethod", "CARD",
                        "feeType", "RATE",
                        "feeValue", "0.0210",
                        "settlementCycleDays", 2,
                        "startDate", "2027-01-01",
                        "endDate", "2027-12-31");

        mockMvc.perform(
                        post("/api/v1/policies")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pgCompany").value("PG1"))
                .andExpect(jsonPath("$.merchantId").value("merchant1"))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"));
    }

    @Test
    @DisplayName("정책 기간 중복 등록 실패")
    void createPolicyOverlapFail() throws Exception {
        Map<String, Object> request =
                Map.of(
                        "pgCompany", "PG1",
                        "merchantId", "merchant1",
                        "paymentMethod", "CARD",
                        "feeType", "RATE",
                        "feeValue", "0.0210",
                        "settlementCycleDays", 2,
                        "startDate", "2026-06-01",
                        "endDate", "2026-12-31");

        mockMvc.perform(
                        post("/api/v1/policies")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("수수료 정책 기간 중복"));
    }

    @Test
    @DisplayName("존재하지 않는 정책 변경 요청은 404를 반환한다")
    void updateNotFoundPolicyFail() throws Exception {
        Map<String, Object> request = Map.of("endDate", "2026-12-31");

        mockMvc.perform(
                        put("/api/v1/policies/{policyId}", 999999L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("수수료 정책 없음"));
    }
}
