package com.example.shared.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ロールを問わない操作であることを宣言する（[ADR-008]）。
 *
 * <p>利用者ヘッダを受け取るメソッドは、原則としてロールを検査する
 * （{@code authorizationCalledRule} が検査する）。<strong>認証済みでありさえすればよい</strong>
 * 操作はそこから外れるが、外れることを黙って許すと「書き忘れ」と見分けられない。
 *
 * <p>そこで<strong>例外の側に宣言させる</strong>。付けるときは、なぜロールを問わないのかを
 * その場に書く。付いていないメソッドは、認可を呼んでいなければ違反である。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AnyAuthenticatedUser {
}
