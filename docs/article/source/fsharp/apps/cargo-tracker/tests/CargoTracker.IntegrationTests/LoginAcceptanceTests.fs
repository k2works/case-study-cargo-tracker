module CargoTracker.IntegrationTests.LoginAcceptanceTests

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

// タスク 2.3/2.6: ログインフローの受け入れテスト（TestServer）。
// 「ログイン → ダッシュボード」の一気通貫と未認証リダイレクトを検証する（ADR-0005）。

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

/// 一時 SQLite にマイグレーションを適用し、営業ユーザーを 1 名投入する。
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
            VALUES ('admin', 'admin@example.com', '%s', 1, '2026-07-14');
            INSERT INTO user_roles (user_id, role) VALUES (2, 'ROLE_ADMIN');
            """
            hash
            hash

    cmd.ExecuteNonQuery() |> ignore

/// TestServer を構築する。DB は指定した SQLite 接続文字列を使う。
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

/// テスト用のスコープ: 一時 DB + TestServer + リダイレクト非追従クライアント。
let private withServer (test: HttpClient -> unit) =
    let dbFile =
        Path.Combine(Path.GetTempPath(), sprintf "cargo_web_%s.db" (System.Guid.NewGuid().ToString("N")))

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

let private loginForm (username: string) (password: string) =
    new FormUrlEncodedContent(dict [ "username", username; "password", password ])

[<Fact>]
[<Trait("Category", "Integration")>]
let ``ログイン画面は認証不要で表示される`` () =
    withServer (fun client ->
        let res = run (client.GetAsync "/login")
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "ログイン")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``未認証でダッシュボードにアクセスするとログインへリダイレクトする`` () =
    withServer (fun client ->
        let res = run (client.GetAsync "/")
        res.StatusCode |> should equal HttpStatusCode.Found
        res.Headers.Location.OriginalString |> should haveSubstring "/login")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``誤った資格情報では 401 とエラーメッセージを返す`` () =
    withServer (fun client ->
        let res = run (client.PostAsync("/login", loginForm "sales01" "wrong"))
        res.StatusCode |> should equal HttpStatusCode.Unauthorized
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "正しくありません")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``ログイン成功でホームへリダイレクトし認証 Cookie を発行する`` () =
    withServer (fun client ->
        let res = run (client.PostAsync("/login", loginForm "sales01" "pw"))
        res.StatusCode |> should equal HttpStatusCode.Found
        res.Headers.Location.OriginalString |> should equal "/"
        res.Headers.Contains "Set-Cookie" |> should equal true)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``ログイン後のダッシュボードは営業ロールの導線を表示する`` () =
    withServer (fun client ->
        let loginRes = run (client.PostAsync("/login", loginForm "sales01" "pw"))
        let cookies = loginRes.Headers.GetValues "Set-Cookie" |> Seq.head
        let cookieValue = cookies.Split(';').[0]

        use req = new HttpRequestMessage(HttpMethod.Get, "/")
        req.Headers.Add("Cookie", cookieValue)
        let res = run (client.SendAsync req)
        res.StatusCode |> should equal HttpStatusCode.OK
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "ダッシュボード"
        body |> should haveSubstring "荷主管理"
        // ナビのログアウトは POST フォームであること（GET リンクだと 404 になるため）
        body |> should haveSubstring "action=\"/logout\""
        body |> should haveSubstring "method=\"post\"")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``ログアウト（POST /logout）でログインへリダイレクトする`` () =
    withServer (fun client ->
        let loginRes = run (client.PostAsync("/login", loginForm "sales01" "pw"))

        let cookieValue =
            (loginRes.Headers.GetValues "Set-Cookie" |> Seq.head).Split(';').[0]

        use req = new HttpRequestMessage(HttpMethod.Post, "/logout")
        req.Headers.Add("Cookie", cookieValue)
        let res = run (client.SendAsync req)
        res.StatusCode |> should equal HttpStatusCode.Found
        res.Headers.Location.OriginalString |> should haveSubstring "/login")

[<Fact>]
[<Trait("Category", "Integration")>]
let ``GET /logout は 404（ログアウトは POST のみ）`` () =
    withServer (fun client ->
        let res = run (client.GetAsync "/logout")
        res.StatusCode |> should equal HttpStatusCode.NotFound)

[<Fact>]
[<Trait("Category", "Integration")>]
let ``sales 以外のシードユーザー（admin）でもログインできる`` () =
    withServer (fun client ->
        let loginRes = run (client.PostAsync("/login", loginForm "admin" "pw"))
        loginRes.StatusCode |> should equal HttpStatusCode.Found

        let cookieValue =
            (loginRes.Headers.GetValues "Set-Cookie" |> Seq.head).Split(';').[0]

        use req = new HttpRequestMessage(HttpMethod.Get, "/")
        req.Headers.Add("Cookie", cookieValue)
        let res = run (client.SendAsync req)
        res.StatusCode |> should equal HttpStatusCode.OK
        // 管理者ロールの導線が表示される
        let body = run (res.Content.ReadAsStringAsync())
        body |> should haveSubstring "管理設定")
