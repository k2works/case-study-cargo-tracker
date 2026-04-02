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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ルート検索フローの E2E テスト。
 * Spring Security（フォームログイン）を通したハッピーパスと未認証アクセスを検証する。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("ルート検索フロー E2E テスト")
class RoutingWebE2ETest extends PostgreSQLIntegrationTestBase {

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
        // 荷主を作成して Location ヘッダーから ID を取得
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

        // 予約を作成して Location ヘッダーから ID を取得
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

    // ── 未認証アクセス ─────────────────────────────────────────────────────

    @Test
    @DisplayName("未認証でルート検索にアクセスするとログイン画面にリダイレクトされる")
    void 未認証_ルート検索はログインへリダイレクト() throws Exception {
        mockMvc.perform(get("/routings/search").param("bookingId", bookingId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ── ログイン → ルート検索 ハッピーパス ──────────────────────────────────

    @Test
    @DisplayName("ログイン後に予約 ID でルート候補一覧を表示できる")
    void ルート候補一覧が表示される() throws Exception {
        assertNotNull(bookingId, "テスト用予約 ID が null です");

        mockMvc.perform(get("/routings/search")
                        .session(session)
                        .param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ルート候補")))
                .andExpect(content().string(containsString("所要日数")))
                .andExpect(content().string(containsString("概算料金")));
    }

    @Test
    @DisplayName("ルート検索ページには再検索フォームが表示される")
    void 再検索フォームが表示される() throws Exception {
        mockMvc.perform(get("/routings/search")
                        .session(session)
                        .param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("再検索")))
                .andExpect(content().string(containsString("希望着日")))
                .andExpect(content().string(containsString("貨物種別")));
    }
}
