package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** US04: 貨物予約を登録する。受け入れ基準に 1:1 で対応させる。 */
@AutoConfigureMockMvc
@WithMockUser(username = "sales", roles = "SALES")
// 到着期限の「当日」「過去」は現在日時に対する相対的な概念である。固定日で書くと
// **時間の経過とともにテストの意味が変わり、いずれ「過去の予約は登録できない」が
// すべての予約に当てはまってしまう。** ここでシステム時計を使うのは仕様そのものである。
// 時計に依存しない検証はユニットテスト（CargoTest）が担っている。
@SuppressWarnings("java:S8692")
class CargoBookingTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingQueryService queryService;

    private String shipperCode;

    /** 予約には荷主が要る。荷主の登録経路（US02）は別テストが担保する。 */
    @BeforeEach
    void 荷主を用意する() {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        shipperCode = "SHP-%06d".formatted(seq);
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '山田物産株式会社', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """,
                UUID.randomUUID(), shipperCode, "booking-%s@example.com".formatted(shipperCode));
    }

    private Map<String, String> form() {
        Map<String, String> values = new HashMap<>();
        values.put("shipperCode", shipperCode);
        values.put("origin", "JPOSA");
        values.put("destination", "USLAX");
        values.put("arrivalDeadline", LocalDate.now().plusDays(30).toString());
        values.put("cargoType", "GENERAL");
        values.put("weight", "1200.5");
        values.put("quantity", "10");
        values.put("dimensionLength", "120");
        values.put("dimensionWidth", "80");
        values.put("dimensionHeight", "100");
        values.put("description", "電子部品");
        return values;
    }

    private MockHttpServletRequestBuilder postForm(Map<String, String> values) {
        var req = post("/bookings").with(csrf());
        values.forEach(req::param);
        return req;
    }

    /** 受入基準: 荷主 ID を入力して既存荷主を選択できる／予約番号が発行され状態が仮予約になる。 */
    @Test
    void 予約を登録すると予約番号が発行され仮予約になる() throws Exception {
        var result = mockMvc.perform(postForm(form()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/bookings/*"))
                .andReturn();

        String bookingId = result.getResponse().getRedirectedUrl().replace("/bookings/", "");
        var view = queryService.findById(bookingId).orElseThrow();

        assertThat(view.bookingStatus()).isEqualTo("PRELIMINARY");
        assertThat(view.statusLabel()).isEqualTo("仮予約");
        assertThat(view.shipperCode()).isEqualTo(shipperCode);
    }

    /** 受入基準: 貨物種別・重量・寸法・個数・品名を入力できる。 */
    @Test
    void 貨物種別と重量と寸法と個数と品名が登録される() throws Exception {
        var result = mockMvc.perform(postForm(form())).andReturn();
        String bookingId = result.getResponse().getRedirectedUrl().replace("/bookings/", "");

        var view = queryService.findById(bookingId).orElseThrow();

        assertThat(view.cargoTypeLabel()).isEqualTo("一般貨物");
        assertThat(view.weight()).isEqualByComparingTo("1200.5");
        assertThat(view.dimensions()).isEqualTo("120 × 80 × 100 cm");
        assertThat(view.quantity()).isEqualTo(10);
        assertThat(view.description()).isEqualTo("電子部品");
    }

    /** 受入基準: 出発地と目的地が同じ予約は登録できない。 */
    @Test
    void 出発地と目的地が同じ予約は登録できない() throws Exception {
        Map<String, String> values = form();
        values.put("destination", "JPOSA");

        mockMvc.perform(postForm(values))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/form"))
                .andExpect(content().string(Matchers.containsString("出発地と目的地")));
    }

    /** 受入基準: 希望着日が過去の予約は登録できない。 */
    @Test
    void 希望着日が過去の予約は登録できない() throws Exception {
        Map<String, String> values = form();
        values.put("arrivalDeadline", LocalDate.now().minusDays(1).toString());

        mockMvc.perform(postForm(values))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/form"))
                .andExpect(content().string(Matchers.containsString("到着期限")));
    }

    /**
     * 境界値。**当日着は業務上ありふれており、これを弾くと受付ができなくなる。**
     */
    @Test
    void 希望着日が当日の予約は登録できる() throws Exception {
        Map<String, String> values = form();
        values.put("arrivalDeadline", LocalDate.now().toString());

        mockMvc.perform(postForm(values)).andExpect(status().is3xxRedirection());
    }

    @Test
    void 存在しない荷主コードでは登録できない() throws Exception {
        Map<String, String> values = form();
        values.put("shipperCode", "SHP-999999");

        mockMvc.perform(postForm(values))
                .andExpect(status().isOk())
                .andExpect(view().name("booking/form"))
                .andExpect(content().string(Matchers.containsString("該当する荷主がありません")));
    }

    /** 寸法は 3 辺すべてか、すべて未入力か。**入力途中のデータを寸法として保存しない。** */
    @Test
    void 寸法を一部だけ入力した予約は登録できない() throws Exception {
        Map<String, String> values = form();
        values.remove("dimensionWidth");
        values.remove("dimensionHeight");

        mockMvc.perform(postForm(values))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("寸法")));
    }

    @Test
    void 寸法と個数と品名は無くても登録できる() throws Exception {
        Map<String, String> values = form();
        values.remove("dimensionLength");
        values.remove("dimensionWidth");
        values.remove("dimensionHeight");
        values.remove("quantity");
        values.remove("description");

        mockMvc.perform(postForm(values)).andExpect(status().is3xxRedirection());
    }

    /** 受入基準: 仮予約の予約はキャンセルできる。 */
    @Test
    void 仮予約の予約をキャンセルできる() throws Exception {
        var result = mockMvc.perform(postForm(form())).andReturn();
        String bookingId = result.getResponse().getRedirectedUrl().replace("/bookings/", "");

        mockMvc.perform(post("/bookings/{id}/cancel", bookingId).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(queryService.findById(bookingId).orElseThrow().bookingStatus())
                .isEqualTo("CANCELLED");
    }

    /**
     * キャンセル済みの予約に再度キャンセルを送っても状態は変わらない。
     *
     * <p>**詳細画面にボタンが出ていなくても、リクエストは直接送れる。**
     * 画面で消すことと、受け付けないことは別である。
     */
    @Test
    void キャンセル済みの予約は再度キャンセルできない() throws Exception {
        var result = mockMvc.perform(postForm(form())).andReturn();
        String bookingId = result.getResponse().getRedirectedUrl().replace("/bookings/", "");
        mockMvc.perform(post("/bookings/{id}/cancel", bookingId).with(csrf()));

        mockMvc.perform(post("/bookings/{id}/cancel", bookingId).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(queryService.findById(bookingId).orElseThrow().bookingStatus())
                .isEqualTo("CANCELLED");
    }

    /** キャンセルできない状態の予約には、詳細画面にキャンセルボタンを出さない。 */
    @Test
    void キャンセル済みの予約詳細にはキャンセルボタンが出ない() throws Exception {
        var result = mockMvc.perform(postForm(form())).andReturn();
        String bookingId = result.getResponse().getRedirectedUrl().replace("/bookings/", "");
        mockMvc.perform(post("/bookings/{id}/cancel", bookingId).with(csrf()));

        mockMvc.perform(get("/bookings/{id}", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("/cancel"))));
    }

    @Test
    void 存在しない予約の詳細は404を返す() throws Exception {
        mockMvc.perform(get("/bookings/{id}", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    /** URL を直接編集しただけで 500 にしない。 */
    @Test
    void 予約IDの形式が不正でも500にならない() throws Exception {
        mockMvc.perform(get("/bookings/{id}", "not-a-uuid"))
                .andExpect(status().isNotFound());
    }
}
