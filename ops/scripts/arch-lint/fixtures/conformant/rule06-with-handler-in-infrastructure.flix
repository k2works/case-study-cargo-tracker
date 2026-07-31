// 適法: infrastructure 層でハンドラを適用する
// パス: src/shared/infrastructure/db/Ok.flix 相当
mod SharedOkHandlerInInfra {
    pub def readOnly(ds: DataSource, f: Unit -> a \ ef + Tx): a \ (ef - Tx) + IO =
        withConnection(ds, conn ->
            run f() with handler Tx {
                def connection(k) = k(conn)
                def afterCommit(_a, k) = k()
            })
}
