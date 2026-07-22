---
title: IT3 開発成果物レビュー - 経路算出・選択
description: IT3（US08/US09）実装のマルチパースペクティブレビュー（xp-programmer/tester/architect/technical-writer/user-representative）
published: true
date: 2026-07-22T00:00:00.000Z
tags: review, development, it3, routing, rust
---

# IT3 開発成果物レビュー - 経路算出・選択

## レビュー対象

- domain-routing（RouteLeg・RouteCandidate・RouteCandidateCalculator 経路探索、CargoSpecProvider/SelectedRouteRepository ポート）
- app-routing（RoutePlanningService: plan_routes/confirm_route）
- infra-persistence（SqlxCargoSpecProvider・SqlxSelectedRouteRepository・search の SQL 化）
- interface-web（経路設計・割り当て画面 route_design/route_confirm）
- CI（.github/workflows/rust-ci.yml）
- テスト（domain 8 + app 4 + infra 統合 + web フロー 4）

## 総合評価

IT3 の実装はヘキサゴナル + DDD の層分離、BC 独立性（RouteLeg/CargoType の Routing 固有型化・CargoSpecProvider ACL の Booking 非依存）、Arc ブランケット実装（ADR-0003）、DFS 経路探索（max_legs 上限・サイクル回避・時系列連結）を高水準で満たす。5 視点すべてが「設計の正に対して整合性が高く、動くきれいなコード」と評価。重大な設計欠陥はない。一方、**(1) 探索境界のテスト漏れ（同時刻接続の等号境界・max_legs 上限超過・サイクル）、(2) 0 件通知の web テスト欠落（受入基準対応表の主張と不一致）、(3) 業務要件（費用列・期限超過候補の選択可否・条件調整導線・確定経路の予約詳細表示）、(4) plan_routes の find_all 全件取得と confirm_route の再算出 TOCTOU** が共通指摘。多くは IT3 スコープ合意内だが、テスト漏れ 2 件は受入基準の実証に関わる。

## 改善提案（重要度順）

### 高（IT3 クローズ前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | 経路 0 件時の web テスト追加（seed が常に直行便を投入するため未実証） | route_flow_test.rs | tester, user-rep | US08 受入 6「期限内に到達可能な経路がない場合の通知」が web レベルで未実証。受入基準対応表は主張するが実テストが無い |
| 2 | 探索境界テスト追加（同時刻接続の等号成立・max_legs 上限超過の打ち切り・同一航海サイクル回避） | route.rs | tester | 対応表のリスク欄が「探索深さ上限をテストで固定」と明言するが未実装。境界値分析の `==` と上限が抜けている |
| 3 | route_confirm の 422（範囲外候補拒否）の web テスト追加 | route_flow_test.rs | tester | 対応表 US09 異常系「route_confirm の 422」の主張と実テスト不一致（app 層で止まっている） |

### 中（対応推奨・一部 IT4）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 4 | `plan_routes` の `find_all()` 全件取得を最低限 `cargo_type` で SQL 絞り込み（多段のため origin 絞りは不可） | app-routing/lib.rs:319 | programmer, architect | search を SQL 最適化したのに経路探索で活かされず、航海数増でボトルネック |
| 5 | 推奨順ソートの第2（所要日数）・第3（期限内優先）キーの分離検証テスト | route.rs:213 | tester | `直行便は経由便より上位` は第1キーしか差がつかない。期限内優先が第3キーの妥当性を受入基準と突合 |
| 6 | `confirm_route` の確定候補の中身検証（fake が booking_id のみ記録し RouteCandidate を捨てている） | app-routing/lib.rs, テスト | tester | US09 受入 2「最適候補を選択」の実質は選ばれた候補の中身。voyage_numbers を記録・アサートする fake に強化 |
| 7 | ui_design.md に費用列（Routing の RouteCandidate に無く Estimation 由来）・0 件メッセージの書き分け注記 | docs/design/ui_design.md | technical-writer | 「経路 0 件」と「期限内 0 件」の書き分け不足、費用列の由来注記不足 |
| 8 | 相関副問い合わせ `(SELECT MIN/MAX(seq_number)...)` の CTE 化（DRY・可読性） | voyage_repository.rs:199 | programmer | 5 回繰り返され各行で再評価。ROW_NUMBER/DISTINCT ON で先頭/末尾 movement を CTE 化 |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| 9 | Schedule に `first_movement()`/`last_movement()` アクセサを設け RouteLeg::from_voyage の添字 panic 前提を解消 | route.rs:29 | programmer |
| 10 | `i32::try_from(seq+1).unwrap_or(i32::MAX)` を `.expect()` に（黙って壊れたデータを保存しない） | selected_route_repository.rs:63, voyage_repository.rs:146 | programmer |
| 11 | transit_days の同日到着（0）・月跨ぎの境界テスト | route.rs:147 | tester |
| 12 | 貨物種別ラベルの表記ゆれ確認（GENERAL_CARGO vs GENERAL） | 各所 | technical-writer |

## 業務視点の指摘（user-representative・多くは IT4 スコープ）

| # | 指摘 | 対応方針 |
|---|------|----------|
| A | 費用列の欠落（US08 受入 3・選択の主要判断軸） | 列を「-」で用意。正式費用は Billing/Estimation 連携（後続）。IT3 リスク表で合意済み |
| B | 期限超過候補（⚠）が推奨順に混在し選択可能（誤確定事故） | 選択不可化 or 明示的な確認を IT4 で検討 |
| C | 0 件時・最適候補なし時の条件調整導線が文言のみ（US10 は IT4） | US10（条件調整・再算出）を IT4 で実装（計画済み境界） |
| D | 確定後の予約詳細に確定経路が出ない | US11（IT4）で Cargo への紐付け・RouteProposed 反映時に表示 |

## ADR 起票推奨（architect）

- **経路探索アルゴリズムの範囲**: 内製 DFS + 外部 ExternalRoutingService 連携の後続化（計画の ADR-XXXX を正式起票）
- **確定経路のスナップショット方針**: selected_route が cargo への FK を持たない（BC 独立・疎結合）判断
- **CI カバレッジゲートの段階拡大**: domain-routing 絞り込みはストラングラーフィグ的段階改善として妥当。レガシー（domain-booking/shipper）の返済計画とゲート拡大条件を明文化

## 懸念事項（共通）

- **confirm_route の TOCTOU**: 表示時と確定時で `plan_routes` を再算出し index で引き当てるため、間に航海データが更新されると別候補を指しうる。IT3 要件では許容だが、確定時に候補同一性（航海番号列）を照合する設計を IT4/ADR で検討（programmer, technical-writer, architect が独立指摘）
- **SelectedRouteRepository::exists の未使用**: US11（IT4）で使う前提。現時点は YAGNI

## 高優先度指摘への対応方針

| # | 指摘 | 対応方針 |
|---|------|----------|
| 1-3 | 受入基準の実証漏れ（0 件 web・探索境界・422 web） | **IT3 クローズ前に対応**。テストを追加し受入基準を実証する |
| 4-8 中 | N+1・ソート検証・確定候補検証・ui_design・SQL CTE | N+1 と ui_design は本 IT で対応可、他は IT4/後続に整理 |
| A-D 業務 | 費用・期限超過選択・条件調整・確定表示 | IT3 スコープ合意（自動算出のみ）。B は誤確定防止として IT4 優先検討 |

## エージェント別サマリー

- **xp-programmer**（高 0 / 中 2 / 低 4）: BC 独立・DFS の正しさ・エラー設計を高評価。find_all の N+1・相関副問い合わせの DRY・confirm の TOCTOU を指摘
- **xp-tester**（重要 2 / 中 3 / 低 1）: ピラミッド良好。等号境界・max_legs/サイクル・0 件 web・422 web のテスト漏れと対応表不一致を指摘
- **xp-architect**（中 2 + ADR 3）: 設計整合性が高く分割は自然。ソート第3キー・find_all・ADR 起票（探索範囲・スナップショット・ゲート拡大）を推奨
- **xp-technical-writer**: rustdoc/設計反映は高水準。ui_design の費用列・0 件書き分け・confirm TOCTOU のドキュメント担保を指摘
- **xp-user-representative**: 中核導線は使える水準。費用列・期限超過選択可否・条件調整導線・確定経路表示の 4 点を後続で手当て推奨

## 更新履歴

| 日付 | 更新内容 |
|------|---------|
| 2026-07-22 | 初版作成（5 エージェント並列レビュー統合） |
