---
title: イテレーション 3 ふりかえり
description: IT3（輸送見積・予約引き渡し・航海スケジュール検索・US08 先行スパイク）の KPT ふりかえり
published: true
date: 2026-05-16T00:00:00.000Z
---

# イテレーション 3 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 3 / 8 |
| **期間** | 2026-06-11 〜 2026-06-24（計画）／ 2026-05-15 〜 2026-05-16 で前倒し完了 |
| **ゴール** | 輸送見積（US01）・予約引き渡し（US06）・航海スケジュール検索（US07）・既存航海スケジュール更新（US25）と IT2 持越し品質基盤（PIT / ドキュメント）を完了させる |
| **計画 SP / 実績 SP** | 16 / 16（達成率 100%） |

---

## Keep（続けること）

### K1: US08 先行スパイク（PoC）の計画的実施

IT3 バッファ枠（4h）で US08 経路候補算出の PoC を完了させた。`OptimalRouteService`（DFS 全経路列挙）・`TransitEdge` / `TransitPath` / `RouteSearchSpecification` の 4 クラスを新規作成し、6 つのドメイン不変条件（直行・経由・期限・貨物種別・寄港地連続性・乗り継ぎ時間）を実行可能仕様として固定した。IT4 への技術的基盤を SP に計上せず確保できた。

### K2: SonarQube Quality Gate PASS を維持

IT3 完了時点で Backend new_coverage 87.9%、violations 0、hotspots 100%、duplications 2.02% で Quality Gate PASS を達成した。特に `QuotationProjectionsEventHandler`（テストカバレッジ 8% → 87%）を `QuotationProjectionsEventHandlerTest`（4 テスト）で補強したことで、IT2 の「正面突破」方針（K4）を IT3 でも継続できた。

### K3: Checkstyle + SonarQube の規律強化

IT3 では `NeedBraces`（`continue` にはブレース必須）・`LeftCurly`（`{` 後改行必須）・Cognitive Complexity (≤ 15) など複数の静的解析規則違反を `dfs()` の `isEligible()` / `explore()` メソッド抽出でゼロに抑えた。Code Smell 14 件を IT3 内で解消し、技術的負債を持ち越さないパターンを維持した。

### K4: ArgumentCaptor パターンによる投影イベントハンドラのユニットテスト

`QuotationProjectionsEventHandler` のテストで Mockito `ArgumentCaptor` を使い、`mapper.insertQuotation()` / `mapper.insertCandidate()` の引数を直接検証する方法を定着させた。MyBatis Mapper のモック + キャプチャパターンは他の投影ハンドラにも応用できる。

### K5: 多視点コードレビューの定例化

IT3 完了時に 5 エージェント並列レビュー（programmer / tester / architect / writer / user-rep）を実施し、H8 件・M10 件・L4 件の改善提案を `docs/review/us08_spike_review_20260516.md` として記録した。レビュー指摘が IT4 の着手前チェックリストとして次イテレーション計画に直接つながる形になっている。

### K6: ADR 先行完了による IT3 着手リスク解消

IT2 retrospective T3 で「ADR-0009 の Subscribing → Pooled 確定」を課題として挙げ、IT3 着手前に解消済み（commit `18df5932`）。ADR ベースのリスク先行対処が機能し、IT3 中に同じ問題でブロックされることはなかった。

---

## Problem（問題点）

### P1: BFS / Dijkstra / DFS の命名混乱

`OptimalRouteService` のクラス Javadoc は「BFS」、テストの `@DisplayName` は「Dijkstra」と記述しているが、実装は DFS（深さ優先全経路列挙）であることがレビューで判明した（H1）。PoC 段階でアルゴリズム名を定めないまま実装を進めたため、IT4 担当者が「BFS 実装済み」と誤認してリライト判断をスキップするリスクが残っている。

### P2: PoC と本実装の境界が曖昧

`OptimalRouteService` が PoC か本実装かの判断基準（捨てる / プロモートする）を明文化しないままスパイクを終えた（H7）。アーキテクトレビューが指摘した通り、スパイクが無審査で本実装に昇格するアンチパターンを防ぐための ADR または IT3 完了報告への追記が必要。

### P3: ドメイン型の文字列代替

`TransitEdge` の `fromUnLocode` / `toUnLocode` は `String` で実装しており、bookingms の `Location`（`UnLocode` VO）と型レベルで乖離している（H8）。Checkstyle / SonarQube は通過するが、バリデーション漏れが潜伏している。

### P4: 乗り継ぎ最小時間が業務要件を満たさない

現状の `hasValidTransfer()` は「到着 < 出発」のみを検証し、1 分乗り継ぎも許容している（H4）。ユーザー代表レビューで「最低 24h の乗り継ぎ時間」が業務要件として挙がったが、`RouteSearchSpecification` に制約として追加されていない。

### P5: IT3 計画と実施期間の大きな乖離

計画期間は 2026-06-11 〜 2026-06-24 だったが、実際は 2026-05-15 〜 2026-05-16 の 2 日間で完了した。ベロシティ試算の前提（1 日 4 理想時間 × 10 日 = 40 理想時間）と実際の実施時間に大きな差があり、リリース計画のスケジュール精度評価が難しくなっている。

---

## Try（次に試すこと）

### T1: IT4 着手前に PoC 処理方針を ADR に記録する

`OptimalRouteService` を「捨てる（ゼロから再実装）」か「プロモートする（型安全化・アルゴリズム改善して本実装化）」かを IT4 第 0 スプリント（着手前 1 日）で合意し ADR に記録する。テスト（実行可能仕様）は残し、実装は IT4 でゼロから書き直す方針を推奨（レビュー矛盾事項 2 の推奨判断）。

### T2: Javadoc / コメントのアルゴリズム名統一

IT4 着手時に `OptimalRouteService.java:13` と `OptimalRouteServiceTest.java:17` の BFS/Dijkstra 記述を「DFS による全経路列挙（PoC）、IT4 で評価関数導入予定」に統一する。レビュー H1 を IT4 第 0 スプリントの最初のタスクとする。

### T3: CarrierMovement と TransitEdge の責務 ADR 起票

`routingms` の `CarrierMovement`（既存）と `TransitEdge`（新規 PoC）の責務分担が暗黙のままである（M7）。IT4 着手前に ADR を起票し、両者の役割（Read Model vs. Domain VO）を明示する。

### T4: US08 ストーリー詳細化でユーザーと合意

「乗り継ぎ最小時間（24h 等）」「候補上限」「期限の解釈（絶対デッドライン vs. 希望到着日）」「候補 0 件時の代替案提示」（H4, H5, H6）を IT4 計画時にユーザー代表と合意してから `RouteSearchSpecification` に反映する。

### T5: 機械的リファクタリングの計画化

`List<String>` → `Set<CargoType>` の型安全化（レビュー H8, M5）は機械的なリファクタリングであるため、IT4 タスクリストの先頭に明示的に追加し「実装より先に型を正す」順序を守る。

---

## IT4 への申し送り事項

### 持越しタスク

| タスク | 元 ID | SP 影響 |
|--------|--------|---------|
| US04-r1 / US05-r1 / US24-r1 業務的入力検証（IT3 起票のみ） | IT2 retrospective T4 | 実装は IT4 以降、SP 見積必要 |
| Axon Server 停止時 smoke test（ADR-0009 regression 防止） | IT3 成功基準未完了 | バッファ枠（2h） |
| PoC 処理方針の ADR 起票 | レビュー H7 | 第 0 スプリント必須 |
| Javadoc BFS/DFS/Dijkstra 命名統一 | レビュー H1 | 第 0 スプリント必須 |
| CarrierMovement / TransitEdge 責務 ADR 起票 | レビュー M7 | 第 0 スプリント必須 |
| `List<String>` → `Set<CargoType>` 型安全化 | レビュー H8, M5 | IT4 タスク先頭 |

### IT4 で注意すべきリスク

1. **PoC 昇格のアンチパターン**: `OptimalRouteService` PoC をレビューなしに本実装化すると、String 型・O(|E|^d) 計算量・アルゴリズム名乖離が本実装に引き継がれる。IT4 第 0 スプリントで明示的に捨てる / プロモートするかを合意すること。
2. **隣接リスト未使用の計算量爆発**: `edges` 全件走査は carrier_movement が増えると指数的に悪化する（H3）。IT4 本実装では `Map<String, List<TransitEdge>>` の隣接リスト構造に変更する。
3. **乗り継ぎ最小時間の業務未合意**: 現状は 1 分乗り継ぎも許容（H4）。IT4 で実際の shipping ドメイン要件（一般的に 24h）を確認してから実装すること。
4. **`OptimalRouteService` 命名**: 「Optimal（最適化）」を行っていないため `RouteCandidateFinder` への名称変更を IT4 で検討（M8）。

### 申し送りメモ

- IT3 ふりかえり結果は K6 件・P5 件・T5 件。Keep が前回同数、Problem が 1 件減。品質基盤の定着が進んでいる。
- US08 PoC のテスト（実行可能仕様）は IT4 本実装の出発点として価値が高い。実装は捨てても仕様は残す。
- IT3 の最大の成果は「6 つのドメイン不変条件を実行可能仕様として固定した」こと。IT4 の設計議論の基盤になる。

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-16 | 初版作成（IT3 完了処理の一部として） | AI Agent（XP PM） |
