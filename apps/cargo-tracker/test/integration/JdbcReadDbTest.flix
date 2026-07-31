///
/// JDBC ハンドラの統合テスト（H2 + Flyway）。
///
/// SQL の正確性と ResultSet → Read Model のデコードを検証する。
///
mod JdbcReadDbTest {
    use SharedDbMigration.migrate
    use SharedDbPool.{create, h2Config, borrow, close}
    use SharedDbPool.DataSource
    use SharedDbTx.readOnly
    use TrackingJdbcReadDb.withJdbcReadDb
    use TrackingQuery.findByTrackingNumber

    def setup(dbName: String): DataSource \ IO =
        let config = h2Config(dbName);
        discard migrate(config);
        let ds = create(config);
        let conn = borrow(ds);
        let stmt = conn.createStatement();
        discard stmt.executeUpdate(
            "INSERT INTO tracking_activity (tracking_number, booking_id, transport_status) " +
            "VALUES ('TRK-JDBC-0001', 'BK-9001', 'CLAIMED')");
        discard stmt.executeUpdate(
            "INSERT INTO tracking_handling_event (tracking_id, event_type, event_time, location_unlocode) " +
            "SELECT a.id, 'CLAIM', TIMESTAMP '2026-08-05 10:00:00', 'USLAX' " +
            "FROM tracking_activity a WHERE a.tracking_number = 'TRK-JDBC-0001'");
        stmt.close();
        conn.close();
        ds

    /// JOIN を含む読み取りクエリが Read Model へ正しくデコードされること
    @Test
    def testFindByTrackingNumberDecodesJoinResult(): Unit \ Assert + IO =
        let ds = setup("jdbc_readdb_decode");
        let result = readOnly(ds, () -> withJdbcReadDb(() -> findByTrackingNumber("TRK-JDBC-0001")));
        close(ds);
        match result {
            case None    => Assert.fail("追跡情報が見つからなかった")
            case Some(v) =>
                Assert.assertEq(expected = "CLAIMED", TrackingView.transportStatus(v));
                // location_unlocode → location.name の JOIN が効いていること
                Assert.assertEq(expected = "Los Angeles", TrackingView.currentLocation(v))
        }

    /// 存在しない追跡番号で None を返すこと
    @Test
    def testFindByTrackingNumberReturnsNoneWhenAbsent(): Unit \ Assert + IO =
        let ds = setup("jdbc_readdb_absent");
        let result = readOnly(ds, () -> withJdbcReadDb(() -> findByTrackingNumber("TRK-NOPE")));
        close(ds);
        Assert.assertTrue(Option.isEmpty(result))

    /// SQL インジェクションを試みる入力で情報漏洩・構文エラーが起きないこと（セキュリティ回帰）
    @Test
    def testSqlInjectionAttemptIsSafe(): Unit \ Assert + IO =
        let ds = setup("jdbc_readdb_injection");
        let malicious = "' OR '1'='1";
        let result = readOnly(ds, () -> withJdbcReadDb(() -> findByTrackingNumber(malicious)));
        close(ds);
        Assert.assertTrue(Option.isEmpty(result))
}
