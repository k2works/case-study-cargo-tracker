module CargoTracker.IntegrationTests.ShipperAcceptanceTests

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

// タスク 3.3: 荷主管理画面の受け入れテスト（ROLE_SALES 認可・登録フロー・異常系）。

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

/// 営業ユーザーと荷役ユーザーを 1 名ずつ投入する。
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
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('handler01', 'handler01@example.com', '%s', 1, '2026-07-14');
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
        Path.Combine(Path.GetTempPath(), sprintf "cargo_shp_%s.db" (System.Guid.NewGuid().ToString("N")))

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

/// ログインして認証 Cookie 文字列を得る。
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
let ``営業ロールは荷主一覧を表示できる`` () =
    withServer (fun client ->
        let cookie = authCookie client "sales01"
        let res = authedGet client cookie "/shippers"
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "荷主一覧")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``荷役ロールは荷主一覧にアクセスできない（403）`` () =
    withServer (fun client ->
        let cookie = authCookie client "handler01"
        let res = authedGet client cookie "/shippers"
        res.StatusCode |> should equal HttpStatusCode.Forbidden)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``個人荷主を登録すると一覧に表示される`` () =
    withServer (fun client ->
        let cookie = authCookie client "sales01"

        let res =
            authedPost
                client
                cookie
                "/shippers"
                [ "name", "山田商店"; "email", "yamada@example.com"; "phone", ""; "address", "" ]

        res.StatusCode |> should equal HttpStatusCode.Found
        res.Headers.Location.OriginalString |> should equal "/shippers"

        let listBody =
            run ((authedGet client cookie "/shippers").Content.ReadAsStringAsync())

        listBody |> should haveSubstring "山田商店"
        listBody |> should haveSubstring "個人")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``法人荷主を割引率付きで登録できる`` () =
    withServer (fun client ->
        let cookie = authCookie client "sales01"

        let res =
            authedPost
                client
                cookie
                "/shippers"
                [ "name", "サンプル株式会社"
                  "email", "corp@example.com"
                  "isCorporate", "true"
                  "contractNumber", "CT-1"
                  "discountRatePercent", "20" ]

        res.StatusCode |> should equal HttpStatusCode.Found

        let listBody =
            run ((authedGet client cookie "/shippers").Content.ReadAsStringAsync())

        listBody |> should haveSubstring "サンプル株式会社"
        listBody |> should haveSubstring "法人"
        listBody |> should haveSubstring "20.0%")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``荷役ロールは荷役管理プレースホルダにアクセスできる`` () =
    withServer (fun client ->
        let cookie = authCookie client "handler01"
        let res = authedGet client cookie "/handling"
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "準備中")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``営業ロールは航路管理プレースホルダにアクセスできない（403）`` () =
    withServer (fun client ->
        let cookie = authCookie client "sales01"
        let res = authedGet client cookie "/voyages"
        res.StatusCode |> should equal HttpStatusCode.Forbidden)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``割引率が範囲外なら 400 とエラーメッセージを返す`` () =
    withServer (fun client ->
        let cookie = authCookie client "sales01"

        let res =
            authedPost
                client
                cookie
                "/shippers"
                [ "name", "過大割引社"
                  "email", "over@example.com"
                  "isCorporate", "true"
                  "contractNumber", "CT-2"
                  "discountRatePercent", "40" ]

        res.StatusCode |> should equal HttpStatusCode.BadRequest
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "割引率")
