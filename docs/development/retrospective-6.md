---
title: イテレーション 6 ふりかえり
date: 2026-06-23
---

# イテレーション 6 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| 期間 | 2026-08-31 〜 2026-09-13（計画）/ 1 日（AI ペアプロ実績） |
| ゴール | US16 引取作業 + US17 状態手動更新 + US21 輸送料金算出 (12 SP) を完成、Billing Context を新設、IT5 セルフレビュー高優先度 7 件 (H1-H7) + 中観察 3 件 (O1-O3) のうち 7 件解消、Release 1.0 MVP ゲートに到達 |
| 計画 SP | 12 (US16: 3 + US17: 3 + US21: 6) |
| 実績 SP | 12 (100% 主要部完了) |

## 達成事項

- **IT5 申し送り 7/10 解消**: H1 appendEvent 戻り値を `TrackingActivity` 化 (新 version 付き) / H2 `BookingTrackingNumber` opaque type を Booking Context に新設し `issueTracking` を型安全化 / H4 `TrackingActivitySpec` に `OutOfOrder` + 同時刻イベントテスト追加 / H5 `ScalikeJdbcTrackingActivityRepositoryIntegrationSpec` (Testcontainers) で楽観ロック衝突を検証 / H7 `TrackingActivity` 不変条件 3 として `require(transportStatus == deriveStatus(events))` を追加 / O1 公開ページ用 `layout/public.scala.html` 切り出し / O2 tracking_number 採番を `MAX(id)+1` → `nextval('tracking_activity_id_seq')` に変更 (ADR 0013 追加)
- **US16 (3 SP)**: Flyway V15 (`handling_activity.recipient_confirmation`)、`HandlingActivity` に `recipientConfirmation: Option[String]` フィールド + `Claim` 時必須化、`Cargo.deliver()` ドメインメソッド (TrackingIssued/InTransit → Delivered)、`BookingStatus.canTransitionTo` 拡張、荷役登録 UI に Customs/Claim ラジオ + JS で `recipientConfirmation` 条件付き表示、`HandlingController` の Claim 時に `BookingCommandService.completeDelivery` を直接連結 (Orchestrator 0.3 未着手のため Controller 一時連結)、`NotificationType.DeliveryCompleted` + `NotificationPayload` + JSON シリアライザ拡張、Flyway V16 (notification_log CHECK 制約拡張)、E2E 2 件 (Claim 成功 + Delivered 確認 / 荷受人確認なしエラー)
- **US17 (3 SP)**: `UpdateTrackingStatusCommand` + `TrackingCommandService.updateStatus` (status→eventType マッピング + 楽観ロック対応)、`TrackingActivity.recordManualUpdate` 相当機能を既存 `addEvent + appendEvent` で実現、追跡詳細画面に Bootstrap モーダル (状態セレクト / 港湾 / 日時) + POST `/tracking/:trackingNumber/update-status` 追加、`NotificationType.ManualStatusUpdated` + `NotificationPayload` + JSON 拡張、`BookingCommandService.logManualStatusUpdate` で通知ログ連携、E2E 1 件 (Loaded への手動更新)
- **US21 (6 SP)**: **Billing Context 新設** — `Invoice` 集約 + opaque type 群 (`InvoiceId` `INV-NNNNNN` / `BillingBookingId` / `BillingShipperId(isCorporate)` / `DiscountRate(0.0000〜0.3000)` / `Money` (Long, 円)) + enum (`PaymentStatus` / `DiscountPolicyType`) + `InvoiceRepository` ポート、Flyway V17 (`invoice` + `invoice_line_item` + `payment` + `cargo.invoice_id` + `invoice_id_seq`)、`ScalikeJdbcInvoiceRepository` (nextval 採番 + 楽観ロック付き save)、`PricingService.calculateActual` (現状は `estimateCost` 委譲)、`BillingCommandService.generate` (Delivered 必須 / Pending 発行 / 冪等)、請求書 UI (`/billing/invoices` 一覧 + `/billing/invoices/new` 発行 + `/billing/invoices/:invoiceId` 詳細)、ダッシュボード「請求管理」カードに差し替え、E2E 2 件 (発行成功 / Delivered 必須エラー)、`Module.scala` に DI バインディング追加 (E2E 実行時に Guice MissingImplementation 発見・修正)

### 品質メトリクス

| 指標 | 結果 |
|------|------|
| Unit テスト | 261 件 / 全件成功 |
| Playwright E2E | 36 / 36 PASS (1.3 分、IT6 5 件含む) |
| Testcontainers IT | 2 件 (TrackingActivity 楽観ロック、Docker 起動時のみ) |
| ArchUnit | 5/5 緑 (ただし新コンテキスト billing/handling/tracking/notification は検査対象外 — IT7 で拡張必須) |
| マイグレーション | V1-V17 適用済 |
| scalafmt / scalafix / CI | ✅ |
| 新コンテキスト | Billing (Invoice 集約 + 4 VO + 2 enum + repository) |
| 新 ADR | ADR 0013 (tracking_number_seq シーケンス採番、ADR 0010 更新) |
| developing-review | 正式実施済 ([it6_implementation_review_20260623.md](../review/it6_implementation_review_20260623.md)) — 高 8 件 / 中 12 件 / 低 8 件 |

## KPT

### Keep（継続したいこと）

- **TDD 規律の維持**: Red-Green-Refactor のサイクルで集約・コマンドサービス・コントローラを実装。`InvoiceSpec` で割引境界 (0/10% /範囲外/負数)、`BookingCommandServiceSpec` で `completeDelivery` の冪等性と通知ログを先に固定化してから実装に進んだ
- **opaque type による型分離パターンの継承**: Billing Context の `InvoiceId` / `BillingBookingId` / `BillingShipperId` / `DiscountRate` / `Money` を opaque type で表現、IT5 で確立した「コンテキスト境界を型レベルで強制」を踏襲
- **冪等性ファースト**: `Cargo.deliver` (Delivered/Settled で即 Right)、`BillingCommandService.generate` (既存 Invoice を返す)、`TrackingCommandService.assign` 同様、再実行可能性を仕様としてテストに固定化
- **シーケンス採番 + ADR 一体記録**: nextval 化 (O2) と ADR 0013 を同セッションで作成し、判断理由 / 代替案 / 帰結を残した
- **`require` による不変条件のコンストラクタ集約**: `TrackingActivity` で `transportStatus == deriveStatus(events)` を `require` 化し、永続化値が壊れていれば fail-fast。reconstruct も同経路を通す互換性確保
- **マルチパースペクティブレビュー 2 段階運用**: Ralph Loop 中の self-review に加え、developing-review (XP 5 エージェント並列) を staging 完了後に正式実施し高 8 件 / 中 12 件 / 低 8 件を可視化
- **E2E 駆動での DI バグ発見**: Playwright で `/health` 起動エラーから `InvoiceRepository` バインディング欠落を即発見。ユニットテストでは検出不能だった統合層のバグを E2E が捕捉した

### Problem（問題だったこと）

- **ArchUnit `contexts` が新コンテキストを未カバー**: `HexagonalArchitectureSpec.scala:54` の `contexts` は `auth/booking/estimation/routing/shipper` 5 件のみで、`billing/handling/tracking/notification` の境界違反 (Billing → Booking 直接結合等) を検出できない (architect 高優先指摘 H1)
- **Billing → Booking の domain 直接結合**: `BillingCommandService` が `CargoRepository` / `BookingId` / `BookingStatus` を直接 import。ACL Port (`BillingCargoQueryPort`) を経由していない (H2)
- **HandlingController での集約間オーケストレーション**: 0.3 `BookingHandlingOrchestrator` 未着手のため、Claim → `bookingCommandService.completeDelivery` を Controller で順次呼び出し。各 service が独立 `DB.localTx` を張るため、Claim 登録成功 + completeDelivery 失敗で「配送完了通知だけ残り status は InTransit」の不整合リスク (H3)
- **Money 型の二重定義**: `shared.domain.Money` (currency + amount) と `billing.Money` (opaque Long 円) が共存。`BillingMoney.unsafeFrom(base.amount)` で通貨情報を黙って捨てており USD 入力で誤請求リスク (H4)
- **PricingService.calculateActual の素通し実装**: 「輸送実績ベース」を謳いつつ `estimateCost` に委譲しているだけ。荷役回数加算・例外加算なし。`invoice_line_item` テーブルは作成済だが未活用で「動作するきれいなゴミ」の予兆 (M8)
- **業務適合性の不足**: 請求書発行で「法人フラグ手入力」は荷主登録時に確定済の属性を毎回問うことになり月末ヒューマンエラー必発 (H5)。料金内訳非表示は問い合わせ対応不可 (H6)。荷受人確認 1 フィールドは紛争時証跡として弱い (M6)。手動更新理由欄不在は内部統制 NG (M7)
- **未消化 IT5 申し送り 3 件**: 0.2 H6 CargoSnapshot ACL / 0.3 H3 Orchestrator / 0.10 O3 Itinerary leg。アーキ高優先指摘 (H1/H2/H3) と同根
- **TrackingCommandService.updateStatus の OptimisticLockException を throw のまま UI に伝播**: 競合時に「再読込してください」を表示できず復旧操作不能 (H8)
- **ユビキタス言語の表記揺れ**: `DeliveryCompleted` (ドメイン) / 「配送完了通知」/ 「引取作業」が混在 (M10)
- **Scala 3 opaque type erasure による命名妥協**: `issueTracking(BookingTrackingNumber)` と `issueTrackingByRaw(String)` の二重オーバーロード回避策。ドメインに I/O 由来語彙が漏れる (M1)
- **iteration_plan-6.md 冒頭ゴール / 末尾完了条件のチェックボックス追従漏れ**: 詳細タスク表は更新したが、サマリーセクションは `[ ]` のまま残り読者誤解の余地 (M12)

### Try（次に試したいこと）

| # | アクション | 責任者 | 期限 | 期待効果 |
|---|-----------|--------|------|---------|
| T1 | **アーキ堅牢化バンドル** (H1+H2+H3+0.2+0.3): ArchUnit `contexts` 拡張 → 違反可視化 → `BillingCargoQueryPort` + `HandlingOrchestrator` + `CargoSnapshot` ACL 抽出 | IT7 冒頭 | Day 1-2 | 隠れた依存違反を可視化、単一 `DB.localTx` 境界化で部分失敗時の不整合を防止 |
| T2 | **業務適合性修正バンドル** (H5+H6+M6+M7+M10): 法人フラグ自動判定 / 料金内訳表示 (`invoice_line_item` 活用) / 荷受人確認種別 + 値 / 手動更新理由 + Tracker ロール限定 / ユビキタス言語統一 | IT7 | Day 3-5 | 月末オペレーション破綻リスク解消、内部統制対応、紛争時証跡強化 |
| T3 | **Money 統一 ADR 0014** (H4): 単通貨 (JPY) 確定の ADR を起票し `shared.domain.Money` 一本化 + extension に `multiplyByRate` 移植 | IT7 冒頭 | Day 1 | 通貨情報の暗黙喪失リスク解消、DRY 達成 |
| T4 | **テスト補強** (H7+M3+M4+M5+M9): `PricingService` 失敗系 / `updateStatus` OutOfOrder 衝突 / `recipientConfirmation` 空文字境界 / `DiscountRate` 100%/99.99% 境界 / `Invoice` 楽観ロック IT | IT7 | 随時 | 「お金」と「並行性」のエッジ網羅 |
| T5 | **`updateStatus` OptimisticLockException の Either 化** (H8): 例外を UI フレンドリーメッセージに変換 | IT7 | Day 2 | ユーザーが復旧操作を実行可能になる |
| T6 | **ADR 0014 で `*ByRaw` 命名問題に決着** (M1): smart constructor を Application 層に寄せ domain 公開 API を opaque type 一本化 | IT7 | Day 1 | ドメインから I/O 由来語彙を排除 |
| T7 | **ドキュメント追従の自動化検討** (M11+M12+L1-L7): iteration_plan のチェックボックス更新を tracking-progress スキルで強制、`mkdocs.yml` 重複登録チェック、Scaladoc 補足 | IT7 | 随時 | 進捗ドキュメントの読者誤解防止 |
| T8 | **`PricingService.calculateActual` の本実装** (M8): `invoice_line_item` を経路 / 重量 / 貨物種別 / 荷役回数の内訳付きで生成 | IT8 (US22 と同時) | - | US21 の業務価値顕在化、US22 法人割引適用との地続き |

### IT7 への申し送り (developing-review 高優先まとめ)

- H1 ArchUnit 拡張 — billing/handling/tracking/notification を contexts に追加
- H2 Billing → Booking 直結を `BillingCargoQueryPort` で ACL 化
- H3 `HandlingController` → `HandlingOrchestrator` 抽出 + 単一 `DB.localTx`
- H4 Money 二重定義解消 (ADR 0014)
- H5 請求書発行で法人フラグ手入力廃止
- H6 請求書詳細に料金内訳表示
- H7 PricingService 失敗系テスト
- H8 `updateStatus` OptimisticLock の Either 化
- IT5 未消化申し送り: 0.2 / 0.3 / 0.10
- US21 3.5 法人割引自動取得 (IT6 で未着手)

## 学んだこと

- **DI バインディングは Unit テストでは検出されない**: `ScalikeJdbcInvoiceRepository` を実装しても `Module.scala` への登録漏れは Unit テスト 261 件全 PASS でも検出されず、E2E 起動時の Guice エラーで初めて発覚した。**「層を跨ぐ統合」は E2E でしか保証できない** という教訓
- **opaque type は erasure で同名異シグネチャのメソッドを共存できない**: `Cargo.issueTracking(BookingTrackingNumber)` と `issueTracking(String)` の overload は erasure で衝突。`*ByRaw` 命名は妥協、Application 層に smart constructor を寄せる方が境界明確化と DRY を両立する
- **「動作するきれいなゴミ」予兆の検出**: `invoice_line_item` テーブルを作りながら `PricingService.calculateActual` が `estimateCost` への素通しで明細を生成しないのは、業務価値が薄いまま器だけ作る兆候。次イテレーションでの本実装を計画に組み込む必要
- **マルチパースペクティブレビューの 2 段階運用が機能した**: Ralph Loop 中の self-review で局所改善 (require 追加、ADR 起票) を回し、staging 完了後に正式 developing-review で構造課題 (ArchUnit カバレッジ / Money 二重 / ACL 不在) を浮上させる役割分担が確立

## 関連ドキュメント

- [イテレーション 6 計画](./iteration_plan-6.md)
- [IT6 実装レビュー (developing-review)](../review/it6_implementation_review_20260623.md)
- [ADR 0013 tracking_number シーケンス採番](../adr/0013-tracking-number-sequence-numbering.md)
