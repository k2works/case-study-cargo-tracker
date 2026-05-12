# ADR-0001 メッセージング基盤として Axon Framework 5 を採用する

国際貨物輸送管理システムのマイクロサービス間メッセージング、コマンド処理、イベント永続化、Saga による業務プロセス調整を統合的に扱う基盤として、**Axon Framework 5 系 + Axon Server（2024.x 系）** を採用する。

日付: 2026-05-12

## ステータス

2026-05-12 受け入れ済み（Axon Framework 5 採用）

## コンテキスト

国際貨物輸送管理システムはマイクロサービスアーキテクチャ（authms / bookingms / routingms / trackingms / handlingms / billingms / gatewayms）として設計している。複数のサービスをまたぐ業務プロセスが多く、メッセージング基盤の選定が次の特性を満たす必要がある。

- **長期に渡る業務プロセスの調整**：予約 → 経路割り当て → 追跡番号発行 → 荷役 → 配送 → 精算という複数集約・複数サービスにわたる業務プロセスを、信頼性高く調整できること
- **イベント駆動による疎結合**：状態変更を「事実」としてイベントで配信し、購読側が結果整合性で追随できること
- **監査と再現性**：貨物の状態履歴を後から完全に追跡可能であり、必要なら過去の状態を再生できること（例外調査・問い合わせ対応）
- **CQRS の自然な実装**：書き込み（コマンド）と読み取り（クエリ）の分離をフレームワークレベルで強制し、Read Model を独立に最適化できること
- **Spring Boot との親和性**：既存の Spring Boot / Spring Cloud スタックと整合的に統合できること

代表的な選択肢として次の 3 つを評価した。

### 候補 1: RabbitMQ + Spring Cloud Stream（参考プロジェクトの当初案）

- 長所：軽量で運用知見が広く存在する。Spring Cloud Stream で抽象化可能
- 短所：CQRS / Event Sourcing / Saga はアプリ側で自作する必要があり、ボイラープレートが多い。Transactional Outbox / Saga パターンの実装責任がチーム側に残る。トランザクション境界とイベント発行の同期も自前で担保する必要がある

### 候補 2: Apache Kafka + Spring Cloud Stream

- 長所：高スループット、ストリーミング処理に強い。ログ指向のため Event Sourcing に親和的
- 短所：運用コストが高い（ZooKeeper / KRaft）。Saga / Command Bus / Query Bus は別途実装が必要。Topic 設計の責任が大きく、開発初期の学習コストが大きい

### 候補 3: Axon Framework 5 + Axon Server（採用）

- 長所：
  - **CQRS / Event Sourcing / Saga が一体提供**：`@Aggregate`、`@CommandHandler`、`@EventSourcingHandler`、`@EventHandler`、`@QueryHandler`、`@Saga` の各アノテーションでフレームワークが標準パターンを強制
  - **Event Store 内蔵**：Axon Server がイベントの永続化・配信・スナップショット・トークン管理を統合的に提供
  - **トランザクション境界が明確**：集約単位のコマンド処理 + Event Store への永続化 + 購読者への配信が一貫して扱える
  - **Saga 実装が宣言的**：`@Saga` + `associationProperty` で長期プロセスを宣言的に記述可能
  - **テスト容易性**：`AggregateTestFixture` / `SagaTestFixture` で Given-When-Then 形式のテストが書ける
  - **Spring Boot Starter**：`axon-spring-boot-starter` 一つで統合完了
  - **Axon 5 の追加メリット**：Spring Boot 3 系以降との整合、機能ベース設定 API（Configurer / Component Registry）、改善された Saga / Event Processor、Java 17+ の言語機能の活用
- 短所：
  - フレームワーク固有の学習コスト
  - Axon Server がメッセージング基盤のシングルポイント。Standard Edition は単一ノード（HA は Enterprise Edition）
  - Event Sourcing 採用に伴うスキーマ進化対応（Upcaster）が必要
  - Axon 5 は Axon 4 系から API 変更が一部あるため、参考実装（Chapter 6・Axon 4.2）からのコード移植時に読み替えが必要

## 決定

**メッセージング基盤として Axon Framework 5 系 + Axon Server 2024.x 系を採用する。**

具体的には次のとおりとする。

- **採用バージョン**: Axon Framework 5.1.0 GA、Axon Server 2026.0.0（Standard Edition）
  - 実装着手前に **公式情報源で GA 時期と EOL を確認** する。確認チェックリストは [tech_stack.md §実装着手前の確認チェックリスト](../design/tech_stack.md) 参照
  - 主要情報源: <https://www.axoniq.io/products/axon-framework>, <https://github.com/AxonFramework/AxonFramework/releases>, <https://docs.axoniq.io/axon-server-reference/>
  - GA 未達の場合の代替案: Axon Framework 4.10.x + Spring Boot 3.3 LTS の組合せ
- **集約は Event Sourcing で永続化**：`@Aggregate` + `@CommandHandler` + `@EventSourcingHandler` を使用し、Axon Server の Event Store に永続化する（authms を除く）
- **Read Model は MyBatis + PostgreSQL**：`@EventHandler` で Event Store のイベントを購読し、各サービス専用 DB の Read Model テーブルを MyBatis Mapper で更新する。Axon の `JdbcTokenStore` / `JdbcSagaStore` は同一 DataSource を共有し、Projection 更新と同一 JDBC トランザクションで処理する
- **マイクロサービス間連携は Axon Server 経由の分散 Event Bus**：RabbitMQ / Kafka は採用しない
- **業務プロセスは `@Saga`**：複数集約・複数サービスにまたがる業務プロセスは Saga で調整する。例: `BookingSagaManager`（予約 → 経路 → 追跡）
- **同期クエリは REST**：経路候補取得など Saga 内で必要となる同期クエリは ACL 経由の REST API で行う（または Axon 分散 Query Gateway の利用を検討する）
- **authms は Event Sourcing 対象外**：認証データは状態指向のため通常の MyBatis CRUD で管理する
- **Java / Spring Boot バージョン**: Axon 5 は Java 17 以上を要求する。本プロジェクトは Java 25 / Spring Boot 4.x で運用する

### 採用判断の根拠

- 業務領域が「中核の業務領域」で複雑なビジネスルールと長期プロセスを持つ
- 監査・追跡可能性（透明性の高い輸送サービスというビジネスビジョン）と相性が良い
- Saga の宣言的な記述により業務プロセスのコード化が読みやすくなる
- 候補 1 / 2 と比較して、CQRS / Event Sourcing / Saga のボイラープレートが圧倒的に少ない
- Practical DDD in Enterprise Java（Chapter 6）でリファレンス実装が示されており、学習資料が揃っている

## 影響

### コードへの影響

- 各マイクロサービスは Axon 5 の依存関係（`org.axonframework:axon-spring-boot-starter:5.x`）を導入する
- パッケージ構成を Axon 標準（`commandgateways/`, `querygateways/`, `sagaparticipants/`, `commands/`, `events/`, `queries/`, `queryhandlers/`, `projections/`）に揃える
- Aggregate は Axon のアノテーションでマークし、`AggregateLifecycle.apply()` でイベントを発行する
- Read Model（Projection）は **MyBatis Mapper + POJO** で実装する（JPA `@Entity` は使用しない）
- 参考実装（Chapter 6・Axon 4.2）からの移植時は、`javax → jakarta` への置換、設定 API の刷新箇所（Configurer / Component Registry）への追従が必要

### インフラへの影響

- Axon Server コンテナを Docker Compose / ECS に追加する
- Event Store 用のストレージを確保する（コンテナボリュームまたは EBS）
- 各マイクロサービスは Axon Server へのネットワーク到達性を確保する（8124 / gRPC、8024 / HTTP UI）

### 運用への影響

- Axon Server の監視（Event Stream の状態、Token の遅延、トランザクション件数）を追加する
- スキーマ進化時は **アップキャスター（Upcaster）** をリリース手順に組み込む
- Read Model 再構築時は **Event Processor の Token をリセット** する手順を整備する

### チームへの影響

- Axon Framework 5・Event Sourcing・CQRS・Saga の学習が必要
- 「現在の状態を直接書き換える」発想から「事実イベントを記録する」発想への移行
- テストは Axon Test Fixture（Axon 5 版）を使う前提で標準化する
- 学習リソース: AxonIQ 公式ドキュメント（5.x）、Axon University、参考実装 Chapter 6 は 4.2 系であるため API 差分の認識が必要

### 将来への影響

- 大規模化 / HA 要件発生時は Axon Server Enterprise Edition への移行で対応可能（フォーマット互換性あり）
- 監査・透明性要求が強まった場合、Event Sourcing による完全な履歴を活用できる
- 業務プロセスが複雑化しても Saga の追加で対応可能

## フェーズ別稼働率と EE 移行計画

Axon Server Standard Edition は単一ノード前提のため、フェーズ 1 では稼働率目標を **業務時間内 99.9% / 24 時間 99.5%** に設定する。これは SE の MTTR（EBS 再アタッチ運用で数分〜十数分）と整合する現実的目標。99.95% 以上を求める場合はクラスタ構成（EE）が必須となるため、フェーズ 2 で EE への移行を計画する。

| フェーズ | Axon Server | SLA（業務時間内） | SLA（24h） | RPO（Event Store） | 想定時期 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **フェーズ 1（v1.0）** | Standard Edition（単一ノード） | 99.9% | 99.5% | 1 時間 | リリース初年度 |
| **フェーズ 2** | Enterprise Edition（3 ノードクラスタ） | 99.95% | 99.9% | < 5 分 | 利用者 500 名超過 or 重大障害 1 件発生時 |
| **フェーズ 3（必要時）** | EE マルチリージョン | 99.99% | 99.95% | < 1 分 | グローバル展開時 |

### EE 移行のトリガー条件（いずれか 1 つで開始）

- 同時利用者ピークが 500 名を 3 ヶ月連続で超過
- Axon Server SE 起因の SEV-1 または SEV-2 障害が四半期に 1 件以上発生
- 業務要件として 99.95% SLA を契約で約束する顧客が発生
- RPO 1 時間を下回るデータ保全要求（金融・行政連携など）

### EE 移行の主要作業

| 作業 | 内容 | 所要 |
| :--- | :--- | :--- |
| ライセンス契約 | AxonIQ から EE ライセンス取得 | 1〜2 ヶ月（営業・契約） |
| クラスタ構築 | 3 ノード ECS EC2 起動タイプ、AZ 分散、共有ストレージなし（各ノード EBS） | 2 週間 |
| データ移行 | SE の Event Store を EE クラスタへインポート、整合性確認 | 1 日（メンテナンス時間帯） |
| クライアント更新 | `axon.axonserver.servers` を複数ノード指定に変更 | 1 日 |
| 監視・運用更新 | EE 固有メトリクス（リーダー選出・レプリケーション遅延）の追加 | 1 週間 |
| 検証 | リーダー停止 → フェイルオーバー訓練 | 半日 |

### コスト影響

- SE → EE 移行で月額コストが約 $80 → 約 $1,500（ライセンス + 3 ノード分のインフラ）に増加見込み
- SLA 99.95% の業務価値（クレーム削減・顧客信頼）と費用対効果を都度評価する

## コプライアンス

- バックエンドアーキテクチャドキュメント（`docs/design/architecture_backend.md`）が Axon Framework 5 ベースで記述されていること
- 各マイクロサービスの `build.gradle` に `axon-spring-boot-starter:5.x` 依存が含まれていること
- 永続化は MyBatis Mapper（XML / Annotation）で実装され、JPA（`jakarta.persistence.*`）への依存が無いこと（ArchUnit で検証）
- Aggregate を持つマイクロサービスにおいて、`@Aggregate` を付与した集約が存在し、対応する `@EventSourcingHandler` を持つこと
- Read Model は `@EventHandler` で更新され、Query Side は `@QueryHandler` 経由で参照されること
- メッセージング基盤として RabbitMQ / Kafka を追加で導入していないこと
- Docker Compose / インフラ定義に `axoniq/axonserver:2026.0.0` コンテナが定義されていること（Docker Hub の実在タグを使用。`-LTS` サフィックスは現リリースモデルには存在しない）

ArchUnit による静的検証（パッケージ依存・アノテーション利用ルール）で上記を自動チェックする。

## 備考

- 著者: アーキテクト
- 参照: [バックエンドアーキテクチャ](../design/architecture_backend.md)、Practical DDD in Enterprise Java（Chapter 6）、参考プロジェクト `tmp/case-study-cargo-tracker/docs/design/architecture_backend.md`
- 関連 ADR: 後続でデータベース選定（PostgreSQL 維持）・Java バージョン・ビルドツールの ADR を発行予定
