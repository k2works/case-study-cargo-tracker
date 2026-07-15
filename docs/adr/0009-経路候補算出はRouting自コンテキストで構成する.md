# ADR-0009: 経路候補算出は Routing Context が自コンテキストの Voyage スケジュールから構成する

US08 の経路候補算出を、外部経路サービス（`ExternalRoutingServicePort`）への委譲ではなく、Routing Context が自ら保有する Voyage スケジュールから接続経路を構成するドメインサービスとして実装する決定。

日付: 2026-07-15

## ステータス

2026-07-15 提案

## コンテキスト

IT3 の US08（経路候補を算出する）では、経路設計者が航海スケジュール検索結果をもとに、制約条件（寄港地接続・貨物種別対応・到着期限）を考慮した経路候補を自動算出する。US24/US25 で Routing Context に航海スケジュール（`Voyage` 集約・`CarrierMovement` の運送区間列）が登録・更新されるため、経路候補算出に必要なデータは Routing Context が自ら保有する。

一方、設計ドキュメント（`domain-model.md`・`tech_stack.md`・`architecture_backend.md`）には外部経路システムへの ACL ポート `ExternalRoutingServicePort`（`RouteSpecification -> Async<Result<CargoItinerary list, DomainError>>`）が定義されており、IT1 では Estimation Context の概算見積（US01）がこのポートのスタブ（`StubRoutingService`）を用いてルート候補を得ていた。

この二つの経路算出パス — (A) Routing が保有スケジュールから算出、(B) 外部サービスへ委譲 — のどちらを US08 の正とするかを決めないと、算出ロジックの置き場所（ドメインサービス vs ACL アダプタ）と、Routing Context の責務境界が定まらない。曖昧なまま実装すると、経路算出ロジックが Routing ドメインと外部アダプタに二重化するおそれがある。

## 決定

**US08 の経路候補算出は、Routing Context のドメインサービス `RouteComputation` として、自コンテキストが保有する `Voyage` スケジュール群から接続経路を構成する方式を採用する。** 外部 `ExternalRoutingServicePort` への委譲は US08 では用いない。

### 変更箇所

1. **ドメインサービス `RouteComputation`**（`CargoTracker.Routing.Domain`）: 純粋関数として実装する。

   ```fsharp
   // 登録済み航海群と探索条件から、制約を満たす経路候補を推奨順に返す純粋関数。
   // computeCandidates : Voyage list -> RouteQuery -> RouteCandidate list
   ```

   - **接続探索**: `CarrierMovement` の到着港＝次区間の出発港で連結する経路を、出発地→目的地に向けて探索する（深さ制限で発散を防ぐ）。
   - **制約評価**: 貨物種別対応（`Voyage.supportedCargoTypes`）・到着期限（最終到着日 ≤ 期限）で絞り込む。
   - **推奨順**: 直行便（単一 Voyage・単一区間）を最優先とし、以降は所要日数の短い順に並べる。

2. **Routing 固有の候補型**: 算出結果は Routing Context 固有の候補型（航海番号・経由港・所要日数・費用）として返す。Estimation Context の `RouteCandidate`（見積用・`EstimatedCost`）とは別概念として分離する。

3. **費用の暫定算出**: US08 AC3 の「費用」は、Billing Context（IT7）の本格的な料金計算に先立ち、IT3 では区間ベースの簡易ヒューリスティック（区間数・距離代替）で算出する。正式な料金計算は Billing 実装時に置き換える。

4. **`ExternalRoutingServicePort` の役割限定**: 本ポートは (a) Estimation の概算見積（US01・IT1 スタブ）、(b) 将来、外部運送会社の経路 API を直接引く必要が生じた場合の連携、に限定する。IT3 では Estimation 側のスタブに WireMock.Net の契約テストを追加して契約を固定する（tech_stack のスタブ→契約固定方針）。

### 代替案

- **案 B: 外部 `ExternalRoutingServicePort` へ委譲する**（却下）: Routing Context が US24/US25 で航海スケジュールを保有・管理する以上、経路算出の入力データは自コンテキスト内に存在する。それを外部サービスへ送って算出を委譲するのは、保有データの二重管理と不要な外部依存を生む。ドメインの中核ロジック（接続探索・制約評価）を ACL アダプタの外側に追い出すことになり、複雑ドメインをドメイン層に凝集させる中盤インサイドアウトの方針（開発戦略）に反する。
- **案 C: Estimation の `RouteCandidate` 型を Routing でも共用する**（却下）: `RouteCandidate` は Estimation Context の見積概念（`EstimatedCost` を保持）であり、Routing の経路候補（確定的な運航スケジュールに基づく）とは意味論が異なる。BC 間で値オブジェクトを共有すると責務が混線する。Routing 固有型として分離する。

## 影響

### ポジティブ

- 経路算出の中核ロジックが Routing Context のドメイン層（純粋関数 `RouteComputation`）に凝集し、FsCheck で接続制約・推奨順・期限判定を網羅検証できる（貧血モデルの回避）。
- 外部サービスへの不要な依存が生じず、Routing Context が自律的に経路候補を提供できる。ArchUnitNET の BC 分離を保てる。
- Estimation（概算・外部スタブ）と Routing（確定スケジュールからの算出）の責務が明確に分離される。

### ネガティブ

- 接続探索アルゴリズム（グラフ探索）を自前で実装するため、経路が多段（多経由）になると探索コストが増える。深さ制限・枝刈りで発散を防ぐ必要がある。
- 費用が IT3 では簡易ヒューリスティックにとどまり、Billing（IT7）で正式な料金計算に置き換える二段構えになる。UI・候補型に「暫定費用」である旨の考慮が要る。

## コンプライアンス

- `RouteComputation` は純粋関数（副作用なし）として実装し、接続制約（到着港＝次出発港）・貨物種別対応・期限判定・直行便優先の推奨順を FsCheck で網羅検証すること。
- 経路算出ロジックが Infrastructure 層（外部 ACL アダプタ）に漏れていないことを ArchUnitNET と実装レビューで確認すること。
- `ExternalRoutingServicePort` を US08 の経路算出に使用しないこと（Estimation 概算・将来連携に限定）をレビューで確認すること。

## 備考

著者: アーキテクト（Claude Code 支援）。関連: ADR-0001（垂直スライス・BC 分離）、`docs/design/domain-model.md`（Routing Context・Voyage・ExternalRoutingServicePort）、`docs/design/architecture_backend.md`（Routing Context・CQRS）、`docs/design/tech_stack.md`（WireMock.Net）、`docs/development/development_strategy.md`（中盤インサイドアウト）、`docs/development/iteration_plan-3.md`（US08・タスク 1.4）。
