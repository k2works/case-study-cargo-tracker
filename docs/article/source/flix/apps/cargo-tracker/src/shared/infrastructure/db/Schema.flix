///
/// スキーマの検査ユーティリティ。テストとヘルスチェックから使用する。
///
mod SharedDbSchema {
    use SharedDbPool.DataSource
    use SharedDbPool.withConnection

    ///
    /// テーブルの存在を確認する。
    ///
    /// `DatabaseMetaData` を使い、SQL を組み立てずに判定する。
    /// これにより SQL 文字列補間（arch-lint 規約 9）を避けられ、
    /// 例外を制御フローに使う必要もなくなる。
    ///
    /// 注: テーブル名は H2・PostgreSQL とも小文字で格納されるため、そのまま渡す。
    ///
    pub def tableExists(ds: DataSource, tableName: String): Bool \ IO =
        withConnection(ds, conn -> {
            let meta = conn.getMetaData();
            let nullString = unchecked_cast(null as String);
            let nullTypes = unchecked_cast(null as Array[String, Static]);
            let rs = meta.getTables(nullString, nullString, tableName, nullTypes);
            let found = rs.next();
            rs.close();
            found
        })
}
