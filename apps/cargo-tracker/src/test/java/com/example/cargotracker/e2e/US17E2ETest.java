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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US17 法人割引適用 E2E テスト。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US17 法人割引適用 E2E テスト")
class US17E2ETest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private MockHttpSession session;
    private String confirmedBookingId;
    private String freightChargeId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        session = loginAsAdmin();
        confirmedBookingId = createConfirmedBookingWithCorporateShipper();

        // 輸送料金を算出する
        var fcResponse = mockMvc.perform(post("/api/v1/freight-charges")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"bookingId\": \"%s\"}", confirmedBookingId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        String fcLocation = fcResponse.getHeader("Location");
        assertNotNull(fcLocation);
        freightChargeId = fcLocation.substring(fcLocation.lastIndexOf('/') + 1);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM invoices");
        jdbcTemplate.execute("DELETE FROM freight_charges");
        jdbcTemplate.execute("DELETE FROM handling_events");
        jdbcTemplate.execute("DELETE FROM tracking_numbers");
        jdbcTemplate.execute("DELETE FROM bookings");
        jdbcTemplate.execute("DELETE FROM shippers");
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession();
    }

    /**
     * 法人荷主（割引率 10%）で確定済み予約を作成する。
     */
    private String createConfirmedBookingWithCorporateShipper() throws Exception {
        // 1. 法人荷主を登録する
        var shipperLocation = mockMvc.perform(post("/api/shippers")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"テスト法人 US17\", \"email\": \"test-us17@example.com\", " +
                                "\"category\": \"CORPORATE\", \"contractNumber\": \"CN-E2E-001\", \"discountRate\": 10}")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(shipperLocation);
        String shipperId = shipperLocation.substring(shipperLocation.lastIndexOf('/') + 1);

        // 2. 予約を作成する（GENERAL_CARGO / 100 kg）
        var bookingLocation = mockMvc.perform(post("/api/bookings")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"shipperId\": \"%s\", \"cargoType\": \"GENERAL_CARGO\", \"weightKg\": 100.0, " +
                                "\"quantity\": 1, \"originLocation\": \"JPTYO\", \"destinationLocation\": \"SGSIN\", " +
                                "\"requestedPickupDate\": \"2026-04-01\", \"requestedDeliveryDate\": \"2026-06-01\"}", shipperId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(bookingLocation);
        String bookingId = bookingLocation.substring(bookingLocation.lastIndexOf('/') + 1);

        // 3. ルートを割り当てる
        mockMvc.perform(post("/api/bookings/" + bookingId + "/route")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voyageNumber\": \"V015\", \"routePath\": \"JPTYO -> SGSIN\", \"estimatedArrival\": \"2026-06-01\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        // 4. 予約を確定する（status → CONFIRMED）
        mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        // 5. RECEIVE 荷役イベントを登録する（料金算出の前提条件）
        mockMvc.perform(post("/api/v1/handling-events")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"bookingId\": \"%s\", \"eventType\": \"RECEIVE\", \"locationCode\": \"SGSIN\", " +
                                "\"completionTime\": \"2026-05-31T10:00:00\", \"memo\": \"E2E テスト用引取\", " +
                                "\"receiveConfirmationCode\": \"RC-E2E-US17-001\"}", bookingId))
                        .with(csrf()))
                .andExpect(status().isCreated());

        return bookingId;
    }

    /**
     * 個人荷主で確定済み予約を作成する。
     */
    private String createConfirmedBookingWithIndividualShipper() throws Exception {
        // 1. 個人荷主を登録する
        var shipperLocation = mockMvc.perform(post("/api/shippers")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"テスト個人 US17\", \"email\": \"test-individual-us17@example.com\", " +
                                "\"category\": \"INDIVIDUAL\"}")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(shipperLocation);
        String shipperId = shipperLocation.substring(shipperLocation.lastIndexOf('/') + 1);

        // 2. 予約を作成する（GENERAL_CARGO / 100 kg）
        var bookingLocation = mockMvc.perform(post("/api/bookings")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"shipperId\": \"%s\", \"cargoType\": \"GENERAL_CARGO\", \"weightKg\": 100.0, " +
                                "\"quantity\": 1, \"originLocation\": \"JPTYO\", \"destinationLocation\": \"SGSIN\", " +
                                "\"requestedPickupDate\": \"2026-04-01\", \"requestedDeliveryDate\": \"2026-06-01\"}", shipperId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(bookingLocation);
        String bookingId = bookingLocation.substring(bookingLocation.lastIndexOf('/') + 1);

        // 3. ルートを割り当てる
        mockMvc.perform(post("/api/bookings/" + bookingId + "/route")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voyageNumber\": \"V015\", \"routePath\": \"JPTYO -> SGSIN\", \"estimatedArrival\": \"2026-06-01\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        // 4. 予約を確定する
        mockMvc.perform(post("/api/bookings/" + bookingId + "/confirm")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        // 5. RECEIVE 荷役イベントを登録する
        mockMvc.perform(post("/api/v1/handling-events")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"bookingId\": \"%s\", \"eventType\": \"RECEIVE\", \"locationCode\": \"SGSIN\", " +
                                "\"completionTime\": \"2026-05-31T10:00:00\", \"memo\": \"E2E テスト用引取\", " +
                                "\"receiveConfirmationCode\": \"RC-E2E-US17-002\"}", bookingId))
                        .with(csrf()))
                .andExpect(status().isCreated());

        return bookingId;
    }

    @Test
    @DisplayName("E18: 法人割引を適用すると調整額がマイナスで更新される")
    void e18_法人割引を適用すると調整額がマイナスで更新される() throws Exception {
        // PUT /api/v1/freight-charges/{id}/apply-discount
        mockMvc.perform(put("/api/v1/freight-charges/" + freightChargeId + "/apply-discount")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"bookingId\": \"%s\"}", confirmedBookingId))
                        .with(csrf()))
                .andExpect(status().isOk());

        // DB 検証: adjustment_amount が -10 (100 × 10%)、total_amount が 90
        BigDecimal adjustmentAmount = jdbcTemplate.queryForObject(
                "SELECT adjustment_amount FROM freight_charges WHERE id = ?",
                BigDecimal.class, freightChargeId);
        assertThat(adjustmentAmount).isEqualByComparingTo(new BigDecimal("-10"));

        BigDecimal totalAmount = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM freight_charges WHERE id = ?",
                BigDecimal.class, freightChargeId);
        assertThat(totalAmount).isEqualByComparingTo(new BigDecimal("90"));
    }

    @Test
    @DisplayName("E18: 個人荷主では割引が適用されない（調整額0）")
    void e18_個人荷主では割引が適用されない() throws Exception {
        // 個人荷主で別途予約を作成し料金算出する
        String individualBookingId = createConfirmedBookingWithIndividualShipper();

        var fcResponse = mockMvc.perform(post("/api/v1/freight-charges")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"bookingId\": \"%s\"}", individualBookingId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        String fcLocation = fcResponse.getHeader("Location");
        assertNotNull(fcLocation);
        String individualFreightChargeId = fcLocation.substring(fcLocation.lastIndexOf('/') + 1);

        // apply-discount を実行する
        mockMvc.perform(put("/api/v1/freight-charges/" + individualFreightChargeId + "/apply-discount")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"bookingId\": \"%s\"}", individualBookingId))
                        .with(csrf()))
                .andExpect(status().isOk());

        // DB 検証: adjustment_amount が 0、total_amount が base_amount と同じ
        BigDecimal adjustmentAmount = jdbcTemplate.queryForObject(
                "SELECT adjustment_amount FROM freight_charges WHERE id = ?",
                BigDecimal.class, individualFreightChargeId);
        assertThat(adjustmentAmount).isEqualByComparingTo(BigDecimal.ZERO);

        BigDecimal totalAmount = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM freight_charges WHERE id = ?",
                BigDecimal.class, individualFreightChargeId);
        BigDecimal baseAmount = jdbcTemplate.queryForObject(
                "SELECT base_amount FROM freight_charges WHERE id = ?",
                BigDecimal.class, individualFreightChargeId);
        assertThat(totalAmount).isEqualByComparingTo(baseAmount);
    }
}
