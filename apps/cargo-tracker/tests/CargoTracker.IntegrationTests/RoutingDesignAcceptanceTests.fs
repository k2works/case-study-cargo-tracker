module CargoTracker.IntegrationTests.RoutingDesignAcceptanceTests

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

// US07/US08: 経路設計・候補算出画面の受け入れテスト（経路設計中の予約に対する経路候補算出）。

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

/// 経路設計者・営業ユーザーと、荷主 1 件をシードする。
let private seedDatabase (connStr: string) : string =
    match Db.runMigrations Db.Sqlite connStr (Path.Combine(repoRoot, "db", "scripts")) with
    | Ok() -> ()
    | Error e -> failwithf "マイグレーション失敗: %s" e

    use conn = new SqliteConnection(connStr)
    conn.Open()
    let hash = Auth.Password.hash "pw"
    let shipperUuid = System.Guid.NewGuid().ToString("D")
    use cmd = conn.CreateCommand()

    cmd.CommandText <-
        sprintf
            """
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('designer01', 'd@example.com', '%s', 1, '2026-08-11');
            INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_ROUTE_DESIGNER');
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('sales01', 's@example.com', '%s', 1, '2026-08-11');
            INSERT INTO user_roles (user_id, role) VALUES (2, 'ROLE_SALES');
            INSERT INTO shipper
                (shipper_code, shipper_uuid, shipper_type, name, email, discount_rate, created_at, updated_at, version)
            VALUES ('SHP-TEST0001', '%s', 'INDIVIDUAL', 'テスト荷主', 'ship@example.com', 0, '2026-08-11', '2026-08-11', 0);
            """
            hash
            hash
            shipperUuid

    cmd.ExecuteNonQuery() |> ignore
    shipperUuid

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

let private withServer (test: HttpClient -> string -> unit) =
    let dbFile =
        Path.Combine(Path.GetTempPath(), sprintf "cargo_rd_%s.db" (System.Guid.NewGuid().ToString("N")))

    let connStr = sprintf "Data Source=%s" dbFile
    let shipperUuid = seedDatabase connStr
    use server = buildServer connStr
    let client = server.CreateClient()

    try
        test client shipperUuid
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

/// 営業が予約を登録し、経路設計依頼まで進めて booking_id を返す。
let private bookAndRequestRouting (client: HttpClient) (shipperUuid: string) : string =
    let salesCookie = authCookie client "sales01"

    let bookRes =
        authedPost
            client
            salesCookie
            "/bookings"
            [ "shipperId", shipperUuid
              "originUnlocode", "JPTYO"
              "destinationUnlocode", "USLAX"
              "arrivalDeadline", "2026-12-01"
              "cargoType", "GENERAL"
              "weightKg", "500" ]

    let bookingId = (string bookRes.Headers.Location).Replace("/bookings/", "")
    // 経路設計依頼（Preliminary → RoutingRequested）
    authedPost client salesCookie (sprintf "/bookings/%s/routing" bookingId) []
    |> ignore

    bookingId

/// 経路設計者が航海 V001（JPTYO→USLAX 直行）を登録する。
let private registerDirectVoyage (client: HttpClient) =
    let cookie = authCookie client "designer01"

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
let ``経路設計依頼一覧に経路設計中の予約が表示される`` () =
    withServer (fun client shipperUuid ->
        let bookingId = bookAndRequestRouting client shipperUuid
        let cookie = authCookie client "designer01"
        let res = authedGet client cookie "/routing/requests"
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "経路設計依頼一覧"
        body |> should haveSubstring bookingId)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``経路設計画面で予約に対する経路候補が算出される`` () =
    withServer (fun client shipperUuid ->
        let bookingId = bookAndRequestRouting client shipperUuid
        registerDirectVoyage client
        let cookie = authCookie client "designer01"
        let res = authedGet client cookie (sprintf "/routing/requests/%s" bookingId)
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "経路候補"
        body |> should haveSubstring "V001"
        body |> should haveSubstring "直行")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``航海が無い予約の経路設計は候補なしの案内を表示する`` () =
    withServer (fun client shipperUuid ->
        let bookingId = bookAndRequestRouting client shipperUuid
        // 航海を登録しない
        let cookie = authCookie client "designer01"
        let res = authedGet client cookie (sprintf "/routing/requests/%s" bookingId)
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "経路候補がありません")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``経路候補を確定すると予約が経路確定状態になり詳細に反映される`` () =
    withServer (fun client shipperUuid ->
        let bookingId = bookAndRequestRouting client shipperUuid
        registerDirectVoyage client
        let cookie = authCookie client "designer01"

        // 先頭候補（index 0）を選択して確定する。
        let proposeRes =
            authedPost client cookie (sprintf "/routing/requests/%s/propose" bookingId) [ "candidateIndex", "0" ]

        // PRG: 予約詳細へリダイレクトする。
        proposeRes.StatusCode |> should equal HttpStatusCode.Found

        (string proposeRes.Headers.Location)
        |> should haveSubstring (sprintf "/bookings/%s" bookingId)

        // 予約詳細で経路確定状態が表示される（営業ロールで確認）。
        let salesCookie = authCookie client "sales01"
        let detail = authedGet client salesCookie (sprintf "/bookings/%s" bookingId)
        let body = run (detail.Content.ReadAsStringAsync())
        body |> should haveSubstring "経路確定")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``不正な候補インデックスの確定は 400 を返す`` () =
    withServer (fun client shipperUuid ->
        let bookingId = bookAndRequestRouting client shipperUuid
        registerDirectVoyage client
        let cookie = authCookie client "designer01"

        let res =
            authedPost client cookie (sprintf "/routing/requests/%s/propose" bookingId) [ "candidateIndex", "99" ]

        res.StatusCode |> should equal HttpStatusCode.BadRequest)
