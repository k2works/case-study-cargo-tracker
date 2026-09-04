package com.example.cargotracker.shared.domain.location;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.util.regex.Pattern;

/** ISO 3166-1 の 2 文字国コード（domain-model.md「Shared Kernel」）。 */
public record CountryCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{2}$");

    public CountryCode {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new BusinessRuleViolation("国コードは英大文字 2 文字です: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
