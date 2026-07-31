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
    use SharedDbPool.withConnection

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
    /// コネクションは `withConnection` により、例外時も含めて必ず返却される。
    ///
    pub def readOnly(ds: DataSource, f: Unit -> a \ ef + Tx): a \ (ef - Tx) + IO =
        withConnection(ds, conn -> {
            conn.setReadOnly(true);
            run {
                f()
            } with handler Tx {
                def connection(k) = k(conn)
                def afterCommit(_action, k) = k()   // 読み取り専用では登録を無視する
            }
        })

    ///
    /// 書き込みトランザクションを実行する。
    ///
    /// 自動コミットを切り、処理の完了後にコミットする。
    ///
    /// **例外時のロールバックについて**: Flix の `try/catch` はネストしたラムダで
    /// 不正なバイトコードを生成する不具合があるため（手順書 7.5 の制約 12）、
    /// ここでは例外を捕捉しない。処理が例外で中断した場合はコミットに到達せず、
    /// `withConnection` がコネクションを閉じる際に JDBC が未コミットの変更を破棄する。
    /// 結果としてロールバックと同じ状態になる。
    ///
    /// `afterCommit` に登録された処理はコミット成功後に実行する。コミット前に実行すると、
    /// ロールバックされた変更に対して通知やイベント配信が走ることになる。
    ///
    pub def transactional(ds: DataSource, f: Unit -> a \ ef + Tx): a \ (ef - Tx) + IO =
        withConnection(ds, conn -> {
            conn.setAutoCommit(false);
            let pending = Ref.fresh(Static, Nil);
            let result = run {
                f()
            } with handler Tx {
                def connection(k) = k(conn)
                def afterCommit(action, k) = {
                    Ref.put(action :: Ref.get(pending), pending);
                    k()
                }
            };
            conn.commit();
            Ref.get(pending) |> List.reverse |> List.forEach(action -> action());
            result
        })
}
