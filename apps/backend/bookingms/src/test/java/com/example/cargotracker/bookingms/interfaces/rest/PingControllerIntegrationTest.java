package com.example.cargotracker.bookingms.interfaces.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PingController の統合テスト。
 *
 * <p>Phase 0 レビュー指摘対応（タスク 1.1）。MockMvc で GET /api/ping の
 * レスポンス構造を検証する。
 */
@SpringBootTest
@ActiveProfiles({"local-h2", "springboot-integration-test"})
@DisplayName("PingController 統合テスト")
class PingControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("GET /api/ping は service・phase・timestamp を返す")
    void ping_レスポンスにサービス情報が含まれる() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("bookingms"))
                .andExpect(jsonPath("$.phase").value("0"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
