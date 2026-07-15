module CargoTracker.IntegrationTests.HandlingRepositoryTests

open System
open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Handling.Domain
open CargoTracker.Handling.Application
open CargoTracker.Handling.Infrastructure

// US15/US16: HandlingRepository（Donald）と履歴クエリの統合テスト。

let private ddl =
    """
    CREATE TABLE handling_activity (
        id                     INTEGER PRIMARY KEY AUTOINCREMENT,
        booking_id             TEXT    NOT NULL,
        event_type             TEXT    NOT NULL,
        event_completion_time  TEXT    NOT NULL,
        location_unlocode      TEXT    NOT NULL,
        voyage_number          TEXT,
        consignee_confirmation TEXT,
        operator_name          TEXT,
        created_at             TEXT    NOT NULL,
        updated_at             TEXT    NOT NULL,
        version                INTEGER NOT NULL DEFAULT 0
    );
    """

let private openDb () : IDbConnection =
    let conn = new SqliteConnection("Data Source=:memory:")
    conn.Open()
    use cmd = conn.CreateCommand()
    cmd.CommandText <- ddl
    cmd.ExecuteNonQuery() |> ignore
    conn :> IDbConnection

let private fixedClock: Clock =
    fun () -> DateTimeOffset(2026, 9, 10, 0, 0, 0, TimeSpan.Zero)

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "%s" e

let private voyage n =
    match VoyageNumber.create n with
    | Ok v -> v
    | Error e -> failwithf "%A" e

let private now = DateTimeOffset(2026, 9, 10, 0, 0, 0, TimeSpan.Zero)

let private snapshot () =
    { BookingId = "BKG-0001"
      Origin = "JPTYO"
      Destination = "USLAX"
      ItineraryLegs =
        [ { LoadLocation = "JPTYO"
            UnloadLocation = "USLAX"
            VoyageNumber = "V001" } ] }

[<Fact>]
[<Trait("Category", "Integration")>]
let ``荷役作業を保存して履歴で取得できる`` () =
    use conn = openDb ()
    let repo = HandlingRepository.create conn fixedClock

    let activity, _, _ =
        match HandlingActivity.register (snapshot ()) Receive (loc "JPTYO") now None with
        | Ok r -> r
        | Error e -> failwithf "%A" e

    repo.Save activity |> Async.RunSynchronously |> Result.isOk |> should equal true

    let rows = HandlingQueries.findAll conn
    rows |> List.length |> should equal 1
    rows.[0].HandlingType |> should equal "RECEIVE"
    rows.[0].BookingId |> should equal "BKG-0001"

[<Fact>]
[<Trait("Category", "Integration")>]
let ``積込作業は航海番号込みで保存される`` () =
    use conn = openDb ()
    let repo = HandlingRepository.create conn fixedClock

    let activity, _, _ =
        match HandlingActivity.register (snapshot ()) (Load(voyage "V001")) (loc "JPTYO") now None with
        | Ok r -> r
        | Error e -> failwithf "%A" e

    repo.Save activity |> Async.RunSynchronously |> ignore

    let rows = HandlingQueries.findAll conn
    rows.[0].HandlingType |> should equal "LOAD"
    rows.[0].VoyageNumber |> should equal (Some "V001")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``引取作業は荷受人確認込みで保存される`` () =
    use conn = openDb ()
    let repo = HandlingRepository.create conn fixedClock

    let activity, _, _ =
        match HandlingActivity.register (snapshot ()) Claim (loc "USLAX") now (Some "確認コード:1234") with
        | Ok r -> r
        | Error e -> failwithf "%A" e

    repo.Save activity |> Async.RunSynchronously |> Result.isOk |> should equal true
    HandlingQueries.findAll conn |> List.length |> should equal 1
