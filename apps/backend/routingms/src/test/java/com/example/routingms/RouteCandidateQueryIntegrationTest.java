package com.example.routingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.routingms.application.port.VoyageRepository;
import com.example.routingms.domain.model.CargoType;
import com.example.routingms.domain.model.CarrierMovement;
import com.example.routingms.domain.model.RouteSearchSpecification;
import com.example.routingms.domain.model.Schedule;
import com.example.routingms.domain.model.TransitPath;
import com.example.routingms.domain.model.TransitPathFinder;
import com.example.routingms.domain.model.Voyage;
import com.example.routingms.domain.model.VoyageNumber;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 経路探索の対象を引くクエリ（US08 Phase 2）。本番と同じ PostgreSQL に対して確かめる。
 *
 * <p>絞りが<strong>集約の判定より狭いと候補が落ちる</strong>。落ちた候補は画面に出ないため、
 * 経路設計者には「その経路は無い」としか見えない。ここで確かめるのは「広く引けているか」であり、
 * 運べるかどうかの判定は集約が行う（同じ判定を SQL に書き直さない）。
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("経路探索の対象を引く")
class RouteCandidateQueryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private VoyageRepository repository;

    private final TransitPathFinder finder = new TransitPathFinder();

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final Location SHANGHAI = Location.of("CNSHA", "Shanghai");

    private static CarrierMovement leg(Location from, Location to, String departure, String arrival) {
        return CarrierMovement.of(from, to, Instant.parse(departure), Instant.parse(arrival));
    }

    private Voyage save(String number, Set<CargoType> supported, CarrierMovement... legs) {
        return repository.save(Voyage.register(VoyageNumber.of(number), "船 " + number, "運送会社",
                supported, Schedule.of(List.of(legs))));
    }

    /** 探索の起点にする「いま」。テストのデータはすべてこれより後に出発する。 */
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    private static RouteSearchSpecification spec(CargoType type, String deadline) {
        return RouteSearchSpecification.of(TOKYO, LOS_ANGELES, Instant.parse(deadline), type);
    }

    @Test
    @DisplayName("貨物種別に対応し、期限より前に出る航海を引く")
    void findsVoyagesWorthExploring() {
        save("Q-GENERAL", Set.of(CargoType.GENERAL),
                leg(TOKYO, LOS_ANGELES, "2026-11-01T09:00:00Z", "2026-11-15T09:00:00Z"));
        save("Q-HAZARD", Set.of(CargoType.HAZARDOUS),
                leg(TOKYO, LOS_ANGELES, "2026-11-02T09:00:00Z", "2026-11-16T09:00:00Z"));
        save("Q-TOO-LATE", Set.of(CargoType.GENERAL),
                leg(TOKYO, LOS_ANGELES, "2026-12-20T09:00:00Z", "2027-01-05T09:00:00Z"));

        List<Voyage> candidates =
                repository.findCandidates(spec(CargoType.GENERAL, "2026-11-30T00:00:00Z"), NOW);

        assertThat(candidates).extracting(voyage -> voyage.voyageNumber().value())
                .contains("Q-GENERAL")
                .doesNotContain("Q-HAZARD", "Q-TOO-LATE");
    }

    /**
     * 往復航海の復路を落とさない。
     *
     * <p>IT3 では SQL の絞りと集約の判定が食い違い、集約側が復路を運べないと答えていた。
     * ここでは<strong>引いた航海に対して集約が復路を運べると答える</strong>ことまで見る。
     * SQL だけを見ても、集約だけを見ても、この食い違いは判別できない。
     */
    @Test
    @DisplayName("往復航海は復路の探索でも引けて、集約も運べると答える")
    void keepsRoundTripsForTheReturnLeg() {
        save("Q-ROUND", Set.of(CargoType.GENERAL),
                leg(TOKYO, LOS_ANGELES, "2026-11-01T09:00:00Z", "2026-11-15T09:00:00Z"),
                leg(LOS_ANGELES, TOKYO, "2026-11-18T09:00:00Z", "2026-12-02T09:00:00Z"));

        RouteSearchSpecification returning = RouteSearchSpecification.of(
                LOS_ANGELES, TOKYO, Instant.parse("2026-12-31T00:00:00Z"), CargoType.GENERAL);

        List<Voyage> candidates = repository.findCandidates(returning, NOW);

        assertThat(candidates).extracting(voyage -> voyage.voyageNumber().value())
                .contains("Q-ROUND");
        assertThat(candidates).filteredOn(voyage -> voyage.voyageNumber().value().equals("Q-ROUND"))
                .allSatisfy(voyage -> assertThat(voyage.connects(LOS_ANGELES, TOKYO)).isTrue());

        List<TransitPath> paths = finder.find(returning, candidates);
        assertThat(paths).isNotEmpty();
    }

    /**
     * 引いた航海をそのまま探索に渡して、経路が出ることまで通す。
     *
     * <p>クエリと集約を別々に検査すると、両方が緑でも噛み合わないことがある。
     */
    @Test
    @DisplayName("引いた航海から積み替えの経路が組める")
    void foundVoyagesProduceRoutes() {
        save("Q-A", Set.of(CargoType.GENERAL),
                leg(TOKYO, SHANGHAI, "2026-11-01T09:00:00Z", "2026-11-03T09:00:00Z"));
        save("Q-B", Set.of(CargoType.GENERAL),
                leg(SHANGHAI, LOS_ANGELES, "2026-11-04T09:00:00Z", "2026-11-20T09:00:00Z"));

        RouteSearchSpecification specification = spec(CargoType.GENERAL, "2026-11-30T00:00:00Z");
        List<TransitPath> paths = finder.find(specification, repository.findCandidates(specification, NOW));

        assertThat(paths).anySatisfy(path ->
                assertThat(path.transitPorts()).containsExactly(SHANGHAI));
    }

    /**
     * すでに出発した航海を引かない。
     *
     * <p>押さえられない船を候補の材料にすると、経路設計者は存在しない選択肢を見る。
     * 一覧（US07）が既定で「本日以降」に絞っているのと同じ扱いにする。
     */
    @Test
    @DisplayName("すでに出発した航海は探索の対象にしない")
    void excludesVoyagesThatHaveAlreadyDeparted() {
        save("Q-DEPARTED", Set.of(CargoType.GENERAL),
                leg(TOKYO, LOS_ANGELES, "2026-09-20T09:00:00Z", "2026-11-05T09:00:00Z"));

        List<Voyage> candidates =
                repository.findCandidates(spec(CargoType.GENERAL, "2026-11-30T00:00:00Z"), NOW);

        assertThat(candidates).extracting(voyage -> voyage.voyageNumber().value())
                .doesNotContain("Q-DEPARTED");
    }
}