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

/// 経路確定まで進めて予約詳細で確定可能な状態にする。
let private bookRequestAndPropose (client: HttpClient) (shipperUuid: string) : string =
    let bookingId = bookAndRequestRouting client shipperUuid
    registerDirectVoyage client
    let cookie = authCookie client "designer01"

    authedPost client cookie (sprintf "/routing/requests/%s/propose" bookingId) [ "candidateIndex", "0" ]
    |> ignore

    bookingId

[<Fact>]
[<Trait("Category", "Integration")>]
let ``営業が予約を確定すると予約確定状態になる`` () =
    withServer (fun client shipperUuid ->
        let bookingId = bookRequestAndPropose client shipperUuid
        let salesCookie = authCookie client "sales01"

        let confirmRes =
            authedPost client salesCookie (sprintf "/bookings/%s/confirm" bookingId) []

        confirmRes.StatusCode |> should equal HttpStatusCode.Found

        let detail = authedGet client salesCookie (sprintf "/bookings/%s" bookingId)
        let body = run (detail.Content.ReadAsStringAsync())
        body |> should haveSubstring "予約確定")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``確定した予約を経路設計へ差し戻せる`` () =
    withServer (fun client shipperUuid ->
        let bookingId = bookRequestAndPropose client shipperUuid
        let salesCookie = authCookie client "sales01"

        authedPost client salesCookie (sprintf "/bookings/%s/confirm" bookingId) []
        |> ignore

        let restoreRes =
            authedPost client salesCookie (sprintf "/bookings/%s/restore" bookingId) []

        restoreRes.StatusCode |> should equal HttpStatusCode.Found

        let detail = authedGet client salesCookie (sprintf "/bookings/%s" bookingId)
        let body = run (detail.Content.ReadAsStringAsync())
        body |> should haveSubstring "経路設計中")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``経路確定した予約をキャンセルできる`` () =
    withServer (fun client shipperUuid ->
        let bookingId = bookRequestAndPropose client shipperUuid
        let salesCookie = authCookie client "sales01"

        let cancelRes =
            authedPost client salesCookie (sprintf "/bookings/%s/cancel" bookingId) []

        cancelRes.StatusCode |> should equal HttpStatusCode.Found

        let detail = authedGet client salesCookie (sprintf "/bookings/%s" bookingId)
        let body = run (detail.Content.ReadAsStringAsync())
        body |> should haveSubstring "キャンセル")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``経路確定後に荷主へ通知できる（US12）`` () =
    withServer (fun client shipperUuid ->
        let bookingId = bookRequestAndPropose client shipperUuid
        let salesCookie = authCookie client "sales01"

        let notifyRes =
            authedPost client salesCookie (sprintf "/bookings/%s/notify" bookingId) []

        // PRG: 予約詳細へリダイレクトする（通知成功）。
        notifyRes.StatusCode |> should equal HttpStatusCode.Found

        (string notifyRes.Headers.Location)
        |> should haveSubstring (sprintf "/bookings/%s" bookingId))

[<Fact>]
[<Trait("Category", "Integration")>]
let ``到着期限を短縮すると候補が絞られ緩和で再び算出される（US10）`` () =
    withServer (fun client shipperUuid ->
        let bookingId = bookAndRequestRouting client shipperUuid
        registerDirectVoyage client // V001 は 2026-09-20 到着
        let cookie = authCookie client "designer01"

        // 期限を到着日より前（2026-09-10）に調整すると候補が無くなる。
        let tight =
            authedGet client cookie (sprintf "/routing/requests/%s?deadline=2026-09-10" bookingId)

        let tightBody = run (tight.Content.ReadAsStringAsync())
        tightBody |> should haveSubstring "経路候補がありません"

        // 期限を緩和（2026-12-31）すると再び候補が算出される。
        let relaxed =
            authedGet client cookie (sprintf "/routing/requests/%s?deadline=2026-12-31" bookingId)

        let relaxedBody = run (relaxed.Content.ReadAsStringAsync())
        relaxedBody |> should haveSubstring "V001")

/// 指定した到着期限で予約し経路設計依頼まで進める。
let private bookWithDeadline (client: HttpClient) (shipperUuid: string) (deadline: string) : string =
    let salesCookie = authCookie client "sales01"

    let bookRes =
        authedPost
            client
            salesCookie
            "/bookings"
            [ "shipperId", shipperUuid
              "originUnlocode", "JPTYO"
              "destinationUnlocode", "USLAX"
              "arrivalDeadline", deadline
              "cargoType", "GENERAL"
              "weightKg", "500" ]

    let bookingId = (string bookRes.Headers.Location).Replace("/bookings/", "")

    authedPost client salesCookie (sprintf "/bookings/%s/routing" bookingId) []
    |> ignore

    bookingId

[<Fact>]
[<Trait("Category", "Integration")>]
let ``緩和期限で見えた候補を確定してもドメインは元の期限で棄却する（US10 条件協議は営業経由）`` () =
    withServer (fun client shipperUuid ->
        // 元の期限 2026-09-15 < 航海 V001 到着 2026-09-20。
        let bookingId = bookWithDeadline client shipperUuid "2026-09-15"
        registerDirectVoyage client
        let cookie = authCookie client "designer01"

        // 期限を緩和（2026-12-31）すると候補が見える。
        let relaxed =
            authedGet client cookie (sprintf "/routing/requests/%s?deadline=2026-12-31" bookingId)

        (run (relaxed.Content.ReadAsStringAsync())) |> should haveSubstring "V001"

        // その候補を緩和期限のまま確定しようとすると、集約は元の期限で検証するため棄却される。
        let proposeRes =
            authedPost
                client
                cookie
                (sprintf "/routing/requests/%s/propose" bookingId)
                [ "candidateIndex", "0"; "deadline", "2026-12-31" ]

        proposeRes.StatusCode |> should equal HttpStatusCode.BadRequest)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``操作後に予約詳細で成功メッセージが表示される（レビュー H2）`` () =
    withServer (fun client shipperUuid ->
        let bookingId = bookRequestAndPropose client shipperUuid
        let salesCookie = authCookie client "sales01"

        // 荷主通知 → PRG の msg=notified を付けた詳細で成功メッセージが出る。
        authedPost client salesCookie (sprintf "/bookings/%s/notify" bookingId) []
        |> ignore

        let detail =
            authedGet client salesCookie (sprintf "/bookings/%s?msg=notified" bookingId)

        let body = run (detail.Content.ReadAsStringAsync())
        body |> should haveSubstring "荷主に確定経路を通知しました"
        body |> should haveSubstring "alert-success")
