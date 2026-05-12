package com.example.trackingms.interfaces.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TrackingExceptionController 統合テスト（H2 インメモリ DB 使用）
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Sql(statements = {
        "DELETE FROM tracking_exception_event",
        "DELETE FROM tracking_handling_event",
        "DELETE FROM tracking_activity"
},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DisplayName("TrackingExceptionController 統合テスト")
class TrackingExceptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /exceptions — 遅延例外を記録すると 200 で EXCEPTION 状態が返る")
    @Sql(statements = {
            "DELETE FROM tracking_exception_event",
            "DELETE FROM tracking_handling_event",
            "DELETE FROM tracking_activity",
            "INSERT INTO tracking_activity (tracking_number, booking_id, transport_status, created_at, updated_at) " +
            "VALUES ('TRK-000001', 'BK-001234', 'LOADED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
    }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void shouldRecordExceptionAndReturnException() throws Exception {
        String requestBody = """
                {
                    "exceptionType": "DELAY",
                    "locationUnlocode": "JPTYO",
                    "reason": "悪天候による遅延",
                    "escalationFlag": false
                }
                """;

        mockMvc.perform(post("/api/tracking/v1/TRK-000001/exceptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRK-000001"))
                .andExpect(jsonPath("$.transportStatus").value("EXCEPTION"))
                .andExpect(jsonPath("$.exceptions[0].exceptionType").value("DELAY"))
                .andExpect(jsonPath("$.exceptions[0].status").value("OPEN"));
    }

    @Test
    @DisplayName("POST /exceptions — 存在しない追跡番号の場合は 400 が返る")
    void shouldReturn400ForNonExistentTrackingNumber() throws Exception {
        String requestBody = """
                {
                    "exceptionType": "DELAY",
                    "reason": "遅延理由"
                }
                """;

        mockMvc.perform(post("/api/tracking/v1/TRK-999999/exceptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /exceptions/{id}/response — 対応内容を更新すると 200 で IN_PROGRESS が返る")
    @Sql(statements = {
            "DELETE FROM tracking_exception_event",
            "DELETE FROM tracking_handling_event",
            "DELETE FROM tracking_activity",
            "INSERT INTO tracking_activity (tracking_number, booking_id, transport_status, created_at, updated_at) " +
            "VALUES ('TRK-000001', 'BK-001234', 'EXCEPTION', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            "INSERT INTO tracking_exception_event (tracking_id, exception_type, occurred_at, location_unlocode, reason, escalation_flag, response_content, new_estimated_arrival, status, created_at) " +
            "VALUES ((SELECT id FROM tracking_activity WHERE tracking_number = 'TRK-000001'), 'DELAY', CURRENT_TIMESTAMP, 'JPTYO', '悪天候による遅延', false, NULL, NULL, 'OPEN', CURRENT_TIMESTAMP)"
    }, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void shouldRespondToExceptionAndReturnInProgress() throws Exception {
        String requestBody = """
                {
                    "responseContent": "代替航路を手配しました",
                    "newEstimatedArrival": "2026-06-25"
                }
                """;

        mockMvc.perform(put("/api/tracking/v1/TRK-000001/exceptions/1/response")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRK-000001"))
                .andExpect(jsonPath("$.exceptions[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.exceptions[0].responseContent").value("代替航路を手配しました"));
    }
}
