package com.example.authms.domain.model;

/**
 * 利用者を名乗るための情報。
 *
 * <p>4 つとも {@code String} であり、引数として並べると順番を取り違えても型が合ってしまう。
 * 表示名とメールアドレスが入れ替わったまま保存されても、コンパイルは通る。
 *
 * @param username ログイン名
 * @param email メールアドレス
 * @param displayName 画面に出す呼び名
 * @param passwordHash パスワードのハッシュ
 */
public record UserIdentity(
        String username, String email, String displayName, String passwordHash) {
}
