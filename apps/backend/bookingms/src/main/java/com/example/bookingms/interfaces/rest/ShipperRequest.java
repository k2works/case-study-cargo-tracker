package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.ShipperType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ShipperRequest(
        @NotNull ShipperType type,
        @NotBlank String name,
        @NotBlank String email,
        @NotBlank String address,
        String phone,
        /** 同じメールアドレスの荷主があっても新規で登録するか（営業担当者の選択）。 */
        boolean registerAnyway) {
}
