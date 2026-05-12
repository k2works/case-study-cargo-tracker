package com.example.trackingms.interfaces.rest;

import com.example.trackingms.domain.ports.TrackingEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TrackingStatusController 統合テスト（H2 インメモリ DB 使用）
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {"DELETE FROM tracking_handling_event", "DELETE FROM tracking_activity"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("TrackingStatusController 統合テスト")
class TrackingStatusControllerTest {

    @MockitoBean
    TrackingEventPublisher trackingEventPublisher;

    @Autowired
    private MockMvc mockMvc;

    private String issueTrackingNumber(String bookingId) throws Exception {
        String response = mockMvc.perform(post("/api/tracking/v1/numbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"" + bookingId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"trackingNumber\":\"([^\"]+)\".*", "$1");
    }

    @Test
    @DisplayName("GET /api/tracking/v1/{trackingNumber} — 追跡情報を取得できること")
    void shouldGetTrackingActivity() throws Exception {
        String trackingNumber = issueTrackingNumber("BK-001234");

        mockMvc.perform(get("/api/tracking/v1/" + trackingNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.bookingId").value("BK-001234"))
                .andExpect(jsonPath("$.transportStatus").value("NOT_RECEIVED"));
    }

    @Test
    @DisplayName("GET /api/tracking/v1/{trackingNumber} — 存在しない追跡番号は 404 を返すこと")
    void shouldReturn404ForNonExistentTrackingNumber() throws Exception {
        mockMvc.perform(get("/api/tracking/v1/TRK-999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("PUT /api/tracking/v1/{trackingNumber}/status — 貨物状態を手動更新できること")
    void shouldUpdateTrackingStatus() throws Exception {
        String trackingNumber = issueTrackingNumber("BK-001234");

        mockMvc.perform(put("/api/tracking/v1/" + trackingNumber + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\": \"EXCEPTION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.transportStatus").value("EXCEPTION"));
    }

    @Test
    @DisplayName("PUT /api/tracking/v1/{trackingNumber}/status — 無効な状態値は 400 を返すこと")
    void shouldReturn400ForInvalidStatus() throws Exception {
        String trackingNumber = issueTrackingNumber("BK-001234");

        mockMvc.perform(put("/api/tracking/v1/" + trackingNumber + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\": \"INVALID_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("PUT /api/tracking/v1/{trackingNumber}/status — 存在しない追跡番号は 404 を返すこと")
    void shouldReturn404WhenTrackingNumberNotFound() throws Exception {
        mockMvc.perform(put("/api/tracking/v1/TRK-999999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newStatus\": \"EXCEPTION\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }
}
