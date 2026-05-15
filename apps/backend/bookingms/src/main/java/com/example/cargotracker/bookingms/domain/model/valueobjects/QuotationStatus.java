package com.example.cargotracker.bookingms.domain.model.valueobjects;

/**
 * 見積の状態（data-model.md L362 {@code quotation.status}）。
 *
 * <ul>
 *   <li>{@code DRAFT}: 作成直後（候補算出中）</li>
 *   <li>{@code OFFERED}: 候補確定し荷主への提示が可能</li>
 *   <li>{@code ACCEPTED}: 荷主が受諾し予約化（{@code Cargo} 集約に引き継ぐ）</li>
 *   <li>{@code EXPIRED}: 有効期限切れ</li>
 * </ul>
 */
public enum QuotationStatus {
    DRAFT,
    OFFERED,
    ACCEPTED,
    EXPIRED
}
