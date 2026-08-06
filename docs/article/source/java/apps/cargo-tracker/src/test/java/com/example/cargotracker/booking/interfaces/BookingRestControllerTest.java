package com.example.cargotracker.booking.interfaces;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin"
})
@ActiveProfiles("test")
@DisplayName("Booking REST Controller 統合テスト")
class BookingRestControllerTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE cargo, shipper RESTART IDENTITY CASCADE");
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/bookings で有効な予約を登録すると 201 と bookingId が返る")
    void postBooking_shouldReturnCreated() throws Exception {
        String shipperId = insertShipper("SHP-1001");

        mockMvc.perform(post("/api/v1/bookings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBookingRequest(shipperId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").isNotEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/bookings でバリデーションエラーは 400 が返る")
    void postBooking_withValidationError_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shipperId": "",
                                  "cargoType": "GENERAL",
                                  "weight": 0,
                                  "originUnlocode": "JP",
                                  "destinationUnlocode": "USLAX",
                                  "arrivalDeadline": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/bookings で存在しない shipperId は 404 が返る")
    void postBooking_withUnknownShipper_shouldReturnNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBookingRequest(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("SHIPPER_NOT_FOUND")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/bookings/{bookingId} で登録済み予約が取得できる")
    void getBookingById_shouldReturnRegisteredBooking() throws Exception {
        String shipperId = insertShipper("SHP-1002");
        String bookingId = registerBooking(shipperId);

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId", is(bookingId)))
                .andExpect(jsonPath("$.shipperId", is(shipperId)))
                .andExpect(jsonPath("$.cargoType", is("GENERAL")))
                .andExpect(jsonPath("$.originUnlocode", is("JPTYO")))
                .andExpect(jsonPath("$.destinationUnlocode", is("USLAX")))
                .andExpect(jsonPath("$.status", is("PRELIMINARY")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/bookings で全件一覧が取得できる")
    void getBookings_shouldReturnAllBookings() throws Exception {
        String firstShipperId = insertShipper("SHP-1003");
        String secondShipperId = insertShipper("SHP-1004");
        registerBooking(firstShipperId);
        registerBooking(secondShipperId);

        mockMvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    private String registerBooking(String shipperId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBookingRequest(shipperId)))
                .andExpect(status().isCreated())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        return content.replaceFirst("^.*\"bookingId\"\\s*:\\s*\"([^\"]+)\".*$", "$1");
    }

    private String insertShipper(String shipperCode) {
        String shipperId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO shipper (id, shipper_code, shipper_type, name, email, phone)
                VALUES (CAST(? AS UUID), ?, 'INDIVIDUAL', ?, ?, ?)
                """,
                shipperId,
                shipperCode,
                "テスト荷主 " + shipperCode,
                shipperCode.toLowerCase() + "@example.com",
                "090-0000-0000"
        );
        return shipperId;
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/bookings/{bookingId} で shipperName が含まれる")
    void getBookingById_shouldIncludeShipperName() throws Exception {
        String shipperId = insertShipper("SHP-1005");
        String bookingId = registerBooking(shipperId);

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipperName", is("テスト荷主 SHP-1005")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/bookings で危険物貨物の予約を登録すると 201 が返る")
    void postHazardousBooking_shouldReturnCreated() throws Exception {
        String shipperId = insertShipper("SHP-2001");

        mockMvc.perform(post("/api/v1/bookings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(hazardousBookingRequest(shipperId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").isNotEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/bookings で冷凍貨物の予約を登録すると 201 が返る")
    void postRefrigeratedBooking_shouldReturnCreated() throws Exception {
        String shipperId = insertShipper("SHP-2002");

        mockMvc.perform(post("/api/v1/bookings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refrigeratedBookingRequest(shipperId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").isNotEmpty());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/bookings で HAZARDOUS タイプに hazardousClass なしは 400 が返る")
    void postHazardousBooking_missingDeclaration_shouldReturn400() throws Exception {
        String shipperId = insertShipper("SHP-2003");

        mockMvc.perform(post("/api/v1/bookings")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shipperId": "%s",
                                  "cargoType": "HAZARDOUS",
                                  "weight": 10.500,
                                  "originUnlocode": "JPTYO",
                                  "destinationUnlocode": "USLAX",
                                  "arrivalDeadline": "%s"
                                }
                                """.formatted(shipperId, LocalDate.now().plusDays(14))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/bookings/{bookingId}/confirm で予約確定すると 200 と CONFIRMED 状態が返る")
    void confirmBooking_shouldReturnConfirmedStatus() throws Exception {
        String shipperId = insertShipper("SHP-3001");
        String bookingId = registerBooking(shipperId);

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/confirm", bookingId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CONFIRMED")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/bookings/{bookingId}/confirm で存在しない予約は 404 が返る")
    void confirmBooking_unknownBooking_shouldReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/{bookingId}/confirm", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("BOOKING_NOT_FOUND")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/bookings/{bookingId}/confirm で確定済み予約を再確定すると 409 が返る")
    void confirmBooking_alreadyConfirmed_shouldReturn409() throws Exception {
        String shipperId = insertShipper("SHP-3002");
        String bookingId = registerBooking(shipperId);
        mockMvc.perform(post("/api/v1/bookings/{bookingId}/confirm", bookingId).with(csrf()));

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/confirm", bookingId)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_STATE_TRANSITION")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/bookings/{bookingId}/cancel で予約キャンセルすると 200 と CANCELLED 状態が返る")
    void cancelBooking_shouldReturnCancelledStatus() throws Exception {
        String shipperId = insertShipper("SHP-3003");
        String bookingId = registerBooking(shipperId);

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/cancel", bookingId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /api/v1/bookings/{bookingId}/cancel でキャンセル済み予約を再キャンセルすると 409 が返る")
    void cancelBooking_alreadyCancelled_shouldReturn409() throws Exception {
        String shipperId = insertShipper("SHP-3004");
        String bookingId = registerBooking(shipperId);
        mockMvc.perform(post("/api/v1/bookings/{bookingId}/cancel", bookingId).with(csrf()));

        mockMvc.perform(post("/api/v1/bookings/{bookingId}/cancel", bookingId)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("INVALID_STATE_TRANSITION")));
    }

    private String validBookingRequest(String shipperId) {
        return """
                {
                  "shipperId": "%s",
                  "cargoType": "GENERAL",
                  "weight": 10.500,
                  "originUnlocode": "JPTYO",
                  "destinationUnlocode": "USLAX",
                  "arrivalDeadline": "%s"
                }
                """.formatted(shipperId, LocalDate.now().plusDays(14));
    }

    private String hazardousBookingRequest(String shipperId) {
        return """
                {
                  "shipperId": "%s",
                  "cargoType": "HAZARDOUS",
                  "weight": 10.500,
                  "originUnlocode": "JPTYO",
                  "destinationUnlocode": "USLAX",
                  "arrivalDeadline": "%s",
                  "hazardousClass": "3",
                  "unNumber": "UN1203",
                  "properShippingName": "ガソリン"
                }
                """.formatted(shipperId, LocalDate.now().plusDays(14));
    }

    private String refrigeratedBookingRequest(String shipperId) {
        return """
                {
                  "shipperId": "%s",
                  "cargoType": "REFRIGERATED",
                  "weight": 10.500,
                  "originUnlocode": "JPTYO",
                  "destinationUnlocode": "USLAX",
                  "arrivalDeadline": "%s",
                  "minTemperature": -25,
                  "maxTemperature": -18,
                  "temperatureUnit": "CELSIUS"
                }
                """.formatted(shipperId, LocalDate.now().plusDays(14));
    }
}
