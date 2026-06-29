# Changelog

[Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) 規約に従う。
[Semantic Versioning](https://semver.org/lang/ja/) を採用 (Internal Alpha は 0.x.x-alpha)。

---

## [Unreleased]

### 予定 (v0.2.0)

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
