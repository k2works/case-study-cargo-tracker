# Changelog

[Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) 規約に従う。
[Semantic Versioning](https://semver.org/lang/ja/) を採用 (Internal Alpha は 0.x.x-alpha)。

---

## [Unreleased]

### 予定 (v0.3.0-mvp-preview, IT5 完了予定 2026-09-13)

Phase 3 前半 (追跡番号発行・荷役・引取・追跡照会) を Domain → Application → 最小 HTTP 結線 → UI の順に完成させ、プレ MVP デモを可能化する。

#### Added (計画)

* **US14** 追跡番号を発行する (TrackingNumber VO + IssueTrackingNumberCommand + BookingConfirmed イベント購読)
* **US15** 荷役作業を記録する (HandlingActivity + Voyage/Location 検証 + POST `/handling/new`)
* **US16** 引取作業を記録する (ConfirmationCode 検証 6 桁数字 + bcrypt cost=10 + 5 回試行上限)
* **US18** 追跡情報を照会する (公開ページ `/public/tracking/:trackingNumber` + Leaflet 地図 + タイムライン + rate-limit)
* **セッション認証** (ADR-0010): opaque Cookie + Postgres KV + Servant Auth (JWT は API 用として維持)
* **予約確定/キャンセル HTTP 結線** (IT4 繰越): POST `/bookings/:id/confirm` / cancel / route (POST + DELETE)
* **Itinerary + Leg 永続化** (ADR-0008 採用昇格): PostgresItineraryRepository + migration 2 本
* **荷役オフライン対応** (ADR-0011 提案予定): Service Worker + IndexedDB queue + BackgroundSync

#### Changed (計画)

* HPC カバレッジ gate 74% → 75% (T4-12)
* arch-check ALLOWLIST 全 5 件に sunset 日付コメント必須化 (T4-16)
* arch-check に H-01 SSoT 検査 (Tracking Context 外の TransportStatus コンストラクタ直接使用を警告)
* domain-model.md §4 Tracking Context に ConfirmationCode VO + ConfirmationCodeGenerator を追加

#### ADR

* **ADR-0008** Itinerary + Leg を Booking 集約配下に配置 (提案 → **採用 2026-07-01**)
* **ADR-0010** セッション認証方式 (opaque Cookie + Servant Auth + Postgres KV) 提案
* **ADR-0011** 荷役オフライン対応方式 (Service Worker + IndexedDB) 提案予定

### 見送り (IT6 繰越)

* EvaluateRoute POST エンドポイント (task 1.1 5/5 未達、制約 Form + Query + Command + View の複数モジュール統合が必要)
* ALLOWLIST 実削減 (sunset 日付追加で解消期限は明示、実削減は IT6/IT7)

### 予定 (v0.2.0、IT4 成果物)

* US08b 経路候補 制約評価 / US09 経路選択・確定 / US11 経路紐付け / US13 予約確定
* arch-check Phase 2 (Rule 6) + Phase 3 (T-01〜T-03)
* Playwright E2E 拡張

---

## [0.1.0-alpha] - 2026-06-29

国際貨物輸送管理システム (Haskell 版) の最初の内部レビュー用バイナリ。

詳細: [docs/release/v0.1.0-alpha.md](docs/release/v0.1.0-alpha.md)

### Added

* **US01** 輸送見積を作成する (`/estimates/new` UI + 候補表示 + 予約遷移)
* **US04 + US05** 貨物予約 + CargoType 動的フォーム (htmx)
* **US06** 予約を経路設計者に引き渡す (Draft → Submitted → RouteProposed)
* **US07** 航海スケジュールを検索する (`/voyages/search` + 検索結果)
* **US08a** 経路候補を算出する 基本 (`/bookings/:id/routes` + DFS)
* **US24 + US25** 航海スケジュール登録・更新 (プリフィル対応)
* **US27** 通関情報を予約に紐付ける (HS 6-10 桁 + 申告ステータス 4 値)
* ロール別ナビメニュー (8 ロール + 未認証)
* ADR-0004 Cross-BC ShipperRef VO
* ADR-0005 BC 固有エラーの分離 (Phase 1 起票)
* HPC ゲート 70% + Domain モジュール監視 (29 件)
* RouteFinder criterion 性能ベンチ (1000 voyage で 12.6ms / 目標 500ms)
* RouteFinder hedgehog プロパティ 3 件
* CHANGELOG.md / docs/release/v0.1.0-alpha.md

### Changed

* arch-check Rule 4 ALLOWLIST: 7 件 → **0 件** (ShipperRef VO 移行完了)
* HPC カバレッジゲート: 60% → 70%
* `submitBooking` のエラー型を `ConcurrentModification` → `InvalidStateTransition` (H-01)
* IdGenerator: partial 関数 `alphaNumTable !!` を total 関数 `intToAlphaNumChar` に置換 (H-02)
* customs_declaration テーブル: IT3 US27 用カラム (hs_code / broker_name / declaration_status) を含む最小スキーマで新規作成

### Fixed

* H-01 submitBooking のエラー型不整合 (ConcurrentModification が状態遷移エラーに化けていた)
* H-02 IdGenerator の partial 関数による潜在的クラッシュリスク + 衝突確率コメントを誕生日パラドックス補正
* H-03 US06 で Submit ボタンが UI に存在しなかった問題 (Draft → Submitted の動線が完成)
* H-09 ベロシティ表記の不一致 (180% / 100% / 22 SP) を 3 ドキュメントで統一注記

### Deprecated

* `Cargotracker.Shared.Domain.DomainError.BookingNotFound` / `InvalidStateTransition`: Phase 3 (IT5+) で `Cargotracker.Booking.Domain.Error` に移管予定 (ADR-0005)

### Security

* APP_ENV=production 環境変数で DATABASE_URL / JWT_SECRET 未設定時 fail-fast 維持

---

## [0.1.0-pre] - 2026-06-27 (IT2)

* IT2 完了時点 (US01 / US04+US05 / US06 / US25 + Try 10 件)
* arch-check Phase 1 + Rule 4 + ALLOWLIST 機構
* Estimation Context

## [0.0.x] - 2026-06-26 (IT1)

* Booking / Shipper / Routing Context の基盤
* Servant + Lucid + htmx + PostgreSQL スタック
* JWT 認証 + Login UI

[Unreleased]: https://github.com/k2works/case-study-cargo-tracker/compare/v0.1.0-alpha...haskell/take-1
[0.1.0-alpha]: https://github.com/k2works/case-study-cargo-tracker/releases/tag/v0.1.0-alpha
