module CargoTracker.IntegrationTests.ShipperExistenceAdapterTests

open System
open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Shipper.Domain
open CargoTracker.Shipper.Infrastructure
open CargoTracker.Booking.Infrastructure

// ADR-0008: ShipperExistenceChecker アダプタ（shipper_uuid による荷主存在確認）の統合テスト。

let private shipperDdl =
    """
    CREATE TABLE shipper (
        id              INTEGER PRIMARY KEY AUTOINCREMENT,
        shipper_code    TEXT    NOT NULL UNIQUE,
        shipper_uuid    TEXT,
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

let private openDb () : IDbConnection =
    let conn = new SqliteConnection("Data Source=:memory:")
    conn.Open()
    use cmd = conn.CreateCommand()
    cmd.CommandText <- shipperDdl
    cmd.ExecuteNonQuery() |> ignore
    conn :> IDbConnection

let private fixedClock: Clock =
    fun () -> DateTimeOffset(2026, 7, 28, 0, 0, 0, TimeSpan.Zero)

let private registerShipper (conn: IDbConnection) (id: ShipperId) =
    let repo = ShipperRepository.create conn fixedClock

    let name =
        match ShipperName.create "テスト荷主" with
        | Ok n -> n
        | Error e -> failwithf "%A" e

    let email =
        match Email.create "s@example.com" with
        | Ok e -> e
        | Error e -> failwithf "%A" e

    let shipper, _ = Shipper.register id name email None None Individual
    repo.Save shipper |> Async.RunSynchronously |> Result.isOk |> should equal true

[<Fact>]
let ``登録済みの ShipperId は存在確認で true を返す`` () =
    use conn = openDb ()
    let id = ShipperId.ofGuid (Guid.NewGuid())
    registerShipper conn id

    let checker = ShipperExistenceAdapter.create conn

    match checker.Exists id |> Async.RunSynchronously with
    | Ok true -> ()
    | other -> failwithf "Ok true を期待したが: %A" other

[<Fact>]
let ``未登録の ShipperId は存在確認で false を返す`` () =
    use conn = openDb ()
    registerShipper conn (ShipperId.ofGuid (Guid.NewGuid()))

    let checker = ShipperExistenceAdapter.create conn
    let unknown = ShipperId.ofGuid (Guid.NewGuid())

    match checker.Exists unknown |> Async.RunSynchronously with
    | Ok false -> ()
    | other -> failwithf "Ok false を期待したが: %A" other
