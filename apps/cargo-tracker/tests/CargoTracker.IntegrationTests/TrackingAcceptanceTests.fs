module CargoTracker.IntegrationTests.TrackingAcceptanceTests

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

// US18: 貨物追跡照会（認証あり `/tracking/{tn}` + 未認証 `/public/tracking/{token}`）。

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

/// 荷主ユーザーと、追跡活動 1 件（RECEIVED 済）をシードする。
let private seedDatabase (connStr: string) : unit =
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
            VALUES ('shipper01', 'sh@example.com', '%s', 1, '2026-09-08');
            INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_SHIPPER');
            INSERT INTO tracking_activity
                (tracking_number, booking_id, transport_status, access_token, created_at, updated_at, version)
            VALUES ('TRK-TEST0001', 'BKG-0001', 'RECEIVED', 'PUBLICTOKEN123', '2026-09-08', '2026-09-08', 0);
            INSERT INTO tracking_handling_event
                (tracking_id, event_type, event_time, location_unlocode, seq_number, created_at, updated_at)
            VALUES (1, 'RECEIVED', '2026-09-09T00:00:00', 'JPTYO', 1, '2026-09-08', '2026-09-08');
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
        Path.Combine(Path.GetTempPath(), sprintf "cargo_trk_%s.db" (System.Guid.NewGuid().ToString("N")))

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
let ``荷主は追跡番号で状態とイベント履歴を照会できる（US18）`` () =
    withServer (fun client ->
        let cookie = authCookie client "shipper01"
        let res = authedGet client cookie "/tracking/TRK-TEST0001"
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "受領済"
        body |> should haveSubstring "JPTYO")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``存在しない追跡番号は 404 と案内を返す`` () =
    withServer (fun client ->
        let cookie = authCookie client "shipper01"
        let res = authedGet client cookie "/tracking/TRK-NOPE"
        res.StatusCode |> should equal HttpStatusCode.NotFound
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "追跡番号が見つかりません")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``公開トークンで未認証でも追跡照会できる（US18）`` () =
    withServer (fun client ->
        // 認証なしでアクセスする。
        let res = run (client.GetAsync("/public/tracking/PUBLICTOKEN123"))
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "受領済")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``不正な公開トークンは 404 を返す`` () =
    withServer (fun client ->
        let res = run (client.GetAsync("/public/tracking/WRONGTOKEN"))
        res.StatusCode |> should equal HttpStatusCode.NotFound)
