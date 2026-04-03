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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * US14/US15 例外処理フロー E2E テスト。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US14/US15 例外処理フロー E2E テスト")
class US14US15ExceptionE2ETest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private MockHttpSession session;
    private String trackingNumber;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        session = loginAsUser();
        trackingNumber = createConfirmedBookingAndGetTrackingNumber();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM cargo_exceptions");
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

    private String createConfirmedBookingAndGetTrackingNumber() throws Exception {
        var shipperLocation = mockMvc.perform(post("/api/shippers")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"テスト荷主 US14-15\", \"email\": \"test-us14-15@example.com\", \"category\": \"INDIVIDUAL\"}")
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
        String bookingId = bookingLocation.substring(bookingLocation.lastIndexOf('/') + 1);

        mockMvc.perform(post("/api/bookings/" + bookingId + "/route")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voyageNumber\": \"V015\", \"routePath\": \"JPTYO -> SGSIN\", \"estimatedArrival\": \"2026-06-01\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        return jdbcTemplate.queryForObject(
                "SELECT tracking_number FROM tracking_numbers WHERE booking_id = ?",
                String.class, bookingId);
    }

    @Test
    @DisplayName("E15 遅延例外を記録すると cargo_exceptions に保存される")
    void E15_recordDelayException_shouldPersistToDatabase() throws Exception {
        assertNotNull(trackingNumber);

        mockMvc.perform(post("/exceptions/new")
                        .session(session)
                        .with(csrf())
                        .param("trackingNumber", trackingNumber)
                        .param("exceptionType", "DELAY")
                        .param("locationCode", "JPTYO")
                        .param("occurredAt", "2026-05-28T10:00")
                        .param("reason", "悪天候による港湾閉鎖")
                        .param("resolution", "代替船を手配し、到着予定を 2026-06-05 に更新")
                        .param("estimatedArrivalDate", "2026-06-05"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exceptions/new"))
                .andExpect(flash().attribute("successMessage", containsString("荷主への通知を手動で行ってください")));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cargo_exceptions WHERE tracking_number = ? AND exception_type = 'DELAY'",
                Integer.class, trackingNumber);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("E15 遅延例外の到着予定日が DB に保存される")
    void E15_recordDelayException_shouldSaveEstimatedArrivalDate() throws Exception {
        assertNotNull(trackingNumber);

        mockMvc.perform(post("/exceptions/new")
                        .session(session)
                        .with(csrf())
                        .param("trackingNumber", trackingNumber)
                        .param("exceptionType", "DELAY")
                        .param("locationCode", "JPTYO")
                        .param("occurredAt", "2026-05-28T10:00")
                        .param("reason", "悪天候")
                        .param("resolution", "代替船を手配")
                        .param("estimatedArrivalDate", "2026-06-05"))
                .andExpect(status().is3xxRedirection());

        String savedDate = jdbcTemplate.queryForObject(
                "SELECT estimated_arrival_date::text FROM cargo_exceptions WHERE tracking_number = ? AND exception_type = 'DELAY'",
                String.class, trackingNumber);
        assertThat(savedDate).isEqualTo("2026-06-05");
    }

    @Test
    @DisplayName("E16 紛失例外を記録すると urgent フラグが true で保存される")
    void E16_recordLossException_shouldSetUrgentFlag() throws Exception {
        assertNotNull(trackingNumber);

        mockMvc.perform(post("/exceptions/new")
                        .session(session)
                        .with(csrf())
                        .param("trackingNumber", trackingNumber)
                        .param("exceptionType", "LOSS")
                        .param("locationCode", "SGSIN")
                        .param("occurredAt", "2026-05-31T08:00")
                        .param("reason", "保管中に紛失")
                        .param("resolution", "調査を開始"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exceptions/new"))
                .andExpect(flash().attribute("successMessage", containsString("管理担当者への通知を手動で行ってください")));

        Boolean urgent = jdbcTemplate.queryForObject(
                "SELECT urgent FROM cargo_exceptions WHERE tracking_number = ? AND exception_type = 'LOSS'",
                Boolean.class, trackingNumber);
        assertThat(urgent).isTrue();
    }

    @Test
    @DisplayName("E16 破損例外を記録すると urgent フラグが false で保存される")
    void E16_recordDamageException_shouldNotSetUrgentFlag() throws Exception {
        assertNotNull(trackingNumber);

        mockMvc.perform(post("/exceptions/new")
                        .session(session)
                        .with(csrf())
                        .param("trackingNumber", trackingNumber)
                        .param("exceptionType", "DAMAGE")
                        .param("locationCode", "USNYC")
                        .param("occurredAt", "2026-05-30T14:00")
                        .param("reason", "積み降ろし中に破損")
                        .param("resolution", "補償手続きを開始"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/exceptions/new"))
                .andExpect(flash().attribute("successMessage", containsString("荷主への通知を手動で行ってください")));

        Boolean urgent = jdbcTemplate.queryForObject(
                "SELECT urgent FROM cargo_exceptions WHERE tracking_number = ? AND exception_type = 'DAMAGE'",
                Boolean.class, trackingNumber);
        assertThat(urgent).isFalse();
    }
}
