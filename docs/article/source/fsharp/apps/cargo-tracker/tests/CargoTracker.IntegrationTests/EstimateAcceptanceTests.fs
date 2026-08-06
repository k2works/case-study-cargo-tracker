module CargoTracker.IntegrationTests.EstimateAcceptanceTests

// WebHostBuilder / TestServer は受け入れテスト用の正当なパターン。非推奨（FS0044）は抑制する。
#nowarn "44"

open System.IO
open System.Net
open System.Net.Http
open System.Threading.Tasks
open Microsoft.AspNetCore.Hosting
open Microsoft.AspNetCore.TestHost
open Microsoft.Extensions.Configuration
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Web

// タスク 4.3/4.4: 見積作成画面の受け入れテスト（ROLE_SALES・スタブ経路・異常系）。

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
            VALUES ('sales01', 'sales01@example.com', '%s', 1, '2026-07-14');
            INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_SALES');
            """
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
        Path.Combine(Path.GetTempPath(), sprintf "cargo_est_%s.db" (System.Guid.NewGuid().ToString("N")))

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

let private authCookie (client: HttpClient) : string =
    let form =
        new FormUrlEncodedContent(dict [ "username", "sales01"; "password", "pw" ])

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

let private validEstimate =
    [ "originUnlocode", "JPTYO"
      "destinationUnlocode", "USLAX"
      "arrivalDeadline", "2026-09-01"
      "cargoType", "GENERAL"
      "weightKg", "500" ]

[<Fact>]
[<Trait("Category", "Integration")>]
let ``見積を作成するとスタブ候補付きで一覧に表示される`` () =
    withServer (fun client ->
        let cookie = authCookie client
        let res = authedPost client cookie "/estimates" validEstimate
        res.StatusCode |> should equal HttpStatusCode.Found
        res.Headers.Location.OriginalString |> should equal "/estimates"

        let listBody =
            run ((authedGet client cookie "/estimates").Content.ReadAsStringAsync())

        listBody |> should haveSubstring "JPTYO → USLAX"
        // スタブ経路サービスが 2 候補を返す
        listBody |> should haveSubstring "見積一覧")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``出発地と目的地が同一なら 400 とエラーを返す`` () =
    withServer (fun client ->
        let cookie = authCookie client

        let res =
            authedPost
                client
                cookie
                "/estimates"
                [ "originUnlocode", "JPTYO"
                  "destinationUnlocode", "JPTYO"
                  "arrivalDeadline", "2026-09-01"
                  "cargoType", "GENERAL"
                  "weightKg", "500" ]

        res.StatusCode |> should equal HttpStatusCode.BadRequest
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "同一地点")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``不正な UN/LOCODE は 400 とエラーを返す`` () =
    withServer (fun client ->
        let cookie = authCookie client

        let res =
            authedPost
                client
                cookie
                "/estimates"
                [ "originUnlocode", "JP"
                  "destinationUnlocode", "USLAX"
                  "arrivalDeadline", "2026-09-01"
                  "cargoType", "GENERAL"
                  "weightKg", "500" ]

        res.StatusCode |> should equal HttpStatusCode.BadRequest)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``見積作成画面は認証と ROLE_SALES を要求する`` () =
    withServer (fun client ->
        // 未認証はログインへリダイレクト
        let res = run (client.GetAsync "/estimates/new")
        res.StatusCode |> should equal HttpStatusCode.Found
        res.Headers.Location.OriginalString |> should haveSubstring "/login")
