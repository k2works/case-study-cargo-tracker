# ADR-006: Testcontainers でシングルトンコンテナパターンを採用する

API E2E テストで PostgreSQL Testcontainers のシングルトンコンテナパターンを使用し、テスト実行を高速化する。

日付: 2026-04-04

## ステータス

承認済み

## コンテキスト

API E2E テストでは実際の PostgreSQL を使用して結合テストを行う必要がある。

- テストクラスごとにコンテナを起動・停止すると実行時間が大幅に増加する
- Spring コンテキストキャッシュとの不整合が発生する可能性がある
- Docker Desktop の最新版（API 1.54）と Testcontainers 1.20.4 の間で API バージョンの不一致が発生する

## 決定

**`PostgreSQLIntegrationTestBase` 基底クラスで static ブロックによるシングルトンコンテナパターンを採用する。Docker API バージョンは 1.43 に固定する。**

### 変更箇所

- `src/test/java/.../support/PostgreSQLIntegrationTestBase.java` でシングルトンコンテナを定義
- `build.gradle` に `docker-java-api:3.7.0`、`docker-java-transport:3.7.0`、`docker-java-transport-zerodep:3.7.0` を追加
- `build.gradle` の test タスクに `DOCKER_API_VERSION=1.43` を設定

### 代替案

| 代替案 | 却下理由 |
|--------|---------|
| `@Testcontainers` + `@Container` アノテーション | テストクラスごとにコンテナが再作成され、実行が遅い |
| H2 互換モードで統合テスト | PostgreSQL 固有機能のテストができない |
| 外部 PostgreSQL に直接接続 | テスト環境の再現性が低い |

## 影響

### ポジティブ

- JVM プロセス内で PostgreSQL コンテナが 1 回だけ起動されるため高速
- `@DynamicPropertySource` で Spring の DataSource と Flyway 設定が自動的にコンテナに向けられる
- テストクラス間でコンテナを共有するため、Spring コンテキストキャッシュが有効活用される

### ネガティブ

- テスト間のデータ分離は `@AfterEach` での手動クリーンアップが必要
- Docker Desktop が起動していない環境ではテストが実行できない

## コンプライアンス

- `./gradlew test --tests "com.example.cargotracker.e2e.*"` が成功すること
- Docker Desktop が起動した状態で実行すること

## 備考

- 関連コミット: `6b4ca26`
- 関連 ADR: ADR-001
