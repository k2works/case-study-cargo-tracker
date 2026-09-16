package com.example.cargotracker.billing.domain.service;

import com.example.cargotracker.billing.domain.model.valueobjects.FreightCharge;
import com.example.cargotracker.billing.domain.model.valueobjects.Money;
import com.example.cargotracker.billing.domain.model.valueobjects.PortRegion;
import com.example.cargotracker.billing.domain.model.valueobjects.RateTable;
import com.example.cargotracker.billing.domain.model.valueobjects.TransportRecord;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基本料金を数える（US21 §受入基準 3・正典の式）。
 *
 * <pre>
 * 基本料金 = 基準運賃 × 区間係数 × 重量係数 × 貨物種別係数
 *   区間係数     = 区間ごとの地域係数の合計（両端の区分が違えば重いほう）
 *   重量係数     = 重量(kg) ÷ 1,000（下限 0.1）
 *   貨物種別係数 = 料率表から引く（知らない種別は断る）
 * </pre>
 *
 * <p><b>料率は持たない。</b> 引数で受け取る（{@code RateTable}）ので、料率を
 * 変えてもこのクラスは変わらない。料率の出典は {@code application.yml} 1 か所
 * （[ADR-0016]）。</p>
 *
 * <p><b>途中で丸めない。</b> 丸めは {@code Money} の中 1 か所だけ（正典）。
 * 重量係数を丸めると 1,234 kg が 1,000 kg と同じ料金になる。</p>
 *
 * <p><b>根拠を一緒に返す。</b> 経理が確かめるのは「なぜこの額か」であり、
 * 金額だけでは答えられない。</p>
 */
public class FreightChargeCalculator {

    /** 重量係数の分母。基準運賃が「1,000kg あたり」であることに対応する。 */
    private static final BigDecimal WEIGHT_UNIT = new BigDecimal("1000");

    /** 重量係数の下限。軽い貨物でも運ぶ手間は掛かる（正典）。 */
    private static final BigDecimal MIN_WEIGHT_FACTOR = new BigDecimal("0.1");

    private static final DecimalFormat WEIGHT_FORMAT = new DecimalFormat("#,##0");

    public FreightCharge calculate(TransportRecord transport, RateTable rates) {
        List<PortRegion> regions = new ArrayList<>();
        BigDecimal regionFactor = BigDecimal.ZERO;
        for (TransportRecord.BilledLeg leg : transport.legs()) {
            PortRegion region = rates.regionOfLeg(leg.from(), leg.to());
            regions.add(region);
            // **区間は足す**（掛けない）。正典は「区間ごとの地域係数の合計」。
            regionFactor = regionFactor.add(rates.regionFactor(region));
        }

        BigDecimal weightFactor = transport.weightKg()
                .divide(WEIGHT_UNIT, 4, java.math.RoundingMode.HALF_UP);
        if (weightFactor.compareTo(MIN_WEIGHT_FACTOR) < 0) {
            weightFactor = MIN_WEIGHT_FACTOR;
        }

        BigDecimal cargoTypeFactor = rates.cargoTypeFactor(transport.cargoType());

        Money baseCharge = rates.baseFare()
                .multiply(regionFactor)
                .multiply(weightFactor)
                .multiply(cargoTypeFactor);

        return new FreightCharge(baseCharge, regions,
                describe(transport, regions, rates, cargoTypeFactor));
    }

    /**
     * 明細に出す根拠。
     *
     * <p><b>距離は書かない。</b> 数えていないものを根拠に書くと、読む人が
     * 「距離で計算されている」と誤解する（計画の注 N3）。</p>
     */
    private String describe(TransportRecord transport, List<PortRegion> regions,
            RateTable rates, BigDecimal cargoTypeFactor) {
        String legs = regions.stream()
                .map(region -> region.label() + " " + rates.regionFactor(region).toPlainString())
                .collect(Collectors.joining(" + "));
        return regions.size() + " 区間・" + legs + "・"
                + WEIGHT_FORMAT.format(transport.weightKg()) + " kg・"
                + cargoTypeLabel(transport.cargoType()) + " " + cargoTypeFactor.toPlainString();
    }

    /** 画面に出す貨物種別の呼び名。<b>列挙名を出さない。</b> */
    private String cargoTypeLabel(String cargoType) {
        return switch (cargoType) {
            case "GENERAL" -> "一般";
            case "HAZARDOUS" -> "危険物";
            case "REFRIGERATED" -> "冷凍";
            default -> cargoType;
        };
    }

    /**
     * 消費税。<b>輸出（出発地と目的地の国が違う）は免税</b>（正典）。
     *
     * <p><b>掛ける相手は割引後の額</b>である。割引前に掛けると取りすぎる。</p>
     *
     * <p><b>区間の地域区分では判定しない。</b> 近海の区間を通る国内輸送もあり、
     * 「遠くまで運んだから輸出」ではない。判定は出発地と目的地の国で行う。</p>
     */
    public Money taxOn(TransportRecord transport, Money taxableAmount, RateTable rates) {
        if (transport.isExport()) {
            return Money.zero();
        }
        return taxableAmount.multiply(rates.taxRate());
    }
}
