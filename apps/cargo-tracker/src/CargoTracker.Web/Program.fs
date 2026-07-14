module CargoTracker.Web.Program

open System.IO
open Microsoft.AspNetCore.Builder
open Microsoft.Extensions.Configuration
open Microsoft.Extensions.DependencyInjection
open Giraffe
open CargoTracker.Web

/// ルーティング定義。ヘルスチェック + ウォーキングスケルトンの主要ルート。
/// 認証・ロール解決は後続タスク（ADR-0005）で追加する。現状はロール空で描画する。
let webApp: HttpHandler =
    choose
        [ route "/health" >=> text "Healthy"
          route "/" >=> htmlView (Views.dashboard [])
          route "/login" >=> htmlView (Views.login "" None) ]

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
    builder.Services.AddGiraffe() |> ignore
    let app = builder.Build()

    // 起動時に forward-only マイグレーションを適用する（ADR-0003）。
    let provider, connStr, scriptsRoot =
        resolveDbConfig app.Configuration app.Environment.ContentRootPath

    match Db.runMigrations provider connStr scriptsRoot with
    | Ok() -> ()
    | Error e -> failwithf "DB マイグレーションに失敗しました: %s" e

    app.UseGiraffe webApp
    app.Run("http://0.0.0.0:8080")
    0
