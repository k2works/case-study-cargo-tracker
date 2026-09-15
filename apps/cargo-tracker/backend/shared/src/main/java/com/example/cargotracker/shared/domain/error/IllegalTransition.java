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

    /**
     * 原因を伴う遷移違反。
     *
     * <p><b>握りつぶさずに包む。</b> 同じ出来事が下の層では別の形（制約違反など）
     * で届くことがある。利用者への断りは業務の言葉にしつつ、<b>原因は残す</b>
     * ——切り分けるのは記録を読む人である。</p>
     */
    public IllegalTransition(String message, Throwable cause) {
        super(MARKER + message, cause);
    }
}
