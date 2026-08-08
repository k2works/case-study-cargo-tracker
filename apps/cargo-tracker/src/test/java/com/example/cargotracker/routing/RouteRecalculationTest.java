package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.routing.domain.model.CarrierMovement;
import com.example.cargotracker.routing.domain.model.CarrierName;
import com.example.cargotracker.routing.domain.model.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.RoutingWeight;
import com.example.cargotracker.routing.domain.model.Schedule;
import com.example.cargotracker.routing.domain.model.VesselName;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageNumber;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * US10: 経路条件を調整して再算出する。受入基準に 1:1 で対応させる。
 *
 * <p><strong>候補ゼロは異常ではなく日常的に起きる</strong>（業務代表の指摘）。
 * 期限が厳しい、危険物対応港が限られる、といった理由で 1 件も出ないことは珍しくない。
 * 出ないまま行き止まりにすると、経路設計者は画面の外（電話・メール）で仕事をすることになる。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "router", roles = "ROUTER")
@DisplayName("US10 経路条件を調整して再算出する")
class RouteRecalculationTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VoyageRepository voyageRepository;

    @Autowired
    private Clock clock;

    /** 業務の暦の上の今日。**JVM 既定の now() を使うと CI（UTC）でだけずれる。** */
    private LocalDate 業務上の今日() {
        return LocalDate.now(clock);
    }

    private ZoneId 業務のゾーン() {
        return clock.getZone();
    }

    private UUID 引き渡し済みの予約(String origin, String destination, LocalDate deadline) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '山田物産株式会社', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """,
                shipperId, "SHP-%06d".formatted(seq), "recalc-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status)
                VALUES (?, ?, 'GENERAL', 1000.000, ?, ?, ?, 'ROUTE_PROPOSED', 'NOT_ROUTED')
                """,
                bookingId, shipperId, origin, destination, deadline);
        return bookingId;
    }

    private String 航海を登録する(String... legs) {
        String number = "V" + UUID.randomUUID().toString().substring(0, 8);
        List<CarrierMovement> movements = new java.util.ArrayList<>();
        for (int i = 0; i < legs.length; i += 4) {
            movements.add(CarrierMovement.of(
                    Location.of(legs[i]), Location.of(legs[i + 2]),
                    Instant.parse(legs[i + 1]), Instant.parse(legs[i + 3])));
        }
        voyageRepository.save(Voyage.register(new RegisterVoyageCommand(
                new VoyageNumber(number), new VesselName("さくら丸"),
                new CarrierName("日本海運"), Schedule.of(movements),
                Set.of(RoutingCargoType.GENERAL),
                RoutingWeight.ofKilograms(new java.math.BigDecimal("100000")))));
        return number;
    }

    private String 未来(int daysFromToday, int hour) {
        return 業務上の今日().plusDays(daysFromToday)
                .atTime(hour, 0).atZone(業務のゾーン()).toInstant().toString();
    }

    /** 受入基準: 現在の制約条件（期限・経由回数の上限）を確認できる。 */
    @Test
    void 現在の探索条件が画面に出る() throws Exception {
        var bookingId = 引き渡し済みの予約("JPOSA", "USLAX", 業務上の今日().plusDays(10));
        航海を登録する("JPOSA", 未来(2, 10), "USLAX", 未来(30, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("探索条件")))
                .andExpect(content().string(Matchers.containsString("経由回数の上限")));
    }

    /**
     * 受入基準: 条件を調整して再算出でき、新たな候補が提示される。
     *
     * <p><strong>本 IT の中心。</strong> 期限が厳しくて 1 件も出ない状態から、
     * 期限を延ばすと候補が現れる。
     */
    @Test
    void 期限を延ばすと候補が現れる() throws Exception {
        var bookingId = 引き渡し済みの予約("JPKIX", "CLVAP", 業務上の今日().plusDays(5));
        String voyage = 航海を登録する("JPKIX", 未来(1, 10), "CLVAP", 未来(9, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));
        // 期限（5 日後）に間に合わないため、期限を満たす候補は無い
        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(
                        Matchers.containsString("希望期限までに到着できる経路がありません")));

        // 期限を 7 日延ばして再算出する
        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId)
                        .param("extraDays", "7").with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(Matchers.containsString(voyage)))
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("希望期限までに到着できる経路がありません"))));
    }

    /** 経由回数の上限を緩めると、乗り継ぎの多い経路が候補に入る。 */
    @Test
    void 経由回数の上限を緩めると乗り継ぎの多い経路が出る() throws Exception {
        var bookingId = 引き渡し済みの予約("JPYOK", "DEHAM", 業務上の今日().plusDays(90));
        String voyage = 航海を登録する(
                "JPYOK", 未来(2, 10), "SGSIN", 未来(10, 6),
                "SGSIN", 未来(11, 10), "INNSA", 未来(20, 6),
                "INNSA", 未来(21, 10), "ITGOA", 未来(30, 6),
                "ITGOA", 未来(31, 10), "DEHAM", 未来(45, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));
        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(Matchers.not(Matchers.containsString(voyage))));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId)
                        .param("maxTransitCount", "5").with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(Matchers.containsString(voyage)));
    }

    /**
     * <strong>当初の期限は残す。</strong> 延ばした事実そのものが消えると、
     * 荷主に「何日延びたか」を伝えられない（US12）。
     */
    @Test
    void 延ばしても当初の期限が残る() throws Exception {
        LocalDate deadline = 業務上の今日().plusDays(5);
        var bookingId = 引き渡し済みの予約("JPHKT", "TWKHH", deadline);
        航海を登録する("JPHKT", 未来(1, 10), "TWKHH", 未来(9, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));
        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId)
                .param("extraDays", "7").with(csrf()));

        var row = jdbcTemplate.queryForMap(
                "SELECT arrival_deadline, original_arrival_deadline"
                        + " FROM booking_route_proposal WHERE booking_id = ?", bookingId);
        assertThat(((java.sql.Date) row.get("arrival_deadline")).toLocalDate())
                .isEqualTo(deadline.plusDays(7));
        assertThat(((java.sql.Date) row.get("original_arrival_deadline")).toLocalDate())
                .isEqualTo(deadline);

        // 画面にも「延ばした」ことが出る。**記録だけして見せないと誰も気づかない**
        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(Matchers.containsString("当初")));
    }

    /** 2 回続けて延ばしても、当初の期限は最初の 1 回目のものである。 */
    @Test
    void 二度延ばしても当初の期限は変わらない() throws Exception {
        LocalDate deadline = 業務上の今日().plusDays(5);
        var bookingId = 引き渡し済みの予約("JPNGO", "AUSYD", deadline);
        航海を登録する("JPNGO", 未来(1, 10), "AUSYD", 未来(9, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));
        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId)
                .param("extraDays", "3").with(csrf()));
        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId)
                .param("extraDays", "3").with(csrf()));

        var row = jdbcTemplate.queryForMap(
                "SELECT arrival_deadline, original_arrival_deadline"
                        + " FROM booking_route_proposal WHERE booking_id = ?", bookingId);
        assertThat(((java.sql.Date) row.get("original_arrival_deadline")).toLocalDate())
                .isEqualTo(deadline);
        assertThat(((java.sql.Date) row.get("arrival_deadline")).toLocalDate())
                .isEqualTo(deadline.plusDays(6));
    }

    /**
     * <strong>緩め方に上限を置く。</strong> 無制限に延ばせるなら、期限そのものが
     * 業務上の意味を失う。荷主の合意なしに 1 年先まで延ばせてはならない。
     */
    @Test
    void 上限を超える延長はできない() throws Exception {
        LocalDate deadline = 業務上の今日().plusDays(5);
        var bookingId = 引き渡し済みの予約("JPTYO", "USNYC", deadline);
        航海を登録する("JPTYO", 未来(1, 10), "USNYC", 未来(9, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));
        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId)
                        .param("extraDays", "400").with(csrf()))
                .andExpect(status().is3xxRedirection());

        var row = jdbcTemplate.queryForMap(
                "SELECT arrival_deadline FROM booking_route_proposal WHERE booking_id = ?",
                bookingId);
        assertThat(((java.sql.Date) row.get("arrival_deadline")).toLocalDate())
                .isEqualTo(deadline);

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(Matchers.containsString("延長できる日数の上限")));
    }

    /** 候補ゼロの空状態から、条件を緩める操作へ**その場で**進める（行き止まりにしない）。 */
    @Test
    void 候補ゼロの画面から条件を緩める操作へ進める() throws Exception {
        var bookingId = 引き渡し済みの予約("MXZLO", "USSAV", 業務上の今日().plusDays(40));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(
                        Matchers.containsString("条件に合う航路が見つかりませんでした")))
                // ワンクリックで緩められる。**利用者に条件を考えさせない**
                .andExpect(content().string(Matchers.containsString("+7 日")))
                .andExpect(content().string(Matchers.containsString("経由 +1")))
                // 「今後の提供です」は残らない
                .andExpect(content().string(
                        Matchers.not(Matchers.containsString("探し直す機能は今後の提供"))));
    }

    /** 経路設計者以外は再算出できない。 */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 営業担当者は再算出できない() throws Exception {
        mockMvc.perform(post("/bookings/{id}/route/proposals", UUID.randomUUID())
                        .param("extraDays", "7").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
