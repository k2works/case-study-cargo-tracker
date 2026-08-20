package com.example.routingms.interfaces.rest;

/**
 * 利用者に見せる文だけを取り出す。
 *
 * <p>ドメインの例外は「文 + コロン + 診断情報」で書く。診断情報は原因を追う側に必要だが、
 * 画面にそのまま出すとマニュアルの誤り一覧と字面が合わなくなり、入力した値も画面に返る。
 * 境界のここ 1 箇所で切る。
 */
final class UserFacingMessage {

    private static final String DIAGNOSTIC_SEPARATOR = ": ";

    private UserFacingMessage() {
    }

    static String of(Throwable e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return "入力内容を確認してください";
        }
        int separator = message.indexOf(DIAGNOSTIC_SEPARATOR);
        return separator < 0 ? message : message.substring(0, separator);
    }
}
