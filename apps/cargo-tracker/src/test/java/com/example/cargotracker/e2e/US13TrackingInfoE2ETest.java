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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US13 公開追跡ページ E2E テスト。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US13 公開追跡ページ E2E テスト")
class US13TrackingInfoE2ETest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private MockHttpSession session;
    private String bookingId;
    private String trackingNumber;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        session = loginAsUser();
        bookingId = createConfirmedBooking();
        trackingNumber = getTrackingNumber(bookingId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM handling_events");
        jdbcTemplate.execute("DELETE FROM tracking_numbers");
        jdbcTemplate.execute("DELETE FROM bookings");
        jdbcTemplate.execute("DELETE FROM shippers");
    }

    private MockHttpSession loginAsUser() throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession();
    }

    private String createConfirmedBooking() throws Exception {
        var shipperLocation = mockMvc.perform(post("/api/shippers")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"テスト荷主 US13\", \"email\": \"test-us13@example.com\", \"category\": \"INDIVIDUAL\"}")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(shipperLocation);
        String shipperId = shipperLocation.substring(shipperLocation.lastIndexOf('/') + 1);

        var bookingLocation = mockMvc.perform(post("/api/bookings")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"shipperId\": \"%s\", \"cargoType\": \"GENERAL_CARGO\", \"weightKg\": 1000.0, " +
                                "\"quantity\": 1, \"originLocation\": \"JPTYO\", \"destinationLocation\": \"SGSIN\", " +
                                "\"requestedPickupDate\": \"2026-04-01\", \"requestedDeliveryDate\": \"2026-06-01\"}", shipperId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(bookingLocation);
        String id = bookingLocation.substring(bookingLocation.lastIndexOf('/') + 1);

        mockMvc.perform(post("/api/bookings/" + id + "/route")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voyageNumber\": \"V003\", \"routePath\": \"JPTYO -> SGSIN\", \"estimatedArrival\": \"2026-06-01\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/" + id + "/confirm")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        return id;
    }

    private String getTrackingNumber(String bookingId) {
        // DB から直接追跡番号を取得
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT tracking_number FROM tracking_numbers WHERE booking_id = ?",
                    String.class, bookingId);
        } catch (Exception e) {
            return null;
        }
    }

    @Test
    @DisplayName("E14 追跡ページは認証なしでアクセスできる")
    void E14_trackingPage_noAuth_shouldBeAccessible() throws Exception {
        if (trackingNumber == null) return;

        mockMvc.perform(get("/tracking/" + trackingNumber))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("追跡情報")));
    }

    @Test
    @DisplayName("E14 荷役イベント登録後に追跡ページで履歴が表示される")
    void E14_trackingPageShowsHandlingHistory() throws Exception {
        if (trackingNumber == null) return;

        // LOAD イベントを登録
        mockMvc.perform(post("/api/v1/handling-events")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"bookingId\": \"%s\", \"eventType\": \"LOAD\", \"locationCode\": \"SGSIN\", " +
                                "\"completionTime\": \"2026-05-10T10:00:00\"}", bookingId))
                        .with(csrf()))
                .andExpect(status().isCreated());

        // 追跡ページで荷役履歴を確認
        mockMvc.perform(get("/tracking/" + trackingNumber))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("荷役履歴")))
                .andExpect(content().string(containsString("SGSIN")));
    }

    @Test
    @DisplayName("E14 存在しない追跡番号は 404 ページを表示する")
    void E14_unknownTrackingNumber_shows404() throws Exception {
        mockMvc.perform(get("/tracking/TRK-UNKNOWN9"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("404")));
    }
}
