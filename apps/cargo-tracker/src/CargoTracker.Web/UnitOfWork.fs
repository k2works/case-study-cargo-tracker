namespace CargoTracker.Web

open System.Data
open CargoTracker.Shared.Domain

// UnitOfWork + post-commit ドメインイベントディスパッチ（ADR-0002）。
// トランザクションのコミット成功後にのみイベントを発行し、
// ロールバック時は未コミットデータに基づく通知を他コンテキストへ届けない。

module UnitOfWork =

    /// トランザクション境界でワークフローを実行する。
    ///  - work が Ok を返す → コミット後にイベントを順次ディスパッチする
    ///  - work が Error を返す / 例外 → ロールバックし、イベントは発行しない
    let execute
        (conn: IDbConnection)
        (dispatch: 'event -> Async<unit>)
        (work: IDbTransaction -> Async<Result<'a * 'event list, DomainError>>)
        : Async<Result<'a, DomainError>> =
        async {
            use tx = conn.BeginTransaction()

            try
                let! result = work tx

                match result with
                | Ok(value, events) ->
                    tx.Commit()

                    for e in events do
                        do! dispatch e

                    return Ok value
                | Error err ->
                    tx.Rollback()
                    return Error err
            with ex ->
                tx.Rollback()
                return Error(BusinessRuleViolation("UnitOfWork", ex.Message))
        }
