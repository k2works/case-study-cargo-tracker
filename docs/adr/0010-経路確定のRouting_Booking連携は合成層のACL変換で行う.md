# ADR-0010: 経路確定の Routing→Booking 連携は合成層の ACL 変換で行う

US09 で選択された Routing の経路候補を、US11 で Booking の `CargoItinerary` へ変換して予約に紐付ける横断連携を、Web 合成層の ACL 変換関数で実装する決定。

日付: 2026-07-15

## ステータス

2026-07-15 提案
2026-09-05 承認済み（IT4 で `CargoTracker.Web.RouteAcl` として参照実装・受け入れテストで検証）

## コンテキスト

IT4 の US09（経路を選択・確定する）で経路設計者が Routing Context の経路候補（`RouteCandidate`: 航海番号・経由港・所要日数・費用）を 1 件選択し、US11（経路情報を予約に紐付ける）で Booking Context の貨物予約に紐付ける。紐付けにより Booking の状態は `RoutingRequested → RouteProposed of CargoItinerary` に遷移する（`Cargo.execute` の `ProposeRoute` コマンド）。

ここで、Routing の `RouteCandidate`（Routing 固有型・ADR-0009）を Booking の `CargoItinerary`（`Leg` 非空リスト・Booking 固有型）へ**変換**する必要がある。両者は別コンテキストの型であり、さらに Routing の `VoyageNumber` と Booking の `VoyageNumber`（`Leg.Voyage` 用）も**別型**として定義されている（domain-model の型帰属方針・設計レビュー #33）。

BC 分離（ADR-0001）を保ちつつ、この横断変換をどこに配置するかを決めないと、変換ロジックが Routing・Booking のいずれかのドメインへ漏れ出し、BC の自律性を損なうおそれがある。

## 決定

**Routing の `RouteCandidate` を Booking の `CargoItinerary` へ変換する連携を、Web 合成層（`CargoTracker.Web`）の ACL 変換関数として実装する。** Routing・Booking の各ドメイン層は互いの型を参照しない。

### 変更箇所

1. **合成層の ACL 変換関数**（`CargoTracker.Web`）: 選択された `RouteCandidate`（Routing）を受け取り、各区間を Booking の `Leg`（積込港・荷降港・積込/荷降時刻・Booking 側 `VoyageNumber`）へ写像し、`CargoItinerary.create` で連結制約を検証して構成する。

   ```fsharp
   // 疑似シグネチャ（合成層）
   // toCargoItinerary : Routing.RouteCandidate -> Result<Booking.CargoItinerary, DomainError>
   ```

2. **Booking の紐付けワークフロー**: 構成済みの `CargoItinerary` を Booking の `ProposeRoute` コマンドに渡し、`RouteSpecification.isSatisfiedBy` で旅程がルート仕様（出発地・目的地・期限）を満たすことを検証したうえで `RouteProposed` に遷移する（`CargoRouted` イベント発行）。

3. **VoyageNumber の型変換**: Routing の `VoyageNumber`（文字列値）を Booking の `VoyageNumber.create`（または復元）で Booking 側の型に変換する。合成層でのみ両型を扱い、各ドメインには相手の型を持ち込まない。

### 代替案

- **案 B: ドメインイベント（`RouteSelected`）経由で Booking が旅程を構築**（却下）: イベント基盤の実利用にはなるが、変換ロジック（`RouteCandidate → CargoItinerary`）がイベントハンドラに分散し、選択操作から紐付けまでの流れが追跡しづらくなる。US09→US11 は同一ユースケース内の同期的な操作であり、非同期イベントに載せる必然性がない。post-commit イベント（`CargoRouted`）は紐付け成功後の通知として別途発火する（タスク 2.3）。
- **案 C: Routing または Booking のドメイン層に変換を置く**（却下）: いずれかのドメインが相手コンテキストの型を参照することになり、BC 分離（ArchUnitNET で担保）に抵触する。ADR-0009（Routing 自律）・ADR-0001（垂直スライス）と矛盾する。

## 影響

### ポジティブ

- Routing・Booking の各ドメインが相手の型を参照せず、横断変換が合成層に閉じ込められる。BC 自律性を保ち、ArchUnitNET の BC 分離ルールを緑に維持できる（IT3 の荷主名解決・`ShipperExistenceAdapter` と同方針）。
- 変換関数を合成層のユニットテストで独立に検証でき、Routing/Booking のドメインテストは純粋なまま保てる。

### ネガティブ

- 2 つの `VoyageNumber` 型・`RouteCandidate`→`Leg` の写像を合成層で明示的に行うため、変換コードが必要になる。将来 Routing の候補型や Booking の旅程型が変わると、この変換関数の追随が要る。
- 合成層に業務的な変換ロジックが載るため、合成層が薄いハンドラに留まらず、変換関数のテスト・可読性への配慮が要る。

## コンプライアンス

- Routing ドメインが Booking を、Booking ドメインが Routing を直接参照しないことを ArchUnitNET（IT3 で Routing を登録済み）で確認する。
- 変換関数が `CargoItinerary.create` の連結制約と `RouteSpecification.isSatisfiedBy` を通し、不正な旅程を予約に紐付けないことを合成層テストで確認する。
- 紐付け成功後の `CargoRouted` イベントは post-commit（コミット成功後）にのみ発火することを統合テストで確認する（ADR-0002）。

## 備考

著者: アーキテクト（Claude Code 支援）。関連: ADR-0001（垂直スライス・BC 分離）、ADR-0002（post-commit イベント）、ADR-0007（BookingState 拡張）、ADR-0009（Routing 自コンテキスト算出）、`docs/design/domain-model.md`（CargoItinerary・Leg・execute・VoyageNumber 型帰属）、`docs/design/ui_design.md`（経路確定は経路設計フローに一本化・設計レビュー #25/#76）、`docs/development/iteration_plan-4.md`（US09/US11・タスク 2.1/2.2）。
