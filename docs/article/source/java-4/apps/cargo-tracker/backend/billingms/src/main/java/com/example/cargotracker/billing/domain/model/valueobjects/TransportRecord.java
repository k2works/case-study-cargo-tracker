package com.example.cargotracker.billing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.UnLocode;
import java.math.BigDecimal;
import java.util.List;

/**
 * 輸送実績（US21 §受入基準 2）。料金の式の入力。
 *
 * <p><b>距離は持たない。</b> 正典の式は区間の<b>地域係数</b>で数える。US21 §2 は
 * 「距離」を挙げるが、数えていないものを画面に出すと根拠として読めない
 * （計画の注 N3）。</p>
 *
 * <p><b>重量が無ければ作れない。</b> 重量を契約に載せたのは IT13 で、それ以前の
 * 貨物には入っていない。0 で埋めると重量係数が下限に落ち、<b>足りない重量で安い
 * 請求</b>が黙って出る。作れないことをここで断り、呼ぶ側が要確認へ出す。</p>
 *
 * @param legs 実際に通った区間（積む順）
 * @param weightKg 重量（kg）
 * @param cargoType 貨物種別の名前
 * @param origin 出発地（輸出免税の判定に使う）
 * @param destination 目的地
 */
public record TransportRecord(
        List<BilledLeg> legs,
        BigDecimal weightKg,
        String cargoType,
        UnLocode origin,
        UnLocode destination) {

    public TransportRecord {
        if (legs == null || legs.isEmpty()) {
            throw new BusinessRuleViolation("区間が分からないので料金を算出できません");
        }
        if (weightKg == null) {
            throw new BusinessRuleViolation(
                    "重量が分からないので料金を算出できません（足りない重量で安い請求を出さない）");
        }
        if (weightKg.signum() <= 0) {
            throw new BusinessRuleViolation("重量が 0 以下です: " + weightKg);
        }
        if (cargoType == null || cargoType.isBlank()) {
            throw new BusinessRuleViolation("貨物種別が分からないので料金を算出できません");
        }
        if (origin == null || destination == null) {
            throw new BusinessRuleViolation("出発地と目的地が分からないので料金を算出できません");
        }
        legs = List.copyOf(legs);
    }

    /** 輸出（出発地と目的地の国が違う）か。<b>輸出は免税</b>（正典）。 */
    public boolean isExport() {
        return !origin.countryCode().equals(destination.countryCode());
    }

    /** 請求の対象になる 1 区間。 */
    public record BilledLeg(UnLocode from, UnLocode to) {

        public BilledLeg {
            if (from == null || to == null) {
                throw new BusinessRuleViolation("区間の両端が分かりません");
            }
        }
    }
}
