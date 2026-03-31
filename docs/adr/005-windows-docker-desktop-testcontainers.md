# ADR-005: Windows Docker Desktop では Testcontainers を docker_engine_linux に接続する

Windows 環境の統合テストは Docker Desktop の Linux Engine と Testcontainers を使って実行します。

日付: 2026-03-31

## ステータス

承認済み

## コンテキスト

開発環境は Windows 11 + Docker Desktop を前提にしていますが、Spring Boot 統合テストで Testcontainers を使う際に標準の named pipe 参照では PostgreSQL コンテナを安定して起動できませんでした。

- Docker CLI は `npipe:////./pipe/docker_engine_linux` で Linux Engine に正常接続できる
- Testcontainers の既定挙動では `docker_engine` を見に行き、API version 交渉や named pipe の解決で失敗する
- `ShipperRepositoryTest` は PostgreSQL Testcontainers を前提とした統合テストであり、H2 フォールバックでは検証の厳密性が落ちる
- Windows ローカル開発でも CI に近い形で PostgreSQL を使った統合テストを継続実行したい

## 決定

Windows Docker Desktop 上の統合テストでは、Testcontainers を Docker Desktop の Linux Engine (`docker_engine_linux`) に明示接続する。

### 変更箇所

- `apps/cargo-tracker/build.gradle`
  - `test` タスクに `DOCKER_HOST=npipe:////./pipe/docker_engine_linux` を設定する
  - Testcontainers が参照する `api.version` と `DOCKER_API_VERSION` を設定する
  - `docker-java` 関連依存を `3.7.0` に引き上げる
- `apps/cargo-tracker/src/test/resources/testcontainers.properties`
  - `docker.host=npipe:////./pipe/docker_engine_linux` を定義する
  - `dockerconfig.source=autoIgnoringUserProperties` を定義する
- `apps/cargo-tracker/src/test/java/com/example/cargotracker/support/PostgreSQLIntegrationTestBase.java`
  - `@Testcontainers` を付与し、`@Container` の PostgreSQL 起動を JUnit 連携で管理する
- `apps/cargo-tracker/src/main/resources/mapper/ShipperMapper.xml`
  - `ShipperRecord` が Java record であることに合わせて constructor mapping を使用する
  - `findById` の PostgreSQL 固有キャスト `::uuid` を外し、DB 依存を弱める

### 代替案

- 既定の `docker_engine` を使う
  - Docker Desktop 上で API version 交渉に失敗し、Testcontainers が接続できなかったため却下
- 統合テストを H2 に固定する
  - PostgreSQL との差異を見逃しやすく、Testcontainers 導入意図に反するため却下
- ローカルでは Docker を使わず CI のみ Testcontainers を使う
  - 開発者の手元で再現できる統合テストが失われ、フィードバックが遅くなるため却下

## 影響

### ポジティブ

- Windows ローカル環境でも PostgreSQL を使った統合テストを安定実行できる
- Docker Desktop の Linux Engine を明示利用するため、接続先が分かりやすい
- CI に近い DB 実行環境でリポジトリ統合テストを回せる

### ネガティブ

- Windows Docker Desktop 固有の設定がテスト基盤に入る
- Testcontainers と `docker-java` の実装差分に影響されるため、依存更新時の再検証が必要
- Docker Desktop が起動していない環境では PostgreSQL 統合テストは失敗する

## コンプライアンス

- `apps/cargo-tracker` で `./gradlew.bat test --tests com.example.cargotracker.shipper.infrastructure.ShipperRepositoryTest` を実行し成功すること
- テストログに `Found Docker environment ... docker_engine_linux` が出力されること
- テストログに `Container postgres:16-alpine started` と PostgreSQL JDBC URL が出力されること
- `./gradlew.bat test` が成功すること

## 備考

- 著者: Codex
- 関連コミット: 未コミット
- 関連 ADR: ADR-001
- 参照コミット: `a97357b`
