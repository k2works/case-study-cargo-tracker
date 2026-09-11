package com.example.cargotracker.billing.infrastructure.config;

import com.example.cargotracker.billing.domain.model.valueobjects.Money;
import com.example.cargotracker.billing.domain.model.valueobjects.PortRegion;
import com.example.cargotracker.billing.domain.model.valueobjects.RateTable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 料率を設定から読む（[ADR-0016]）。
 *
 * <p><b>ハードコードしない。</b> 見積（US01・IT14）も同じ値を読むので、出典が
 * 1 つでなければ片方だけ直る。料率は業務が決める数字であって、コードに埋める
 * ものではない。</p>
 *
 * <p><b>欠けていれば起動で分かる。</b> {@code RateTable} が組み立ての時点で断る
 * ので、請求のたびに落ちることはない。</p>
 */
@Configuration
@EnableConfigurationProperties(RateTableConfiguration.RateProperties.class)
public class RateTableConfiguration {

    @Bean
    public RateTable rateTable(RateProperties properties) {
        Map<PortRegion, BigDecimal> regions = new LinkedHashMap<>();
        properties.regionFactors().forEach((name, factor) ->
                regions.put(PortRegion.valueOf(name), factor));

        Map<String, PortRegion> countries = new LinkedHashMap<>();
        properties.countryRegions().forEach((country, region) ->
                countries.put(country, PortRegion.valueOf(region)));

        return new RateTable(Money.yen(properties.baseFare()), regions,
                properties.cargoTypeFactors(), countries, properties.taxRate());
    }

    /**
     * {@code billing.rates.*}。
     *
     * @param baseFare 基準運賃（円）
     * @param regionFactors 地域係数（{@code DOMESTIC} / {@code NEAR_SEA} / {@code OCEAN}）
     * @param cargoTypeFactors 貨物種別係数
     * @param countryRegions 国コード → 地域区分。表に無い国は遠洋
     * @param taxRate 消費税率
     */
    @ConfigurationProperties(prefix = "billing.rates")
    public record RateProperties(
            BigDecimal baseFare,
            Map<String, BigDecimal> regionFactors,
            Map<String, BigDecimal> cargoTypeFactors,
            Map<String, String> countryRegions,
            BigDecimal taxRate) {
    }
}
