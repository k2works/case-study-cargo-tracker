module CargoTracker.IntegrationTests.EstimateRepositoryTests

open System
open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Estimation.Domain
open CargoTracker.Estimation.Infrastructure

// US01: EstimateRepository（Donald）の統合テスト。estimate + route_candidate の 1 対多を検証。

let private ddl =
    """
    CREATE TABLE estimate (
        id                   INTEGER PRIMARY KEY AUTOINCREMENT,
        estimate_id          TEXT    NOT NULL UNIQUE,
        origin_unlocode      TEXT    NOT NULL,
        destination_unlocode TEXT    NOT NULL,
        arrival_deadline     TEXT    NOT NULL,
        cargo_type           TEXT    NOT NULL,
        weight_kg            NUMERIC NOT NULL,
        status               TEXT    NOT NULL,
        created_at           TEXT    NOT NULL,
        updated_at           TEXT    NOT NULL
    );
    CREATE TABLE route_candidate (
        id             INTEGER PRIMARY KEY AUTOINCREMENT,
        estimate_id    INTEGER NOT NULL REFERENCES estimate(id),
        voyage_number  TEXT    NOT NULL,
        transit_port   TEXT,
        transit_days   INTEGER NOT NULL,
        estimated_cost NUMERIC NOT NULL,
        rank           INTEGER NOT NULL DEFAULT 0
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
    fun () -> DateTimeOffset(2026, 7, 14, 0, 0, 0, TimeSpan.Zero)

let private loc code =
    match Location.create code with
    | Ok l -> l
    | Error e -> failwithf "%s" e

let private makeEstimate () =
    let weight =
        match WeightKg.create 500m with
        | Ok w -> w
        | Error e -> failwithf "%A" e

    let estimate =
        match
            Estimate.create (fun () -> Guid.NewGuid()) (loc "JPTYO") (loc "USLAX") (DateOnly(2026, 9, 1)) General weight
        with
        | Ok(e, _) -> e
        | Error e -> failwithf "%A" e

    let candidates =
        [ RouteCandidate.create "V001" "SGSIN" 21 120000m
          RouteCandidate.create "V002" "HKHKG" 25 110000m ]
        |> List.map (function
            | Ok c -> c
            | Error e -> failwithf "%A" e)

    match Estimate.replaceCandidates candidates estimate with
    | Ok e -> e
    | Error e -> failwithf "%A" e

[<Fact>]
[<Trait("Category", "Integration")>]
let ``見積とルート候補を保存できる`` () =
    use conn = openDb ()
    let repo = EstimateRepository.create conn fixedClock
    let estimate = makeEstimate ()

    match repo.Save estimate |> Async.RunSynchronously with
    | Ok() -> ()
    | Error e -> failwithf "保存に失敗: %A" e

    use cmd = conn.CreateCommand()
    cmd.CommandText <- "SELECT COUNT(*) FROM route_candidate"
    let count = cmd.ExecuteScalar() |> Convert.ToInt32
    count |> should equal 2

[<Fact>]
[<Trait("Category", "Integration")>]
let ``子 INSERT が失敗すると親 estimate も永続化されない（トランザクション原子性）`` () =
    use conn = openDb ()
    // route_candidate.transit_days に NOT NULL 制約違反を起こすため列を落とした不整合スキーマを再作成
    use drop = conn.CreateCommand()

    drop.CommandText <-
        "DROP TABLE route_candidate; CREATE TABLE route_candidate (id INTEGER PRIMARY KEY, estimate_id INTEGER NOT NULL, voyage_number TEXT NOT NULL, transit_port TEXT, transit_days INTEGER NOT NULL, estimated_cost NUMERIC NOT NULL, rank INTEGER NOT NULL, extra TEXT NOT NULL)"

    drop.ExecuteNonQuery() |> ignore

    let repo = EstimateRepository.create conn fixedClock
    let estimate = makeEstimate ()

    // extra 列（NOT NULL・INSERT で未指定）により子 INSERT が失敗する
    match repo.Save estimate |> Async.RunSynchronously with
    | Error _ -> ()
    | Ok() -> failwith "子 INSERT 失敗で Error になるはず"

    // 親 estimate はロールバックされ 0 件であること
    use cmd = conn.CreateCommand()
    cmd.CommandText <- "SELECT COUNT(*) FROM estimate"
    cmd.ExecuteScalar() |> Convert.ToInt32 |> should equal 0

[<Fact>]
[<Trait("Category", "Integration")>]
let ``ルート候補は rank が 1 始まりで採番される`` () =
    use conn = openDb ()
    let repo = EstimateRepository.create conn fixedClock
    let estimate = makeEstimate ()

    repo.Save estimate |> Async.RunSynchronously |> ignore

    use cmd = conn.CreateCommand()
    cmd.CommandText <- "SELECT voyage_number FROM route_candidate ORDER BY rank ASC LIMIT 1"
    let firstVoyage = cmd.ExecuteScalar() |> string
    firstVoyage |> should equal "V001"
