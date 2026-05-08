package com.example.trackingms.interfaces.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TrackingNumberController 統合テスト（H2 インメモリ DB 使用）
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {"DELETE FROM tracking_handling_event", "DELETE FROM tracking_activity"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("TrackingNumberController 統合テスト")
class TrackingNumberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/tracking/v1/numbers — 追跡番号を正常に発行できること")
    void shouldIssueTrackingNumber() throws Exception {
        mockMvc.perform(post("/api/tracking/v1/numbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bookingId": "BK-001234"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").isNotEmpty())
                .andExpect(jsonPath("$.bookingId").value("BK-001234"))
                .andExpect(jsonPath("$.transportStatus").value("NOT_RECEIVED"));
    }

    @Test
    @DisplayName("POST /api/tracking/v1/numbers — 同一予約 ID での重複発行は既存の追跡番号を返すこと")
    void shouldReturnExistingTrackingNumberForDuplicateRequest() throws Exception {
        String firstResponse = mockMvc.perform(post("/api/tracking/v1/numbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId": "BK-001234"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstTrackingNumber = firstResponse.replaceAll(".*\"trackingNumber\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/tracking/v1/numbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId": "BK-001234"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value(firstTrackingNumber));
    }

    @Test
    @DisplayName("POST /api/tracking/v1/numbers — bookingId が空の場合は 400 を返すこと")
    void shouldReturn400WhenBookingIdIsBlank() throws Exception {
        mockMvc.perform(post("/api/tracking/v1/numbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId": ""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
