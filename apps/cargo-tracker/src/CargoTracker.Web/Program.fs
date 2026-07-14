module CargoTracker.Web.Program

open System.IO
open Microsoft.AspNetCore.Builder
open Microsoft.Extensions.Configuration
open CargoTracker.Web

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
        | "" -> Path.Combine(contentRoot, "..", "..", "db", "scripts")
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

    match Db.runMigrations provider connStr scriptsRoot with
    | Ok() -> ()
    | Error e -> failwithf "DB マイグレーションに失敗しました: %s" e

    // 開発用の既定ユーザーを投入する（users が空のときのみ・冪等）。
    use seedConn = Db.openConnection provider connStr
    Seed.ensureDefaultUsers seedConn (System.DateTimeOffset.Now.UtcDateTime.ToString("o"))

    App.configureApp app

    // ASPNETCORE_URLS（launchSettings / dotnet watch / コンテナ）が設定されていればそれを尊重し、
    // 未設定なら本番既定の 8080 で起動する。dotnet watch のブラウザ自動更新と両立させる。
    match System.Environment.GetEnvironmentVariable "ASPNETCORE_URLS" with
    | null
    | "" -> app.Run("http://0.0.0.0:8080")
    | _ -> app.Run()

    0
