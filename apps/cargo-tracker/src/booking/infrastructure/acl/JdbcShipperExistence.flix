///
/// `ShipperExistenceChecker`（ACL）の JDBC 実装。
///
/// **`shipper` テーブルを直接引く**。Shipper Context のモジュール
/// （`ShipperModel` / `ShipperRepo`）は参照しない。参照すると Booking が
/// Shipper のモデルに依存し、`arch-lint` 規約 4 に違反する。
///
/// ACL の実装がインフラ層にあるのはこのためである。**コンテキスト間の結合を
/// SQL の 1 文に閉じ込める**ことで、ドメイン層とアプリケーション層は
/// 相手のモデルを知らずに済む。
///
mod BookingJdbcShipperExistence {
    use SharedDbPool.withPreparedStatement
    use SharedDbTx.Tx
    use ShipperExistenceChecker.ShipperExistenceChecker

    ///
    /// 存在確認の SQL。
    ///
    /// 取るのは `shipper_id` だけである。氏名や割引率まで取れる形にすると、
    /// いずれ「ついでに氏名も」と越境が始まる。
    ///
    def resolveSql(): String = "SELECT shipper_id FROM shipper WHERE shipper_code = ?"

    def resolveInDb(shipperCode: String): Option[String] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, resolveSql(), stmt -> {
            stmt.setString(1, String.toUpperCase(String.trim(shipperCode)));
            let rs = stmt.executeQuery();
            let found = if (rs.next()) Some(rs.getString("shipper_id")) else None;
            rs.close();
            found
        })

    /// ACL ポートに JDBC 実装を注入する
    pub def withJdbcShipperExistence(f: Unit -> a \ ef + ShipperExistenceChecker):
            a \ (ef - ShipperExistenceChecker) + Tx + IO =
        run {
            f()
        } with handler ShipperExistenceChecker {
            def resolveShipperId(shipperCode, k) = k(resolveInDb(shipperCode))
        }
}
