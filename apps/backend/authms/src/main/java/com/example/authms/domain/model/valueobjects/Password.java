package com.example.authms.domain.model.valueobjects;

import java.util.Objects;

public class Password {
    private final String encodedValue;

    private Password(String encodedValue) {
        this.encodedValue = encodedValue;
    }

    public static Password fromEncoded(String encodedValue) {
        if (encodedValue == null || encodedValue.isBlank()) {
            throw new IllegalArgumentException("パスワードは必須です");
        }
        return new Password(encodedValue);
    }

    public String getEncodedValue() {
        return encodedValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Password password = (Password) o;
        return Objects.equals(encodedValue, password.encodedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(encodedValue);
    }
}
