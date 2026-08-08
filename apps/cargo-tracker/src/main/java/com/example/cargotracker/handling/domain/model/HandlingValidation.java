package com.example.cargotracker.handling.domain.model;

/**
 * 荷役の妥当性検証の結果。
 *
 * <p><strong>登録の可否ではない。</strong> 予定と違う場所での作業も、起きた事実として
 * 記録する。判定が伝えるのは「予定と違うか」「それが誤配にあたるか」である。
 *
 * @param outcome 判定
 * @param message 利用者に伝える内容。予定どおりなら {@code null}
 */
public record HandlingValidation(Outcome outcome, String message) {

    /** 判定の種別。 */
    public enum Outcome {
        /** 予定どおり。 */
        AS_PLANNED,
        /** 予定と違うが、輸送そのものは予定どおり進む（受領・引取）。 */
        WARNING,
        /** 誤配。旅程からの逸脱であり、貨物は予定と違う船・違う港へ向かう。 */
        MISROUTED
    }

    public static HandlingValidation asPlanned() {
        return new HandlingValidation(Outcome.AS_PLANNED, null);
    }

    public static HandlingValidation warning(String message) {
        return new HandlingValidation(Outcome.WARNING, message);
    }

    public static HandlingValidation misrouted(String message) {
        return new HandlingValidation(Outcome.MISROUTED, message);
    }

    /** 誤配として確定したか。**Booking の経路状態を MISROUTED にするのはこの場合だけ。** */
    public boolean isMisrouted() {
        return outcome == Outcome.MISROUTED;
    }

    /** 利用者に何かを伝える必要があるか。 */
    public boolean hasMessage() {
        return message != null;
    }
}
