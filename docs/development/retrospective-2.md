---
title: イテレーション 2 ふりかえり
description: IT2（見積・貨物予約・引き渡し）の KPT ふりかえり
date: 2026-07-28
---

# イテレーション 2 ふりかえり（KPT）

対象: IT2（US01 見積作成・US04/US05 貨物予約・US06 引き渡し）。目標 15SP / 実績 15SP（達成率 100%）。Phase 1（MVP）完了。

## サマリー

| 指標 | 値 |
| :--- | :--- |
| 目標 SP / 実績 SP | 15 / 15 |
| テスト | 211 件 green（単体・統合）+ Playwright E2E 8 件 |
| カバレッジ | 全体 94.7% |
| SonarQube Quality Gate | PASS（Bug 0・Vulnerability 0・Code Smell 0・重複 0%） |
| CI | success（verify + E2E） |
| 新規 ADR | ADR-007（共有カーネル拡張方針・スタブ ACL） |
| コミット数 | 9（実装 + レビュー対応 + クローズ） |

## Keep（継続すること）

- **序盤アウトサイドイン + BC 独立性の徹底**が機能。Booking→Shipper を ShipperExistenceChecker ACL 経由に保ち、dependency-cruiser で機械的に検証できた。
- **前 IT Try の反映が効いた**: `shared→contexts` 逆流禁止ルール（T5）を先に入れたことで共有カーネル拡張時の統制が効いた。lock クリーン再生成手順（T3）も定着。
- **CQRS のコマンド/クエリ分離**が読み取り経路を軽量に保った（Query Service が DTO を直接返す）。
- **マルチパースペクティブレビューで実バグを検出**: 期限判定の日付粒度バグ（DATE vs 時刻付き ETA）をテスターが着手前に指摘し、クローズ内で修正できた。
- **ドメインイベント（CargoBookedEvent）を発行のみで先行**し、購読は IT4 に分離。段階的縦切りが保てた。
- **設計ドリフトの都度是正**（data-model 荷受人カラム・domain-model の他 take 由来注記・CargoType 共有カーネル化）を実装と同一クローズで同期。

## Problem（問題点）

- **期限判定の日付粒度バグ**を初回実装で作り込んだ（`getTime()` ミリ秒比較で当日着を誤判定しうる）。メモリ [[feedback_date-vs-timestamp-deadline]] の既知パターンを再発させた。
- **エラー分類の一貫性欠如**: Estimation が plain Error を throw し、Location の検証エラーが内部障害に誤分類される設計を初回で見落とした（IT1 で確立した ValidationError パターンを Estimation に展開し忘れ）。
- **ReDoS 脆弱な Email 正規表現**を IT1 から引き継ぎ、IT2 でも複製した。SonarQube 品質ゲートで初めて顕在化。
- **US04「見積情報との整合性確認」が未実装**。見積→予約の連携が受入基準の一部を満たしていない（今回はスコープを明確化し次 IT へ）。
- **統合テストのフレーク**: supertest + express-session でフルスイート時に稀にログインセッションのタイミングで 302 になる（~0.5%）。retry:1 で暫定吸収。
- **SonarQube hotspot をトークンでレビュー済み化できない**（分析トークンは 403）。今回は ReDoS を根本修正して hotspot 自体を消滅させたが、手動レビューが必要な hotspot が今後出た場合の運用が未確立。

## Try（次に試すこと）

| # | アクション | 期待効果 | 反映先 |
| :--- | :--- | :--- | :--- |
| T1 | 新規 BC 実装時、IT1/IT2 で確立した ValidationError パターン（利用者提示 vs 内部障害の区別）を着手時のチェック項目にする | エラー分類の一貫性を初回から担保 | opening-iteration DoD |
| T2 | 日付・期限を扱うロジックは「日付単位比較」を既定とし、時刻付き値との境界テスト（当日着）を必須化 | date vs timestamp バグの再発防止 | 開発戦略・テスト方針 |
| T3 | US04 見積連携（EstimateId 参照・整合性確認）を実装 | 見積→予約フローの受入基準完全充足 | IT3 or IT4 |
| T4 | 統合テストのログインを共通ヘルパー化し、セッション確立をアサートして安定化（retry 依存を解消） | フレーク恒久対応 | IT3 着手時 |
| T5 | SonarQube hotspot 用に、レビュー権限を持つトークンを .env に用意するか、CI 側で hotspot レビューを運用に組み込む | 手動レビュー必須 hotspot への対応確立 | operating-qt 運用 |
| T6 | dependency-cruiser の no-cross-context 例外（acl 一律許可・未使用）を実態に合わせ厳格化 | 誤用余地の排除 | IT3 着手時 |
| T7 | ルート候補スタブ ACL を Routing Context 実装時に外部経路 ACL へ差し替え（ADR-007 返済トリガー） | 意図的負債の計画的返済 | IT3 |
| T8 | Weight・origin≠destination の重複を共有カーネルへ集約 | 検証ロジックの一貫性 | IT3 以降 |

## 次イテレーション（IT3）への引き継ぎ

- **スコープ**: US24/US25 航海スケジュール・US07 検索・US08 経路候補算出（中盤インサイドアウトへ移行）。Routing Context の中核ドメインをドメイン層から作り込む。
- **持ち越し（受入基準の残）**: T3（US04 見積連携）
- **技術的負債の返済**: T7（スタブ ACL → 外部経路 ACL、ADR-007 返済トリガー）・T6（dependency-cruiser 厳格化）・T4（テスト安定化）
- **局面転換**: IT3 から中盤インサイドアウト。経路候補算出（US08）・Leg 連結制約は中核ドメインのため、ドメイン層のテストファーストを徹底する。
- **基盤は再利用可能**: 認証・RBAC・DB 基盤・共有カーネル・イベント基盤・CI・SonarQube 連携は整備済み。
