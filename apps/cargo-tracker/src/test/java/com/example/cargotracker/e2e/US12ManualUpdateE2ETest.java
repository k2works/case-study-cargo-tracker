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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * US12 手動更新フロー E2E テスト。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US12 手動更新フロー E2E テスト")
class US12ManualUpdateE2ETest extends PostgreSQLIntegrationTestBase {

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
        session = loginAsUser();
        bookingId = createConfirmedBooking();
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
                        .content("{\"name\": \"テスト荷主 US12\", \"email\": \"test-us12@example.com\", \"category\": \"INDIVIDUAL\"}")
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
                        .content("{\"voyageNumber\": \"V002\", \"routePath\": \"JPTYO -> SGSIN\", \"estimatedArrival\": \"2026-06-01\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/" + id + "/confirm")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        return id;
    }

    @Test
    @DisplayName("E13 メモ付きで手動更新を記録できる")
    void E13_recordManualUpdate_shouldSucceedWithMemo() throws Exception {
        mockMvc.perform(post("/handling/manual-update")
                        .session(session)
                        .with(csrf())
                        .param("bookingId", bookingId)
                        .param("eventType", "MANUAL_UPDATE")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2026-05-15T10:00")
                        .param("memo", "台風のため一時保管"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/handling?bookingId=*"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("E13 メモなしで手動更新を記録しようとするとエラーになる")
    void E13_recordManualUpdate_withoutMemo_shouldShowError() throws Exception {
        mockMvc.perform(post("/handling/manual-update")
                        .session(session)
                        .with(csrf())
                        .param("bookingId", bookingId)
                        .param("eventType", "MANUAL_UPDATE")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2026-05-15T10:00")
                        .param("memo", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/manual-update"))
                .andExpect(model().attributeHasFieldErrors("form", "memo"));
    }

    @Test
    @DisplayName("E13 手動更新フォームにアクセスできる")
    void E13_manualUpdateForm_shouldBeAccessible() throws Exception {
        mockMvc.perform(get("/handling/manual-update")
                        .session(session)
                        .param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("手動更新記録")));
    }

    @Test
    @DisplayName("US12 未認証ユーザーは手動更新ページにアクセスすると /login にリダイレクトされる")
    void US12_unauthenticated_access_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/handling/manual-update"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }
}
