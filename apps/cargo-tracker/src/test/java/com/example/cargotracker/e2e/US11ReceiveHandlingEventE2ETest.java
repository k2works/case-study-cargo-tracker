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
 * US11 引取記録フロー E2E テスト。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US11 引取記録フロー E2E テスト")
class US11ReceiveHandlingEventE2ETest extends PostgreSQLIntegrationTestBase {

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
                        .content("{\"name\": \"テスト荷主\", \"email\": \"test-us11@example.com\", \"category\": \"INDIVIDUAL\"}")
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

        // ルートを割り当て
        mockMvc.perform(post("/api/bookings/" + id + "/route")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voyageNumber\": \"V001\", \"routePath\": \"JPTYO -> SGSIN\", \"estimatedArrival\": \"2026-06-01\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        // 予約を確定
        mockMvc.perform(post("/api/bookings/" + id + "/confirm")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        return id;
    }

    @Test
    @DisplayName("E12 引取イベントを記録できる")
    void E12_recordReceiveHandlingEvent_shouldSucceed() throws Exception {
        mockMvc.perform(post("/handling/receive")
                        .session(session)
                        .with(csrf())
                        .param("bookingId", bookingId)
                        .param("eventType", "RECEIVE")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2026-05-01T09:00")
                        .param("receiveConfirmationCode", "RC-E2E-001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/handling?bookingId=*"))
                .andExpect(flash().attribute("successMessage", containsString("引取済")));
    }

    @Test
    @DisplayName("E12 同一予約に再度 RECEIVE を登録するとエラーになる")
    void E12_duplicateReceive_shouldShowError() throws Exception {
        // 1 回目の RECEIVE 登録
        mockMvc.perform(post("/handling/receive")
                        .session(session)
                        .with(csrf())
                        .param("bookingId", bookingId)
                        .param("eventType", "RECEIVE")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2026-05-01T09:00")
                        .param("receiveConfirmationCode", "RC-E2E-001"))
                .andExpect(status().is3xxRedirection());

        // 2 回目の RECEIVE 登録 → エラー
        mockMvc.perform(post("/handling/receive")
                        .session(session)
                        .with(csrf())
                        .param("bookingId", bookingId)
                        .param("eventType", "RECEIVE")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2026-05-02T09:00")
                        .param("receiveConfirmationCode", "RC-E2E-002"))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/receive"))
                .andExpect(model().attributeHasFieldErrors("form", "bookingId"));
    }

    @Test
    @DisplayName("E12 引取フォームにアクセスできる")
    void E12_receiveForm_shouldBeAccessible() throws Exception {
        mockMvc.perform(get("/handling/receive")
                        .session(session)
                        .param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("引取記録")))
                .andExpect(content().string(containsString("引取確認コード")));
    }

    @Test
    @DisplayName("E12 引取確認コードなしでは引取記録できない")
    void E12_receiveWithoutConfirmationCode_shouldShowError() throws Exception {
        mockMvc.perform(post("/handling/receive")
                        .session(session)
                        .with(csrf())
                        .param("bookingId", bookingId)
                        .param("eventType", "RECEIVE")
                        .param("locationCode", "JPTYO")
                        .param("completionTime", "2026-05-01T09:00")
                        .param("receiveConfirmationCode", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("handling/receive"))
                .andExpect(model().attributeHasFieldErrors("form", "receiveConfirmationCode"));
    }

    @Test
    @DisplayName("US11 未認証ユーザーは引取記録ページにアクセスすると /login にリダイレクトされる")
    void US11_unauthenticated_access_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/handling/receive"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
    }
}
