# Changelog

## [0.1.0] - 2026-05-09

### Features

- **TI04**: bookingms↔trackingms RabbitMQ イベント連携実装（TrackingNumberIssuedEvent）(e44599a7)
- **TI05 H6**: API バリデーションエラーに具体的なフィールド・メッセージを含む構造化レスポンスを実装 (3120965f)
- IT5 実装: ダッシュボード遷移カード・ナビ拡充・E2E テスト追加 (58a78013)
- IT5: TI02/US18/US06/US12 実装（追跡管理 UI、荷役記録、経路設計担当一覧） (f0eaaca1)
- IT4: 追跡管理 UI 実装（US14/US15/US17） (42bc3650)
- trackingms: US17 貨物状態手動更新 API (babef3fe)
- trackingms: US15 荷役作業記録 API (c5a53726)
- trackingms: US14 追跡番号発行 API (8136c0a3)
- bookingms: デモ用貨物シードデータ追加（仮予約3件・経路提案2件・確定3件） (ea69f928)
- 経路設計ページにデフォルト値の自動検索を追加（JPTYO/CNSHA） (ecf2ce95)
- bookingms: CargoRoutedEvent 発行ロジック追加（RabbitMQ） (d867e880)
- US13: 予約確定・キャンセル機能実装（CONFIRMED / CANCELLED への状態遷移）
- US09/US11: 予約管理・経路設計機能実装

### Bug Fixes

- **TI05/TI07 FE**: イベント履歴状態列バグ・経路通知バッジ条件・追跡番号保持・逆行遷移制限 (985bf0b4)
- 経路設計ページへの直接遷移を禁止し予約経由ワークフローに修正 (4d0c295f)
- routingms: 航海シードデータを追加して E2E 経路検索テストを修正 (4d1410c7)
- CI: bookingms 起動時の RabbitMQ ヘルスチェックを無効化（CI 環境 503 対応） (d0700074)
- bookingms: IT3 コードレビュー高優先度指摘解消 (62f1f3c6)

### Tests

- **TI07 BE**: TrackingQueryServiceTest・イベント発行テスト・RecordingPublisher 記録追加 (71d0d858)
- **TI03**: E2E テスト整備（基幹フロー: 荷役記録→追跡照会シナリオ追加） (af543041)
- **TI06 FE**: テストカバレッジ改善（DashboardPage・BookingForm テスト追加） (65c5d249)
- Testcontainers RabbitMQ 連携テスト追加 (7c5e2e35)
- 予約→経路割り当て→予約確定の一連フロー E2E テスト追加

### Refactoring

- SonarQube Code Smell 解消（IT4/IT5） (4c385137, fd5bb451, 5c08c845)
- CargoCommandServiceTest の SonarQube Code Smell 解消 (fd5bb451)
- bookingms/authms/gatewayms/routingms: コード品質改善とテスト追加

### Documentation

- ADR-003/004 追加（CargoRoutedEvent・Testcontainers RabbitMQ テスト戦略） (bf1ae100)
- IT4〜IT6 イテレーション計画・完了報告書・ふりかえり

---

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
