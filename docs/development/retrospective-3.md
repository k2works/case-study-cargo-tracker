---
title: イテレーション 3 ふりかえり
description: IT3（経路算出・選択・US08/US09）の Keep・Problem・Try
published: true
date: 2026-07-22T00:00:00.000Z
---

# イテレーション 3 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 3（経路算出・選択） |
| **局面** | 中盤（インサイドアウト） |
| **計画 SP / 実績 SP** | 11 / 11（達成率 100%） |
| **対象ストーリー** | US08（経路候補算出）・US09（経路選択・確定） |
| **テスト** | domain-routing 単体 28 + app-routing 単体 9 + Repository 統合（CargoSpecProvider/SelectedRouteRepository/search）+ HTTP フロー 6＝全 green |
| **カバレッジ** | domain-routing 92.45% lines（route.rs 97.56%、cargo-llvm-cov、CI ゲート 85% を突破） |
| **実装コミット** | feat（domain/infra/app/web）+ test（受入基準実証）+ ci + docs |
| **成果** | 予約起点の経路設計フロー（`/bookings/{id}/route`）で DFS 経路探索→候補提示→確定・永続化の縦切りが実 PostgreSQL 上で成立。CI 品質ゲート導入・IT2 Try 全項目に着手・developing-review 完了 |

## Keep（継続すること）

### 技術的成功事項

- **DFS 経路探索の段階的実装**: 直行→単純接続→多段接続（max_legs=3）と段階的に組み上げ、サイクル回避（同一航海の再訪防止）・時系列連結（前区間の荷揚 ≤ 次区間の積載）・推奨順ソート（区間数→所要日数→期限内優先）を小さな単体テストで固めた。中盤局面のインサイドアウトが最難関ストーリーでも機能した。
- **BC 独立の徹底**: `RouteLeg`（Booking の `Leg` とは別の Routing 固有 VO）・`CargoType`（Routing 固有列挙）・`CargoSpecProvider`（Booking 非依存の ACL ポート）で、Routing が Booking を直接参照しない設計をコンパイラ（Cargo.toml 依存宣言）で強制した。計画時に `validating-iteration-plan` が Booking の `Leg` 流用を検知し、着手前に是正できた。
- **IT2 Try #2 の実践（search の SQL 化）**: `find_all` + Rust フィルタだった `search` を EXISTS 副問い合わせベースの SQL WHERE 絞り込み（origin=先頭区間の出発・destination=末尾区間の到着・貨物種別・出発期間）に作り直した。N+1 の解消に着手できた。
- **Arc ブランケット実装で DIP 維持**: ACL ポート（CargoSpecProvider/SelectedRouteRepository）を `Arc<dyn Trait>` の composition root 注入で結線し、ADR-0003 のパターンを踏襲した。
- **CI 品質ゲートの導入（IT2 Try #4）**: `.github/workflows/rust-ci.yml` に lint（fmt+clippy）・test・coverage（`cargo llvm-cov --fail-under-lines 85 -p domain-routing`）を組み込み、暫定ゲートを恒久化した。レガシー（domain-booking/shipper）除外理由もコメントで明文化した。

### プロセス的成功事項

- **IT2 Try #1 の実践（受入基準×テストケース 1:1）**: 計画時に受入基準をテストケースへ 1:1 対応させた対応表を作り、タスク分解の起点にした。
- **計画前の 3 検証**: `validating-iteration-plan` + `validating-design`（軸 A/B/C 横断整合）を着手前に実施し、BC 独立違反・IT2 設計反映漏れ（voyage 論理 ER の vessel_name/carrier 欠落）を事前検知できた。
- **developing-review の活用**: 中核実装完了後に 5 エージェント並列レビューを実施し、高優先度 3 件（0 件 web テスト・探索境界テスト・422 web テスト）をクローズ前に補完した。

## Problem（問題点）

### スコープ・計画の課題

- **受入基準対応表の「主張」と実テストの不一致**: 対応表は「0 件通知の web テスト」「探索境界テストで深さ上限固定」「route_confirm の 422」を主張していたが、初期実装では web/境界レベルで未実証だった（app 層で止まっていた）。Try #1（1:1 対応）を実践したが、対応表に書いた=実装した、を担保する仕組みがまだ弱い（レビューで tester が発覚、3 件補完）。
- **業務判断軸の欠落がレビューで再確認**: 費用列（Estimation 由来で Routing の RouteCandidate に無い）・期限超過候補（⚠）の選択可否・条件調整導線が未対応。多くは IT3 スコープ合意内だが、user-representative が「誤確定事故」リスク（期限超過候補が推奨順に混在し選択可能）を中優先度で指摘した。

### 技術的課題

- **plan_routes の find_all 全件取得**: `search` は SQL 化したが、経路探索本体（`plan_routes`）は依然 `find_all()` で全航海をロードしてから DFS する。多段接続のため origin 絞りは不可だが、最低限 `cargo_type` での SQL 絞り込みは可能で、航海数増でボトルネックになる（Try #2 は search のみ完了、探索本体は残課題）。
- **confirm_route の TOCTOU**: 表示時と確定時で `plan_routes` を再算出し index で引き当てるため、間に航海データが更新されると別候補を指しうる。IT3 要件では許容だが、候補同一性（航海番号列）の照合設計を後続で要検討（programmer/architect/technical-writer が独立指摘）。
- **相関副問い合わせの重複**: `search` の `(SELECT MIN/MAX(seq_number)...)` が 5 回繰り返され各行で再評価される。CTE（ROW_NUMBER/DISTINCT ON）化の余地（中優先度）。

## Try（次に試すこと）

| # | 改善アクション | 担当 | 期限 | 期待効果 |
|---|--------------|------|------|----------|
| 1 | 受入基準対応表に「実証テストのファイル:テスト名」列を設け、対応表とテストの存在をクローズ前に機械照合する（対応表=実装の担保） | 開発 | IT4 計画時 | 「主張と実テストの不一致」の再発防止（Problem 上位の根本対策） |
| 2 | `plan_routes` の探索対象取得を `cargo_type` で SQL 絞り込みし、find_all 全件ロードを解消する | 開発 | IT4 前半 | N+1 解消の完遂（IT2 Try #2 の残課題） |
| 3 | 期限超過候補（⚠）の選択不可化 or 確定時の明示確認を実装し、誤確定事故を防ぐ | 開発 | IT4 | 業務リスク（誤確定）の低減（user-rep 指摘 B） |
| 4 | confirm_route に候補同一性（航海番号列）照合を追加し TOCTOU を閉じる。設計判断は ADR 起票 | 開発 | IT4 | 表示候補と確定候補の一致保証 |
| 5 | 相関副問い合わせを CTE 化し `search` の可読性・性能を改善する | 開発 | IT4 以降 | DRY・スケール耐性 |

## 次イテレーション（IT4）への引き継ぎ

- **US10（経路条件調整・再算出）・US11（経路情報を予約に紐付け）・US12（確定経路の荷主通知）・US06（予約情報の経路設計者引き渡し）** が IT4 スコープ。US11 で `SelectedRouteRepository::exists`（現状 YAGNI で未使用）を活用し、確定経路を予約詳細に表示する（user-rep 指摘 D の解消）。
- **費用列の扱い**: RouteCandidate に費用が無いため、正式費用は Billing/Estimation 連携で後続対応。IT4 では列を「-」で用意する現状方針を維持しつつ、連携タイミングを検討する。
- **ADR 起票候補**: 経路探索アルゴリズムの範囲（内製 DFS + 外部 ExternalRoutingService 連携の後続化）・確定経路のスナップショット方針（selected_route が cargo への FK を持たない BC 独立判断）・CI カバレッジゲートの段階拡大（レガシー返済計画とゲート拡大条件）を IT4 で正式起票する。
- **infra-eventbus 骨格**: Booking/Tracking 連携が始まる IT4-5 で完成させる方針を維持する。

## 数値指標

| 指標 | 実績 |
|------|------|
| テストカバレッジ（domain-routing） | 92.45% lines（route.rs 97.56%、CI ゲート 85% 突破） |
| 全テスト | 全 green（domain-routing 28 / app-routing 9 / Repository 統合 / HTTP フロー 6・ワークスペース全体 green） |
| ビルド・Lint | ワークスペース clippy `-D warnings` クリーン・fmt 準拠・CI パイプライン稼働 |
| ベロシティ | 11 SP（IT1=16 → IT2=11 → IT3=11、計画ラインと一致し安定） |

## 関連ドキュメント

- [イテレーション 3 計画](./iteration_plan-3.md)
- [IT3 開発成果物レビュー](../review/it3_development_review_20260722.md)
- [イテレーション 2 ふりかえり](./retrospective-2.md)
