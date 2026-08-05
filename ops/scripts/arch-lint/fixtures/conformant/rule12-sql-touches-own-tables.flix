///
/// 規約 12 の正例: 自 BC のテーブルと共有マスタだけを引く。
///
/// **負例との違いは JOIN 先 1 行**である。荷主名での絞り込みは
/// ACL（`ShipperExistenceChecker` と同じ形）で解決し、SQL では引かない。
///
mod BookingJdbcCargoRepo {
    def searchSql(): String =
        "SELECT c.booking_id, c.origin_unlocode " +
        "FROM cargo c " +
        "JOIN location l ON l.unlocode = c.origin_unlocode " +
        "WHERE c.shipper_id = ?"
}
