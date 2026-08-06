module CargoTracker.IntegrationTests.HandlingAcceptanceTests

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

// US15/US16: 荷役作業登録と、荷役→追跡状態更新の BC 連携。

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

/// 荷役作業員と、追跡活動 + cargo + leg（JPTYO→USLAX 直行 V001）をシードする。
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
            VALUES ('handler01', 'h@example.com', '%s', 1, '2026-09-08');
            INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_HANDLER');
            INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_TRACKER');
            INSERT INTO cargo
                (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode,
                 arrival_deadline, booking_status, created_at, updated_at, version)
            VALUES ('BKG-0001', '00000000-0000-0000-0000-000000000001', 'GENERAL', 500,
                    'JPTYO', 'USLAX', '2026-12-01', 'CONFIRMED', '2026-09-08', '2026-09-08', 0);
            INSERT INTO leg
                (cargo_id, voyage_number, load_location_unlocode, unload_location_unlocode,
                 load_time, unload_time, seq_number, created_at, updated_at)
            VALUES (1, 'V001', 'JPTYO', 'USLAX', '2026-09-10T00:00:00', '2026-09-20T00:00:00', 1, '2026-09-08', '2026-09-08');
            INSERT INTO tracking_activity
                (tracking_number, booking_id, transport_status, access_token, created_at, updated_at, version)
            VALUES ('TRK-TEST0001', 'BKG-0001', 'NOT_RECEIVED', 'TOKEN-AAA', '2026-09-08', '2026-09-08', 0);
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
        Path.Combine(Path.GetTempPath(), sprintf "cargo_hdl_%s.db" (System.Guid.NewGuid().ToString("N")))

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
let ``荷役作業員が受領を登録すると追跡状態が受領済に更新される（US15 BC 連携）`` () =
    withServer (fun client ->
        let cookie = authCookie client "handler01"

        let res =
            authedPost
                client
                cookie
                "/handling"
                [ "trackingNumber", "TRK-TEST0001"
                  "handlingType", "RECEIVE"
                  "location", "JPTYO"
                  "voyageNumber", ""
                  "consigneeConfirmation", "" ]

        res.StatusCode |> should equal HttpStatusCode.Found

        // 追跡照会で受領済に更新されている（荷役→追跡の BC 連携）。
        let detail = authedGet client cookie "/tracking/TRK-TEST0001"
        let body = run (detail.Content.ReadAsStringAsync())
        body |> should haveSubstring "受領済")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``引取は荷受人確認が無いと登録できない（US16）`` () =
    withServer (fun client ->
        let cookie = authCookie client "handler01"

        let res =
            authedPost
                client
                cookie
                "/handling"
                [ "trackingNumber", "TRK-TEST0001"
                  "handlingType", "CLAIM"
                  "location", "USLAX"
                  "voyageNumber", ""
                  "consigneeConfirmation", "" ]

        res.StatusCode |> should equal HttpStatusCode.BadRequest
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "荷受人確認")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``存在しない追跡番号の荷役登録は 404`` () =
    withServer (fun client ->
        let cookie = authCookie client "handler01"

        let res =
            authedPost
                client
                cookie
                "/handling"
                [ "trackingNumber", "TRK-NOPE"
                  "handlingType", "RECEIVE"
                  "location", "JPTYO"
                  "voyageNumber", ""
                  "consigneeConfirmation", "" ]

        res.StatusCode |> should equal HttpStatusCode.NotFound)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``予定ルート外の積込は Misrouted 警告が一覧に表示される（US15 受入7）`` () =
    withServer (fun client ->
        let cookie = authCookie client "handler01"

        // 旅程に無い航海 V999 での積込 → Misrouted。
        let res =
            authedPost
                client
                cookie
                "/handling"
                [ "trackingNumber", "TRK-TEST0001"
                  "handlingType", "LOAD"
                  "location", "JPTYO"
                  "voyageNumber", "V999"
                  "consigneeConfirmation", "" ]

        res.StatusCode |> should equal HttpStatusCode.Found
        (string res.Headers.Location) |> should haveSubstring "msg=handling_misrouted"

        // 一覧の PRG 後に Misrouted 警告が表示される。
        let list = authedGet client cookie "/handling?msg=handling_misrouted"
        let body = run (list.Content.ReadAsStringAsync())
        body |> should haveSubstring "Misrouted")
