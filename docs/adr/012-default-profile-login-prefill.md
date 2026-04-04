# ADR-012: デフォルトプロファイルでログインフォームに認証情報をプリセットする

開発効率向上のため、デフォルトプロファイル（`application.yml`）でログインフォームに認証情報をプリセットし、ボタン一発でログインできるようにする。

日付: 2026-04-04

## ステータス

承認済み

## コンテキスト

- 本プロジェクトは 1 名 + AI ペアプログラミングの体制で開発しており、開発中にログイン画面を頻繁に通過する
- Spring Security のフォームログインを採用しているため、毎回 username / password を手入力する必要がある
- E2E テスト（Playwright）では `fixtures` でログイン処理を自動化しているが、手動確認時には毎回入力が発生する
- 開発用のデフォルトユーザー（`admin` / `admin`）は `spring.security.user` で設定済みだが、ログインフォームへの自動入力は行われない

## 決定

**デフォルトプロファイルでは `app.login.default-username` / `app.login.default-password` プロパティを設定し、Thymeleaf テンプレートの `th:value` でフォームにプリセットする。** 本番プロファイル（`application-product.yml`）では空値を設定し、プリセットを無効化する。

### 変更箇所

1. **`application.yml`** — `app.login` プロパティを追加:

    ```yaml
    app:
      login:
        default-username: admin
        default-password: admin
    ```

2. **`application-product.yml`** — 同プロパティを空値で上書き:

    ```yaml
    app:
      login:
        default-username:
        default-password:
    ```

3. **`templates/login.html`** — `th:value` で環境プロパティを参照:

    ```html
    <input type="text" ... th:value="${@environment.getProperty('app.login.default-username')}">
    <input type="password" ... th:value="${@environment.getProperty('app.login.default-password')}">
    ```

### 代替案

1. **ブラウザのパスワードマネージャに依存する**

    - 却下理由: 開発者ごとにブラウザ設定が異なり、CI 環境や新規セットアップ時に効果がない

2. **Spring DevTools の自動ログイン機能を実装する**

    - 却下理由: Spring Security にはそのような標準機能がなく、カスタム `AuthenticationFilter` の実装は過剰

3. **JavaScript で `localStorage` からフォームを埋める**

    - 却下理由: サーバーサイドで完結する Thymeleaf の `th:value` のほうがシンプルで確実

## 影響

### ポジティブ

- 開発中のログイン操作が 1 クリックで完了し、手動確認の効率が大幅に向上する
- プロファイルベースの制御により、本番環境への影響がない
- 追加のコントローラやフィルタが不要で、実装がシンプル

### ネガティブ

- HTML ソース上に `value="admin"` が出力されるため、デフォルトプロファイルのセキュリティは低下する（開発環境なので許容）
- `application-product.yml` に空値設定を忘れた場合、本番でも認証情報が HTML に出力されるリスクがある

## コンプライアンス

- `application-product.yml` で `app.login.default-username` / `app.login.default-password` が空値であることを確認する
- 本番デプロイ時に `SPRING_PROFILES_ACTIVE=product` が設定されていることを確認する
- ログインフォームの HTML ソースに認証情報が含まれないことを本番環境で目視確認する

## 備考

- 関連コミット: `7f029c2` feat(login): デフォルトプロファイルでログインフォームに認証情報をプリセット
- 関連 ADR: なし
