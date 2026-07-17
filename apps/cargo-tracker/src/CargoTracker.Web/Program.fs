module CargoTracker.Web.Program

open System
open System.Data
open System.IO
open Microsoft.AspNetCore.Builder
open Microsoft.Extensions.Configuration
open CargoTracker.Web

let private tryResolveScriptsRoot (contentRoot: string) =
    let rec candidates current =
        seq {
            if not (String.IsNullOrWhiteSpace current) then
                let candidate = Path.GetFullPath(Path.Combine(current, "db", "scripts"))
                yield candidate

                let parent = Directory.GetParent(current)

                if not (isNull parent) then
                    yield! candidates parent.FullName
        }

    candidates contentRoot |> Seq.tryFind Directory.Exists

let private normalizeAspNetCoreUrls (urls: string) =
    let normalizeSegment (segment: string) =
        let s = segment.Trim()

        if s.StartsWith("http://0.0.0.0:", StringComparison.OrdinalIgnoreCase) then
            s.Replace("0.0.0.0", "localhost")
        elif s.StartsWith("https://0.0.0.0:", StringComparison.OrdinalIgnoreCase) then
            s.Replace("0.0.0.0", "localhost")
        elif s.StartsWith("http://+:", StringComparison.OrdinalIgnoreCase) then
            s.Replace("http://+:", "http://localhost:")
        elif s.StartsWith("https://+:", StringComparison.OrdinalIgnoreCase) then
            s.Replace("https://+:", "https://localhost:")
        elif s.StartsWith("http://*:", StringComparison.OrdinalIgnoreCase) then
            s.Replace("http://*:", "http://localhost:")
        elif s.StartsWith("https://*:", StringComparison.OrdinalIgnoreCase) then
            s.Replace("https://*:", "https://localhost:")
        else
            s

    urls.Split(';', StringSplitOptions.RemoveEmptyEntries)
    |> Array.map normalizeSegment
    |> String.concat ";"

/// 設定から DB プロバイダ・接続文字列・スクリプトルートを解決する。
let resolveDbConfig (config: IConfiguration) (contentRoot: string) =
    let provider = config.["Database:Provider"] |> Db.providerOfString

    let connStr =
        match config.["Database:ConnectionString"] with
        | null
        | "" -> "Data Source=cargo_tracker.db"
        | s -> s

    let scriptsRoot =
        match config.["Database:ScriptsRoot"] with
        | null
        | "" ->
            match tryResolveScriptsRoot contentRoot with
            | Some path -> path
            | None -> Path.Combine(contentRoot, "db", "scripts")
        | s -> s

    provider, connStr, scriptsRoot

[<EntryPoint>]
let main args =
    let builder = WebApplication.CreateBuilder(args)
    App.configureServices builder.Configuration builder.Services
    let app = builder.Build()

    // 起動時に forward-only マイグレーションを適用する（ADR-0003）。
    let provider, connStr, scriptsRoot =
        resolveDbConfig app.Configuration app.Environment.ContentRootPath

    // 共有インメモリ SQLite（開発用）は、接続が全て閉じると DB が破棄される。
    // マイグレーション前に keep-alive 接続を開き、プロセス終了まで保持して DB を存続させる。
    // （毎起動でスキーマがまっさらになるため、file DB の SchemaVersions ジャーナル不整合を回避できる）
    let keepAlive: IDbConnection option =
        if Db.isSharedInMemory provider connStr then
            Some(Db.openConnection provider connStr)
        else
            None

    match Db.runMigrations provider connStr scriptsRoot with
    | Ok() -> ()
    | Error e -> failwithf "DB マイグレーションに失敗しました: %s" e

    // シード投入（各テーブルが空のときのみ・冪等）。
    // Seed:SampleData=true（開発既定）のときはロール別の既定ユーザー＋業務サンプルデータを投入する。
    // 本番（Seed:SampleData=false）では、環境変数の管理者認証情報からのみ管理者を初期ブートストラップする。
    use seedConn = Db.openConnection provider connStr
    let nowStr = System.DateTimeOffset.Now.UtcDateTime.ToString("o")

    let sampleData =
        match app.Configuration.["Seed:SampleData"] with
        | null
        | "" -> app.Environment.EnvironmentName = "Development"
        | v -> v.Equals("true", System.StringComparison.OrdinalIgnoreCase)

    if sampleData then
        Seed.ensureDefaultUsers seedConn nowStr
        Seed.ensureBusinessData seedConn System.DateTimeOffset.Now
    else
        // 本番: 管理者ブートストラップ（Seed:AdminUsername / Seed:AdminPassword または環境変数）。
        let adminUser = app.Configuration.["Seed:AdminUsername"]
        let adminPass = app.Configuration.["Seed:AdminPassword"]

        if Seed.ensureAdminUser seedConn nowStr adminUser adminPass then
            printfn "[Seed] 管理者ユーザー '%s' を初期投入しました。初回ログイン後にパスワードを変更してください。" adminUser
        else
            eprintfn "[Seed] サンプルデータ無効かつ管理者未投入です。Seed__AdminUsername / Seed__AdminPassword を設定して再起動するか、手動で管理者を作成してください。"

    App.configureApp app

    // ASPNETCORE_URLS（launchSettings / dotnet watch / コンテナ）が設定されていればそれを尊重し、
    // 未設定ならローカル開発向けに localhost:8080 で起動する。
    match System.Environment.GetEnvironmentVariable "ASPNETCORE_URLS" with
    | null
    | "" -> app.Run("http://localhost:8080")
    | urls ->
        let normalized = normalizeAspNetCoreUrls urls

        if normalized <> urls then
            System.Environment.SetEnvironmentVariable("ASPNETCORE_URLS", normalized)

        app.Run()

    // シャットダウンまで keep-alive 接続を保持し、ここで明示的に閉じる（早期 GC 防止）。
    keepAlive |> Option.iter (fun c -> c.Dispose())
    0
