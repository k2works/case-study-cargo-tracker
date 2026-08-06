package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.booking.domain.model.BookingStatus;
import com.example.cargotracker.booking.domain.model.Cargo;
import com.example.cargotracker.booking.domain.model.CargoSpecification;
import com.example.cargotracker.booking.domain.model.CargoType;
import com.example.cargotracker.booking.domain.model.Description;
import com.example.cargotracker.booking.domain.model.Dimensions;
import com.example.cargotracker.booking.domain.model.Quantity;
import com.example.cargotracker.booking.domain.model.RouteSpecification;
import com.example.cargotracker.booking.domain.model.Weight;
import com.example.cargotracker.booking.domain.repository.CargoRepository;
import com.example.cargotracker.shared.domain.model.Location;
import com.example.cargotracker.shared.domain.model.ShipperId;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class CargoRepositoryTest extends PostgreSQLIntegrationTestBase {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private CargoRepository cargoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                        Location.of("JPOSA"), Location.of("USLAX"), TODAY.plusDays(30), TODAY)));
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
}
