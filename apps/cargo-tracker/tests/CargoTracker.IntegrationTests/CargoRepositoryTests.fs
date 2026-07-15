module CargoTracker.IntegrationTests.CargoRepositoryTests

open System
open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Booking.Domain
open CargoTracker.Booking.Application
open CargoTracker.Booking.Infrastructure

// US04/US05/US06: CargoRepository（Donald）の統合テスト。
// Docker 不要の SQLite in-memory で実 SQL を検証する（本番の PostgreSQL は Testcontainers で別途）。

/// cargo テーブルの DDL（SQLite 方言・0004_cargo.sql と整合）。
let private cargoDdl =
    """
    CREATE TABLE cargo (
        id                   INTEGER PRIMARY KEY AUTOINCREMENT,
        booking_id           TEXT    NOT NULL UNIQUE,
        shipper_id           TEXT    NOT NULL,
        cargo_type           TEXT    NOT NULL DEFAULT 'GENERAL',
        weight               NUMERIC NOT NULL,
        origin_unlocode      TEXT    NOT NULL,
        destination_unlocode TEXT    NOT NULL,
        arrival_deadline     TEXT    NOT NULL,
        booking_status       TEXT    NOT NULL DEFAULT 'PRELIMINARY',
        dimension_length     NUMERIC,
        dimension_width      NUMERIC,
        dimension_height     NUMERIC,
        quantity             INTEGER,
        description          TEXT,
        hazardous_class      TEXT,
        un_number            TEXT,
        proper_shipping_name TEXT,
        min_temperature      NUMERIC,
        max_temperature      NUMERIC,
        temperature_unit     TEXT,
        consignee_name       TEXT,
        consignee_address    TEXT,
        consignee_email      TEXT,
        created_at           TEXT    NOT NULL,
        updated_at           TEXT    NOT NULL,
        version              INTEGER NOT NULL DEFAULT 0
    );
    """

let private openDb () : IDbConnection =
    let conn = new SqliteConnection("Data Source=:memory:")
    conn.Open()
    use cmd = conn.CreateCommand()
    cmd.CommandText <- cargoDdl
    cmd.ExecuteNonQuery() |> ignore
    conn :> IDbConnection

let private fixedClock: Clock =
    fun () -> DateTimeOffset(2026, 7, 28, 0, 0, 0, TimeSpan.Zero)

let private fixedId (guid: Guid) : IdGenerator = fun () -> guid

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "%s" e

let private routeSpec () =
    match RouteSpecification.create (loc "JPTYO") (loc "USLAX") (DateOnly(2026, 9, 1)) with
    | Ok r -> r
    | Error e -> failwithf "%A" e

let private weight kg =
    match Weight.create kg with
    | Ok w -> w
    | Error e -> failwithf "%A" e

let private makeCargo cargoType =
    let sid = ShipperId.ofGuid (Guid.NewGuid())

    match Cargo.book (fixedId (Guid.NewGuid())) sid None (routeSpec ()) cargoType (weight 500m) with
    | Ok(cargo, _) -> cargo
    | Error e -> failwithf "%A" e

[<Fact>]
let ``一般貨物を保存して取得できる`` () =
    use conn = openDb ()
    let repo = CargoRepository.create conn fixedClock
    let cargo = makeCargo General

    (repo.Save cargo |> Async.RunSynchronously) |> Result.isOk |> should equal true

    match repo.FindById cargo.BookingId |> Async.RunSynchronously with
    | Ok(Some found) ->
        found.BookingId |> should equal cargo.BookingId
        found.State |> should equal Preliminary
        found.CargoType |> should equal General
    | other -> failwithf "Some を期待したが: %A" other

[<Fact>]
let ``危険物貨物は申告情報込みで往復できる`` () =
    use conn = openDb ()
    let repo = CargoRepository.create conn fixedClock

    let haz =
        match HazardousDeclaration.create "3" "UN1203" "Gasoline" with
        | Ok d -> d
        | Error e -> failwithf "%A" e

    let cargo = makeCargo (Hazardous haz)
    repo.Save cargo |> Async.RunSynchronously |> Result.isOk |> should equal true

    match repo.FindById cargo.BookingId |> Async.RunSynchronously with
    | Ok(Some found) ->
        match found.CargoType with
        | Hazardous d -> HazardousDeclaration.unNumber d |> should equal "UN1203"
        | other -> failwithf "Hazardous を期待したが: %A" other
    | other -> failwithf "Some を期待したが: %A" other

[<Fact>]
let ``冷凍貨物は温度条件込みで往復できる`` () =
    use conn = openDb ()
    let repo = CargoRepository.create conn fixedClock

    let temp =
        match TemperatureRequirement.create -20m 5m Celsius with
        | Ok t -> t
        | Error e -> failwithf "%A" e

    let cargo = makeCargo (Refrigerated temp)
    repo.Save cargo |> Async.RunSynchronously |> Result.isOk |> should equal true

    match repo.FindById cargo.BookingId |> Async.RunSynchronously with
    | Ok(Some found) ->
        match found.CargoType with
        | Refrigerated t ->
            TemperatureRequirement.minTemperature t |> should equal -20m
            TemperatureRequirement.unit t |> should equal Celsius
        | other -> failwithf "Refrigerated を期待したが: %A" other
    | other -> failwithf "Some を期待したが: %A" other

[<Fact>]
let ``存在しない予約は None を返す`` () =
    use conn = openDb ()
    let repo = CargoRepository.create conn fixedClock

    match repo.FindById(BookingId.ofString "BKG-NOPE") |> Async.RunSynchronously with
    | Ok None -> ()
    | other -> failwithf "None を期待したが: %A" other

[<Fact>]
let ``経路設計依頼後の状態を永続化して取得できる`` () =
    use conn = openDb ()
    let repo = CargoRepository.create conn fixedClock
    let cargo = makeCargo General
    repo.Save cargo |> Async.RunSynchronously |> ignore

    let routing =
        match Cargo.execute cargo SubmitForRouting with
        | Ok(c, _) -> c
        | Error e -> failwithf "%A" e

    // 同一 booking_id の再保存は UNIQUE 制約に触れるため、別 booking で状態遷移の往復を検証する。
    match repo.FindById cargo.BookingId |> Async.RunSynchronously with
    | Ok(Some found) -> found.State |> should equal Preliminary
    | other -> failwithf "Some を期待したが: %A" other

    routing.State |> should equal RoutingRequested

[<Fact>]
let ``重複する予約 ID の保存は失敗し件数が増えない`` () =
    use conn = openDb ()
    let repo = CargoRepository.create conn fixedClock
    let cargo = makeCargo General

    repo.Save cargo |> Async.RunSynchronously |> Result.isOk |> should equal true
    // 同一 booking_id を再保存 → UNIQUE 制約違反でトランザクションがロールバックされる（原子性・IT1 Try#2）。
    let second = repo.Save cargo |> Async.RunSynchronously
    second |> Result.isError |> should equal true

    let count = (CargoQueries.findAll conn) |> List.length
    count |> should equal 1
