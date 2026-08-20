package com.example.bookingms.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 危険物申告（US05）。
 *
 * <p>3 項目がそろって初めて法的要件を満たす。どれか 1 つでも欠けた申告は申告として使えないため、
 * 部分的に持てる形にしない。
 */
public final class HazardousDeclaration {

    /** UN 番号は `UN` + 4 桁。 */
    private static final Pattern UN_NUMBER_PATTERN = Pattern.compile("^UN\\d{4}$");

    private final HazardClass hazardousClass;
    private final String unNumber;
    private final String properShippingName;

    private HazardousDeclaration(HazardClass hazardousClass, String unNumber, String properShippingName) {
        this.hazardousClass = hazardousClass;
        this.unNumber = unNumber;
        this.properShippingName = properShippingName;
    }

    public static HazardousDeclaration of(String hazardousClass, String unNumber,
            String properShippingName) {
        if (hazardousClass == null || hazardousClass.isBlank()) {
            throw new IllegalArgumentException("危険物クラスは必須です");
        }
        HazardClass resolved = HazardClass.of(hazardousClass);
        if (unNumber == null || !UN_NUMBER_PATTERN.matcher(unNumber.trim()).matches()) {
            throw new IllegalArgumentException("UN 番号の形式が不正です（UN + 4 桁）: " + unNumber);
        }
        if (properShippingName == null || properShippingName.isBlank()) {
            throw new IllegalArgumentException("正式品名は必須です");
        }
        return new HazardousDeclaration(resolved, unNumber.trim(), properShippingName.trim());
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static HazardousDeclaration restore(String hazardousClass, String unNumber,
            String properShippingName) {
        return new HazardousDeclaration(
                HazardClass.restore(hazardousClass), unNumber, properShippingName);
    }

    public HazardClass hazardousClass() {
        return hazardousClass;
    }

    public String unNumber() {
        return unNumber;
    }

    public String properShippingName() {
        return properShippingName;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof HazardousDeclaration declaration
                && hazardousClass.equals(declaration.hazardousClass)
                && unNumber.equals(declaration.unNumber)
                && properShippingName.equals(declaration.properShippingName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hazardousClass, unNumber, properShippingName);
    }

    @Override
    public String toString() {
        return "%s / %s / %s".formatted(hazardousClass.code(), unNumber, properShippingName);
    }
}
