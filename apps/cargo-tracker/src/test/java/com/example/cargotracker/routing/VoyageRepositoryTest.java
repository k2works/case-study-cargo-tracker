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
}
