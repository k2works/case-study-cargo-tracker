module CargoTracker.IntegrationTests.ShipperRepositoryTests

open System
open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Shipper.Domain
open CargoTracker.Shipper.Infrastructure

// US02/US03: ShipperRepository（Donald）の統合テスト。
// Docker 不要の SQLite in-memory で実 SQL を検証する（本番の PostgreSQL は Testcontainers で別途）。

/// shipper テーブルの DDL（SQLite 方言。ANSI 標準の範囲）。
let private shipperDdl =
    """
    CREATE TABLE shipper (
        id              INTEGER PRIMARY KEY AUTOINCREMENT,
        shipper_code    TEXT    NOT NULL UNIQUE,
        shipper_type    TEXT    NOT NULL,
        name            TEXT    NOT NULL,
        email           TEXT    NOT NULL,
        phone           TEXT,
        contract_number TEXT,
        discount_rate   NUMERIC DEFAULT 0,
        created_at      TEXT    NOT NULL,
        updated_at      TEXT    NOT NULL,
        version         INTEGER NOT NULL DEFAULT 0
    );
    """

/// in-memory SQLite 接続を開き、スキーマを適用して返す。接続を閉じると DB は消える。
let private openDb () : IDbConnection =
    let conn = new SqliteConnection("Data Source=:memory:")
    conn.Open()
    use cmd = conn.CreateCommand()
    cmd.CommandText <- shipperDdl
    cmd.ExecuteNonQuery() |> ignore
    conn :> IDbConnection

let private fixedClock: Clock =
    fun () -> DateTimeOffset(2026, 7, 14, 0, 0, 0, TimeSpan.Zero)

let private makeShipper email kind =
    let id = ShipperId.ofGuid (Guid.NewGuid())

    let name =
        match ShipperName.create "テスト荷主" with
        | Ok n -> n
        | Error e -> failwithf "%A" e

    let email =
        match Email.create email with
        | Ok e -> e
        | Error e -> failwithf "%A" e

    Shipper.register id name email None None kind |> fst

[<Fact>]
[<Trait("Category", "Integration")>]
let ``荷主を保存し同一メールの存在確認ができる`` () =
    use conn = openDb ()
    let repo = ShipperRepository.create conn fixedClock
    let shipper = makeShipper "saved@example.com" Individual

    match repo.Save shipper |> Async.RunSynchronously with
    | Ok() -> ()
    | Error e -> failwithf "保存に失敗: %A" e

    match Email.create "saved@example.com" with
    | Ok email ->
        match repo.ExistsByEmail email |> Async.RunSynchronously with
        | Ok exists -> exists |> should equal true
        | Error e -> failwithf "%A" e
    | Error e -> failwithf "%A" e

[<Fact>]
[<Trait("Category", "Integration")>]
let ``未登録メールは存在しないと判定される`` () =
    use conn = openDb ()
    let repo = ShipperRepository.create conn fixedClock

    match Email.create "unknown@example.com" with
    | Ok email ->
        match repo.ExistsByEmail email |> Async.RunSynchronously with
        | Ok exists -> exists |> should equal false
        | Error e -> failwithf "%A" e
    | Error e -> failwithf "%A" e

[<Fact>]
[<Trait("Category", "Integration")>]
let ``法人荷主は契約番号と割引率が永続化される`` () =
    use conn = openDb ()
    let repo = ShipperRepository.create conn fixedClock

    let kind =
        match ContractNumber.create "CT-9", DiscountRate.create 0.15m with
        | Ok c, Ok r -> Corporate(c, r)
        | _ -> failwith "テスト前提の生成失敗"

    let shipper = makeShipper "corp@example.com" kind

    match repo.Save shipper |> Async.RunSynchronously with
    | Ok() -> ()
    | Error e -> failwithf "保存に失敗: %A" e

    // 直接クエリで永続化された割引率を検証
    use cmd = conn.CreateCommand()
    cmd.CommandText <- "SELECT discount_rate FROM shipper WHERE email = 'corp@example.com'"
    let rate = cmd.ExecuteScalar() |> Convert.ToDecimal
    rate |> should equal 0.15m
