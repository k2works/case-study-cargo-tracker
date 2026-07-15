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

let private authedPost (client: HttpClient) (cookie: string) (path: string) (fields: (string * string) list) =
    use req = new HttpRequestMessage(HttpMethod.Post, path)
    req.Headers.Add("Cookie", cookie)
    req.Content <- new FormUrlEncodedContent(dict fields)
    run (client.SendAsync req)

/// 荷主を 1 件登録し、その shipper_uuid（Guid）を返す。
let private seedShipper (connStr: string) : string =
    let guid = System.Guid.NewGuid()
    use conn = new SqliteConnection(connStr)
    conn.Open()
    use cmd = conn.CreateCommand()

    cmd.CommandText <-
        sprintf
            """
            INSERT INTO shipper
                (shipper_code, shipper_uuid, shipper_type, name, email, discount_rate, created_at, updated_at, version)
            VALUES ('SHP-TEST0001', '%s', 'INDIVIDUAL', 'テスト荷主', 'ship@example.com', 0, '2026-07-28', '2026-07-28', 0);
            """
            (guid.ToString("D"))

    cmd.ExecuteNonQuery() |> ignore
    guid.ToString("D")

/// サーバーを起動し、荷主を 1 件シードしたうえで (client, shipperUuid) をテストへ渡す。
let private withSeededShipper (test: HttpClient -> string -> unit) =
    let dbFile =
        Path.Combine(Path.GetTempPath(), sprintf "cargo_bkgs_%s.db" (System.Guid.NewGuid().ToString("N")))

    let connStr = sprintf "Data Source=%s" dbFile
    seedDatabase connStr
    let shipperUuid = seedShipper connStr
    use server = buildServer connStr
    let client = server.CreateClient()

    try
        test client shipperUuid
    finally
        server.Dispose()
        SqliteConnection.ClearAllPools()

        if File.Exists dbFile then
            File.Delete dbFile

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

[<Fact>]
[<Trait("Category", "Integration")>]
let ``営業ロールは貨物予約登録フォームを表示できる`` () =
    withSeededShipper (fun client _uuid ->
        let cookie = authCookie client "sales01"
        let res = authedGet client cookie "/bookings/new"
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "新規予約登録"
        body |> should haveSubstring "テスト荷主")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``一般貨物の予約を登録すると一覧へリダイレクトし永続化される`` () =
    withSeededShipper (fun client uuid ->
        let cookie = authCookie client "sales01"

        let res =
            authedPost
                client
                cookie
                "/bookings"
                [ "shipperId", uuid
                  "originUnlocode", "JPTYO"
                  "destinationUnlocode", "USLAX"
                  "arrivalDeadline", "2026-09-01"
                  "cargoType", "GENERAL"
                  "weightKg", "500" ]

        // PRG（Post/Redirect/Get）で 302 リダイレクト
        res.StatusCode |> should equal HttpStatusCode.Found

        // 一覧に登録された予約が表示される
        let list = authedGet client cookie "/bookings"
        let body = run (list.Content.ReadAsStringAsync())
        body |> should haveSubstring "BKG-")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``危険物の必須情報が欠けると 400 になる`` () =
    withSeededShipper (fun client uuid ->
        let cookie = authCookie client "sales01"

        let res =
            authedPost
                client
                cookie
                "/bookings"
                [ "shipperId", uuid
                  "originUnlocode", "JPTYO"
                  "destinationUnlocode", "USLAX"
                  "arrivalDeadline", "2026-09-01"
                  "cargoType", "HAZARDOUS"
                  "weightKg", "500"
                  "hazardClass", ""
                  "unNumber", ""
                  "properShippingName", "" ]

        res.StatusCode |> should equal HttpStatusCode.BadRequest)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``存在しない荷主を指定すると 400 になる`` () =
    withSeededShipper (fun client _uuid ->
        let cookie = authCookie client "sales01"

        let res =
            authedPost
                client
                cookie
                "/bookings"
                [ "shipperId", System.Guid.NewGuid().ToString("D")
                  "originUnlocode", "JPTYO"
                  "destinationUnlocode", "USLAX"
                  "arrivalDeadline", "2026-09-01"
                  "cargoType", "GENERAL"
                  "weightKg", "500" ]

        res.StatusCode |> should equal HttpStatusCode.BadRequest)

/// 予約を 1 件登録し、その booking_id を一覧ページから抽出する。
let private bookOne (client: HttpClient) (cookie: string) (uuid: string) : string =
    authedPost
        client
        cookie
        "/bookings"
        [ "shipperId", uuid
          "originUnlocode", "JPTYO"
          "destinationUnlocode", "USLAX"
          "arrivalDeadline", "2026-09-01"
          "cargoType", "GENERAL"
          "weightKg", "500" ]
    |> ignore

    let body = run ((authedGet client cookie "/bookings").Content.ReadAsStringAsync())
    let m = System.Text.RegularExpressions.Regex.Match(body, "BKG-[0-9A-F]+")

    if m.Success then
        m.Value
    else
        failwith "booking_id が一覧に見つからない"

[<Fact>]
[<Trait("Category", "Integration")>]
let ``予約詳細を表示できる`` () =
    withSeededShipper (fun client uuid ->
        let cookie = authCookie client "sales01"
        let bookingId = bookOne client cookie uuid
        let res = authedGet client cookie (sprintf "/bookings/%s" bookingId)
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring bookingId
        body |> should haveSubstring "経路設計を依頼")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``経路設計を依頼すると状態が経路設計中になる`` () =
    withSeededShipper (fun client uuid ->
        let cookie = authCookie client "sales01"
        let bookingId = bookOne client cookie uuid

        let res = authedPost client cookie (sprintf "/bookings/%s/routing" bookingId) []
        res.StatusCode |> should equal HttpStatusCode.Found

        let detail = authedGet client cookie (sprintf "/bookings/%s" bookingId)
        let body = run (detail.Content.ReadAsStringAsync())
        body |> should haveSubstring "経路設計中"
        // 経路設計中は再依頼ボタンを出さない
        body |> should not' (haveSubstring "経路設計を依頼"))
