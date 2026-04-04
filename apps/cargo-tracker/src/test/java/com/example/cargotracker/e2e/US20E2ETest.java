package com.example.cargotracker.e2e;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US20 航海スケジュール検索 E2E テスト。
 *
 * <p>シードデータ（V015）の航海データを使用して検索・フィルタリングを検証する。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US20 航海スケジュール検索 E2E テスト")
class US20E2ETest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        session = loginAsAdmin();
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession();
    }

    @Test
    @DisplayName("E1: JPTYO→SGSIN の航海スケジュールをすべて取得できる")
    void e1_航海スケジュールをすべて取得できる() throws Exception {
        // シードデータ: SG001, SG002, SG003 は JPTYO→SGSIN（直行または経由）
        mockMvc.perform(get("/api/v1/routings/voyage-schedules")
                        .session(session)
                        .param("origin", "JPTYO")
                        .param("dest", "SGSIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("E2: 期限フィルタで期限超過の航海が除外される")
    void e2_期限フィルタで期限超過の航海が除外される() throws Exception {
        // SG003: JPTYO→SGSIN 到着 2026-06-28 > 2026-06-20 → 除外
        // SG001: 到着 2026-06-15 ≤ 2026-06-20 → 含まれる
        // SG002: 最終レグ到着 2026-06-19 ≤ 2026-06-20 → 含まれる
        mockMvc.perform(get("/api/v1/routings/voyage-schedules")
                        .session(session)
                        .param("origin", "JPTYO")
                        .param("dest", "SGSIN")
                        .param("deadline", "2026-06-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.voyageNumber == 'SG001')]").exists())
                .andExpect(jsonPath("$[?(@.voyageNumber == 'SG002')]").exists());
    }

    @Test
    @DisplayName("E3: 航海スケジュールにはレグ情報が含まれる")
    void e3_航海スケジュールにレグ情報が含まれる() throws Exception {
        mockMvc.perform(get("/api/v1/routings/voyage-schedules")
                        .session(session)
                        .param("origin", "JPTYO")
                        .param("dest", "SGSIN")
                        .param("deadline", "2026-06-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].voyageNumber", is("SG001")))
                .andExpect(jsonPath("$[0].legs").isArray())
                .andExpect(jsonPath("$[0].legs[0].originLocode", is("JPTYO")))
                .andExpect(jsonPath("$[0].legs[0].destinationLocode", is("SGSIN")));
    }

    @Test
    @DisplayName("E4: 出発地が一致しない航海は検索されない")
    void e4_出発地が一致しない航海は検索されない() throws Exception {
        // JPOSA→SGSIN のシードデータは SG004 のみ
        mockMvc.perform(get("/api/v1/routings/voyage-schedules")
                        .session(session)
                        .param("origin", "JPOSA")
                        .param("dest", "SGSIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].voyageNumber", is("SG004")));
    }
}
