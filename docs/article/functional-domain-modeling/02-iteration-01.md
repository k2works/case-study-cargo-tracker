---
type: Article
title: "第 2 章：IT1 型で守る土台をつくる"
description: "IT1。値オブジェクトと判別共用体で荷主を表し、Railway Oriented Programming と副作用の締め出しで土台を作る。"
tags: [article, functional-domain-modeling]
status: stable
generated: { by: human:kakimomokuri, at: 2026-08-06T02:33:18Z }
---

# 第 2 章：IT1 型で守る土台をつくる

## このイテレーションのゴール

> 技術基盤（DbUp・UnitOfWork + post-commit イベント・FsToolkit ROP ワークフロー・認証）を確立し、荷主登録と見積で最初の業務価値を届ける

最初のイテレーションで作るのは、業務機能 2 つと**それを支える型の作法**です。ここで決めた作法が 8 イテレーション通して使われるため、決めそこねると後で全部書き直しになります。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 10（+ 基盤タスク） |
| アプローチ | 序盤＝アウトサイドイン（受入テスト Red から着手） |
| ADR | 0001 垂直スライス / 0002 post-commit イベント / 0003 DbUp / 0004 Donald 永続化 / 0005 Cookie 認証 / 0006 時刻・GUID 注入 |

## 扱うユーザーストーリー

| ID | ストーリー | SP |
| :--- | :--- | :--- |
| US02 | 荷主を登録する | 3 |
| US03 | 法人荷主を登録する | 2 |
| US01 | 輸送見積を作成する | 5 |

成功基準には、業務ストーリーに加えて基盤の検証項目が並んでいます。

- 「ログイン → 荷主登録 → 見積作成」が WebApplicationFactory 受入テストで一気通貫
- ロールバック時にドメインイベントが発行されないことを統合テストで実証
- ArchUnitNET ルール（Domain → Infrastructure 非依存・Giraffe/Donald 非侵入）が緑

3 つ目が重要です。**ドメイン層に Web フレームワークとデータアクセスライブラリを侵入させない**というルールを、最初のイテレーションから機械が検証しています。

## モデリング：値オブジェクトから始める

最初に書いたのは集約ではなく値オブジェクトです。

```fsharp
// src/CargoTracker.Shipper/Domain.fs
/// 荷主の氏名または社名（1〜200 文字）。
type ShipperName = private ShipperName of string

module ShipperName =

    let create (value: string) : Result<ShipperName, DomainError> =
        if String.IsNullOrWhiteSpace value then
            Error(ValidationError("ShipperName", "荷主名は空にできません。"))
        elif value.Length > 200 then
            Error(ValidationError("ShipperName", "荷主名は 200 文字以内でなければなりません。"))
        else
            Ok(ShipperName value)

    let value (ShipperName v) = v
```

型 + モジュールの 2 点セットが、この実装の値オブジェクトの定型です。型は `private` コンストラクタで作れなくし、同名モジュールに `create`（検証）と `value`（取り出し）を置きます。

`create` が `Result` を返すことが要点です。例外を投げないので、呼び出し側は失敗の処理を**書き忘れられません**。`Result` を無視すると型が合わず、コンパイルが通らないためです。

## モデリング：荷主の個人／法人を DU で分ける

US02（個人）と US03（法人）の差は、法人だけが契約番号と割引率を持つことです。

```fsharp
// src/CargoTracker.Shipper/Domain.fs
/// 荷主種別。継承ではなく DU で表現し、法人は契約番号と割引率を「必ず」持つ。
type ShipperKind =
    | Individual
    | Corporate of ContractNumber * DiscountRate

type Shipper =
    { Id: ShipperId
      Code: ShipperCode
      Name: ShipperName
      Email: Email
      Phone: Phone option
      Address: Address option
      Kind: ShipperKind }
```

コメントに「継承ではなく DU で表現し」とある通り、これは意識的な選択です。

継承（基底クラス `Shipper` + 派生 `CorporateShipper`）で表すと、「法人だが契約情報を持たない `Shipper` インスタンス」が作れてしまいます。Java 実装はまさにその形になっており、`corporateBase` というファクトリが中間状態を返します。

DU なら中間状態が存在しません。`Corporate` を作るには契約番号と割引率が要ります。

割引率の適用も、パターンマッチ 1 つで書けます。

```fsharp
    /// 適用される割引率を取得する。個人は 0%。
    let effectiveDiscountRate (shipper: Shipper) : decimal =
        match shipper.Kind with
        | Individual -> 0.0000m
        | Corporate(_, rate) -> DiscountRate.value rate
```

`Individual` のケースを書き忘れれば、コンパイラが網羅性の警告を出します。

任意項目は `option` で表しています（`Phone option`・`Address option`）。null を使わないため、「電話番号が未入力」と「電話番号が空文字」が型で区別されます。

## ワークフロー：Railway Oriented Programming

ドメインが揃ったら、アプリケーション層で合成します。

```fsharp
// src/CargoTracker.Shipper/Application.fs
/// 荷主を登録する。入力検証（全エラー収集）→ メール重複チェック → 永続化 → イベント返却。
let register
    (repo: ShipperRepository)
    (newId: IdGenerator)
    (cmd: RegisterShipperCommand)
    : Async<Result<ShipperRegistered, DomainError>> =
    asyncResult {
        // 入力の適用的検証（フィールドエラーを全収集）
        let! name, email, phone, address, kind =
            validation {
                let! name = ShipperName.create cmd.Name
                and! email = Email.create cmd.Email
                and! phone = validateOptional Phone.create cmd.Phone
                and! address = validateOptional Address.create cmd.Address
                and! kind = validateKind cmd.Corporate
                return name, email, phone, address, kind
            }
            |> Result.mapError List.head

        // メールアドレスの一意制約（ドメイン不変条件の補完）
        let! exists = repo.ExistsByEmail email

        do!
            if exists then
                Error(BusinessRuleViolation("EmailAlreadyRegistered", "このメールアドレスは既に登録されています。"))
            else
                Ok()

        let id = ShipperId.ofGuid (newId ())
        let shipper, event = Shipper.register id name email phone address kind
        do! repo.Save shipper
        return event
    }
```

読み方は上から下です。エラー処理の分岐が 1 つも書かれていませんが、どのステップで `Error` が返っても、そこで打ち切られて呼び出し元に伝わります。

`validation { ... and! ... }` の部分だけ性質が違います。5 つの検証は互いに独立しているので、**全部走らせてエラーを集めます**。名前が空でメールも不正なら、両方のエラーが返ります。

法人情報の検証も入れ子の `validation` です。

```fsharp
    /// 種別入力を検証済み ShipperKind に変換する。
    let private validateKind (corporate: CorporateInput option) : Validation<ShipperKind, DomainError> =
        match corporate with
        | None -> Ok Individual
        | Some c ->
            validation {
                let! contract = ContractNumber.create c.ContractNumber
                and! rate = DiscountRate.create c.DiscountRate
                return Corporate(contract, rate)
            }
```

契約番号と割引率が両方妥当なときだけ `Corporate` が構築されます。片方でも不正なら `Corporate` は作られません——**不正な法人荷主が存在しうる時間がゼロ**ということです。

なお `|> Result.mapError List.head` で、集めたエラーの先頭だけを取り出しています。ここは実装の妥協点です。`DomainError` 1 つを返す関数シグネチャに合わせるため、せっかく集めた残りのエラーを捨てています。UI で全項目のエラーを出すには、この形を `DomainError list` に変える必要があります。

## 副作用の締め出し

ドメイン関数を純粋に保つため、時刻と ID 生成をポートにしました。ADR-0006 として記録されています。

```fsharp
// src/CargoTracker.Shared/Domain.fs
/// 現在時刻の注入ポート（ADR-0006）。ドメイン関数は DateTimeOffset.Now を直接呼ばず、
/// このポートから取得した値を引数で受け取ることで純粋性を保つ。
type Clock = unit -> DateTimeOffset

/// GUID 生成の注入ポート（ADR-0006）。採番するドメイン関数はこのポートを引数で受ける。
type IdGenerator = unit -> Guid
```

識別子の採番はこのポートを通ります。

```fsharp
// src/CargoTracker.Shipper/Domain.fs
    /// ShipperId から決定的にコードを生成する。
    let generate (id: ShipperId) : ShipperCode =
        let head = (ShipperId.value id).ToString("N").Substring(0, 8).ToUpperInvariant()
        ShipperCode(sprintf "SHP-%s" head)
```

`ShipperCode` は `ShipperId` から決定的に導かれるため、`ShipperId` さえ固定できればテストでコードをアサートできます。姉妹シリーズで見た Java 実装は `UUID.randomUUID()` と `LocalDate.now()` をドメイン内で直接呼んでおり、生成値の検証ができません。

ポートを**インターフェースではなく関数型**にした点も効いています。テストで差し替えるのに実装クラスが要らず、`fun () -> knownGuid` と書くだけです。

リポジトリのように操作が複数あるものは関数のレコードです。

```fsharp
// src/CargoTracker.Shipper/Application.fs
/// 荷主リポジトリの出力ポート（関数レコード）。テストは関数リテラルで差し替える。
type ShipperRepository =
    { ExistsByEmail: Email -> Async<Result<bool, DomainError>>
      Save: Shipper -> Async<Result<unit, DomainError>> }
```

モックライブラリは使いません。テストでは必要なフィールドだけ挙動を書きます。

## 基盤：post-commit イベントと ArchUnit

IT1 の成功基準に、次の項目があります。

> ロールバック時にドメインイベントが発行されないことを統合テストで実証（`UnitOfWorkTest`）

ドメインイベントを**永続化のコミット成功後にだけ**発火させる方針が ADR-0002 として記録されています。トランザクションがロールバックされたのにイベントだけ飛ぶと、他コンテキストが存在しない予約を前提に動き出します。

```fsharp
// src/CargoTracker.Booking/Application.fs
/// ドメインイベントの発行ポート（ADR-0002）。永続化コミット成功後にのみ発火する。
/// 他コンテキストへの連携はこのポートの実装で吸収する（BC 分離）。
type BookingEventDispatcher =
    { Dispatch: BookingEvent -> Async<unit> }
```

この方針は「そう決めた」だけでは守られません。ロールバック時にイベントが出ないことをテストで固定する、というのが成功基準に書かれている理由です。

アーキテクチャ検証も IT1 から入っています。

```fsharp
// tests/CargoTracker.ArchTests/ArchTests.fs
/// 「<Context>.Domain はデータアクセス（Donald）に依存しない」ルール。
let private domainNotDependOnDonald (context: string) =
    Types()
        .That()
        .ResideInNamespace(sprintf "CargoTracker.%s.Domain" context)
        .Should()
        .NotDependOnAny(Types().That().ResideInNamespace("Donald"))
        .WithoutRequiringPositiveResults()
```

`WithoutRequiringPositiveResults()` により、まだ型が存在しないコンテキストでもルールが空マッチで通ります。**コンテキストを先に列挙しておき、型が増えたら自動的にルールが効く**構成です。

姉妹シリーズの Java 実装では、IT1 でコンテキスト間の直接依存を書いてしまい、ArchUnit を追加した IT2 まで検出できませんでした。F# 実装が IT1 からアーキテクチャテストを置いたのは、その種の事故を最初から潰す判断です。

## このイテレーションの学び

### 型の作法を最初に決める価値

IT1 で決めた「型 + モジュール + `create`/`value`」「`Result` を返す」「副作用はポート」という 3 つの作法は、以降 8 イテレーションを通して一度も変わっていません。

姉妹シリーズで見た通り、Java 実装は集約の可変・不変が時期によってばらつきました（`Cargo` は不変、`Invoice` と `TrackingRecord` は可変）。明示的なガイドラインがないと、一貫性は時間とともに失われます。

F# 実装で揺れが起きなかったのは、言語がレコードを既定で不変にしていることに加えて、**IT1 の時点で定型を作りきった**ためです。

### アウトサイドインで始める

開発戦略には、序盤の進め方が明記されています。

> 各ストーリーは `HttpHandler`／WebApplicationFactory の受け入れテストを Red にする所から着手し、UI ニーズから Command／Port を導出して薄く縦に貫通させる。

ドメインから作り始めると、UI が必要としないものを作り込む危険があります。受入テストを先に赤にすれば、**必要な型だけが必要な形で生まれます**。

このアプローチは、関数型ドメインモデリングと相性がよいものです。「型を先に設計する」という手法は、ともすると業務から遊離した型の体操になります。UI 側から要求を引くことで、型が業務に接地します。

### 見積のルート候補はスタブ

US01（輸送見積）は経路候補を必要としますが、経路候補の算出（US07・US08）は IT3 のストーリーです。IT1 ではスタブで通しました。

姉妹シリーズの Java 実装は同じ判断をしたものの、スタブと実装を差し替えるためのポートを切り忘れ、次のイテレーションで抽出作業が発生しています。

F# ではポートが関数レコードであるため、スタブを置く時点で自然に型が決まります。関数の型がそのままインターフェースなので、「後でインターフェースを切る」という作業がそもそも発生しません。

---

- 前の章：[第 1 章：関数型ドメインモデリングとは](01-functional-domain-modeling.md)
- 次の章：[第 3 章：IT2 貨物予約と特殊貨物](03-iteration-02.md)
