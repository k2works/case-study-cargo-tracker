package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.BookingStatus;
import com.example.cargotracker.booking.domain.model.BookingTrackingNumber;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.model.CargoItinerary;
import com.example.cargotracker.booking.domain.model.CargoRoutingStatus;
import com.example.cargotracker.booking.domain.model.CargoSpecification;
import com.example.cargotracker.booking.domain.model.CargoType;
import com.example.cargotracker.booking.domain.model.Description;
import com.example.cargotracker.booking.domain.model.Dimensions;
import com.example.cargotracker.booking.domain.model.Leg;
import com.example.cargotracker.booking.domain.model.Quantity;
import com.example.cargotracker.booking.domain.model.RouteSpecification;
import com.example.cargotracker.booking.domain.model.Weight;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code cargo} の永続化を実 PostgreSQL で検証する（ADR-003）。
 *
 * <p><strong>SQL の正しさを H2 で判断しない。</strong> 本テストが検証しているのは
 * ドメインの規則ではなく SQL とスキーマの整合であり、方言差がそのまま
 * 「テストは緑だが本番で落ちる」に化ける。
 */
// 到着期限の「当日」「過去」は現在日時に対する相対的な概念である。固定日で書くと
// **時間の経過とともにテストの意味が変わり、いずれ「過去の予約は登録できない」が
// すべての予約に当てはまってしまう。** ここでシステム時計を使うのは仕様そのものである。
// 時計に依存しない検証はユニットテスト（CargoTest）が担っている。
@SuppressWarnings("java:S8692")
class CargoRepositoryTest extends PostgreSQLIntegrationTestBase {


    @Autowired
    private CargoRepository cargoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 業務日付を判断する時計。
     *
     * <p><strong>テストも同じ時計で「今日」を決める。</strong> JVM 既定の
     * タイムゾーンで {@code LocalDate.now()} を呼ぶと、CI（UTC）では
     * アプリの業務日付（Asia/Tokyo）と 1 日ずれる。
     */
    @Autowired
    private java.time.Clock clock;

    /**
     * {@code cargo.shipper_id} は {@code shipper} への外部キーであるため、先に親行を作る。
     *
     * <p><strong>Shipper のドメインモデルは使わない。</strong> 本テストの対象は
     * Booking の SQL であり、荷主は外部キーを満たすためのテストデータにすぎない。
     * 他 BC の集約を経由すると、Booking のテストが Shipper の仕様変更で落ちるようになり、
     * ArchUnit ルール 4 が禁じている BC 間の直接参照そのものになる。
     */
    private ShipperId 荷主を用意する() {
        ShipperId id = ShipperId.generate();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '山田太郎', ?, '090-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """,
                id.value(),
                "SHP-%06d".formatted(次の連番()),
                "cargo-repo-%s@example.com".formatted(id.value()));
        return id;
    }

    /**
     * 荷主コードは採番シーケンスから取る（V4）。
     *
     * <p>ここで MAX + 1 を使うと、シーケンスで採番する本番経路と番号が衝突し、
     * **他のテストが UNIQUE 制約で落ちる**（実測）。テストデータの採番であっても
     * 本番と同じ経路を使う。
     */
    private long 次の連番() {
        Long next = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        return next == null ? 1L : next;
    }

    private Cargo 予約を作る(ShipperId shipper, CargoSpecification spec) {
        return Cargo.book(new BookCargoCommand(
                shipper,
                spec,
                RouteSpecification.of(
                        Location.of("JPOSA"), Location.of("USLAX"),
                        LocalDate.now(clock).plusDays(30), LocalDate.now(clock))));
    }

    private static CargoSpecification 全項目入りの仕様() {
        return new CargoSpecification(
                CargoType.GENERAL,
                Weight.ofKilograms(new BigDecimal("1200.500")),
                Dimensions.ofCentimeters(
                        new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100")),
                Quantity.of(10),
                Description.of("電子部品"));
    }

    @Test
    void 予約を保存して読み戻せる() {
        Cargo cargo = 予約を作る(荷主を用意する(), 全項目入りの仕様());
        cargoRepository.save(cargo);

        Cargo reloaded = cargoRepository.findById(cargo.bookingId()).orElseThrow();

        assertThat(reloaded.bookingId()).isEqualTo(cargo.bookingId());
        assertThat(reloaded.shipperId()).isEqualTo(cargo.shipperId());
        assertThat(reloaded.bookingStatus()).isEqualTo(BookingStatus.PRELIMINARY);
        assertThat(reloaded.routeSpecification()).isEqualTo(cargo.routeSpecification());
    }

    /**
     * 重量は NUMERIC(10,3)。**丸めの差は請求額の差になる。**
     *
     * <p>H2 では通っても PostgreSQL では丸めが変わりうるため、実 DB で固定する。
     */
    @Test
    void 重量の小数第3位が保存で失われない() {
        Cargo cargo = 予約を作る(荷主を用意する(),
                new CargoSpecification(
                        CargoType.GENERAL, Weight.ofKilograms(new BigDecimal("0.001")),
                        null, null, null));
        cargoRepository.save(cargo);

        assertThat(cargoRepository.findById(cargo.bookingId()).orElseThrow()
                .cargoSpecification().weight().kilograms())
                .isEqualByComparingTo("0.001");
    }

    @Test
    void 寸法と個数と品名を保存して読み戻せる() {
        Cargo cargo = 予約を作る(荷主を用意する(), 全項目入りの仕様());
        cargoRepository.save(cargo);

        CargoSpecification spec =
                cargoRepository.findById(cargo.bookingId()).orElseThrow().cargoSpecification();

        assertThat(spec.dimensions().length()).isEqualByComparingTo("120");
        assertThat(spec.dimensions().width()).isEqualByComparingTo("80");
        assertThat(spec.dimensions().height()).isEqualByComparingTo("100");
        assertThat(spec.quantity().value()).isEqualTo(10);
        assertThat(spec.description().value()).isEqualTo("電子部品");
    }

    /** オプション項目が未入力でも保存・復元できる（V3 のカラムは NULL 許容）。 */
    @Test
    void 寸法と個数と品名が未入力でも保存して読み戻せる() {
        Cargo cargo = 予約を作る(荷主を用意する(),
                CargoSpecification.of(CargoType.GENERAL, Weight.ofKilograms(BigDecimal.ONE)));
        cargoRepository.save(cargo);

        CargoSpecification spec =
                cargoRepository.findById(cargo.bookingId()).orElseThrow().cargoSpecification();

        assertThat(spec.dimensions()).isNull();
        assertThat(spec.quantity()).isNull();
        assertThat(spec.description()).isNull();
    }

    @Test
    void 存在しない予約IDでは空を返す() {
        assertThat(cargoRepository.findById(BookingId.generate())).isEqualTo(Optional.empty());
    }

    @Test
    void キャンセルすると状態が更新される() {
        Cargo cargo = 予約を作る(荷主を用意する(), 全項目入りの仕様());
        cargoRepository.save(cargo);

        Cargo reloaded = cargoRepository.findById(cargo.bookingId()).orElseThrow();
        reloaded.cancel();

        assertThat(cargoRepository.update(reloaded)).isTrue();
        assertThat(cargoRepository.findById(cargo.bookingId()).orElseThrow().bookingStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }

    /**
     * 楽観的ロックが<strong>実際に競合を検出する</strong>ことを確認する。
     *
     * <p>「version カラムを足した」ことと「後勝ちを防げている」ことは別である。
     * 同じ version を読んだ 2 つの更新のうち、後の 1 つは必ず失敗しなければならない。
     */
    @Test
    void 同時更新の後勝ちを防ぐ() {
        Cargo cargo = 予約を作る(荷主を用意する(), 全項目入りの仕様());
        cargoRepository.save(cargo);

        // 2 人が同じ時点の予約を読み出す
        Cargo sessionA = cargoRepository.findById(cargo.bookingId()).orElseThrow();
        Cargo sessionB = cargoRepository.findById(cargo.bookingId()).orElseThrow();

        sessionA.cancel();
        sessionB.cancel();

        assertThat(cargoRepository.update(sessionA)).isTrue();
        assertThat(cargoRepository.update(sessionB))
                .as("先行する更新があったのに成功すると、後から書いた内容が黙って前の更新を消す")
                .isFalse();
    }


    /**
     * 引き渡し済み（経路割り当て待ち）の予約を保存して読み直す。
     *
     * <p><strong>状態の更新と経路の割り当ては別の操作である。</strong>
     * {@code updateRouting} は予約状態を書かない（経路を確定しても
     * {@code BookingStatus} は動かないため）ので、引き渡しは先に永続化する。
     * 更新で version が進むため、<strong>読み直してから経路を割り当てる</strong>。
     */
    private Cargo 引き渡し済みで保存する() {
        Cargo cargo = 予約を作る(荷主を用意する(), 全項目入りの仕様());
        cargoRepository.save(cargo);
        cargo.assignToRouting();
        assertThat(cargoRepository.update(cargo)).isTrue();
        return cargoRepository.findById(cargo.bookingId()).orElseThrow();
    }

    /**
     * 旅程と経路状態を保存して読み戻せる（US09 / US11）。
     *
     * <p><strong>読み戻しで落ちる値を作らない。</strong> 保存の経路だけを確かめると、
     * 読み直したときに経路状態が既定値へ戻る欠陥を見逃す（IT4 の教訓）。
     */
    @Test
    void 旅程と経路状態を保存して読み戻せる() {
        Cargo cargo = 引き渡し済みで保存する();
        cargo.assignItinerary(CargoItinerary.of(List.of(
                Leg.of("V-IT5-A", Location.of("JPOSA"), Location.of("CNSHA"),
                        Instant.parse("2026-10-01T10:00:00Z"),
                        Instant.parse("2026-10-03T06:00:00Z")),
                Leg.of("V-IT5-A", Location.of("CNSHA"), Location.of("USLAX"),
                        Instant.parse("2026-10-04T10:00:00Z"),
                        Instant.parse("2026-10-18T06:00:00Z")))));

        assertThat(cargoRepository.updateRouting(cargo)).isTrue();
        Cargo reloaded = cargoRepository.findById(cargo.bookingId()).orElseThrow();

        assertThat(reloaded.routingStatus()).isEqualTo(CargoRoutingStatus.ROUTED);
        assertThat(reloaded.cargoItinerary().legs())
                .extracting(leg -> leg.loadLocation().unlocode() + "->"
                        + leg.unloadLocation().unlocode())
                .containsExactly("JPOSA->CNSHA", "CNSHA->USLAX");
    }

    /**
     * <strong>区間は順序どおりに読み戻す。</strong>
     *
     * <p>順序が崩れると連結制約の検証で「つながっていない」と判定され、
     * <strong>保存できたものが読めなくなる</strong>。
     */
    @Test
    void 旅程の区間は順序どおりに読み戻される() {
        Cargo cargo = 引き渡し済みで保存する();
        cargo.assignItinerary(CargoItinerary.of(List.of(
                Leg.of("V-IT5-B", Location.of("JPOSA"), Location.of("HKHKG"),
                        Instant.parse("2026-11-01T10:00:00Z"),
                        Instant.parse("2026-11-04T06:00:00Z")),
                Leg.of("V-IT5-B", Location.of("HKHKG"), Location.of("SGSIN"),
                        Instant.parse("2026-11-05T10:00:00Z"),
                        Instant.parse("2026-11-08T06:00:00Z")),
                Leg.of("V-IT5-B", Location.of("SGSIN"), Location.of("USLAX"),
                        Instant.parse("2026-11-09T10:00:00Z"),
                        Instant.parse("2026-11-25T06:00:00Z")))));
        assertThat(cargoRepository.updateRouting(cargo)).isTrue();

        Cargo reloaded = cargoRepository.findById(cargo.bookingId()).orElseThrow();

        assertThat(reloaded.cargoItinerary().legs())
                .extracting(leg -> leg.loadLocation().unlocode())
                .containsExactly("JPOSA", "HKHKG", "SGSIN");
    }

    /** 経路を割り当て直すと、前の旅程は残らない。 */
    @Test
    void 旅程を割り当て直すと前の区間が残らない() {
        Cargo cargo = 引き渡し済みで保存する();
        cargo.assignItinerary(CargoItinerary.of(List.of(
                Leg.of("V-OLD", Location.of("JPOSA"), Location.of("CNSHA"),
                        Instant.parse("2026-10-01T10:00:00Z"),
                        Instant.parse("2026-10-03T06:00:00Z")),
                Leg.of("V-OLD", Location.of("CNSHA"), Location.of("USLAX"),
                        Instant.parse("2026-10-04T10:00:00Z"),
                        Instant.parse("2026-10-18T06:00:00Z")))));
        assertThat(cargoRepository.updateRouting(cargo)).isTrue();

        Cargo reloaded = cargoRepository.findById(cargo.bookingId()).orElseThrow();
        reloaded.assignItinerary(CargoItinerary.of(List.of(
                Leg.of("V-NEW", Location.of("JPOSA"), Location.of("USLAX"),
                        Instant.parse("2026-10-02T10:00:00Z"),
                        Instant.parse("2026-10-16T06:00:00Z")))));
        assertThat(cargoRepository.updateRouting(reloaded)).isTrue();

        assertThat(cargoRepository.findById(cargo.bookingId()).orElseThrow()
                .cargoItinerary().legs())
                .extracting(Leg::voyageNumber)
                .containsExactly("V-NEW");
    }
    /** 経路が割り当てられ、確定済みの予約を用意する（追跡番号の発行はここから始まる）。 */
    private Cargo 確定済みで保存する() {
        Cargo cargo = 引き渡し済みで保存する();
        cargo.assignItinerary(CargoItinerary.of(List.of(
                Leg.of("V-IT6", Location.of("JPOSA"), Location.of("USLAX"),
                        Instant.parse("2026-10-01T10:00:00Z"),
                        Instant.parse("2026-10-18T06:00:00Z")))));
        assertThat(cargoRepository.updateRouting(cargo)).isTrue();
        Cargo routed = cargoRepository.findById(cargo.bookingId()).orElseThrow();
        routed.confirm();
        assertThat(cargoRepository.update(routed)).isTrue();
        return cargoRepository.findById(cargo.bookingId()).orElseThrow();
    }

    /**
     * 追跡番号を保存して読み戻せる（US14）。
     *
     * <p><strong>読み戻しで落ちると、発行済みの追跡番号が消える。</strong>
     * 荷主に番号を伝えた後で「その番号は無い」と言うことになる。
     */
    @Test
    void 追跡番号を保存して読み戻せる() {
        Cargo cargo = 確定済みで保存する();
        cargo.issueTrackingNumber(new BookingTrackingNumber("TRK-20261001-0001"));

        assertThat(cargoRepository.updateTrackingNumber(cargo)).isTrue();

        Cargo reloaded = cargoRepository.findById(cargo.bookingId()).orElseThrow();
        assertThat(reloaded.bookingStatus()).isEqualTo(BookingStatus.TRACKING_ISSUED);
        assertThat(reloaded.trackingNumber().value()).isEqualTo("TRK-20261001-0001");
    }

    /**
     * 追跡番号から予約を引き当てられる（US15）。
     *
     * <p>荷役作業員が手に持っているのは追跡番号だけである。
     */
    @Test
    void 追跡番号から予約を引き当てられる() {
        Cargo cargo = 確定済みで保存する();
        cargo.issueTrackingNumber(new BookingTrackingNumber("TRK-20261001-0002"));
        assertThat(cargoRepository.updateTrackingNumber(cargo)).isTrue();

        assertThat(cargoRepository.findByTrackingNumber("TRK-20261001-0002"))
                .get()
                .extracting(c -> c.bookingId().value())
                .isEqualTo(cargo.bookingId().value());
    }

    /** 存在しない追跡番号では空を返す。**受入基準「追跡番号が存在しない場合のエラー」の土台。** */
    @Test
    void 存在しない追跡番号では空を返す() {
        assertThat(cargoRepository.findByTrackingNumber("TRK-20261001-9999")).isEmpty();
    }

    /**
     * <strong>同じ追跡番号を 2 つの予約に付けられない。</strong>
     *
     * <p>DB の一意制約で守る。重複すると、問い合わせに対して別の貨物の状態を答える。
     */
    @Test
    void 同じ追跡番号を2つの予約に付けられない() {
        Cargo first = 確定済みで保存する();
        first.issueTrackingNumber(new BookingTrackingNumber("TRK-20261001-0003"));
        assertThat(cargoRepository.updateTrackingNumber(first)).isTrue();

        Cargo second = 確定済みで保存する();
        second.issueTrackingNumber(new BookingTrackingNumber("TRK-20261001-0003"));

        // 戻り値を握りつぶさない。**一意制約は例外で返る**（false ではない）ことを
        // 明示するため、戻り値も評価したうえで例外を待つ。
        assertThatThrownBy(
                () -> assertThat(cargoRepository.updateTrackingNumber(second)).isTrue())
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
}
