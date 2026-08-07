package com.example.cargotracker.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.CargoRoutingStatus;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
 * 経路の確定と予約への紐付け（US09 / US11）。
 *
 * <p>受入基準に 1:1 で対応させる。
 *
 * <p><strong>本テストは BC の外（{@code scenario}）に置く。</strong> 操作は Routing の
 * 画面から行い、確かめるのは Booking の事実（貨物の経路状態と旅程）である。
 * どちらの BC 配下に置いても、テストが相手の型を参照して BC 分離
 * （ArchUnit ルール 4）を破る。<strong>ルールはテストにも等しく効く。</strong>
 *
 * <p>受け入れシナリオは<strong>複数の BC をまたぐのが仕事</strong>であり、
 * BC の内側に置く方が無理がある。
 */
@AutoConfigureMockMvc
@WithMockUser(username = "router", roles = "ROUTER")
class RouteConfirmationScenarioTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VoyageRepository voyageRepository;

    @Autowired
    private CargoRepository cargoRepository;

    @Autowired
    private Clock clock;

    private LocalDate 業務上の今日() {
        return LocalDate.now(clock);
    }

    private String 未来(int daysFromToday, int hour) {
        return 業務上の今日().plusDays(daysFromToday)
                .atTime(hour, 0).atZone(clock.getZone()).toInstant().toString();
    }

    private UUID 引き渡し済みの予約(String origin, String destination, String cargoType,
            BigDecimal weight) {
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
                shipperId, "SHP-%06d".formatted(seq), "confirm-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ROUTE_PROPOSED', 'NOT_ROUTED')
                """,
                bookingId, shipperId, cargoType, weight, origin, destination,
                業務上の今日().plusDays(60));
        return bookingId;
    }

    private String 航海を登録する(Set<RoutingCargoType> types, BigDecimal capacityKg,
            String... legs) {
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
                RoutingWeight.ofKilograms(capacityKg))));
        return number;
    }

    private void 候補を算出する(UUID bookingId) throws Exception {
        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    /** 受入基準（US09）: 最適な経路候補を 1 件選択でき、経路状態が確定になる。 */
    @Test
    void 候補を選んで確定できる() throws Exception {
        var bookingId = 引き渡し済みの予約("JPOSA", "USLAX", "GENERAL", new BigDecimal("1000"));
        String voyage = 航海を登録する(Set.of(RoutingCargoType.GENERAL),
                new BigDecimal("100000"), "JPOSA", 未来(5, 10), "USLAX", 未来(20, 6));
        候補を算出する(bookingId);

        mockMvc.perform(post("/bookings/{id}/route/selection", bookingId)
                        .param("voyageNumber", voyage).with(csrf()))
                .andExpect(redirectedUrl("/bookings/" + bookingId));

        var cargo = cargoRepository.findById(new BookingId(bookingId)).orElseThrow();
        assertThat(cargo.routingStatus()).isEqualTo(CargoRoutingStatus.ROUTED);
        assertThat(cargo.cargoItinerary().legs()).isNotEmpty();
    }

    /**
     * 受入基準（US11）: 紐付け後も<strong>予約状態は「経路提案済」のまま</strong>である。
     */
    @Test
    void 確定しても予約状態は経路提案済のまま() throws Exception {
        var bookingId = 引き渡し済みの予約("JPYOK", "DEHAM", "GENERAL", new BigDecimal("1000"));
        String voyage = 航海を登録する(Set.of(RoutingCargoType.GENERAL),
                new BigDecimal("100000"), "JPYOK", 未来(5, 10), "DEHAM", 未来(30, 6));
        候補を算出する(bookingId);

        mockMvc.perform(post("/bookings/{id}/route/selection", bookingId)
                .param("voyageNumber", voyage).with(csrf()));

        var status = jdbcTemplate.queryForObject(
                "SELECT booking_status FROM cargo WHERE booking_id = ?", String.class, bookingId);
        assertThat(status).isEqualTo("ROUTE_PROPOSED");
    }

    /**
     * <strong>乗る区間から降りる区間までだけを旅程にする。</strong>
     *
     * <p>この便は貨物の出発地（大阪）より前から走っている。航海の全区間を
     * そのまま旅程にすると、<strong>荷主が頼んでいない区間まで含む旅程</strong>に
     * なり、端点が予約と食い違う。
     */
    @Test
    void 途中から乗る便では乗降の区間だけが旅程になる() throws Exception {
        var bookingId = 引き渡し済みの予約("JPKIX", "USSEA", "GENERAL", new BigDecimal("1000"));
        String voyage = 航海を登録する(Set.of(RoutingCargoType.GENERAL),
                new BigDecimal("100000"),
                // 貨物が乗る前の区間
                "JPHKT", 未来(1, 10), "JPKIX", 未来(2, 6),
                "JPKIX", 未来(3, 10), "CNSHA", 未来(6, 6),
                "CNSHA", 未来(7, 10), "USSEA", 未来(25, 6));
        候補を算出する(bookingId);

        mockMvc.perform(post("/bookings/{id}/route/selection", bookingId)
                .param("voyageNumber", voyage).with(csrf()));

        var cargo = cargoRepository.findById(new BookingId(bookingId)).orElseThrow();
        assertThat(cargo.cargoItinerary().legs())
                .extracting(leg -> leg.loadLocation().unlocode())
                .containsExactly("JPKIX", "CNSHA");
    }

    /** <strong>満船の便は確定できない。</strong> 画面で無効にするだけでは通ってしまう。 */
    @Test
    void 満船の便は確定できない() throws Exception {
        var bookingId = 引き渡し済みの予約("JPNGO", "AUSYD", "GENERAL", new BigDecimal("1000"));
        // 容量ちょうどの便に、別の貨物をすでに割り当てておく
        String voyage = 航海を登録する(Set.of(RoutingCargoType.GENERAL),
                new BigDecimal("1000"), "JPNGO", 未来(5, 10), "AUSYD", 未来(20, 6));
        var other = 引き渡し済みの予約("JPNGO", "AUSYD", "GENERAL", new BigDecimal("1000"));
        候補を算出する(other);
        mockMvc.perform(post("/bookings/{id}/route/selection", other)
                .param("voyageNumber", voyage).with(csrf()));

        候補を算出する(bookingId);
        mockMvc.perform(post("/bookings/{id}/route/selection", bookingId)
                        .param("voyageNumber", voyage).with(csrf()))
                .andExpect(redirectedUrl("/bookings/" + bookingId + "/route"));

        var cargo = cargoRepository.findById(new BookingId(bookingId)).orElseThrow();
        assertThat(cargo.routingStatus()).isEqualTo(CargoRoutingStatus.NOT_ROUTED);
    }

    /** 運べない便は確定できない。 */
    @Test
    void 運べない便は確定できない() throws Exception {
        var bookingId = 引き渡し済みの予約("JPHKT", "TWKHH", "HAZARDOUS",
                new BigDecimal("1000"));
        String voyage = 航海を登録する(Set.of(RoutingCargoType.GENERAL),
                new BigDecimal("100000"), "JPHKT", 未来(5, 10), "TWKHH", 未来(8, 6));
        候補を算出する(bookingId);

        mockMvc.perform(post("/bookings/{id}/route/selection", bookingId)
                        .param("voyageNumber", voyage).with(csrf()))
                .andExpect(redirectedUrl("/bookings/" + bookingId + "/route"));

        var cargo = cargoRepository.findById(new BookingId(bookingId)).orElseThrow();
        assertThat(cargo.routingStatus()).isEqualTo(CargoRoutingStatus.NOT_ROUTED);
    }

    /** 確定した経路が<strong>予約詳細に出る</strong>（受入基準: 確定経路と予約番号を確認できる）。 */
    @Test
    void 確定した経路が予約詳細に表示される() throws Exception {
        var bookingId = 引き渡し済みの予約("JPTYO", "USNYC", "GENERAL", new BigDecimal("1000"));
        String voyage = 航海を登録する(Set.of(RoutingCargoType.GENERAL),
                new BigDecimal("100000"), "JPTYO", 未来(5, 10), "USNYC", 未来(35, 6));
        候補を算出する(bookingId);
        mockMvc.perform(post("/bookings/{id}/route/selection", bookingId)
                .param("voyageNumber", voyage).with(csrf()));

        mockMvc.perform(get("/bookings/{id}", bookingId))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(voyage)))
                .andExpect(content().string(Matchers.containsString("割り当て済")));
    }

    /** 経路設計者以外は確定できない。 */
    @Test
    @WithMockUser(username = "sales", roles = "SALES")
    void 営業担当者は確定できない() throws Exception {
        var bookingId = 引き渡し済みの予約("JPKOB", "KRPUS", "GENERAL", new BigDecimal("1000"));

        mockMvc.perform(post("/bookings/{id}/route/selection", bookingId)
                        .param("voyageNumber", "V001").with(csrf()))
                .andExpect(status().isForbidden());
    }
}
