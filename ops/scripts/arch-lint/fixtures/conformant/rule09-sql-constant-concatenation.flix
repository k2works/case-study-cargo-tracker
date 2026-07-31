// 適法: SQL の定数同士の連結（可読性のため。変数の埋め込みはない）
// パス: src/tracking/infrastructure/repositories/Ok.flix 相当
mod TrackingOkSql {
    def selectEventsSql(): String =
        "SELECT e.event_time, e.event_type, l.name AS location_name " +
        "FROM tracking_handling_event e " +
        "WHERE a.tracking_number = ? " +
        "ORDER BY e.event_time DESC"
}
