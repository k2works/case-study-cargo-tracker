---
title: US08 先行スパイク コードレビュー
date: 2026-05-16
target: IT3 バッファ枠 US08 先行スパイク（BFS 経路候補算出 PoC）
---

# コードレビュー結果

## レビュー対象

| ファイル | 種別 |
|---------|------|
| `routingms/domain/services/OptimalRouteService.java` | 新規（PoC）|
| `routingms/domain/model/valueobjects/TransitEdge.java` | 新規（PoC）|
| `routingms/domain/model/valueobjects/TransitPath.java` | 新規（PoC）|
| `routingms/domain/model/valueobjects/RouteSearchSpecification.java` | 新規（PoC）|
| `routingms/domain/services/OptimalRouteServiceTest.java` | 新規（6 テスト）|
| `bookingms/interfaces/events/QuotationProjectionsEventHandlerTest.java` | 新規（4 テスト）|
| `routingms/interfaces/rest/VoyageControllerIntegrationTest.java` | 追加（4 テスト）|

## 総合評価

PoC として「BFS/Dijkstra ベース経路探索の可能性検証」という目的は達成しており、6 つのドメイン不変条件（直行・経由・期限・貨物種別・寄港地連続性・乗り継ぎ時間）を実行可能仕様として固定した点は高く評価できます。SonarQube Quality Gate も PASS（カバレッジ 87.9%、violations 0）。一方で、クラス名・Javadoc・実装アルゴリズムの三者不一致（BFS/Dijkstra/DFS の混同）という致命的なドキュメント欠陥と、IT4 本実装に向けて設計上の再考が必要な点（型安全性・計算量・ユーザー利用観点）が複数存在します。

---

## 改善提案（重要度順）

### 高（IT4 着手前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | Javadoc/コメントのアルゴリズム名を「DFS による全経路列挙」に統一（BFS/Dijkstra の混同を解消） | `OptimalRouteService.java:13`, `OptimalRouteServiceTest.java:17` | programmer / writer | IT4 担当者が「BFS で実装済み」と誤認してリライト判断をスキップするリスク |
| H2 | `isVisited` の訪問判定を `Set<String> visitedPorts` に変更 | `OptimalRouteService:isVisited` | programmer | `fromUnLocode` の判定が過剰で意図不明確、O(n) → O(1) に改善 |
| H3 | `edges` 全件走査を隣接リスト `Map<String, List<TransitEdge>>` に変更 | `OptimalRouteService:dfs` | programmer | carrier_movement が増えると O(\|E\|^d) に爆発する |
| H4 | 乗り継ぎ最小時間（既定 24h）を制約として追加 | `RouteSearchSpecification` / `hasValidTransfer` | user-rep | 現状は「到着 < 出発」のみで 1 分乗り継ぎも許容 |
| H5 | 候補 0 件時の挙動（代替案の提示）を仕様化 | `OptimalRouteService:findCandidates` | user-rep | 経路設計者が次アクションを取れない |
| H6 | 候補の比較軸（所要日数・到着日・経由数）を `TransitPath` に追加 | `TransitPath.java` | user-rep / programmer | UI で候補を比較・ソートするために必要 |
| H7 | IT4 着手時に PoC「捨てる/プロモート」の方針を明文化（ADR または IT3 完了報告への追記） | `iteration_plan-3.md` | architect | スパイクが無審査で本実装に昇格するアンチパターンを防ぐ |
| H8 | ドメイン型（`UnLocode`, `VoyageNumber`, `CargoType`）への型置換をリファクタリングとして計画 | 全 TransitEdge / RouteSearchSpecification | architect | `String` によるバリデーション回避が不変条件を潜伏リスクに |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M1 | `arrivalDeadline` 境界値テスト（当日・翌日・前日）を追加 | `OptimalRouteServiceTest` | tester | 「以前/以下/未満」の混同を防ぐ |
| M2 | 異常系テスト（経路なし・ループグラフ・null 入力）を追加 | `OptimalRouteServiceTest` | tester | 無効同値クラスが空白 |
| M3 | `REFRIGERATED` 貨物種別のテストを追加 | `OptimalRouteServiceTest` | tester | 3 値のうち 2 値しか検証されていない |
| M4 | `TransitPath` に振る舞い（`totalDuration()`, `legCount()`, `arrivalAt()`）を追加 | `TransitPath.java` | programmer | Train Wreck（`p.edges().get(0).voyageNumber()`）を解消 |
| M5 | `acceptedCargoTypes` を `List<String>` → `Set<CargoType>` に変更 | `TransitEdge.java` | programmer | 型安全性と O(1) 判定の実現 |
| M6 | 経由数上限（`maxTransfers`）を `RouteSearchSpecification` に追加 | `RouteSearchSpecification` | user-rep | 組み合わせ爆発を防ぎ、業務的に不合理な長距離経由を除外 |
| M7 | `CarrierMovement` と `TransitEdge` の責務関係を ADR に記録 | 設計ドキュメント | architect | 両者の役割が暗黙で将来の混乱を招く |
| M8 | `OptimalRouteService` → `RouteCandidateFinder` への命名変更を IT4 で検討 | `OptimalRouteService.java` | programmer / user-rep | 「Optimal（最適化）」を行っておらず、名称と実装が乖離 |
| M9 | `VoyageControllerIntegrationTest` の `危険物貨物種別で絞り込める` に `status().isOk()` を追加 | `VoyageControllerIntegrationTest.java` | tester | 500 エラーでも `doesNotExist()` が通過するリスク |
| M10 | PoC の制約と IT4 への引き継ぎ事項を Javadoc に明記 | `OptimalRouteService.java` | writer | IT4 担当者への引き継ぎ情報として必要 |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| L1 | テストの期限基準日を定数化（2099 固定 → `BASE_DATE` 相対） | `OptimalRouteServiceTest` | tester | 将来のロジック変更時の安定性 |
| L2 | 候補返却の順序保証（到着日昇順）をテストで検証 | `OptimalRouteServiceTest` | user-rep | 画面表示時の再現性に必要 |
| L3 | `RouteCandidate` の位置引数 Builder 化 | テスト全般 | programmer | 第一引数 `14` が何を意味するか不明 |
| L4 | `EdgeRepository` ポートを定義して IT4 の DI 切り替えを準備 | `OptimalRouteService` | programmer | コンストラクタ注入方式は Spring Bean 化時に変更が必要 |

---

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| 1 | **programmer**: `isVisited` の `from` 判定は過剰 | **architect**: 仕様として「同一港再訪禁止」の設計意図は妥当 | 実装の正確さ vs 仕様の適切さ | IT4 で明示的に仕様化した上で実装を修正（仕様先行） |
| 2 | **tester**: PoC のテストを本実装の出発点にすると劣化リスク | **architect**: テストは実行可能仕様として価値がある | スパイクコードの扱い | テスト（仕様）は残し、実装は IT4 でゼロから書き直す |

---

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer（高: 8 / 中: 6 / 低: 4）</summary>

**評価サマリー**: PoC として意図と構造が読み取りやすく、責務分離も妥当な実装。ただし「BFS」と名乗りつつ実装は DFS であること、訪問済み判定のセマンティクスに欠陥があること、本実装に向けてグラフ表現とドメインモデルの再設計が必要な点が課題。

**主な指摘**:
- アルゴリズム名 BFS/DFS 混同（高）
- `isVisited` の O(n) 線形走査と意図の不明確さ（高）
- `edges` 全件走査による計算量爆発リスク（高）
- deadline チェックが goal 判定内だけで枝刈り不足（中）
- `acceptedCargoTypes` を `Set<CargoType>` に変更（中）
- `TransitPath` に振る舞いを追加（中）
- 命名 `OptimalRouteService` と実装の乖離（低）
</details>

<details>
<summary>xp-tester（高: 3 / 中: 4 / 低: 1）</summary>

**評価サマリー**: TDD サイクルに沿って書かれ、不変条件（寄港地連続性・乗り継ぎ時間）をプロパティ的に検証する設計が秀逸。境界値テストの欠如・固定値依存など本実装に向けて強化すべき点が複数あり。

**主な指摘**:
- 到着期限の境界値テスト（当日・翌日・前日）が欠落（高）
- 異常系・エッジケース（経路なし・null・循環）が未網羅（高）
- 候補順序・件数上限が未検証（高）
- `REFRIGERATED` 貨物種別テスト欠落（中）
- テストデータの定数化・意味付け（中）
- 統合テストの HTTP ステータス検証漏れ（中）
</details>

<details>
<summary>xp-architect（高: 3 / 中: 3 / 低: 1）</summary>

**評価サマリー**: PoC として IT4 での問題を炙り出すスパイク目的は達成。ドメインモデル設計書との型乖離・アルゴリズム名の不一致・PoC と本実装の境界の曖昧さが技術的負債蓄積のリスク。

**主な指摘**:
- IT4 着手時に「捨てる/プロモート」の方針を明文化（高）
- 設計書と PoC の型乖離（`String` vs 値オブジェクト）を解消（高）
- アルゴリズム名（BFS/Dijkstra/DFS）の不一致（高）
- コンストラクタ注入方式の IT4 での変更予定（中）
- `CarrierMovement` と `TransitEdge` の責務関係を ADR に記録（中）
- `isVisited` の判定が過剰（中）
</details>

<details>
<summary>xp-technical-writer（高: 1 / 中: 2 / 低: 2）</summary>

**評価サマリー**: PoC 目的・スコープを明示する Javadoc がある点は良い。Javadoc に書かれたアルゴリズム名と実装が一致していないという致命的な欠陥あり。

**主な指摘**:
- Javadoc と実装のアルゴリズム名乖離（BFS/Dijkstra/DFS）（高）
- PoC の制約と IT4 への引き継ぎ事項を Javadoc に追記（中）
- `isVisited` の命名と挙動の乖離（中）
- レコード型のコメントなしは CLAUDE.md 方針に合致（低）
- README/ドキュメント更新は IT4 本実装時で良い（低）
</details>

<details>
<summary>xp-user-representative（高: 3 / 中: 3 / 低: 2）</summary>

**評価サマリー**: 経路探索の骨格（直行/経由・期限・貨物種別・連続性・乗り継ぎ）が PoC として最小限カバーされており足場として妥当。経路設計者が実務で「使える」レベルには候補比較・0 件挙動・乗り継ぎ最小時間が不足。

**主な指摘**:
- 乗り継ぎ最小時間（24h 等）を制約として追加（高）
- 候補 0 件時の代替案提示を仕様化（高）
- 候補の比較軸（所要日数・到着日）を `TransitPath` に追加（高）
- 経由数上限（`maxTransfers`）追加（中）
- 期限の解釈（絶対デッドライン vs 希望到着日）の業務仕様確認（中）
- 冷凍+危険物の複合 CargoType 対応の将来検討（中）
</details>

---

## IT4 着手前チェックリスト

以下を IT4 の第 0 スプリントとして実施することを推奨します。

- [ ] Javadoc の BFS/Dijkstra/DFS 混用を「DFS による全経路列挙（PoC）、IT4 で評価関数導入予定」に統一
- [ ] IT3 完了報告または ADR に「PoC コードの扱い（捨てる/プロモート）」の方針を記録
- [ ] `CarrierMovement` と `TransitEdge` の責務分離 ADR の起票
- [ ] US08 ストーリー詳細化で「乗り継ぎ最小時間」「候補上限」「期限の解釈」をユーザーと合意
- [ ] `List<String>` → `Set<CargoType>` の型安全化（機械的リファクタリング）
</content>
</invoke>