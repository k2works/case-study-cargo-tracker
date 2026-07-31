///
/// スキーマの検査ユーティリティ。テストとヘルスチェックから使用する。
///
mod SharedDbSchema {
    import java.lang.Exception

    use SharedDbPool.DataSource
    use SharedDbPool.borrow

    /// テーブルの存在を確認する
    pub def tableExists(ds: DataSource, tableName: String): Bool \ IO =
        let conn = borrow(ds);
        let stmt = conn.createStatement();
        let found = try {
            discard stmt.executeQuery("SELECT 1 FROM ${tableName} WHERE 1 = 0");
            true
        } catch {
            case _: Exception => false
        };
        stmt.close();
        conn.close();
        found
}
