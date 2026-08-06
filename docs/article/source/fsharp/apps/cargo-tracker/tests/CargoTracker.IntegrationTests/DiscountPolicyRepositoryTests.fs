module CargoTracker.IntegrationTests.DiscountPolicyRepositoryTests

open System
open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Billing.Domain
open CargoTracker.Billing.Infrastructure

// US-ADM-01: 割引ポリシーマスタの永続化（登録・更新・無効化・有効判定）。

let private ddl =
    """
    CREATE TABLE discount_policy (
        id                   INTEGER PRIMARY KEY AUTOINCREMENT,
        policy_type          TEXT    NOT NULL,
        discount_rate        NUMERIC NOT NULL,
        applicable_condition TEXT,
        effective_from       TEXT    NOT NULL,
        effective_to         TEXT,
        active               INTEGER NOT NULL DEFAULT 1,
        created_at           TEXT    NOT NULL,
        updated_at           TEXT    NOT NULL
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
    fun () -> DateTimeOffset(2026, 10, 6, 0, 0, 0, TimeSpan.Zero)

let private rate v =
    match DiscountRate.create v with
    | Ok r -> r
    | Error e -> failwithf "%A" e

let private master () =
    DiscountPolicyMaster.create
        CorporateStandard
        (rate 0.10m)
        "法人標準契約"
        (DateOnly(2026, 10, 1))
        (Some(DateOnly(2026, 12, 31)))

[<Fact>]
[<Trait("Category", "Integration")>]
let ``割引ポリシーを保存して ID で復元できる`` () =
    use conn = openDb ()
    let repo = DiscountPolicyRepository.create conn fixedClock

    let id =
        match repo.Save(master ()) |> Async.RunSynchronously with
        | Ok id -> id
        | Error e -> failwithf "%A" e

    id |> should be (greaterThan 0L)

    match repo.FindById id |> Async.RunSynchronously with
    | Ok(Some m) ->
        m.Policy |> should equal CorporateStandard
        DiscountRate.value m.Rate |> should equal 0.10m
        m.Active |> should equal true
        m.EffectiveFrom |> should equal (DateOnly(2026, 10, 1))
    | other -> failwithf "Some を期待したが: %A" other

[<Fact>]
[<Trait("Category", "Integration")>]
let ``無効化したポリシーは有効一覧から除外される（US-ADM-01 受入5）`` () =
    use conn = openDb ()
    let repo = DiscountPolicyRepository.create conn fixedClock

    let id =
        match repo.Save(master ()) |> Async.RunSynchronously with
        | Ok id -> id
        | Error e -> failwithf "%A" e

    // 有効日 10/6 は有効期間内なので有効一覧に含まれる
    match repo.FindEffective(DateOnly(2026, 10, 6)) |> Async.RunSynchronously with
    | Ok list -> list |> List.length |> should equal 1
    | Error e -> failwithf "%A" e

    // 無効化
    let stored =
        match repo.FindById id |> Async.RunSynchronously with
        | Ok(Some m) -> m
        | other -> failwithf "%A" other

    repo.Update(DiscountPolicyMaster.deactivate stored)
    |> Async.RunSynchronously
    |> Result.isOk
    |> should equal true

    match repo.FindEffective(DateOnly(2026, 10, 6)) |> Async.RunSynchronously with
    | Ok list -> list |> List.isEmpty |> should equal true
    | Error e -> failwithf "%A" e

[<Fact>]
[<Trait("Category", "Integration")>]
let ``有効期間外の日付では有効一覧に含まれない`` () =
    use conn = openDb ()
    let repo = DiscountPolicyRepository.create conn fixedClock
    repo.Save(master ()) |> Async.RunSynchronously |> ignore

    // 期間（10/1〜12/31）より後
    match repo.FindEffective(DateOnly(2027, 1, 1)) |> Async.RunSynchronously with
    | Ok list -> list |> List.isEmpty |> should equal true
    | Error e -> failwithf "%A" e
