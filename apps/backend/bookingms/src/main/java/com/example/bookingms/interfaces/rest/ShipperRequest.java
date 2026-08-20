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
        /** 法人のときだけ意味を持つ。個人で送られたら拒否する（集約が判断する）。 */
        String contractNumber,
        /** 百分率（12.5 は 12.5%）。未設定は 0% ではなく「未設定」。 */
        java.math.BigDecimal discountRatePercent,
        /** 同じメールアドレスの荷主があっても新規で登録するか（営業担当者の選択）。 */
        boolean registerAnyway) {
}
