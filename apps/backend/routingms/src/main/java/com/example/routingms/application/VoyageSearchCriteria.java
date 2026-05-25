package com.example.routingms.application;

import java.time.LocalDateTime;

/**
 * 航海スケジュール検索条件（US07）。
 *
 * <p>各項目は {@code null} 許容で、指定された条件のみで絞り込む。
 * {@code cargoType} を指定した場合、その貨物種別を受け入れ可能な航海のみに絞り込む
 * （対応貨物種別が未登録の航海は一般貨物のみ受け入れる、というドメイン規則に従う）。
 */
public record VoyageSearchCriteria(
        String origin,
        String destination,
        LocalDateTime departureFrom,
        LocalDateTime departureTo,
        String cargoType
) {
}
