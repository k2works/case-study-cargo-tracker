package com.example.cargotracker.handling.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.domain.location.Location;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 予定ルート外の判定（不変条件 2・3 / US15 §受入基準 7）。
 *
 * <p><b>判定はここ 1 か所。</b> 集約も画面も書き直さない。</p>
 */
class CargoSnapshotTest {

    private static final Location TOKYO = Location.of("JPTYO");
    private static final Location SINGAPORE = Location.of("SGSIN");
    private static final Location NEW_YORK = Location.of("USNYC");
    private static final Location HAMBURG = Location.of("DEHAM");

    private static CargoSnapshot cargo() {
        return new CargoSnapshot("TRK-8K2QX7M4RB", "b-1", TOKYO, NEW_YORK, "GENERAL",
                List.of(new CargoSnapshot.LegSnapshot("V-MOL-001", TOKYO, SINGAPORE),
                        new CargoSnapshot.LegSnapshot("V-ONE-002", SINGAPORE, NEW_YORK)));
    }

    @Test
    @DisplayName("受領は出発港でなければ予定外")
    void receiveMatchesTheOrigin() {
        assertThat(cargo().isOffRoute(HandlingType.RECEIVE, TOKYO)).isFalse();
        assertThat(cargo().isOffRoute(HandlingType.RECEIVE, SINGAPORE)).isTrue();
    }

    @Test
    @DisplayName("積込は旅程のどこかの積込港なら予定どおり")
    void loadMatchesAnyLoadPort() {
        assertThat(cargo().isOffRoute(HandlingType.LOAD, TOKYO)).isFalse();
        assertThat(cargo().isOffRoute(HandlingType.LOAD, SINGAPORE)).isFalse();
        assertThat(cargo().isOffRoute(HandlingType.LOAD, HAMBURG)).isTrue();
    }

    @Test
    @DisplayName("荷降しは旅程のどこかの荷降港なら予定どおり")
    void unloadMatchesAnyUnloadPort() {
        assertThat(cargo().isOffRoute(HandlingType.UNLOAD, SINGAPORE)).isFalse();
        assertThat(cargo().isOffRoute(HandlingType.UNLOAD, NEW_YORK)).isFalse();
        // 東京は積む港であって降ろす港ではない。
        assertThat(cargo().isOffRoute(HandlingType.UNLOAD, TOKYO)).isTrue();
    }

    @Test
    @DisplayName("引取は目的港でなければ予定外")
    void claimMatchesTheDestination() {
        assertThat(cargo().isOffRoute(HandlingType.CLAIM, NEW_YORK)).isFalse();
        assertThat(cargo().isOffRoute(HandlingType.CLAIM, SINGAPORE)).isTrue();
    }

    @Test
    @DisplayName("不変条件 3: 旅程が無い貨物の積込・荷降しは予定外に倒す")
    void treatsMissingItineraryAsOffRoute() {
        // **分からないときは「予定どおり」と言わない。** 予定外の記録は残るが、
        // 予定どおりと記録すると誤配が見えなくなる。
        var withoutLegs = new CargoSnapshot("TRK-8K2QX7M4RB", "b-1", TOKYO, NEW_YORK,
                "GENERAL", List.of());

        assertThat(withoutLegs.isOffRoute(HandlingType.LOAD, TOKYO)).isTrue();
        assertThat(withoutLegs.isOffRoute(HandlingType.UNLOAD, NEW_YORK)).isTrue();
        // 端点は旅程が無くても分かる。
        assertThat(withoutLegs.isOffRoute(HandlingType.RECEIVE, TOKYO)).isFalse();
    }

    @Test
    @DisplayName("場所が無ければ予定外（判定の材料が無い）")
    void treatsMissingLocationAsOffRoute() {
        assertThat(cargo().isOffRoute(HandlingType.LOAD, null)).isTrue();
    }
}
