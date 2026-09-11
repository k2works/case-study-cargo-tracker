package com.example.cargotracker.handling.infrastructure.projection;

import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.infrastructure.persistence.CargoSnapshotMapper;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 貨物の写し（US15 / [ADR-0012]）。
 *
 * <p><b>契約イベントから作れることを実 DB で確かめる。</b> 集約の検査では
 * 「投影がどう見えるか」を判別しない。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CargoSnapshotProjectionIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-08T01:00:00Z");

    @Autowired
    private CargoSnapshotProjection projection;

    @Autowired
    private CargoSnapshotMapper cargos;

    private static TrackingInitializedEvent initialized(String trackingNumber) {
        return new TrackingInitializedEvent(trackingNumber, "b-" + System.nanoTime(),
                "SHP-000001", "JPTYO", "USNYC", "GENERAL",
                new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                Instant.parse("2026-09-10T09:00:00Z"),
                                Instant.parse("2026-09-16T08:00:00Z")),
                        new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                Instant.parse("2026-09-17T06:00:00Z"),
                                Instant.parse("2026-09-24T18:00:00Z"))),
                AT);
    }

    @Test
    @DisplayName("ADR-0012 決定 1: 契約イベント TrackingInitializedEvent から作られる")
    void buildsFromTrackingInitialized() {
        String trackingNumber = "TRK-S" + System.nanoTime() % 1000000000L;

        projection.on(initialized(trackingNumber), "evt-1");

        var row = cargos.findByTrackingNumber(trackingNumber);
        assertThat(row).isNotNull();
        assertThat(row.originUnlocode()).isEqualTo("JPTYO");
        assertThat(row.destinationUnlocode()).isEqualTo("USNYC");
        assertThat(row.cargoType()).isEqualTo("GENERAL");
        assertThat(cargos.findLegs(trackingNumber))
                .extracting(CargoSnapshotMapper.CargoSnapshotLegRow::voyageNumber)
                .containsExactly("V-MOL-001", "V-ONE-002");
    }

    @Test
    @DisplayName("ADR-0012 決定 3: キャンセルの既定は false（書き手は US30・IT15）")
    void defaultsToNotCancelled() {
        String trackingNumber = "TRK-S" + System.nanoTime() % 1000000000L;

        projection.on(initialized(trackingNumber), "evt-2");

        assertThat(cargos.findByTrackingNumber(trackingNumber).cancelled()).isFalse();
    }

    @Test
    @DisplayName("S50 の起点: この航海がこの港で扱う貨物を引ける（積む港からも）")
    void findsCargosOnVoyage() {
        // **追跡番号は現場が持っていない。** 荷役作業員は船と港から始める。
        String mine = "TRK-S" + System.nanoTime() % 1000000000L;
        projection.on(initialized(mine), "evt-3");

        // この船は東京で積んでシンガポールで降ろす。**どちらの港でも作業がある**
        // ——降ろす港だけで引くと、受領と積込をする港から画面が始まらない
        // （種別は 3 つ選べるのに対象が出ない。IT9 のレビューで発見）。
        assertThat(cargos.findOnVoyage("V-MOL-001", "SGSIN"))
                .extracting(CargoSnapshotMapper.CargoSnapshotRow::trackingNumber)
                .contains(mine);
        assertThat(cargos.findOnVoyage("V-MOL-001", "JPTYO"))
                .extracting(CargoSnapshotMapper.CargoSnapshotRow::trackingNumber)
                .contains(mine);
        // その船が寄らない港では引けない。
        assertThat(cargos.findOnVoyage("V-MOL-001", "DEHAM"))
                .extracting(CargoSnapshotMapper.CargoSnapshotRow::trackingNumber)
                .doesNotContain(mine);
    }

    @Test
    @DisplayName("S02 荷役: 積む港と降ろす港の両方が航海の一覧に出る")
    void listsBothLoadAndUnloadPorts() {
        String mine = "TRK-S" + System.nanoTime() % 1000000000L;
        projection.on(initialized(mine), "evt-ports");

        assertThat(cargos.findVoyagePorts(200))
                .extracting(CargoSnapshotMapper.VoyagePortRow::voyageNumber,
                        CargoSnapshotMapper.VoyagePortRow::unlocode)
                .contains(org.assertj.core.groups.Tuple.tuple("V-MOL-001", "JPTYO"),
                        org.assertj.core.groups.Tuple.tuple("V-MOL-001", "SGSIN"),
                        org.assertj.core.groups.Tuple.tuple("V-ONE-002", "SGSIN"),
                        org.assertj.core.groups.Tuple.tuple("V-ONE-002", "USNYC"));
    }

    @Test
    @DisplayName("リプレイで行も区間も増えない")
    void isIdempotentOnReplay() {
        String trackingNumber = "TRK-S" + System.nanoTime() % 1000000000L;
        var event = initialized(trackingNumber);

        projection.on(event, "evt-4");
        projection.on(event, "evt-4");

        assertThat(cargos.findByTrackingNumber(trackingNumber)).isNotNull();
        assertThat(cargos.findLegs(trackingNumber)).hasSize(2);
    }

    @Test
    @DisplayName("ADR-0012 決定 4: 写しは港と航海だけを持ち、時刻は持たない")
    void keepsOnlyPortsAndVoyages() {
        // **予定の時刻は写さない。** 航海の予定は routingms が変える（US25）ので、
        // 写した時刻は黙って古くなる。荷役が要るのは「この船がこの港で降ろすか」
        // だけで、時刻は要らない（判断の材料にしない）。
        String trackingNumber = "TRK-P" + System.nanoTime() % 1000000000L;
        projection.on(initialized(trackingNumber), "evt-adr12");

        assertThat(CargoSnapshotMapper.CargoSnapshotLegRow.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getType)
                .as("区間に時刻の型があると、予定の時刻を写す道ができる")
                .doesNotContain(Instant.class);
        assertThat(cargos.findLegs(trackingNumber))
                .extracting(CargoSnapshotMapper.CargoSnapshotLegRow::voyageNumber,
                        CargoSnapshotMapper.CargoSnapshotLegRow::unloadUnlocode)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("V-MOL-001", "SGSIN"),
                        org.assertj.core.groups.Tuple.tuple("V-ONE-002", "USNYC"));
    }
}
