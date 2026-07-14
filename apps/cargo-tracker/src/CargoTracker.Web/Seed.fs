namespace CargoTracker.Web

open System.Data
open Donald

// 開発用シードデータ。users テーブルが空のときのみ、ロール別の既定ユーザーを投入する（冪等）。
// 本番では別途ユーザー管理を行う前提のため、空でない場合は何もしない。

module Seed =

    /// ログイン画面に事前入力する既定ユーザー。
    [<Literal>]
    let DefaultUsername = "sales"

    /// 既定ユーザーの共通パスワード（開発用）。
    [<Literal>]
    let DefaultPassword = "password"

    /// (ユーザー名, ロール) の既定シード。1 ユーザー 1 ロール。
    let private defaultUsers =
        [ "sales", "ROLE_SALES"
          "designer", "ROLE_ROUTE_DESIGNER"
          "tracker", "ROLE_TRACKER"
          "handler", "ROLE_HANDLER"
          "billing", "ROLE_BILLING"
          "admin", "ROLE_ADMIN" ]

    /// ログイン画面のユーザー選択に使う既定ユーザー名一覧。
    let defaultUsernames = defaultUsers |> List.map fst

    /// users が空なら既定ユーザーを投入する。now は監査タイムスタンプ（ISO 8601）。
    let ensureDefaultUsers (conn: IDbConnection) (now: string) : unit =
        let count =
            conn
            |> Db.newCommand "SELECT COUNT(*) AS c FROM users"
            |> Db.querySingle (fun rd -> rd.ReadInt32 "c")
            |> Option.defaultValue 0

        if count = 0 then
            let hash = Auth.Password.hash DefaultPassword

            for username, role in defaultUsers do
                conn
                |> Db.newCommand
                    """
                    INSERT INTO users (username, email, password, enabled, created_at)
                    VALUES (@u, @e, @p, @enabled, @now)
                    """
                |> Db.setParams
                    [ "u", SqlType.String username
                      "e", SqlType.String(sprintf "%s@example.com" username)
                      "p", SqlType.String hash
                      "enabled", SqlType.Boolean true
                      "now", SqlType.String now ]
                |> Db.exec

                let userId =
                    conn
                    |> Db.newCommand "SELECT id AS id FROM users WHERE username = @u"
                    |> Db.setParams [ "u", SqlType.String username ]
                    |> Db.querySingle (fun rd -> rd.ReadInt64 "id")
                    |> Option.defaultValue 0L

                conn
                |> Db.newCommand "INSERT INTO user_roles (user_id, role) VALUES (@id, @role)"
                |> Db.setParams [ "id", SqlType.Int64 userId; "role", SqlType.String role ]
                |> Db.exec
