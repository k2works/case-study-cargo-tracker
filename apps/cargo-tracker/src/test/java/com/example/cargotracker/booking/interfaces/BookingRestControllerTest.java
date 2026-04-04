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
}
