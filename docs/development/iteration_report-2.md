---
title: イテレーション 2 完了報告書
description: IT2（見積・貨物予約・引き渡し）の完了報告
date: 2026-07-28
---

# イテレーション 2 完了報告書

## エグゼクティブサマリー

IT2 では、見積作成（US01）→ 貨物予約登録（US04/US05）→ 経路設計者への引き渡し（US06）の MVP 縦フローを実装し、**Phase 1（予約・荷主管理基盤 MVP）を完了**した。Estimation Context と Booking Context を新規実装し、BC 独立性（ShipperExistenceChecker ACL）・CQRS・ドメインイベントを設計どおりに実現した。目標 15SP を 100% 達成。品質ゲート（211 テスト green・Playwright E2E 8 件・SonarQube Quality Gate PASS・CI success）をすべて通過した。

| 項目 | 内容 |
| :--- | :--- |
| 期間 | 2026-08-10 〜 2026-08-23（計画） |
| 目標 SP / 実績 SP | 15 / 15（達成率 100%） |
| 対象ストーリー | US01・US04・US05・US06 |
| コミット数 | 9 |
| 変更規模 | 73 ファイル / +3,599 行 |
| 累計 SP | 23 / 81（Phase 1 完了） |

## 達成状況

| ID | ストーリー | SP | 状態 | 備考 |
| :--- | :--- | :--: | :--- | :--- |
| US01 | 輸送見積を作成する | 5 | ✅ 完了 | ルート候補スタブ算出・見積番号発行・期限充足通知・危険物 htmx |
| US04 | 貨物予約を登録する | 5 | ✅ 完了 | 荷主 ID 存在確認（ACL）・荷受人・仮受付・CargoBookedEvent 発行（見積連携は次 IT） |
| US05 | 危険物・冷凍貨物の予約を登録する | 3 | ✅ 完了 | 危険物申告必須・温度管理条件必須（htmx 差替 + ドメイン検証 + 否定パス） |
| US06 | 予約情報を経路設計者に引き渡す | 2 | ✅ 完了 | PRELIMINARY→ROUTING_IN_PROGRESS・通知イベント・経路設計待ち一覧到達性 |

## 技術的成果

- **Estimation Context**: Estimate 集約・RouteCandidate・スタブ経路算出 ACL・CQRS（コマンド + クエリ）・見積画面
- **Booking Context**: Cargo 集約（荷受人・危険物/冷凍の不変条件・状態遷移）・ShipperExistenceChecker ACL・CargoBookedEvent（EventEmitter2）・予約画面
- **共有カーネル拡張**: Location（UN/LOCODE）・CargoType（ADR-007）
- **DB**: マイグレーション 002（location/estimate/route_candidate/cargo・荷受人カラム前倒し）・location シード
- **品質**: `shared→contexts` 逆流禁止ルール追加・EstimateRepository のトランザクション化

## 品質指標

| メトリクス | 実績 | 目標 | 判定 |
| :--- | :--- | :--- | :--- |
| 単体・統合テスト | 211 件 green | 全 green | ✅ |
| E2E（Playwright） | 8 件 green | 主要シナリオ | ✅ |
| カバレッジ（全体） | 94.7% | 80% | ✅ |
| SonarQube Bug / Vulnerability | 0 / 0 | 0 / 0 | ✅ |
| Code Smell / 重複率 | 0 / 0.0% | 0 / <3% | ✅ |
| SonarQube Quality Gate | PASS | PASS | ✅ |
| CI（verify + E2E） | success | success | ✅ |

## レビュー結果

XP 5 視点のマルチパースペクティブレビューを実施（[レビューレポート](../review/IT2実装_review_20260728.md)）。高優先度 7 件（H1〜H7）と収束した中優先度 6 件を本イテレーション内で対応した。

- H1/H2: 期限判定を日付単位比較へ修正・Estimate 集約に集約（DRY）
- H3: SharedValidationError/EstimateValidationError 導入でエラー分類を一貫化
- H4/H5: US05 否定パス・CargoBookedEvent ペイロード検証を追加
- H7: UN/LOCODE datalist・荷主登録前提の文書化
- セキュリティ: **Email 正規表現の ReDoS 脆弱性を線形実装へ置換**、open-redirect を encodeURIComponent で緩和
- M1/M2/M3: EstimateRepository トランザクション化・ADR-007 起票

## ADR

- **ADR-007**: 共有カーネルに Location/CargoType を配置、ルート候補算出はスタブ ACL（意図的負債、返済トリガー = Routing Context 着手）

設計反映: domain-model（CargoType 共有カーネル化・他 take ドリフト是正）、data-model（cargo 荷受人カラム）を実装と同期。

## 課題と残作業（IT3 引き継ぎ）

- US04 見積連携（EstimateId 参照・整合性確認）の実装
- ルート候補スタブ ACL → 外部経路 ACL への差し替え（ADR-007 返済トリガー）
- dependency-cruiser の ACL 例外の厳格化
- 統合テストのログイン安定化（retry 依存の解消）
- SonarQube hotspot のレビュー運用確立（分析トークンは 403）

詳細は [ふりかえり](retrospective-2.md) の Try を参照。

## 次イテレーション（IT3）

**スコープ**: US24/US25 航海スケジュール・US07 検索・US08 経路候補算出（**中盤インサイドアウトへ移行**）。Routing Context の中核ドメイン（Voyage 集約・経路候補算出）をドメイン層のテストファーストで作り込む。IT2 で整備した基盤・共有カーネル・イベント基盤を再利用する。
