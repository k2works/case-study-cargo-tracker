package com.example.billingms.application.internal.queryservices;

import com.example.billingms.domain.model.valueobjects.CargoType;
import com.example.billingms.domain.model.valueobjects.ChargeableLeg;
import com.example.billingms.domain.model.valueobjects.Money;
import com.example.billingms.domain.model.valueobjects.PortRegion;
import com.example.billingms.domain.model.valueobjects.TransportCharge;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 料金の試算（US01-3・[ADR-028] 決定 6）。
 *
 * <p><strong>式は 1 か所にある。</strong>見積（bookingms）が自分で計算すると、
 * 荷主に出した見積と運び終えたあとの請求が違う金額になる——営業担当者は毎回
 * 「見積はあくまで概算です」と言うことになり、見積の意味が消える。
 *
 * <p><strong>保存しない。</strong>試算は請求ではない（[ADR-027] 決定 3 と同じ立場）。
 *
 * <p><strong>割引は入れない。</strong>見積の時点では荷主が決まっていないことがあり、
 * 決まっていても契約割引は請求の話である。<strong>基本料金だけを返す</strong>
 * ——「概算」であることを金額そのもので示す。
 */
@Service
public class QuoteChargeUseCase {

    /**
     * 区間ごとの地域区分から基本料金を出す。
     *
     * @param legRegions 区間ごとの両端の地域区分
     * @param weightKg 重量
     * @param cargoType 貨物種別
     */
    public Money quote(List<QuoteLeg> legRegions, BigDecimal weightKg, String cargoType) {
        if (legRegions == null || legRegions.isEmpty()) {
            throw new IllegalArgumentException("区間が 1 本も無い経路の料金は試算できません");
        }
        List<ChargeableLeg> legs = legRegions.stream()
                .map(leg -> new ChargeableLeg(PortRegion.of(leg.loadRegion()),
                        PortRegion.of(leg.unloadRegion())))
                .toList();
        return TransportCharge.of(legs, weightKg, CargoType.of(cargoType)).baseAmount();
    }

    /**
     * 試算の入力になる 1 区間。
     *
     * @param loadRegion 積み地の地域区分
     * @param unloadRegion 揚げ地の地域区分
     */
    public record QuoteLeg(String loadRegion, String unloadRegion) {
    }
}
