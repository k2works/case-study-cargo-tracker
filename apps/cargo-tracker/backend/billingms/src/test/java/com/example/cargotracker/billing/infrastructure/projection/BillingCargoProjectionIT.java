package com.example.cargotracker.billing.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.infrastructure.persistence.BillingCargoSnapshotMapper;
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
 * 請求が読む貨物スナップショット（US21 §受入基準 2・3 の入力）。
 *
 * <p><b>引渡のイベントだけでは料金を数えられない。</b> {@code CargoDeliveredEvent} は
 * 追跡番号・予約・時刻・場所しか運ばない。式が要るのは区間・重量・貨物種別なので、
 * {@code TrackingInitializedEvent} を購読して自分の読み取りモデルに写す
 * （ADR-0012 と同じ形）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BillingCargoProjectionIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-08T01:00:00Z");

    @Autowired
    private BillingCargoProjection projection;

    @Autowired
    private BillingCargoSnapshotMapper cargos;

    private TrackingInitializedEvent initialized(String trackingNumber, BigDecimal weightKg) {
        return new TrackingInitializedEvent(trackingNumber, "b-" + trackingNumber, "SHP-000001",
                "JPTYO", "USNYC", "GENERAL", weightKg,
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                AT.plusSeconds(86_400), AT.plusSeconds(600_000)),
                        new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                AT.plusSeconds(700_000), AT.plusSeconds(1_400_000))),
                AT);
    }

    @Test
    @DisplayName("US21 §2: 区間・重量・貨物種別が写る（料金の式が要る材料）")
    void projectsWhatTheFormulaNeeds() {
        String number = "TRK-BC-" + System.nanoTime() % 1000000000L;

        projection.on(initialized(number, new BigDecimal("1200")), "evt-" + System.nanoTime());

        var row = cargos.find(number);
        assertThat(row).isNotNull();
        assertThat(row.weightKg()).isEqualByComparingTo(new BigDecimal("1200"));
        assertThat(row.cargoType()).isEqualTo("GENERAL");
        assertThat(row.originUnLocode()).isEqualTo("JPTYO");
        assertThat(row.destinationUnLocode()).isEqualTo("USNYC");
        // **区間は順序を持つ。** 地域係数は区間ごとに数えるので、順が崩れると
        // 数え方そのものが変わる。
        assertThat(cargos.findLegs(number))
                .extracting(BillingCargoSnapshotMapper.LegRow::unloadUnLocode)
                .containsExactly("SGSIN", "USNYC");
    }

    @Test
    @DisplayName("重量が載っていない古いイベントも写る（NULL のまま残す）")
    void keepsMissingWeightAsNull() {
        // **勝手に 0 で埋めない。** 0 で埋めると重量係数が下限に落ち、
        // **足りない重量で安い請求**が黙って出る。NULL のままにして、
        // 算出のときに断る。
        String number = "TRK-BCW-" + System.nanoTime() % 1000000000L;

        projection.on(initialized(number, null), "evt-" + System.nanoTime());

        assertThat(cargos.find(number).weightKg()).isNull();
    }

    @Test
    @DisplayName("同じイベントを 2 度読んでも区間は積み上がらない")
    void isIdempotent() {
        // **追記専用の行はリプレイで増える**（IT6 で実際に踏んだ）。区間は
        // 先に消してから入れ直す。
        String number = "TRK-BCI-" + System.nanoTime() % 1000000000L;
        var event = initialized(number, new BigDecimal("1200"));

        projection.on(event, "evt-idem");
        projection.on(event, "evt-idem");

        assertThat(cargos.findLegs(number)).hasSize(2);
    }
}
