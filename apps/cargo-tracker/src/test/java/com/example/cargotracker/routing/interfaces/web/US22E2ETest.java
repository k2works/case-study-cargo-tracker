package com.example.cargotracker.routing.interfaces.web;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US22 経路を選択・確定する E2E テスト。
 * #assignModal に区間詳細テーブルのプレースホルダーが含まれることを検証する。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US22 経路を選択・確定する E2E テスト")
class US22E2ETest extends PostgreSQLIntegrationTestBase {

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
        bookingId = createTestBooking();
    }

    @AfterEach
    void cleanUp() {
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
                                  "name": "テスト荷主",
                                  "email": "test@example.com",
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
                                  "requestedDeliveryDate": "2026-06-01"
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
    @DisplayName("US22 AC1: 経路候補一覧から 1 件を選択できる（割り当てボタンが表示される）")
    void 経路候補に割り当てボタンが表示される() throws Exception {
        mockMvc.perform(get("/routings/search")
                        .session(session)
                        .param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("この予約に割り当てる")))
                .andExpect(content().string(containsString("assignModal")));
    }

    @Test
    @DisplayName("US22 AC2: #assignModal に区間詳細テーブルのプレースホルダーが含まれる")
    void assignModalに区間詳細テーブルが含まれる() throws Exception {
        mockMvc.perform(get("/routings/search")
                        .session(session)
                        .param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("modal-legs-table")))
                .andExpect(content().string(containsString("区間詳細")))
                .andExpect(content().string(containsString("出発港")))
                .andExpect(content().string(containsString("到着港")));
    }

    @Test
    @DisplayName("US22 AC3: 割り当てボタンには data-voyage-number 属性が含まれる")
    void 割り当てボタンにdata属性が含まれる() throws Exception {
        mockMvc.perform(get("/routings/search")
                        .session(session)
                        .param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-voyage-number")))
                .andExpect(content().string(containsString("data-estimated-arrival")));
    }
}
