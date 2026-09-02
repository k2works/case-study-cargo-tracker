package com.example.cargotracker.booking.domain.model.valueobjects;

/** 法人契約。契約番号と割引率を持つ（domain-model.md）。 */
public record CorporateContract(String contractNumber, DiscountRate discountRate) {

    public CorporateContract {
        if (contractNumber == null || contractNumber.isBlank()) {
            throw new IllegalArgumentException("法人は契約番号が必須です");
        }
        if (discountRate == null) {
            throw new IllegalArgumentException("割引率は必須です");
        }
    }
}
