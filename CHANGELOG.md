# CHANGELOG

すべての重要な変更はこのファイルに記録されます。
フォーマットは [Keep a Changelog](https://keepachangelog.com/ja/1.0.0/) に準拠し、
バージョン管理は [Semantic Versioning](https://semver.org/lang/ja/) に従います。

## [1.1.0] - 2026-07-20 (IT7-IT8 完了: Phase 3 経路設計高度化)

### Added

- **US19**: 経路設計条件確認機能（`routing` コンテキスト）
  - 予約詳細画面から「経路設計」ボタンで設計条件（出発地・目的地・最終着日・貨物種別・重量）を表示
  - `DesignConditionQueryService` + `GET /api/v1/routings/bookings/{id}/design-condition`
  - `/routing/search?bookingId=...` 経由で経路候補算出へ遷移
- **US20**: 航海スケジュール検索機能（`routing` コンテキスト）
  - 出港地 UNLOCODE・出発日範囲で航海スケジュールを一覧表示
  - `VoyageScheduleQueryService` + `GET /api/v1/routings/voyages`
  - `/routing/voyages` 画面（検索フォーム + テーブル）
- **US21**: 経路候補算出機能（`routing` コンテキスト）
  - 制約条件（出発地・目的地・最終着日・貨物種別）を考慮した経路候補の自動算出
  - `RouteSearchService` + `StubRouteProviderAdapter`（非 product プロファイル）
  - `/routing/search` 画面（候補カード一覧）
- **US22**: 経路選択・確定機能（`routing` + `booking` コンテキスト）
  - 候補カードの「この予約に割り当てる」ボタンでモーダル表示
  - モーダルで航海区間詳細（経由港・出発日・到着日）を確認してから確定
  - `VoyageLegsQueryService` + `GET /api/v1/routings/voyages/{voyageNumber}/legs`
  - 確定操作で `booking_legs` テーブルに区間詳細を永続化
- **US23**: 経路条件調整・再算出機能（`routing` コンテキスト）
  - 候補なし時に条件調整フォーム（期限延長・貨物種別変更）を表示
  - フォーム送信で経路候補の再算出（US21）を自動実行
  - 調整不能時は「営業担当者に交渉を依頼」リンクを表示
- **US24**: 経路情報予約紐付け・通知機能（`booking` コンテキスト）
  - 経路確定後に `BookingRouteAssignedEvent` を発行
  - `BookingEventHandler` で営業担当者・荷主への通知ログを記録
  - 予約詳細画面に「割り当て済みルート」カード（航海番号・ルートパス・推定着日）を表示
- `BookingLeg` 値オブジェクト（voyageNumber・origin・destination・departure・arrival）
- `booking_legs` テーブル（V016 マイグレーション）
- Playwright E2E テスト: US22（E33〜E35）・US23（E36〜E38）・US24（E39〜E40）

[1.1.0]: https://github.com/example/case-study-cargo-tracker-take-1/releases/tag/v1.1.0

---

## [Unreleased] (IT4 完了)

### Added

- **US11**: 引取記録機能（`handling` コンテキスト）
  - 予約 ID・場所コード・完了日時を入力して引取（RECEIVE）イベントを記録
  - 同一予約での引取二重登録を防ぐバリデーション（409 Conflict）
  - 引取記録画面（`/handling/receive`）
- **US12**: 手動更新記録機能（`handling` コンテキスト）
  - 管理者のみが MANUAL_UPDATE イベントを記録できる（Spring Security ロール制御）
  - メモ必須バリデーション付き
  - 手動更新記録画面（`/handling/manual-update`）
- **US13**: 追跡情報照会機能（`tracking` コンテキスト）
  - 追跡番号で荷役履歴・現在状態・位置を照会（認証不要の公開ページ）
  - `TrackingRestController`（`/api/v1/tracking/{trackingNumber}`）
  - `TrackingWebController`（`/tracking/{trackingNumber}`）

---

## [Unreleased] (IT3 完了)

### Added

- **US10**: 荷役作業登録機能（`handling` コンテキスト）
  - 荷役種別（LOAD/UNLOAD/CUSTOMS/TRANSHIP）・予約 ID・場所・完了日時を記録
  - 予約 BC との連携（`BookingExistencePort` ACL）
  - 荷役作業一覧画面（検索フィルタ付き）・登録フォーム

---

## [Unreleased] (IT2 完了)

### Added

- **US01**: 輸送見積作成機能（`quote` コンテキスト）
  - 出発地・目的地（UN/LOCODE）・希望着日・貨物種別・重量を入力して見積を登録
  - 登録時に `QuoteIssuedEvent` を発行（ADR-002 準拠）
  - 見積番号（Q-YYYYMMDD-XXXX 形式）を自動採番
  - 見積一覧・詳細画面（ルート候補の間に合う/超過バッジ表示）
- **US06**: 最適ルート検索機能（`routing` コンテキスト）
  - 予約 ID 起点のルート候補検索（予約詳細画面の「ルート検索」ボタン）
  - 直接条件指定による再検索（候補なし時のフォーム）
  - ルート候補のフィルタリング（希望着日・貨物種別対応）
  - `BookingQueryPort`（ACL）による booking→routing コンテキスト連携
- スタブルートプロバイダー（`StubRouteProviderAdapter`）- 非 product プロファイルでルート検索を模擬
- RoutingRestController: `RouteSearchService` 未設定時 503 レスポンス

### Changed

- 予約詳細画面に「ルート検索」ボタンを追加
- 見積登録フォームに予約 ID クエリパラメータ連携（`?shipperId=...` に加え `?bookingId=...`）

---

## [0.1.0] - 2026-03-31 (IT1 完了)

### Added

- **US02**: 荷主登録機能（個人荷主）
  - 荷主名・メールアドレス・電話番号を入力して荷主を登録
  - 登録完了後に荷主 ID（UUID）を発行
- **US03**: 法人荷主登録機能
  - 法人名・契約番号・割引率を追加入力して法人荷主を登録
- **US04**: 貨物予約登録機能
  - 荷主 ID・貨物仕様（種別・重量・寸法・個数・品名）・輸送条件（出発地・目的地・希望日）を入力して予約登録
  - 登録時に仮受付（PROVISIONAL）状態の予約を生成し予約番号（UUID）を発行
  - 登録完了後に `BookingRegisteredEvent` をトランザクションコミット後に発行（ADR-002 準拠）
- Spring Boot 4.x + ヘキサゴナルアーキテクチャ基盤（ポート＆アダプター）
- MyBatis + PostgreSQL（Flyway マイグレーション）
- Thymeleaf + htmx による画面（荷主名リアルタイム検索）
- Docker Compose によるローカル起動環境
- Spring Boot DevTools によるホットリロード開発環境
- ログイン画面の開発プロファイル向け自動入力

[0.1.0]: https://github.com/example/case-study-cargo-tracker-take-1/releases/tag/v0.1.0
