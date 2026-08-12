package com.example.cargotracker.shipper.domain.model.valueobjects;

/** 荷主種別。値の正典は {@code docs/design/domain-model.md}。 */
public enum ShipperType {
    /** 個人。契約割引の概念を持たない。 */
    INDIVIDUAL,
    /** 法人。契約割引率を持つ（US03 / US22）。 */
    CORPORATE
}
