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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US18 精算書発行 E2E テスト。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US18 精算書発行 E2E テスト")
class US18E2ETest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private MockHttpSession session;
    private String confirmedBookingId;
    private String confirmedFreightChargeId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        session = loginAsAdmin();
        confirmedBookingId = createConfirmedBooking();

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
        confirmedFreightChargeId = fcLocation.substring(fcLocation.lastIndexOf('/') + 1);

        // 輸送料金を確定する（DRAFT → CONFIRMED）
        mockMvc.perform(post("/api/v1/freight-charges/" + confirmedFreightChargeId + "/confirm")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());
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
     * REST API 経由で確定済み予約を作成して booking_id を返す。
     * GENERAL_CARGO / 100 kg の予約を CONFIRMED 状態にする。
     */
    private String createConfirmedBooking() throws Exception {
        // 1. 荷主を登録する
        var shipperLocation = mockMvc.perform(post("/api/shippers")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"テスト荷主 US18\", \"email\": \"test-us18@example.com\", \"category\": \"INDIVIDUAL\"}")
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
                                "\"receiveConfirmationCode\": \"RC-E2E-US18-001\"}", bookingId))
                        .with(csrf()))
                .andExpect(status().isCreated());

        return bookingId;
    }

    @Test
    @DisplayName("E19: 確定済み輸送料金から精算書を発行すると PENDING で保存される")
    void e19_確定済み輸送料金から精算書を発行するとPENDINGで保存される() throws Exception {
        // POST /api/v1/invoices
        var invoiceResponse = mockMvc.perform(post("/api/v1/invoices")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"bookingId\": \"%s\", \"freightChargeId\": \"%s\"}",
                                confirmedBookingId, confirmedFreightChargeId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        String invoiceLocation = invoiceResponse.getHeader("Location");
        assertNotNull(invoiceLocation);
        String invoiceId = invoiceLocation.substring(invoiceLocation.lastIndexOf('/') + 1);

        // DB 検証: payment_status が PENDING
        String paymentStatus = jdbcTemplate.queryForObject(
                "SELECT payment_status FROM invoices WHERE id = ?",
                String.class, invoiceId);
        assertThat(paymentStatus).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("E19: 支払い確認で支払い状態が CONFIRMED になる")
    void e19_支払い確認で支払い状態がCONFIRMEDになる() throws Exception {
        // 精算書を発行する
        var invoiceResponse = mockMvc.perform(post("/api/v1/invoices")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"bookingId\": \"%s\", \"freightChargeId\": \"%s\"}",
                                confirmedBookingId, confirmedFreightChargeId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        String invoiceLocation = invoiceResponse.getHeader("Location");
        assertNotNull(invoiceLocation);
        String invoiceId = invoiceLocation.substring(invoiceLocation.lastIndexOf('/') + 1);

        // PUT /api/v1/invoices/{id}/confirm-payment
        mockMvc.perform(put("/api/v1/invoices/" + invoiceId + "/confirm-payment")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        // DB 検証: payment_status が CONFIRMED
        String paymentStatus = jdbcTemplate.queryForObject(
                "SELECT payment_status FROM invoices WHERE id = ?",
                String.class, invoiceId);
        assertThat(paymentStatus).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("E19: DRAFT 状態の輸送料金では精算書を発行できない（409）")
    void e19_DRAFT状態の輸送料金では精算書を発行できない() throws Exception {
        // 別の予約を作成し、料金算出のみ行う（confirm しない → DRAFT のまま）
        var draftShipperLocation = mockMvc.perform(post("/api/shippers")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"テスト荷主 US18 DRAFT\", \"email\": \"test-us18-draft@example.com\", \"category\": \"INDIVIDUAL\"}")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(draftShipperLocation);
        String draftShipperId = draftShipperLocation.substring(draftShipperLocation.lastIndexOf('/') + 1);

        var draftBookingLocation = mockMvc.perform(post("/api/bookings")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"shipperId\": \"%s\", \"cargoType\": \"GENERAL_CARGO\", \"weightKg\": 100.0, " +
                                "\"quantity\": 1, \"originLocation\": \"JPTYO\", \"destinationLocation\": \"SGSIN\", " +
                                "\"requestedPickupDate\": \"2026-04-01\", \"requestedDeliveryDate\": \"2026-06-01\"}", draftShipperId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(draftBookingLocation);
        String draftBookingId = draftBookingLocation.substring(draftBookingLocation.lastIndexOf('/') + 1);

        mockMvc.perform(post("/api/bookings/" + draftBookingId + "/route")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voyageNumber\": \"V015\", \"routePath\": \"JPTYO -> SGSIN\", \"estimatedArrival\": \"2026-06-01\"}")
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/" + draftBookingId + "/confirm")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/handling-events")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"bookingId\": \"%s\", \"eventType\": \"RECEIVE\", \"locationCode\": \"SGSIN\", " +
                                "\"completionTime\": \"2026-05-31T10:00:00\", \"memo\": \"E2E テスト用引取\", " +
                                "\"receiveConfirmationCode\": \"RC-E2E-US18-002\"}", draftBookingId))
                        .with(csrf()))
                .andExpect(status().isCreated());

        // DRAFT 状態の輸送料金を算出する（confirm しない）
        var draftFcResponse = mockMvc.perform(post("/api/v1/freight-charges")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"bookingId\": \"%s\"}", draftBookingId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        String draftFcLocation = draftFcResponse.getHeader("Location");
        assertNotNull(draftFcLocation);
        String draftFreightChargeId = draftFcLocation.substring(draftFcLocation.lastIndexOf('/') + 1);

        // DRAFT 状態で精算書発行を試みる → 409 Conflict
        mockMvc.perform(post("/api/v1/invoices")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"bookingId\": \"%s\", \"freightChargeId\": \"%s\"}",
                                draftBookingId, draftFreightChargeId))
                        .with(csrf()))
                .andExpect(status().isConflict());
    }
}
