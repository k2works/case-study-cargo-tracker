module CargoTracker.IntegrationTests.BookingAcceptanceTests

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

// US04: 貨物予約一覧画面の受け入れテスト（ROLE_SALES / ROLE_SHIPPER 認可・ナビゲーション整合性）。

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

/// 営業・荷主・荷役ユーザーを 1 名ずつ投入する。
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
            VALUES ('sales01', 'sales01@example.com', '%s', 1, '2026-07-28');
            INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_SALES');
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('handler01', 'handler01@example.com', '%s', 1, '2026-07-28');
            INSERT INTO user_roles (user_id, role) VALUES (2, 'ROLE_HANDLER');
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
        Path.Combine(Path.GetTempPath(), sprintf "cargo_bkg_%s.db" (System.Guid.NewGuid().ToString("N")))

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

[<Fact>]
[<Trait("Category", "Integration")>]
let ``営業ロールは貨物予約一覧を表示できる`` () =
    withServer (fun client ->
        let cookie = authCookie client "sales01"
        let res = authedGet client cookie "/bookings"
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "貨物予約一覧")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``荷役ロールは貨物予約一覧にアクセスできない（403）`` () =
    withServer (fun client ->
        let cookie = authCookie client "handler01"
        let res = authedGet client cookie "/bookings"
        res.StatusCode |> should equal HttpStatusCode.Forbidden)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``ナビゲーション整合性: 営業ロールのダッシュボードに貨物予約導線がある`` () =
    withServer (fun client ->
        let cookie = authCookie client "sales01"
        // navbar・ダッシュボードカードともに navMenu 由来で /bookings への導線を含む。
        let dashboard = authedGet client cookie "/"
        dashboard.StatusCode |> should equal HttpStatusCode.OK
        let body = run (dashboard.Content.ReadAsStringAsync())
        body |> should haveSubstring "貨物予約"
        body |> should haveSubstring "/bookings")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``ナビゲーション整合性: 荷役ロールのダッシュボードに貨物予約導線がない`` () =
    withServer (fun client ->
        let cookie = authCookie client "handler01"
        let dashboard = authedGet client cookie "/"
        let body = run (dashboard.Content.ReadAsStringAsync())
        body |> should not' (haveSubstring "/bookings"))
