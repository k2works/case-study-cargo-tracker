///
/// 受入テスト用のアプリケーション起動支援。
///
/// マイグレーション適用 → シードデータ投入 → サーバ起動 → テスト実行 → 停止 を行う。
///
mod TestApp {
    use SharedDbMigration.migrate
    use SharedDbPool.{create, h2Config, borrow, close}
    use SharedDbPool.DataSource
    use SharedHttpServer.{port, stop}
    use AppComposition.startServer

    /// テスト用の追跡データを投入する
    def seedTrackingData(ds: DataSource): Unit \ IO =
        let conn = borrow(ds);
        let stmt = conn.createStatement();
        discard stmt.executeUpdate(
            "INSERT INTO tracking_activity (tracking_number, booking_id, transport_status) " +
            "VALUES ('TRK-20260803-0001', 'BK-0001', 'ONBOARD_CARRIER')");
        discard stmt.executeUpdate(
            "INSERT INTO tracking_handling_event (tracking_id, event_type, event_time, location_unlocode) " +
            "SELECT a.id, 'LOAD', TIMESTAMP '2026-08-03 09:15:00', 'JPTYO' " +
            "FROM tracking_activity a WHERE a.tracking_number = 'TRK-20260803-0001'");
        discard stmt.executeUpdate(
            "INSERT INTO tracking_handling_event (tracking_id, event_type, event_time, location_unlocode) " +
            "SELECT a.id, 'RECEIVE', TIMESTAMP '2026-08-02 14:00:00', 'JPTYO' " +
            "FROM tracking_activity a WHERE a.tracking_number = 'TRK-20260803-0001'");
        stmt.close();
        conn.close()

    ///
    /// アプリケーションを起動してテストを実行する。
    ///
    /// ポートは 0 を指定して OS に空きポートを選ばせる（並列実行時の競合を防ぐ）。
    ///
    pub def withApp(dbName: String, f: Int32 -> a \ ef + IO): a \ ef + IO =
        let config = h2Config(dbName);
        discard migrate(config);
        let ds = create(config);
        seedTrackingData(ds);
        let server = startServer(ds, 0);
        let result = f(port(server));
        stop(server);
        close(ds);
        result
}
