---
title: イテレーション 4 ふりかえり
description: IT4（US08 経路候補算出・US09 経路確定・IT3 Try 全返済）の KPT ふりかえり。
tags: development, retrospective, iteration-4, kpt, go
---

# イテレーション 4 ふりかえり（KPT）

対象: IT4（2026-07-25 開発完了）。中盤局面（インサイドアウト）。US08 経路候補算出（RouteFinder グラフ探索）・US09 経路確定（CargoItinerary/Leg/Delivery/RoutingStatus・BC 横断合成ルート注入）を実装し、IT3 の Try（T1-T6）を全返済。実績 11 SP。

## Keep（うまくいったこと）

### 技術的成功

- **探索アルゴリズムの domain 隔離**: IT4 最難関の経路探索を `RouteFinder` ドメインサービスとして domain 層に閉じ、Voyage 群を接続グラフとみなす DFS を副作用なく実装。routing domain 96.6% のカバレッジで、直行/経由/接続不能/期限超過/貨物種別非対応/期限ちょうど/循環回避/推奨順タイブレークまで境界を隔離検証。上位層（application/interfaces）に探索ロジックが漏れなかった。
- **BC 横断の合成ルート注入方式が奏功**: `RouteSearcher` ACL ポートを消費側 BC（Booking/Estimation）の application に定義し、Routing の `SearchRoutesService` を `cmd/server` の変換アダプタで注入。`.go-arch-lint.yml` を一切変更せず BC 独立性を保ったまま Routing 探索を 2 つの BC で再利用できた（ADR-0007）。Architect レビューで「新規実装に構造的問題なし」。
- **Delivery 経由の RoutingStatus 保持**: 設計本体（ADR-0003）どおり `Cargo → Delivery → RoutingStatus` とし、US09 の経路確定（routingStatus=ROUTED）と US13 の予約確定（BookingStatus=CONFIRMED）を明確に分離。状態の単一情報源を集約に寄せた。
- **Try 返済を IT スコープに組み込んだ**: Clock 注入（T3）・Estimate 不変条件（T6）・境界テスト（T5）を Task 0 で先行し、見積の実経路化（T3）・動的区間（T2）を本体タスクと並行返済。持ち越しゼロで IT4 を閉じた。

### プロセス的成功

- **開発中の中間 self-review が効いた**: 3 観点（programmer/tester/architect）を開発中に並列で回し、programmer/tester が**同時に**指摘した確証バグ（DATE 期限 vs TIMESTAMP 到着の齟齬で期限当日着を誤って刈る）をクローズ前に是正。境界テストで再発を防止。2 観点独立一致は指摘の信頼性が高い。
- **クローズ時のレビューで残 2 観点を補完**: technical-writer が data-model の未同期（cargo.routing_status・route_candidate.waypoints）を、user-representative が期限充足の判断 UX 欠落（H1）を検出。いずれもクローズ前に対応し、設計と実装の乖離・受入基準の穴を残さなかった。
- **SonarQube 品質ゲートで機械的に締めた**: 引数過多 2 件（Code Smell）をゲートが検出。params 構造体化（`NewEstimateParams`）とテストヘルパの `time.Time` 化で解消し PASS。テスト緑だけで満足せず静的解析まで通した。

## Problem（うまくいかなかったこと・課題）

- **設計ドキュメント同期が T1 内で漏れた**: /route ロール・RouteCargoCommand・ドメインモデルは実装と同時に反映したが、data-model の物理カラム（cargo.routing_status・route_candidate.waypoints）が「将来追加予定」「transit_port」のまま残り、technical-writer レビューで検出された。マイグレーション追加時に data-model 物理テーブルを同時更新するチェックが機能しなかった。
- **UX の受入基準充足が実装時に後回し**: route.html は候補の所要日数・費用は出したが、到着予定日・到着期限・残日数（期限充足の判断材料）を欠いた状態で E2E を通していた。E2E が「候補が出て確定できる」ことだけを検証し、業務判断に必要な情報の有無を検証していなかったため、user-representative レビューまで気づかなかった。
- **feature ブランチの CI が自動起動しない**: CI は main への push と PR でのみ自動起動し、go/take-1 へは workflow_dispatch 手動起動が必要。クローズ時に手動トリガーを忘れると「ローカル緑・CI 未実行」で緑を誤認するリスクがあった。
- **sqlcgen 全スキーマ重複の負債が拡大**: leg/routing_status/waypoints の追加で、各 BC の sqlcgen models.go に他 BC のテーブル型が複製される既存負債（ADR-0005 決定3）がさらに広がった。go-arch-lint では検出できない。

## Try（次イテレーションでの改善アクション）

| Try | 内容 | 担当 | 期限/期待効果 |
|-----|------|------|--------------|
| T1 | **マイグレーション追加時に data-model 物理テーブルを同時更新**する手順を DoD 化。migration 000NNN を追加したら data-model の物理カラム表・DDL・論理モデルの 3 箇所を同一コミットで更新 | AI | IT5〜。設計と DB の乖離を構造的に防ぐ |
| T2 | **受入基準 UX を E2E アサートに落とす**: 画面が「業務判断に必要な情報」を表示しているかを E2E で検証（例: 経路候補に到着予定日・期限が表示される）。機能導線だけでなく情報充足も検証 | AI | IT5〜。user-rep 指摘の事前検出 |
| T3 | **US10 経路条件調整**（該当なし通知からの再算出・条件緩和導線）を IT5 で実装。route.html の行き止まり解消 | AI | IT5。US08/09 の運用完成 |
| T4 | **経路確定前の確認ステップ / 確定後のリルート導線維持**（user-rep M2）を UX 改善として検討 | AI | IT5〜 |
| T5 | **sqlcgen per-BC schema 分離**の返済（各 BC の sqlc に必要なテーブルのみを schema 指定）。全スキーマ重複の解消 | AI | IT5-6。負債返済枠 |
| T6 | **経路探索の多段乗り継ぎ深掘り・費用モデル精緻化**（現状 maxRouteLegs=4・簡易費用）。US10 の条件調整と併せて | AI | IT5-6。段階実装の後続 |
| T7 | **クローズ時の CI 手動トリガーを手順に明記**（feature ブランチは workflow_dispatch 必須）。ローカル緑・CI 未実行の誤認防止 | AI | IT5〜 |

## 次イテレーション（IT5）への引き継ぎ

- **持ち越し事項**: US10（経路条件調整）が IT5 の中心。route.html の該当なし導線・確定前確認・並び順可視化（user-rep M1/M2/L2）を US10 と同時に UX 改善する。
- **技術的負債**: sqlcgen 全スキーマ重複（T5）・経路探索の多段/費用精緻化（T6）・候補選択の TOCTOU（programmer L-2・リスク顕在時）。
- **良好な状態の維持**: BC 独立性（go-arch-lint 無改変）・ドメイン層 90%+ カバレッジ・合成ルート注入方式・中間 self-review + クローズ 5 観点レビューの 2 段運用を継続。

## 実績サマリー

| 項目 | 値 |
|------|-----|
| 計画 SP | 11（US08 8・US09 3） |
| 実績 SP | 11（100%）+ IT3 Try 全返済（T1-T6） |
| ドメイン層カバレッジ | routing 96.6%・booking 96.4%・estimation 91.5%・shared 95.8% |
| SonarQube Quality Gate | PASS（new_coverage 80.8%・重複 0.48%・violations 0・Bug 0・Vuln 0） |
| 品質ゲート | make check green・CI success・govulncheck 脆弱性なし |
| レビュー | 中間 self-review（3 観点）+ クローズ（5 観点統合）・高優先度は全対応 |
| 新規 ADR | ADR-0007（経路探索 BC 横断 ACL・探索アルゴリズム段階実装） |
