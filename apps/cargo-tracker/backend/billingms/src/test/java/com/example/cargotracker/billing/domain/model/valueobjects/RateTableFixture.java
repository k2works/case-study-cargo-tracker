package com.example.cargotracker.billing.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 正典の料率をそのまま持つ料率表（検査用）。
 *
 * <p><b>値は {@code application.yml} の既定と同じもの</b>を書く。ずれたら
 * {@code RateTableConfigurationTest} が赤になる——設定と検査が別の料率を持つと、
 * 検査だけが正しくて本番が違う、が起きる。</p>
 */
public final class RateTableFixture {

    private RateTableFixture() {
    }

    public static RateTable canonical() {
        return new RateTable(
                Money.yen(new BigDecimal("50000")),
                Map.of(PortRegion.DOMESTIC, new BigDecimal("1.0"),
                        PortRegion.NEAR_SEA, new BigDecimal("2.5"),
                        PortRegion.OCEAN, new BigDecimal("6.0")),
                Map.of("GENERAL", new BigDecimal("1.0"),
                        "HAZARDOUS", new BigDecimal("1.8"),
                        "REFRIGERATED", new BigDecimal("1.5")),
                Map.of("JP", PortRegion.DOMESTIC,
                        "CN", PortRegion.NEAR_SEA, "KR", PortRegion.NEAR_SEA,
                        "TW", PortRegion.NEAR_SEA, "SG", PortRegion.NEAR_SEA,
                        "HK", PortRegion.NEAR_SEA, "TH", PortRegion.NEAR_SEA,
                        "VN", PortRegion.NEAR_SEA, "MY", PortRegion.NEAR_SEA,
                        "PH", PortRegion.NEAR_SEA),
                new BigDecimal("0.10"));
    }
}
