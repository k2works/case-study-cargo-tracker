package com.example.cargotracker.routing.interfaces;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin"
})
@ActiveProfiles("test")
@DisplayName("Voyage Controller 統合テスト")
class VoyageControllerTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE carrier_movement, voyage RESTART IDENTITY CASCADE");
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithMockUser
    @DisplayName("GET /voyages で航路一覧画面がレンダリングされる")
    void getVoyages_shouldRenderIndexPage() throws Exception {
        mockMvc.perform(get("/voyages"))
                .andExpect(status().isOk())
                .andExpect(view().name("voyage/index"))
                .andExpect(content().string(containsString("航路一覧")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /voyages でテストデータ（V001）が表示される")
    void getVoyages_shouldShowVoyageV001() throws Exception {
        // V9 マイグレーションで V001 が INSERT 済みのため、TRUNCATE 後に再 INSERT
        jdbcTemplate.execute(
                "INSERT INTO voyage (voyage_number) VALUES ('V001')");
        jdbcTemplate.execute(
                "INSERT INTO carrier_movement (voyage_id, departure_location_unlocode, arrival_location_unlocode, departure_date, arrival_date) " +
                        "VALUES ((SELECT id FROM voyage WHERE voyage_number = 'V001'), 'JPTYO', 'USNYC', '2026-05-10 09:00:00', '2026-06-10 14:00:00')");

        mockMvc.perform(get("/voyages"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("V001")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /voyages?originUnlocode=JPTYO で出発地フィルタが動作する")
    void getVoyages_withOriginFilter_shouldReturnFilteredResults() throws Exception {
        jdbcTemplate.execute(
                "INSERT INTO voyage (voyage_number) VALUES ('V001')");
        jdbcTemplate.execute(
                "INSERT INTO carrier_movement (voyage_id, departure_location_unlocode, arrival_location_unlocode, departure_date, arrival_date) " +
                        "VALUES ((SELECT id FROM voyage WHERE voyage_number = 'V001'), 'JPTYO', 'USNYC', '2026-05-10 09:00:00', '2026-06-10 14:00:00')");

        mockMvc.perform(get("/voyages").param("originUnlocode", "JPTYO"))
                .andExpect(status().isOk())
                .andExpect(view().name("voyage/index"))
                .andExpect(content().string(containsString("V001")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /voyages?originUnlocode=DEHAM で該当なしメッセージが表示される")
    void getVoyages_withNoMatchFilter_shouldShowEmptyMessage() throws Exception {
        mockMvc.perform(get("/voyages").param("originUnlocode", "DEHAM"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("条件に合致する航海スケジュールが見つかりません")));
    }
}
