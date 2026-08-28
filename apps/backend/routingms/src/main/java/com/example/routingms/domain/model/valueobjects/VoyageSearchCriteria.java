package com.example.routingms.domain.model.valueobjects;

import java.time.Instant;

/**
 * 航海の検索条件（US07）。すべて任意で、指定した条件だけで絞る。
 *
 * @param originUnLocode 出発地（UN/LOCODE）
 * @param destinationUnLocode 目的地（UN/LOCODE）
 * @param departureFrom 出発期間の開始
 * @param departureTo 出発期間の終了
 * @param cargoType 積みたい貨物の種別。対応できる航海だけを残す
 */
public record VoyageSearchCriteria(
        String originUnLocode,
        String destinationUnLocode,
        Instant departureFrom,
        Instant departureTo,
        CargoType cargoType) {

    public static VoyageSearchCriteria all() {
        return new VoyageSearchCriteria(null, null, null, null, null);
    }
}
