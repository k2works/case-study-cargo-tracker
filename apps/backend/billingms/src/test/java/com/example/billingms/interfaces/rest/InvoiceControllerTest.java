package com.example.billingms.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("InvoiceController 統合テスト")
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("POST /api/billing/v1/invoices/calculate — 料金を算出できること")
    void shouldCalculateInvoice() throws Exception {
        String body = """
                {
                  "bookingId": "BK-001234",
                  "shipperId": "SH-001",
                  "lineItems": [
                    {"description": "基本料金", "amountValue": 100000},
                    {"description": "距離料金", "amountValue": 50000}
                  ]
                }
                """;

        mockMvc.perform(post("/api/billing/v1/invoices/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value("BK-001234"))
                .andExpect(jsonPath("$.baseAmountValue").value(150000))
                .andExpect(jsonPath("$.finalAmountValue").value(165000))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.lineItems").isArray())
                .andExpect(jsonPath("$.lineItems.length()").value(2));
    }

    @Test
    @DisplayName("POST /api/billing/v1/invoices/{id}/confirm — 料金を確定できること")
    void shouldConfirmInvoice() throws Exception {
        String calcBody = """
                {
                  "bookingId": "BK-002222",
                  "shipperId": "SH-002",
                  "lineItems": [
                    {"description": "基本料金", "amountValue": 200000}
                  ]
                }
                """;

        String result = mockMvc.perform(post("/api/billing/v1/invoices/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(calcBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(result).get("id").asLong();

        mockMvc.perform(post("/api/billing/v1/invoices/" + id + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("CONFIRMED"));
    }
}
