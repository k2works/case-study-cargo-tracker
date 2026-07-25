---
title: IT4 マルチパースペクティブレビュー
description: IT4（US08 経路候補算出・US09 経路確定・Try 返済）の XP 5 視点レビュー統合レポート。
tags: review, iteration-4, developing-review, go
---

# IT4 マルチパースペクティブレビュー（2026-07-25）

対象: IT4 実装差分 `ab4bcbe1..HEAD`（約 52 ファイル・約 2,400 行）。US08 経路探索（RouteFinder ドメインサービス）・US09 経路確定（CargoItinerary/Leg/Delivery/RoutingStatus・BC 横断合成ルート注入）・Try 返済（Clock 注入・見積実経路化・航海フォーム動的区間・Estimate 不変条件・境界テスト）。
手法: XP 5 視点（programmer / tester / architect / technical-writer / user-representative）を並列起動し統合。開発中の中間 self-review（programmer/tester/architect の 3 視点）とクローズ時の正式レビュー（technical-writer/user-representative を追加）を統合。

## エグゼクティブサマリー

経路探索という IT4 最難関の複雑ドメインを、`RouteFinder` ドメインサービスとして domain 層に隔離し境界を網羅検証（Architect: 構造健全・BC 独立性違反なし）。BC 横断（Routing 探索 → Booking/Estimation）は合成ルート注入方式（ADR-0007）で `.go-arch-lint.yml` 無改変を維持。高優先度指摘は **経路探索の期限境界バグ**（DATE vs TIMESTAMP）と **経路割り当て画面の期限充足判断 UX** に集約され、いずれもクローズ前に対応した。ドメイン層カバレッジは全 BC で 90% 以上（routing 96.6%・booking 96.4%・estimation 91.5%・shared 95.8%）。

## 視点別サマリーと対応

| 視点 | 判定 | 主な高/中優先度指摘 | 対応 |
|---|---|---|---|
| Programmer | 要対応 1（中） | M-1: 到着期限判定が DATE(00:00) vs TIMESTAMP(時分) の齟齬で期限当日着を誤って刈る。Estimation の候補ゼロ誤通知にも波及 | ✅ `exceedsDeadline` を日付単位比較に是正・境界テスト追加。L-2（Candidates/Assign の TOCTOU 再探索）は既知の設計判断としてコメント済み・記録に留める |
| Tester | 要追加 6（高 2） | Gap1: 期限ちょうど/DATE-TIMESTAMP 境界未検証、Gap2: 循環回避・深度制限が無検証、多区間の種別フィルタ・3区間経由港・推奨順タイブレーク | ✅ RouteFinder 境界テスト 6 種追加・route ハンドラに直行/経由の推奨順描画検証を追加 |
| Architect | 構造健全（高 0） | 中: sqlcgen 全スキーマ重複（ADR-0005 既存負債の順当な拡大・IT4 新規逸脱ではない） | ✅ 既知負債として記録（返済は sqlc per-BC schema 分離が必要・後続） |
| Technical Writer | 要対応 2（中） | M1: data-model の cargo.routing_status が「将来追加予定」のまま、M2: route_candidate.waypoints 未反映（transit_port のまま） | ✅ data-model.md の物理カラム表・DDL・論理モデルを実装に同期。L1（直行表記の画面間不統一）は許容 |
| User Representative | 要対応 1（高） | H1: 経路割り当て画面に到着期限も各候補の到着予定日/残日数もなく期限充足を判断不能。M3: 確定経路に到着予定日/所要日数がない | ✅ H1/M3/L1 を対応（下記）。M1（US10 導線）・M2（確定前確認）・L2（並び順見出し）は IT5 繰越 |

## クローズ前に対応した高/中優先度

- **到着期限判定の是正**（Prog M-1 / Tester Gap1・確証バグ）: `RouteFinder.exceedsDeadline` を日付単位比較に変更し、期限当日に時刻付きで到着する正当な候補が刈られる不具合を修正。DATE-TIMESTAMP 境界テストを追加。
- **経路探索の境界テスト補強**（Tester Gap1-5）: 期限ちょうど・循環回避の停止・多区間の貨物種別フィルタ・3 区間経由港・推奨順タイブレーク（所要日数）を追加。ハンドラに直行+経由の推奨順描画・経由港ラベル検証を追加。
- **経路割り当て画面の期限充足 UX**（User-rep H1・受入基準直結）: 画面上部に到着期限、各候補行に到着予定日と「期限まで残り N 日／期限超過」を表示。概算費用に 3 桁区切り（L1）。
- **確定経路の到着情報**（User-rep M3）: 予約詳細の確定経路に到着予定日・所要日数を表示。
- **設計ドキュメント同期**（TW M1/M2・DoD 必達）: data-model の cargo.routing_status・route_candidate.waypoints を実装に反映。

## 次イテレーション（IT5）への Try（保留・繰越）

| 由来 | 内容 | 優先 |
|---|---|---|
| User-rep M1 | 該当なし通知から経路条件調整（US10）への導線 | 中（US10 は IT5） |
| User-rep M2 | 確定前の確認ステップ、確定後のリルート導線維持 | 中 |
| User-rep L2 | 推奨順の並び基準の可視化（見出し/推奨バッジ） | 低 |
| Architect | sqlcgen 全スキーマ重複の返済（sqlc per-BC schema 分離） | 中（既存負債） |
| Programmer L-2 | 候補一覧→確定間の TOCTOU（候補ハッシュ突合）※リスク顕在時 | 低 |
| — | 経路探索の多段乗り継ぎ深掘り・費用モデル精緻化 | 中（段階実装の後続） |

## 品質ゲート

- 全テスト green（単体・統合 testcontainers・E2E Playwright）
- `make check`（build + test + lint + arch）PASS・`golangci-lint` 0 issues・`go-arch-lint` OK
- ドメイン層カバレッジ 90% 以上（routing 96.6%・booking 96.4%・estimation 91.5%・shared 95.8%）
- govulncheck: 脆弱性なし
- CI（Backend CI・go/take-1）: success
