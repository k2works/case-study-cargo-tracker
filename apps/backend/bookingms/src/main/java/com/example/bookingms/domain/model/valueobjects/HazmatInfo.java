package com.example.bookingms.domain.model.valueobjects;

/**
 * 危険物申告情報
 */
public record HazmatInfo(
        String unCode,
        String hazardClass,
        String packingGroup
) {
    public HazmatInfo {
        if (unCode == null || unCode.isBlank()) {
            throw new IllegalArgumentException("UN コードは必須です");
        }
        if (hazardClass == null || hazardClass.isBlank()) {
            throw new IllegalArgumentException("危険物クラスは必須です");
        }
    }
}
