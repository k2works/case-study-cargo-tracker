///
/// 規約 12 の負例: BC の SQL が他コンテキストのテーブルを引く。
///
/// **実コードの形で書く**（IT9 レビュー M2）。`JdbcCargoRepo` が荷主名で
/// 絞り込むために `shipper` を JOIN する——実際に起きうる形であり、
/// モジュール参照は増えないため**規約 4 は通ってしまう**。
///
mod BookingJdbcCargoRepo {
    def searchSql(): String =
        "SELECT c.booking_id, c.origin_unlocode " +
        "FROM cargo c " +
        "JOIN shipper s ON s.shipper_id = c.shipper_id " +
        "WHERE LOWER(s.name) LIKE ?"
}
