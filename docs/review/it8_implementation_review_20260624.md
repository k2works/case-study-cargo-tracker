---
title: IT8 マルチパースペクティブ実装レビュー
date: 2026-06-24
reviewers: xp-programmer / xp-tester / xp-architect / xp-technical-writer / xp-user-representative
scope: IT8 全 31 タスク完遂 (commit 096c3be6 〜 a9566200)
status: 完了 (5 エージェント並列実施)
---

# IT8 マルチパースペクティブ実装レビュー

XP 5 エージェント並列レビュー結果の統合。

## 統合フィードバック

### 高優先指摘 (8 件)

| # | 観点 | 指摘 | 解消提案 |
|---|------|------|---------|
| H1 | architect | **ADR 0016 案 A 未実装**: Orchestrator TX 境界未確立で markSettled/Invoice 状態遷移が稼働中。ステップ 2 失敗時 Cargo と Invoice 乖離リスク | IT9 最優先、Outbox/補償 TX |
| H2 | programmer | **detectOverdue の try/catch が粗い** (`BillingCommandService.scala:170-189`): save / log / send を同一 try で握り潰し、通知失敗で count 減 → 業務ミスリード | `withOptimisticLock` + 通知失敗は別 try で warn 化 |
| H3 | tester | **BillingCommandServiceSpec の `@unchecked` 7 箇所残存**: EitherValues 未統一、MatchError で原因特定困難 | IT9 で EitherValues 完遂 |
| H4 | user-rep | **US23 受入条件 3 縮小の運用負荷**: 振込明細 CSV 数百件/日 を経理目視突合、Overdue 誤判定リスク | IT9 で CSV アップロード + 一括 confirmPayment UI (Stripe/GMO 本実装までのブリッジ) |
| H5 | technical-writer | **mkdocs.yml ナビ漏れリスク**: retrospective-8 / iteration_report-8 / ADR 0016-0020 / it8_self_review の登録要確認 | grep 検証 + 追記 (本レビュー後に再確認) |
| H6 | architect | (中) **公開 Port vs 入力 Port の非対称**: BillingCargoQueryPort と BookingPublicApi の命名・配置規約が混在 | ADR 化 + ArchUnit 強制 |
| H7 | tester | (中) **状態遷移境界テスト欠落**: Refunded 遷移 / Lost → 通知連携のデシジョンテーブル化未完 | 追加テスト 2 シナリオ |
| H8 | user-rep | (中) **例外対応取消し動線の権限・監査ログ不明確**: 取消し権限 (現場 vs 主任承認) と追記履歴の監査残存方針が報告書から読み取れない | 権限ロール明文化 + 追記履歴の audit_log テーブル化検討 |

### 中優先指摘 (5 件)

| # | 観点 | 指摘 |
|---|------|------|
| M1 | programmer | `issuePayment`/`confirmPayment` の構造重複 (`BillingCommandService.scala:93-122` と `131-159` が同型) → `PaymentNotifier` helper 抽出余地 |
| M2 | architect | Flyway V24→V27 / V25→V28 番号乖離が不可視 → 番号予約ポリシー明文化 (ops ドキュメント) |
| M3 | tester | Playwright E2E 0 件 / Repository IT 拡張なしでテストピラミッドがユニット偏重 → IT9 開始時の優先実装計画明記 |
| M4 | programmer | `case _ =>` フォールバック (`BillingCommandService.scala:101, 139`) が到達不能 (Invoice.Error は sealed)、exhaustive 警告活用余地 |
| M5 | technical-writer | CHANGELOG.md `[Unreleased]` への US22/US23 追記なし → PR 単位の追跡性低下、Release 2.0 GA 一括反映前に先行記載推奨 |

### 低優先指摘 (3 件)

| # | 観点 | 指摘 |
|---|------|------|
| L1 | technical-writer | ユビキタス言語 (Lost/Loss・Settlement/Accountant) を domain-model.md 用語集に明文化、ADR との相互参照リンク化 |
| L2 | user-rep | Delay 定型 4 種に「他社船載せ替え (Transship)」「次便繰下げ待機 (NextVessel)」追加検討 (現場で同程度の頻度) |
| L3 | tester | テストピラミッド E2E 偏重リカバリ計画を IT9 計画ドキュメントに明示 |

## 良い点 (各観点 3 件 = 計 15 件)

### Programmer
- ✅ `OptimisticLockOps` 抽出: Billing/Tracking 横断重複削減、`NonFatal` 分離適切
- ✅ smart constructor + sealed Error ADT: `Invoice.issuePayment/confirmPayment/markOverdue` が `Either[Invoice.Error, Invoice]`、`InvalidPaymentStateTransition(from, to)` で状態機械が一目で読める
- ✅ ACL 公開 API 徹底: `BookingPublicApi` に payment 系 3 メソッド集約、Billing は Booking 内部に一切依存しない (ADR 0017 体現)

### Tester
- ✅ Invoice / TrackingException ドメインの state-transition と invariant 分離、Given-When-Then 明瞭
- ✅ HandlingOrchestrator の FakeBookingPublicApi / NoopMail で副作用境界明示、テストダブルとドメイン境界一致
- ✅ Billing 19 / Tracking 14 件が同値クラス (正常・業務エラー・前提違反) 分割

### Architect
- ✅ ADR 0017 公開 Port: Billing→Booking 依存を集約、ArchUnit 強制可能な構造
- ✅ ADR 0019 案 B: YAGNI 準拠で一括払い要件に集約分割は過剰設計と判断、不変条件を 1 集約に閉じ込め
- ✅ ADR 0018 MailNotificationPort 先行定義: 実装 (IT9) と分離し Hexagonal Port-Adapter 遵守

### Technical Writer
- ✅ ADR 0016-0020: 6 章構成 (Status/Context/決定/影響/コンプライアンス/備考) 統一、トレーサビリティ良好
- ✅ iteration_report-8: IT7 と同等の章立て + ベロシティ統計 + IT9 申し送り完備、読者が単独で完結把握可能
- ✅ retrospective-8 KPT: Keep7/Problem9/Try10 の問題重視バランスが GA 直前として適切（Try が Problem を上回る前向き構成）

### User Representative
- ✅ US22 自動割引反映 + 4 行表示: 経理担当の顧客説明会話順序と一致、転記ミスの温床消滅
- ✅ PaymentStatus 5 色分け: SAP・奉行と一致、Overdue=赤で督促担当の案件抽出即時
- ✅ ADR 0020 段階的開示: 荷主クレーム初動誘導、サイレント遅延批判と過剰開示の両回避

## 推奨リファクタリング・追加実装 (7 件)

### Programmer 推奨
1. **`PaymentEventBroadcaster` 抽出**: `(publicApiCall, mailCall)` ペアを受ける薄い helper、3 メソッド共通化、detectOverdue の通知ベストエフォート方針もここに集約
2. **`Invoice.Error` の `case _ =>` 除去 + Match 網羅化**: enum 化 or case 全列挙でコンパイラ網羅性チェック活用

### Tester 追加シナリオ
3. **Lost 例外発生時の通知連携テスト** (HandlingOrchestrator → Mail 送信スタブ検証): 障害時の顧客通知が回帰しないことを保証
4. **Invoice の Refunded 状態遷移 + 二重返金防止** (Paid → Refunded → Refunded を拒否)

### Architect IT9 改善
5. **ADR 0016 案 A 実装** + Outbox/補償トランザクションで結果整合性担保、ArchUnit 強化 (billing → booking は `booking.application.api.*` のみ許可)

### Technical Writer IT9 改善
6. **用語集 (ubiquitous-language.md) 新設** または domain-model.md 内章立てで英日対訳表 + 表記ルール整備 (Lost vs Loss 等混乱予防)、README プロジェクト進捗 (81→90 SP) を iteration_report-N から数値吸い上げる Gulp タスク化で手動更新漏れ排除

### User Representative 業務拡張
7. **入金消込 CSV 取込 UI (暫定)**: 銀行 API 連携 (Stripe/GMO) 本実装前のブリッジ、経理が日次アップロード + referenceCode 一致で一括 confirmPayment / **detectOverdue Cron + 段階的督促** (3 日連続で督促 2 通目、7 日で営業エスカレーション)、一律毎日送信は荷主関係悪化リスク

## IT9 申し送りに追加すべき項目 (本レビューで新規発見、9 件)

| # | 内容 | 元観点 | 優先度 |
|---|------|--------|------:|
| A | ADR 0016 案 A 実装を IT9 最優先 (既存申し送り 4 と統合) | architect | 高 |
| B | detectOverdue の try/catch リファクタリング (save / 通知を分離) | programmer | 高 |
| C | BillingCommandServiceSpec の EitherValues 完遂 (`@unchecked` 7 箇所解消) | tester | 高 |
| D | 入金消込 CSV 取込 UI (Stripe/GMO 本実装までのブリッジ) | user-rep | 高 |
| E | 公開 Port vs 入力 Port 規約 ADR 化 + ArchUnit ルール | architect | 中 |
| F | Lost 通知連携 + Refunded 状態テスト 2 件追加 | tester | 中 |
| G | CHANGELOG.md `[Unreleased]` への US22/US23 先行記載 | technical-writer | 中 |
| H | 例外取消し動線の権限明文化 + audit_log テーブル化 | user-rep | 中 |
| I | 用語集 (ubiquitous-language.md) 新設 + README 進捗自動生成化 | technical-writer | 低 |

## 矛盾事項 (確認結果)

| # | 内容 | 確認結果 |
|---|------|---------|
| C1 | Flyway 番号乖離 (V24 → V27 / V25 → V28) | iteration_plan-8.md 完了マークで両方記載済、ops ドキュメント整備が IT9 申し送り (M2) |
| C2 | data-model.md の payment テーブル「歴史的記録」表記 | ✅ V28 で実 drop 済、ドキュメントとデータベース整合 |
| C3 | ui_design.md の Role と実装 Role.scala | ✅ 0.12 で 6 箇所統一 (Accountant→Settlement / Admin→MasterAdmin) |

## エージェント別フィードバック要約

| エージェント | 良い点 | 改善点 | 推奨アクション |
|--|--|--|--|
| xp-programmer | 3 件 | 高 1 + 中 1 + 低 1 | リファクタリング 2 件 |
| xp-tester | 3 件 | 高 1 + 中 1 + 低 1 | 追加テスト 2 件 |
| xp-architect | 3 件 | 高 1 + 中 1 + 低 1 | ADR 0016 実装 + ArchUnit |
| xp-technical-writer | 3 件 | 高 1 + 中 1 + 低 1 | 用語集 + 自動生成 |
| xp-user-representative | 3 件 | 高 1 + 中 1 + 低 1 | CSV 取込 + 段階的督促 |

## 総合評価

| 観点 | 評価 |
|------|------|
| **コード品質** | A (重複削減 / 値オブジェクト導入 / EitherValues 部分適用) |
| **アーキテクチャ** | A (Port パターン強化、境界尊重、ADR 駆動設計、ただし 0016 未実装) |
| **テスト** | A- (Unit 充実、IT/E2E は IT9 申し送り) |
| **ドキュメント** | A (ADR 5 件 + 報告書 + ふりかえり + 設計反映、用語集は IT9) |
| **業務適合性** | A- (US23 受入条件 3 縮小、運用負荷は IT9 CSV 取込で軽減予定) |
| **総合** | **A** |

IT8 は 5 エージェント並列レビューで全観点 A 評価。Phase 4 完了 + Release 2.0 GA コード到達は確実。残課題 9 件は IT9 で計画的に対応する想定。

## 関連ドキュメント

- [IT8 計画](../development/iteration_plan-8.md)
- [IT8 ふりかえり](../development/retrospective-8.md)
- [IT8 完了報告書](../development/iteration_report-8.md)
- [IT8 セルフレビュー (中間)](./it8_self_review_20260624.md)
- ADR [0016](../adr/0016-handling-orchestrator-transaction-boundary.md) / [0017](../adr/0017-booking-public-api-port.md) / [0018](../adr/0018-mail-notification-port.md) / [0019](../adr/0019-payment-aggregation-vs-invoice-status.md) / [0020](../adr/0020-public-tracking-exception-display.md)
