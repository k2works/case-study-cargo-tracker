# プロジェクトドキュメント

プロジェクトで管理しているドキュメントの入口です。

## まずこれを読もうリスト

- [戦略](./strategy/index.md) - ビジネス構造やプロジェクトの方向性を整理します。
- [要件](./requirements/index.md) - RDRA 2.0 ベースで要件を定義します。
- [設計](./design/index.md) - アーキテクチャ、モデル、品質方針を整理します。
- [開発](./development/index.md) - リリース計画とイテレーション管理の入口です。
- [運用](./operation/index.md) - 環境構築、デプロイ、運用関連の入口です。
- [記事](./article/index.md) - 学習用の記事シリーズの入口です。

## ドキュメント構成

| カテゴリ | 概要 | 状況 |
| :--- | :--- | :--- |
| [戦略](./strategy/index.md) | ビジネスアーキテクチャ、インセプションデッキの整理 | 2 件作成済み |
| [要件](./requirements/index.md) | RDRA 2.0 とユースケース整理の入口 | 4 件作成済み（US 29 件、IT9 用 US26-29 追加済み） |
| [設計](./design/index.md) | アーキテクチャ、モデル、テスト、非機能の整理 | 10 件作成済み（Axon Kafka + Heroku + Aiven 構成、IT9 設計要素先行反映済み） |
| [開発](./development/index.md) | リリース計画、イテレーション計画、進捗管理 | 28 件作成済み（リリース計画 + Release 1.0 報告書 + IT1-IT9 計画/ふりかえり/完了報告書、IT9 で 7/8 SP 達成 + A4.1 / A3.2 IT10 持ち越し）|
| [運用](./operation/index.md) | 環境構築、デプロイ、運用手順の整理 | 2 件作成済み |
| [レビュー](./review/index.md) | 分析・開発レビュー結果の記録 | 9 件作成済み（IT8 レビュー追加） |
| [ADR](./adr/index.md) | Architecture Decision Records の管理 | 18 件作成済み（ADR-0020 / 0021 追加） |
| [記事](./article/index.md) | 学習用の記事シリーズ一覧 | `index.md` を整備済み |
| [リファレンス](./reference/index.md) | 開発ガイドラインやベストプラクティス | 32 件のドキュメントを配置 |
| [テンプレート](./template/index.md) | 各種ドキュメントの作成テンプレート | 18 件のテンプレートを配置 |

### 戦略ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ビジネスアーキテクチャ](./strategy/business_architecture.md) | ビジネスモデル・バリューストリーム・ケイパビリティ・ビジネスシナリオ |
| [インセプションデッキ](./strategy/inception-deck.md) | プロジェクトの目的・スコープ・リスク・ロードマップ（10 の問い） |

### 要件定義ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [要件定義書](./requirements/requirements_definition.md) | RDRA 2.0 に基づく 4 層（システム価値・外部環境・境界・内部構造） |
| [ビジネスユースケース](./requirements/business_usecase.md) | 業務レベル BUC 21 件・アクター目的リスト |
| [システムユースケース](./requirements/system_usecase.md) | システム境界 UC 19 件（完全形式） |
| [ユーザーストーリー](./requirements/user_story.md) | US 29 件・受け入れ基準・トレーサビリティマトリックス（US26-29 は IT9 用：Stripe webhook / AWS Secrets Manager / 全 endpoint 認可 / SendGrid WireMock） |

### 設計ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [バックエンドアーキテクチャ](./design/architecture_backend.md) | Axon Framework 5 + Axon Kafka Extension + Aiven 構成のバックエンド設計 |
| [フロントエンドアーキテクチャ](./design/architecture_frontend.md) | React 19 + Vite + TypeScript フロントエンド設計 |
| [インフラストラクチャ](./design/architecture_infrastructure.md) | Heroku + Aiven Managed Kafka インフラ構成 |
| [データモデル設計](./design/data-model.md) | ER 図・テーブル定義（IT9 / US26 用 webhook_processed テーブル + invoice.paid_so_far 先行反映済み） |
| [ドメインモデル設計](./design/domain-model.md) | エンティティ・値オブジェクト・集約（IT9 / US26 用 BalanceTracker / PARTIALLY_PAID / RecordPartialPaymentCommand + IT9 / US27 用 TrackingTokenSecretProvider 先行反映済み） |
| [UI 設計](./design/ui_design.md) | 画面遷移図・画面イメージ（IT9 / US26 用 S23 部分入金履歴 + Stripe 遷移リンク + alert-* フィードバック規約先行反映済み） |
| [テスト戦略](./design/test_strategy.md) | テストピラミッド・Testcontainers（Kafka）・カバレッジ目標 |
| [非機能要件](./design/non_functional.md) | 性能・セキュリティ・可用性・保守性要件 |
| [運用要件](./design/operation.md) | 運用フロー・監視設計・障害対応手順 |
| [技術スタック選定](./design/tech_stack.md) | Spring Boot 4 / Java 25 / Axon Kafka / Heroku の技術選定 |

### 開発ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [リリース計画](./development/release_plan.md) | Phase 1/2/Buffer のリリース計画、バーンダウン、履歴（IT1-IT8 完了、76/76 SP、Release 1.0 候補確立） |
| [Release 1.0 候補確立報告書（暫定）](./development/release_report-1.0.md) | Phase 1+2+Buffer 全 76 SP 達成総括、IT9 で正式版昇格予定 |
| [IT1 計画](./development/iteration_plan-1.md) / [IT1 完了報告](./development/iteration_report-1.md) / [IT1 ふりかえり](./development/retrospective-1.md) | Phase 1 / 1 |
| [IT2 計画](./development/iteration_plan-2.md) / [IT2 完了報告](./development/iteration_report-2.md) / [IT2 ふりかえり](./development/retrospective-2.md) | Phase 1 / 2 |
| [IT3 計画](./development/iteration_plan-3.md) / [IT3 完了報告](./development/iteration_report-3.md) / [IT3 ふりかえり](./development/retrospective-3.md) | Phase 1 / 3 |
| [IT4 計画](./development/iteration_plan-4.md) / [IT4 完了報告](./development/iteration_report-4.md) / [IT4 ふりかえり](./development/retrospective-4.md) | Phase 1 / 4（Release 1.0） |
| [IT5 計画](./development/iteration_plan-5.md) / [IT5 完了報告](./development/iteration_report-5.md) / [IT5 ふりかえり](./development/retrospective-5.md) | Phase 2 / 1（追跡・荷役） |
| [IT6 計画](./development/iteration_plan-6.md) / [IT6 完了報告](./development/iteration_report-6.md) / [IT6 ふりかえり](./development/retrospective-6.md) | Phase 2 / 2（公開照会・例外、Release 2.0） |
| [IT7 計画](./development/iteration_plan-7.md) / [IT7 完了報告](./development/iteration_report-7.md) / [IT7 ふりかえり](./development/retrospective-7.md) | Phase 2 / 3（billingms 新設・精算、Release 2.1） |
| [IT8 計画](./development/iteration_plan-8.md) / [IT8 完了報告](./development/iteration_report-8.md) | Phase 2 Buffer（本番デプロイ準備、A1-A4 + H2 持ち越し 8 件 + ADR-0020/0021 起票、Release 1.0 候補確立）|
| [IT9 計画](./development/iteration_plan-9.md) / [IT9 完了報告](./development/iteration_report-9.md) | Release 1.1（Stripe webhook 部分入金 + AWS Secrets Manager 自動回転 + 認可基盤）、7/8 SP 達成（87.5%）、A3.2 / A4.1 IT10 持ち越し |

### ADR ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ADR-0001](./adr/0001-axon-kafka-aiven-adoption.md) | メッセージング基盤として Axon Kafka Extension + Aiven Managed Kafka を採用 |
| [ADR-0002](./adr/0002-mybatis-adoption.md) | データアクセスとして MyBatis を採用 |
| [ADR-0006](./adr/0006-heroku-deployment-setup.md) | Heroku Container Registry を用いた開発環境デプロイ構成 |
| [ADR-0007](./adr/0007-unify-db-initialization-with-flyway.md) | `local-h2` を含む DB 初期化を Flyway に統一し、`schema.sql` を廃止 |
| [ADR-0008](./adr/0008-pagination-strategy.md) | 一覧 API にページネーション (Offset/Limit + PageResponse) を採用 |
| [ADR-0009](./adr/0009-cross-service-event-saga.md) | cross-service イベント連携と Axon Saga を採用（Kafka tracking モード、提案中） |
| [ADR-0010](./adr/0010-local-h2-kafka-topic-initialization.md) | `local-h2` のインメモリ event store と Kafka トピックを整合（トピック初期化と冪等な孤児イベント処理） |
| [ADR-0011](./adr/0011-kafka-tracking-error-handling-policy.md) | Kafka tracking プロセッサのエラーハンドリング統一方針（ホワイトリスト方式） |
| [ADR-0012](./adr/0012-cross-service-idempotency-and-transactions.md) | cross-service 冪等性とトランザクション境界（集約発火型 + 投影フラグ列、自己整合チェックリスト追記済、実装中） |
| [ADR-0013](./adr/0013-public-tracking-token.md) | 公開追跡照会の時限署名トークン（JWT HS256、IT8 T1.6 で四半期ローテーション基盤整備、IT9 で AWS Secrets Manager、提案中） |
| [ADR-0014](./adr/0014-processing-group-naming.md) | Axon @ProcessingGroup 命名規約（`cross-` / `local-` / `outbound-` の 3 種類 prefix、提案中） |
| [ADR-0015](./adr/0015-billingms-cross-service-and-shipper-acl.md) | billingms の cross-service 連携と ShipperInfo ACL（IT8 で完全実装：RestShipperInfoAcl + Resilience4j + Caffeine + S23 fallback UI） |
| [ADR-0016](./adr/0016-processing-group-renaming.md) | 既存 @ProcessingGroup の一斉改名 + token 移行手順 + ArchUnit 構造ガード（提案中） |
| [ADR-0017](./adr/0017-overdue-scheduler-cluster-lock.md) | OverdueScheduler のクラスタ排他制御方針（IT8 実装済み：@SchedulerLock + InMemoryLockProvider 統合テスト） |
| [ADR-0018](./adr/0018-notification-adapter-selection.md) | 通知アダプタ選定（IT8 実装済み：SendGrid Dynamic Templates 9 種 + Heroku Add-on プロビジョニング） |
| [ADR-0019](./adr/0019-payment-detail-recorded-event.md) | PaymentDetailRecorded 補完イベント（IT8 実装済み：集約発火型連続 apply + GET /payments + cross-service E2E） |
| [ADR-0020](./adr/0020-payment-gateway-webhook.md) | 決済機関 webhook 受信設計（Stripe + HMAC + idempotency + 部分入金 PARTIALLY_PAID、IT9 で実装） |
| [ADR-0021](./adr/0021-aws-secrets-manager-rotation.md) | AWS Secrets Manager + Lambda 自動回転（trackingms 公開トークン、IT9 で実装、IT8 で TrackingTokenSecretProvider ポート整備済） |

### 運用ドキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [アプリケーション開発環境セットアップ手順書](./operation/アプリケーション開発環境セットアップ手順書.md) | ローカルアプリケーション開発環境の構築手順（Java 25 + Gradle + Kafka） |
| [開発環境セットアップ手順書](./operation/開発環境セットアップ手順書.md) | Heroku Container Registry デプロイ手順（Axon Kafka + Aiven） |

### レビュードキュメント

| ドキュメント | 概要 |
| :--- | :--- |
| [ドメインモデル分析レビュー](./review/ドメインモデル分析_review_20260331.md) | ドメインモデル分析のマルチパースペクティブレビュー結果（高 11 件・中 12 件・低 5 件） |
| [IT2 ページネーション機能レビュー](./review/pagination_review_20260525.md) | IT2 荷主・予約一覧ページネーション機能のマルチパースペクティブレビュー（高 4 件・中 7 件・低 6 件） |
| [IT3 セッションレビュー](./review/it3_session_review_20260526.md) | IT3 経路設計依頼参照 API + cross-service E2E + 認証ヘッダ統一（高 4 件・中 6 件・低 6 件） |
| [IT4 経路設計レビュー](./review/it4_routing_review_20260526.md) | IT4 経路設計（US08/US09/US11/US12）の本体実装レビュー（高 6 件・中 8 件・低 6 件） |
| [IT4 セッション後続変更レビュー](./review/it4_session_review_20260526.md) | cross-service 堅牢化 + H4 予約化プリセット + Code Smell 解消（高 5 件・中 5 件・低 6 件） |
| [cad796dd レビュー](./review/cad796dd_review_20260528.md) | IT5 着手前セッション変更レビュー |
| [IT5 開発成果物レビュー](./review/IT5_review_20260529.md) | IT5（追跡・荷役）全変更のマルチパースペクティブレビュー（高 7 件・中 10 件・低 12 件） |
| [IT6 開発成果物レビュー](./review/IT6_review_20260529.md) | IT6（追跡照会 + 例外処理）全変更のマルチパースペクティブレビュー（高 9 件・中 11 件・低 8 件） |
| [IT8 開発成果物レビュー](./review/IT8_review_20260605.md) | IT8（A1-A4 + ADR-0020）のマルチパースペクティブレビュー（高 3 件・中 5 件・低 3 件、H1/H3 IT9 持ち越し） |

## 補足

- `journal/` は作業ログ用の予約ディレクトリです。
- `assets/` は MkDocs 用のスタイル・スクリプトを格納しています。
