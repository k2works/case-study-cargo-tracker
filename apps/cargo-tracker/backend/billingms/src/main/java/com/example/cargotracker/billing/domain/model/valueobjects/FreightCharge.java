package com.example.cargotracker.billing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.util.List;

/**
 * 算出した基本料金と、その<b>根拠</b>（US21 §受入基準 2・3）。
 *
 * <p><b>金額だけでは根拠にならない。</b> 経理が確かめるのは「なぜこの額か」なので、
 * 区間ごとの地域区分・重量・貨物種別を一緒に運ぶ。S61 はこれを明細に並べる。</p>
 *
 * @param baseCharge 基本料金（丸め前。丸めは {@link Money} の中）
 * @param regions 区間ごとの地域区分（積む順）
 * @param description 画面と明細に出す根拠の文（「3 区間・近海 2.5 + 遠洋 6.0・…」）
 */
public record FreightCharge(Money baseCharge, List<PortRegion> regions, String description) {

    public FreightCharge {
        if (baseCharge == null) {
            throw new BusinessRuleViolation("基本料金がありません");
        }
        if (regions == null || regions.isEmpty()) {
            throw new BusinessRuleViolation("区間の地域区分がありません（根拠にならない）");
        }
        regions = List.copyOf(regions);
    }
}
