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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US16 輸送料金算出 E2E テスト。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US16 輸送料金算出 E2E テスト")
class US16E2ETest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private MockHttpSession session;
    private String confirmedBookingId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        session = loginAsAdmin();
        confirmedBookingId = createConfirmedBooking();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM freight_charges");
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
                        .content("{\"name\": \"テスト荷主 US16\", \"email\": \"test-us16@example.com\", \"category\": \"INDIVIDUAL\"}")
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

        return bookingId;
    }

    @Test
    @DisplayName("E17: 確定済み予約の輸送料金を算出し freight_charges に DRAFT で保存される")
    void e17_calculateFreightCharge_shouldPersistAsDraft() throws Exception {
        // 2. REST POST /api/v1/freight-charges で料金算出 → 201 Created
        var response = mockMvc.perform(post("/api/v1/freight-charges")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"bookingId\": \"%s\"}", confirmedBookingId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        String location = response.getHeader("Location");
        assertNotNull(location, "Location ヘッダーが返されること");
        String freightId = location.substring(location.lastIndexOf('/') + 1);
        assertNotNull(freightId);

        // 3. DB で freight_charges テーブルを検証する
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM freight_charges WHERE id = ?",
                String.class, freightId);
        assertThat(status).isEqualTo("DRAFT");

        BigDecimal baseAmount = jdbcTemplate.queryForObject(
                "SELECT base_amount FROM freight_charges WHERE id = ?",
                BigDecimal.class, freightId);
        // GENERAL_CARGO 100 kg × 1.0 JPY/kg = 100 JPY
        assertThat(baseAmount).isEqualByComparingTo(new BigDecimal("100"));

        String bookingId = jdbcTemplate.queryForObject(
                "SELECT booking_id FROM freight_charges WHERE id = ?",
                String.class, freightId);
        assertThat(bookingId).isEqualTo(confirmedBookingId);
    }

    @Test
    @DisplayName("E17: 算出された輸送料金の合計金額が正しく計算される")
    void e17_calculateFreightCharge_totalAmountIsCorrect() throws Exception {
        // REST で料金算出する
        var response = mockMvc.perform(post("/api/v1/freight-charges")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"bookingId\": \"%s\"}", confirmedBookingId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        String location = response.getHeader("Location");
        assertNotNull(location);
        String freightId = location.substring(location.lastIndexOf('/') + 1);

        // total_amount = base_amount + adjustment_amount (0) = 100
        BigDecimal totalAmount = jdbcTemplate.queryForObject(
                "SELECT total_amount FROM freight_charges WHERE id = ?",
                BigDecimal.class, freightId);
        assertThat(totalAmount).isEqualByComparingTo(new BigDecimal("100"));
    }
}
