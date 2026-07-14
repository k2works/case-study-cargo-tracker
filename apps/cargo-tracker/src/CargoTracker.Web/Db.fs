namespace CargoTracker.Web

open System
open System.Data
open System.IO
open Microsoft.Data.Sqlite
open Npgsql
open DbUp

// DB プロバイダの選択・接続ファクトリ・DbUp マイグレーション実行（ADR-0003）。
// 開発は SQLite、ステージング/本番は PostgreSQL。方言差異はプロバイダ別スクリプトで吸収する。

module Db =

    type DbProvider =
        | Sqlite
        | Postgres

    /// 設定文字列からプロバイダを判定する（既定は SQLite）。
    let providerOfString (value: string) : DbProvider =
        match (value |> Option.ofObj |> Option.defaultValue "").Trim().ToLowerInvariant() with
        | "postgres"
        | "postgresql" -> Postgres
        | _ -> Sqlite

    /// プロバイダに応じた ADO.NET 接続を開く。
    let openConnection (provider: DbProvider) (connectionString: string) : IDbConnection =
        let conn: IDbConnection =
            match provider with
            | Sqlite -> new SqliteConnection(connectionString)
            | Postgres -> new NpgsqlConnection(connectionString)

        conn.Open()
        conn

    /// プロバイダ別のスクリプトディレクトリ（db/scripts/{sqlite|postgresql}）。
    let scriptsDir (scriptsRoot: string) (provider: DbProvider) : string =
        let dialect =
            match provider with
            | Sqlite -> "sqlite"
            | Postgres -> "postgresql"

        Path.Combine(scriptsRoot, dialect)

    /// DbUp で forward-only マイグレーションを適用する（journal テーブルで適用管理）。
    let runMigrations (provider: DbProvider) (connectionString: string) (scriptsRoot: string) : Result<unit, string> =
        let dir = scriptsDir scriptsRoot provider

        let builder =
            match provider with
            | Sqlite -> DeployChanges.To.SqliteDatabase(connectionString)
            | Postgres -> DeployChanges.To.PostgresqlDatabase(connectionString)

        let result =
            builder.WithScriptsFromFileSystem(dir).LogToNowhere().Build().PerformUpgrade()

        if result.Successful then
            Ok()
        else
            Error(
                match result.Error with
                | null -> "マイグレーションに失敗しました。"
                | ex -> ex.Message
            )
