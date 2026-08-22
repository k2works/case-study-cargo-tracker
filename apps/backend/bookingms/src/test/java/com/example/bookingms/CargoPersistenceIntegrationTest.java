package com.example.bookingms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bookingms.application.internal.BookCargoCommand;
import com.example.bookingms.application.internal.BookCargoUseCase;
import com.example.bookingms.application.internal.RegisterShipperCommand;
import com.example.bookingms.application.internal.RegisterShipperUseCase;
import com.example.bookingms.application.internal.RegistrationOutcome;
import com.example.bookingms.application.internal.SearchCargoUseCase;
import com.example.bookingms.application.port.CargoSummary;
import com.example.bookingms.application.port.CargoRepository;
import com.example.bookingms.application.port.LocationRepository;
import com.example.bookingms.domain.model.BookingId;
import com.example.shared.domain.model.Location;
import com.example.bookingms.domain.model.BookingStatus;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.CargoItinerary;
import com.example.bookingms.domain.model.TrackingNumber;
import com.example.bookingms.domain.model.CargoType;
import com.example.bookingms.domain.model.HazardClass;
import com.example.bookingms.domain.model.Leg;
import com.example.bookingms.domain.model.RoutingStatus;
import com.example.bookingms.domain.model.ShipperType;
import com.example.bookingms.domain.model.TransportStatus;
import com.example.bookingms.domain.model.VoyageNumber;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 貨物予約が実際の DB で成立することを確認する。
 *
 * <p>採番・地点の結合・危険物と冷凍の保存は、いずれも DB の振る舞いに依存する。
 * ユニットテストのスタブが緑でも、ここが噛み合わなければ 1 件も登録できない。
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("貨物予約の永続化")
class CargoPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private BookCargoUseCase bookCargo;

    @Autowired
    private SearchCargoUseCase searchCargo;

    @Autowired
    private RegisterShipperUseCase registerShipper;

    @Autowired
    private LocationRepository locations;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CargoRepository repository;

    /** 業務タイムゾーンの時刻源。実装と同じものを使う（別々に「今日」を決めない）。 */
    @Autowired
    private java.time.Clock clock;

    private Long shipperId(String name, String email) {
        RegistrationOutcome outcome = registerShipper.registerAnyway(new RegisterShipperCommand(
                ShipperType.INDIVIDUAL, name, email, "東京都千代田区 1-1-1", null));
        return ((RegistrationOutcome.Registered) outcome).shipper().id();
    }

    private BookCargoCommand command(Long shipperId, CargoType type) {
        return new BookCargoCommand(shipperId, type, new BigDecimal("12000"), 20, "電子部品",
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"),
                "JPTYO", "USLAX", LocalDate.of(2030, Month.SEPTEMBER, 1), LocalDate.of(2030, Month.SEPTEMBER, 20),
                type == CargoType.HAZARDOUS ? "3" : null,
                type == CargoType.HAZARDOUS ? "UN1263" : null,
                type == CargoType.HAZARDOUS ? "PAINT" : null,
                type == CargoType.REFRIGERATED ? new BigDecimal("-20") : null,
                type == CargoType.REFRIGERATED ? new BigDecimal("-15") : null);
    }

    @Test
    @DisplayName("予約番号が本番経路（DB シーケンス）で採番される")
    void assignsBookingIdFromDatabase() {
        Cargo booked = bookCargo.book(command(shipperId("採番太郎", "cargo-seq@example.com"),
                CargoType.GENERAL));

        assertThat(booked.bookingId())
                .as("予約番号が採番されていない。5 サービスが参照するキーが空になる")
                .isPresent();
        // 形式そのものが契約になる（ADR-011）
        String bookingId = booked.bookingId().orElseThrow().value();
        assertThat(bookingId).matches("^BKG-\\d{10}$");

        // 読み戻して確かめる。戻り値だけを見ると、採番はしたが列に書いていない実装でも通る
        assertThat(repository.findByBookingId(bookingId))
                .as("採番した予約番号で引き当てられない。列に保存できていない")
                .isPresent();
    }

    @Test
    @DisplayName("連続して登録しても予約番号が衝突しない")
    void assignsDistinctBookingIds() {
        Long shipperId = shipperId("連番太郎", "cargo-seq2@example.com");

        BookingId first = bookCargo.book(command(shipperId, CargoType.GENERAL))
                .bookingId().orElseThrow();
        BookingId second = bookCargo.book(command(shipperId, CargoType.GENERAL))
                .bookingId().orElseThrow();

        assertThat(first).isNotEqualTo(second);
        // 2 行とも DB に残っていること。戻り値だけだと、2 件目が 1 件目を上書きしても通る
        assertThat(repository.findByBookingId(first.value())).isPresent();
        assertThat(repository.findByBookingId(second.value())).isPresent();
    }

    @Test
    @DisplayName("仮受付として保存され、状態が空欄にならない")
    void persistsPreliminaryStatuses() {
        Cargo booked = bookCargo.book(command(shipperId("状態太郎", "cargo-status@example.com"),
                CargoType.GENERAL));

        // <strong>読み戻して確かめる</strong>（IT6 タスク 0.9）。戻り値だけを見る検査は、
        // 状態を列に書かない実装でも緑になる。このテストの名前は「保存され」と主張しており、
        // 保存を確かめていないことが読み手に伝わらない
        Cargo reloaded = repository.findByBookingId(booked.bookingId().orElseThrow().value())
                .orElseThrow().cargo();

        assertThat(reloaded.bookingStatus()).isEqualTo(BookingStatus.PRELIMINARY);
        assertThat(reloaded.transportStatus()).isEqualTo(TransportStatus.NOT_RECEIVED);
        assertThat(reloaded.routingStatus()).isEqualTo(RoutingStatus.NOT_ROUTED);
    }

    @Test
    @DisplayName("地点は名称まで読み戻せる（画面がコードから引き直さずに済む）")
    void restoresLocationNames() {
        Cargo booked = bookCargo.book(command(shipperId("地点太郎", "cargo-loc@example.com"),
                CargoType.GENERAL));

        // 名前のとおり<strong>読み戻す</strong>（IT6 タスク 0.9）。戻り値には登録時の地点が
        // そのまま入っているため、DB 経由の復元では名称が落ちていても気づけない
        Cargo reloaded = repository.findByBookingId(booked.bookingId().orElseThrow().value())
                .orElseThrow().cargo();

        assertThat(reloaded.routeSpecification().origin().name()).isEqualTo("Tokyo");
        assertThat(reloaded.routeSpecification().destination().name()).isEqualTo("Los Angeles");
    }

    @Test
    @DisplayName("危険物の申告が保存され、経路設計が読める形になっている")
    void persistsHazardousDeclaration() {
        // IT3 の経路設計はこの値を入力にする。保存されていなければ、対応可能な航海を選べない
        Long shipperId = shipperId("危険物太郎", "cargo-haz@example.com");
        Cargo booked = bookCargo.book(command(shipperId, CargoType.HAZARDOUS));

        Cargo reloaded = searchCargo.search(CargoType.HAZARDOUS, null).cargoes().stream()
                .map(CargoSummary::cargo)
                .filter(c -> c.id().equals(booked.id()))
                .findFirst()
                .orElseThrow();

        assertThat(reloaded.requiresHazardousDeclaration()).isTrue();
        assertThat(reloaded.hazardousDeclaration().orElseThrow().unNumber()).isEqualTo("UN1263");
        assertThat(reloaded.hazardousDeclaration().orElseThrow().hazardousClass())
                .isEqualTo(HazardClass.CLASS_3);
        assertThat(reloaded.hazardousDeclaration().orElseThrow().properShippingName())
                .isEqualTo("PAINT");
    }

    @Test
    @DisplayName("冷凍の温度条件が保存され、下限と上限が入れ替わらない")
    void persistsTemperatureRequirement() {
        Long shipperId = shipperId("冷凍太郎", "cargo-ref@example.com");
        Cargo booked = bookCargo.book(command(shipperId, CargoType.REFRIGERATED));

        Cargo reloaded = searchCargo.search(CargoType.REFRIGERATED, null).cargoes().stream()
                .map(CargoSummary::cargo)
                .filter(c -> c.id().equals(booked.id()))
                .findFirst()
                .orElseThrow();

        assertThat(reloaded.temperatureRequirement().orElseThrow().minCelsius())
                .isEqualByComparingTo("-20");
        assertThat(reloaded.temperatureRequirement().orElseThrow().maxCelsius())
                .isEqualByComparingTo("-15");
    }

    @Test
    @DisplayName("いまの規則に合わない行が 1 件あっても一覧は開ける")
    void restoresRowsThatViolateCurrentInvariants() {
        // 不変条件は後から足される。当時の規則で通った行や、列が無かったころの行を
        // 復元時に検査すると、1 行のせいで一覧全体が開けなくなり、直す手立ても失う
        Long shipperId = shipperId("旧規則太郎", "cargo-legacy@example.com");
        Cargo booked = bookCargo.book(command(shipperId, CargoType.GENERAL));
        jdbcTemplate.update("UPDATE cargo SET length_cm = 0, width_cm = 0, height_cm = 0 "
                + "WHERE id = ?", booked.id());

        List<CargoSummary> all = searchCargo.search(null, null).cargoes();

        assertThat(all).anyMatch(c -> c.cargo().id().equals(booked.id()));
    }

    @Test
    @DisplayName("存在しない荷主 ID の予約は拒否する")
    void rejectsUnknownShipper() {
        // 通すと、誰の貨物か分からない予約が保存される
        BookCargoCommand unknownShipper = command(999_999L, CargoType.GENERAL);

        assertThatThrownBy(() -> bookCargo.book(unknownShipper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("荷主");
    }

    @Test
    @DisplayName("存在しない地点コードの予約は拒否する")
    void rejectsUnknownLocation() {
        Long shipperId = shipperId("地点不明", "cargo-noloc@example.com");
        BookCargoCommand unknown = new BookCargoCommand(shipperId, CargoType.GENERAL,
                new BigDecimal("100"), null, null, null, null, null,
                "JPTYO", "ZZZZZ", null, LocalDate.of(2030, Month.SEPTEMBER, 20),
                null, null, null, null, null);

        assertThatThrownBy(() -> bookCargo.book(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目的地");
    }

    @Test
    @DisplayName("一覧は新しい順で、種別で絞り込める")
    void listsNewestFirstAndFiltersByType() {
        Long shipperId = shipperId("一覧太郎", "cargo-list@example.com");
        bookCargo.book(command(shipperId, CargoType.GENERAL));
        Cargo latest = bookCargo.book(command(shipperId, CargoType.GENERAL));

        List<CargoSummary> all = searchCargo.search(null, null).cargoes();

        // 登録順だと、今入れた 1 件が常に最下部に沈む
        assertThat(all.get(0).cargo().id()).isEqualTo(latest.id());
        assertThat(searchCargo.search(CargoType.GENERAL, null).cargoes())
                .allMatch(summary -> summary.cargo().type() == CargoType.GENERAL);
    }

    @Test
    @DisplayName("予約番号と荷主名で絞り込める")
    void filtersByKeyword() {
        Long shipperId = shipperId("絞込花子", "cargo-keyword@example.com");
        Cargo booked = bookCargo.book(command(shipperId, CargoType.GENERAL));
        String bookingId = booked.bookingId().orElseThrow().value();

        assertThat(searchCargo.search(null, "絞込花子").cargoes())
                .extracting(summary -> summary.cargo().id())
                .contains(booked.id());
        // 社名で探した結果に社名が無いと、同名の別会社が混ざっていないか確かめられない
        assertThat(searchCargo.search(null, "絞込花子").cargoes())
                .extracting(CargoSummary::shipperName)
                .contains("絞込花子");
        assertThat(searchCargo.search(null, bookingId).cargoes()).hasSize(1);
    }

    @Test
    @DisplayName("地点マスタは業務タイムゾーンを持つ")
    void locationsCarryBusinessTimeZone() {
        // 到着期限を目的地の暦で判断するのに要る（ADR-010）
        assertThat(locations.timeZoneOf("USLAX")).contains(ZoneId.of("America/Los_Angeles"));
        assertThat(locations.timeZoneOf("JPTYO")).contains(ZoneId.of("Asia/Tokyo"));
        assertThat(locations.findAll()).isNotEmpty();
    }

    /**
     * 既にある予約を保存し直すと、行が増えずに内容だけ変わること。
     *
     * <p>常に INSERT する実装だと、経路設計の依頼（US06）のような更新が「新しい予約を作る」
     * 動きになる。しかも元の予約は変わらないままなので、画面には依頼できたように見えて、
     * 一覧には依頼済みの別番号が増える。IT3 の kind 統合環境で実際にこの形で現れた。
     */
    @Test
    @DisplayName("既にある予約を保存し直すと、予約番号は変わらず行も増えない")
    void updatesInsteadOfInsertingWhenCargoAlreadyExists() {
        Cargo saved = bookCargo.book(command(
                shipperId("更新太郎", "cargo-update@example.com"), CargoType.GENERAL));
        BookingId bookingId = saved.bookingId().orElseThrow();
        long countBefore = repository.count(null, null, null, null);

        Cargo updated = repository.save(saved.requestRouting());

        assertThat(updated.bookingId()).contains(bookingId);
        assertThat(updated.id()).isEqualTo(saved.id());
        assertThat(updated.routingStatus()).isEqualTo(RoutingStatus.ROUTING_REQUESTED);
        assertThat(repository.count(null, null, null, null))
                .as("更新のはずが行が増えている")
                .isEqualTo(countBefore);

        Cargo reloaded = repository.findByBookingId(bookingId.value()).orElseThrow().cargo();
        assertThat(reloaded.routingStatus()).isEqualTo(RoutingStatus.ROUTING_REQUESTED);
    }

    private static final ZoneId LA = ZoneId.of("America/Los_Angeles");

    private static CargoItinerary itineraryVia(String transitUnLocode, String transitName) {
        return CargoItinerary.of(List.of(
                Leg.of(VoyageNumber.of("V0201"), Location.of("JPTYO", "Tokyo"),
                        Location.of(transitUnLocode, transitName),
                        Instant.parse("2030-09-02T09:00:00Z"),
                        Instant.parse("2030-09-05T09:00:00Z")),
                Leg.of(VoyageNumber.of("V0202"), Location.of(transitUnLocode, transitName),
                        Location.of("USLAX", "Los Angeles"),
                        Instant.parse("2030-09-06T09:00:00Z"),
                        Instant.parse("2030-09-18T09:00:00Z"))));
    }

    @Test
    @DisplayName("旅程が保存され、区間が順序どおりに読み戻せる")
    void persistsItinerary() {
        Cargo booked = bookCargo.book(command(shipperId("旅程太郎", "itinerary@example.com"),
                CargoType.GENERAL));
        Cargo assigned = repository.save(
                booked.requestRouting().assignItinerary(itineraryVia("CNSHA", "Shanghai"), LA));

        Cargo found = repository.findById(assigned.id()).orElseThrow();

        assertThat(found.routingStatus()).isEqualTo(RoutingStatus.ROUTED);
        assertThat(found.bookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
        assertThat(found.itinerary()).isPresent();
        // 順序に意味がある。並びが崩れると「東京 → ロサンゼルス → 上海」になる
        assertThat(found.itinerary().orElseThrow().legs())
                .extracting(leg -> leg.loadLocation().unLocode())
                .containsExactly("JPTYO", "CNSHA");
        // 地点は名称まで読み戻す。画面がコードから引き直さずに済む
        assertThat(found.itinerary().orElseThrow().legs().get(0).unloadLocation().name())
                .isEqualTo("Shanghai");
        assertThat(found.itinerary().orElseThrow().expectedArrivalTime())
                .isEqualTo(Instant.parse("2030-09-18T09:00:00Z"));
    }

    /**
     * IT3 の欠陥と同じ形。区間を消さずに入れ直すと、旅程が二重になる。
     *
     * <p>しかも順序は保たれるため、画面上は「区間が増えた」ようにしか見えない。
     */
    @Test
    @DisplayName("経路を差し替えても区間の行が増えない")
    void replacingItineraryDoesNotAddRows() {
        Cargo booked = bookCargo.book(command(shipperId("差替太郎", "replace@example.com"),
                CargoType.GENERAL));
        Cargo assigned = repository.save(
                booked.requestRouting().assignItinerary(itineraryVia("CNSHA", "Shanghai"), LA));

        Cargo replaced = repository.save(
                assigned.assignItinerary(itineraryVia("SGSIN", "Singapore"), LA));

        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM leg WHERE cargo_id = ?", Long.class, assigned.id());
        assertThat(rows).as("差し替えで区間の行が増えている").isEqualTo(2L);
        // **保存の戻り値ではなく DB から読み戻す。**戻り値は渡した集約そのものなので、
        // 古い区間を消して古い区間を入れ直す実装でも緑になる
        assertThat(repository.findById(assigned.id()).orElseThrow().itinerary().orElseThrow()
                .legs())
                .extracting(leg -> leg.unloadLocation().unLocode())
                .containsExactly("SGSIN", "USLAX");
        assertThat(replaced.itinerary()).isPresent();
    }

    @Test
    @DisplayName("経路が決まっていない予約は旅程を持たない")
    void cargoWithoutItineraryReadsBack() {
        Cargo booked = bookCargo.book(command(shipperId("未定太郎", "no-itinerary@example.com"),
                CargoType.GENERAL));

        // 空のリストと「旅程が無い」を取り違えると、画面が空の旅程表を出す
        assertThat(repository.findById(booked.id()).orElseThrow().itinerary()).isEmpty();
    }

    /**
     * 通知の記録と追跡番号が<strong>読み戻せる</strong>（US12-4・US14）。
     *
     * <p>戻り値だけを見ると、列に書いていない実装でも通る（IT6 タスク 0.9 で直した形と同じ）。
     */
    @Test
    @DisplayName("通知の記録と追跡番号が読み戻せる")
    void persistsNotificationAndTrackingNumber() {
        Cargo booked = bookCargo.book(command(shipperId("通知太郎", "cargo-notify@example.com"),
                CargoType.GENERAL));
        String bookingId = booked.bookingId().orElseThrow().value();

        Cargo routed = repository.save(
                booked.requestRouting().assignItinerary(itineraryVia("CNSHA", "Shanghai"), LA));

        Instant notifiedAt = Instant.parse("2026-08-22T02:00:00Z");
        Cargo confirmed = repository.save(
                repository.save(routed.notifyShipper(notifiedAt, "sales01")).confirm());

        // 採番は本番と同じ経路（シーケンス）を通す。自前採番だと UNIQUE 制約で落ちる
        String number = repository.nextTrackingNumber();
        assertThat(number).matches("^TRK-\\d{8}-\\d{4}$");
        repository.save(confirmed.issueTrackingNumber(TrackingNumber.of(number)));

        assertThat(repository.findByBookingId(bookingId))
                .get()
                .satisfies(found -> {
                    Cargo cargo = found.cargo();
                    assertThat(cargo.bookingStatus()).isEqualTo(BookingStatus.TRACKING_ISSUED);
                    assertThat(cargo.routeNotification().orElseThrow().notifiedAt())
                            .isEqualTo(notifiedAt);
                    assertThat(cargo.routeNotification().orElseThrow().notifiedBy())
                            .isEqualTo("sales01");
                    assertThat(cargo.trackingNumber().orElseThrow().value()).isEqualTo(number);
                });
    }

    /** 採番は続けて呼んでも衝突しない（US14-2）。 */
    @Test
    @DisplayName("追跡番号は続けて採番しても衝突しない")
    void numbersDistinctTrackingNumbers() {
        assertThat(repository.nextTrackingNumber())
                .isNotEqualTo(repository.nextTrackingNumber());
    }

    /**
     * 追跡番号の日付は<strong>業務タイムゾーン</strong>で決まる（IT6 のクローズレビュー）。
     *
     * <p>`CURRENT_DATE` は DB のセッションのタイムゾーン（コンテナは通常 UTC）で決まる。
     * それを使うと、<strong>日本時間の 00:00〜09:00 に発行した番号が前日の日付を持つ</strong>。
     * 「番号だけでいつごろの貨物か分かる」という目的が 1 日 9 時間ぶん外れる。
     *
     * <p><strong>テストも同じ Clock で「今日」を決める。</strong>ここで
     * {@code LocalDate.now()} を書くと、CI（UTC）でだけ落ちるテストになる。
     */
    @Test
    @DisplayName("追跡番号の日付は業務タイムゾーンの今日")
    void numbersWithTheBusinessDate() {
        String expected = java.time.LocalDate.now(clock)
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

        assertThat(repository.nextTrackingNumber()).startsWith("TRK-" + expected + "-");
    }
}
