package com.example.cargotracker.shared.domain.error;

/**
 * 業務規則で断った（HTTP 422）。
 *
 * <p><b>入口で判別できる形にする。</b> サービス越しに来た例外は
 * {@code AxonServerRemoteCommandHandlingException} に包まれ、根の例外の型が
 * 置き換わります（[ADR-0001] 決定 5 第 12 項）。型で見分けようとすると、
 * コマンドがサービスを越えた瞬間に 409 が黙って 422 に劣化します。</p>
 *
 * <p>そこで<b>種類を文言の接頭辞として運びます</b>。型が失われても、
 * 文言は {@code CommandExecutionException} のメッセージとして届きます。</p>
 */
public class BusinessRuleViolation extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /** 文言の先頭に付ける印。入口はこれを見て 422 と 409 を分ける。 */
    public static final String MARKER = "[BUSINESS_RULE] ";

    public BusinessRuleViolation(String message) {
        super(MARKER + message);
    }

    /** 印を外した文言。画面に出すのはこちら。 */
    public static String strip(String message) {
        if (message == null) {
            return null;
        }
        return message.replace(MARKER, "").replace(IllegalTransition.MARKER, "");
    }
}
