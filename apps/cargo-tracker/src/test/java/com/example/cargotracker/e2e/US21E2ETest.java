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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US21 経路候補算出 E2E テスト。
 *
 * <p>{@code test} プロファイルでは {@link StubRouteProviderAdapter} が有効となり、
 * 固定の 2 件（SG001: 14 日, SG002: 18 日）が返る。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US21 経路候補算出 E2E テスト")
class US21E2ETest extends PostgreSQLIntegrationTestBase {

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
        session = loginAsAdmin();
        bookingId = createBooking();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM bookings");
        jdbcTemplate.execute("DELETE FROM shippers");
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        return (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession();
    }

    private String createBooking() throws Exception {
        var shipperLocation = mockMvc.perform(post("/api/shippers")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"テスト荷主 US21\", \"email\": \"test-us21@example.com\", \"category\": \"INDIVIDUAL\"}")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(shipperLocation);
        String shipperId = shipperLocation.substring(shipperLocation.lastIndexOf('/') + 1);

        var bookingLocation = mockMvc.perform(post("/api/bookings")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"shipperId\": \"%s\", \"cargoType\": \"GENERAL_CARGO\", \"weightKg\": 100.0, " +
                                "\"quantity\": 1, \"originLocation\": \"JPTYO\", \"destinationLocation\": \"SGSIN\", " +
                                "\"requestedPickupDate\": \"2026-06-01\", \"requestedDeliveryDate\": \"2026-12-31\"}", shipperId))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertNotNull(bookingLocation);
        return bookingLocation.substring(bookingLocation.lastIndexOf('/') + 1);
    }

    @Test
    @DisplayName("E1: 予約 ID でルート候補 2 件が返る（スタブ）")
    void e1_予約IDでルート候補が返る() throws Exception {
        mockMvc.perform(get("/api/v1/routings/search")
                        .session(session)
                        .param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("E2: ルート候補は transitDays 昇順にソートされている")
    void e2_ルート候補はtransitDays昇順にソートされている() throws Exception {
        // Note: スタブデータが SG001（14 日）→ SG002（18 日）の固定昇順のため、
        // ソートロジックが無効でもこのテストは GREEN になる。
        // ソートの単体保証は RouteSearchServiceTest#searchByConditionは優先度ソートを適用する で行っている。
        // SG001: transitDays=14（先）, SG002: transitDays=18（後）
        mockMvc.perform(get("/api/v1/routings/search")
                        .session(session)
                        .param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].voyageNumber").value("SG001"))
                .andExpect(jsonPath("$[0].transitDays").value(14))
                .andExpect(jsonPath("$[1].voyageNumber").value("SG002"))
                .andExpect(jsonPath("$[1].transitDays").value(18));
    }

    @Test
    @DisplayName("E3: ルート候補には viaLocodes・estimatedPrice・estimatedArrival が含まれる")
    void e3_ルート候補に必要なフィールドが含まれる() throws Exception {
        mockMvc.perform(get("/api/v1/routings/search")
                        .session(session)
                        .param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].viaLocodes").isArray())
                .andExpect(jsonPath("$[0].estimatedPrice").isNumber())
                .andExpect(jsonPath("$[0].estimatedArrival").isString())
                .andExpect(jsonPath("$[0].supportedCargoTypes").isArray());
    }

    @Test
    @DisplayName("E4: 存在しない予約 ID は 404 を返す")
    void e4_存在しない予約IDは404を返す() throws Exception {
        mockMvc.perform(get("/api/v1/routings/search")
                        .session(session)
                        .param("bookingId", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
