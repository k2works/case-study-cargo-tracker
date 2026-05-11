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

    private static final String SETUP_SQL_INSERT_ACTIVITY =
            "INSERT INTO tracking_activity (tracking_number, booking_id, transport_status, created_at, updated_at) " +
            "VALUES ('TRK-000001', 'BK-001234', 'LOADED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

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
}
