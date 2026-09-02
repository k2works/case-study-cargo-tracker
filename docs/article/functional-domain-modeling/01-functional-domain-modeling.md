---
type: Article
title: "第 1 章：関数型ドメインモデリングとは"
description: "不正な状態を作れなくするための 5 技法（単一ケース DU・和型・状態機械・Railway Oriented Programming・ポート注入）。"
tags: [article, functional-domain-modeling]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-06T02:33:18Z }
---

# 第 1 章：関数型ドメインモデリングとは

## この章の狙い

以降のイテレーション章で繰り返し使う技法を、先にまとめて示します。個々の技法は目新しいものではありませんが、**組み合わせたときに何が起こるか**がこの手法の本体です。

## 出発点：不正な状態を作れなくする

業務システムのバグの多くは「あってはならない状態」が作れてしまうことに由来します。

- 法人荷主なのに契約番号がない
- 貨物種別が危険物なのに危険物申告がない
- 経路が提案済みなのに旅程が入っていない
- 例外が解決済みなのに解決時刻が空

オブジェクト指向の定石は、コンストラクタで検査して例外を投げることです。これは動きますが、**実行するまで分かりません**。テストを書いていなければ、本番で初めて分かります。

関数型ドメインモデリングは、これを型の問題に置き換えます。不正な状態を表す値が**そもそも作れない**ように型を設計する。作れないものはテストする必要もありません。

## 技法 1：単一ケース DU + スマートコンストラクタ

`string` や `decimal` を裸で持ち回ると、検証済みかどうかが型から分かりません。F# では単一ケースの判別共用体（DU）で包み、コンストラクタを `private` にします。

```fsharp
// src/CargoTracker.Shared/Domain.fs
/// UN/LOCODE で識別される地点を表す値オブジェクト。
/// 不正な状態を型で表現不可能にするため、スマートコンストラクタ経由でのみ生成する。
type Location = private Location of string

module Location =

    /// UN/LOCODE は 5 文字の大文字英字。
    let create (code: string) : Result<Location, string> =
        match code with
        | null -> Error "ロケーションコードが null です"
        | c when c.Length <> 5 -> Error "ロケーションコードは 5 文字である必要があります"
        | c when not (c |> Seq.forall (fun ch -> Char.IsLetter ch && Char.IsUpper ch)) ->
            Error "ロケーションコードは大文字英字である必要があります"
        | c -> Ok(Location c)

    /// 内部の文字列表現を取り出す。
    let value (Location code) = code
```

`private` が効いているため、モジュール外から `Location "abc"` とは書けません。`Location` 型の値を手に入れる唯一の道は `Location.create` を通ることであり、その関数は検証に失敗すると `Error` を返します。

つまり **`Location` 型の値が存在する = UN/LOCODE として妥当**が、コンパイラによって保証されます。以降どのレイヤでも再検査は不要です。

同じパターンが実装全体で使われています。

```fsharp
type ShipperId = private ShipperId of Guid       // Shared
type BookingId = private BookingId of string     // Booking
type Weight = private Weight of decimal          // Booking
type DiscountRate = private DiscountRate of decimal  // Billing
type TrackingNumber = private TrackingNumber of string  // Tracking
```

副次的な効果として、**型の取り違えがコンパイルエラーになります**。`BookingId` と `TrackingNumber` はどちらも内部表現が `string` ですが、別の型なので引数の入れ違いが起きません。

さらに重要なのは、コンテキストをまたいだ同名概念の取り違えも防げることです。

```fsharp
// src/CargoTracker.Booking/Domain.fs
/// 航海番号（Booking Context 固有・単一ケース DU）。Leg.Voyage 用。
/// Routing Context の同名型とは別型であり、コンテキスト跨ぎの取り違えはコンパイルエラーになる。
type VoyageNumber = private VoyageNumber of string
```

Booking の `VoyageNumber` と Routing の `VoyageNumber` は名前も内部表現も同じですが、別型です。境界を越えるときは必ず明示的な変換を書くことになり、それが ACL（腐敗防止層）の実体になります。

## 技法 2：和型で「条件付き必須」を消す

「危険物なら申告が必須」は、フィールドを 2 つ持たせて実行時に突き合わせる代わりに、**種別そのものにデータを持たせる**ことで表現できます。

```fsharp
// src/CargoTracker.Booking/Domain.fs
/// 貨物種別（US05）。危険物・冷凍は必要な追加情報を DU ケースに埋め込み、必須制約を型に昇格する。
type CargoType =
    | General
    | Hazardous of HazardousDeclaration
    | Refrigerated of TemperatureRequirement
```

`Hazardous` を作るには `HazardousDeclaration` を渡すしかありません。「種別は危険物だが申告は null」という値は**書けません**。

比較のため、同じ要件を Java 実装がどう書いたかを見ます。

```java
// Java 版: 種別は列挙、追加情報は別フィールド。実行時に突き合わせる
if (this.cargoType == CargoType.HAZARDOUS && this.hazardousDeclaration == null) {
    throw new IllegalArgumentException("hazardousDeclaration is required for HAZARDOUS cargo");
}
```

Java 版はこの検査を書き忘れれば通ってしまいます。F# 版は書き忘れる余地がありません。

## 技法 3：状態にデータを持たせる状態機械

技法 2 を状態機械に適用すると、効果はさらに大きくなります。

```fsharp
// src/CargoTracker.Booking/Domain.fs
type BookingState =
    | Preliminary
    | RoutingRequested
    | RouteProposed of CargoItinerary
    | Confirmed of CargoItinerary
    | Delivered of CargoItinerary
    | Settled of CargoItinerary
    | Cancelled of reason: string
```

旅程（`CargoItinerary`）は独立したフィールドではなく、**それを持つべき状態だけが持ちます**。

- `Preliminary`・`RoutingRequested` は旅程を持たない
- `RouteProposed` 以降は必ず旅程を持つ
- `Cancelled` は理由を持つ

「経路提案済みなのに旅程が null」という状態は表現できません。旅程を取り出すコードでは、パターンマッチが状態の確認を兼ねます。

姉妹シリーズで見た通り、Java 実装はここで躓きました。旅程が `Cargo` の独立したフィールドであり、しかも「経路設計へ引き渡した」と「経路が紐付いた」を同じ状態名で表したため、**状態を見ても経路の有無が分からない**設計になっています。F# 版では状態を見れば分かります。

遷移そのものは、状態とコマンドの組に対する単一の関数です。

```fsharp
/// 状態遷移（US06/US11/US13）。網羅的パターンマッチにより許可されない遷移は InvalidStateTransition を返す。
let execute (cargo: Cargo) (command: BookingCommand) : Result<Cargo * BookingEvent list, DomainError> =
    match cargo.State, command with
    | Preliminary, SubmitForRouting ->
        Ok({ cargo with State = RoutingRequested }, [ RoutingRequestedEvent cargo.BookingId ])
    // ...
    | state, cmd -> Error(InvalidStateTransition(BookingState.toString state, sprintf "%A" cmd))
```

許可された遷移だけを列挙し、最後に「それ以外は不正」で閉じます。状態やコマンドを追加したとき、直す場所はこの関数 1 つです。

## 技法 4：Railway Oriented Programming

失敗を例外ではなく `Result` として返し、成功経路と失敗経路の 2 本のレールで合成する手法です。

全コンテキストが共通のエラー型を持ちます。

```fsharp
// src/CargoTracker.Shared/Domain.fs
/// 全コンテキスト共通のドメインエラー表現（domain-model 準拠）。
/// 例外を投げず Railway Oriented Programming で失敗を値として扱う。
type DomainError =
    | ValidationError of field: string * message: string
    | InvalidStateTransition of current: string * attempted: string
    | BusinessRuleViolation of rule: string * message: string
    | NotFound of entity: string * id: string
```

エラーの種類が DU なので、呼び出し側は `NotFound` を 404、`ValidationError` を 400 に写す、といった分岐を網羅的に書けます。

合成には FsToolkit.ErrorHandling のコンピュテーション式を使います。

```fsharp
// src/CargoTracker.Booking/Application.fs
asyncResult {
    let! shipperId = ShipperId.ofString cmd.ShipperId
    // ...
    let! routeSpec = RouteSpecification.create origin destination cmd.ArrivalDeadline
    let! exists = shipperChecker.Exists shipperId
    do! if exists then Ok() else Error(NotFound("Shipper", cmd.ShipperId))
    let! cargo, events = Cargo.book newId shipperId consignee routeSpec cargoType weight
    do! repo.Save cargo
    return cargo, events
}
```

`let!` のいずれかが `Error` を返せば、そこで処理が止まり `Error` が返ります。`try`/`catch` も早期 return もありません。**成功経路だけを読めば処理の本筋が読める**のが ROP の効用です。

### モナド的合成と適用的合成の使い分け

`asyncResult` は最初のエラーで止まります。フォーム入力の検証では、全項目のエラーをまとめて返したいので使い分けます。

```fsharp
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
```

`and!` は適用的合成で、**互いに独立した検証を全部走らせてエラーを蓄積します**。出発地が不正でも重量の検証は行われ、両方のエラーが集まります。

一方 `let!` はモナド的合成で、前の結果に依存する処理に使います。上のコードで `routeSpec` の生成が `validation` の外にあるのは、`origin` と `destination` が揃わないと作れないためです。

**依存がなければ `and!`、依存があれば `let!`** という使い分けが、そのままフォーム UX に反映されます。1 項目直すたびに次のエラーが出る画面と、全部まとめて出る画面の差です。

## 技法 5：副作用をポートとして注入する

ドメイン関数を純粋に保つには、時刻・乱数・DB を関数の外に出す必要があります。

```fsharp
// src/CargoTracker.Shared/Domain.fs
/// 現在時刻の注入ポート（ADR-0006）。ドメイン関数は DateTimeOffset.Now を直接呼ばず、
/// このポートから取得した値を引数で受け取ることで純粋性を保つ。
type Clock = unit -> DateTimeOffset

/// GUID 生成の注入ポート（ADR-0006）。採番するドメイン関数はこのポートを引数で受ける。
type IdGenerator = unit -> Guid
```

ポートは**インターフェースではなく関数型**です。テストでは `fun () -> knownGuid` と書くだけで差し替えられます。

リポジトリのように操作が複数あるポートは、関数のレコードで表します。

```fsharp
// src/CargoTracker.Booking/Application.fs
/// 貨物予約リポジトリの出力ポート（関数レコード）。テストは関数リテラルで差し替える。
type CargoRepository =
    { Save: Cargo -> Async<Result<unit, DomainError>>
      Update: Cargo -> Async<Result<unit, DomainError>>
      FindById: BookingId -> Async<Result<Cargo option, DomainError>> }
```

モックライブラリは使いません。テストでは必要なフィールドだけ本物の挙動を書き、残りは失敗する関数を入れておけば、想定外の呼び出しがテストを落とします。

## 適用しなかったこと

手法の限界も先に述べておきます。この実装は、次の点で「型で全部やる」を選んでいません。

| 項目 | 選択 | 理由 |
| :--- | :--- | :--- |
| 荷主の個人／法人 | DU（`ShipperKind`）を採用 | 契約情報の条件付き必須が明確 |
| 貨物種別の追加情報 | DU（`CargoType`）を採用 | 同上 |
| 旅程の非空保証 | `Leg list` + スマートコンストラクタ | `NonEmptyList` 型は導入せず、永続化との往復を優先 |
| 状態遷移の完全な型分離 | 単一の `execute` 関数 | 状態ごとに型を分けると型の数が状態数だけ増える |
| 金額の通貨整合 | 実行時に `Result` で判定 | 通貨を型引数にすると DB 復元が煩雑になる |

いずれも「型でできるが、永続化やコード量とのトレードオフで選ばなかった」ものです。関数型ドメインモデリングは全か無かの手法ではなく、**どこまで型に持ち上げるかを都度決める**という運用になります。

その判断の実例が、以降の 8 章です。

## モジュール構成

コンテキストごとに .NET プロジェクトを分け、その中を 3 ファイルに固定しています。

```
src/
  CargoTracker.Shared/       Domain.fs
  CargoTracker.Shipper/      Domain.fs  Application.fs  Infrastructure.fs
  CargoTracker.Booking/      Domain.fs  Application.fs  Infrastructure.fs
  CargoTracker.Estimation/   同上
  CargoTracker.Routing/      同上
  CargoTracker.Tracking/     同上
  CargoTracker.Handling/     同上
  CargoTracker.Billing/      同上
  CargoTracker.Web/          App.fs  Views.fs  Program.fs  ...（合成ルート）
```

ADR-0001 で「垂直スライス（コンテキストファースト）」として記録されています。レイヤを第一階層にしないのは、変更がコンテキスト内に閉じるようにするためです。

F# ではプロジェクト参照とファイル順序が依存を強制するため、**循環参照が構造的に作れません**。Booking が Shipper を参照していなければ、参照するコードは書けません。ArchUnitNET によるアーキテクチャテストも併用しています。

```fsharp
// tests/CargoTracker.ArchTests/ArchTests.fs
/// 「<Context>.Domain は <Context>.Infrastructure に依存しない」ルール。
let private domainNotDependOnInfrastructure (context: string) =
    Types()
        .That()
        .ResideInNamespace(sprintf "CargoTracker.%s.Domain" context)
        .Should()
        .NotDependOnAny(Types().That().ResideInNamespace(sprintf "CargoTracker.%s.Infrastructure" context))
        .WithoutRequiringPositiveResults()
```

`[<Theory>]` で 7 コンテキスト分を回すため、コンテキストを追加したらルールも自動的に増えます。

## 次の章から

第 2 章以降は、IT1 から IT8 までを順に追います。各章で「その回に新しく必要になったモデリング」に焦点を当てます。

- [第 2 章：IT1 型で守る土台をつくる](02-iteration-01.md)
