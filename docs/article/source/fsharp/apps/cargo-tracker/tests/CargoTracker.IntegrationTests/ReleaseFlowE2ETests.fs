module CargoTracker.IntegrationTests.ReleaseFlowE2ETests

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

// Release 1.0 MVP の一気通貫 E2E（US13 予約確定 → US14 追跡番号発行 → US15 荷役 → US18 照会）。

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

/// 営業・経路設計者・荷役作業員（TRACKER 兼務）と荷主 1 件をシードする。
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
            VALUES ('sales01', 's@example.com', '%s', 1, '2026-09-08');
            INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_SALES');
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('designer01', 'd@example.com', '%s', 1, '2026-09-08');
            INSERT INTO user_roles (user_id, role) VALUES (2, 'ROLE_ROUTE_DESIGNER');
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('handler01', 'h@example.com', '%s', 1, '2026-09-08');
            INSERT INTO user_roles (user_id, role) VALUES (3, 'ROLE_HANDLER');
            INSERT INTO user_roles (user_id, role) VALUES (3, 'ROLE_TRACKER');
            INSERT INTO user_roles (user_id, role) VALUES (3, 'ROLE_BILLING');
            INSERT INTO shipper
                (shipper_code, shipper_uuid, shipper_type, name, email, discount_rate, created_at, updated_at, version)
            VALUES ('SHP-E2E00001', '%s', 'INDIVIDUAL', 'E2E 荷主', 'ship@example.com', 0, '2026-09-08', '2026-09-08', 0);
            """
            hash
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

let private authCookie (client: HttpClient) (username: string) : string =
    let form =
        new FormUrlEncodedContent(dict [ "username", username; "password", "pw" ])

    let res = run (client.PostAsync("/login", form))
    (res.Headers.GetValues "Set-Cookie" |> Seq.head).Split(';').[0]

let private post (client: HttpClient) (cookie: string) (path: string) (fields: (string * string) list) =
    use req = new HttpRequestMessage(HttpMethod.Post, path)
    req.Headers.Add("Cookie", cookie)
    req.Content <- new FormUrlEncodedContent(dict fields)
    run (client.SendAsync req)

let private get (client: HttpClient) (cookie: string) (path: string) =
    use req = new HttpRequestMessage(HttpMethod.Get, path)
    req.Headers.Add("Cookie", cookie)
    run (client.SendAsync req)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``予約確定から追跡・例外・精算まで一気通貫（US13→US14→US15→US18→US19→US21→US23）`` () =
    let dbFile =
        Path.Combine(Path.GetTempPath(), sprintf "cargo_e2e_%s.db" (System.Guid.NewGuid().ToString("N")))

    let connStr = sprintf "Data Source=%s" dbFile
    let shipperUuid = seedDatabase connStr
    use server = buildServer connStr
    let client = server.CreateClient()

    try
        let sales = authCookie client "sales01"
        let designer = authCookie client "designer01"
        let handler = authCookie client "handler01"

        // 経路設計者: 航海 V001（JPTYO→USLAX 直行）を登録
        post
            client
            designer
            "/voyages"
            [ "voyageNumber", "V001"
              "vesselName", "Ever Given"
              "carrierName", "Evergreen"
              "cargoGeneral", "true"
              "leg1Dep", "JPTYO"
              "leg1Arr", "USLAX"
              "leg1DepDate", "2026-09-15T00:00"
              "leg1ArrDate", "2026-09-25T00:00" ]
        |> ignore

        // 営業: 予約登録 → 経路設計依頼
        let bookRes =
            post
                client
                sales
                "/bookings"
                [ "shipperId", shipperUuid
                  "originUnlocode", "JPTYO"
                  "destinationUnlocode", "USLAX"
                  "arrivalDeadline", "2026-12-01"
                  "cargoType", "GENERAL"
                  "weightKg", "500" ]

        let bookingId = (string bookRes.Headers.Location).Replace("/bookings/", "")
        post client sales (sprintf "/bookings/%s/routing" bookingId) [] |> ignore

        // 経路設計者: 経路候補を確定（index 0）
        post client designer (sprintf "/routing/requests/%s/propose" bookingId) [ "candidateIndex", "0" ]
        |> ignore

        // 営業: 予約確定 → 追跡番号が自動発行される（US13→US14）
        let confirmRes = post client sales (sprintf "/bookings/%s/confirm" bookingId) []
        confirmRes.StatusCode |> should equal HttpStatusCode.Found

        // 自動発行された追跡番号を DB から取得
        let trackingNumber =
            use conn = new SqliteConnection(connStr)
            conn.Open()
            use cmd = conn.CreateCommand()
            cmd.CommandText <- sprintf "SELECT tracking_number FROM tracking_activity WHERE booking_id = '%s'" bookingId

            match cmd.ExecuteScalar() with
            | null -> failwith "追跡番号が自動発行されていない（US14 BC 連携）"
            | v -> string v

        trackingNumber |> should startWith "TRK-"

        // 荷役作業員: 受領を記録（US15）→ 追跡状態が受領済へ
        let handleRes =
            post
                client
                handler
                "/handling"
                [ "trackingNumber", trackingNumber
                  "handlingType", "RECEIVE"
                  "location", "JPTYO"
                  "voyageNumber", ""
                  "consigneeConfirmation", "" ]

        handleRes.StatusCode |> should equal HttpStatusCode.Found

        // 荷主/追跡: 追跡照会で受領済を確認（US18）
        let detail = get client handler (sprintf "/tracking/%s" trackingNumber)
        detail.StatusCode |> should equal HttpStatusCode.OK
        let body = run (detail.Content.ReadAsStringAsync())
        body |> should haveSubstring "受領済"
        body |> should haveSubstring "受領"

        // 追跡管理者: 遅延例外を登録（US19）→ 例外発生へ
        let exRes =
            post
                client
                handler
                (sprintf "/tracking/%s/exceptions/new" trackingNumber)
                [ "exceptionType", "DELAY"; "location", "USLAX"; "description", "荒天による寄港遅延" ]

        exRes.StatusCode |> should equal HttpStatusCode.Found

        let inExDetail = get client handler (sprintf "/tracking/%s" trackingNumber)
        let inExBody = run (inExDetail.Content.ReadAsStringAsync())
        inExBody |> should haveSubstring "例外発生"
        inExBody |> should haveSubstring "遅延"

        // 追跡管理者: 例外を解決（US19 対応報告）→ 受領済へ復帰
        let resolveRes =
            post
                client
                handler
                (sprintf "/tracking/%s/exceptions/0/resolve" trackingNumber)
                [ "resolutionNote", "新到着予定日を荷主へ提示" ]

        resolveRes.StatusCode |> should equal HttpStatusCode.Found

        let resolvedDetail = get client handler (sprintf "/tracking/%s" trackingNumber)
        let resolvedBody = run (resolvedDetail.Content.ReadAsStringAsync())
        resolvedBody |> should haveSubstring "解決済み"
        resolvedBody |> should haveSubstring "受領済"

        // 経理担当者（ROLE_BILLING）: 料金算出→精算書発行（個人荷主・割引なし）→入金確認（US21/US23）
        // 距離係数 100 × 重量 500 × 一般 1.0 = 50000、個人なので割引なし → 50000
        let billRes =
            post client handler "/billing/invoices" [ "bookingId", bookingId; "distanceFactor", "100" ]

        billRes.StatusCode |> should equal HttpStatusCode.Found
        let invPath = string billRes.Headers.Location

        let invDetail = get client handler invPath
        let invBody = run (invDetail.Content.ReadAsStringAsync())
        invBody |> should haveSubstring "50,000"
        invBody |> should haveSubstring "支払待ち"

        // 入金確認 → 精算済・予約 Settled 同期
        let invoiceNumber = invPath.Replace("/billing/invoices/", "")

        let confirmBillRes =
            post client handler (sprintf "/billing/invoices/%s/confirm" invoiceNumber) []

        confirmBillRes.StatusCode |> should equal HttpStatusCode.Found

        let invList = get client handler "/billing/invoices"
        let invListBody = run (invList.Content.ReadAsStringAsync())
        invListBody |> should haveSubstring "精算済"
    finally
        server.Dispose()
        SqliteConnection.ClearAllPools()

        if File.Exists dbFile then
            File.Delete dbFile
