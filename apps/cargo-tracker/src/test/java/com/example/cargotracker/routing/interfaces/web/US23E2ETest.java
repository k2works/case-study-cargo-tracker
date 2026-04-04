package com.example.cargotracker.routing.interfaces.web;

import com.example.cargotracker.routing.application.internal.queryservices.RouteSearchService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US23 経路条件を調整して再算出する E2E テスト。
 * 候補なし時に再検索フォームと「営業担当者に条件交渉を依頼」ボタンが表示されることを検証する。
 * RouteSearchService をモック化して候補なし状態を強制する。
 */
@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin",
        "app.seed.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("US23 経路条件を調整して再算出する E2E テスト")
class US23E2ETest extends PostgreSQLIntegrationTestBase {

    @MockitoBean
    private RouteSearchService routeSearchService;

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
        when(routeSearchService.searchByCondition(any())).thenReturn(List.of());
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
                                  "name": "テスト荷主 US23",
                                  "email": "us23@example.com",
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
    @DisplayName("US23 AC1: 現在の制約条件（出発地・目的地・希望着日・貨物種別）を確認できる")
    void 検索条件が表示される() throws Exception {
        mockMvc.perform(get("/routings/search")
                        .session(session)
                        .param("bookingId", bookingId)
                        .param("originLocode", "JPTYO")
                        .param("destinationLocode", "SGSIN")
                        .param("requestedArrivalDate", "2026-04-30")
                        .param("cargoType", "GENERAL")
                        .param("weightKg", "1000.0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("JPTYO")))
                .andExpect(content().string(containsString("SGSIN")))
                .andExpect(content().string(containsString("検索条件")));
    }

    @Test
    @DisplayName("US23 AC2: 条件を変更して再検索できる（再検索フォームが表示される）")
    void 再検索フォームが表示される() throws Exception {
        mockMvc.perform(get("/routings/search")
                        .session(session)
                        .param("bookingId", bookingId)
                        .param("originLocode", "JPTYO")
                        .param("destinationLocode", "SGSIN")
                        .param("requestedArrivalDate", "2026-04-30")
                        .param("cargoType", "GENERAL")
                        .param("weightKg", "1000.0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("条件を変更して再検索")))
                .andExpect(content().string(containsString("希望着日")))
                .andExpect(content().string(containsString("再検索")));
    }

    @Test
    @DisplayName("US23 AC4: 候補なし時に営業担当者に交渉を依頼するリンクが表示される")
    void 営業担当者交渉リンクが表示される() throws Exception {
        mockMvc.perform(get("/routings/search")
                        .session(session)
                        .param("bookingId", bookingId)
                        .param("originLocode", "JPTYO")
                        .param("destinationLocode", "SGSIN")
                        .param("requestedArrivalDate", "2026-04-30")
                        .param("cargoType", "GENERAL")
                        .param("weightKg", "1000.0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("営業担当者に条件交渉を依頼")))
                .andExpect(content().string(containsString("それでも候補が見つからない場合")));
    }
}
