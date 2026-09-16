package com.example.cargotracker.handling.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.infrastructure.persistence.CargoSnapshotMapper;
import com.example.cargotracker.shared.contract.event.HandlingActivityRegisteredEvent;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * シミュレーション由来の貨物を荷役の作業から外す（US33 §受入基準 3 /
 * [ADR-0020] 決定 4）。
 *
 * <p><b>読み口を数え上げる。</b> 荷役は荷主で絞らない——現場は船と港から
 * 仕事を始める。だから {@code cargo_snapshot} を読む口は<b>すべて</b>外す。
 * 1 つでも残ると、US36 の継続実行が作った架空の貨物が本物の作業に混ざる。</p>
 *
 * <table>
 *   <caption>handlingms で cargo_snapshot を読む口</caption>
 *   <tr><th>読み口</th><th>外すか</th><th>理由</th></tr>
 *   <tr><td>findOnVoyage（S50 作業一覧）</td><td>外す</td><td>船と港で引く。荷主で絞らない</td></tr>
 *   <tr><td>findVoyagePorts（S02 ダッシュボード）</td><td>外す</td><td>同上。件数が架空の貨物で膨らむ</td></tr>
 *   <tr><td>findAwaitingClaim（S54 引取待ち）</td><td>外す</td><td>同上</td></tr>
 *   <tr><td>findByTrackingNumber（単票）</td><td>外さない</td>
 *       <td>US34 の実行結果が工程ごとにここへ辿る。シミュレーション自身も荷役を記録する</td></tr>
 * </table>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SimulatedOriginExcludedIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-15T01:00:00Z");

    @Autowired
    private ShipperOriginProjection shipperOrigins;

    @Autowired
    private CargoSnapshotProjection projection;

    @Autowired
    private HandlingActivityProjection activities;

    @Autowired
    private CargoSnapshotMapper cargos;

    private String registerShipper(boolean simulated) {
        String shipperId = (simulated ? "SHP-S-" : "SHP-R-") + System.nanoTime();
        shipperOrigins.on(new ShipperRegisteredEvent(shipperId, "INDIVIDUAL",
                "検査用商事", shipperId + "@example.com", "03-0000-0000",
                "東京都", null, null, simulated));
        return shipperId;
    }

    /** 航海番号は VARCHAR(20)。<b>接頭辞 + 連番は収める</b>（IT16 で 2 度はみ出した）。 */
    private static String voyage(String prefix) {
        return prefix + "-" + (System.nanoTime() % 100000000L);
    }

    private String snapshot(String shipperId, String voyageNumber, String destination) {
        String trackingNumber = "T-H-" + System.nanoTime();
        projection.on(new TrackingInitializedEvent(trackingNumber,
                "b-h-" + System.nanoTime(), shipperId, "JPTYO", destination, "GENERAL",
                new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg(voyageNumber, "JPTYO", destination,
                        Instant.parse("2026-09-20T09:00:00Z"),
                        Instant.parse("2026-09-28T08:00:00Z"))),
                AT), "evt-" + System.nanoTime());
        return trackingNumber;
    }

    @Test
    @DisplayName("US33 §3: シミュレーション由来の貨物は、航海の作業一覧（S50）に出ない")
    void excludesSimulatedFromTheVoyageWorklist() {
        String voyage = voyage("VSM");
        String simulated = snapshot(registerShipper(true), voyage, "USNYC");
        String real = snapshot(registerShipper(false), voyage, "USNYC");

        assertThat(cargos.findOnVoyage(voyage, "USNYC"))
                .extracting(CargoSnapshotMapper.CargoSnapshotRow::trackingNumber)
                .containsExactly(real)
                .doesNotContain(simulated);
        assertThat(cargos.findByTrackingNumber(simulated))
                .as("単票は外さない——US34 の実行結果が辿る先である")
                .isNotNull();
    }

    @Test
    @DisplayName("US33 §3: 荷役のダッシュボード（S02）の件数に、シミュレーションを数えない")
    void excludesSimulatedFromTheDashboardCounts() {
        String voyage = voyage("VDS");
        snapshot(registerShipper(true), voyage, "USNYC");
        snapshot(registerShipper(false), voyage, "USNYC");

        assertThat(cargos.findVoyagePorts(10000))
                .filteredOn(row -> row.voyageNumber().equals(voyage))
                .allSatisfy(row -> assertThat(row.cargoCount())
                        .as("積む港・降ろす港のどちらも本物 1 件だけ")
                        .isEqualTo(1));
    }

    /**
     * 目的港で荷降しを記録する。
     *
     * <p><b>これが無いと検査が空振りする。</b> 引取待ちは「荷降しが済んで引取が
     * まだ」で決まるので、荷降しを書かない貨物はそもそも一覧に出ない——外す守りを
     * 壊しても緑のままだった（実測）。</p>
     */
    private void unloadAt(String trackingNumber, String unLocode, String voyageNumber) {
        activities.on(new HandlingActivityRegisteredEvent(
                "act-" + System.nanoTime(), trackingNumber, "b-h-" + System.nanoTime(),
                "UNLOAD", unLocode, voyageNumber, false, true, "op-1", AT, AT));
    }

    @Test
    @DisplayName("US33 §3: 引取待ち（S54）にシミュレーションを出さない")
    void excludesSimulatedFromAwaitingClaim() {
        String destination = "AUMEL";
        String simulatedVoyage = voyage("VCS");
        String realVoyage = voyage("VCR");
        String simulated = snapshot(registerShipper(true), simulatedVoyage, destination);
        String real = snapshot(registerShipper(false), realVoyage, destination);
        unloadAt(simulated, destination, simulatedVoyage);
        unloadAt(real, destination, realVoyage);

        assertThat(cargos.findAwaitingClaim(destination))
                .extracting(CargoSnapshotMapper.CargoSnapshotRow::trackingNumber)
                .contains(real)
                .doesNotContain(simulated);
    }
}
