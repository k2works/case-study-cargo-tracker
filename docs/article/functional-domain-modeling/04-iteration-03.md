# 第 4 章：IT3 航海スケジュールと経路候補算出

## このイテレーションのゴール

> 航海スケジュールを管理・検索し、制約を考慮した経路候補を自動算出できる（Routing Context の中核ドメインを確立する）

このイテレーションで**アプローチが変わります**。IT1・IT2 は序盤＝アウトサイドイン（受入テストから引く）でしたが、ここからは中盤＝インサイドアウトです。経路探索という計算そのものが難所であり、UI から引いても設計が出てこないためです。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 14（US24 / US25 / US07 / US08） |
| 局面 | 中盤／アプローチ: **インサイドアウト** |
| ユニットテスト | 94 件緑（+34） |
| 統合テスト | 85 件緑（+20） |
| カバレッジ（全体 / ドメイン層） | 93.0% / 88.4% |
| ADR | 0009 経路候補算出は Routing 自コンテキストで構成する |

ユニットテストが 34 件増え、統合テストの増加（20 件）を上回りました。純粋関数で書ける領域が増えたことが数字に出ています。

## 扱うユーザーストーリー

| ID | ストーリー |
| :--- | :--- |
| US24 | 航海スケジュールを登録する |
| US25 | 航海スケジュールを更新する |
| US07 | 航海スケジュールを検索する |
| US08 | 経路候補を算出する |

## モデリング：入れ子になった不変条件

航海スケジュールは 3 階層の構造を持ちます。区間（`CarrierMovement`）→ スケジュール（`Schedule`）→ 航海（`Voyage`）。各階層に不変条件があります。

### 階層 1：区間

```fsharp
// src/CargoTracker.Routing/Domain.fs
/// 運送区間（出発港・到着港・出発日時・到着日時・順序）。
/// 出発港 ≠ 到着港・出発日時 < 到着日時をスマートコンストラクタで保証する。
type CarrierMovement =
    private
        { DepartureLocation: Location
          ArrivalLocation: Location
          DepartureDate: DateTimeOffset
          ArrivalDate: DateTimeOffset
          SeqNumber: int }

module CarrierMovement =

    let create (departureLocation: Location) (arrivalLocation: Location)
               (departureDate: DateTimeOffset) (arrivalDate: DateTimeOffset)
               (seqNumber: int) : Result<CarrierMovement, DomainError> =
        if Location.sameAs departureLocation arrivalLocation then
            Error(BusinessRuleViolation("CarrierMovement", "出発港と到着港は異なる必要があります。"))
        elif departureDate >= arrivalDate then
            Error(BusinessRuleViolation("CarrierMovement", "出発日時は到着日時より前でなければなりません。"))
        elif seqNumber < 1 then
            Error(ValidationError("SeqNumber", "区間順序は 1 以上でなければなりません。"))
        else
            Ok { DepartureLocation = departureLocation
                 ArrivalLocation = arrivalLocation
                 DepartureDate = departureDate
                 ArrivalDate = arrivalDate
                 SeqNumber = seqNumber }
```

要素単体の不変条件です。港が同じでない、出発が到着より前。

### 階層 2：スケジュール

```fsharp
/// 航海スケジュール（順序付き運送区間の非空列）。
/// 連結制約 `movement[n].到着港 = movement[n+1].出発港` と時系列（前区間到着 ≤ 次区間出発）を保証する。
type Schedule = private Schedule of CarrierMovement list

module Schedule =

    /// 運送区間列からスケジュールを構成する。非空・連結・時系列を検証する。
    let create (movements: CarrierMovement list) : Result<Schedule, DomainError> =
        match movements with
        | [] -> Error(ValidationError("Schedule", "航海スケジュールは 1 つ以上の運送区間が必要です。"))
        | _ ->
            let connectivityBroken =
                movements
                |> List.pairwise
                |> List.tryFind (fun (prev, next) -> not (Location.sameAs prev.ArrivalLocation next.DepartureLocation))

            let timelineBroken =
                movements
                |> List.pairwise
                |> List.tryFind (fun (prev, next) -> prev.ArrivalDate > next.DepartureDate)

            match connectivityBroken, timelineBroken with
            | Some _, _ ->
                Error(BusinessRuleViolation("ScheduleConnectivity", "運送区間が連結していません（前区間の到着港と次区間の出発港が一致しません）。"))
            | _, Some _ -> Error(BusinessRuleViolation("ScheduleTimeline", "運送区間の時系列が不正です（前区間の到着後に次区間が出発する必要があります）。"))
            | None, None -> Ok(Schedule movements)
```

ここが**要素の不変条件と集合の不変条件は別物**という論点です。区間が個々に妥当でも、繋がっていなければスケジュールとして無意味です。`List.pairwise` で隣接ペアを作り、連結と時系列の 2 つを別々に検査しています。

姉妹シリーズで見た通り、Java 実装は同じ構造で**集合の不変条件を検査していません**。`Leg` は「積込時刻 < 荷降し時刻」を守りますが、`CargoItinerary` は区間が繋がっているかを見ていません。ふりかえりに「`Leg` の時刻整合性バリデーション」が申し送りとして残り、そのまま持ち越されています。

F# 実装は Booking Context 側の旅程でも同じ検査をしています。

```fsharp
// src/CargoTracker.Booking/Domain.fs
type CargoItinerary = private CargoItinerary of Leg list

module CargoItinerary =

    let create (legs: Leg list) : Result<CargoItinerary, DomainError> =
        match legs with
        | [] -> Error(ValidationError("Legs", "旅程は 1 つ以上の輸送区間が必要です。"))
        | _ ->
            let broken =
                legs
                |> List.pairwise
                |> List.tryFind (fun (prev, next) ->
                    not (Location.sameAs (Leg.unloadLocation prev) (Leg.loadLocation next)))

            match broken with
            | Some _ -> Error(BusinessRuleViolation("LegConnectivity", "輸送区間が連結していません。"))
            | None -> Ok(CargoItinerary legs)
```

`List.pairwise |> List.tryFind` という**同じ形**で書けるのが効いています。連結制約というパターンが 2 箇所に現れ、どちらも 5 行で済んでいます。

### 階層 3：航海

```fsharp
type Voyage =
    { VoyageNumber: VoyageNumber
      Vessel: VesselName
      Carrier: CarrierName
      Schedule: Schedule
      SupportedCargoTypes: Set<CargoTypeTag> }

module Voyage =

    let register (voyageNumber: VoyageNumber) (vessel: VesselName) (carrier: CarrierName)
                 (schedule: Schedule) (supportedCargoTypes: Set<CargoTypeTag>)
                 : Result<Voyage * VoyageEvent list, DomainError> =
        if Set.isEmpty supportedCargoTypes then
            Error(ValidationError("SupportedCargoTypes", "対応貨物種別を 1 つ以上指定してください。"))
        else
            // ...
            Ok(voyage, [ VoyageRegistered voyageNumber ])
```

対応貨物種別は `Set<CargoTypeTag>` です。リストではなく集合を選んだことで、重複が構造的に排除されます。「危険物、危険物、一般」という登録は起こりません。

## モデリング：純粋関数としての経路探索

US08 の経路候補算出は、このプロジェクトで最も計算らしい部分です。

まず、探索の入出力を型で定義します。

```fsharp
/// 経路探索の条件（出発地・目的地・貨物種別・到着期限）。
type RouteQuery =
    { Origin: Location
      Destination: Location
      CargoType: CargoTypeTag
      Deadline: DateTimeOffset }

/// 経路候補の 1 区間（どの航海で、どこからどこへ、いつ）。
type RouteLeg =
    { VoyageNumber: VoyageNumber
      From: Location
      To: Location
      Departure: DateTimeOffset
      Arrival: DateTimeOffset }

/// 経路候補（Routing 固有型・Estimation の RouteCandidate とは別概念・ADR-0009）。
type RouteCandidate =
    { Legs: RouteLeg list
      TransitPorts: Location list
      TransitDays: int
      EstimatedCost: decimal
      IsDirect: bool }
```

`RouteCandidate` は Routing Context 固有の型で、Estimation Context の同名概念とは別型です。ADR-0009 に「経路候補算出は Routing Context が自コンテキストの Voyage スケジュールから構成する」と記録されています。

探索本体は**ドメインサービス**です。集約に属さない業務ロジックなので、モジュールとして独立させています。

```fsharp
/// 経路候補算出ドメインサービス（US08）。純粋関数。
/// 登録済み Voyage 群を航海単位のエッジ（出発港→到着港）とみなし、出発地→目的地の接続経路を探索する。
module RouteComputation =

    /// 探索の最大乗継段数（発散防止・直行 + 最大 2 回乗継まで）。
    let private maxLegs = 3

    /// 費用の暫定ヒューリスティック（区間所要日数ベース）。正式な料金計算は Billing（IT7）で置換する。
    let private dailyRate = 12_000m
```

探索は深さ優先の再帰関数です。

```fsharp
    /// 経路候補を推奨順に算出する。貨物種別対応・接続・期限を制約とし、直行優先→所要日数昇順で並べる。
    let computeCandidates (voyages: Voyage list) (query: RouteQuery) : RouteCandidate list =
        // 貨物種別に対応する航海のみをエッジ化する。
        let edges =
            voyages |> List.filter (Voyage.supports query.CargoType) |> List.map toEdge

        // 現在地から目的地までの経路を深さ優先で探索する（時刻連結・期限・訪問済み航海の重複回避）。
        let rec search
            (current: Location)
            (earliest: DateTimeOffset)
            (usedVoyages: Set<string>)
            (acc: RouteLeg list)
            : RouteLeg list list =
            if List.length acc >= maxLegs then
                []
            else
                edges
                |> List.filter (fun e ->
                    Location.sameAs e.From current
                    && e.Departure >= earliest
                    && not (Set.contains (VoyageNumber.value e.VoyageNumber) usedVoyages))
                |> List.collect (fun e ->
                    let legs = acc @ [ e ]

                    if Location.sameAs e.To query.Destination then
                        [ legs ]
                    else
                        search e.To e.Arrival (Set.add (VoyageNumber.value e.VoyageNumber) usedVoyages) legs)
```

引数がすべて明示されています。現在地・最早出発時刻・訪問済み航海・累積経路。**可変状態が 1 つもありません**。再帰呼び出しのたびに新しい値を渡すため、探索の途中状態がどこかに残ることがありません。

制約は `List.filter` の中に 3 つ並んでいます。

1. `Location.sameAs e.From current` — 現在地から出る航海だけ
2. `e.Departure >= earliest` — 前の区間の到着後に出発するものだけ
3. `not (Set.contains ... usedVoyages)` — 同じ航海を 2 回使わない

3 つ目が循環の防止です。`Set` を引き回すことで、経路ごとに独立した訪問済み集合を持てます。

最後に整形と絞り込みをパイプラインで繋ぎます。

```fsharp
        search query.Origin DateTimeOffset.MinValue Set.empty []
        |> List.map toCandidate
        // 期限内に到達できる候補のみ。
        |> List.filter (fun c -> (List.last c.Legs).Arrival <= query.Deadline)
        // 直行を最優先、次に所要日数の短い順。
        |> List.sortBy (fun c -> (not c.IsDirect), c.TransitDays)
```

`List.sortBy (fun c -> (not c.IsDirect), c.TransitDays)` の 1 行が「直行優先、次に所要日数昇順」です。F# のタプルは辞書式に比較されるため、優先順位をタプルの並び順で表せます。`false < true` なので `not c.IsDirect` は直行（`false`）が先に来ます。

### 他実装との差

姉妹シリーズで見た Java 実装の候補算出はこうでした。

```java
// Java: 航海を所要日数昇順に並べるだけ。乗り継ぎは扱わない
return voyages.stream()
        .map(this::toRouteCandidate)
        .sorted(Comparator.comparingInt(RouteCandidate::transitDays))
        .toList();
```

出発地から目的地への直行便を探すだけで、積み替えを含む経路は出しません。しかも `cargoType` を引数に取りながら使っておらず、危険物・冷凍への対応可否で絞り込んでいません（この未達は最後まで残りました）。

F# 実装は貨物種別のフィルタを探索の入口に置いています。

```fsharp
let edges = voyages |> List.filter (Voyage.supports query.CargoType) |> List.map toEdge
```

引数を受け取って使わない、という状態が起きにくいのは、パイプラインで書くと**使われない値が浮いて見える**ためです。とはいえ、これは型が保証するものではありません。使わなくてもコンパイルは通ります。

## このイテレーションの学び

### インサイドアウトに切り替える判断

開発戦略では、局面ごとにアプローチを変えることが定められています。

| 局面 | イテレーション | アプローチ |
| :--- | :--- | :--- |
| 序盤 | IT1・IT2 | アウトサイドイン（受入テスト Red から） |
| 中盤 | IT3・IT4 | インサイドアウト（ドメインから） |

経路探索のような計算は、UI から要求を引いても設計が出てきません。「候補が一覧に出る」という受入基準からは、グラフ探索の必要性も乗継段数の制限も導けないためです。

インサイドアウトが機能する条件は、**ドメインが純粋であること**です。`RouteComputation.computeCandidates` は DB にもフレームワークにも依存しないため、テストが速く、探索アルゴリズムだけを集中して詰められます。ユニットテストが 34 件増えたのはそのためです。

### 暫定値であることを型で示せない

ふりかえりの Problem に、次の項目があります。

> **費用が暫定ヒューリスティック**: US08 AC3 の費用は区間所要日数ベースの簡易計算にとどまる。正式な料金計算は Billing（IT7）に依存するため、経路候補の費用は暫定値である旨が UI で明示されていない。

コード側には意図が書かれています。

```fsharp
    /// 費用の暫定ヒューリスティック（区間所要日数ベース）。正式な料金計算は Billing（IT7）で置換する。
    let private dailyRate = 12_000m
```

しかし `EstimatedCost: decimal` という型は、その値が暫定であることを伝えません。型で表すなら `EstimatedCost of Provisional | Confirmed` のような DU にする手もありますが、この実装は採っていません。

**型で表せることと、表す価値があることは別**という判断の実例です。ここでは UI 側に注記を出すのが妥当な解であり、ふりかえりでもそう記録されています。

### 探索の制限を明示する

もう 1 つの Problem。

> **経路探索の深さ制限**: RouteComputation は発散防止のため最大 3 区間に制限。多段乗継が必要な稀なケースは候補に出ない。現実的な航路では十分だが制限は明示的ドキュメント化が薄い。

`maxLegs = 3` は、探索が発散しないための実装上の都合です。しかし利用者から見れば「4 回乗り継げば行ける経路が候補に出ない」という業務上の制限になります。

この種の**静かな打ち切り**は、姉妹シリーズでも繰り返し現れました。制限したこと自体は妥当でも、それが記録されず利用者に伝わらないと、「候補なし」が「本当に経路がない」のか「制限に引っかかった」のか区別できません。

コード内の私的な定数にコメントを添えるだけでは足りず、ふりかえりで「ドキュメント化が薄い」と記録されている通りです。

---

- 前の章：[第 3 章：IT2 貨物予約と特殊貨物](03-iteration-02.md)
- 次の章：[第 5 章：IT4 経路確定から予約確定まで](05-iteration-04.md)
