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
    import java.sql.ResultSet
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

    ///
    /// 荷主名での検索 SQL（IT8・TS14。IT6 レビュー M4）。
    ///
    /// **荷主コードも対象にする**。営業が控えているのはどちらか一方であり、
    /// 欄を 2 つに分けると「どちらに入れるか」を利用者に考えさせることになる。
    ///
    /// 取るのは `shipper_id` だけである。ここで名前まで返すと、
    /// 呼び出し側が「ついでに表示に使う」ようになり越境が始まる。
    ///
    /// `ESCAPE '\'` を明示するのは、入力に含まれる `%` や `_` を
    /// ワイルドカードとして扱わないためである（一覧の絞り込みと同じ）。
    ///
    def findIdsByNameSql(): String =
        "SELECT shipper_id FROM shipper " +
        "WHERE LOWER(name) LIKE ? ESCAPE '\\' OR LOWER(shipper_code) LIKE ? ESCAPE '\\'"

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

    def findIdsByNameInDb(namePart: String): List[String] \ Tx + IO =
        let pattern = "%" + escapeLike(String.toLowerCase(String.trim(namePart))) + "%";
        let conn = Tx.connection();
        withPreparedStatement(conn, findIdsByNameSql(), stmt -> {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            let rs = stmt.executeQuery();
            let ids = collectIds(rs, Nil);
            rs.close();
            List.reverse(ids)
        })

    def collectIds(rs: ResultSet, acc: List[String]): List[String] \ IO =
        if (rs.next()) collectIds(rs, rs.getString("shipper_id") :: acc) else acc

    ///
    /// `LIKE` のメタ文字を打ち消す。
    ///
    /// **`\` を最初に置換する**。後に回すと、`%` を `\%` へ変えた後の `\` まで
    /// 二重に打ち消してしまう（`JdbcCargoRepo` と同じ理由・同じ実装）。
    ///
    def escapeLike(value: String): String =
        value
            |> String.replace(src = "\\", dst = "\\\\")
            |> String.replace(src = "%", dst = "\\%")
            |> String.replace(src = "_", dst = "\\_")

    /// ACL ポートに JDBC 実装を注入する
    pub def withJdbcShipperExistence(f: Unit -> a \ ef + ShipperExistenceChecker):
            a \ (ef - ShipperExistenceChecker) + Tx + IO =
        run {
            f()
        } with handler ShipperExistenceChecker {
            def resolveShipperId(shipperCode, k) = k(resolveInDb(shipperCode))
            def describeShipper(shipperId, k) = k(describeInDb(shipperId))
            def findShipperIdsByName(namePart, k) = k(findIdsByNameInDb(namePart))
        }
}
