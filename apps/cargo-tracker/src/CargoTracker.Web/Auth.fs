namespace CargoTracker.Web

open System
open System.Data
open System.Security.Cryptography
open Donald

// 認証・RBAC の中核（ADR-0005）。ASP.NET Core Identity を使わず、
// PBKDF2 パスワードハッシュ + Donald の軽量ユーザーストアで自前管理する。

module Auth =

    /// 認証済みユーザー（ユーザー名とロール）。Cookie のクレームに載せる。
    type AuthenticatedUser =
        { Username: string; Roles: string list }

    /// 永続化されたユーザーレコード。
    type UserRecord =
        { Username: string
          PasswordHash: string
          Enabled: bool
          Roles: string list }

    /// ユーザーストアの出力ポート（関数レコード）。
    type UserStore =
        { FindByUsername: string -> UserRecord option }

    /// PBKDF2（SHA-256）によるパスワードハッシュ。形式: "iterations.saltBase64.hashBase64"。
    module Password =

        let private iterations = 100_000
        let private saltSize = 16
        let private keySize = 32

        let hash (plain: string) : string =
            let salt = RandomNumberGenerator.GetBytes saltSize

            use pbkdf2 =
                new Rfc2898DeriveBytes(plain, salt, iterations, HashAlgorithmName.SHA256)

            let key = pbkdf2.GetBytes keySize
            sprintf "%d.%s.%s" iterations (Convert.ToBase64String salt) (Convert.ToBase64String key)

        let verify (plain: string) (stored: string) : bool =
            match stored.Split('.') with
            | [| iterStr; saltB64; keyB64 |] ->
                match Int32.TryParse iterStr with
                | true, iter ->
                    let salt = Convert.FromBase64String saltB64
                    let expected = Convert.FromBase64String keyB64
                    use pbkdf2 = new Rfc2898DeriveBytes(plain, salt, iter, HashAlgorithmName.SHA256)
                    let actual = pbkdf2.GetBytes expected.Length
                    CryptographicOperations.FixedTimeEquals(actual, expected)
                | _ -> false
            | _ -> false

    /// ユーザー名・パスワードを検証し、成功時に認証済みユーザーを返す。
    let authenticate (store: UserStore) (username: string) (password: string) : AuthenticatedUser option =
        match store.FindByUsername username with
        | Some u when u.Enabled && Password.verify password u.PasswordHash ->
            Some
                { Username = u.Username
                  Roles = u.Roles }
        | _ -> None

    /// Donald による users / user_roles ストア実装。
    module UserStore =

        let create (conn: IDbConnection) : UserStore =
            let findByUsername (username: string) : UserRecord option =
                let userOpt =
                    conn
                    |> Db.newCommand "SELECT id, username, password, enabled FROM users WHERE username = @u"
                    |> Db.setParams [ "u", SqlType.String username ]
                    |> Db.querySingle (fun rd ->
                        rd.ReadInt64 "id", rd.ReadString "username", rd.ReadString "password", rd.ReadBoolean "enabled")

                match userOpt with
                | None -> None
                | Some(id, uname, pw, enabled) ->
                    let roles =
                        conn
                        |> Db.newCommand "SELECT role FROM user_roles WHERE user_id = @id"
                        |> Db.setParams [ "id", SqlType.Int64 id ]
                        |> Db.query (fun rd -> rd.ReadString "role")

                    Some
                        { Username = uname
                          PasswordHash = pw
                          Enabled = enabled
                          Roles = roles }

            { FindByUsername = findByUsername }
