# 第 3 章：IT2 貨物予約と特殊貨物

## このイテレーションのゴール

> 貨物予約（危険物・冷凍対応）を登録し、見積整合性を確認したうえで経路設計者へ引き渡せる

このイテレーションの主題は「条件付き必須」です。**危険物なら危険物申告が必須、冷凍なら温度管理条件が必須**という要件を、実行時検査ではなく型で表します。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 10（US04 / US05 / US06） |
| ユニットテスト | 60 件緑 |
| 統合テスト | 65 件緑 |
| アーキテクチャテスト | 8 件緑 |
| カバレッジ（全体 / ドメイン層） | 91.9% / 85.7%（閾値 80% / 85%） |

カバレッジに **2 つの閾値**が設定されている点に注目してください。全体 80%、ドメイン層 85%。ドメイン層のほうを厳しくするのは、そこに業務ルールが集中しているためです。

## 扱うユーザーストーリー

| ID | ストーリー | SP |
| :--- | :--- | :--- |
| US04 | 貨物予約を登録する | 5 |
| US05 | 危険物・冷凍貨物の予約を登録する | 3 |
| US06 | 予約情報を経路設計者に引き渡す | 2 |

## モデリング：条件付き必須を型に昇格する

まず、追加情報そのものを値オブジェクトにします。

```fsharp
// src/CargoTracker.Booking/Domain.fs
/// 危険物申告情報（US05）。危険物クラス・UN 番号・正式輸送品名を必須で持つ。
type HazardousDeclaration =
    private
        { HazardClass: string
          UnNumber: string
          ProperShippingName: string }

module HazardousDeclaration =

    let create
        (hazardClass: string)
        (unNumber: string)
        (properShippingName: string)
        : Result<HazardousDeclaration, DomainError> =
        if String.IsNullOrWhiteSpace hazardClass then
            Error(ValidationError("HazardClass", "危険物クラスは空にできません。"))
        elif String.IsNullOrWhiteSpace unNumber then
            Error(ValidationError("UnNumber", "UN 番号は空にできません。"))
        elif String.IsNullOrWhiteSpace properShippingName then
            Error(ValidationError("ProperShippingName", "正式輸送品名は空にできません。"))
        else
            Ok
                { HazardClass = hazardClass
                  UnNumber = unNumber
                  ProperShippingName = properShippingName }
```

レコード型にも `private` が付いています。フィールドは 3 つとも必須で、`create` を通らないと構築できません。

温度条件は、フィールド間の関係が不変条件になります。

```fsharp
/// 温度管理条件（US05）。最低温度 ≤ 最高温度の不変条件をスマートコンストラクタで保証する。
type TemperatureRequirement =
    private
        { MinTemperature: decimal
          MaxTemperature: decimal
          Unit: TemperatureUnit }

module TemperatureRequirement =

    let create
        (minTemperature: decimal)
        (maxTemperature: decimal)
        (unit: TemperatureUnit)
        : Result<TemperatureRequirement, DomainError> =
        if minTemperature > maxTemperature then
            Error(ValidationError("TemperatureRequirement", "最低温度は最高温度以下でなければなりません。"))
        else
            Ok
                { MinTemperature = minTemperature
                  MaxTemperature = maxTemperature
                  Unit = unit }
```

「最低温度が最高温度を超える `TemperatureRequirement`」は存在しえません。

そして、これらを貨物種別に埋め込みます。

```fsharp
/// 貨物種別（US05）。危険物・冷凍は必要な追加情報を DU ケースに埋め込み、必須制約を型に昇格する。
type CargoType =
    | General
    | Hazardous of HazardousDeclaration
    | Refrigerated of TemperatureRequirement
```

この 4 行が、このイテレーションの成果の中心です。

`Hazardous` を構築するには `HazardousDeclaration` が要り、その値は `create` を通っているので検証済みです。したがって **`Hazardous` ケースの値が存在する = 妥当な危険物申告が付いている** が保証されます。

集約側には、条件付き必須の検査コードが 1 行もありません。

```fsharp
/// 集約ルート。予約の中心。状態遷移・貨物仕様を統括する。
type Cargo =
    { BookingId: BookingId
      ShipperId: ShipperId
      Consignee: Consignee option
      RouteSpecification: RouteSpecification
      CargoType: CargoType
      Weight: Weight
      State: BookingState
      Dimensions: (decimal * decimal * decimal) option
      Quantity: Quantity option
      Description: Description option }
```

### 他実装との差

同じ要件を、姉妹シリーズの各実装がどう書いたかを並べます。

```java
// Java: 種別は列挙、追加情報は別フィールド。集約のコンストラクタで突き合わせる
if (this.cargoType == CargoType.HAZARDOUS && this.hazardousDeclaration == null) {
    throw new IllegalArgumentException("hazardousDeclaration is required for HAZARDOUS cargo");
}
```

```go
// Go: 欠落だけでなく「一般貨物なのに温度条件がある」も拒否する
case CargoTypeGeneral:
    if hazardous != nil || temperature != nil {
        return ErrSpecialInfoNotAllowed
    }
```

Go の実装は Java より丁寧で、逆方向の不整合まで見ています。しかし F# の DU では、そもそも `General` に追加情報を添える書き方が存在しません。**Go が明示的に潰しに行った不整合を、F# は考える必要すらない**という関係です。

一方で、この選択には代償があります。DB のテーブルは「種別カラム + nullable な追加カラム」という形なので、DU との相互変換を書く必要があります。Rust 実装が和型を持つ言語でありながら貨物種別に enum + `Option` を選んだのは、この永続化の都合でした。

F# 実装は変換を引き受ける側を選び、`Infrastructure.fs` にマッピングを閉じ込めています。

## モデリング：ルート仕様と関係の不変条件

出発地と目的地は「異なること」が業務要件です。

```fsharp
/// ルート仕様（US04）。出発地・目的地・到着期限の要件。出発地 ≠ 目的地を保証する。
type RouteSpecification =
    private
        { Origin: Location
          Destination: Location
          ArrivalDeadline: DateOnly }

module RouteSpecification =

    let create
        (origin: Location)
        (destination: Location)
        (arrivalDeadline: DateOnly)
        : Result<RouteSpecification, DomainError> =
        if Location.sameAs origin destination then
            Error(BusinessRuleViolation("RouteSpecification", "出発地と目的地は異なる必要があります。"))
        else
            Ok
                { Origin = origin
                  Destination = destination
                  ArrivalDeadline = arrivalDeadline }
```

エラー種別が `ValidationError` ではなく `BusinessRuleViolation` である点に注意してください。「5 文字でない UN/LOCODE」は入力形式の誤り、「出発地と目的地が同じ」は業務ルール違反です。エラーを DU で分けているため、UI 側で扱いを変えられます。

## ワークフロー：入力 DTO から検証済みの型へ

UI から来るのは平坦な文字列です。これを検証済みの型に変換するのがアプリケーション層の仕事になります。

```fsharp
// src/CargoTracker.Booking/Application.fs
/// 貨物種別の入力（UI からの DTO）。種別に応じて追加情報を持つ。
type CargoTypeInput =
    | GeneralInput
    | HazardousInput of hazardClass: string * unNumber: string * properShippingName: string
    | RefrigeratedInput of minTemperature: decimal * maxTemperature: decimal * unit: string
```

入力側も DU にしています。検証前の生データと検証後のドメイン型が、**同じ形をした別の型**として並ぶ構図です。

変換はパターンマッチで書きます。

```fsharp
    /// 種別入力を検証済み CargoType に変換する（危険物・冷凍は必須情報を検証）。
    /// 温度単位の文字列変換は Domain の `TemperatureUnit.ofString` に集約している（DRY）。
    let private validateCargoType (input: CargoTypeInput) : Result<CargoType, DomainError> =
        match input with
        | GeneralInput -> Ok General
        | HazardousInput(hazardClass, unNumber, properShippingName) ->
            HazardousDeclaration.create hazardClass unNumber properShippingName
            |> Result.map Hazardous
        | RefrigeratedInput(minTemperature, maxTemperature, unit) ->
            result {
                let! u = TemperatureUnit.ofString unit
                let! req = TemperatureRequirement.create minTemperature maxTemperature u
                return Refrigerated req
            }
```

`HazardousDeclaration.create ... |> Result.map Hazardous` の 1 行が、この設計の要約です。検証に成功したときだけ `Hazardous` ケースが構築されます。失敗すれば `Error` が伝播し、`CargoType` の値は生まれません。

冷凍のほうは 2 段階（単位の解析 → 温度条件の構築）なので `result { }` で繋いでいます。単位が不正なら温度条件の検証には進みません——**依存関係があるためモナド的合成が正しい**という例です。

予約登録の全体はこうなります。

```fsharp
    /// 貨物予約を登録する（US04/US05）。
    /// 入力検証 → 荷主存在確認（ACL）→ 集約生成 → 永続化 → イベント返却。
    let book
        (repo: CargoRepository)
        (shipperChecker: ShipperExistenceChecker)
        (newId: IdGenerator)
        (cmd: BookCargoCommand)
        : Async<Result<Cargo * BookingEvent list, DomainError>> =
        asyncResult {
            let! shipperId = ShipperId.ofString cmd.ShipperId

            let! origin, destination, weight, cargoType, consignee =
                validation {
                    let! origin = toLocation "Origin" cmd.OriginUnlocode
                    and! destination = toLocation "Destination" cmd.DestinationUnlocode
                    and! weight = Weight.create cmd.WeightKg
                    and! cargoType = validateCargoType cmd.CargoType
                    and! consignee = validateConsignee cmd.Consignee
                    return origin, destination, weight, cargoType, consignee
                }
                |> Result.mapError List.head

            let! routeSpec = RouteSpecification.create origin destination cmd.ArrivalDeadline

            // 荷主の存在を ACL 経由で確認する（存在しなければ NotFound）。
            let! exists = shipperChecker.Exists shipperId

            do!
                if exists then
                    Ok()
                else
                    Error(NotFound("Shipper", cmd.ShipperId))

            let! cargo, events = Cargo.book newId shipperId consignee routeSpec cargoType weight
            do! repo.Save cargo
            return cargo, events
        }
```

構造がはっきり分かれています。

1. **`validation { and! }`** — 互いに独立した 5 項目を全部検証（エラーを収集）
2. **`let!`** — `origin` と `destination` が揃ってから `routeSpec` を構築（依存あり）
3. **ACL 経由の荷主確認** — Shipper Context には直接触らない
4. **集約生成と永続化**

`Cargo.book` に渡される引数は**すべて検証済みの型**です。ドメイン関数は再検査をしません。

## モデリング：コンテキスト境界を型で守る

Booking Context は荷主が実在するか確認しますが、Shipper Context のドメインモデルには触れません。

```fsharp
/// 荷主存在確認の ACL ポート（domain-model: ShipperId -> Async<bool>）。
/// Booking Context は Shipper Context に直接依存せず、このポート経由で荷主の存在を確認する。
type ShipperExistenceChecker =
    { Exists: ShipperId -> Async<Result<bool, DomainError>> }
```

ポートが返すのは `bool` だけです。`Shipper` を返す設計にすると、Shipper Context のドメインモデルが Booking Context に漏れ出します。

このルールは ArchUnitNET で機械的に検証されているうえ、**F# ではプロジェクト参照を張っていなければそもそも書けません**。`CargoTracker.Booking.fsproj` が `CargoTracker.Shipper` を参照していないため、`Shipper` 型を名前で書いてもコンパイルが通りません。

## 状態遷移の芽

US06（経路設計者への引き渡し）で、最初の状態遷移が入ります。

```fsharp
type BookingState =
    | Preliminary
    | RoutingRequested
    // IT4 で RouteProposed 以降を追加
```

```fsharp
let execute (cargo: Cargo) (command: BookingCommand) : Result<Cargo * BookingEvent list, DomainError> =
    match cargo.State, command with
    | Preliminary, SubmitForRouting ->
        Ok({ cargo with State = RoutingRequested }, [ RoutingRequestedEvent cargo.BookingId ])
    // ...
    | state, cmd -> Error(InvalidStateTransition(BookingState.toString state, sprintf "%A" cmd))
```

`match cargo.State, command with` という**タプルに対するパターンマッチ**が、状態機械の実装形です。許可された組み合わせを列挙し、最後に catch-all で閉じます。

戻り値が `Cargo * BookingEvent list` である点も設計です。状態を変えると同時に、**何が起きたか**をイベントとして返します。イベントの発行はアプリケーション層が永続化成功後に行います（ADR-0002）。

姉妹シリーズで見た通り、Java 実装はイベント発行をアプリケーション層で個別に書いており、`assignItinerary` でのイベント発行漏れが 5 イテレーション気づかれませんでした。F# 版はドメイン関数が状態とイベントを**組で返す**ため、状態だけ変えてイベントを返さないコードは書けます（`[]` を返せばよい）が、遷移とイベントが同じ `match` の腕に並ぶので、書き漏らしが目に見えます。

## このイテレーションの学び

### ドメイン層のカバレッジ閾値を分ける

このイテレーションのカバレッジは全体 91.9%、ドメイン層 85.7% で、閾値はそれぞれ 80% と 85% です。

全体だけを見ていると、ドメイン層の被覆不足が薄まって見えません。姉妹シリーズで見た通り、Java 実装は新しいコンテキストを追加するたびに全体カバレッジが下がり、最終的にブランチカバレッジ 74% で目標未達のままリリースしています。

**層別の閾値を CI で強制する**のが、F# 実装が最後まで 88% 以上を維持できた理由です。

### 型で消えたテスト

条件付き必須を DU に昇格させたことで、**書かなくてよくなったテスト**があります。

- 「種別が危険物なのに申告が null なら例外」— 状態が作れないので不要
- 「一般貨物に温度条件を渡したら無視される／エラー」— 渡す方法がないので不要
- 「最低温度 > 最高温度の温度条件が保存される」— 作れないので不要

代わりに書くのは「不正な入力から `create` が `Error` を返す」テストだけで、1 箇所です。Java 実装が集約のコンストラクタと入力変換の両方でテストを必要としたのに対し、検証点が値オブジェクトに 1 本化されています。

### 未接続のイベントディスパッチ

ふりかえりでは、ADR-0002 の post-commit イベントディスパッチが**消費者不在のため未結線**であることが記録されています。仕組みは作ったが、まだ誰も購読していない状態です。

これ自体は正しい判断です。消費者がいないうちから結線しても検証できません。ただし姉妹シリーズで繰り返し見た通り、「実装したが駆動していない」状態は緑のテストの下に隠れます。ふりかえりで明示的に「未結線（継続）」と記録し続けたことが、IT5 以降での結線につながりました。

---

- 前の章：[第 2 章：IT1 型で守る土台をつくる](02-iteration-01.md)
- 次の章：[第 4 章：IT3 航海スケジュールと経路候補算出](04-iteration-03.md)
