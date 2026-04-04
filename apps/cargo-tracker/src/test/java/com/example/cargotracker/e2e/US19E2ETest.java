package com.example.cargotracker.e2e;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US19 経路設計条件確認 E2E テスト。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US19 経路設計条件確認 E2E テスト")
class US19E2ETest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private MockHttpSession session;
    private String bookingId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        session = loginAsAdmin();
        bookingId = createBooking();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM bookings");
        jdbcTemplate.execute("DELETE FROM shippers");
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession();
    }

    private String createBooking() throws Exception {
        var shipperLocation = mockMvc.perform(post("/api/shippers")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"テスト荷主 US19\", \"email\": \"test-us19@example.com\", \"category\": \"INDIVIDUAL\"}")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(shipperLocation);
        String shipperId = shipperLocation.substring(shipperLocation.lastIndexOf('/') + 1);

        var bookingLocation = mockMvc.perform(post("/api/bookings")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"shipperId\": \"%s\", \"cargoType\": \"GENERAL_CARGO\", \"weightKg\": 500.0, " +
                                "\"quantity\": 1, \"originLocation\": \"JPTYO\", \"destinationLocation\": \"SGSIN\", " +
                                "\"requestedPickupDate\": \"2026-06-01\", \"requestedDeliveryDate\": \"2026-06-30\"}", shipperId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(bookingLocation);
        return bookingLocation.substring(bookingLocation.lastIndexOf('/') + 1);
    }

    @Test
    @DisplayName("E1: 予約 ID で経路設計条件を取得できる")
    void e1_予約IDで経路設計条件を取得できる() throws Exception {
        mockMvc.perform(get("/api/v1/routings/design-condition")
                        .session(session)
                        .param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.originLocode").value("JPTYO"))
                .andExpect(jsonPath("$.destinationLocode").value("SGSIN"))
                .andExpect(jsonPath("$.requestedArrivalDate").value("2026-06-30"))
                .andExpect(jsonPath("$.cargoType").value("GENERAL"))
                .andExpect(jsonPath("$.complete").value(true));
    }

    @Test
    @DisplayName("E2: 存在しない予約 ID は 404 を返す")
    void e2_存在しない予約IDは404を返す() throws Exception {
        mockMvc.perform(get("/api/v1/routings/design-condition")
                        .session(session)
                        .param("bookingId", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
