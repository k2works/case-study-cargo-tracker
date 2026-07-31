///
/// JDBC ハンドラの統合テスト（H2 + Flyway）。
///
/// SQL の正確性と ResultSet → Read Model のデコードを検証する。
///
mod JdbcReadDbTest {
    use SharedDbMigration.migrate
    use SharedDbPool.{create, h2Config, close}
    use SharedDbPool.DataSource
    use SharedDbTx.readOnly
    use TrackingJdbcReadDb.withJdbcReadDb
    use TrackingQuery.findByTrackingNumber

    /// 推定到着日を持たない貨物（NULL のデコード経路を通す）
    def noEtaTrackingNumber(): String = "TRK-JDBC-0001"

    def setup(dbName: String): DataSource \ IO =
        let config = h2Config(dbName);
        discard migrate(config);
        let ds = create(config);
        TrackingFixtures.seedWithoutArrival(ds, noEtaTrackingNumber());
        ds

    /// JOIN を含む読み取りクエリが Read Model へ正しくデコードされること
    @Test
    def testFindByTrackingNumberDecodesJoinResult(): Unit \ Assert + IO =
        let ds = setup("jdbc_readdb_decode");
        let result = readOnly(ds, () -> withJdbcReadDb(() -> findByTrackingNumber(noEtaTrackingNumber())));
        close(ds);
        match result {
            case None    => Assert.fail("追跡情報が見つからなかった")
            case Some(v) =>
                Assert.assertEq(expected = "CLAIMED", TrackingView.transportStatus(v));
                // location_unlocode → location.name の JOIN が効いていること
                // location_unlocode → location.name の JOIN が効いていること（最新イベントの場所名）
                Assert.assertEq(expected = "Los Angeles",
                    TrackingView.events(v) |> List.head |> Option.map(TrackingView.locationName)
                                            |> Option.getWithDefault(""));
                // estimated_arrival を指定せず INSERT しているため NULL。
                // NULL → 空文字 → 画面の「未定」という経路の起点をここで固定する
                Assert.assertEq(expected = "", TrackingView.estimatedArrival(v))
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
