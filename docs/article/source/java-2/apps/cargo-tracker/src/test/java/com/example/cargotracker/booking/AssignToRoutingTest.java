package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.shared.application.paging.PageRequest;
import com.example.cargotracker.support.LogCapture;
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

/**
 * US06: 予約情報を経路設計者に引き渡す。受け入れ基準に 1:1 で対応させる。
 *
 * <p><strong>通知は送らない。</strong> ADR-006 により外部連携は実装しない。
 * 引き渡した予約が経路設計者の作業入口に現れることが、業務上の「引き渡し」である。
 */
@AutoConfigureMockMvc
@SuppressWarnings("java:S8692")
class AssignToRoutingTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingQueryService queryService;

    /**
     * 業務日付を判断する時計。
     *
     * <p><strong>テストも同じ時計で「今日」を決める。</strong> JVM 既定の
     * タイムゾーンで {@code LocalDate.now()} を呼ぶと、CI（UTC）では
     * アプリの業務日付（Asia/Tokyo）と 1 日ずれる。
     */
    @Autowired
    private java.time.Clock clock;

    private String shipperCode;

    @BeforeEach
    void 荷主を用意する() {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        shipperCode = "SHP-%06d".formatted(seq);
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '引き渡し検証荷主', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """,
                UUID.randomUUID(), shipperCode, "assign-%s@example.com".formatted(shipperCode));
    }

    private Map<String, String> form() {
        Map<String, String> values = new HashMap<>();
        values.put("shipperCode", shipperCode);
        values.put("origin", "JPOSA");
        values.put("destination", "USLAX");
        values.put("arrivalDeadline", LocalDate.now(clock).plusDays(30).toString());
        values.put("cargoType", "GENERAL");
        values.put("weight", "1000");
        return values;
    }

    @WithMockUser(username = "sales", roles = "SALES")
    private String 予約する() throws Exception {
        var req = post("/bookings").with(csrf());
        form().forEach(req::param);
        return mockMvc.perform(req).andReturn().getResponse().getRedirectedUrl()
                .replace("/bookings/", "");
    }

    /** 受入基準: 引き渡すと状態が「経路提案済」に更新される。 */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 引き渡すと経路提案済になる() throws Exception {
        String bookingId = 予約する();

        mockMvc.perform(post("/bookings/{id}/assign-to-routing", bookingId).with(csrf()))
                .andExpect(status().is3xxRedirection());

        var view = queryService.findById(bookingId).orElseThrow();
        assertThat(view.status().booking()).isEqualTo("ROUTE_PROPOSED");
        assertThat(view.status().label()).isEqualTo("経路提案済");
    }

    /**
     * 受入基準: 引き渡した予約が経路設計者の作業入口に現れる。
     *
     * <p><strong>これが「通知」の代わりである。</strong> 経路設計者は通知を待つのではなく、
     * 自分の作業入口を見る。
     */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 引き渡した予約が経路割り当て待ちに現れる() throws Exception {
        String bookingId = 予約する();

        assertThat(待ち一覧の予約ID())
                .as("引き渡す前は待ち一覧に現れない")
                .doesNotContain(bookingId);

        mockMvc.perform(post("/bookings/{id}/assign-to-routing", bookingId).with(csrf()));

        assertThat(待ち一覧の予約ID()).contains(bookingId);
    }

    private java.util.List<String> 待ち一覧の予約ID() {
        return queryService.findAwaitingRouting(PageRequest.of(1)).items().stream()
                .map(v -> v.bookingId())
                .toList();
    }

    /** 受入基準: 引き渡し済みの予約は重ねて引き渡せない。 */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 引き渡し済みの予約は重ねて引き渡せない() throws Exception {
        String bookingId = 予約する();
        mockMvc.perform(post("/bookings/{id}/assign-to-routing", bookingId).with(csrf()));

        mockMvc.perform(post("/bookings/{id}/assign-to-routing", bookingId).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(queryService.findById(bookingId).orElseThrow().status().booking())
                .isEqualTo("ROUTE_PROPOSED");
    }

    /** 引き渡し済みの予約詳細には「引き渡す」ボタンが出ない。**二重に依頼が飛ばない。** */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 引き渡し済みの予約詳細には引き渡すボタンが出ない() throws Exception {
        String bookingId = 予約する();

        mockMvc.perform(get("/bookings/{id}", bookingId))
                .andExpect(content().string(Matchers.containsString("assign-to-routing")));

        mockMvc.perform(post("/bookings/{id}/assign-to-routing", bookingId).with(csrf()));

        mockMvc.perform(get("/bookings/{id}", bookingId))
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("assign-to-routing"))));
    }

    /** 受入基準: 誰がいつどの予約を引き渡したかが業務操作ログに記録される。 */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 引き渡しが誰の操作として記録される() throws Exception {
        String bookingId = 予約する();

        try (LogCapture capture = LogCapture.of("audit.booking")) {
            mockMvc.perform(post("/bookings/{id}/assign-to-routing", bookingId).with(csrf()));

            assertThat(capture.messages())
                    .anySatisfy(message -> assertThat(message)
                            .contains("経路設計者への引き渡し")
                            .contains(bookingId)
                            .contains("actor=sales"));
        }
    }

    /** 経路設計者は引き渡しの操作をできない（引き渡すのは営業担当者）。 */
    @Test
    @WithMockUser(username = "router", roles = "ROUTER")
    void 経路設計者は引き渡しの操作をできない() throws Exception {
        mockMvc.perform(post("/bookings/{id}/assign-to-routing", UUID.randomUUID().toString())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
