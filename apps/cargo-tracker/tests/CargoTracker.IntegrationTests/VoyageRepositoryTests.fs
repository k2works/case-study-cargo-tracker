module CargoTracker.IntegrationTests.VoyageRepositoryTests

open System
open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Routing.Domain
open CargoTracker.Routing.Application
open CargoTracker.Routing.Infrastructure

// US24/US25: VoyageRepository（Donald）の統合テスト。voyage + carrier_movement 親子。

let private voyageDdl =
    """
    CREATE TABLE voyage (
        id                    INTEGER PRIMARY KEY AUTOINCREMENT,
        voyage_number         TEXT    NOT NULL UNIQUE,
        vessel_name           TEXT    NOT NULL,
        carrier_name          TEXT    NOT NULL,
        supported_cargo_types TEXT    NOT NULL,
        created_at            TEXT    NOT NULL,
        updated_at            TEXT    NOT NULL,
        version               INTEGER NOT NULL DEFAULT 0
    );
    CREATE TABLE carrier_movement (
        id                          INTEGER PRIMARY KEY AUTOINCREMENT,
        voyage_id                   INTEGER NOT NULL REFERENCES voyage(id),
        departure_location_unlocode TEXT    NOT NULL,
        arrival_location_unlocode   TEXT    NOT NULL,
        departure_date              TEXT    NOT NULL,
        arrival_date                TEXT    NOT NULL,
        seq_number                  INTEGER NOT NULL,
        created_at                  TEXT    NOT NULL,
        updated_at                  TEXT    NOT NULL
    );
    """

let private openDb () : IDbConnection =
    let conn = new SqliteConnection("Data Source=:memory:")
    conn.Open()
    use cmd = conn.CreateCommand()
    cmd.CommandText <- voyageDdl
    cmd.ExecuteNonQuery() |> ignore
    conn :> IDbConnection

let private fixedClock: Clock =
    fun () -> DateTimeOffset(2026, 8, 11, 0, 0, 0, TimeSpan.Zero)

let private dt (y, m, d) =
    DateTimeOffset(y, m, d, 0, 0, 0, TimeSpan.Zero)

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "%s" e

let private mv dep arr departDate arriveDate seq =
    match CarrierMovement.create (loc dep) (loc arr) departDate arriveDate seq with
    | Ok m -> m
    | Error e -> failwithf "%A" e

let private makeVoyage vn movements tags =
    let sched =
        match Schedule.create movements with
        | Ok s -> s
        | Error e -> failwithf "%A" e

    let vnv =
        match VoyageNumber.create vn with
        | Ok v -> v
        | Error e -> failwithf "%A" e

    let vessel =
        (VesselName.create "V")
        |> function
            | Ok v -> v
            | Error e -> failwithf "%A" e

    let carrier =
        (CarrierName.create "C")
        |> function
            | Ok c -> c
            | Error e -> failwithf "%A" e

    match Voyage.register vnv vessel carrier sched (Set.ofList tags) with
    | Ok(v, _) -> v
    | Error e -> failwithf "%A" e

[<Fact>]
let ``単一区間の航海を保存して取得できる`` () =
    use conn = openDb ()
    let repo = VoyageRepository.create conn fixedClock

    let v =
        makeVoyage "V001" [ mv "JPTYO" "USLAX" (dt (2026, 9, 1)) (dt (2026, 9, 20)) 1 ] [ General ]

    repo.Save v |> Async.RunSynchronously |> Result.isOk |> should equal true

    match repo.FindByNumber v.VoyageNumber |> Async.RunSynchronously with
    | Ok(Some found) ->
        Location.value (Schedule.origin found.Schedule) |> should equal "JPTYO"
        Location.value (Schedule.destination found.Schedule) |> should equal "USLAX"
        Voyage.supports General found |> should equal true
    | other -> failwithf "Some を期待したが: %A" other

[<Fact>]
let ``複数区間の航海を順序付きで往復できる`` () =
    use conn = openDb ()
    let repo = VoyageRepository.create conn fixedClock

    let v =
        makeVoyage
            "V002"
            [ mv "JPTYO" "SGSIN" (dt (2026, 9, 1)) (dt (2026, 9, 8)) 1
              mv "SGSIN" "USLAX" (dt (2026, 9, 9)) (dt (2026, 9, 25)) 2 ]
            [ General; Refrigerated ]

    repo.Save v |> Async.RunSynchronously |> Result.isOk |> should equal true

    match repo.FindByNumber v.VoyageNumber |> Async.RunSynchronously with
    | Ok(Some found) ->
        Schedule.movements found.Schedule |> List.length |> should equal 2
        Location.value (Schedule.destination found.Schedule) |> should equal "USLAX"
        Voyage.supports Refrigerated found |> should equal true
    | other -> failwithf "Some を期待したが: %A" other

[<Fact>]
let ``存在しない航海は None を返す`` () =
    use conn = openDb ()
    let repo = VoyageRepository.create conn fixedClock

    let vn =
        (VoyageNumber.create "NOPE")
        |> function
            | Ok v -> v
            | Error e -> failwithf "%A" e

    match repo.FindByNumber vn |> Async.RunSynchronously with
    | Ok None -> ()
    | other -> failwithf "None を期待したが: %A" other

[<Fact>]
let ``航海を更新すると区間が入れ替わる`` () =
    use conn = openDb ()
    let repo = VoyageRepository.create conn fixedClock

    let v =
        makeVoyage "V001" [ mv "JPTYO" "USLAX" (dt (2026, 9, 1)) (dt (2026, 9, 20)) 1 ] [ General ]

    repo.Save v |> Async.RunSynchronously |> ignore

    // 経由地ありのスケジュールへ更新
    let updated =
        makeVoyage
            "V001"
            [ mv "JPTYO" "SGSIN" (dt (2026, 9, 1)) (dt (2026, 9, 8)) 1
              mv "SGSIN" "USLAX" (dt (2026, 9, 9)) (dt (2026, 9, 25)) 2 ]
            [ General ]

    repo.Update updated
    |> Async.RunSynchronously
    |> Result.isOk
    |> should equal true

    match repo.FindByNumber v.VoyageNumber |> Async.RunSynchronously with
    | Ok(Some found) -> Schedule.movements found.Schedule |> List.length |> should equal 2
    | other -> failwithf "Some を期待したが: %A" other

[<Fact>]
let ``FindAll で全航海を取得できる`` () =
    use conn = openDb ()
    let repo = VoyageRepository.create conn fixedClock

    repo.Save(makeVoyage "V001" [ mv "JPTYO" "USLAX" (dt (2026, 9, 1)) (dt (2026, 9, 20)) 1 ] [ General ])
    |> Async.RunSynchronously
    |> ignore

    repo.Save(makeVoyage "V002" [ mv "JPTYO" "SGSIN" (dt (2026, 9, 1)) (dt (2026, 9, 8)) 1 ] [ General ])
    |> Async.RunSynchronously
    |> ignore

    match repo.FindAll() |> Async.RunSynchronously with
    | Ok voyages -> voyages |> List.length |> should equal 2
    | Error e -> failwithf "%A" e
