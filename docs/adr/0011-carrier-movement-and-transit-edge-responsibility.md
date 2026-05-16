# ADR-0011: CarrierMovement と TransitEdge の責務分離

`routingms` に存在する `CarrierMovement`（既存）と `TransitEdge`（US08 PoC 新規）の責務を明確に分離し、将来の IT4 本実装で両者が混在・重複しないよう設計方針を定める。

日付: 2026-05-16

## ステータス

承認済み

## コンテキスト

IT3 US08 先行スパイクで `TransitEdge` を新規作成した結果、`routingms` に似た概念を持つ 2 つの値オブジェクトが共存することになった。

### CarrierMovement（既存）

```java
public record CarrierMovement(
        UnLocode departure,   // 型安全: UnLocode VO
        UnLocode arrival,     // 型安全: UnLocode VO
        LocalDateTime departureTime,
        LocalDateTime arrivalTime
) { /* 不変条件: arrival > departure */ }
```

- **所属**: `Voyage` Aggregate の構成要素（Write Side）
- **目的**: 運送会社が運航する 1 区間の運行スケジュール（コマンド側の永続化対象）
- **不変条件**: 自身でコンストラクタ検証（`null`・同一港・時刻順序）
- **特徴**: `UnLocode` 型を使用し型安全性がある。`acceptedCargoTypes` を持たない。

### TransitEdge（PoC 新規）

```java
public record TransitEdge(
        String voyageNumber,         // 型不安全: String
        String fromUnLocode,         // 型不安全: String
        String toUnLocode,           // 型不安全: String
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        List<String> acceptedCargoTypes  // 型不安全: List<String>
) {}
```

- **所属**: `OptimalRouteService`（PoC ドメインサービス）
- **目的**: 経路探索グラフのエッジ（Read Side のクエリ側で使用予定）
- **不変条件**: なし（PoC のため未実装）
- **特徴**: `String` 型で型安全性がない。航海番号・貨物種別を追加で保持する。

IT3 コードレビュー（`docs/review/us08_spike_review_20260516.md`）の M7 指摘:
「`CarrierMovement` と `TransitEdge` の責務関係が暗黙で将来の混乱を招く」

## 決定

### 役割の明確化

| 概念 | CarrierMovement | TransitEdge（IT4 での名称検討含む）|
|------|----------------|----------------------------------|
| 層 | Write Side（Aggregate VO） | Read Side（Query / 経路探索用 VO） |
| 所属 | `Voyage` Aggregate | `RouteCandidateFinder`（サービス） |
| 生成元 | `RegisterVoyageCommand` | `voyage` / `carrier_movement` Read Model |
| 型安全性 | `UnLocode` VO 使用 | IT4 で `UnLocode` VO へ移行 |
| 不変条件 | コンストラクタで検証 | IT4 で追加 |
| 貨物種別 | なし（Voyage 集約の外部仕様） | `Set<CargoType>` へ変更（IT4） |

### 設計方針

1. **CarrierMovement は Write Side の真実のまま維持する**
   - `Voyage` Aggregate の構成要素として変更しない
   - `UnLocode` VO の利用・不変条件検証を維持する

2. **TransitEdge は Read Side 専用の Query VO として再設計する**（IT4）
   - `CarrierMovement` のコピーではなく、経路探索に特化したプロジェクション
   - `Voyage.voyageNumber` + `CarrierMovement` から生成する Read Model への射影として位置づける
   - フィールドを IT4 で型安全化: `String` → `UnLocode`、`List<String>` → `Set<CargoType>`
   - 不変条件（null チェック・時刻順序・乗り継ぎ最小時間）をコンストラクタに追加

3. **`EdgeRepository` ポートを IT4 で定義する**（ADR-0010 参照）
   - `OptimalRouteService`（または `RouteCandidateFinder`）が `EdgeRepository` ポート経由で `TransitEdge` を取得する
   - `voyage` / `carrier_movement` テーブルの JOIN クエリを `EdgeRepository` の MyBatis 実装に閉じ込める
   - テスト時は `EdgeRepository` をモック化してドメインサービスをユニットテストできる

### `CarrierMovement` から `TransitEdge` への変換

```
voyage テーブル          carrier_movement テーブル
voyageNumber:V001  +  departure:JPYOK, arrival:TWKHH, ...
                   →  TransitEdge(voyageNumber="V001",
                                  fromUnLocode=JPYOK,
                                  toUnLocode=TWKHH,
                                  acceptedCargoTypes={GENERAL,HAZARDOUS})
```

この変換は `EdgeRepository` 実装（MyBatis Mapper）が担う。
ドメインサービスは `TransitEdge` のリストのみを扱い、`CarrierMovement` を直接参照しない。

## 却下した選択肢

### CarrierMovement に acceptedCargoTypes を追加して統合する

- **却下理由**: Write Side の Aggregate VO に Query Side の関心事（貨物種別フィルタ）を混入させると CQRS 境界が崩れる。`Voyage` Aggregate のドメインモデルに経路探索の関心事が流入する。

### TransitEdge を CarrierMovement の型エイリアスとして実装する

- **却下理由**: `voyageNumber`（`Voyage` Aggregate の ID）を持つという TransitEdge 固有の責務が失われる。`Voyage` Aggregate 境界の外から `CarrierMovement` を直接操作することになり、ADR-0004 の境界違反になる。

## 結果

- CQRS の Write/Read Side 境界が明確になる
- ドメインサービスが `CarrierMovement`（Aggregate の内部構造）に直接依存しなくなる
- IT4 で `EdgeRepository` ポートを定義することで、テスト可能性と DI 切り替えが実現できる
- 将来の Read Model 最適化（非正規化・キャッシュ）が `TransitEdge` 側に閉じて実施できる

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-16 | 初版作成（IT3 コードレビュー M7 対応） | AI Agent |
