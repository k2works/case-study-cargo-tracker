package com.example.cargotracker.shared.infrastructure.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 開発環境でログイン画面に認証情報を事前入力する設定。
 *
 * <p>目的は開発中の確認サイクルを速くすることであり、画面を触るたびに ID とパスワードを
 * 打ち直す手間をなくす。
 *
 * <p><strong>既定は無効である。</strong> 有効化を明示した環境（local / dev）でのみ効く。
 * 「本番でうっかり有効になる」経路を作らないため、安全側を既定にして opt-in にしている。
 * 有効時は画面に開発環境である旨を必ず表示する。事前入力されていることを利用者に
 * 隠すと、本番同様の画面だと思い込まれる。
 *
 * @param enabled  事前入力を有効にするか
 * @param username 事前入力する利用者 ID
 * @param password 事前入力するパスワード
 */
@ConfigurationProperties(prefix = "app.demo-login")
public record DemoLoginProperties(boolean enabled, String username, String password) {

    public DemoLoginProperties {
        username = username == null ? "" : username;
        password = password == null ? "" : password;
    }
}
