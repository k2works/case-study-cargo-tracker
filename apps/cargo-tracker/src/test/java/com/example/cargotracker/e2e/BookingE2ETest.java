package com.example.cargotracker.e2e;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin"
})
@ActiveProfiles("test")
@DisplayName("Booking API E2E テスト")
class BookingE2ETest extends PostgreSQLIntegrationTestBase {

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
    @DisplayName("荷主を登録してから貨物予約を登録できる")
    void shouldRegisterBookingAfterRegisteringShipper() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession();

        String shipperId = registerShipper(session);
        String bookingId = registerBooking(session, shipperId);

        mockMvc.perform(get("/api/v1/bookings/{bookingId}", bookingId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId", is(bookingId)))
                .andExpect(jsonPath("$.shipperId", is(shipperId)))
                .andExpect(jsonPath("$.cargoType", is("GENERAL")))
                .andExpect(jsonPath("$.status", is("PRELIMINARY")));
    }

    @Test
    @DisplayName("予約登録フォームに required 属性が設定されている")
    void bookingNewForm_shouldHaveRequiredAttributes() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession();

        mockMvc.perform(get("/bookings/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("required")))
                .andExpect(content().string(containsString("aria-describedby")));
    }

    @Test
    @DisplayName("予約一覧でステータスバッジが日本語・色分けで表示される")
    void bookingIndex_shouldDisplayJapaneseStatusBadge() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession();

        String shipperId = registerShipper(session);
        registerBooking(session, shipperId);

        mockMvc.perform(get("/bookings").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("仮受付")))
                .andExpect(content().string(containsString("text-bg-primary")));
    }

    @Test
    @DisplayName("予約登録フォームで貨物種別が日本語表示される")
    void bookingNewForm_shouldDisplayJapaneseCargoTypes() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession();

        mockMvc.perform(get("/bookings/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("一般")))
                .andExpect(content().string(containsString("危険物")))
                .andExpect(content().string(containsString("冷凍・冷蔵")));
    }

    @Test
    @DisplayName("予約登録成功時にフラッシュメッセージがリダイレクト先に渡される")
    void bookingCreate_shouldSetFlashMessageOnSuccess() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession();

        String shipperId = registerShipper(session);

        mockMvc.perform(post("/bookings").session(session).with(csrf())
                        .param("shipperId", shipperId)
                        .param("cargoType", "GENERAL")
                        .param("weight", "10.5")
                        .param("originUnlocode", "JPTYO")
                        .param("destinationUnlocode", "NLRTM")
                        .param("arrivalDeadline", LocalDate.now().plusDays(21).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    @DisplayName("予約登録フォームに荷主ドロップダウンと UN/LOCODE ヘルプが表示される")
    void bookingNewForm_shouldHaveShipperDropdownAndUnlocodeHelp() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession();

        registerShipper(session);

        mockMvc.perform(get("/bookings/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<select")))
                .andExpect(content().string(containsString("E2E 荷主")))
                .andExpect(content().string(containsString("JPTYO")));
    }

    private String registerShipper(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/shippers")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "E2E 荷主",
                                  "email": "e2e-shipper@example.com",
                                  "phone": "090-1111-2222",
                                  "shipperType": "INDIVIDUAL"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        return result.getResponse().getContentAsString()
                .replaceFirst("^.*\"id\"\\s*:\\s*\"([^\"]+)\".*$", "$1");
    }

    private String registerBooking(MockHttpSession session, String shipperId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "shipperId": "%s",
                                  "cargoType": "GENERAL",
                                  "weight": 12.750,
                                  "originUnlocode": "JPTYO",
                                  "destinationUnlocode": "NLRTM",
                                  "arrivalDeadline": "%s"
                                }
                                """.formatted(shipperId, LocalDate.now().plusDays(21))))
                .andExpect(status().isCreated())
                .andReturn();

        return result.getResponse().getContentAsString()
                .replaceFirst("^.*\"bookingId\"\\s*:\\s*\"([^\"]+)\".*$", "$1");
    }
}
