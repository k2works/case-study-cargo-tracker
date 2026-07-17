module CargoTracker.IntegrationTests.DiscountPolicyAcceptanceTests

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

// US-ADM-01: 割引ポリシー管理（ROLE_ADMIN・登録/一覧/無効化・権限）。

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
            VALUES ('admin01', 'a@example.com', '%s', 1, '2026-10-06');
            INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_ADMIN');
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('sales01', 's@example.com', '%s', 1, '2026-10-06');
            INSERT INTO user_roles (user_id, role) VALUES (2, 'ROLE_SALES');
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
        Path.Combine(Path.GetTempPath(), sprintf "cargo_dp_%s.db" (System.Guid.NewGuid().ToString("N")))

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

let private post (client: HttpClient) (cookie: string) (path: string) (fields: (string * string) list) =
    use req = new HttpRequestMessage(HttpMethod.Post, path)
    req.Headers.Add("Cookie", cookie)
    req.Content <- new FormUrlEncodedContent(dict fields)
    run (client.SendAsync req)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``運用管理者は割引ポリシーを登録し一覧で確認できる（US-ADM-01）`` () =
    withServer (fun client ->
        let admin = authCookie client "admin01"

        let res =
            post
                client
                admin
                "/admin/discount-policies"
                [ "policyType", "CORPORATE_STANDARD"
                  "discountRate", "10.0"
                  "condition", "法人標準契約"
                  "effectiveFrom", "2026-10-01"
                  "effectiveTo", "2026-12-31" ]

        res.StatusCode |> should equal HttpStatusCode.Found

        let list = authedGet client admin "/admin/discount-policies"
        let body = run (list.Content.ReadAsStringAsync())
        body |> should haveSubstring "法人標準"
        body |> should haveSubstring "10.0%"
        body |> should haveSubstring "有効")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``割引率が範囲外（31%）だと登録できずエラー表示される（US-ADM-01 受入6）`` () =
    withServer (fun client ->
        let admin = authCookie client "admin01"

        let res =
            post
                client
                admin
                "/admin/discount-policies"
                [ "policyType", "CORPORATE_STANDARD"
                  "discountRate", "31.0"
                  "condition", "不正"
                  "effectiveFrom", "2026-10-01"
                  "effectiveTo", "" ]

        res.StatusCode |> should equal HttpStatusCode.BadRequest
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "割引率は 0〜30%")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``営業担当者は割引ポリシー管理にアクセスできない（ROLE_ADMIN 必須）`` () =
    withServer (fun client ->
        let sales = authCookie client "sales01"
        let res = authedGet client sales "/admin/discount-policies"
        res.StatusCode |> should equal HttpStatusCode.Forbidden)
