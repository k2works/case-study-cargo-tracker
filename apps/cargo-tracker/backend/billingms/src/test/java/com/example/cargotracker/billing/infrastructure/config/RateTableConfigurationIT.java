package com.example.cargotracker.billing.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.valueobjects.PortRegion;
import com.example.cargotracker.billing.domain.model.valueobjects.RateTable;
import com.example.cargotracker.billing.domain.model.valueobjects.RateTableFixture;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 料率は設定から読む（[ADR-0016] 決定 1）。
 *
 * <p><b>検査が別の料率を持つと、検査だけが正しくて本番が違う、が起きる</b>
 * （IT10 の「判定はテスト側に書き直さない」と同じ形）。ここでは<b>実際に起動した
 * アプリの {@code RateTable}</b> を読み、検査が使うフィクスチャと一致することを見る。
 * どちらかを直したらもう一方も直すことになる。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RateTableConfigurationIT extends AbstractAxonIntegrationTest {

    @Autowired
    private RateTable rates;

    @Test
    @DisplayName("application.yml の料率が、正典の料率と一致する")
    void readsTheCanonicalRatesFromConfiguration() {
        RateTable canonical = RateTableFixture.canonical();

        assertThat(rates.baseFare().amount())
                .as("基準運賃をコードに埋めると、見積と請求で別々に直すことになる")
                .isEqualByComparingTo(canonical.baseFare().amount());
        for (PortRegion region : PortRegion.values()) {
            assertThat(rates.regionFactor(region))
                    .as("%s の地域係数", region.label())
                    .isEqualByComparingTo(canonical.regionFactor(region));
        }
        for (String cargoType : new String[] {"GENERAL", "HAZARDOUS", "REFRIGERATED"}) {
            assertThat(rates.cargoTypeFactor(cargoType))
                    .as("%s の係数", cargoType)
                    .isEqualByComparingTo(canonical.cargoTypeFactor(cargoType));
        }
        assertThat(rates.taxRate()).isEqualByComparingTo(canonical.taxRate());
    }

    @Test
    @DisplayName("国 → 地域区分も設定から読む（表に無い国は遠洋）")
    void readsPortRegionsFromConfiguration() {
        assertThat(rates.regionOf(new com.example.cargotracker.shared.domain.location.UnLocode(
                "JPTYO"))).isEqualTo(PortRegion.DOMESTIC);
        assertThat(rates.regionOf(new com.example.cargotracker.shared.domain.location.UnLocode(
                "SGSIN"))).isEqualTo(PortRegion.NEAR_SEA);
        assertThat(rates.regionOf(new com.example.cargotracker.shared.domain.location.UnLocode(
                "USNYC"))).isEqualTo(PortRegion.OCEAN);
    }
}
