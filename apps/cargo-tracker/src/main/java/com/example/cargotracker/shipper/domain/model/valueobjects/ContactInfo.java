package com.example.cargotracker.shipper.domain.model.valueobjects;

import java.util.Objects;

public final class ContactInfo {

    private final String email;
    private final String phone;

    public ContactInfo(String email, String phone) {
        if (email == null) {
            throw new IllegalArgumentException("メールアドレスは null にできません");
        }
        if (email.isBlank()) {
            throw new IllegalArgumentException("メールアドレスは空文字にできません");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("メールアドレスの形式が不正です: " + email);
        }
        this.email = email;
        this.phone = phone;
    }

    public String email() {
        return email;
    }

    public String phone() {
        return phone;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContactInfo that)) return false;
        return Objects.equals(email, that.email) && Objects.equals(phone, that.phone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, phone);
    }
}
