package com.example.cargotracker.tracking.interfaces;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin"
})
@ActiveProfiles("test")
@DisplayName("Tracking Thymeleaf Controller 統合テスト")
class TrackingThymeleafControllerTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE tracking_handling_event, tracking_record RESTART IDENTITY CASCADE");
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithMockUser
    @DisplayName("GET /tracking/handling で荷役作業記録フォームが表示される")
    void getHandling_shouldRenderHandlingForm() throws Exception {
        mockMvc.perform(get("/tracking/handling"))
                .andExpect(status().isOk())
                .andExpect(view().name("tracking/handling"))
                .andExpect(content().string(containsString("荷役作業記録")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /tracking/handling に存在しない追跡番号を送ると errorMessage がセットされる")
    void postHandling_withInvalidTrackingNumber_shouldSetErrorMessage() throws Exception {
        mockMvc.perform(post("/tracking/handling").with(csrf())
                        .param("trackingNumber", "TRK-00000000-NOTEXIST")
                        .param("eventType", "RECEIVE")
                        .param("completionTime", "2027-01-01T10:00")
                        .param("locationUnlocode", "JPTYO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/tracking/handling"))
                .andExpect(flash().attribute("errorMessage",
                        containsString("Tracking record not found")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /tracking/handling に有効な追跡番号を送ると successMessage がセットされる")
    void postHandling_withValidTrackingNumber_shouldSetSuccessMessage() throws Exception {
        // テスト用追跡レコードを直接 DB に挿入
        jdbcTemplate.update(
                "INSERT INTO tracking_record (tracking_number, booking_id, cargo_status) VALUES (?, ?, ?)",
                "TRK-20270101-TEST0001", "00000000-0000-0000-0000-000000000001", "AWAITING_RECEIPT");

        mockMvc.perform(post("/tracking/handling").with(csrf())
                        .param("trackingNumber", "TRK-20270101-TEST0001")
                        .param("eventType", "RECEIVE")
                        .param("completionTime", "2027-01-01T10:00")
                        .param("locationUnlocode", "JPTYO"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/tracking/handling"))
                .andExpect(flash().attribute("successMessage",
                        containsString("荷役作業を記録しました")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /tracking/handling に CLAIM イベントを送ると successMessage がセットされる")
    void postHandling_withClaimEvent_shouldSetSuccessMessage() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO tracking_record (tracking_number, booking_id, cargo_status) VALUES (?, ?, ?)",
                "TRK-20270101-TEST0002", "00000000-0000-0000-0000-000000000002", "UNLOADED");

        mockMvc.perform(post("/tracking/handling").with(csrf())
                        .param("trackingNumber", "TRK-20270101-TEST0002")
                        .param("eventType", "CLAIM")
                        .param("completionTime", "2027-01-16T10:00")
                        .param("locationUnlocode", "USLAX"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/tracking/handling"))
                .andExpect(flash().attribute("successMessage",
                        containsString("荷役作業を記録しました")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /tracking/{trackingNumber} で追跡情報詳細ページが表示される")
    void getTrackingDetail_shouldRenderDetailPage() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO tracking_record (tracking_number, booking_id, cargo_status) VALUES (?, ?, ?)",
                "TRK-20270101-TEST0003", "00000000-0000-0000-0000-000000000003", "RECEIVED");

        mockMvc.perform(get("/tracking/TRK-20270101-TEST0003"))
                .andExpect(status().isOk())
                .andExpect(view().name("tracking/detail"))
                .andExpect(content().string(containsString("TRK-20270101-TEST0003")));
    }

    @Test
    @DisplayName("GET /public/tracking/{trackingNumber} で認証なしでも追跡情報が表示される")
    void getPublicTrackingDetail_shouldRenderDetailPageWithoutAuth() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO tracking_record (tracking_number, booking_id, cargo_status) VALUES (?, ?, ?)",
                "TRK-20270101-TEST0004", "00000000-0000-0000-0000-000000000004", "LOADED");

        mockMvc.perform(get("/public/tracking/TRK-20270101-TEST0004"))
                .andExpect(status().isOk())
                .andExpect(view().name("tracking/detail"))
                .andExpect(content().string(containsString("TRK-20270101-TEST0004")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /tracking/{trackingNumber} で存在しない追跡番号の場合 404 が返る")
    void getTrackingDetail_withNotFound_shouldReturn404() throws Exception {
        mockMvc.perform(get("/tracking/TRK-00000000-NOTEXIST"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /tracking/status で貨物状態を手動更新できる")
    void postTrackingStatus_shouldUpdateCargoStatus() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO tracking_record (tracking_number, booking_id, cargo_status) VALUES (?, ?, ?)",
                "TRK-20270101-TEST0005", "00000000-0000-0000-0000-000000000005", "RECEIVED");

        mockMvc.perform(post("/tracking/status").with(csrf())
                        .param("trackingNumber", "TRK-20270101-TEST0005")
                        .param("newStatus", "LOADED")
                        .param("locationUnlocode", "JPTYO")
                        .param("updateTime", "2027-01-02T10:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage",
                        containsString("状態を更新しました")));
    }
}
