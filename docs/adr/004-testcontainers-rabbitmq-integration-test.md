# ADR-004: RabbitMQ 連携テストに Testcontainers を採用する

RabbitMQ を必要とするメッセージング統合テストには、モックではなく Testcontainers でリアルな RabbitMQ コンテナを起動して検証するアプローチを採用します。

日付: 2026-05-08

## ステータス

承認済み

## コンテキスト

ADR-003 でポート/アダプタパターンを採用した結果、`RabbitMqCargoEventPublisher` の動作を検証するテストが必要になった。

- `CargoCommandService.assignRoute()` 呼び出し後に実際に RabbitMQ キューへメッセージが到達するかを確認したい
- モックでは「`CargoEventPublisher.publishCargoRouted` が呼ばれた」ことは検証できても、「実際にキューに届いた」かは検証できない
- 開発環境には常時稼働の RabbitMQ が存在しないため、テスト時にコンテナを起動する必要がある

## 決定

**Testcontainers の `RabbitMQContainer` を使用して、テスト実行時のみ RabbitMQ コンテナを起動する。**

### 変更箇所

`CargoRoutedEventPublisherTest.java` に以下の設計を採用した。

| 設計要素 | 内容 |
|---------|------|
| `@Testcontainers` + `static RabbitMQContainer` | クラス単位でコンテナを共有 |
| `@DynamicPropertySource` | コンテナのポート/認証情報を Spring 設定に注入 |
| `@TestConfiguration` + `@Primary` | テスト用 `CargoEventPublisher`（JSON変換設定済み）を明示定義 |
| `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` | テスト間のコンテキスト分離 |
| `@Sql(AFTER_TEST_METHOD)` | H2 共有 DB のテストデータクリーンアップ |
| `Jackson2JsonMessageConverter` | テスト用 `RabbitTemplate` に JSON コンバーターを設定 |

### テスト設計の判断

テスト内 `@TestConfiguration` で `CargoEventPublisher` を `@Primary` として定義する理由：
- `MessagingConfiguration` の `@ConditionalOnBean(RabbitTemplate.class)` は Bean 登録順序に依存するため、テストコンテキストでは `NoOp` が選ばれる場合があった
- `@Primary` で明示的に RabbitMQ 実装を優先させることで確実な動作を保証する

### 代替案

| 案 | 説明 | 却下理由 |
|----|------|---------|
| Mockito でモック | 高速・シンプル | ネットワーク経路・シリアライズ・Exchange/Queue バインディングが未検証 |
| 外部 RabbitMQ に依存 | 設定が最小限 | CI 環境で常時起動の保証ができない。環境依存テストになる |
| RabbitMQ のみの単体テスト | ピンポイント検証 | `CargoCommandService` → `CargoEventPublisher` → RabbitMQ の連携が未検証 |

### 他のテストへの影響

RabbitMQ を必要としないテスト（`CargoControllerTest`・`BookingApplicationTests`）では `@MockitoBean CargoEventPublisher` を使用。`test/resources/application.properties` では AMQP の除外設定を行わず、テストクラスごとに制御する方針とした。

## 影響

### ポジティブ

- メッセージが実際にキューに到達することをエンドツーエンドで検証できる
- CI で Docker が使える環境なら追加のインフラ設定なしに実行できる
- JSON シリアライズ・Exchange バインディング等のインフラ設定も自然にテストされる

### ネガティブ

- Docker コンテナ起動のため、テスト実行時間が増加する（`rabbitmq:3.13-management` のプル・起動に数十秒）
- `@DirtiesContext` でコンテキストが毎テストメソッドごとに再作成されるため、テストスイート全体の実行時間が長くなる

## コンプライアンス

- `CargoRoutedEventPublisherTest` が CI（GitHub Actions）で正常に通過することで確認する
- テストが Docker デーモンに依存するため、CI ジョブの `services` または `runs-on` に Docker が利用可能な環境を指定する

## 備考

- 著者: 開発チーム
- 関連コミット: `7c5e2e35`
- 関連 ADR: ADR-003
