---
title: イテレーション 7 ふりかえり
description: IT7（US17 貨物状態手動更新・US19 遅延例外・US20 破損/紛失例外）の KPT ふりかえり。Tracking 例外処理を実装。
tags: development, retrospective, iteration-7, kpt, go
---

# イテレーション 7 ふりかえり（KPT）

対象: IT7（2026-07-27 開発完了）。終盤局面（アウトサイドイン）の初回。IT6 で「枠のみ」だった Tracking Context の**例外処理**（`TrackingExceptionEvent`・`ExceptionType`・`EscalationPolicy`・例外解決の状態復帰）と**貨物状態手動更新**を実装。実績 13 SP（計画どおり）。

## Keep（うまくいったこと）

### 技術的成功

- **既存の追跡基盤を再利用して例外処理を凝集度高く実装**: IT6 の `TrackingActivity`・`TransportStatus`（EXCEPTION 値）・`CurrentStatus` の UNKNOWN 読み飛ばしロジックを踏襲し、例外を集約内エンティティとして追加。**貨物状態 EXCEPTION を別カラムで二重持ちせず `HasActiveException()` から算出**し、解決で発生前状態に自然復帰する設計は、programmer/architect が「状態の二重管理を避け変更を安全にできる」と高評価。
- **EscalationPolicy をステートレスドメインサービスに分離**: LOST 即時 / DELAY 48h 超過（`>` 判定）の 2 系統を集約し、48 時間境界（47:59/48:00/48:01）を Clock 注入 + テーブル駆動で決定的に検証（T2 達成）。tech-writer が「境界値・即時エスカレーションの網羅は模範的」と評価。
- **T5（フルフロー E2E・統合テスト開発フェーズ内実施）を達成**: 例外ライフサイクル（登録→EXCEPTION→解決→状態復帰）の testcontainers 統合テストと handler httptest を**開発中に**追加。IT6 で「クローズに回した」反省を活かした。
- **BC 独立性を維持**: `NotificationPort` を tracking/application に定義し、他 BC を直接 import せず。`make arch` 全 green・`.go-arch-lint.yml` 無改変。

### プロセス的成功

- **クローズ 5 視点レビューで業務ループの穴と契約誤りを捕捉**: user-rep が「荷主に対応報告が届かない」「荷役作業員の破損登録導線欠落」を、programmer/architect が「通知失敗＝ユースケース失敗」の契約誤りを検出。いずれもクローズ前に是正。着手前（opening の注1〜4）＋締め（closing レビュー）の 2 段検証が機能。
- **設計是正を実装と同時反映**: 注1〜4（例外/状態更新 UI・location カラム・エスカレーション 2 系統・トレーサビリティ US 番号）を実装と同一 IT で反映。さらにレビューで test_strategy の旧 API 例・US 番号ズレ残存を追加是正。
- **SonarQube を実際に通してクローズ**: 開発中の初回 FAIL（violations 3）をリテラル定数化・パラメータ構造体化で PASS（80.3%）まで持ち込み、レビュー修正後も PASS 維持。

## Problem（うまくいかなかったこと・課題）

- **検証結果・対応報告のフィードバック到達がまた弱かった**（user-rep 高・IT6 T1 の再発傾向）: 追跡詳細（荷主照会）に発生状況・対応報告を出しておらず、US19 の業務ループが開発時点で閉じていなかった。破損登録も ROLE_TRACKER のみで荷役作業員が入れなかった。DoD に T1 を入れていたが、実装時の確認が甘かった。
- **通知の契約が非トランザクションで誤っていた**（programmer/architect 高）: Save コミット後の通知失敗をユースケース失敗として返し、再送で二重登録を招く契約。ログ実装のうちは潜在だが外部連携で事故る。ベストエフォートに是正。
- **受入基準の一部が未実装/未検証だった**（tester 中）: DELAY 48h のサービス層エスカレーション経路・DAMAGE 経路のサービステストが欠落。US19 の「新到着予定日」は resolutionNotes フリーテキストに集約され構造化されず。
- **返済枠 T3/T4（ADR-0008）が未着手**: 採番原子化・荷役履歴リプレイは核心 3 ストーリー優先で見送り、IT8 へ再繰越。2 IT 連続の繰越で負債が固定化しつつある。
- **フルフロー E2E が seed 依存で実質 skip**（tester 高）: 統合テストで永続化は担保したが、web→service→通知の end-to-end は seed 未整備で skip。

## Try（次イテレーションでの改善アクション）

| Try | 内容 | 担当 | 期限/期待効果 |
|-----|------|------|--------------|
| T1 | **フィードバック到達を実装時チェックリスト化**: 検証結果・通知・対応報告が関係ロール画面に出ることを、実装直後（コミット前）に確認する運用に。DoD の宣言だけでなく手順化 | AI | IT8〜。到達漏れの再発防止 |
| T2 | **受入基準を成功基準テストに 1:1 マッピング**: US の各受入行に対応するテスト（サービス層含む）を計画時に列挙し、実装で 1:1 に埋める | AI | IT8〜 |
| T3 | **返済枠は「独立コミット枠」として先に着手**（ADR-0008 T3 採番原子化・T4 履歴リプレイ）: IT8 の Day 1-2 で先に返済し、後回しで再繰越する連鎖を断つ | AI | IT8。負債固定化の解消 |
| T4 | **フルフロー E2E の seed 基盤整備**: 発行→荷役→例外の一連を seed する E2E fixture を用意し skip を撤廃 | AI | IT8〜 |
| T5 | **エスカレーション登録後再評価**（ADR-0009）: DELAY の 48h 経過を照会時再評価 or 定期バッチ | AI | IT8 |
| T6 | **例外の周辺機能**（ADR-0009・user-rep）: 管理職向け緊急例外ワークリスト・ETA 構造化・紛失解決の CLOSED 終端・index→安定 ID | AI | IT8〜 |
| T7 | **TrackingExceptionDetectedEvent 配信**（ADR-0009）: Booking/Notification への連携を合成ルートで配線 | AI | IT8 |
| T8 | **協議依頼/通知待ちワークリスト**（IT5 由来・T6）: 3 IT 連続繰越。IT8 で決着 or 明示的にスコープ外宣言 | AI | IT8 |

## 次イテレーション（IT8）への引き継ぎ

- **IT8 スコープ**: 終盤局面・Phase 3 精算。US21 輸送料金算出・US22 法人割引・US23 精算（Billing Context・13SP）→ **Release 1.0（全機能）到達**。
- **高優先度返済**: ADR-0008（採番原子化・履歴リプレイ）・ADR-0009（エスカレーション再評価・イベント配信）を IT8 序盤で着手（T3）。
- **業務ループ**: 管理職ワークリスト・ETA 構造化・協議依頼/通知待ちワークリスト（T6/T8）。
- **良好な状態の維持**: BC 独立性（go-arch-lint 全 green）・ドメイン層 90%+ カバレッジ・SonarQube ゲート PASS・opening/closing の 2 段検証・統合テストの開発フェーズ内実施（T5）。

## 実績サマリー

| 項目 | 値 |
|------|-----|
| 計画 SP | 13（US17 3・US19 5・US20 5） |
| 実績 SP | 13（100%）+ ADR-0009 起票 |
| ドメイン層カバレッジ | tracking 93%+（例外含む） |
| SonarQube Quality Gate | PASS（new_coverage 80.3%・重複 0.3%・violations 0） |
| 品質ゲート | make check green・統合テスト（testcontainers・例外ライフサイクル）green |
| CI | 未 push（当ブランチは workflow_dispatch 手動起動） |
| レビュー | developing-review（XP 5 視点）・高優先度はクローズ前対応/一部 IT8 繰越（ADR-0009） |
| 設計反映 | ADR-0009（追跡例外設計）・domain-model/data-model/ui_design/test_strategy 注1〜4 + レビュー是正 |
