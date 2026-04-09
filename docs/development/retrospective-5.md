# イテレーション 5 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 5 |
| **期間** | 2026-04-23 〜 2026-05-06（計画）/ 2026-04-09 に全主要タスク完了（実績） |
| **ゴール** | 経路選択・確定・条件再算出・予約紐付けを完成させ、Phase 2 の経路設計フローを完結させる。SonarQube Quality Gate をセッション冒頭で必ず確認する |
| **計画 SP** | 10 |
| **実績 SP** | 10 |
| **達成率** | 100% |
| **ふりかえり実施日** | 2026-04-09 |

---

## 実績メトリクス

| メトリクス | 目標 | 実績 | 評価 |
|-----------|------|------|------|
| ストーリーポイント | 10 SP | 10 SP | 達成 |
| Java テスト | 全パス（230 件以上目安） | 250 件全パス（IT4: 217 件から +33 件） | 達成 |
| Playwright E2E テスト | 全パス（46 件以上目安） | 56 件全パス（IT4: 40 件から +16 件） | 達成 |
| SonarQube Quality Gate | PASS（初回達成） | PASS（IT5 冒頭で確認・対応完了） | 達成 |
| 命令カバレッジ | 80% 以上 | 88% | 達成 |
| ブランチカバレッジ | 80% 以上 | 75% | 部分達成 |
| IT4 申し送り対応 | SonarQube・状態ガード抽出・ナビ順序変更 | 3 件全完了 | 達成 |
| US09 経路を選択・確定する | 完了 | 完了（経路割り当て画面・PRG パターン実装済み） | 達成 |
| US10 経路条件を調整して再算出する | 完了 | 完了（検索フォーム拡張・htmx 部分更新実装済み） | 達成 |
| US11 経路情報を予約に紐付ける | 完了 | 完了（`CargoItinerary`・`Leg` 値オブジェクト・V9 マイグレーション実装済み） | 達成 |
| コードレビュー | 実施 | `developing-review` 実施済み（高 9 件・中 7 件・低 4 件） | 実施済み |

### ベロシティ

| 項目 | 値 |
|------|-----|
| 計画ベロシティ | 10 SP/イテレーション |
| IT5 実績ベロシティ | 10 SP |
| 累計実績ベロシティ | 48 SP（IT1: 10 + IT2: 10 + IT3: 10 + IT4: 8 + IT5: 10） |
| 平均ベロシティ（IT1-5） | 9.6 SP/イテレーション |

---

## KPT 分析

### Keep（継続すべきこと）

#### 技術的成功事項

- **IT4 申し送りの即時対応**: IT4 で 4 イテレーション連続未確認となっていた SonarQube スキャンを IT5 冒頭で必ず実施するルールを守り、Quality Gate PASS を達成した。申し送り事項をイテレーション計画の第 1 タスクに組み込む習慣が定着している
- **`Cargo.requireStatus()` 一元化**: `cancel()`・`confirmBooking()`・`assignToRouting()` の状態ガードを `requireStatus(BookingStatus)` に集約し、DRY 原則を実現した。将来の状態追加時の修正箇所が 1 箇所になり保守性が向上した
- **`CargoItinerary`・`Leg` 値オブジェクトの設計**: Java record の compact constructor でバリデーションを実装し、不変性を保証した。DDD の値オブジェクトパターンが `domain-model.md` に忠実に実装できた
- **PRG パターンの一貫した適用**: `POST /bookings/{bookingId}/route` → `redirect:/bookings/{bookingId}` の PRG（Post-Redirect-Get）パターンを適用し、ブラウザのリロードによる二重送信を防止した。全フォーム送信エンドポイントでパターンが統一されている
- **htmx 部分更新の実装**: `GET /bookings/{bookingId}/route/detail` エンドポイントと `hx-get` 属性を組み合わせ、航路ラジオ選択時の詳細パネルをページリロードなしで更新できた。ユーザー体験が向上した
- **Playwright Page Object パターンの活用**: `BookingRoutePage` クラスを追加し、経路割り当て画面の操作を抽象化した。E2E テストの可読性・保守性が維持されている
- **Strict mode violation の即時診断**: `page.locator('p.text-secondary')` が複数要素にマッチするエラーを `main .d-flex p.text-secondary` に絞り込むことで即時解決した

#### プロセス的成功事項

- **AI ペアプログラミングによる加速継続**: IT5 の全ストーリー（US09・US10・US11）を計画 2 週間に対し当日完了。5 イテレーション連続で計画期間より大幅に短い実績を達成している
- **`/compact` によるコンテキスト管理**: セッションの文脈量が限界に近づく前に `/compact` を実施し、作業継続性を維持した
- **`developing-review` による多角的レビュー**: 実装完了後に 5 エージェント並列レビューを実施し、高優先度 9 件の改善提案を IT6 の申し送り事項として整理した。コードレビューが品質保証プロセスとして確立されている

### Problem（問題点）

#### 品質関連

- **ブランチカバレッジが目標未達（75%）**: 命令カバレッジ 88% に対しブランチカバレッジ 75% と目標の 80% を下回った。特に `CargoItinerary`・`Leg` の異常系（null チェック・不正値）のブランチが未テスト
- **受入条件の部分未達**: IT5 レビュー（`IT5_review_20260409.md`）の xp-user-representative より、以下の受入基準が未実装と指摘：
  - US09-AC1: 費用情報が経路一覧に表示されていない
  - US11-AC1: 予約詳細画面に割り当て済み経路情報が表示されていない

#### アーキテクチャ関連

- **`assignItinerary` の EnumSet パターン不一致**: `cancel()` は `CANCELLABLE_STATUSES` EnumSet を使用しているが、`assignItinerary()` はインライン条件で状態を検査しており、`requireStatus()` パターンと不整合が残った
- **`assignItinerary` でドメインイベントが未発行**: `confirmBooking()`・`cancelBooking()` は `BookingConfirmedEvent`・`BookingCancelledEvent` を発行しているが、`assignItinerary()` ではイベントが発行されていない
- **Routing コンテキストへの ACL（Anti-Corruption Layer）不在**: `BookingThymeleafController` が `VoyageQueryService`・`Voyage` に直接依存しており、Shipper コンテキストで確立した `ShipperExistenceChecker` ACL パターンが適用されていない

#### テスト関連

- **`BookingThymeleafControllerTest` のセットアップ重複**: `given(voyageQueryService.findVoyages(...)).willReturn(...)` 等のセットアップコードが 10 箇所以上重複しており、テストの保守性が低下している
- **E2E 異常系テストが未追加**: 航路なし・バリデーションエラー・権限エラー等の異常系シナリオが E2E では未カバー

### Try（次に試すこと）

| # | 改善アクション | 対象 | 期限 | 期待効果 |
|---|---------------|------|------|----------|
| T1 | `assignItinerary` に `requireStatus(EnumSet.of(...))` パターンを適用し、状態ガードを統一する | アーキテクチャ | IT6 Week 1 | DRY 原則の完全遵守、状態遷移の一貫性確保 |
| T2 | `assignItinerary` 完了時に `CargoRoutedEvent` を発行してイベント駆動の一貫性を確保する | ドメイン設計 | IT6 Week 1 | イベントソーシングの整合性確保 |
| T3 | `assignRoute` コントローラメソッドを `executeBookingCommand` パターンに統合する | コントローラ | IT6 Week 1 | 例外処理の一元化・コードの DRY 化 |
| T4 | US09-AC1 費用情報を `VoyageQueryService` から取得して `route.html` に表示する | 受入条件達成 | IT6 Week 1 | ユーザー代表視点での受入条件充足 |
| T5 | US11-AC1 予約詳細画面（`show.html`）に割り当て済み経路情報セクションを追加する | 受入条件達成 | IT6 Week 1 | 受入条件の完全充足 |
| T6 | `BookingThymeleafControllerTest` のセットアップを `@BeforeEach` に集約する | テスト品質 | IT6 Week 1 | テストの保守性向上 |
| T7 | `route.html` に `alert-success`・`alert-danger` フィードバックメッセージ表示領域を追加する | UI 品質 | IT6 Week 1 | ユーザーへの操作フィードバック改善 |
| T8 | Routing コンテキストへのアクセスに ACL を設計・導入する（IT7 以降） | アーキテクチャ | IT7 | コンテキスト間の依存方向の整理 |

---

## IT6 への申し送り事項

### 高優先度（IT6 で必ず対応）

| # | 内容 | 出典 |
|---|------|------|
| 1 | US09-AC1: 費用情報を経路一覧に表示する | IT5 レビュー H-8 |
| 2 | US11-AC1: 予約詳細画面に割り当て済み経路情報を表示する | IT5 レビュー H-9 |
| 3 | `assignItinerary` に `requireStatus(EnumSet)` パターン適用 | IT5 レビュー H-1 |
| 4 | `assignItinerary` でドメインイベント（`CargoRoutedEvent`）発行 | IT5 レビュー H-2 |
| 5 | `assignRoute` を `executeBookingCommand` パターンに統合 | IT5 レビュー H-3 |
| 6 | `routeDetail` の未使用 `bookingId` パスパラメータを削除 | IT5 レビュー H-5 |
| 7 | 統合テスト（`BookingThymeleafControllerTest`）のセットアップ重複解消 | IT5 レビュー H-6 |
| 8 | `route.html` にフィードバックメッセージ表示領域を追加 | IT5 レビュー H-7 |

### 中優先度（対応推奨）

| # | 内容 | 出典 |
|---|------|------|
| 9 | 統合テストと E2E テストの責務分離（重複検証の整理） | IT5 レビュー M-1 |
| 10 | htmx フラグメントを `th:fragment` で実装（`display:none` 廃止） | IT5 レビュー M-2 |
| 11 | `Leg` compact constructor に時刻整合性バリデーション追加 | IT5 レビュー M-4 |
| 12 | `assign-to-routing` ステップに `status().is3xxRedirection()` 検証追加 | IT5 レビュー M-5 |
| 13 | E2E テストの異常系シナリオ追加（候補なし・バリデーションエラー） | IT4 申し送り持ち越し |

### 低優先度（改善の余地あり）

| # | 内容 | 出典 |
|---|------|------|
| 14 | `RouteCargoCommand` の `String bookingId` を `BookingId` 型に変更 | IT5 レビュー L-1 |
| 15 | `VoyageQueryService.findByVoyageNumber()` の戻り値を `Optional<Voyage>` に変更 | IT5 レビュー L-2 |
| 16 | テスト命名規則の統一（日本語か英語かに統一） | IT3 申し送り持ち越し |
| 17 | Routing コンテキストへの ACL 導入設計（実装は IT7 以降） | IT5 レビュー H-4（保留） |

---

## 次のイテレーション計画への反映

### IT6 のスコープ（案）

IT5 レビュー高優先度指摘対応（リファクタリング）と Phase 3 の精算機能を対象とする。

| ID | ユーザーストーリー / テーマ | SP |
|----|--------------------------|----|
| IT5-改善 | IT5 レビュー高優先度対応（受入条件充足・ドメインイベント・パターン統一） | 3 |
| US22 | 法人割引を適用する | 3 |
| US23 | 精算を処理する | 4 |
| **合計** | | **10** |

### ベロシティ調整

- IT1-5 の実績ベロシティ平均: 9.6 SP
- IT6 は 10 SP を目標に維持する

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-04-09 | 初版作成 | - |
