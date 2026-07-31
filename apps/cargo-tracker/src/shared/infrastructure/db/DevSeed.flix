///
/// 開発用サンプルデータの投入。
///
/// **業務データのシードをマイグレーションに置かない**のは、テストが自前で用意する
/// データと衝突するためである（IT1 で実際に一意制約違反が発生した）。
/// マイグレーションに置くのはマスタデータ（`location`）までとし、
/// 業務データは開発起動時にのみ投入する。
///
mod SharedDbDevSeed {
    use SharedDbPool.DataSource
    use SharedDbPool.{withConnection, withStatement}

    /// サンプルの追跡データを投入する（冪等）
    pub def seed(ds: DataSource): Unit \ IO =
        withConnection(ds, conn -> withStatement(conn, stmt -> {
        discard stmt.executeUpdate(
            "MERGE INTO tracking_activity (id, tracking_number, booking_id, transport_status) " +
            "KEY (tracking_number) VALUES (1, 'TRK-20260803-0001', 'BK-0001', 'ONBOARD_CARRIER')");
        discard stmt.executeUpdate("DELETE FROM tracking_handling_event WHERE tracking_id = 1");
        discard stmt.executeUpdate(
            "INSERT INTO tracking_handling_event " +
            "(tracking_id, event_type, event_time, location_unlocode, voyage_number) VALUES " +
            "(1, 'RECEIVE', TIMESTAMP '2026-08-02 14:00:00', 'JPTYO', NULL), " +
            "(1, 'LOAD', TIMESTAMP '2026-08-03 09:15:00', 'JPTYO', 'V0042')")
        }))
}
