package com.example.cargotracker.booking.domain.service;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.EstimatedAmount;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.booking.domain.model.valueobjects.PortRegion;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotationRates;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotedRoute;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteCandidate;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 候補経路から概算料金を出す（US01 §受入基準 3・正典の料金計算）。
 *
 * <pre>
 * 概算料金 = 基準運賃 × 区間係数 × 重量係数 × 貨物種別係数
 *   区間係数     = 区間ごとの地域係数の合計（両端の区分が違えば重いほう）
 *   重量係数     = 重量(kg) ÷ 1,000（下限 0.1）
 *   貨物種別係数 = 料率表から引く（知らない種別は断る）
 * </pre>
 *
 * <p><b>billingms の {@code FreightChargeCalculator} と同じ式だが、共有しない。</b>
 * 式を共有カーネルに置くと、見積と請求が同じ理由で変わることになる——実際には
 * 変わる理由が違う（見積は候補の出し方、請求は実績の数え方）。<b>共有するのは
 * 料率の出典だけ</b>で、同一性は {@code RateTableParityTest} が固定する
 * （[ADR-0016] 決定 1）。</p>
 *
 * <p><b>消費税は載せない。</b> 見積は概算であり、輸出免税の判定は実際の輸送で
 * 決まる。税込みで示すと、請求との差が税の分だけ増えて「説明できない差」になる。</p>
 *
 * <p><b>途中で丸めない。</b> 丸めは {@link EstimatedAmount} の中 1 か所だけ
 * （正典）。重量係数を丸めると 1,234 kg が 1,000 kg と同じ料金になる。</p>
 */
public class QuotationEstimator {

    /** 重量係数の分母。基準運賃が「1,000kg あたり」であることに対応する。 */
    private static final BigDecimal WEIGHT_UNIT = new BigDecimal("1000");

    /** 重量係数の下限。軽い貨物でも運ぶ手間は掛かる（正典）。 */
    private static final BigDecimal MIN_WEIGHT_FACTOR = new BigDecimal("0.1");

    /**
     * 候補ごとに概算を付ける。
     *
     * <p><b>候補 0 件でも断らない。</b> 期限に間に合う経路が無いことも荷主に
     * 伝えるべき答えで、見積そのものは作れる（正典の不変条件）。</p>
     */
    public List<QuotedRoute> estimate(List<RouteCandidate> candidates, CargoType cargoType,
            Weight weight, QuotationRates rates) {
        List<QuotedRoute> quoted = new ArrayList<>();
        for (RouteCandidate candidate : candidates) {
            quoted.add(new QuotedRoute(candidate.legs(), candidate.transitDays(),
                    estimateOne(candidate.legs(), cargoType, weight, rates),
                    candidate.overdueDays()));
        }
        return List.copyOf(quoted);
    }

    /** 1 経路ぶんの概算。 */
    public EstimatedAmount estimateOne(List<Leg> legs, CargoType cargoType, Weight weight,
            QuotationRates rates) {
        BigDecimal regionFactor = BigDecimal.ZERO;
        for (Leg leg : legs) {
            PortRegion region = rates.regionOfLeg(
                    leg.load().unLocode(), leg.unload().unLocode());
            // **区間は足す**（掛けない）。正典は「区間ごとの地域係数の合計」。
            regionFactor = regionFactor.add(rates.regionFactor(region));
        }

        BigDecimal weightFactor = weight.kilograms()
                .divide(WEIGHT_UNIT, 4, RoundingMode.HALF_UP);
        if (weightFactor.compareTo(MIN_WEIGHT_FACTOR) < 0) {
            weightFactor = MIN_WEIGHT_FACTOR;
        }

        return EstimatedAmount.yen(rates.baseFare())
                .multiply(regionFactor)
                .multiply(weightFactor)
                .multiply(rates.cargoTypeFactor(cargoType));
    }
}
