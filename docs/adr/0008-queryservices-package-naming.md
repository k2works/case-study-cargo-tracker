# 0008 queryservices パッケージ命名規約を入出力 DTO 許容に拡張する

CQRS の Query 側パッケージ `application.queryservices` に、サービス実装（`*QueryService`）以外に入出力 DTO の同居を許容する。

日付: 2026-06-21

## ステータス

2026-06-21 提案されました（IT4 タスク 0.6）

## コンテキスト

IT2 で導入した ArchUnit ルール 4 は、`commandservices` パッケージのトップレベルを `*CommandService` か `*Command` の 2 種に限定する一方、`queryservices` 側は `*QueryService` のみを許容していた。

IT3 タスク 2.7（US08 経路候補算出）で `CalculateRouteCommand` / `SearchVoyageCommand` / `PricedRouteCandidate` を実装した際、これらの DTO は QueryService の入出力として queryservices/ に同居させたかったが、ArchUnit ルール 4 違反となり `application` 直下に逃がした。結果：

1. **対称性の欠如**: commandservices は「実装 + DTO」が同居、queryservices は「実装のみ + DTO 別置」となり、Query 側の凝集度が低下。
2. **import 文の複雑化**: Controller / Spec から QueryService と DTO を別パッケージ参照する必要があり、リファクタリング時の冗長性が目立つ。
3. **命名の不統一**: CQRS では Query 側でも「DTO 名は Command でも Query でも Result でも構わない（副作用なしを示すのはサービス命名で十分）」とする慣行が多い。

## 決定

### (a) ArchUnit ルール 4 の queryservices 側を以下に拡張

```scala
val queryRule = ArchRuleDefinition
  .classes()
  .that()
  .resideInAPackage("..cargotracker..application.queryservices..")
  .and()
  .areTopLevelClasses()
  .and(notScalaSynthetic)
  .should()
  .haveSimpleNameEndingWith("QueryService")
  .orShould().haveSimpleNameEndingWith("Query")
  .orShould().haveSimpleNameEndingWith("Command")
  .orShould().haveSimpleNameEndingWith("Result")
  .orShould().haveSimpleNameEndingWith("Candidate")
```

許容する命名サフィックス:

| サフィックス | 用途 | 例 |
| :--- | :--- | :--- |
| `*QueryService` | Query 側アプリケーションサービス（必須実装） | `RouteCandidateQueryService` |
| `*Query` | 入力 DTO（読み取り操作） | `ListVoyageQuery` |
| `*Command` | 入力 DTO（CQRS では Query パスでも実行コマンドと表現可） | `CalculateRouteCommand`, `SearchVoyageCommand` |
| `*Result` | 出力 DTO（結果のまとめ） | `RouteSearchResult` |
| `*Candidate` | Routing コンテキストで複数候補を返すケースの出力 DTO | `PricedRouteCandidate` |

### (b) `CalculateRouteCommand` / `SearchVoyageCommand` / `PricedRouteCandidate` を queryservices/ に戻す

`application/` 直下 → `application/queryservices/` に再配置。Controller / Spec の import を更新する。

### (c) commandservices 側は現状維持

commandservices は `*CommandService` / `*Command` のみで十分対称性が取れているため変更しない。

## 結果

### 利点

- Query 側の凝集度が向上し、commandservices と対称な構造になる
- Controller / Spec の import 文が短縮される
- 将来 Query 側で `*Result` 型を追加する際に追加の ADR を要しない

### 欠点

- 許容サフィックスが 5 種に増え、命名規約がやや緩む（ただしすべて CQRS 文献上の慣用句なので学習コストは低い）

## 代替案

### 代替案 1: queryservices 直下に dto/ サブパッケージを切る

`application/queryservices/dto/` を新設して DTO を分離する。命名規約は厳密に保てるが、サービスと DTO が階層的に分かれる利点は薄く、ファイル数 5 件で導入する価値が小さい。

### 代替案 2: queryservices ルール撤廃

ArchUnit ルール 4 の queryservices 部分を削除し、自由命名を許容する。命名統制が緩みすぎるため不採用。

## 関連

- ADR 0006 航海データモデル追補
- IT3 マルチパースペクティブレビュー高優先度 #6
- 実装: 本 ADR と同時に `CalculateRouteCommand` / `SearchVoyageCommand` / `PricedRouteCandidate` を queryservices/ に再配置
