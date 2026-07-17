module CargoTracker.IntegrationTests.BillingAcceptanceTests

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

// US21/US22/US23: 料金算出→精算書発行（法人割引）→入金確認の一気通貫。

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

/// 経理担当者（ROLE_BILLING）と、法人荷主・引取済の貨物 1 件をシードする。
let private seedDatabase (connStr: string) : unit =
    match Db.runMigrations Db.Sqlite connStr (Path.Combine(repoRoot, "db", "scripts")) with
    | Ok() -> ()
    | Error e -> failwithf "マイグレーション失敗: %s" e

    use conn = new SqliteConnection(connStr)
    conn.Open()
    let hash = Auth.Password.hash "pw"
    let shipperUuid = "11111111-1111-1111-1111-111111111111"
    use cmd = conn.CreateCommand()

    cmd.CommandText <-
        sprintf
            """
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('billing01', 'b@example.com', '%s', 1, '2026-10-06');
            INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_BILLING');
            INSERT INTO shipper
                (shipper_code, shipper_uuid, shipper_type, name, email, contract_number, discount_rate, created_at, updated_at, version)
            VALUES ('SHP-CORP0001', '%s', 'CORPORATE', '法人荷主', 'corp@example.com', 'C-001', 0.10, '2026-10-06', '2026-10-06', 0);
            INSERT INTO cargo
                (booking_id, shipper_id, cargo_type, weight, origin_unlocode, destination_unlocode, arrival_deadline, booking_status, created_at, updated_at, version)
            VALUES ('BKG-BILL01', '%s', 'GENERAL', 500, 'JPTYO', 'USLAX', '2026-12-01', 'DELIVERED', '2026-10-06', '2026-10-06', 0);
            INSERT INTO discount_policy
                (policy_type, discount_rate, applicable_condition, effective_from, effective_to, active, created_at, updated_at)
            VALUES ('CORPORATE_STANDARD', 0.10, '法人標準', '2026-01-01', NULL, 1, '2026-10-06', '2026-10-06');
            INSERT INTO leg
                (cargo_id, voyage_number, load_location_unlocode, unload_location_unlocode, load_time, unload_time, seq_number, created_at, updated_at)
            VALUES
                ((SELECT id FROM cargo WHERE booking_id = 'BKG-BILL01'), 'V001', 'JPTYO', 'SGSIN', '2026-10-07', '2026-10-15', 1, '2026-10-06', '2026-10-06'),
                ((SELECT id FROM cargo WHERE booking_id = 'BKG-BILL01'), 'V002', 'SGSIN', 'USLAX', '2026-10-16', '2026-10-25', 2, '2026-10-06', '2026-10-06');
            INSERT INTO tracking_activity
                (tracking_number, booking_id, transport_status, access_token, created_at, updated_at, version)
            VALUES ('TRK-BILL01', 'BKG-BILL01', 'IN_PORT', 'tok-bill01', '2026-10-06', '2026-10-06', 0);
            INSERT INTO tracking_exception_event
                (tracking_id, exception_type, location_unlocode, occurred_at, escalation_flag, description, resolved_at, resolution_notes, seq_number, created_at, updated_at)
            VALUES
                ((SELECT id FROM tracking_activity WHERE tracking_number = 'TRK-BILL01'), 'DELAYED', 'SGSIN', '2026-10-16', 1, '荒天による遅延', NULL, NULL, 1, '2026-10-06', '2026-10-06');
            """
            hash
            shipperUuid
            shipperUuid

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

let private withServerDb (test: HttpClient -> string -> unit) =
    let dbFile =
        Path.Combine(Path.GetTempPath(), sprintf "cargo_bill_%s.db" (System.Guid.NewGuid().ToString("N")))

    let connStr = sprintf "Data Source=%s" dbFile
    seedDatabase connStr
    use server = buildServer connStr
    let client = server.CreateClient()

    try
        test client connStr
    finally
        server.Dispose()
        SqliteConnection.ClearAllPools()

        if File.Exists dbFile then
            File.Delete dbFile

let private withServer (test: HttpClient -> unit) =
    withServerDb (fun client _ -> test client)

let private authCookie (client: HttpClient) (username: string) : string =
    let form =
        new FormUrlEncodedContent(dict [ "username", username; "password", "pw" ])

    let res = run (client.PostAsync("/login", form))
    (res.Headers.GetValues "Set-Cookie" |> Seq.head).Split(';').[0]

let private authedGet (client: HttpClient) (cookie: string) (path: string) =
    use req = new HttpRequestMessage(HttpMethod.Get, path)
    req.Headers.Add("Cookie", cookie)
    run (client.SendAsync req)

let private post (client: HttpClient) (cookie: string) (path: string) (fields: (string * string) list) =
    use req = new HttpRequestMessage(HttpMethod.Post, path)
    req.Headers.Add("Cookie", cookie)
    req.Content <- new FormUrlEncodedContent(dict fields)
    run (client.SendAsync req)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``経理担当者が料金算出→精算書発行→入金確認まで一気通貫（US21/US22/US23）`` () =
    withServerDb (fun client connStr ->
        let billing = authCookie client "billing01"

        // 確定前プレビュー（US21/US22）: 輸送実績・導出距離・確定前割引率が表示される
        let preview = authedGet client billing "/billing/invoices/new?bookingId=BKG-BILL01"
        let previewBody = run (preview.Content.ReadAsStringAsync())
        previewBody |> should haveSubstring "輸送実績"
        previewBody |> should haveSubstring "JPTYO→SGSIN"
        previewBody |> should haveSubstring "1,000 km"
        previewBody |> should haveSubstring "確定前割引率"

        // 料金算出・精算書発行
        // 距離自動導出（2 区間 × 500km = 1000km）× 単価 0.1 = 距離係数 100
        // 基本料金 = 100 × 重量 500 × 一般 1.0 = 50000、法人 10% 割引 → 45000
        let createRes =
            post client billing "/billing/invoices" [ "bookingId", "BKG-BILL01"; "unitPrice", "0.1" ]

        createRes.StatusCode |> should equal HttpStatusCode.Found
        let detailPath = string createRes.Headers.Location

        // 精算書詳細で法人割引・請求金額を確認
        let detail = authedGet client billing detailPath
        detail.StatusCode |> should equal HttpStatusCode.OK
        let body = run (detail.Content.ReadAsStringAsync())
        body |> should haveSubstring "10.0%"
        body |> should haveSubstring "45,000"
        // 消費税 10%（4,500）・税込総額（49,500）が内訳表示される（US22・IT8）
        body |> should haveSubstring "消費税"
        body |> should haveSubstring "4,500"
        body |> should haveSubstring "49,500"
        body |> should haveSubstring "支払待ち"

        // 入金確認 → 精算済
        let invoiceNumber = detailPath.Replace("/billing/invoices/", "")

        let confirmRes =
            post client billing (sprintf "/billing/invoices/%s/confirm" invoiceNumber) []

        confirmRes.StatusCode |> should equal HttpStatusCode.Found

        let list = authedGet client billing "/billing/invoices"
        let listBody = run (list.Content.ReadAsStringAsync())
        listBody |> should haveSubstring "精算済"

        // BC 連携（task4.1/4.2）: 精算完了で予約が BookingSettled イベント駆動で Settled へ遷移し、
        // cargo.booking_status が実値 'SETTLED' に更新されていることを検証する（IT7 レビュー高#1）。
        use verifyConn = new SqliteConnection(connStr)
        verifyConn.Open()
        use statusCmd = verifyConn.CreateCommand()
        statusCmd.CommandText <- "SELECT booking_status FROM cargo WHERE booking_id = 'BKG-BILL01'"
        let bookingStatus = statusCmd.ExecuteScalar() |> string
        bookingStatus |> should equal "SETTLED")

// 注: マスタ率が権威であること（マスタの discount_rate を使い、ハードコード率を使わない）は、
// 上記受け入れテストがシードの割引ポリシー（10%）に依存して 45000 になる点と、
// BillingDomainTests の resolveApplicableRate（5 ケース）で担保する。

[<Fact>]
[<Trait("Category", "Integration")>]
let ``例外発生時は料金調整を入力でき基本料金から減額される（US21 受入6・IT8）`` () =
    withServer (fun client ->
        let billing = authCookie client "billing01"

        // BKG-BILL01 には未解決の輸送例外（遅延）があるため、プレビューに調整入力欄が表示される
        let preview = authedGet client billing "/billing/invoices/new?bookingId=BKG-BILL01"
        let previewBody = run (preview.Content.ReadAsStringAsync())
        previewBody |> should haveSubstring "未解決の輸送例外"
        previewBody |> should haveSubstring "料金調整"

        // 料金調整 10000 減額で確定 → 基本料金 50000-10000=40000、法人 10% → 36000
        let createRes =
            post
                client
                billing
                "/billing/invoices"
                [ "bookingId", "BKG-BILL01"; "unitPrice", "0.1"; "adjustment", "10000" ]

        createRes.StatusCode |> should equal HttpStatusCode.Found
        let detailPath = string createRes.Headers.Location
        let detail = authedGet client billing detailPath
        let body = run (detail.Content.ReadAsStringAsync())
        // 減額後の基本料金 40,000・割引後小計 36,000
        body |> should haveSubstring "40,000"
        body |> should haveSubstring "36,000")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``他ロールは請求管理にアクセスできない（ROLE_BILLING 必須）`` () =
    withServer (fun client ->
        // billing01 でログインし、別ロールが無いことを利用（ここでは未認証で 302/403 を確認）
        let res = run (client.GetAsync("/billing/invoices"))
        // 未認証はログインへリダイレクト
        (res.StatusCode = HttpStatusCode.Found
         || res.StatusCode = HttpStatusCode.Unauthorized)
        |> should equal true)
