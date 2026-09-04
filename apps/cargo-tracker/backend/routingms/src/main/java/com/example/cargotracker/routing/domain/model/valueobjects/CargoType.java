package com.example.cargotracker.routing.domain.model.valueobjects;

/**
 * 航海が受け入れる貨物種別。
 *
 * <p><b>Booking の {@code CargoType} とは別の型</b>にする。同じ名前でも、Booking では
 * 「その貨物が何か」、Routing では「その航海が何を受け入れるか」で、値が増える理由も
 * 別になる。共有カーネルに列挙型は置かない（domain-model.md）。</p>
 */
public enum CargoType {
    /** 一般貨物。 */
    GENERAL,
    /** 危険物。 */
    HAZARDOUS,
    /** 冷凍・冷蔵貨物。 */
    REEFER
}
