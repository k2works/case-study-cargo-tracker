package com.example.bookingms.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bookingms.domain.model.valueobjects.ShipperType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * シミュレーション由来の荷主（[ADR-030] 決定 3）。
 *
 * <p><strong>識別の根拠は荷主コードの帯 1 本にする。</strong>列を別に持つと、
 * 帯と列が食い違う行が生まれる——どちらが正しいかを決める規則がまた要る。
 * 貨物・請求書・追跡はすべて荷主から辿れるため、帯だけで判断できる。
 */
@DisplayName("シミュレーション由来の荷主")
class SimulatedShipperTest {

    @Test
    @DisplayName("実業務の登録は、シミュレーション由来ではない")
    void realRegistrationIsNotSimulated() {
        Shipper shipper = Shipper.register(ShipperType.INDIVIDUAL, "伊藤商事",
                "ito@example.com", "東京都", "03-0000-0000");

        assertThat(shipper.simulated()).isFalse();
    }

    @Test
    @DisplayName("シミュレーションの登録は、シミュレーション由来である")
    void simulatedRegistrationIsMarked() {
        Shipper shipper = Shipper.registerSimulated(ShipperType.INDIVIDUAL,
                "シミュレーション荷主", "sim@example.com", "東京都", "03-0000-0000");

        assertThat(shipper.simulated()).isTrue();
    }

    /**
     * <strong>復元では帯から読む。</strong>
     *
     * <p>登録時の指示を別の列に持つと、帯を直したときに列が古いまま残る。
     */
    @Test
    @DisplayName("読み出した荷主は、荷主コードの帯で判断する")
    void restoredShipperIsJudgedByItsCode() {
        Shipper simulated = Shipper.restore(1L, "SIM-000001", ShipperType.INDIVIDUAL,
                "シミュレーション荷主", "sim@example.com", "東京都", "03-0000-0000");
        Shipper real = Shipper.restore(2L, "SHP-000001", ShipperType.INDIVIDUAL,
                "伊藤商事", "ito@example.com", "東京都", "03-0000-0000");

        assertThat(simulated.simulated()).isTrue();
        assertThat(real.simulated()).isFalse();
    }
}
