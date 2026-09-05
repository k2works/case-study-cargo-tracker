package com.example.cargotracker.shared.domain.error;

/**
 * 状態が合わないので断った（HTTP 409）。
 *
 * <p>「もう一度やっても同じ」ではなく「今はできない」を表します。利用者が
 * やり直せばよいのか、入力を直すのかを判断できるように 422 と分けます。</p>
 *
 * <p>種類を文言の接頭辞として運ぶ理由は {@link BusinessRuleViolation} と同じです。</p>
 */
public class IllegalTransition extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /** 文言の先頭に付ける印。 */
    public static final String MARKER = "[ILLEGAL_STATE] ";

    public IllegalTransition(String message) {
        super(MARKER + message);
    }
}
