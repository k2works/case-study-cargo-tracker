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
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('tracker01', 'tr@example.com', '%s', 1, '2026-09-08');
            INSERT INTO user_roles (user_id, role) VALUES (2, 'ROLE_TRACKER');
            INSERT INTO tracking_activity
                (tracking_number, booking_id, transport_status, access_token, created_at, updated_at, version)
            VALUES ('TRK-TEST0001', 'BKG-0001', 'RECEIVED', 'PUBLICTOKEN123', '2026-09-08', '2026-09-08', 0);
            INSERT INTO tracking_handling_event
                (tracking_id, event_type, event_time, location_unlocode, seq_number, created_at, updated_at)
            VALUES (1, 'RECEIVED', '2026-09-09T00:00:00', 'JPTYO', 1, '2026-09-08', '2026-09-08');
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
        body |> should haveSubstring "JPTYO"
        // 現在地・推定到着日が表示される（レビュー高#5）
        body |> should haveSubstring "現在地"
        body |> should haveSubstring "推定到着日")

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

[<Fact>]
[<Trait("Category", "Integration")>]
let ``追跡管理者が貨物状態を手動更新できる（US17）`` () =
    withServer (fun client ->
        let cookie = authCookie client "tracker01"

        // 受領済 → 出港（DEPARTED）で輸送中へ手動更新。
        use req = new HttpRequestMessage(HttpMethod.Post, "/tracking/TRK-TEST0001/status")
        req.Headers.Add("Cookie", cookie)
        req.Content <- new FormUrlEncodedContent(dict [ "eventType", "DEPARTED"; "location", "JPTYO" ])
        let res = run (client.SendAsync req)
        res.StatusCode |> should equal HttpStatusCode.Found

        // 追跡詳細で輸送中に更新されている。
        let detail = authedGet client cookie "/tracking/TRK-TEST0001"
        let body = run (detail.Content.ReadAsStringAsync())
        body |> should haveSubstring "輸送中")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``荷主は手動更新フォームにアクセスできない（ROLE_TRACKER 必須）`` () =
    withServer (fun client ->
        let cookie = authCookie client "shipper01"
        let res = authedGet client cookie "/tracking/TRK-TEST0001/status/new"
        res.StatusCode |> should equal HttpStatusCode.Forbidden)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``追跡管理者が紛失例外を登録すると In/エスカレーション表示され解決で復帰する（US19/US20）`` () =
    withServer (fun client ->
        let cookie = authCookie client "tracker01"

        // 紛失例外を登録
        use req =
            new HttpRequestMessage(HttpMethod.Post, "/tracking/TRK-TEST0001/exceptions/new")

        req.Headers.Add("Cookie", cookie)

        req.Content <-
            new FormUrlEncodedContent(
                dict [ "exceptionType", "LOST"; "location", "USLAX"; "description", "海上事故により紛失" ]
            )

        let res = run (client.SendAsync req)
        res.StatusCode |> should equal HttpStatusCode.Found

        // 追跡詳細で例外発生・エスカレーションが表示される
        let detail = authedGet client cookie "/tracking/TRK-TEST0001"
        let body = run (detail.Content.ReadAsStringAsync())
        body |> should haveSubstring "例外発生"
        body |> should haveSubstring "紛失"
        body |> should haveSubstring "エスカレーション"

        // 例外を解決すると復帰する
        use rreq =
            new HttpRequestMessage(HttpMethod.Post, "/tracking/TRK-TEST0001/exceptions/0/resolve")

        rreq.Headers.Add("Cookie", cookie)
        rreq.Content <- new FormUrlEncodedContent(dict [ "resolutionNote", "代替手配・補償対応済" ])
        let rres = run (client.SendAsync rreq)
        rres.StatusCode |> should equal HttpStatusCode.Found

        let detail2 = authedGet client cookie "/tracking/TRK-TEST0001"
        let body2 = run (detail2.Content.ReadAsStringAsync())
        body2 |> should haveSubstring "解決済み"
        // 復帰後は受領済（RECEIVED）に戻る
        body2 |> should haveSubstring "受領済")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``荷主は例外登録フォームにアクセスできない（ROLE_TRACKER 必須）`` () =
    withServer (fun client ->
        let cookie = authCookie client "shipper01"
        let res = authedGet client cookie "/tracking/TRK-TEST0001/exceptions/new"
        res.StatusCode |> should equal HttpStatusCode.Forbidden)
