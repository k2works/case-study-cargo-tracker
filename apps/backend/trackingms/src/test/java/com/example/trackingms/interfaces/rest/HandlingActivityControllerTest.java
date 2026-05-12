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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HandlingActivityController 統合テスト（H2 インメモリ DB 使用）
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {"DELETE FROM tracking_handling_event", "DELETE FROM tracking_activity"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("HandlingActivityController 統合テスト")
class HandlingActivityControllerTest {

    @MockitoBean
    TrackingEventPublisher trackingEventPublisher;

    @Autowired
    private MockMvc mockMvc;

    /**
     * 追跡番号を発行してから荷役作業を記録するヘルパー
     */
    private String issueTrackingNumber(String bookingId) throws Exception {
        String response = mockMvc.perform(post("/api/tracking/v1/numbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingId\": \"" + bookingId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"trackingNumber\":\"([^\"]+)\".*", "$1");
    }

    @Test
    @DisplayName("POST /api/handling/v1/activities — RECEIVE イベントを記録すると状態が RECEIVED になること")
    void shouldRecordReceiveActivityAndUpdateStatusToReceived() throws Exception {
        String trackingNumber = issueTrackingNumber("BK-001234");

        mockMvc.perform(post("/api/handling/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackingNumber": "%s",
                                  "eventType": "RECEIVE",
                                  "locationUnlocode": "JPTYO",
                                  "eventTime": "2026-06-15T10:00:00"
                                }
                                """.formatted(trackingNumber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.transportStatus").value("RECEIVED"))
                .andExpect(jsonPath("$.events[0].eventType").value("RECEIVE"))
                .andExpect(jsonPath("$.events[0].locationUnlocode").value("JPTYO"));
    }

    @Test
    @DisplayName("POST /api/handling/v1/activities — LOAD イベントを記録すると状態が LOADED になること")
    void shouldRecordLoadActivityAndUpdateStatusToLoaded() throws Exception {
        String trackingNumber = issueTrackingNumber("BK-002345");

        mockMvc.perform(post("/api/handling/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackingNumber": "%s",
                                  "eventType": "RECEIVE",
                                  "locationUnlocode": "JPTYO",
                                  "eventTime": "2026-06-15T10:00:00"
                                }
                                """.formatted(trackingNumber)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/handling/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackingNumber": "%s",
                                  "eventType": "LOAD",
                                  "locationUnlocode": "JPTYO",
                                  "eventTime": "2026-06-16T08:00:00",
                                  "voyageNumber": "V0042"
                                }
                                """.formatted(trackingNumber)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transportStatus").value("LOADED"))
                .andExpect(jsonPath("$.events").isArray());
    }

    @Test
    @DisplayName("POST /api/handling/v1/activities — 存在しない追跡番号は 404 を返すこと")
    void shouldReturn404ForNonExistentTrackingNumber() throws Exception {
        mockMvc.perform(post("/api/handling/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackingNumber": "TRK-999999",
                                  "eventType": "RECEIVE",
                                  "locationUnlocode": "JPTYO",
                                  "eventTime": "2026-06-15T10:00:00"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/handling/v1/activities — 必須フィールド欠損時は 400 を返すこと")
    void shouldReturn400WhenRequiredFieldMissing() throws Exception {
        mockMvc.perform(post("/api/handling/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackingNumber": "",
                                  "eventType": "RECEIVE",
                                  "locationUnlocode": "JPTYO",
                                  "eventTime": "2026-06-15T10:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
