module CargoTracker.IntegrationTests.AuthTests

open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Web
open CargoTracker.Web.Auth

// タスク 2.1/2.2: パスワードハッシュとユーザーストア・認証（ADR-0005）。

// ---- パスワードハッシュ（純粋・DB 不要）----

[<Fact>]
let ``ハッシュしたパスワードは正しい平文で検証成功する`` () =
    let stored = Password.hash "s3cret-pass"
    Password.verify "s3cret-pass" stored |> should equal true

[<Fact>]
let ``誤ったパスワードは検証失敗する`` () =
    let stored = Password.hash "s3cret-pass"
    Password.verify "wrong-pass" stored |> should equal false

[<Fact>]
let ``同じ平文でもハッシュはソルトにより毎回異なる`` () =
    Password.hash "same" |> should not' (equal (Password.hash "same"))

// ---- ユーザーストア + 認証（SQLite 統合）----

let private seedDb () : IDbConnection =
    let conn = new SqliteConnection("Data Source=:memory:")
    conn.Open()
    use cmd = conn.CreateCommand()

    cmd.CommandText <-
        """
        CREATE TABLE users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL UNIQUE,
            email TEXT NOT NULL UNIQUE,
            password TEXT NOT NULL,
            enabled INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL
        );
        CREATE TABLE user_roles (
            user_id INTEGER NOT NULL,
            role TEXT NOT NULL,
            PRIMARY KEY (user_id, role)
        );
        """

    cmd.ExecuteNonQuery() |> ignore

    let hash = Password.hash "pw"
    use ins = conn.CreateCommand()

    ins.CommandText <-
        sprintf
            """
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('sales01', 'sales01@example.com', '%s', 1, '2026-07-14');
            INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_SALES');
            INSERT INTO users (username, email, password, enabled, created_at)
            VALUES ('disabled', 'd@example.com', '%s', 0, '2026-07-14');
            INSERT INTO user_roles (user_id, role) VALUES (2, 'ROLE_SALES');
            """
            hash
            hash

    ins.ExecuteNonQuery() |> ignore
    conn :> IDbConnection

[<Fact>]
[<Trait("Category", "Integration")>]
let ``ユーザーストアはロール付きでユーザーを取得する`` () =
    use conn = seedDb ()
    let store = UserStore.create conn

    match store.FindByUsername "sales01" with
    | Some u -> u.Roles |> should equal [ "ROLE_SALES" ]
    | None -> failwith "ユーザーが見つかりません"

[<Fact>]
[<Trait("Category", "Integration")>]
let ``正しい資格情報で認証に成功しロールを返す`` () =
    use conn = seedDb ()
    let store = UserStore.create conn

    match authenticate store "sales01" "pw" with
    | Some user ->
        user.Username |> should equal "sales01"
        user.Roles |> should equal [ "ROLE_SALES" ]
    | None -> failwith "認証に成功するはず"

[<Fact>]
[<Trait("Category", "Integration")>]
let ``誤ったパスワードでは認証に失敗する`` () =
    use conn = seedDb ()
    let store = UserStore.create conn
    authenticate store "sales01" "bad" |> should equal None

[<Fact>]
[<Trait("Category", "Integration")>]
let ``無効化されたユーザーは認証に失敗する`` () =
    use conn = seedDb ()
    let store = UserStore.create conn
    authenticate store "disabled" "pw" |> should equal None
