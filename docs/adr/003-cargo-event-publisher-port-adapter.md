# ADR-003: CargoRoutedEvent 発行にポート/アダプタパターンを採用する

ドメイン層にイベント発行ポート（`CargoEventPublisher`）を定義し、RabbitMQ 実装と NoOp フォールバックをインフラ層で提供するポート/アダプタパターンを採用します。

日付: 2026-05-08

## ステータス

承認済み

## コンテキスト

IT3 のユーザーストーリー US11（貨物の経路割り当て）では、経路が確定したタイミングで `CargoRoutedEvent` を発行し、下流サービスへ通知する要件が生まれた。

- `CargoCommandService`（アプリケーション層）が経路割り当て完了後にイベントを発行する
- イベントブローカーは RabbitMQ（`spring-boot-starter-amqp`）を使用
- 開発・テスト環境では RabbitMQ が起動していない場合もある
- ドメイン層を特定インフラに結合させたくない（DDD の依存関係逆転原則）

## 決定

**ドメイン層にインターフェース、インフラ層に実装を配置するポート/アダプタパターンを採用する。**

### 変更箇所

| パッケージ | クラス | 役割 |
|-----------|-------|------|
| `domain.events` | `CargoRoutedEvent` | ドメインイベント（Java record） |
| `domain.ports` | `CargoEventPublisher` | 発行ポート（インターフェース） |
| `infrastructure.messaging` | `RabbitMqCargoEventPublisher` | RabbitMQ アダプタ |
| `infrastructure.messaging` | `MessagingConfiguration` | Bean 設定（JSON コンバーター含む） |

`CargoCommandService` は `CargoEventPublisher` インターフェースのみに依存し、具体実装を知らない。

`MessagingConfiguration` では `Jackson2JsonMessageConverter` を設定し、イベントを JSON 形式でシリアライズする。

### 代替案

| 案 | 説明 | 却下理由 |
|----|------|---------|
| ドメイン層から直接 RabbitTemplate を呼ぶ | 実装が簡単 | ドメイン層がインフラに依存する（DDD 違反） |
| Spring ApplicationEvent 経由で発行 | Spring エコシステムで完結 | マイクロサービス間の非同期通信には不適切。ブローカーへの発行が自明でない |
| NoOp なし（RabbitMQ 必須） | 構成がシンプル | テスト・開発環境での起動コストが増大 |

### RabbitMQ 未設定時のフォールバック

`@ConditionalOnMissingBean(CargoEventPublisher.class)` で NoOp 実装を登録し、RabbitMQ が設定されていない環境（テスト・開発）でも起動できるようにする。テストでは `@MockitoBean CargoEventPublisher` を使用することで、RabbitMQ なしの統合テストが可能になる。

## 影響

### ポジティブ

- ドメイン層がインフラに依存せず、クリーンアーキテクチャを維持できる
- テストで `@MockitoBean` を使って RabbitMQ なしの統合テストが書ける
- 将来的にブローカーを Kafka 等に変更してもドメイン層に影響しない
- JSON シリアライズにより他言語サービスとのインターオペラビリティが向上する

### ネガティブ

- `@ConditionalOnBean` の評価順序によっては NoOp が優先されるリスクがある（テストで `@Primary` を使って回避）
- 設定クラスが増え、Bean のライフサイクル管理が複雑になる

## コンプライアンス

- `domain` パッケージ内に RabbitMQ への直接依存が存在しないことを ArchUnit テストで検証する
- `CargoRoutedEventPublisherTest`（Testcontainers）で実際にキューへの到達を確認する

## 備考

- 著者: 開発チーム
- 関連コミット: `d867e880`, `7c5e2e35`
- 関連 ADR: なし
