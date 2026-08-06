///
/// `LocationCatalog` 効果の JDBC 実装。
///
mod SharedJdbcLocationCatalog {
    import java.sql.ResultSet

    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx
    use SharedLocationCatalog.LocationCatalog

    /// 名称順に並べる。利用者が探すときの手掛かりは港名である
    def selectAllSql(): String =
        "SELECT unlocode, name FROM location ORDER BY name"

    def selectAll(): List[(String, String)] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, selectAllSql(), stmt -> {
            let rs = stmt.executeQuery();
            let rows = collect(rs, Nil);
            rs.close();
            List.reverse(rows)
        })

    def collect(rs: ResultSet, acc: List[(String, String)]): List[(String, String)] \ IO =
        if (rs.next())
            collect(rs, (rs.getString("unlocode"), rs.getString("name")) :: acc)
        else
            acc

    pub def withJdbcLocationCatalog(f: Unit -> a \ ef + LocationCatalog):
            a \ (ef - LocationCatalog) + Tx + IO =
        run {
            f()
        } with handler LocationCatalog {
            def all(k) = k(selectAll())
        }
}
