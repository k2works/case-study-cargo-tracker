---
title: IT9 セルフレビュー（中間）
date: 2026-06-25
reviewer: AI Agent (Ralph Loop)
scope: IT9 全タスク完遂後の中間セルフレビュー (commit c9f81087 〜 6dff198d、23 件)
---

# IT9 セルフレビュー（中間）

正式な XP 5 エージェント並列レビュー (developing-review) は user 起動後に別途実施する想定。本書は前段のセルフレビュー。

## レビュー対象

| カテゴリ | 件数 | 主要 commit |
| :--- | ---: | :--- |
| 0.x 申し送り (IT8 持ち越し) | 7 タスク | `c9f81087` / `1c71c549` / `23e495e5` / `ae7e96c2` / `d6ffb69d` / `f953a109` / `38eab249` |
| US27 Release 2.0 GA (ドラフト) | 1 タスク | `238b5e95` |
| US28 法人 Shipper UI | 2 タスク | `66466508` |
| US29 入金消込 CSV 取込 | 5 タスク | `ed9fbdce` |
| US30 監査ログ | 5 タスク | `670f07ed` / `7905a405` / `f1f30ac8` |
| user 要望対応 | 2 件 | `019d3bd5` (マスタ管理ナビ) / `6dff198d` (E2E 4 spec) |
| クロージング | 5 件 | retrospective / iteration_report / GitHub 同期 / index 更新 |
| **commit 数** | **23 件** | `c9f81087` 〜 `6dff198d` |

## 観点別チェック

### 1. プログラマー観点

| # | 項目 | 結果 | 補足 |
| :--- | :--- | :---: | :--- |
| P1 | scalafmt / scalafix 違反ゼロ | ✅ | pre-commit hook で fullTest 含めて検証 (IT8 6fe0b22c 教訓反映、commit `1c71c549`) |
| P2 | フルテスト 430 件 Green | ✅ | sbt test (430/430、72 Suites) 確認済 |
| P3 | 値オブジェクト / Port パターン | ✅ | AuditLogId opaque type、TransactionBoundary trait、AuditLogPort (UPDATE/DELETE 未提供で不変記録保証) |
| P4 | foldLeft で immutable 化 | ✅ | confirmPaymentsBatch (US29) は var を排除し DisableSyntax.var 準拠 |
| P5 | sealed Error 網羅性 | ✅ | Invoice.refund も InvalidPaymentStateTransition で網羅、明示 case のみ |
| P6 | implicit DBSession 引き回し | ✅ | saveInTx / registerInTx パターン (Phase 1)、既存 save() は互換維持 |

### 2. アーキテクト観点

| # | 項目 | 結果 | 補足 |
| :--- | :--- | :---: | :--- |
| A1 | ADR 0021 起票 + ArchUnit ルール 6 | ✅ | Port 規約 (公開 = application.api / 入力 = domain.model.ports) + 自動検証 |
| A2 | ADR 0016 案 A Phase 1 実装 | 🔄 | Handling 部分のみ、Phase 2 (Tracking/Cargo/NotificationLog) は IT10 |
| A3 | TransactionBoundary 抽象化 | ✅ | 本番 (ScalikeJdbc) / テスト (NoOp) 切替で TX 境界をテスト可能化 |
| A4 | shared.audit kernel 配下 | ✅ | 全 Context から AuditLogPort アクセス可、ArchUnit 6 で他 ports 直接依存禁止 |
| A5 | ArchUnit ルール 4 拡張 | ✅ | commandservices に "Result" suffix 追加 (queryservices と一貫性) |
| A6 | Pekko Mail/SES Adapter | 🔄 | LoggingAdapter のみ、IT10 申し送り |

### 3. テスター観点

| # | 項目 | 結果 | 補足 |
| :--- | :--- | :---: | :--- |
| T1 | 状態遷移境界値テスト | ✅ | Invoice.refund 4 件 (Confirmed→Refunded / 二重 / 他状態 NG / 不可逆性) |
| T2 | Testcontainers IT 拡張 | ✅ | ScalikeJdbcInvoiceRepositoryIT 5 件 + AuditLogAdapterIT 5 件 |
| T3 | バッチ処理テスト | ✅ | confirmPaymentsBatch 2 件 (4 分類混在 / 全件不一致) |
| T4 | Scheduler 時刻ロジック | ✅ | OverdueDetectionSchedulerSpec 4 件 (computeInitialDelay 境界値) |
| T5 | Playwright E2E 4 spec | ✅ spec のみ | 10 シナリオ、実 PASS は user 環境 |
| T6 | Controller IT | 🔄 | ShipperControllerSpec / Controller multipart テスト → IT10 |
| T7 | Lost 通知連携 Controller IT | 🔄 | IT10 申し送り |

### 4. テクニカルライター観点

| # | 項目 | 結果 | 補足 |
| :--- | :--- | :---: | :--- |
| D1 | ADR 0021 構成 (6 章統一) | ✅ | Status / Context / 決定 / 影響 / コンプライアンス / 備考 |
| D2 | CHANGELOG `[2.0.0]` 確定 | ✅ | Phase 4 全成果 (IT7-IT9) を Added/Changed/Documentation で網羅 |
| D3 | release-2.0.0-gate-check.md | ✅ | 5 カテゴリ 25 項目 + 既知の制約 5 件 + user 実施待ち項目明示 |
| D4 | retrospective-9 / iteration_report-9 | ✅ | KPT (Keep 8 / Problem 9 / Try 10) + 22 タスク詳細表 |
| D5 | CLAUDE.md 更新 | ✅ | Flyway 採番ルール (IT8 M2) + ADR ↔ ArchUnit チェックリスト (IT8 R8/T12) |
| D6 | mkdocs ナビ追加 | ✅ | ADR 0021 / retrospective-9 / iteration_report-9 / release-2.0.0-gate-check 全追加 |
| D7 | user_story.md US27-30 正式追加 | ✅ | Day 1 必須作業として完了 |

### 5. ユーザー代表者観点

| # | 項目 | 結果 | 補足 |
| :--- | :--- | :---: | :--- |
| U1 | US28 法人 Shipper UI 操作性 | ✅ | JS 表示制御 (Corporate 選択時のみ法人フィールド)、UX 向上 |
| U2 | US28 ロール制御 | ✅ | Sales / MasterAdmin 限定、他ロール Forbidden |
| U3 | US29 CSV 4 分類結果表示 | ✅ | 成功 / 不一致 / 二重 / エラーの色分けで一目で把握 |
| U4 | US29 監査ログ連携 | ✅ | ImportPaymentsBatch action で記録 |
| U5 | US30 監査ログ MasterAdmin 限定 | ✅ | UI dropdown 非表示 + Controller Forbidden の二重防御 |
| U6 | US30 監査ログフィルタ (5 種) | ✅ | from/to/operator/action/limit 全対応 |
| U7 | ナビ「マスタ管理」dropdown | ✅ | user 要望対応、MasterAdmin 限定で ワンクリック動線 |
| U8 | US23 Refund UI | 🔄 | Invoice.refund はドメイン実装済だが Controller アクション未実装、IT10 申し送り |
| U9 | US27 ステージング検証 | 🔄 | user 実施待ち、ゲートチェックリスト整備済 |

## 統合フィードバック

### 高優先 (IT9 完了前に対応推奨)

| # | 内容 | 緊急度 | 解消提案 |
| :--- | :--- | :---: | :--- |
| H1 | Pekko Mail / SES 連携 (0.2 残) | 高 | IT10 初日、外部認証設定確認後 |
| H2 | ADR 0016 案 A Phase 2 (4 Repository 統合) | 高 | IT10、Tracking/Cargo/NotificationLog の saveInTx 拡張 |
| H3 | Playwright E2E 実 PASS 確認 | 高 | user 環境 (dev server + Postgres) で実行 |
| H4 | Controller IT 環境整備 | 中 | IT10、Play TestKit + 4 Controller (Shipper / Invoice / Tracking / AuditLog) |

### 中優先

| # | 内容 | 解消提案 |
| :--- | :--- | :--- |
| M1 | findByPaymentReference Repository クエリ未追加 | findAll() 全件取得 → Map 構築の非効率、IT10 |
| M2 | Invoice.refund Controller アクション未実装 | UI + Settlement ロール制御 + AuditLog 連携 (Refund action)、IT10 |
| M3 | OverdueDetectionScheduler Pekko TestKit 検証 | 実 Scheduler 起動 / 停止 / cancellable.cancel() 動作確認、IT10 |
| M4 | CSV 取込メッセージの英日混在 | "期待: referenceCode,paidAt,amount" の日本語化、IT10 |
| M5 | audit_log retention ポリシー | 6 ヶ月以上経過ログの自動アーカイブ、IT11+ |

### 低優先

| # | 内容 | 解消提案 |
| :--- | :--- | :--- |
| L1 | E2E spec の `test.skip(true, ...)` 利用 | データセットアップ前提を整備すれば skip 不要、IT10 |
| L2 | InvoiceController が肥大化 (200+ 行) | refund アクション追加で更に膨らむ、PaymentController / CsvImportController 分割検討 IT10+ |
| L3 | E2E PageObject の冗長性 | BookingPage / BillingPage の共通動作 (login, navigation) 抽象化、IT10+ |

## 矛盾事項 / 整合性懸念 (確認結果)

| # | 内容 | 確認結果 |
| :--- | :--- | :--- |
| C1 | Flyway V29 / V30 採番 (計画 V29 audit_log / V30 invoice refund → 実装 V29 invoice refund / V30 audit_log) | CLAUDE.md max+1 採番ルール (IT8 教訓 M2) 準拠で実装側を優先、計画と差異あるが追跡可能 |
| C2 | US27 ステージング → 本番 deploy 未実施 | release-2.0.0-gate-check.md に明示、user 実施待ち |
| C3 | refund Controller 未実装 vs Invoice.refund 実装 | ドメインのみ完成、UI は IT10 申し送り (整合性は維持) |

## 総合評価

| 観点 | 評価 |
| :--- | :--- |
| **コード品質** | A (sealed Error 網羅 / 値オブジェクト / Port パターン / pre-commit fullTest) |
| **アーキテクチャ** | A- (ADR 0021 起票 + Rule 6、ただし Phase 2 単一 TX 未完) |
| **テスト** | A (Unit/IT 充実 + E2E spec 整備、Controller IT は IT10) |
| **ドキュメント** | A+ (CHANGELOG / ゲートチェックリスト / KPT / 完了報告書 / セルフレビュー の 5 文書整備) |
| **業務適合性** | A (US28 UI / US29 CSV / US30 監査ログ / ナビ dropdown が業務直結) |
| **総合** | **A** |

IT9 は新規 US 4 件 + 申し送り 8 件 + ADR 1 件 + Flyway 2 件 + user 追加 2 件と最大規模、AI 完結可能範囲 (11/13 SP) を 1 日で完遂。US27 実 deploy は user 待ち。

## 推奨される次のアクション

1. **正式マルチパースペクティブレビュー** (developing-review、XP 5 エージェント並列): user 起動後に別途実施
2. **IT10 計画策定** (`/planning-releases --iteration 10`): 本書 H1-H4 + retrospective-9 Try 10 件をベースに
3. **ステージング → 本番 deploy 実施** (user 主導): GitHub Release v2.0.0 タグ + CloudWatch / Sentry 設定

## 関連ドキュメント

- [IT9 計画](../development/iteration_plan-9.md)
- [IT9 ふりかえり](../development/retrospective-9.md)
- [IT9 完了報告書](../development/iteration_report-9.md)
- [Release 2.0.0 GA ゲート確認](../development/release-2.0.0-gate-check.md)
- ADR 0021 [Port パターン規約](../adr/0021-port-pattern-convention.md)
- [IT8 セルフレビュー](./it8_self_review_20260624.md) / [IT8 実装レビュー](./it8_implementation_review_20260624.md)
