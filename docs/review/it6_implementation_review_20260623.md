---
title: IT6 実装レビュー (US16 + US17 + US21 + IT5 申し送り 7/10)
date: 2026-06-23
reviewers: xp-programmer / xp-tester / xp-architect / xp-technical-writer / xp-user-representative
scope: 50a04ae1..HEAD (24 commits, 57 files, +2344/-120)
---

# IT6 実装レビュー (developing-review 正式 / マルチパースペクティブ)

## レビュー対象

- ブランチ: `scala/take-1`
- スコープ: IT6 機能 12 SP (US16/US17/US21) + IT5 申し送り 7/10
- 範囲: コミット `50a04ae1` 〜 `3afd4446` (24 commits, 57 files)
- ローカル: 261 unit tests succeeded / 0 failed (Docker 必要な IT/E2E は abort)

## 総合評価

**機能としての MVP は達成、テスト規律・冪等性・命名は健全。** 一方で **新規 Billing Context のコンテキスト境界が ArchUnit 未カバー**、**Money 型の二重定義**、**HandlingController での Orchestrator 不在のための一時連結**、**請求書 UI の業務適合性 (法人フラグ手入力 / 料金内訳非表示) ** が複数視点から高優先で指摘された。Release 1.0 MVP を確保しつつ、**IT7 冒頭で ArchUnit 拡張 + Orchestrator 化 + 業務 UX 修正** を必須とする状態。

## 改善提案 (重要度順)

### 高 (マージ前 or IT7 冒頭で対応)

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | ArchUnit `contexts` に `billing/handling/tracking/notification` を追加し境界違反を可視化 | `test/cargotracker/arch/HexagonalArchitectureSpec.scala:54` | architect | 新規コンテキストが境界チェック対象外 — 隠れた依存違反を黙認している |
| H2 | Billing → Booking の domain 直接結合を ACL Port (`BillingCargoQueryPort`) で分離 | `billing/application/commandservices/BillingCommandService.scala` | architect | `CargoRepository` 直接 import は ADR 趣旨違反。ArchUnit 拡張で必ず違反検出 |
| H3 | `HandlingController` の Claim 時 `completeDelivery` 直接呼出を `HandlingOrchestrator` (Application Service) へ抽出し単一 `DB.localTx` 化 | `handling/interfaces/web/HandlingController.scala:115-125` | programmer / architect | 部分失敗時の不整合リスク (配送完了通知だけ残り status は InTransit)。SRP 違反 |
| H4 | `Money` の二重定義を解消 — `shared.domain.Money` を正とし Billing は `Money.jpy` を利用、`multiplyByRate` を extension で shared に移植 (または ADR 0014 で別物として正当化) | `billing/.../Money.scala` vs `shared/domain/Money.scala` | programmer / architect | DRY 違反 + `BillingMoney.unsafeFrom(base.amount)` で通貨情報を黙って捨てている (USD 入力で誤請求リスク) |
| H5 | US21 請求書発行で「法人フラグ手入力」を廃止し、Booking 経由で荷主属性から自動判定 | `views/billing/newForm.scala.html` | user-rep | 法人区分は荷主登録時点で確定済 — 月末ヒューマンエラー必発、US21 受入条件 (荷主種別割引) にも反する |
| H6 | US21 請求書詳細に料金内訳 (距離 / 重量 / 貨物種別) を表示 | `views/billing/detail.scala.html` | user-rep | 「なぜこの金額か」を経理担当が説明できず月末締めが滞る。`invoice_line_item` テーブルは作成済だが未活用 |
| H7 | `PricingService.calculateActual` 失敗系のテスト追加 (`InMemoryPricingService` ハッピーパスのみカバー) | `test/cargotracker/billing/application/commandservices/BillingCommandServiceSpec.scala` | tester | お金が絡む処理で副作用 (save) が発生しないことの保証が欠落 |
| H8 | `TrackingCommandService.updateStatus` の `OptimisticLockException` を `Either` に畳み込みユーザー向けメッセージを生成 (現状 throw のまま UI に伝播) | `tracking/application/commandservices/TrackingCommandService.scala` | tech-writer | 競合時に「他のユーザーが更新したため再読込してください」が表示されず復旧操作不能 |

### 中 (IT7 中で対応推奨)

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| M1 | `*ByRaw` 命名を見直し、`BookingTrackingNumber.parse` を Application 層に寄せて domain 公開 API を opaque type 一本化 | `Cargo.scala:94` / `BookingCommandService.scala:158` | programmer |
| M2 | `Invoice.issue` が常に `Right` を返すデッドコード解消 (直返しに変更 or 100% 割引 / 0 円ケースのテスト追加) | `Invoice.scala:45-60` | programmer / tech-writer |
| M3 | `TrackingCommandService.updateStatus` の OutOfOrder 衝突 + 同時刻同種テスト追加 | `TrackingCommandServiceSpec.scala` | tester |
| M4 | `HandlingActivity` の `recipientConfirmation = Some("")` / 空白のみ境界テスト追加 | `HandlingActivitySpec.scala` | tester |
| M5 | `DiscountRate` 100% / 99.99% 境界テスト追加 | `InvoiceSpec.scala` | tester |
| M6 | US16 荷受人確認を「種別 (署名/受領印/身分証/コード) + 値」の 2 フィールド構成に変更 | `views/handling/newForm.scala.html` | user-rep |
| M7 | US17 状態手動更新に「更新理由」必須 + `Role.Tracker` ロール限定の UI 制御 | `views/tracking/detail.scala.html` | user-rep / tech-writer |
| M8 | `PricingService.calculateActual` を `invoice_line_item` 込みで実装 (現状は `estimateCost` 素通し、明細テーブル未活用) | `shared/domain/pricing/PricingService.scala` | architect / user-rep |
| M9 | Invoice 楽観ロック競合 IT を Testcontainers で追加 (Tracking と同パターン) | `test/cargotracker/billing/infrastructure/repositories/` | architect |
| M10 | ユビキタス言語の表記揺れ統一 (`DeliveryCompleted` ドメイン vs 「引取作業」UI vs 「配送完了通知」) | `NotificationType.scala` / `views/handling/*` | user-rep / tech-writer |
| M11 | `docs/development/index.md` の IT6 ステータスを「実装中」に更新 / `docs/design/domain-model.md` `data-model.md` に Billing 実装結果を反映 | docs | tech-writer |
| M12 | `iteration_plan-6.md` 冒頭ゴール / 末尾完了条件のチェックボックスを 7/10 申し送り完了に追従 | `docs/development/iteration_plan-6.md:32-37, 817-827` | tech-writer |

### 低 (改善の余地あり)

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| L1 | `Money.minus` (Money.scala:19) のサイレントクランプを Scaladoc 明記 or `Either` 化 | `billing/.../Money.scala:19` | programmer |
| L2 | ADR 0013 と V17 の「BIGSERIAL 暗黙利用 vs 専用シーケンス」一貫性 — ADR に補足 or V17 を BIGSERIAL に統一 | `docs/adr/0013-*.md` / `V17__create_invoice.sql` | architect |
| L3 | `[-]` マークの凡例を `iteration_plan-6.md` 冒頭に追記 (`[-] = 残作業 / E2E 等`) | `docs/development/iteration_plan-6.md` | tech-writer |
| L4 | `mkdocs.yml` の ADR 0010 重複登録の確認 | `mkdocs.yml:132` 付近 | tech-writer |
| L5 | `InException（通関等の例外）` UI 表示を「通関・検査等の例外発生」に変更 | `views/tracking/detail.scala.html` | tech-writer |
| L6 | `billing/list.scala.html` の `状態` 列ヘッダを「支払状態」に統一 | `views/billing/list.scala.html` | tech-writer |
| L7 | `Invoice` Scaladoc の「税抜」記述に「税計算は IT8 で実装」を追記 | `Invoice.scala` | tech-writer |
| L8 | 公開追跡ページに支払情報が露出しないことの staging E2E チェックリスト明記 | staging チェックリスト | tester |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| 1 | programmer: `*ByRaw` 命名は OK (検証境界が明確) | programmer: `*ByRaw` は I/O 由来語彙の domain 漏れ (改善提案 M1) | 同エージェント内で「良い点」と「改善提案」の評価が分裂 | M1 採用 — Application 層に寄せる方が境界明確化と DRY を両立 |

## 未消化 IT5 申し送り (IT7 へ)

- 0.2 H6 CargoSnapshot ACL — Billing Context の境界違反 (H2) と同根
- 0.3 H3 BookingHandlingOrchestrator — Controller 一時連結 (H3) と同根
- 0.10 O3 Itinerary leg + routeDeviation 正式実装 — US21 料金内訳 (H6, M8) と整合連動

これら 3 件は **アーキテクチャ高優先指摘 (H1〜H3) と一体で IT7 冒頭タスクとして束ねる** のが効率的。

## エージェント別フィードバック要旨

### xp-programmer (高 2 / 中 2 / 低 1)
TDD 規律維持、Billing Context 実装は DDD パターン準拠で素直。一方で Money 二重定義 / HandlingController 集約間連結 / `*ByRaw` 命名 / `Invoice.issue` デッドコードが Simple Design 4 ルールの「重複なし」を破る。

### xp-tester (高 2 / 中 3 / 低 1)
ピラミッド形状は健全。冪等性テストの明示・Testcontainers IT 配置は理想形。ただし「お金」と「並行性」のエッジが薄い (PricingService 失敗 / OutOfOrder 衝突 / 100% 割引境界欠落)。E2E を staging に委ねた判断は妥当。

### xp-architect (高 3 / 中 3 / 低 3)
ArchUnit 未カバー (H1) → Billing 直結 (H2) → Controller orchestration (H3) は同根の構造課題。IT7 最優先で ArchUnit 拡張＋ACL 化を実施しないと Release 1.0 直前の切り戻しコストが急騰する。Money 二重 / PricingService 素通し / Invoice IT 欠落も同フェーズで対応。

### xp-technical-writer (高 1 / 中 2 / 低 5)
ADR 0013 は模範的。一方で iteration_plan-6.md / docs/development/index.md / domain-model.md の進捗ドキュメント追従不足。OptimisticLock 例外の UI 文言とユビキタス言語の揺れに業務影響あり。

### xp-user-representative (高 2 / 中 2 / 低 0)
請求書の法人フラグ手入力と料金内訳非表示は月末オペレーションを破綻させる。Claim 荷受人確認の 1 フィールド構成は紛争時証跡として弱い。手動更新の理由欄不在は内部統制 NG。Role.Settlement 開放 / 「精算」→「請求管理」表記変更は妥当。

## 対応方針 (IT7 冒頭ストーリー化推奨)

1. **アーキテクチャ堅牢化バンドル** (H1+H2+H3+0.2+0.3): ArchUnit 拡張 → 違反可視化 → BillingCargoQueryPort + HandlingOrchestrator + CargoSnapshot ACL
2. **業務適合性修正バンドル** (H5+H6+M6+M7+M10): 法人フラグ自動判定 + 料金内訳表示 + 荷受人確認種別 + 手動更新理由 + ユビキタス言語統一
3. **Money 統一 ADR** (H4): 単通貨 (JPY) 確定の ADR 0014 起票 + shared.domain.Money 一本化
4. **テスト補強** (H7+M3+M4+M5+M9): PricingService 失敗 / OutOfOrder / 境界値 / Invoice 楽観ロック IT
5. **ドキュメント追従** (M11+M12+L1〜L7): ステータス更新 / Scaladoc 補足 / mkdocs.yml 重複削除

## 結論

**MVP 機能は揃った。Release 1.0 ゲートはアーキテクチャ堅牢化バンドル (1) と業務適合性修正バンドル (2) を IT7 冒頭で実施することを前提に通過可能。** developing-review 正式実施として、上記指摘を IT7 計画 (planning-releases --iteration 7) で必ず反映すること。
