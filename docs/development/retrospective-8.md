---
title: イテレーション 8 ふりかえり
description: IT8（精算処理・Billing Context 完成・US23・Release 1.1 完成）の Keep・Problem・Try
published: true
date: 2026-07-24T00:00:00.000Z
---

# イテレーション 8 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 8（精算処理・Billing Context 完成・Release 1.1 完成） |
| **局面** | 終盤（アウトサイドイン・予備イテレーション兼安定化） |
| **計画 SP / 実績 SP** | 5 / 5（達成率 100%） |
| **対象ストーリー** | US23（精算を処理する） |
| **テスト** | domain-billing 19 + app-billing 12 + domain-booking 29 + infra invoice_repository 2 + infra-external payment_gateway 3（wiremock）+ interface-web billing_flow 9 + estimation_exception 8 + 既存全 green。E2E it8-demo 2 |
| **成果** | Invoice 集約・精算 3 サービス・決済/予約連携/通知 ACL・invoice/payment 永続化・精算書画面を全層で実装。US23 全 5 受入基準を実装・検証。**Phase 3 完了・Billing Context 完成・累計 97/97 SP（100%）・Release 1.1（例外対応・請求）の全機能が実装完了** |

## Keep（継続すること）

### 技術的成功事項

- **BC 独立が全 IT 中もっとも厳格（architect 評価）**: `domain-billing` は `shared-kernel` のみ依存。予約精算連携（settle）・決済機関・割引取得・通知をすべて app 層 ACL（`BookingSettlementPort`・`PaymentGatewayPort`・`InvoiceNotificationPort`）に切り出し、実装を composition 層（billing_acl.rs・infra-external）に隔離。Billing→Booking の直接依存を張らず ACL 経由で settle を呼ぶ設計を厳守。
- **金額計算の規律（IT6/IT7 Try 教訓の継続）**: 消費税を `calculate_tax` 純粋関数＋`DEFAULT_TAX_RATE` 名前付き定数で固定し、円未満四捨五入（ADR-0010・`MidpointAwayFromZero`）を適用。適用順序（割引後確定料金×1.1）もコードで正しく実現。金額リグレッションを単体テストで固定。
- **決済機関の wiremock 契約テスト**: `ReqwestPaymentGateway` を CONFIRMED/402 失敗/PENDING 未確認の 3 契約で検証（test_strategy §4 準拠）。tester が模範評価。
- **settle 連動の HTTP 実証（Try#1 模範）**: 入金確認 HTTP → `booking_status='SETTLED'` まで DB で実証。BC 跨ぎの状態連動を HTTP で正しく検証。

### プロセス的成功事項

- **8 IT 連続でベロシティ完全一致**: 計画ライン（16→11→11→14→14→13→13→5）と実績が完全一致。累計 97/97 SP（100%）で Release 1.1 全機能を計画通り完成。
- **opening-iteration 検証の実効**: 着手前検証で消費税適用順序・期限超過判定の配置・NotificationType 拡張を計画に反映。BC 独立性違反ゼロで着手できた。

## Problem（問題点）

### 「実装したのに動かない」型の欠落が再発（最重要）

- **CheckOverdueService が未配線だった（受入基準5 が実運用で駆動しない）**: 期限超過→未払い通知のドメイン・app 実装と単体テストは完備していたが、**どのハンドラ・バッチにも配線されておらず、HTTP/E2E で一度も駆動しない実質デッドコード**だった。tester・architect・user-rep の 3 視点が独立に「受入基準のチェックは埋まっても業務上未達」と重複指摘。IT7 Try#1（状態変更系は HTTP/E2E で 1:1 実証必須）に真正面から違反。IT4→IT5→IT7→IT8 と 4 IT 連続で形を変えて再発した「通知系が全レベル未検証」パターンの最新形。クローズ前に手動駆動エンドポイント＋一覧ボタン＋HTTP 実証を追加して返済した。

### レビューで検出した業務価値の欠落

- **精算完了通知がなかった**: 入金確認後に荷主へ「精算完了」を知らせる手段がなく、荷主が入金反映を確認できず経理への問い合わせを生む状態だった。`notify_settlement_completed` を追加して返済。
- **入金確認ボタンが Refunded でも表示・入金明細が画面に残らない**: 監査・消込の観点で不十分。`is_payable`（Pending/Overdue のみ）＋入金明細（入金日時・取引参照番号）表示で返済。
- **ドキュメントと実装の乖離（data-model 列定義）**: invoice/payment の列定義がマイグレーションとずれていた（INTEGER のまま・discount 列・updated_at 等）。実装を正典に同期して返済。

### 意図的負債（Release 1.2 へ）

- 実決済 ACL（ReqwestPaymentGateway）の本番結線は既定スタブのまま（契約テストは済み）。
- CheckOverdue の全走査（find_all）・バッチ駆動化・通知失敗の部分適用・reconstruct の InvoiceId 恒常化・per-handler DI 整理（Try#6）は Release 1.2。

## Try（次に試すこと）

| # | 改善アクション | 担当 | 期限 | 期待効果 |
|---|--------------|------|------|----------|
| 1 | 新規サービス実装時、**「駆動元（HTTP ルート/バッチ/イベント）と HTTP/E2E 実証」をタスク分解の DoD に含める**。単体テストだけの「配線されないサービス」を禁止する | 開発 | Release 1.2 | 「実装したのに動かない」型の根本対策（IT4-8 で 4 IT 連続露見） |
| 2 | 決済など外部連携は**既定スタブと本番実装の切替を composition root（環境変数/プロファイル）で行う設計**を標準化し、AppState にゲートウェイを注入する | 開発 | Release 1.2 | Stub↔Reqwest 切替の一元化・本番結線の明確化 |
| 3 | 期限超過チェックを**定期バッチ（スケジューラ）で自動駆動**し、`find_overdue_candidates(as_of)` ポートで候補を絞る。通知失敗の部分適用も冪等化 | 開発 | Release 1.2 | 督促の自動化・スケーラビリティ |
| 4 | `reconstruct` の InvoiceId 再生成を解消（invoice_id カラム往復 or invoice_number 一本化）・per-handler DI を composition root へ（Try#6 継続） | 開発 | Release 1.2 | 集約同一性の恒常化・DI 一貫性 |
| 5 | rank 一元化（Try#5）・dashboard 拡充/一覧の状態フィルタ（Try#7）・通知 SMTP 実配信・ui_design salt 追記 | 開発 | Release 1.2 | 積み残し負債・UX 改善 |

## Release 1.1（GA）に向けた残懸念

- 実決済 ACL 結線（現状スタブ）・CheckOverdue のバッチ自動化は GA 前に対処が望ましい（user-rep 指摘）。手動導線は返済済み。
- Release 1.1 の全機能は実装完了。`creating-release-report` でリリース完了報告書を作成し GA 判断へ。

## 数値指標

| 指標 | 実績 |
|------|------|
| テスト | ワークスペース全 green（domain/app 単体 + infra/interface 統合 testcontainers + 決済 wiremock + E2E）。IT8 新規: domain-billing 19・app-billing 12・billing_flow 9・payment_gateway 3・invoice_repository 2 |
| ビルド・Lint | ワークスペース clippy `-D warnings` クリーン・fmt 準拠（`+stable` 1.97.1）・cargo audit/deny 緑（非機能受け入れ） |
| ベロシティ | 5 SP（IT1=16 → … → IT7=13 → IT8=5、8 IT 連続で計画ラインと完全一致） |
| 累計進捗 | **97/97 SP（100%）・Phase 3 完了・Release 1.1 全機能実装完了** |
| レビュー | 5 視点・高優先度 7 件クローズ前返済・中低は Release 1.2 繰り越し |

## 関連ドキュメント

- [イテレーション 8 計画](./iteration_plan-8.md)
- [IT8 開発成果物レビュー](../review/it8_development_review_20260724.md)
- [ADR-0009 輸送料金と精算書の段階分割](../adr/0009-freight-charge-and-invoice-separation.md)
- [ADR-0010 Money の BC ローカル定義](../adr/0010-billing-money-value-object.md)
- [イテレーション 7 ふりかえり](./retrospective-7.md)
