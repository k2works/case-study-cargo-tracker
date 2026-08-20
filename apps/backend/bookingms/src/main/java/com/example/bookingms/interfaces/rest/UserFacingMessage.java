package com.example.bookingms.interfaces.rest;

/**
 * 利用者に見せる文だけを取り出す。
 *
 * <p>ドメインの例外メッセージは「文 + コロン + 診断情報」の形で書く（例:
 * {@code "到着期限に過去の日付は指定できません: 2026-01-01"}）。診断情報は原因を追う側に
 * 必要だが、画面にそのまま出すと 2 つの問題が起きる。
 *
 * <ul>
 *   <li>マニュアルの「よくある入力の誤り」の表と字面が合わない。利用者は表で探せなくなる
 *   <li>入力した値がそのまま画面に返る。値の中身によっては、利用者が入れたつもりのない
 *       情報（他人の識別子など）を画面に写すことになる
 * </ul>
 *
 * <p>そこで<strong>境界のここ 1 箇所で切る</strong>。ドメイン側で 2 種類のメッセージを
 * 持ち回ると、書く場所が増えるぶん揃わなくなる。
 */
final class UserFacingMessage {

    /** 文と診断情報の区切り。半角コロン + 空白に限る（日本語の文中に現れない形を選ぶ）。 */
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
