package com.example.cargotracker.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.BookingStatus;
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

/**
 * 予約確定 → 追跡番号発行 → 荷役記録（US13 / US14 / US15）。
 *
 * <p>受入基準に 1:1 で対応させる。<strong>BC をまたぐのが仕事</strong>であるため
 * {@code scenario} に置く（ArchUnit ルール 4 の除外対象）。
 *
 * <p><strong>状態を進めた直後に、同じ画面をもう一度開く</strong>（IT5 の Try T1）。
 * 到達性の抜けは 4 イテレーション連続で起きており、受入基準には現れない。
 */
@AutoConfigureMockMvc
class CargoLifecycleScenarioTest extends PostgreSQLIntegrationTestBase {

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

    private Instant 未来(int daysFromToday, int hour) {
        return 業務上の今日().plusDays(daysFromToday).atTime(hour, 0)
                .atZone(clock.getZone()).toInstant();
    }

    private UUID 引き渡し済みの予約(String origin, String destination) {
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
                shipperId, "SHP-%06d".formatted(seq), "lifecycle-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status)
                VALUES (?, ?, 'GENERAL', 1000, ?, ?, ?, 'ROUTE_PROPOSED', 'NOT_ROUTED')
                """,
                bookingId, shipperId, origin, destination, 業務上の今日().plusDays(60));
        return bookingId;
    }

    private String 航海を登録する(String origin, String destination, BigDecimal capacityKg) {
        String number = "V" + UUID.randomUUID().toString().substring(0, 8);
        voyageRepository.save(Voyage.register(new RegisterVoyageCommand(
                new VoyageNumber(number), new VesselName("さくら丸"), new CarrierName("日本海運"),
                Schedule.of(List.of(CarrierMovement.of(
                        Location.of(origin), Location.of(destination),
                        未来(5, 10), 未来(20, 6)))),
                Set.of(RoutingCargoType.GENERAL),
                RoutingWeight.ofKilograms(capacityKg))));
        return number;
    }

    /** 経路を確定済みの予約を用意する（確定の入口）。 */
    private UUID 経路確定済みの予約(String origin, String destination, BigDecimal capacityKg)
            throws Exception {
        var bookingId = 引き渡し済みの予約(origin, destination);
        String voyage = 航海を登録する(origin, destination, capacityKg);
        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId)
                .with(user("router").roles("ROUTER")).with(csrf()));
        mockMvc.perform(post("/bookings/{id}/route/selection", bookingId)
                .param("voyageNumber", voyage)
                .with(user("router").roles("ROUTER")).with(csrf()));
        return bookingId;
    }

    private String 追跡番号を発行する(UUID bookingId) throws Exception {
        mockMvc.perform(post("/bookings/{id}/confirm", bookingId)
                .with(user("sales").roles("SALES")).with(csrf()));
        mockMvc.perform(post("/bookings/{id}/tracking-number", bookingId)
                .with(user("tracker").roles("TRACKER")).with(csrf()));
        return cargoRepository.findById(new BookingId(bookingId)).orElseThrow()
                .trackingNumber().value();
    }

    /** 受入基準（US13）: 確定操作を行うと予約状態が「予約確定」に更新される。 */
    @Test
    void 経路が確定した予約を確定できる() throws Exception {
        var bookingId = 経路確定済みの予約("JPOSA", "USLAX", new BigDecimal("100000"));

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId)
                        .with(user("sales").roles("SALES")).with(csrf()))
                .andExpect(redirectedUrl("/bookings/" + bookingId));

        assertThat(cargoRepository.findById(new BookingId(bookingId)).orElseThrow()
                .bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    /**
     * <strong>経路が割り当てられていない予約は確定できない</strong>（遷移表 #4 の事前条件）。
     */
    @Test
    void 経路が割り当てられていない予約は確定できない() throws Exception {
        var bookingId = 引き渡し済みの予約("JPYOK", "DEHAM");

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId)
                        .with(user("sales").roles("SALES")).with(csrf()))
                .andExpect(flash().attribute("flashError", Matchers.containsString("経路")));

        assertThat(cargoRepository.findById(new BookingId(bookingId)).orElseThrow()
                .bookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
    }

    /**
     * <strong>算出のあとに満船になった便は確定できない</strong>（IT5 レビュー M3）。
     *
     * <p>候補の算出時に判定した値をそのまま信じると、算出から確定までの間に
     * 他の貨物が割り当てられた便を確定できてしまう。
     */
    @Test
    void 算出のあとに満船になった便は確定できない() throws Exception {
        String origin = "JPNGO";
        String destination = "AUSYD";
        // 容量 2000kg の便に、1000kg の貨物が 3 件を狙う
        String voyage = 航海を登録する(origin, destination, new BigDecimal("2000"));
        var late = 引き渡し済みの予約(origin, destination);
        var first = 引き渡し済みの予約(origin, destination);
        var second = 引き渡し済みの予約(origin, destination);

        // **先に算出だけしておく。** この時点では空きがある
        mockMvc.perform(post("/bookings/{id}/route/proposals", late)
                .with(user("router").roles("ROUTER")).with(csrf()));

        // その間に他の 2 件が確定し、便は満船になる
        for (UUID id : List.of(first, second)) {
            mockMvc.perform(post("/bookings/{id}/route/proposals", id)
                    .with(user("router").roles("ROUTER")).with(csrf()));
            mockMvc.perform(post("/bookings/{id}/route/selection", id)
                    .param("voyageNumber", voyage)
                    .with(user("router").roles("ROUTER")).with(csrf()));
        }

        // **古い判定を持った候補は、そのまま選べてしまう。**
        // 候補に保存された capacity_available は算出時の値である
        mockMvc.perform(post("/bookings/{id}/route/selection", late)
                .param("voyageNumber", voyage)
                .with(user("router").roles("ROUTER")).with(csrf()));

        // 確定の瞬間に数え直すことで、ここで止まる（IT5 レビュー M3）
        mockMvc.perform(post("/bookings/{id}/confirm", late)
                        .with(user("sales").roles("SALES")).with(csrf()))
                .andExpect(flash().attribute("flashError", Matchers.containsString("満船")));

        assertThat(cargoRepository.findById(new BookingId(late)).orElseThrow()
                .bookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
    }

    /** 受入基準（US14）: 「予約確定」状態の予約に追跡番号を発行でき、一意に採番される。 */
    @Test
    void 確定した予約に追跡番号を発行できる() throws Exception {
        var first = 経路確定済みの予約("JPTYO", "USNYC", new BigDecimal("100000"));
        var second = 経路確定済みの予約("JPKIX", "USSEA", new BigDecimal("100000"));

        String firstNumber = 追跡番号を発行する(first);
        String secondNumber = 追跡番号を発行する(second);

        assertThat(firstNumber).matches("TRK-\\d{8}-\\d{4}");
        assertThat(firstNumber).isNotEqualTo(secondNumber);
    }

    /**
     * <strong>発行した後にも予約詳細を開ける</strong>（IT5 の P1・Try T1）。
     *
     * <p>状態を進めた後にもう一度同じ画面を開けるかは受入基準に現れない。
     * 到達性の抜けは 4 イテレーション連続で起きている。
     */
    @Test
    void 追跡番号の発行後も予約詳細を開ける() throws Exception {
        var bookingId = 経路確定済みの予約("JPHKT", "TWKHH", new BigDecimal("100000"));
        String number = 追跡番号を発行する(bookingId);

        mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user("tracker").roles("TRACKER")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(number)));
    }

    /**
     * <strong>追跡管理者は追跡番号発行待ち一覧から予約に辿り着ける</strong>（作業入口）。
     *
     * <p>ADR-006 により通知は送らない。確定した予約がここに現れることが
     * 業務上の「発行依頼」である。
     */
    @Test
    void 確定した予約は追跡番号発行待ちに現れる() throws Exception {
        var bookingId = 経路確定済みの予約("JPKOB", "USOAK", new BigDecimal("100000"));
        mockMvc.perform(post("/bookings/{id}/confirm", bookingId)
                .with(user("sales").roles("SALES")).with(csrf()));

        mockMvc.perform(get("/tracking/queue").with(user("tracker").roles("TRACKER")))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        Matchers.containsString(bookingId.toString().substring(0, 8))));
    }

    /** 発行が済んだ予約は、発行待ち一覧から消える。**終わった仕事が残らない。** */
    @Test
    void 発行済みの予約は発行待ちから消える() throws Exception {
        var bookingId = 経路確定済みの予約("JPTYO", "KRPUS", new BigDecimal("100000"));
        追跡番号を発行する(bookingId);

        mockMvc.perform(get("/tracking/queue").with(user("tracker").roles("TRACKER")))
                .andExpect(content().string(Matchers.not(
                        Matchers.containsString(bookingId.toString().substring(0, 8)))));
    }

    /** 受入基準（US15）: 追跡番号で貨物を特定し、作業種別・日時・場所を登録できる。 */
    @Test
    void 荷役作業を登録すると輸送状態が進む() throws Exception {
        var bookingId = 経路確定済みの予約("JPOSA", "USLGB", new BigDecimal("100000"));
        String number = 追跡番号を発行する(bookingId);

        mockMvc.perform(post("/handling")
                        .param("trackingNumber", number)
                        .param("type", "RECEIVE")
                        .param("completionTime", "2026-12-01T09:00")
                        .param("locationUnlocode", "JPOSA")
                        .with(user("handler").roles("HANDLER")).with(csrf()))
                .andExpect(redirectedUrl("/handling"));

        mockMvc.perform(get("/handling").with(user("handler").roles("HANDLER")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("受領")));
    }

    /** 受入基準（US15）: 追跡番号が存在しない場合、エラーメッセージが表示される。 */
    @Test
    void 存在しない追跡番号では登録できない() throws Exception {
        mockMvc.perform(post("/handling")
                        .param("trackingNumber", "TRK-20261201-9999")
                        .param("type", "RECEIVE")
                        .param("completionTime", "2026-12-01T09:00")
                        .param("locationUnlocode", "JPOSA")
                        .with(user("handler").roles("HANDLER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("見つかりません")));
    }

    /**
     * 受入基準（US15）: 記録後、貨物状態が対応する状態に自動更新される。
     *
     * <p>最初の積込で予約状態も輸送中になる（遷移表 #6）。
     */
    @Test
    void 最初の積込で予約が輸送中になる() throws Exception {
        var bookingId = 経路確定済みの予約("JPOSA", "USSAV", new BigDecimal("100000"));
        String number = 追跡番号を発行する(bookingId);

        mockMvc.perform(post("/handling")
                .param("trackingNumber", number)
                .param("type", "LOAD")
                .param("completionTime", "2026-12-01T09:00")
                .param("locationUnlocode", "JPOSA")
                .param("voyageNumber", 航海番号(bookingId))
                .with(user("handler").roles("HANDLER")).with(csrf()));

        assertThat(cargoRepository.findById(new BookingId(bookingId)).orElseThrow()
                .bookingStatus()).isEqualTo(BookingStatus.IN_TRANSIT);
    }

    /**
     * 受入基準（US15）: 作業場所が予定ルートと異なる場合、警告が表示される。
     *
     * <p><strong>画面の文言だけでなく、貨物の経路状態に残ることを確かめる</strong>
     * （荷役ビジネスルール 1）。誤配が経路状態に残らないと、現在地からの再設計
     * （US28 / IT11）は「誤配が 1 件も無い」システムの上に積まれる。
     */
    @Test
    void 予定ルートと違う場所での積込は誤配として記録される() throws Exception {
        var bookingId = 経路確定済みの予約("JPOSA", "USNYC", new BigDecimal("100000"));
        String number = 追跡番号を発行する(bookingId);

        // **承認を挟む**（US28 / IT11）。予定ルートから外れた作業は、
        // 登録前に警告して承認を求める。記録してしまうと取り消す手段が無い
        mockMvc.perform(post("/handling")
                        .param("trackingNumber", number)
                        .param("type", "LOAD")
                        .param("completionTime", "2026-12-01T09:00")
                        // 予定に無い港
                        .param("locationUnlocode", "JPYOK")
                        .param("voyageNumber", 航海番号(bookingId))
                        .param("acknowledged", "true")
                        .with(user("handler").roles("HANDLER")).with(csrf()))
                .andExpect(flash().attribute("flashWarning",
                        Matchers.containsString("予定ルートに無い")));

        assertThat(cargoRepository.findById(new BookingId(bookingId)).orElseThrow()
                .routingStatus()).isEqualTo(CargoRoutingStatus.MISROUTED);
    }

    /**
     * <strong>予定どおりの積込では誤配にならない。</strong>
     *
     * <p>片側だけを確かめると、「常に誤配にする」実装でも緑になる。
     */
    @Test
    void 予定どおりの積込では誤配にならない() throws Exception {
        var bookingId = 経路確定済みの予約("JPKOB", "CLVAP", new BigDecimal("100000"));
        String number = 追跡番号を発行する(bookingId);

        mockMvc.perform(post("/handling")
                .param("trackingNumber", number)
                .param("type", "LOAD")
                .param("completionTime", "2026-12-01T09:00")
                .param("locationUnlocode", "JPKOB")
                .param("voyageNumber", 航海番号(bookingId))
                .with(user("handler").roles("HANDLER")).with(csrf()));

        assertThat(cargoRepository.findById(new BookingId(bookingId)).orElseThrow()
                .routingStatus()).isEqualTo(CargoRoutingStatus.ROUTED);
    }

    /**
     * <strong>作業日時は業務のタイムゾーンで解釈する。</strong>
     *
     * <p>画面が受け取るのは「日本時間の 09:00」であり、UTC の 09:00 ではない。
     * {@code toInstant(ZoneOffset.UTC)} と書いても画面のテストは緑のまま通るため、
     * <strong>保存された時刻そのものを確かめる</strong>（IT6 レビュー H1）。
     */
    @Test
    void 作業日時は業務のタイムゾーンで解釈される() throws Exception {
        var bookingId = 経路確定済みの予約("JPHKT", "MYPKG", new BigDecimal("100000"));
        String number = 追跡番号を発行する(bookingId);

        mockMvc.perform(post("/handling")
                .param("trackingNumber", number)
                .param("type", "RECEIVE")
                .param("completionTime", "2026-12-01T09:00")
                .param("locationUnlocode", "JPHKT")
                .with(user("handler").roles("HANDLER")).with(csrf()));

        var stored = jdbcTemplate.queryForObject(
                "SELECT event_completion_time FROM handling_activity WHERE booking_id = ?",
                java.time.OffsetDateTime.class, bookingId);
        assertThat(stored.toInstant()).isEqualTo(
                java.time.LocalDateTime.parse("2026-12-01T09:00")
                        .atZone(clock.getZone()).toInstant());
    }

    /** 航海番号の無い積込は登録できない（デシジョンテーブル）。 */
    @Test
    void 航海番号の無い積込は登録できない() throws Exception {
        var bookingId = 経路確定済みの予約("JPNGO", "USHOU", new BigDecimal("100000"));
        String number = 追跡番号を発行する(bookingId);

        mockMvc.perform(post("/handling")
                        .param("trackingNumber", number)
                        .param("type", "LOAD")
                        .param("completionTime", "2026-12-01T09:00")
                        .param("locationUnlocode", "JPNGO")
                        .with(user("handler").roles("HANDLER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("航海番号")));
    }

    private String 航海番号(UUID bookingId) {
        return cargoRepository.findById(new BookingId(bookingId)).orElseThrow()
                .cargoItinerary().legs().getFirst().voyageNumber();
    }
    /**
     * <strong>押す人が画面を開ける</strong>（IT6 開始準備の突合で見つかった欠落）。
     *
     * <p>追跡番号を発行するのは追跡管理者だが、予約詳細の画面一覧には
     * 営業担当者と荷主しか無かった。開けない画面のボタンは押せない。
     */
    @Test
    void 追跡管理者は予約詳細を開ける() throws Exception {
        var bookingId = 経路確定済みの予約("JPKIX", "CNSZX", new BigDecimal("100000"));

        mockMvc.perform(get("/bookings/{id}", bookingId)
                        .with(user("tracker").roles("TRACKER")))
                .andExpect(status().isOk());
    }

    /**
     * <strong>追跡管理者は予約を書き換えられない。</strong>
     *
     * <p>読めるようにしたことと、操作できるようにしたことは別である。
     * GET だけを開く（IT3 の経路設計者と同じ扱い）。
     */
    @Test
    void 追跡管理者は予約を確定できない() throws Exception {
        var bookingId = 経路確定済みの予約("JPHKT", "SGSIN", new BigDecimal("100000"));

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId)
                        .with(user("tracker").roles("TRACKER")).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(cargoRepository.findById(new BookingId(bookingId)).orElseThrow()
                .bookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
    }

    /** <strong>営業担当者は追跡番号を発行できない</strong>（遷移表 #5 は追跡管理者）。 */
    @Test
    void 営業担当者は追跡番号を発行できない() throws Exception {
        var bookingId = 経路確定済みの予約("JPYOK", "NLRTM", new BigDecimal("100000"));
        mockMvc.perform(post("/bookings/{id}/confirm", bookingId)
                .with(user("sales").roles("SALES")).with(csrf()));

        mockMvc.perform(post("/bookings/{id}/tracking-number", bookingId)
                        .with(user("sales").roles("SALES")).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(cargoRepository.findById(new BookingId(bookingId)).orElseThrow()
                .trackingNumber()).isNull();
    }

    /** 荷役の画面は荷役作業員のものである。**URL 直打ちでも開けない。** */
    @Test
    void 荷役作業員以外は荷役の画面を開けない() throws Exception {
        mockMvc.perform(get("/handling").with(user("sales").roles("SALES")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/handling/new").with(user("router").roles("ROUTER")))
                .andExpect(status().isForbidden());
    }

    /** 追跡番号発行待ちは追跡管理者のものである。 */
    @Test
    void 追跡管理者以外は発行待ち一覧を開けない() throws Exception {
        mockMvc.perform(get("/tracking/queue").with(user("handler").roles("HANDLER")))
                .andExpect(status().isForbidden());
    }

    /**
     * <strong>権限の無い POST は 403 の画面へ forward する</strong>（IT5 の P4・Try T4）。
     *
     * <p><strong>MockMvc では forward 先の本文まで描画されない。</strong> ここで確かめられる
     * のは「403 が返り、業務のことばの画面へ forward する」ところまでである。
     * IT5 では MockMvc が緑のまま、実環境で 405 になっていた（forward 先が GET だけに
     * 割り当てられていた）。<strong>実際に押して確かめる工程はクローズ前に別途行う。</strong>
     */
    @Test
    void 権限の無い操作は403の画面へforwardする() throws Exception {
        var bookingId = 経路確定済みの予約("JPNGO", "THBKK", new BigDecimal("100000"));

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId)
                        .with(user("handler").roles("HANDLER")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/access-denied"));
    }
}
