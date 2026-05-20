package com.example.settlement.api.settlement.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.settlement.core.domain.TransactionType;
import com.example.settlement.core.persistence.entity.SettlementExpected;
import com.example.settlement.core.persistence.repository.SettlementExpectedRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SettlementControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SettlementExpectedRepository settlementExpectedRepository;

    @BeforeEach
    void setUp() {
        settlementExpectedRepository.deleteAll();
        settlementExpectedRepository.save(
                new SettlementExpected(
                        LocalDate.parse("2026-03-10"),
                        "PG1",
                        "tx-q-1",
                        "merchant1",
                        LocalDate.parse("2026-03-10"),
                        TransactionType.APPROVAL,
                        new BigDecimal("100000"),
                        new BigDecimal("2060"),
                        new BigDecimal("206"),
                        new BigDecimal("97734"),
                        LocalDate.parse("2026-03-12")));
    }

    @Test
    @DisplayName("필수 검색 조건 누락 시 실패")
    void searchFailWhenRequiredParamsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/settlements"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("잘못된 요청"));
    }

    @Test
    @DisplayName("잘못된 거래 타입 파라미터는 400과 표준 에러 포맷을 반환한다")
    void searchFailWhenTransactionTypeInvalid() throws Exception {
        mockMvc.perform(
                        get("/api/v1/settlements")
                                .queryParam("merchantId", "merchant1")
                                .queryParam("fromDate", "2026-03-01")
                                .queryParam("toDate", "2026-03-31")
                                .queryParam("transactionType", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("잘못된 요청"));
    }

    @Test
    @DisplayName("정산 조회 응답에 필수 항목이 포함된다")
    void searchSuccess() throws Exception {
        mockMvc.perform(
                        get("/api/v1/settlements")
                                .queryParam("merchantId", "merchant1")
                                .queryParam("fromDate", "2026-03-01")
                                .queryParam("toDate", "2026-03-31")
                                .queryParam("page", "0")
                                .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].merchantId").value("merchant1"))
                .andExpect(jsonPath("$.content[0].transactionDate").value("2026-03-10"))
                .andExpect(jsonPath("$.content[0].transactionType").value("APPROVAL"))
                .andExpect(jsonPath("$.content[0].amount").value(100000))
                .andExpect(jsonPath("$.content[0].expectedSettlementAmount").value(97734))
                .andExpect(jsonPath("$.content[0].expectedSettlementDate").value("2026-03-12"));
    }
}
