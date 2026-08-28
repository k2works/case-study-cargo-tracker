package com.example.bookingms.domain.model.aggregates;

import com.example.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.bookingms.domain.model.valueobjects.CargoType;

/**
 * 貨物仕様の不変条件（US04）。
 *
 * <p><strong>集約から分けたのは、対象が違うからである。</strong>{@link Cargo} が守るのは
 * 「予約がどこまで進んだか」であり、ここが守るのは「貨物の申告が揃っているか」である。
 * 仕様のことは仕様に聞く。
 *
 * <p>行数の上限に当たったから割ったのではない。<strong>上限は合図であって、割り方の
 * 基準ではない。</strong>基準は責務である。
 */
final class CargoSpecificationRules {

    private CargoSpecificationRules() {
    }

    /**
     * 貨物仕様の不変条件。
     *
     * <p>付け忘れ（危険物なのに申告が無い）と同じく、付けすぎ（一般貨物に温度条件がある）も
     * 誤りとして扱う。付けすぎを通すと、経路設計（IT3）が「温度条件のある一般貨物」を
     * どう扱うか判断できない。
     */
    static void requireValid(CargoSpecification specification) {
        if (specification == null || specification.type() == null) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        if (specification.weightKg() == null || specification.weightKg().signum() <= 0) {
            throw new IllegalArgumentException(
                    "重量は 0 より大きい値で指定してください: " + specification.weightKg());
        }
        if (specification.quantity() != null && specification.quantity() <= 0) {
            throw new IllegalArgumentException("個数は 1 以上で指定してください: " + specification.quantity());
        }

        boolean hazardous = specification.type() == CargoType.HAZARDOUS;
        boolean refrigerated = specification.type() == CargoType.REFRIGERATED;

        if (hazardous && specification.hazardousDeclaration() == null) {
            throw new IllegalArgumentException("危険物には危険物申告が必要です");
        }
        if (!hazardous && specification.hazardousDeclaration() != null) {
            throw new IllegalArgumentException("危険物申告は危険物にだけ設定できます");
        }
        if (refrigerated && specification.temperatureRequirement() == null) {
            throw new IllegalArgumentException("冷凍・冷蔵貨物には保管温度の条件が必要です");
        }
        if (!refrigerated && specification.temperatureRequirement() != null) {
            throw new IllegalArgumentException("保管温度の条件は冷凍・冷蔵貨物にだけ設定できます");
        }
    }

}
