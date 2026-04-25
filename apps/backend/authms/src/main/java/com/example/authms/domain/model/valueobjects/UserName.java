package com.example.authms.domain.model.valueobjects;

import java.util.Objects;

public class UserName {
    private final String value;

    public UserName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ユーザー名は必須です");
        }
        if (value.length() > 50) {
            throw new IllegalArgumentException("ユーザー名は50文字以内にしてください");
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserName userName = (UserName) o;
        return Objects.equals(value, userName.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
