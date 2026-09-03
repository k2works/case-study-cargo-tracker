package com.example.cargotracker.booking.domain.model.valueobjects;

/** 危険物申告（IMO クラスと UN 番号）。危険物の予約には必ず添える。 */
public record HazardousDeclaration(String imoClass, String unNumber) {

    public HazardousDeclaration {
        if (imoClass == null || imoClass.isBlank()) {
            throw new IllegalArgumentException("IMO クラスは必須です");
        }
        if (unNumber == null || unNumber.isBlank()) {
            throw new IllegalArgumentException("UN 番号は必須です");
        }
    }
}
