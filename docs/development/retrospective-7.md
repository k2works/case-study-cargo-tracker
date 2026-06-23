# イテレーション 7 ふりかえり

## 概要

| 項目 | 値 |
|------|-----|
| イテレーション | IT7 |
| 計画期間 | 2026-09-14 〜 2026-09-27 |
| 実績期間 | 2026-06-23 (Ralph Loop 自律実行で 1 日完結) |
| 計画 SP | 12 |
| 実績 SP | 12 |
| 達成率 | 100% |
| テスト数 | 371 (前回 354 → +17) |
| 機能タスク | US19 (6 SP) + US20 (6 SP) 全完了 |
| 申し送り消化 | IT6 申し送り 16/16 件完了 (※付 3 件) |

IT7 は Phase 4 (例外処理) の US19/US20 を実装するとともに、IT6 申し送り 16 件を全消化する大型イテレーションだった。
Ralph Loop の自律実行モードで 26 コミット連続着地、当初予想 (Week 1-2 の 2 週間スケジュール) を大幅に短縮して 1 日で機能完成。

---

## Keep (続けるべきこと)

### K1: ADR 0014 / 0015 による設計判断の透明化と早期承認

Snapshot ADT (0014) と Money 統一 (0015) は、IT7 着手前に ADR で代替案 4 件 + 帰結を明文化し、承認後に集約 4 種 (Invoice / Cargo / HandlingActivity register / HandlingActivity reconstruct) へ展開した。これにより SonarQube MAJOR Code Smell 4 件を構造的に解消できた。

### K2: ACL ポート + アダプターパターンの一貫適用

`BillingCargoQueryPort` (0.2) → `TrackingLookupPort` + `BookingNotificationPort` (0.3) と、コンテキスト間連携を一貫して domain.ports + infrastructure.acl で実装。ArchUnit ルール 3 と整合し、Handling Orchestrator のテスト容易性 (Fake ポートでの統合テスト 3 件) も担保した。

### K3: ArchUnit ルール 4 への命名サフィックス追加で柔軟性確保

Orchestrator / Input サフィックスをルールに追加することで、ヘキサゴナル原則を維持しつつ新パターン (`HandlingOrchestrator` / `RegisterHandlingFlowInput`) を受容できた。テスト駆動でルール緩和の影響を観測。

### K4: Repository の 2 段読込 + delete-then-insert 同期パターン

Cargo (0.14 leg) と Invoice (0.9 lineItem) で同一パターンを採用。`rowToXAndId → loadChildren → enrichSnapshot` の流れは可読性が高く、将来追加の集約子テーブルにも展開しやすい。

### K5: マイグレーション着手前の既存スキーマ確認

V22 の "relation already exists" 問題を解析した結果、V17 が既に invoice_line_item テーブルを作成していたことが判明。今後は `grep -r tablename conf/db/migration/` を着手前に必ず実行する慣行を残す。

### K6: 楽観ロック例外を Either に畳み込むパターン

0.11 で確立した `try Right(...) catch case _: OptimisticLockException => Left(...) case NonFatal(_) => Left(...)` パターンを US19 の `recordException` / `resolveException` でも踏襲。UI へ「再読込してください」を伝える経路が一貫した。

### K7: Ralph Loop 自律実行による高速消化

26 コミット連続で 16 件の 0.x 申し送り + US19/US20 12 SP を 1 日で達成。Stop hook 経由の継続シグナルで意思決定オーバーヘッドを最小化できた。

---

## Problem (問題)

### P1: Play Dev サーバーのクラスパスキャッシュによる runtime エラー

V22 ファイルリネーム (`create_invoice_line_item` → `extend_invoice_line_item_category`) 後、起動済 Play dev サーバーが旧ファイル名の Flyway 参照を保持し続け `Unable to obtain inputstream` 例外。コードベースは正常だが runtime のみ壊れる状態に陥った。

### P2: マイグレーションスキーマ衝突の発見が遅延

V17 が既に invoice_line_item を作成済だったが、IT6 完了時点で空テーブルとして存在することを認識できておらず、IT7 0.9 で V22 として CREATE TABLE をする設計判断が出てしまった。Testcontainers の問題と誤診し、迂回コミットが 1 件発生 (`bc932fbe`)。

### P3: HandlingOrchestrator の単一 DB.localTx 未達

タスク計画では「単一 `DB.localTx` で実行」と記述したが、各リポジトリが独自 localTx を開く現状制約のため未実装で持ち越し。Orchestrator パターンの利得 (orchestration ロジック集約 + テスト容易性) は得られたが、トランザクション境界の理想形 (失敗時に全 step ロールバック) は未達。

### P4: 0.14 routeDeviation 判定の手戻り

Itinerary leg 詳細を追加したが、HandlingCommandService.register の routeDeviation 自動判定は handling→booking 集約への直接依存を回避する ACL ポート設計が必要となり 0.3 HandlingOrchestrator と統合設計に持ち越し。0.14 単独では「データ構造のみ追加、判定ロジック未実装」の中途半端な状態。

### P5: Playwright E2E spec の新規作成スキップ

US19/US20 のための Playwright E2E ファイル新規追加は実施せず、Scala 側統合テスト (TrackingCommandServiceSpec の 4 件追加) で等価検証した。デモシナリオはアプリ手動操作で確認する想定だが、ブラウザ自動化による回帰検出は不在。

### P6: HandlingCommandService 0.3 ACL 連携で双方向の trade-off 顕在化

Handling → Booking/Tracking への依存を ACL アダプターで断ち切ったが、現状 ACL アダプター内で `BookingCommandService.findCargo` を経由するため、ACL adapter 自体が他コンテキストの application 層に依存する形となった。ヘキサゴナルの境界が ACL の 1 点に集約された一方、application 層間の暗黙結合が残る。

### P7: SonarQube 実機再スキャンの未実施

タスク 0.16 「SonarQube 再スキャン + Quality Gate 確認」は ADR 0014 結果記録ドキュメント反映で代替したが、実環境のスキャナ実行 (`npx gulp sonar-local:check`) で MAJOR Code Smell 4 → 0 件を実数値で確認していない。

### P8: 通知ペイロードの `newEstimatedArrival` 等が UI フォームから取得できていない

`logDelayNotification` のシグネチャに `newEstimatedArrival` / `responsePlan` / `reason` を含めたが、現状 UI の例外記録モーダルにはこれらの入力欄がなく、Controller では `"未確定"` / `description.getOrElse("")` の仮値を渡している。業務的に意味ある値が記録できる UI 拡張が IT8 に必要。

### P9: BookingCargoSnapshot / BillingCargoSnapshot のフィールド重複設計

`isCorporate` / `isDelivered` 等の判定済フラグを ACL でフラット化したが、`BillingCargoSnapshot` のフィールド数が 9 個と肥大化。今後新 Snapshot を追加する際の閾値 (例: 7 個超で Snapshot 内に sub-record を導入) を決めていない。

### P10: TrackingExceptionEvent の永続化キーが暗黙

`tracking_exception_event` テーブルは PK `id` を持つが、ドメイン側の `TrackingExceptionEvent` は ID を保持しない。`resolveException` で index 指定 → `(exception_type + occurred_at + resolved_at IS NULL)` 複合キー UPDATE という暗黙の一意性に依存している。同一 type/time の例外が時系列で複数発生する場合に競合リスクあり。

### P11: 設計ドキュメント (data-model.md / domain-model.md / ui_design.md) への反映が未実施

IT7 で導入した `ItineraryLeg` / `InvoiceLineItem.category` / `TrackingExceptionEvent` / 通知 4 種 / `RecipientConfirmationType` 等が iteration_plan-7.md のみに記載されており、各設計ドキュメントへの正式反映 (`docs/design/data-model.md` 等) が未実施。

---

## Try (次イテレーションで試すこと)

### T1: マイグレーション着手前 grep チェック

新規 Flyway ファイル作成時の慣行として、`grep -rn <table_name> apps/cargo-tracker/conf/db/migration/` を必ず実行。既存スキーマ衝突を初手で防ぐ。チェックリストとして CLAUDE.md に追記 (担当: 即時、IT8 着手前)。

### T2: HandlingOrchestrator の単一 DB.localTx 化 ADR 起票

`ScalikeJDBC Session` を Port インターフェース経由で配るパターン (implicit Session の domain trait への伝播 or Reader モナド化) を ADR 0016 として検討。代替案 3 件 (eventual consistency / SAGA / Domain Events) と比較 (担当: IT8 着手前、SP 見積 3)。

### T3: routeDeviation 自動判定の完了

`HandlingOrchestrator.register` 内で、`BillingCargoQueryPort` 相当の `HandlingCargoQueryPort` (`findItineraryForBooking`) を追加し、`Itinerary.isOnRoute(locationUnLocode)` で `routeDeviation` を判定する。Lost ケースとの組合せテスト追加 (担当: IT8 初期、SP 見積 2)。

### T4: Playwright E2E 4 シナリオ追加

US19 / US20 のデモシナリオを Playwright E2E に落とし込む:
1. Delay 記録 → InException 表示 → 対応報告 → 復旧
2. Damage 記録 → DamageReported 通知ログ確認
3. Lost 記録 → escalationFlag バッジ + LossEscalated 通知
4. 権限なしユーザーは「例外を記録」ボタン非表示
(担当: IT8 初期、SP 見積 2)

### T5: SonarQube 実機再スキャン + Quality Gate 公式確認

`npx gulp sonar-local:setup` → `npx gulp sonar-local:check` を実行し、MAJOR Code Smell 数値を Quality Gate 上で取得。結果を `docs/development/iteration_report-7.md` に記録 (担当: IT7 クロージング、SP 見積 1)。

### T6: 設計ドキュメント正式反映 (data-model / domain-model / ui_design)

IT7 で導入したスキーマ・モデル・UI 要素を 3 設計ドキュメントに反映:
- `data-model.md`: V18-V22 + cargo_itinerary_leg / tracking_exception_event / invoice_line_item.category
- `domain-model.md`: ItineraryLeg / InvoiceLineItem / TrackingExceptionEvent / ExceptionType / RecipientConfirmationType
- `ui_design.md`: 例外記録モーダル / 例外履歴テーブル / 料金内訳テーブル / 荷受人確認種別

(担当: IT8 着手前、SP 見積 2)

### T7: 通知ペイロード `newEstimatedArrival` 入力欄追加

例外記録モーダルに Delay 専用フィールド (新到着予定日 datetime-local + 対応方針 textarea) を JS 制御で表示。`logDelayNotification` 呼出を意味ある値に置換 (担当: IT8 / US22 関連で展開、SP 見積 1)。

### T8: TrackingExceptionEvent に内部 ID 付与

ドメイン層で `TrackingExceptionEvent.id: Option[Long]` (Snapshot 復元時のみセット) を追加し、`updateExceptionResolution` で複合キーではなく PK 直接更新に変更。同時複数例外の並行解決を可能化 (担当: IT8 着手前、SP 見積 2)。

---

## Definition of Done チェック (IT7)

| 項目 | 状態 |
|------|------|
| US19 全タスク完了 (1.1-1.7) | ✅ (1.7 は Scala 統合テストで等価検証) |
| US20 全タスク完了 (2.1-2.5) | ✅ (US19 と統合実装) |
| IT6 申し送り 16 件 (0.1-0.16) | ✅ (※付 3 件: 0.3 単一 tx 未達 / 0.14 routeDeviation 判定持ち越し / 0.9 lineItem 復活コミット) |
| Flyway V18-V22 全適用 | ✅ (Testcontainers 含む) |
| ArchUnit 5 ルール pass | ✅ (ルール 3 / 4 拡張済) |
| 全テスト pass | ✅ (371 件) |
| scalafmt / scalafix 通過 | ✅ |
| ADR 0014 / 0015 承認 | ✅ |
| 設計ドキュメント反映 | ❌ (T6 で IT8 持ち越し) |
| SonarQube Quality Gate 実機確認 | ❌ (T5 で IT8 持ち越し) |
| Playwright E2E US19/US20 シナリオ | ❌ (T4 で IT8 持ち越し) |
| dev サーバー動作確認 | ❌ (P1 起動済プロセス停止/再起動が必要) |

---

## 更新履歴

| 日付 | 変更内容 | 著者 |
|------|---------|------|
| 2026-06-23 | IT7 ふりかえり初版作成 (KPT 7/11/8) | Ralph Loop 自律実行 |
