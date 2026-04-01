package com.example.cargotracker.shipper.interfaces.rest.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ShipperRequest(
        @NotBlank(message = "氏名/社名は必須です")
        @Size(max = 200, message = "氏名/社名は 200 文字以内にしてください")
        String name,
        @NotBlank(message = "メールアドレスは必須です")
        @Email(message = "メールアドレスの形式が不正です")
        @Size(max = 254)
        String email,
        @Size(max = 20)
        String phone,
        @NotNull(message = "荷主種別は必須です")
        String category,
        @Size(max = 50)
        String contractNumber,
        @DecimalMin(value = "0", message = "割引率は 0 以上にしてください")
        @DecimalMax(value = "30", message = "割引率は 30 以下にしてください")
        BigDecimal discountRate
) {
}
