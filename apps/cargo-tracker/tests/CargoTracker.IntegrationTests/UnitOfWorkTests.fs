module CargoTracker.IntegrationTests.UnitOfWorkTests

open System.Data
open Microsoft.Data.Sqlite
open Xunit
open FsUnit.Xunit
open CargoTracker.Shared.Domain
open CargoTracker.Web

// タスク 1.4 / ADR-0002 コンプライアンス:
//  コミット成功後にのみイベントが発行され、ロールバック時は永続化もイベント発行も行われない。

let private openDb () : IDbConnection =
    let conn = new SqliteConnection("Data Source=:memory:")
    conn.Open()
    use cmd = conn.CreateCommand()
    cmd.CommandText <- "CREATE TABLE item (name TEXT NOT NULL)"
    cmd.ExecuteNonQuery() |> ignore
    conn :> IDbConnection

let private insertItem (conn: IDbConnection) (tx: IDbTransaction) (name: string) =
    use cmd = conn.CreateCommand()
    cmd.Transaction <- tx
    cmd.CommandText <- sprintf "INSERT INTO item (name) VALUES ('%s')" name
    cmd.ExecuteNonQuery() |> ignore

let private itemCount (conn: IDbConnection) =
    use cmd = conn.CreateCommand()
    cmd.CommandText <- "SELECT COUNT(*) FROM item"
    cmd.ExecuteScalar() |> System.Convert.ToInt32

[<Fact>]
[<Trait("Category", "Integration")>]
let ``コミット成功時はデータが永続化されイベントが発行される`` () =
    use conn = openDb ()
    let dispatched = ref []

    let dispatch e =
        async { dispatched.Value <- e :: dispatched.Value }

    let work (tx: IDbTransaction) =
        async {
            insertItem conn tx "committed"
            return Ok((), [ "ItemAdded" ])
        }

    match UnitOfWork.execute conn dispatch work |> Async.RunSynchronously with
    | Ok() ->
        itemCount conn |> should equal 1
        dispatched.Value |> should equal [ "ItemAdded" ]
    | Error e -> failwithf "Ok を期待したが Error: %A" e

[<Fact>]
[<Trait("Category", "Integration")>]
let ``ロールバック時はデータもイベントも発行されない`` () =
    use conn = openDb ()
    let dispatched = ref []

    let dispatch e =
        async { dispatched.Value <- e :: dispatched.Value }

    // 挿入した後に Error を返す → ロールバックされる
    let work (tx: IDbTransaction) =
        async {
            insertItem conn tx "rolled-back"
            return Error(BusinessRuleViolation("Test", "意図的な失敗"))
        }

    match UnitOfWork.execute conn dispatch work |> Async.RunSynchronously with
    | Error(BusinessRuleViolation _) ->
        itemCount conn |> should equal 0
        dispatched.Value |> should be Empty
    | other -> failwithf "Error を期待したが: %A" other

[<Fact>]
[<Trait("Category", "Integration")>]
let ``ワークフロー内の例外はロールバックされイベントは発行されない`` () =
    use conn = openDb ()
    let dispatched = ref []

    let dispatch e =
        async { dispatched.Value <- e :: dispatched.Value }

    let work (tx: IDbTransaction) =
        async {
            insertItem conn tx "will-throw"
            failwith "予期しない例外"
            return Ok((), [ "ShouldNotDispatch" ])
        }

    match UnitOfWork.execute conn dispatch work |> Async.RunSynchronously with
    | Error(BusinessRuleViolation("UnitOfWork", _)) ->
        itemCount conn |> should equal 0
        dispatched.Value |> should be Empty
    | other -> failwithf "UnitOfWork エラーを期待したが: %A" other
