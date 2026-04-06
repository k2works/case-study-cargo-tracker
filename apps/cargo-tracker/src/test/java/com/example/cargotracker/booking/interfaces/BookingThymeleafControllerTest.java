package com.example.cargotracker.booking.interfaces;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "spring.security.user.name=admin",
        "spring.security.user.password=admin"
})
@ActiveProfiles("test")
@DisplayName("Booking Thymeleaf Controller 統合テスト")
class BookingThymeleafControllerTest extends PostgreSQLIntegrationTestBase {

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
    @DisplayName("GET /bookings で一覧画面がレンダリングされる")
    void getBookings_shouldRenderIndexPage() throws Exception {
        mockMvc.perform(get("/bookings"))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/index"))
                .andExpect(content().string(containsString("予約管理")));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /bookings/new で登録フォームがレンダリングされる")
    void getNewBooking_shouldRenderFormPage() throws Exception {
        mockMvc.perform(get("/bookings/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/new"))
                .andExpect(content().string(containsString("予約登録")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /bookings で登録後に詳細画面へリダイレクトする")
    void postBooking_shouldRedirectToShowPage() throws Exception {
        String shipperId = insertShipper("SHP-WEB01");

        MvcResult result = mockMvc.perform(post("/bookings")
                        .with(csrf())
                        .param("shipperId", shipperId)
                        .param("cargoType", "GENERAL")
                        .param("weight", "9.500")
                        .param("originUnlocode", "JPTYO")
                        .param("destinationUnlocode", "USLAX")
                        .param("arrivalDeadline", LocalDate.now().plusDays(14).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/bookings/")))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/show"))
                .andExpect(content().string(containsString(shipperId)));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /bookings で HAZARDOUS タイプの危険物フォーム送信で詳細ページにリダイレクトする")
    void postHazardousBooking_shouldRedirectToShowPage() throws Exception {
        String shipperId = insertShipper("SHP-HAZ01");

        MvcResult result = mockMvc.perform(post("/bookings")
                        .with(csrf())
                        .param("shipperId", shipperId)
                        .param("cargoType", "HAZARDOUS")
                        .param("weight", "5.000")
                        .param("hazardousClass", "3")
                        .param("unNumber", "UN1203")
                        .param("properShippingName", "ガソリン")
                        .param("originUnlocode", "JPTYO")
                        .param("destinationUnlocode", "USLAX")
                        .param("arrivalDeadline", LocalDate.now().plusDays(14).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/bookings/")))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/show"))
                .andExpect(content().string(containsString("危険物クラス")))
                .andExpect(content().string(containsString("3")))
                .andExpect(content().string(containsString("UN1203")))
                .andExpect(content().string(containsString("ガソリン")));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /bookings で REFRIGERATED タイプの冷凍フォーム送信で詳細ページにリダイレクトする")
    void postRefrigeratedBooking_shouldRedirectToShowPage() throws Exception {
        String shipperId = insertShipper("SHP-REF01");

        MvcResult result = mockMvc.perform(post("/bookings")
                        .with(csrf())
                        .param("shipperId", shipperId)
                        .param("cargoType", "REFRIGERATED")
                        .param("weight", "12.000")
                        .param("minTemperature", "-25")
                        .param("maxTemperature", "-18")
                        .param("temperatureUnit", "CELSIUS")
                        .param("originUnlocode", "JPTYO")
                        .param("destinationUnlocode", "USLAX")
                        .param("arrivalDeadline", LocalDate.now().plusDays(14).toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/bookings/")))
                .andReturn();

        String location = result.getResponse().getHeader("Location");
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/show"))
                .andExpect(content().string(containsString("温度管理")))
                .andExpect(content().string(containsString("-25")))
                .andExpect(content().string(containsString("-18")))
                .andExpect(content().string(containsString("CELSIUS")));
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
                "画面テスト荷主 " + shipperCode,
                shipperCode.toLowerCase() + "@example.com",
                "090-0000-0000"
        );
        return shipperId;
    }
}
