package com.example.cargotracker.booking.interfaces.web;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.cargotracker.booking.infrastructure.brokers.BookingEventHandler;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US24 経路情報を予約に紐付ける E2E テスト。
 * POST /bookings/{id}/assign-route → 予約に経路が保存され BookingRouteAssignedEvent が発行される。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US24 経路情報を予約に紐付ける E2E テスト")
class US24E2ETest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private MockHttpSession session;
    private String bookingId;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        session = loginAsUser();
        bookingId = createTestBooking();

        logAppender = new ListAppender<>();
        logAppender.start();
        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BookingEventHandler.class);
        logger.addAppender(logAppender);
    }

    @AfterEach
    void cleanUp() {
        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BookingEventHandler.class);
        logger.detachAppender(logAppender);

        jdbcTemplate.execute("DELETE FROM booking_legs");
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

    private String createTestBooking() throws Exception {
        var shipperLocation = mockMvc.perform(post("/api/shippers")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "テスト荷主 US24",
                                  "email": "us24@example.com",
                                  "category": "INDIVIDUAL"
                                }
                                """)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertNotNull(shipperLocation, "荷主 Location ヘッダーが null です");
        String shipperId = shipperLocation.substring(shipperLocation.lastIndexOf('/') + 1);

        var bookingLocation = mockMvc.perform(post("/api/bookings")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shipperId": "%s",
                                  "cargoType": "GENERAL_CARGO",
                                  "weightKg": 1000.0,
                                  "quantity": 1,
                                  "originLocation": "JPTYO",
                                  "destinationLocation": "SGSIN",
                                  "requestedPickupDate": "2026-04-01",
                                  "requestedDeliveryDate": "2026-06-30"
                                }
                                """.formatted(shipperId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertNotNull(bookingLocation, "予約 Location ヘッダーが null です");
        return bookingLocation.substring(bookingLocation.lastIndexOf('/') + 1);
    }

    @Test
    @DisplayName("US24 AC2/AC3: 経路紐付けの操作ができ、保存された経路が予約詳細に表示される")
    void 経路紐付けが保存される() throws Exception {
        mockMvc.perform(post("/bookings/{id}/assign-route", bookingId)
                        .session(session)
                        .param("voyageNumber", "SG001")
                        .param("routePath", "JPTYO→SGSIN")
                        .param("estimatedArrival", "2026-06-15")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/bookings/*"));

        mockMvc.perform(get("/bookings/{id}", bookingId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SG001")))
                .andExpect(content().string(containsString("JPTYO→SGSIN")));
    }

    @Test
    @DisplayName("US24 AC4/AC5: 経路紐付け後に営業担当者・荷主への通知ログが記録される")
    void 経路確定通知ログが記録される() throws Exception {
        mockMvc.perform(post("/bookings/{id}/assign-route", bookingId)
                        .session(session)
                        .param("voyageNumber", "SG001")
                        .param("routePath", "JPTYO→SGSIN")
                        .param("estimatedArrival", "2026-06-15")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        var messages = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertThat(messages).anyMatch(m -> m.contains("経路確定イベントを受信しました"));
        assertThat(messages).anyMatch(m -> m.contains("営業担当者への経路確定通知を送信しました"));
        assertThat(messages).anyMatch(m -> m.contains("荷主への経路確定通知を送信しました"));
    }
}
