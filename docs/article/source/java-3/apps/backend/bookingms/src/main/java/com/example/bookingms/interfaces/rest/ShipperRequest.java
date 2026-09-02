package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.valueobjects.ShipperType;
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
        boolean registerAnyway,
        /**
         * シミュレーションが作る荷主か（[ADR-030] 決定 3）。
         *
         * <p>指定すると荷主コードが {@code SIM-} の帯になり、精算の締め・荷主一覧から外れる。
         *
         * <p><strong>省略できる形にする（{@code Boolean}）。</strong>実業務の画面はこの項目を
         * 送らない。基本型にすると、送らなかった要求が
         * 「{@code null} を {@code boolean} にできない」で 400 になる——
         * <strong>足した項目が、それを知らない既存の呼び出しを壊す</strong>。
         */
        Boolean simulated) {

    /** 送られてこなければ実業務の登録として扱う。 */
    public boolean isSimulated() {
        return Boolean.TRUE.equals(simulated);
    }
}
