module CargoTracker.IntegrationTests.MigrationTests

open System.IO
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Web

// タスク 1.1: DbUp による SQLite マイグレーションの起動時適用を検証する（ADR-0003）。

/// リポジトリルート（db/scripts を含む）を遡って解決する。
let private repoRoot =
    let rec findUp (dir: DirectoryInfo) =
        if isNull dir then
            failwith "CargoTracker.sln が見つかりません"
        elif File.Exists(Path.Combine(dir.FullName, "CargoTracker.sln")) then
            dir.FullName
        else
            findUp dir.Parent

    findUp (DirectoryInfo(System.AppContext.BaseDirectory))

let private scriptsRoot = Path.Combine(repoRoot, "db", "scripts")

let private tableNames (connStr: string) : string list =
    use conn = new SqliteConnection(connStr)
    conn.Open()
    use cmd = conn.CreateCommand()
    cmd.CommandText <- "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name"
    use rd = cmd.ExecuteReader()

    [ while rd.Read() do
          yield rd.GetString(0) ]

[<Fact>]
[<Trait("Category", "Integration")>]
let ``SQLite マイグレーションで全テーブルが作成される`` () =
    let dbFile =
        Path.Combine(Path.GetTempPath(), sprintf "cargo_test_%s.db" (System.Guid.NewGuid().ToString("N")))

    let connStr = sprintf "Data Source=%s" dbFile

    try
        match Db.runMigrations Db.Sqlite connStr scriptsRoot with
        | Ok() -> ()
        | Error e -> failwithf "マイグレーションに失敗: %s" e

        let tables = tableNames connStr
        tables |> should contain "users"
        tables |> should contain "user_roles"
        tables |> should contain "shipper"
        tables |> should contain "estimate"
        tables |> should contain "route_candidate"
    finally
        SqliteConnection.ClearAllPools()

        if File.Exists dbFile then
            File.Delete dbFile

[<Fact>]
[<Trait("Category", "Integration")>]
let ``既定ユーザーをシードすると sales で認証でき冪等である`` () =
    let dbFile =
        Path.Combine(Path.GetTempPath(), sprintf "cargo_seed_%s.db" (System.Guid.NewGuid().ToString("N")))

    let connStr = sprintf "Data Source=%s" dbFile

    try
        Db.runMigrations Db.Sqlite connStr scriptsRoot |> ignore
        use conn = new SqliteConnection(connStr)
        conn.Open()

        Seed.ensureDefaultUsers conn "2026-07-14T00:00:00Z"
        // 冪等: 再実行しても重複投入しない
        Seed.ensureDefaultUsers conn "2026-07-14T00:00:00Z"

        // 既定ユーザーで認証できる
        let store = Auth.UserStore.create conn

        match Auth.authenticate store Seed.DefaultUsername Seed.DefaultPassword with
        | Some user -> user.Roles |> should equal [ "ROLE_SALES" ]
        | None -> failwith "既定ユーザーで認証できるはず"

        // ユーザー数は 6 ロール分（重複なし）
        use cmd = conn.CreateCommand()
        cmd.CommandText <- "SELECT COUNT(*) FROM users"
        cmd.ExecuteScalar() |> System.Convert.ToInt32 |> should equal 6
    finally
        SqliteConnection.ClearAllPools()

        if File.Exists dbFile then
            File.Delete dbFile

[<Fact>]
[<Trait("Category", "Integration")>]
let ``マイグレーションは冪等（再適用してもエラーにならない）`` () =
    let dbFile =
        Path.Combine(Path.GetTempPath(), sprintf "cargo_idem_%s.db" (System.Guid.NewGuid().ToString("N")))

    let connStr = sprintf "Data Source=%s" dbFile

    try
        Db.runMigrations Db.Sqlite connStr scriptsRoot |> ignore

        match Db.runMigrations Db.Sqlite connStr scriptsRoot with
        | Ok() -> ()
        | Error e -> failwithf "再適用に失敗: %s" e
    finally
        SqliteConnection.ClearAllPools()

        if File.Exists dbFile then
            File.Delete dbFile
