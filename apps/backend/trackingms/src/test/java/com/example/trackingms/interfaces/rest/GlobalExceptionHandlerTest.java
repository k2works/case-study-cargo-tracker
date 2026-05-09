package com.example.trackingms.interfaces.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GlobalExceptionHandler 統合テスト
 * バリデーションエラー時に具体的なフィールド名とエラーメッセージを返すことを検証する
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("GlobalExceptionHandler バリデーションエラーレスポンス")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/tracking/v1/numbers — bookingId が空の場合、フィールド名とメッセージを含む 400 レスポンスを返す")
    void shouldReturnFieldErrorsWhenBookingIdIsBlank() throws Exception {
        mockMvc.perform(post("/api/tracking/v1/numbers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookingId": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("バリデーションエラー"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].field").value("bookingId"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/handling/v1/activities — 複数フィールドが欠損の場合、全フィールドのエラーを返す")
    void shouldReturnMultipleFieldErrors() throws Exception {
        mockMvc.perform(post("/api/handling/v1/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "trackingNumber": "",
                                  "eventType": "",
                                  "locationUnlocode": "",
                                  "eventTime": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("バリデーションエラー"))
                .andExpect(jsonPath("$.errors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test
    @DisplayName("PUT /api/tracking/v1/{trackingNumber}/status — newStatus が空の場合、フィールドエラーを返す")
    void shouldReturnFieldErrorWhenNewStatusIsBlank() throws Exception {
        mockMvc.perform(put("/api/tracking/v1/TRK-000001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newStatus": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("バリデーションエラー"))
                .andExpect(jsonPath("$.errors[0].field").value("newStatus"));
    }
}
