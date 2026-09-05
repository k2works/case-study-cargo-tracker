package com.example.cargotracker.shared.domain.location;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
/**
 * 場所（domain-model.md「Shared Kernel」）。
 *
 * <p>同一性は {@link UnLocode} だけで決まる。港名は表示のための情報で表記が揺れるので、
 * 名前まで見て比較すると「東京」と「東京港」が別の港になる。</p>
 */
public record Location(UnLocode unLocode, String name) {

    public Location {
        if (unLocode == null) {
            throw new BusinessRuleViolation("UN/LOCODE は必須です");
        }
    }

    /** UN/LOCODE だけを持つ場所。港名の対応表を引く前の形。 */
    public static Location of(String unLocode) {
        return new Location(new UnLocode(unLocode), null);
    }

    public CountryCode country() {
        return unLocode.countryCode();
    }

    /** 同じ国か。輸出免税（Billing）と国内輸送の判定が使う。 */
    public boolean sameCountryAs(Location other) {
        return country().equals(other.country());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Location location && unLocode.equals(location.unLocode);
    }

    @Override
    public int hashCode() {
        return unLocode.hashCode();
    }
}
