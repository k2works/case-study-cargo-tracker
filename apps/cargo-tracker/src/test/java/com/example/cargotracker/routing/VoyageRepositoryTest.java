package com.example.cargotracker.routing;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 航海の永続化を実 PostgreSQL で検証する（ADR-003）。
 *
 * <p>集約は子コレクション（運送区間）を持つ。**航海だけ・区間だけが残る中途半端な状態を
 * 作らない**ことと、**読み戻したときに区間の順序が保たれる**ことが要点である。
 */
class VoyageRepositoryTest extends PostgreSQLIntegrationTestBase {

    private static final Location 大阪 = Location.of("JPOSA");
    private static final Location 上海 = Location.of("CNSHA");
    private static final Location ロサンゼルス = Location.of("USLAX");

    @Autowired
    private VoyageRepository repository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static CarrierMovement 区間(
            Location from, Location to, String departure, String arrival) {
        return CarrierMovement.of(from, to, Instant.parse(departure), Instant.parse(arrival));
    }

    private Voyage 登録する(Set<RoutingCargoType> types) {
        Voyage voyage = Voyage.register(new RegisterVoyageCommand(
                new VoyageNumber("V" + UUID.randomUUID().toString().substring(0, 8)),
                new VesselName("さくら丸"),
                new CarrierName("日本海運"),
                Schedule.of(List.of(
                        区間(大阪, 上海, "2026-09-01T10:00:00Z", "2026-09-03T08:00:00Z"),
                        区間(上海, ロサンゼルス, "2026-09-04T12:00:00Z", "2026-09-16T06:00:00Z"))),
                types,
                RoutingWeight.ofKilograms(new java.math.BigDecimal("100000"))));
        repository.save(voyage);
        return voyage;
    }

    @Test
    void 航海を保存して読み戻せる() {
        Voyage saved = 登録する(Set.of(RoutingCargoType.GENERAL));

        Voyage reloaded = repository.findByVoyageNumber(saved.voyageNumber()).orElseThrow();

        assertThat(reloaded.voyageNumber()).isEqualTo(saved.voyageNumber());
        assertThat(reloaded.vesselName().value()).isEqualTo("さくら丸");
        assertThat(reloaded.carrierName().value()).isEqualTo("日本海運");
    }

    /**
     * 運送区間の順序が保たれる。
     *
     * <p><strong>順序が崩れると、保存できたものが読めなくなる。</strong>
     * 連結制約の検証で「つながっていない」と判定されるためである。
     */
    @Test
    void 運送区間の順序が保たれる() {
        Voyage saved = 登録する(Set.of(RoutingCargoType.GENERAL));

        Voyage reloaded = repository.findByVoyageNumber(saved.voyageNumber()).orElseThrow();

        assertThat(reloaded.origin()).isEqualTo(大阪);
        assertThat(reloaded.destination()).isEqualTo(ロサンゼルス);
        assertThat(reloaded.callingPorts()).containsExactly(上海);
    }

    /** 時刻は時点として保たれる（TIMESTAMPTZ）。 */
    @Test
    void 出発時刻と到着時刻が保たれる() {
        Voyage saved = 登録する(Set.of(RoutingCargoType.GENERAL));

        Voyage reloaded = repository.findByVoyageNumber(saved.voyageNumber()).orElseThrow();

        assertThat(reloaded.departureTime(大阪)).contains(Instant.parse("2026-09-01T10:00:00Z"));
        assertThat(reloaded.arrivalTime(ロサンゼルス))
                .contains(Instant.parse("2026-09-16T06:00:00Z"));
    }

    /** 対応貨物種別が保たれる。**列挙子名で保存する**（序数だと並べ替えで意味が変わる）。 */
    @Test
    void 対応貨物種別が保たれる() {
        Voyage saved = 登録する(
                Set.of(RoutingCargoType.HAZARDOUS, RoutingCargoType.REFRIGERATED));

        Voyage reloaded = repository.findByVoyageNumber(saved.voyageNumber()).orElseThrow();

        assertThat(reloaded.accepts(RoutingCargoType.HAZARDOUS)).isTrue();
        assertThat(reloaded.accepts(RoutingCargoType.REFRIGERATED)).isTrue();
        assertThat(reloaded.accepts(RoutingCargoType.GENERAL)).isFalse();
    }

    @Test
    void 存在しない航海番号では空を返す() {
        assertThat(repository.findByVoyageNumber(new VoyageNumber("NOPE-999"))).isEmpty();
    }

    @Test
    void 航海番号の存在を判定できる() {
        Voyage saved = 登録する(Set.of(RoutingCargoType.GENERAL));

        assertThat(repository.existsByVoyageNumber(saved.voyageNumber())).isTrue();
        assertThat(repository.existsByVoyageNumber(new VoyageNumber("NOPE-999"))).isFalse();
    }

    /**
     * 港マスタに無い港では登録できない。
     *
     * <p>**外部キーが効いていることを確かめる。** 効いていなければ、実在しない港を
     * 経由する航海が登録でき、経路候補の算出でその港に着いたまま先へ進めなくなる。
     */
    @Test
    void 港マスタに無い港を含む航海は登録できない() {
        Voyage voyage = Voyage.register(new RegisterVoyageCommand(
                new VoyageNumber("V" + UUID.randomUUID().toString().substring(0, 8)),
                new VesselName("さくら丸"),
                new CarrierName("日本海運"),
                Schedule.of(List.of(区間(
                        Location.of("ZZZZZ"), 上海,
                        "2026-09-01T10:00:00Z", "2026-09-03T08:00:00Z"))),
                Set.of(RoutingCargoType.GENERAL),
                RoutingWeight.ofKilograms(new java.math.BigDecimal("100000"))));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.save(voyage))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /**
     * <strong>楽観的ロックが働く。</strong>
     *
     * <p>2 人が同じ便を同時に直したとき、後の更新が黙って前の更新を消す形にしない。
     * **「入れたこと」ではなく「働くこと」を確かめる**（IT6 の空振りの教訓）。
     * 画面のテストは先行更新を起こせないため、ここでしか判別できない。
     */
    @Test
    void 先行する更新があると上書きしない() {
        Voyage voyage = 登録する(Set.of(RoutingCargoType.GENERAL));
        Voyage loaded = repository.findByVoyageNumber(voyage.voyageNumber()).orElseThrow();

        // 先に別の担当者が更新する
        assertThat(repository.update(改名する(loaded, "ひかり丸"))).isTrue();

        // 手元の（古い）内容での更新は通らない
        assertThat(repository.update(改名する(loaded, "のぞみ丸"))).isFalse();
        assertThat(repository.findByVoyageNumber(voyage.voyageNumber()).orElseThrow()
                .vesselName().value()).isEqualTo("ひかり丸");
    }

    /**
     * 船名だけを変える。
     *
     * <p>現在時刻は<strong>スケジュールより前</strong>を渡す。ここで確かめたいのは
     * 楽観的ロックであって出港済みの守りではなく、時刻の選び方でテストの主題が
     * ぶれないようにする（出港済みの守りは {@code VoyageTest} が受け持つ）。
     */
    private static Voyage 改名する(Voyage voyage, String vesselName) {
        return voyage.reschedule(new RegisterVoyageCommand(
                voyage.voyageNumber(),
                new VesselName(vesselName),
                voyage.carrierName(),
                voyage.schedule(),
                voyage.acceptableCargoTypes(),
                voyage.capacityWeight()),
                voyage.schedule().carrierMovements().getFirst().departureTime()
                        .minusSeconds(1));
    }

    /**
     * <strong>キャンセルした予約は便の枠を占め続けない</strong>（US30。X3）。
     *
     * <p>UC22 の成功保証は「確保していた船腹が解放される」と定めている。
     * <strong>解放する処理が無かったのではなく、集計の条件が抜けていた。</strong>
     * 空き容量は経路の状態（{@code routing_status = 'ROUTED'}）だけで数えており、
     * <strong>キャンセル済みの予約を除いていない</strong>。
     *
     * <p>結果として、キャンセルした貨物の重量がその便に載り続け、
     * <strong>他の荷主が積めるはずの枠が埋まったままになる</strong>。
     */
    @Test
    void キャンセルした予約は割当済み重量に数えない() {
        Voyage voyage = 登録する(Set.of(RoutingCargoType.GENERAL));
        UUID bookingId = 割当済みの貨物(voyage.voyageNumber().value(), 3000);

        assertThat(repository.findAssignedWeights(List.of(voyage.voyageNumber()))
                .get(voyage.voyageNumber()).kilograms())
                .as("割り当てた分は枠を使う")
                .isEqualByComparingTo(new java.math.BigDecimal("3000"));

        jdbcTemplate.update(
                "UPDATE cargo SET booking_status = 'CANCELLED' WHERE booking_id = ?",
                bookingId);

        assertThat(repository.findAssignedWeights(List.of(voyage.voyageNumber())))
                .as("**キャンセルすれば船腹が戻る**（UC22 の成功保証）")
                .doesNotContainKey(voyage.voyageNumber());
    }

    /** その便に割り当て済みの貨物を 1 件作り、予約 ID を返す。 */
    private UUID 割当済みの貨物(String voyageNumber, int weightKg) {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('shipper_code_seq')", Long.class);
        UUID shipperId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO shipper (
                    id, shipper_code, shipper_type, name, email, phone,
                    address_country, address_postal_code, address_region,
                    address_city, address_street)
                VALUES (?, ?, 'INDIVIDUAL', '船腹テスト商事', ?, '06-1234-5678',
                        'JP', '530-0001', '大阪府', '大阪市北区', '梅田 1-1-1')
                """, shipperId, "SHP-%06d".formatted(seq), "capacity-%d@example.com".formatted(seq));

        UUID bookingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO cargo (
                    booking_id, shipper_id, cargo_type, weight,
                    origin_unlocode, destination_unlocode, arrival_deadline,
                    booking_status, routing_status, tracking_number)
                VALUES (?, ?, 'GENERAL', ?, 'JPOSA', 'USLAX', CURRENT_DATE + 60,
                        'IN_TRANSIT', 'ROUTED', ?)
                """, bookingId, shipperId, weightKg, "TRK-CAP-%d".formatted(seq));

        Long cargoId = jdbcTemplate.queryForObject(
                "SELECT id FROM cargo WHERE booking_id = ?", Long.class, bookingId);
        jdbcTemplate.update("""
                INSERT INTO leg (
                    cargo_id, voyage_number, load_location_unlocode,
                    unload_location_unlocode, load_time, unload_time, seq_number)
                VALUES (?, ?, 'JPOSA', 'USLAX',
                        TIMESTAMP WITH TIME ZONE '2026-09-01 19:00:00+09',
                        TIMESTAMP WITH TIME ZONE '2026-09-16 15:00:00+09', 1)
                """, cargoId, voyageNumber);
        return bookingId;
    }
}
