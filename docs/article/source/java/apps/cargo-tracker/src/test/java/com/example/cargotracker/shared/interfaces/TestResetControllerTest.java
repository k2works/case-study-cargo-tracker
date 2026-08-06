package com.example.cargotracker.shared.interfaces;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("TestResetController テスト")
class TestResetControllerTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser
    @DisplayName("POST /api/test/reset で cargo・shipper テーブルが空になる")
    void shouldTruncateTablesOnReset() throws Exception {
        // Arrange: データを挿入
        jdbcTemplate.execute("""
                INSERT INTO shipper (id, shipper_code, shipper_type, name, email, phone)
                VALUES ('11111111-1111-1111-1111-111111111111', 'S-TEST-001', 'INDIVIDUAL', 'テスト荷主', 'reset-test@example.com', '090-0000-0001')
                """);
        jdbcTemplate.execute("""
                INSERT INTO cargo (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode, arrival_deadline)
                VALUES ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'GENERAL', 100.0, 'JPTYO', 'USLAX', '2026-12-31')
                """);

        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Act
        mockMvc.perform(post("/api/test/reset").with(csrf()))
                .andExpect(status().isNoContent());

        // Assert: テーブルが空になっていること
        int cargoCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cargo", Integer.class);
        int shipperCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shipper", Integer.class);
        assertThat(cargoCount).isZero();
        assertThat(shipperCount).isZero();
    }
}
