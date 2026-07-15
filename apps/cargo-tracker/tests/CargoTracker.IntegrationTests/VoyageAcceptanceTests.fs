module CargoTracker.IntegrationTests.VoyageAcceptanceTests

// WebHostBuilder / TestServer は受け入れテスト用の正当なパターン。非推奨（FS0044）は抑制する。
#nowarn "44"

open System.IO
open System.Net
open System.Net.Http
open System.Threading.Tasks
open Microsoft.AspNetCore.Hosting
open Microsoft.AspNetCore.TestHost
open Microsoft.Extensions.Configuration
open Microsoft.Extensions.DependencyInjection
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Web

// US24/US07: 航路管理画面の受け入れテスト（ROLE_ROUTE_DESIGNER 認可・登録・ナビゲーション整合性）。

let private repoRoot =
    let rec findUp (dir: DirectoryInfo) =
        if isNull dir then
            failwith "sln 未検出"
        elif File.Exists(Path.Combine(dir.FullName, "CargoTracker.sln")) then
            dir.FullName
        else
            findUp dir.Parent

    findUp (DirectoryInfo(System.AppContext.BaseDirectory))

let private run (t: Task<'a>) = t.GetAwaiter().GetResult()

let private seedDatabase (connStr: string) =
    match Db.runMigrations Db.Sqlite connStr (Path.Combine(repoRoot, "db", "scripts")) with
    | Ok() -> ()
    | Error e -> failwithf "マイグレーション失敗: %s" e

    use conn = new SqliteConnection(connStr)
    conn.Open()
    let hash = Auth.Password.hash "pw"
    use cmd = conn.CreateCommand()

    cmd.CommandText <-
        sprintf
            """
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('designer01', 'designer01@example.com', '%s', 1, '2026-08-11');
            INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_ROUTE_DESIGNER');
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('sales01', 'sales01@example.com', '%s', 1, '2026-08-11');
            INSERT INTO user_roles (user_id, role) VALUES (2, 'ROLE_SALES');
            """
            hash
            hash

    cmd.ExecuteNonQuery() |> ignore

let private buildServer (connStr: string) : TestServer =
    let settings =
        dict [ "Database:Provider", "sqlite"; "Database:ConnectionString", connStr ]

    let config =
        ConfigurationBuilder().AddInMemoryCollection(settings).Build() :> IConfiguration

    let builder =
        WebHostBuilder()
            .ConfigureServices(fun services -> App.configureServices config services)
            .Configure(fun app -> App.configureApp app)

    new TestServer(builder)

let private withServer (test: HttpClient -> unit) =
    let dbFile =
        Path.Combine(Path.GetTempPath(), sprintf "cargo_voy_%s.db" (System.Guid.NewGuid().ToString("N")))

    let connStr = sprintf "Data Source=%s" dbFile
    seedDatabase connStr
    use server = buildServer connStr
    let client = server.CreateClient()

    try
        test client
    finally
        server.Dispose()
        SqliteConnection.ClearAllPools()

        if File.Exists dbFile then
            File.Delete dbFile

let private authCookie (client: HttpClient) (username: string) : string =
    let form =
        new FormUrlEncodedContent(dict [ "username", username; "password", "pw" ])

    let res = run (client.PostAsync("/login", form))
    (res.Headers.GetValues "Set-Cookie" |> Seq.head).Split(';').[0]

let private authedGet (client: HttpClient) (cookie: string) (path: string) =
    use req = new HttpRequestMessage(HttpMethod.Get, path)
    req.Headers.Add("Cookie", cookie)
    run (client.SendAsync req)

let private authedPost (client: HttpClient) (cookie: string) (path: string) (fields: (string * string) list) =
    use req = new HttpRequestMessage(HttpMethod.Post, path)
    req.Headers.Add("Cookie", cookie)
    req.Content <- new FormUrlEncodedContent(dict fields)
    run (client.SendAsync req)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``経路設計者は航路一覧を表示できる`` () =
    withServer (fun client ->
        let cookie = authCookie client "designer01"
        let res = authedGet client cookie "/voyages"
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "航路一覧")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``営業ロールは航路管理にアクセスできない（403）`` () =
    withServer (fun client ->
        let cookie = authCookie client "sales01"
        let res = authedGet client cookie "/voyages"
        res.StatusCode |> should equal HttpStatusCode.Forbidden)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``航海スケジュールを登録すると一覧に表示される`` () =
    withServer (fun client ->
        let cookie = authCookie client "designer01"

        let res =
            authedPost
                client
                cookie
                "/voyages"
                [ "voyageNumber", "V001"
                  "vesselName", "Ever Given"
                  "carrierName", "Evergreen"
                  "cargoGeneral", "true"
                  "leg1Dep", "JPTYO"
                  "leg1Arr", "USLAX"
                  "leg1DepDate", "2026-09-01T00:00"
                  "leg1ArrDate", "2026-09-20T00:00" ]

        res.StatusCode |> should equal HttpStatusCode.Found

        let list = authedGet client cookie "/voyages"
        let body = run (list.Content.ReadAsStringAsync())
        body |> should haveSubstring "V001"
        body |> should haveSubstring "Ever Given")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``日付が逆転した区間の登録は 400 になる`` () =
    withServer (fun client ->
        let cookie = authCookie client "designer01"

        let res =
            authedPost
                client
                cookie
                "/voyages"
                [ "voyageNumber", "V002"
                  "vesselName", "V"
                  "carrierName", "C"
                  "cargoGeneral", "true"
                  "leg1Dep", "JPTYO"
                  "leg1Arr", "USLAX"
                  "leg1DepDate", "2026-09-20T00:00"
                  "leg1ArrDate", "2026-09-01T00:00" ]

        res.StatusCode |> should equal HttpStatusCode.BadRequest)

/// 航海 V001 を登録する。
let private registerV001 (client: HttpClient) (cookie: string) =
    authedPost
        client
        cookie
        "/voyages"
        [ "voyageNumber", "V001"
          "vesselName", "Ever Given"
          "carrierName", "Evergreen"
          "cargoGeneral", "true"
          "leg1Dep", "JPTYO"
          "leg1Arr", "USLAX"
          "leg1DepDate", "2026-09-01T00:00"
          "leg1ArrDate", "2026-09-20T00:00" ]
    |> ignore

[<Fact>]
[<Trait("Category", "Integration")>]
let ``航海更新フォームに既存内容が表示される`` () =
    withServer (fun client ->
        let cookie = authCookie client "designer01"
        registerV001 client cookie
        let res = authedGet client cookie "/voyages/V001/edit"
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "航海スケジュール更新"
        body |> should haveSubstring "Ever Given"
        body |> should haveSubstring "JPTYO")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``航海を更新すると一覧に反映される`` () =
    withServer (fun client ->
        let cookie = authCookie client "designer01"
        registerV001 client cookie

        let res =
            authedPost
                client
                cookie
                "/voyages/V001/edit"
                [ "voyageNumber", "V001"
                  "vesselName", "MSC Oscar"
                  "carrierName", "Evergreen"
                  "cargoGeneral", "true"
                  "leg1Dep", "JPTYO"
                  "leg1Arr", "USLAX"
                  "leg1DepDate", "2026-09-01T00:00"
                  "leg1ArrDate", "2026-09-20T00:00" ]

        res.StatusCode |> should equal HttpStatusCode.Found

        let list = authedGet client cookie "/voyages"
        let body = run (list.Content.ReadAsStringAsync())
        body |> should haveSubstring "MSC Oscar")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``存在しない航海の更新フォームは 404`` () =
    withServer (fun client ->
        let cookie = authCookie client "designer01"
        let res = authedGet client cookie "/voyages/NOPE/edit"
        res.StatusCode |> should equal HttpStatusCode.NotFound)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``ナビゲーション整合性: 経路設計者のダッシュボードに航路管理導線がある`` () =
    withServer (fun client ->
        let cookie = authCookie client "designer01"
        let dashboard = authedGet client cookie "/"
        let body = run (dashboard.Content.ReadAsStringAsync())
        body |> should haveSubstring "航路管理"
        body |> should haveSubstring "/voyages")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``ナビゲーション整合性: 営業ロールのダッシュボードに航路管理導線がない`` () =
    withServer (fun client ->
        let cookie = authCookie client "sales01"
        let dashboard = authedGet client cookie "/"
        let body = run (dashboard.Content.ReadAsStringAsync())
        body |> should not' (haveSubstring "/voyages"))
