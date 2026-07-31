///
/// トランザクション効果。
///
/// Flix には宣言的トランザクション（`@Transactional`）が存在しないため、
/// 「進行中のトランザクション」を効果として表現する。
///
/// **この設計の要点**（docs/design/architecture_backend.md のトランザクション設計）:
///
/// 1. リポジトリのハンドラは自分でコネクションを取得せず、必ず `Tx.connection()` から受け取る。
///    これにより同一トランザクション内の全 SQL が同一コネクション上で実行されることが保証される
/// 2. `transactional` の外でリポジトリ効果を使うと `Tx` が解決されずコンパイルエラーになる
/// 3. コネクションのプールからの借用・返却は `transactional` の 1 箇所に集約される
///
mod SharedDbTx {
    import java.sql.Connection

    use SharedDbPool.DataSource
    use SharedDbPool.borrow

    ///
    /// 進行中のトランザクションを表す効果。
    ///
    /// `afterCommit` はコミット成功後に実行する処理を登録する。
    /// ドメインイベントの after-commit 配信はこの仕組みの上に構築する（IT8）。
    ///
    pub eff Tx {
        def connection(): Connection
        def afterCommit(action: Unit -> Unit \ IO): Unit
    }

    ///
    /// 読み取り専用トランザクションを実行する。
    ///
    /// プールからコネクションを 1 本借り、その 1 本を `Tx` 効果として供給する。
    /// 読み取り専用のためコミットもコールバックも行わない。
    ///
    pub def readOnly(ds: DataSource, f: Unit -> a \ ef + Tx): a \ (ef - Tx) + IO =
        let conn = borrow(ds);
        conn.setReadOnly(true);
        let result = run {
            f()
        } with handler Tx {
            def connection(k) = k(conn)
            def afterCommit(_action, k) = k()   // 読み取り専用では登録を無視する
        };
        conn.close();
        result
}
