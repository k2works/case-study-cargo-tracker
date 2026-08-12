package com.example.cargotracker.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoRoutingStatus;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierName;
import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingWeight;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VesselName;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.routing.domain.repository.BookingRouteProposalRepository;
import com.example.cargotracker.routing.domain.repository.VoyageRepository;
import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import com.example.cargotracker.support.CargoFixture;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 2 人が同じ予約を同時に操作したときの画面の振る舞い（IT4 持ち越し C5）。
 *
 * <p><strong>検証するのは「衝突が起きたら何が起こるか」だけである</strong>（IT5 で決めた方法）。
 * MockMvc で並行リクエストそのものを再現しようとしない。再現しにくいものを
 * 再現しようとするより、<strong>衝突を受け取ったときの振る舞いを確実に固定する</strong>
 * ほうが守れる。後の保存が拒否されること自体はリポジトリのテストが固定している。
 *
 * <p>衝突は<strong>保存の瞬間に起きる</strong>ため、リポジトリを差し替えて
 * {@link ConcurrentModificationException} を投げさせる。
 *
 * <p>確かめるのは 2 点である。
 *
 * <ul>
 *   <li>500 にならず、次にどうすればよいかが画面のことばで伝わる</li>
 *   <li><strong>途中まで書いた分が残らない</strong>（確定は 2 つの BC に書くため、
 *       片方だけ残ると「提案は未確定なのに貨物は割り当て済」になる）</li>
 * </ul>
 */
@AutoConfigureMockMvc
@WithMockUser(username = "router", roles = "ROUTER")
class ConcurrentRouteOperationScenarioTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private VoyageRepository voyageRepository;

    @Autowired
    private Clock clock;

    /**
     * 保存だけを衝突させる。
     *
     * <p><strong>丸ごとモックにしない。</strong> 読み取りまで作り物にすると、
     * 「衝突したときに何が起きるか」ではなく「モックが返す値をそのまま返すか」を
     * 見るテストになる。
     */
    @MockitoSpyBean
    private BookingRouteProposalRepository proposalRepository;

    /** 追跡の保存だけを衝突させる（IT6 レビュー H2）。 */
    @MockitoSpyBean
    private com.example.cargotracker.tracking.domain.repository.TrackingActivityRepository
            trackingActivityRepository;

    /** 予約の保存だけを衝突させる（IT6 レビュー H3）。 */
    @MockitoSpyBean
    private CargoRepository cargoRepository;

    private LocalDate 業務上の今日() {
        return LocalDate.now(clock);
    }

    private Instant 未来(int daysFromToday, int hour) {
        return 業務上の今日().plusDays(daysFromToday).atTime(hour, 0)
                .atZone(clock.getZone()).toInstant();
    }

    private UUID 引き渡し済みの予約(String origin, String destination) {
        CargoFixture.Inserted cargo = CargoFixture.on(jdbcTemplate)
                .shipperNamePrefix("山田物産株式会社")
                .route(origin, destination)
                .arrivalDeadline(業務上の今日().plusDays(60))
                .status("ROUTE_PROPOSED", "NOT_ROUTED")
                .insert();
        return cargo.bookingId();
    }

    private String 航海を登録する(String origin, String destination) {
        String number = "V" + UUID.randomUUID().toString().substring(0, 8);
        voyageRepository.save(Voyage.register(new RegisterVoyageCommand(
                new VoyageNumber(number), new VesselName("さくら丸"),
                new CarrierName("日本海運"),
                Schedule.of(List.of(CarrierMovement.of(
                        Location.of(origin), Location.of(destination),
                        未来(5, 10), 未来(20, 6)))),
                Set.of(RoutingCargoType.GENERAL),
                RoutingWeight.ofKilograms(new BigDecimal("100000")))));
        return number;
    }

    /** 経路を確定して予約を確定した状態まで進める（発行の入口）。 */
    private UUID 確定済みの予約() throws Exception {
        var bookingId = 引き渡し済みの予約("JPOSA", "USLAX");
        String voyage = 航海を登録する("JPOSA", "USLAX");
        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));
        mockMvc.perform(post("/bookings/{id}/route/selection", bookingId)
                .param("voyageNumber", voyage).with(csrf()));
        mockMvc.perform(post("/bookings/{id}/confirm", bookingId)
                .with(user("sales").roles("SALES")).with(csrf()));
        return bookingId;
    }

    /** 追跡番号を発行した状態まで進める（荷役の入口）。 */
    private UUID 追跡番号発行済みの予約() throws Exception {
        var bookingId = 確定済みの予約();
        mockMvc.perform(post("/bookings/{id}/tracking-number", bookingId)
                .with(user("tracker").roles("TRACKER")).with(csrf()));
        return bookingId;
    }

    private String trackingNumberOf(UUID bookingId) {
        return cargoRepository.findById(new BookingId(bookingId)).orElseThrow()
                .trackingNumber().value();
    }

    /** 算出が衝突したとき、500 ではなく画面のことばで返る。 */
    @Test
    void 算出が衝突しても500にならない() throws Exception {
        var bookingId = 引き渡し済みの予約("JPOSA", "USLAX");
        航海を登録する("JPOSA", "USLAX");
        Mockito.doThrow(new ConcurrentModificationException(
                        "別の担当者が先に算出しました。最新の候補を確認してください"))
                .when(proposalRepository).save(any());

        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()))
                .andExpect(redirectedUrl("/bookings/" + bookingId + "/route"))
                .andExpect(flash().attribute("flashError",
                        Matchers.containsString("別の担当者")));
    }

    /**
     * 確定が衝突したとき、<strong>貨物への割り当ても残らない</strong>。
     *
     * <p>確定は提案（Routing）と貨物（Booking）の両方に書く。提案の保存で衝突したのに
     * 貨物だけが割り当て済のまま残ると、<strong>誰も選んでいない経路が貨物に付いている</strong>
     * 状態になる。1 つのトランザクションで書いていることを、画面から確かめる。
     */
    @Test
    void 確定が衝突すると貨物への割り当ても残らない() throws Exception {
        var bookingId = 引き渡し済みの予約("JPYOK", "DEHAM");
        String voyage = 航海を登録する("JPYOK", "DEHAM");
        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));

        // **算出のあとに衝突させる。** 先に仕込むと候補が保存されず、
        // 確定の経路に入る前に落ちて「確定の衝突」を見たことにならない
        Mockito.doThrow(new ConcurrentModificationException(
                        "別の担当者が先に更新しました。最新の内容を確認してください"))
                .when(proposalRepository).save(any());

        mockMvc.perform(post("/bookings/{id}/route/selection", bookingId)
                        .param("voyageNumber", voyage).with(csrf()))
                .andExpect(redirectedUrl("/bookings/" + bookingId + "/route"))
                .andExpect(flash().attribute("flashError",
                        Matchers.containsString("別の担当者")));

        var cargo = cargoRepository.findById(new BookingId(bookingId)).orElseThrow();
        assertThat(cargo.routingStatus()).isEqualTo(CargoRoutingStatus.NOT_ROUTED);
        // 未割り当ての貨物は旅程を持たない（経路状態と旅程はひと組。IT5 の CargoRouting）
        assertThat(cargo.cargoItinerary()).isNull();
    }
    /**
     * <strong>追跡への反映が失敗しても、荷役の記録は残る</strong>（ADR-009 の結果整合）。
     *
     * <p>これは ADR-009 の改訂で<strong>意図的に変えた振る舞い</strong>である。改訂前は
     * 同一トランザクションで書いており、追跡の更新が衝突すると荷役の記録ごと巻き戻していた。
     *
     * <p><strong>順序が逆だった。</strong> 荷役は最も頻度が高く、最も落としてはならない
     * 記録である。追跡の都合（ロック競合）で現場の作業記録が失われてよい理由は無い。
     * 反映は追って行われる（あるいはログに残る）のが正しい形である。
     */
    @Test
    void 追跡への反映が失敗しても荷役の記録は残る() throws Exception {
        var bookingId = 追跡番号発行済みの予約();
        String number = trackingNumberOf(bookingId);
        Mockito.doReturn(false).when(trackingActivityRepository).update(any());

        mockMvc.perform(post("/handling")
                        .param("trackingNumber", number)
                        .param("type", "RECEIVE")
                        .param("completionTime", "2026-12-01T09:00")
                        .param("locationUnlocode", "JPOSA")
                        .with(user("handler").roles("HANDLER")).with(csrf()))
                .andExpect(redirectedUrl("/handling"));

        // **荷役は残る。** 追跡に反映されていないことは、ログにだけ現れる
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM handling_activity WHERE booking_id = ?",
                Integer.class, bookingId);
        assertThat(count).isEqualTo(1);
    }

    /**
     * <strong>予約の確定が衝突したとき、状態は進まない</strong>（IT6 レビュー M6）。
     *
     * <p>IT6 では発行の衝突は固定したが、<strong>確定の衝突は一度も回していなかった</strong>。
     * 確定は追跡番号の発行の前提であり、ここが黙って進むと「確定していないのに
     * 発行待ち一覧に出ない予約」が生まれる。
     *
     * <p>確定は満船の再判定を伴うため、経路の確定（US09）とは別の入口である。
     * <strong>入口が違えば衝突の扱いも別に確かめる。</strong>
     */
    @Test
    void 確定が衝突すると予約状態は進まない() throws Exception {
        var bookingId = 引き渡し済みの予約("JPOSA", "AUSYD");
        String voyage = 航海を登録する("JPOSA", "AUSYD");
        mockMvc.perform(post("/bookings/{id}/route/proposals", bookingId).with(csrf()));
        mockMvc.perform(post("/bookings/{id}/route/selection", bookingId)
                .param("voyageNumber", voyage).with(csrf()));

        // **経路の確定まで済ませてから衝突させる。** 先に仕込むと経路の割り当てで落ち、
        // 「確定の衝突」を見たことにならない
        Mockito.doReturn(false).when(cargoRepository).update(any());

        mockMvc.perform(post("/bookings/{id}/confirm", bookingId)
                        .with(user("sales").roles("SALES")).with(csrf()))
                .andExpect(redirectedUrl("/bookings/" + bookingId))
                .andExpect(flash().attribute("flashError",
                        Matchers.containsString("他の操作が先に")));

        String status = jdbcTemplate.queryForObject(
                "SELECT booking_status FROM cargo WHERE booking_id = ?", String.class, bookingId);
        assertThat(status).isEqualTo("ROUTE_PROPOSED");
    }

    /**
     * <strong>追跡番号の発行が衝突したとき、追跡レコードだけが残らない</strong>
     * （IT6 レビュー H3）。
     *
     * <p><strong>発行はコマンドであり、伝播ではない</strong>（ADR-009）。番号を受け取って
     * 予約に記録するまでが 1 つの操作であり、同期・同一トランザクションで書く。
     *
     * <p>戻り値で返すとトランザクションはコミットされ、どの予約にも紐づかない
     * 追跡レコードが残る。Spring がロールバックするのは非検査例外のときだけである。
     */
    @Test
    void 発行が衝突すると追跡レコードも残らない() throws Exception {
        var bookingId = 確定済みの予約();
        Mockito.doReturn(false).when(cargoRepository).updateTrackingNumber(any());

        mockMvc.perform(post("/bookings/{id}/tracking-number", bookingId)
                        .with(user("tracker").roles("TRACKER")).with(csrf()))
                .andExpect(redirectedUrl("/bookings/" + bookingId))
                .andExpect(flash().attribute("flashError",
                        Matchers.containsString("他の操作が先に")));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tracking_activity WHERE booking_id = ?",
                Integer.class, bookingId);
        assertThat(count).isZero();
    }
}
