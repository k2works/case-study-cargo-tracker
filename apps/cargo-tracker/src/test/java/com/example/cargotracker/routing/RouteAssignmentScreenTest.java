package com.example.cargotracker.routing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * 経路割り当て画面（US08）。
 *
 * <p>受入基準に 1:1 で対応させる。<strong>本 IT では候補の提示までであり、
 * 確定は US09（IT5）である。</strong>
 */
@AutoConfigureMockMvc
@WithMockUser(username = "router", roles = "ROUTER")
class RouteAssignmentScreenTest extends PostgreSQLIntegrationTestBase {

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

    /** 引き渡し済み（経路割り当て待ち）の予約を 1 件作る。 */
    private UUID 引き渡し済みの予約(String origin, String destination, LocalDate deadline,
            String cargoType) {
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
                shipperId, "SHP-%06d".formatted(seq), "route-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status)
                VALUES (?, ?, ?, 1000.000, ?, ?, ?, 'ROUTE_PROPOSED', 'NOT_ROUTED')
                """,
                bookingId, shipperId, cargoType, origin, destination, deadline);
        return bookingId;
    }

    private String 航海を登録する(Set<RoutingCargoType> types, String... legs) {
        String number = "V" + UUID.randomUUID().toString().substring(0, 8);
        List<CarrierMovement> movements = new java.util.ArrayList<>();
        for (int i = 0; i < legs.length; i += 4) {
            movements.add(CarrierMovement.of(
                    Location.of(legs[i]), Location.of(legs[i + 2]),
                    Instant.parse(legs[i + 1]), Instant.parse(legs[i + 3])));
        }
        voyageRepository.save(Voyage.register(new RegisterVoyageCommand(
                new VoyageNumber(number), new VesselName("さくら丸"),
                new CarrierName("日本海運"), Schedule.of(movements), types,
                RoutingWeight.ofKilograms(new java.math.BigDecimal("100000")))));
        return number;
    }

    /** 未来の時刻。テストが時間の経過で意味を変えないようにする。 */
    private String 未来(int daysFromToday, int hour) {
        return 業務上の今日().plusDays(daysFromToday)
                .atTime(hour, 0).atZone(業務のゾーン()).toInstant().toString();
    }

    /** 受入基準: 出発地・目的地・期限を入力として候補が算出される。 */
    @Test
    void 候補を算出すると画面に並ぶ() throws Exception {
        var bookingId = 引き渡し済みの予約("JPOSA", "USLAX",
                業務上の今日().plusDays(40), "GENERAL");
        String voyage = 航海を登録する(Set.of(RoutingCargoType.GENERAL),
                "JPOSA", 未来(5, 10), "USLAX", 未来(20, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()))
                .andExpect(redirectedUrl("/bookings/" + bookingId + "/route"));

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(voyage)));
    }

    /** 受入基準: 所要日数・経由港・費用・航海番号が表示される。 */
    @Test
    void 所要日数と経由港と費用が表示される() throws Exception {
        var bookingId = 引き渡し済みの予約("JPYOK", "DEHAM",
                業務上の今日().plusDays(60), "GENERAL");
        航海を登録する(Set.of(RoutingCargoType.GENERAL),
                "JPYOK", 未来(3, 10), "SGSIN", 未来(10, 6),
                "SGSIN", 未来(11, 10), "DEHAM", 未来(30, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(Matchers.containsString("SGSIN")))
                .andExpect(content().string(Matchers.containsString("日")))
                // 費用は概算であることを明示する（ADR-008）
                .andExpect(content().string(Matchers.containsString("概算")));
    }

    /** 受入基準: 期限内に到達可能な経路がない場合、その旨が通知される。 */
    @Test
    void 期限内に着ける候補が無ければその旨が出る() throws Exception {
        var bookingId = 引き渡し済みの予約("JPKIX", "CLVAP",
                業務上の今日().plusDays(5), "GENERAL");
        航海を登録する(Set.of(RoutingCargoType.GENERAL),
                "JPKIX", 未来(1, 10), "CLVAP", 未来(60, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(
                        Matchers.containsString("希望期限までに到着できる経路がありません")));
    }

    /** 候補ゼロは異常ではない。**次にできることを示す。** */
    @Test
    void 候補ゼロなら航路管理への導線が出る() throws Exception {
        var bookingId = 引き渡し済みの予約("MXZLO", "USSAV",
                業務上の今日().plusDays(40), "GENERAL");

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(
                        Matchers.containsString("条件に合う航路が見つかりませんでした")))
                .andExpect(content().string(Matchers.containsString("/voyages")));
    }

    /** 運べない便は選べないことが画面に出る（ビジネスルール 6）。 */
    @Test
    void 運べない便は理由つきで選べないと出る() throws Exception {
        var bookingId = 引き渡し済みの予約("JPNGO", "AUSYD",
                業務上の今日().plusDays(40), "HAZARDOUS");
        航海を登録する(Set.of(RoutingCargoType.GENERAL),
                "JPNGO", 未来(5, 10), "AUSYD", 未来(20, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(
                        Matchers.containsString("この便は危険物を扱えません")));
    }

    /**
     * <strong>冷凍・冷蔵でも同じ守りが働く</strong>（US05 受入基準 3 / IT12 の C12）。
     *
     * <p>危険物だけを確かめると、<strong>種別を 1 つしか見ない実装</strong>でも緑になる。
     * その実装だと、冷凍設備の無い便に冷凍貨物を積む経路が候補に並ぶ。
     */
    @Test
    void 冷凍を扱えない便も理由つきで選べないと出る() throws Exception {
        var bookingId = 引き渡し済みの予約("JPKOB", "CAVAN",
                業務上の今日().plusDays(40), "REFRIGERATED");
        航海を登録する(Set.of(RoutingCargoType.GENERAL),
                "JPKOB", 未来(5, 10), "CAVAN", 未来(20, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(
                        Matchers.containsString("この便は冷凍・冷蔵を扱えません")));
    }

    /**
     * <strong>画面で出さないだけでは守りにならない。</strong>
     *
     * <p>確定ボタンを出していなくても、<strong>URL を直接叩けば同じ POST が届く</strong>。
     * 受入基準 3 が守っているのは「候補に並ばないこと」ではなく、
     * <strong>運べない便に貨物が積まれないこと</strong>である。
     *
     * <p>拒んだこと（入口）だけでなく、<strong>予約に経路が付かないこと</strong>
     * （出口）まで見る。「エラーを出した」と「実行されなかった」は別である。
     */
    @Test
    void 運べない便はURLを直接叩いても確定できない() throws Exception {
        var bookingId = 引き渡し済みの予約("JPNGO", "AUMEL",
                業務上の今日().plusDays(40), "HAZARDOUS");
        String voyage = 航海を登録する(Set.of(RoutingCargoType.GENERAL),
                "JPNGO", 未来(5, 10), "AUMEL", 未来(20, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));

        mockMvc.perform(post("/bookings/{id}/route/selection", bookingId)
                        .param("voyageNumber", voyage).with(csrf()))
                .andExpect(redirectedUrl("/bookings/" + bookingId + "/route"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("flashError",
                                Matchers.containsString("危険物を扱えません")));

        // **実行されなかったこと**を出口で見る。区間が付いていれば経路は割り当たっている
        Integer legs = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM leg l
                  JOIN cargo c ON c.id = l.cargo_id
                 WHERE c.booking_id = ?
                """, Integer.class, bookingId);
        org.assertj.core.api.Assertions.assertThat(legs)
                .as("運べない便で確定できてはならない").isZero();
        String routingStatus = jdbcTemplate.queryForObject(
                "SELECT routing_status FROM cargo WHERE booking_id = ?", String.class, bookingId);
        org.assertj.core.api.Assertions.assertThat(routingStatus).isEqualTo("NOT_ROUTED");
    }

    /**
     * <strong>行き止まりにしない。</strong> 次にできることが画面に書いてある。
     *
     * <p>IT4 の時点では「確定は今後の提供です」と書いていた（確定は US09 / IT5）。
     * <strong>IT8 で US10（条件を変えて探し直す）が入ったため、
     * 「今後の提供」と書く対象がもう無い。</strong> 未提供の告知が残っていると、
     * 利用者は使える機能を使わない。
     */
    @Test
    void 次にできることが画面に出る() throws Exception {
        var bookingId = 引き渡し済みの予約("JPHKT", "TWKHH",
                業務上の今日().plusDays(40), "GENERAL");
        航海を登録する(Set.of(RoutingCargoType.GENERAL),
                "JPHKT", 未来(5, 10), "TWKHH", 未来(8, 6));

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(content().string(Matchers.containsString("この経路で確定")))
                // 条件を緩めて探し直せる（US10）。**「今後の提供」は残っていない**
                .andExpect(content().string(Matchers.containsString("探索条件")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("今後の提供"))))
                .andExpect(content().string(
                        Matchers.containsString("/bookings/" + bookingId)));
    }

    /** 経路割り当ての対象でない予約は 404。**URL 直打ちで 500 にしない。** */
    @Test
    void 対象でない予約は404になる() throws Exception {
        mockMvc.perform(get("/bookings/{id}/route", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    /** 予約 ID の形式が不正でも 500 にしない。 */
    @Test
    void 予約IDの形式が不正でも500にならない() throws Exception {
        mockMvc.perform(get("/bookings/{id}/route", "not-a-uuid"))
                .andExpect(status().isNotFound());
    }

    /** 経路設計者以外は開けない。 */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 営業担当者は開けない() throws Exception {
        var bookingId = 引き渡し済みの予約("JPTYO", "USNYC",
                業務上の今日().plusDays(40), "GENERAL");

        mockMvc.perform(get("/bookings/{id}/route", bookingId))
                .andExpect(status().isForbidden());
    }

    /** 経路設計者以外は算出も実行できない。**書き込みの入口も守る。** */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 営業担当者は算出を実行できない() throws Exception {
        var bookingId = 引き渡し済みの予約("JPKOB", "KRPUS",
                業務上の今日().plusDays(40), "GENERAL");

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
