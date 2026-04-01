# ADR-006: Spring Boot 4 の開発環境では H2 Console 専用モジュールと security 例外を明示設定する

Spring Boot 4 の開発用 H2 Console は、専用モジュール追加と security 設定をセットで行います。

日付: 2026-04-01

## ステータス

承認済み

## コンテキスト

ローカル開発手順では `default` profile で H2 インメモリ DB を使い、`http://localhost:8080/h2-console` から内容を確認する前提でした。しかし実際には `spring.h2.console.enabled=true` を設定していても、`/h2-console` が 404 になっていました。

- `apps/cargo-tracker/src/main/resources/application.yml` では `spring.h2.console.enabled=true` と `path=/h2-console` を設定済みだった
- Spring Boot 4 では H2 Console 関連が別モジュール化されており、`com.h2database:h2` だけでは Console servlet が登録されない
- Spring Security を有効にしているため、H2 Console を表示するには `/h2-console` と配下へのアクセス許可、CSRF 除外、`frameOptions` 調整が必要だった
- 開発者はドキュメント通りの URL にアクセスして DB 状態を確認できる必要がある

## 決定

Spring Boot 4 の `default` profile 開発環境では、H2 Console を利用するために専用ランタイム依存と H2 Console 向け security 例外を明示的に設定する。

### 変更箇所

- `apps/cargo-tracker/build.gradle`
  - `runtimeOnly 'org.springframework.boot:spring-boot-h2console'` を追加する
- `apps/cargo-tracker/src/main/java/com/example/cargotracker/shared/infrastructure/config/SecurityConfig.java`
  - `/h2-console` と `/h2-console/` を許可する matcher を追加する
  - H2 Console 配下を security の認可対象から除外する
  - H2 Console 向けに CSRF を除外する
  - H2 Console の表示に必要な `frameOptions.sameOrigin()` を有効化する

### 代替案

- `spring.h2.console.enabled=true` のみで運用する
  - Spring Boot 4 では Console servlet が登録されず、`404` のままなので却下
- H2 Console を使わず SQL クライアントやログ確認だけで代替する
  - 既存ドキュメントと運用手順が崩れ、開発時の確認コストが上がるため却下
- H2 Console へのアクセスに認証を必須のまま残す
  - 開発用ユーティリティへの到達性が落ち、`/h2-console` の直接アクセス時に期待どおりの画面へ到達しにくいため却下

## 影響

### ポジティブ

- `default` profile の起動後に `http://localhost:8080/h2-console` から H2 Console を利用できる
- ドキュメントに記載した開発 URL と実装が一致する
- Spring Boot 4 のモジュール分割を明示的にコードへ反映できる

### ネガティブ

- 開発用依存関係と security 例外が増える
- H2 Console は開発環境向け機能なので、本番プロファイルでは引き続き無効化を維持する必要がある
- Spring Boot / Spring Security のメジャー更新時は H2 Console の有効化方法を再確認する必要がある

## コンプライアンス

- `apps/cargo-tracker` で `./gradlew.bat bootRun` を起動し、ログに `H2 console available at '/h2-console'` が出力されること
- `http://localhost:8080/h2-console` が `302` で `/h2-console/` にリダイレクトされること
- `http://localhost:8080/h2-console/` が `200` を返し、H2 Console の HTML を返すこと
- `product` profile では `spring.h2.console.enabled=false` を維持すること

## 備考

- 著者: Codex
- 関連コミット: 未コミット
- 関連 ADR: ADR-001
- 参照コミット: `5d09a3b`
