# ADR-0010: US08 先行スパイク（PoC）の処理方針

IT3 バッファ枠で実施した US08 経路候補算出 PoC（`OptimalRouteService` 等）を
IT4 本実装に向けて「テストは残し、実装はゼロから書き直す」方針に決定する。

日付: 2026-05-16

## ステータス

承認済み

## コンテキスト

IT3 では US08 経路候補算出の技術リスクを解消するため、4h タイムボックスの先行スパイクを実施した。
以下の成果物を作成した。

| ファイル | 種別 |
|---------|------|
| `routingms/domain/services/OptimalRouteService.java` | 実装（PoC） |
| `routingms/domain/model/valueobjects/TransitEdge.java` | 値オブジェクト（PoC） |
| `routingms/domain/model/valueobjects/TransitPath.java` | 値オブジェクト（PoC） |
| `routingms/domain/model/valueobjects/RouteSearchSpecification.java` | 値オブジェクト（PoC） |
| `routingms/domain/services/OptimalRouteServiceTest.java` | テスト（6 件、実行可能仕様） |

IT3 コードレビュー（`docs/review/us08_spike_review_20260516.md`）で、以下の問題が高優先度指摘として挙がった。

1. **命名の混乱**（H1）: クラス Javadoc が「BFS」、テスト Javadoc が「Dijkstra」だが実装は DFS
2. **計算量爆発リスク**（H3）: `edges` 全件走査（O(|E|^d)）は carrier_movement 増加で指数的に悪化
3. **型安全性の欠如**（H8）: `fromUnLocode` / `toUnLocode` が `String` でドメイン型（`UnLocode`）と乖離
4. **乗り継ぎ最小時間の未実装**（H4）: 1 分乗り継ぎも許容
5. **候補 0 件時の未定義動作**（H5）: 空リスト返却のみで代替案提示なし

これらの問題に対し、以下の 2 択を検討した。

### 選択肢 A: 実装をそのままプロモートし IT4 でリファクタリング

- **メリット**: 動作するコードが残るため着手が早い
- **デメリット**: String 型・O(|E|^d) 計算量・命名乖離が本実装に引き継がれる。リファクタリングで「壊す / 壊さない」の判断が複雑になる。テストが実装の内部構造に依存しがちで劣化しやすい。

### 選択肢 B: テストは残し、実装はゼロから書き直す（推奨）

- **メリット**: テスト（実行可能仕様）が IT4 設計の契約として機能する。実装をゼロから書くことで型安全性・計算量・命名をクリーンに設計できる。PoC の問題が本実装に持ち越されない。
- **デメリット**: 実装の再構築に工数が必要

コードレビューの矛盾事項 2（tester vs. architect）でも「テストは残し、実装は IT4 でゼロから書き直す」が推奨判断とされている。

## 決定

**選択肢 B を採用する**: テスト（`OptimalRouteServiceTest.java`）は実行可能仕様として存続させ、実装（`OptimalRouteService.java` および関連 VO）は IT4 でゼロから設計・実装し直す。

### IT4 本実装での具体的な変更事項

| 項目 | PoC の現状 | IT4 での対応 |
|------|-----------|------------|
| アルゴリズム | DFS 全列挙（O(\|E\|^d)） | 評価関数付き探索（Dijkstra / A* / BFS 選択）を設計時に確定 |
| グラフ表現 | `List<TransitEdge>` 全件走査 | `Map<String, List<TransitEdge>>` 隣接リスト |
| 型安全性 | `String fromUnLocode` | `UnLocode` 値オブジェクト（bookingms と統一） |
| 貨物種別 | `List<String> acceptedCargoTypes` | `Set<CargoType>` に変更 |
| 乗り継ぎ制約 | 到着 &lt; 出発のみ | 最小乗り継ぎ時間（24h 等）をユーザーと合意の上 `RouteSearchSpecification` に追加 |
| 候補 0 件 | 空リスト | 代替案提示を仕様化 |
| 候補比較軸 | なし | 所要日数・到着日・経由数を `TransitPath` に追加 |
| クラス名 | `OptimalRouteService`（Optimal と実装が乖離） | `RouteCandidateFinder` への改名を検討 |
| DI 方式 | コンストラクタ直接注入 | Spring Bean（`@Service`）+ `EdgeRepository` ポート経由に変更 |

### テストの扱い

- `OptimalRouteServiceTest.java` の 6 テストは変更せず IT4 本実装の受け入れ基準として使用する
- IT4 で本実装が `OptimalRouteServiceTest` をすべてパスすることを PR の完了条件とする
- 追加テスト（M1〜M3: 境界値・異常系・REFRIGERATED）は IT4 で補強する

## 結果

- スパイクが無審査で本実装に昇格するアンチパターンを防ぐ
- IT4 担当者が「BFS で実装済み」と誤認してリライト判断をスキップするリスクを排除する
- テスト（仕様）と実装の独立性を確保し、テストが壊れにくい状態を維持する

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-16 | 初版作成（IT3 ふりかえり H7 対応） | AI Agent |
