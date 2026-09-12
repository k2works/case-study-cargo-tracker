package com.example.cargotracker.booking.infrastructure.config;

import com.example.cargotracker.booking.domain.model.valueobjects.PortRegion;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotationRates;
import com.example.cargotracker.booking.domain.service.QuotationEstimator;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 見積の料率を設定から読む（[ADR-0016]）。
 *
 * <p><b>請求と同じファイルを読む</b>（{@code shared} の {@code cargo-rates.yml}）。
 * 出典が 1 つでなければ片方だけ直る——見積が旧料率、請求が新料率という状態は、
 * 荷主から見れば「言われた金額と違う」である。</p>
 *
 * <p><b>型は billingms と共有しない。</b> BC をまたいで型を持つと、片方の都合で
 * もう片方が変わる。共有するのは<b>設定ファイルそのもの</b>だけで、同じ料率を
 * 読んでいることは {@code RateTableParityTest} が固定する。</p>
 *
 * <p><b>欠けていれば起動で分かる。</b> {@code QuotationRates} が組み立ての時点で
 * 断るので、見積のたびに落ちることはない。</p>
 */
@Configuration
@EnableConfigurationProperties(QuotationRatesConfiguration.RateProperties.class)
public class QuotationRatesConfiguration {

    @Bean
    public QuotationRates quotationRates(RateProperties properties) {
        Map<PortRegion, BigDecimal> regions = new LinkedHashMap<>();
        properties.regionFactors().forEach((name, factor) ->
                regions.put(PortRegion.valueOf(name), factor));

        Map<String, PortRegion> countries = new LinkedHashMap<>();
        properties.countryRegions().forEach((country, region) ->
                countries.put(country, PortRegion.valueOf(region)));

        return new QuotationRates(properties.baseFare(), regions,
                properties.cargoTypeFactors(), countries);
    }

    @Bean
    public QuotationEstimator quotationEstimator() {
        return new QuotationEstimator();
    }

    /**
     * {@code cargo.rates.*}。
     *
     * <p><b>消費税率は読まない。</b> 見積は概算で税を載せない（輸出免税の判定は
     * 実際の輸送で決まる）。読まないものを型に持つと、使われない設定が増える。</p>
     */
    @ConfigurationProperties(prefix = "cargo.rates")
    public record RateProperties(
            BigDecimal baseFare,
            Map<String, BigDecimal> regionFactors,
            Map<String, BigDecimal> cargoTypeFactors,
            Map<String, String> countryRegions) {
    }
}
