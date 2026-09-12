package com.example.cargotracker.contract.roundtrip;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.valueobjects.RateTable;
import com.example.cargotracker.billing.domain.model.valueobjects.TransportRecord;
import com.example.cargotracker.billing.domain.service.FreightChargeCalculator;
import com.example.cargotracker.billing.infrastructure.config.RateTableConfiguration;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotationRates;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import com.example.cargotracker.booking.domain.service.QuotationEstimator;
import com.example.cargotracker.booking.infrastructure.config.QuotationRatesConfiguration;
import com.example.cargotracker.shared.domain.location.Location;
import com.example.cargotracker.shared.domain.location.UnLocode;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.yaml.snakeyaml.Yaml;

/**
 * 見積と請求が<b>同じ料率</b>を読んでいるか（[ADR-0016] 決定 1・IT14 T3）。
 *
 * <p><b>式は共有しません。</b> 見積の入力は候補経路、請求の入力は実際に通った
 * 区間なので、<b>金額は一致しません</b>（区間数の増減・誤配・留置）。共有するのは
 * 設定ファイルだけで、<b>ずれたら赤になる検査だけが担保</b>になります。</p>
 *
 * <p><b>だから「同じ入力なら同じ額」で突き合わせます。</b> 同じ区間・同じ重量・
 * 同じ貨物種別を両方に与えれば、料率が同じである限り基本料金は一致します。
 * 一方だけ料率の読み方（丸め・係数の掛け順）を変えれば、ここが赤になります。</p>
 *
 * <p><b>両方の BC の型を同時に見られる唯一の場所</b>がここ（contract-tests）です。
 * どちらかのサービスの中に置くと、相手の型を見られないので「自分の側だけ」を
 * 確かめることになります。</p>
 *
 * <p><b>設定ファイルは実物を読みます。</b> フィクスチャに料率を書くと、
 * {@code cargo-rates.yml} を直しても検査は元の値のまま緑になります
 * （IT10 の「判定はテスト側に書き直さない」と同じ形）。</p>
 */
class RateTableParityTest {

    /** 料率の出典。<b>両方の BC がこの 1 つを読む</b>（ADR-0016 決定 1）。 */
    private static final String RATES = "cargo-rates.yml";

    private static final RateTable BILLING_RATES = billingRates();
    private static final QuotationRates QUOTATION_RATES = quotationRates();

    private final QuotationEstimator estimator = new QuotationEstimator();
    private final FreightChargeCalculator calculator = new FreightChargeCalculator();

    @Test
    @DisplayName("基準運賃・地域係数・貨物種別係数が、見積と請求で一致する")
    void bothReadTheSameRates() {
        assertThat(QUOTATION_RATES.baseFare())
                .as("基準運賃がずれると、概算と請求の差が全件に出る")
                .isEqualByComparingTo(BILLING_RATES.baseFare().amount());

        // **列挙から回す。** 名簿を手書きすると、足した区分が名乗り出ない。
        for (var region : com.example.cargotracker.booking.domain.model.valueobjects
                .PortRegion.values()) {
            var billingRegion = com.example.cargotracker.billing.domain.model.valueobjects
                    .PortRegion.valueOf(region.name());
            assertThat(QUOTATION_RATES.regionFactor(region))
                    .as("地域係数 %s", region)
                    .isEqualByComparingTo(BILLING_RATES.regionFactor(billingRegion));
        }

        for (CargoType cargoType : CargoType.values()) {
            assertThat(QUOTATION_RATES.cargoTypeFactor(cargoType))
                    .as("貨物種別係数 %s", cargoType)
                    .isEqualByComparingTo(BILLING_RATES.cargoTypeFactor(cargoType.name()));
        }
    }

    @Test
    @DisplayName("港の地域区分の引き方が、見積と請求で一致する（表に無い国は遠洋）")
    void bothClassifyPortsTheSameWay() {
        for (String port : new String[] {"JPTYO", "CNSHA", "USNYC", "BRSSZ"}) {
            assertThat(QUOTATION_RATES.regionOf(new UnLocode(port)).name())
                    .as("%s の地域区分（表に無い国は遠洋）", port)
                    .isEqualTo(BILLING_RATES.regionOf(new UnLocode(port)).name());
        }
    }

    @ParameterizedTest(name = "{0} → {1}・{2} kg・{3}")
    @CsvSource({
        "JPTYO, JPOSA, 1200, GENERAL",
        "JPTYO, USNYC, 1200, GENERAL",
        "JPTYO, SGSIN, 3400, HAZARDOUS",
        "JPTYO, USNYC, 250, REFRIGERATED",
        // 重量係数の下限（0.1）を踏む。丸めの違いはここに出やすい。
        "JPTYO, JPOSA, 50, GENERAL",
    })
    @DisplayName("同じ区間・重量・貨物種別なら、見積の概算と請求の基本料金は同じ額になる")
    void sameInputProducesTheSameAmount(String origin, String destination, String weightKg,
            String cargoTypeName) {
        CargoType cargoType = CargoType.valueOf(cargoTypeName);

        var quoted = estimator.estimateOne(
                List.of(legOf(origin, destination)), cargoType,
                Weight.ofKilograms(weightKg), QUOTATION_RATES);

        var billed = calculator.calculate(new TransportRecord(
                List.of(new TransportRecord.BilledLeg(
                        new UnLocode(origin), new UnLocode(destination))),
                new BigDecimal(weightKg), cargoTypeName,
                new UnLocode(origin), new UnLocode(destination)), BILLING_RATES);

        assertThat(quoted.amount())
                .as("料率か丸め方がずれている（式は共有しないので、ここだけが担保）")
                .isEqualByComparingTo(billed.baseCharge().amount());
    }

    private static com.example.cargotracker.booking.domain.model.valueobjects.Leg legOf(
            String from, String to) {
        return new com.example.cargotracker.booking.domain.model.valueobjects.Leg(
                "V-PARITY-001", Location.of(from), Location.of(to),
                Instant.parse("2026-10-01T09:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"));
    }

    /** {@code cargo.rates.*} を実物から読む。<b>フィクスチャに書き写さない。</b> */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> rates() {
        try (InputStream in = RateTableParityTest.class.getClassLoader()
                .getResourceAsStream(RATES)) {
            if (in == null) {
                throw new IllegalStateException("料率の設定が見つかりません: " + RATES);
            }
            Map<String, Object> root = new Yaml().load(in);
            return (Map<String, Object>) ((Map<String, Object>) root.get("cargo")).get("rates");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("料率の設定を読めません: " + RATES, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static RateTable billingRates() {
        Map<String, Object> rates = rates();
        return new RateTableConfiguration().rateTable(
                new RateTableConfiguration.RateProperties(
                        decimal(rates.get("base-fare")),
                        decimals((Map<String, Object>) rates.get("region-factors")),
                        decimals((Map<String, Object>) rates.get("cargo-type-factors")),
                        strings((Map<String, Object>) rates.get("country-regions")),
                        decimal(rates.get("tax-rate"))));
    }

    @SuppressWarnings("unchecked")
    private static QuotationRates quotationRates() {
        Map<String, Object> rates = rates();
        return new QuotationRatesConfiguration().quotationRates(
                new QuotationRatesConfiguration.RateProperties(
                        decimal(rates.get("base-fare")),
                        decimals((Map<String, Object>) rates.get("region-factors")),
                        decimals((Map<String, Object>) rates.get("cargo-type-factors")),
                        strings((Map<String, Object>) rates.get("country-regions"))));
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    private static Map<String, BigDecimal> decimals(Map<String, Object> source) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, decimal(value)));
        return result;
    }

    private static Map<String, String> strings(Map<String, Object> source) {
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, String.valueOf(value)));
        return result;
    }
}
