package com.example.routingms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.routingms.domain.repository.VoyageRepository;
import com.example.routingms.domain.model.valueobjects.VoyageSearchCriteria;
import com.example.routingms.domain.model.valueobjects.CargoType;
import com.example.routingms.domain.model.valueobjects.CarrierMovement;
import com.example.routingms.domain.model.valueobjects.Schedule;
import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;
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
 * 航海の永続化（US24）。本番と同じ PostgreSQL に対して確かめる。
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@DisplayName("航海の永続化")
class VoyagePersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private VoyageRepository repository;

    private static final Location TOKYO = Location.of("JPTYO", "Tokyo");
    private static final Location LOS_ANGELES = Location.of("USLAX", "Los Angeles");
    private static final Location SHANGHAI = Location.of("CNSHA", "Shanghai");

    private static Voyage voyage(String number, Set<CargoType> supported,
            List<CarrierMovement> movements) {
        return Voyage.register(VoyageNumber.of(number), "さくら丸", "日本郵船",
                supported, Schedule.of(movements));
    }

    private static CarrierMovement leg(Location from, Location to, String departure, String arrival) {
        return CarrierMovement.of(from, to, Instant.parse(departure), Instant.parse(arrival));
    }

    @Test
    @DisplayName("登録した航海を、寄港の順序も含めて読み戻せる")
    void savesAndRestores() {
        repository.save(voyage("V1001", Set.of(CargoType.GENERAL, CargoType.HAZARDOUS), List.of(
                leg(TOKYO, SHANGHAI, "2026-10-01T09:00:00Z", "2026-10-03T18:00:00Z"),
                leg(SHANGHAI, LOS_ANGELES, "2026-10-04T08:00:00Z", "2026-10-18T12:00:00Z"))));

        Voyage restored = repository.findByVoyageNumber(VoyageNumber.of("V1001")).orElseThrow();

        assertThat(restored.vesselName()).isEqualTo("さくら丸");
        assertThat(restored.carrierName()).isEqualTo("日本郵船");
        assertThat(restored.supportedCargoTypes())
                .containsExactlyInAnyOrder(CargoType.GENERAL, CargoType.HAZARDOUS);
        // 順序が失われると、同じ港の集合でも別の航海になる
        assertThat(restored.schedule().callingPorts())
                .containsExactly(TOKYO, SHANGHAI, LOS_ANGELES);
        assertThat(restored.connects(TOKYO, LOS_ANGELES)).isTrue();
        assertThat(restored.connects(LOS_ANGELES, TOKYO)).isFalse();
    }

    /**
     * 上書きしても区間が二重にならないこと。
     *
     * <p>区間を消さずに入れ直すと、寄港地が倍に増えた航海ができる。しかも順序は保たれるため、
     * 一覧を見ただけでは気づけない。
     */
    @Test
    @DisplayName("同じ航海番号で保存し直すと、前の寄港地は残らない")
    void replacesMovementsOnUpdate() {
        repository.save(voyage("V1002", Set.of(CargoType.GENERAL), List.of(
                leg(TOKYO, SHANGHAI, "2026-10-01T09:00:00Z", "2026-10-03T18:00:00Z"),
                leg(SHANGHAI, LOS_ANGELES, "2026-10-04T08:00:00Z", "2026-10-18T12:00:00Z"))));

        repository.save(voyage("V1002", Set.of(CargoType.REFRIGERATED), List.of(
                leg(TOKYO, LOS_ANGELES, "2026-10-02T09:00:00Z", "2026-10-20T18:00:00Z"))));

        Voyage restored = repository.findByVoyageNumber(VoyageNumber.of("V1002")).orElseThrow();
        assertThat(restored.schedule().callingPorts()).containsExactly(TOKYO, LOS_ANGELES);
        assertThat(restored.supportedCargoTypes()).containsExactly(CargoType.REFRIGERATED);
    }

    @Test
    @DisplayName("寄港の向きで絞る（同じ港に寄ることと、その向きに運べることは別）")
    void searchesByDirection() {
        repository.save(voyage("V1003", Set.of(CargoType.GENERAL), List.of(
                leg(TOKYO, LOS_ANGELES, "2026-11-01T09:00:00Z", "2026-11-18T12:00:00Z"))));
        repository.save(voyage("V1004", Set.of(CargoType.GENERAL), List.of(
                leg(LOS_ANGELES, TOKYO, "2026-11-01T09:00:00Z", "2026-11-18T12:00:00Z"))));

        List<Voyage> eastbound = repository.search(new VoyageSearchCriteria(
                "JPTYO", "USLAX", null, null, null), 50);

        assertThat(eastbound).extracting(v -> v.voyageNumber().value()).contains("V1003");
        assertThat(eastbound).extracting(v -> v.voyageNumber().value()).doesNotContain("V1004");
    }

    @Test
    @DisplayName("対応していない貨物種別の航海は検索結果に出ない")
    void searchesByCargoType() {
        repository.save(voyage("V1005", Set.of(CargoType.GENERAL), List.of(
                leg(TOKYO, SHANGHAI, "2026-12-01T09:00:00Z", "2026-12-03T18:00:00Z"))));
        repository.save(voyage("V1006", Set.of(CargoType.GENERAL, CargoType.HAZARDOUS), List.of(
                leg(TOKYO, SHANGHAI, "2026-12-02T09:00:00Z", "2026-12-04T18:00:00Z"))));

        List<Voyage> hazardous = repository.search(new VoyageSearchCriteria(
                "JPTYO", "CNSHA", null, null, CargoType.HAZARDOUS), 50);

        assertThat(hazardous).extracting(v -> v.voyageNumber().value())
                .contains("V1006")
                .doesNotContain("V1005");
    }

    /**
     * 種別の突き合わせが前方一致にならないこと。
     *
     * <p>カンマ区切りの列を素朴に LIKE '%X%' で見ると、別の種別を含む行まで拾う。
     * ここでは値の顔ぶれ上は起きにくいが、種別が増えたときに静かに壊れる形である。
     */
    @Test
    @DisplayName("貨物種別は区切りごとに突き合わせる（部分一致で拾わない）")
    void matchesCargoTypeByWholeToken() {
        repository.save(voyage("V1007", Set.of(CargoType.REFRIGERATED), List.of(
                leg(TOKYO, SHANGHAI, "2027-01-01T09:00:00Z", "2027-01-03T18:00:00Z"))));

        assertThat(repository.search(
                        new VoyageSearchCriteria(null, null, null, null, CargoType.GENERAL), 50))
                .extracting(v -> v.voyageNumber().value())
                .doesNotContain("V1007");
    }

    @Test
    @DisplayName("出発期間で絞る")
    void searchesByDeparturePeriod() {
        repository.save(voyage("V1008", Set.of(CargoType.GENERAL), List.of(
                leg(TOKYO, SHANGHAI, "2027-02-01T09:00:00Z", "2027-02-03T18:00:00Z"))));
        repository.save(voyage("V1009", Set.of(CargoType.GENERAL), List.of(
                leg(TOKYO, SHANGHAI, "2027-03-01T09:00:00Z", "2027-03-03T18:00:00Z"))));

        List<Voyage> february = repository.search(new VoyageSearchCriteria(
                null, null, Instant.parse("2027-02-01T00:00:00Z"),
                Instant.parse("2027-02-28T23:59:59Z"), null), 50);

        assertThat(february).extracting(v -> v.voyageNumber().value())
                .contains("V1008")
                .doesNotContain("V1009");
    }

    @Test
    @DisplayName("条件に合う総数は上限で切る前の数を返す")
    void countsBeforeTruncation() {
        repository.save(voyage("V1010", Set.of(CargoType.GENERAL), List.of(
                leg(TOKYO, SHANGHAI, "2027-04-01T09:00:00Z", "2027-04-03T18:00:00Z"))));
        repository.save(voyage("V1011", Set.of(CargoType.GENERAL), List.of(
                leg(TOKYO, SHANGHAI, "2027-04-02T09:00:00Z", "2027-04-04T18:00:00Z"))));

        VoyageSearchCriteria criteria = new VoyageSearchCriteria(
                "JPTYO", "CNSHA", Instant.parse("2027-04-01T00:00:00Z"),
                Instant.parse("2027-04-30T00:00:00Z"), null);

        assertThat(repository.search(criteria, 1)).hasSize(1);
        assertThat(repository.countMatching(criteria)).isEqualTo(2);
    }
}
