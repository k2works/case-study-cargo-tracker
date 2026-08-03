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
    /// 取るのは `shipper_id` だけである。
    ///
    def resolveSql(): String = "SELECT shipper_id FROM shipper WHERE shipper_code = ?"

    ///
    /// 表示情報の SQL。
    ///
    /// **取るのは荷主コードと氏名 / 社名だけ**である。予約詳細が「誰の貨物か」を
    /// 示すのに要るのはこの 2 つで、割引率や契約番号は Booking の関心ではない。
    /// 「ついでに全部取る」形にすると、いずれ越境が始まる。
    ///
    def describeSql(): String =
        "SELECT shipper_code, name FROM shipper WHERE shipper_id = ?"

    def resolveInDb(shipperCode: String): Option[String] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, resolveSql(), stmt -> {
            stmt.setString(1, String.toUpperCase(String.trim(shipperCode)));
            let rs = stmt.executeQuery();
            let found = if (rs.next()) Some(rs.getString("shipper_id")) else None;
            rs.close();
            found
        })

    def describeInDb(shipperId: String): Option[(String, String)] \ Tx + IO =
        let conn = Tx.connection();
        withPreparedStatement(conn, describeSql(), stmt -> {
            stmt.setString(1, String.trim(shipperId));
            let rs = stmt.executeQuery();
            let found = if (rs.next())
                            Some((rs.getString("shipper_code"), rs.getString("name")))
                        else
                            None;
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
            def describeShipper(shipperId, k) = k(describeInDb(shipperId))
        }
}
