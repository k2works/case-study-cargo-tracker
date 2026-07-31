mod MigrationTest {
    use SharedDbMigration.migrate
    use SharedDbPool.{create, h2Config}
    use SharedDbPool.DataSource
    use SharedDbPool.DbConfig
    use SharedDbSchema.tableExists

    /// テスト用の H2 設定（PostgreSQL 互換モード）
    pub def testConfig(dbName: String): DbConfig = h2Config(dbName)

    /// テスト用の H2 データソース
    pub def testDataSource(dbName: String): DataSource \ IO =
        create(h2Config(dbName))

    /// マイグレーションが IT1 スコープの 3 テーブルを作成すること
    @Test
    def testMigrationCreatesTables(): Unit \ Assert + IO =
        let config = testConfig("migration_creates");
        discard migrate(config);
        let ds = create(config);
        Assert.assertTrue(
            tableExists(ds, "location") and
            tableExists(ds, "tracking_activity") and
            tableExists(ds, "tracking_handling_event")
        )

    /// マイグレーションは冪等である（2 回目は再適用されない）
    @Test
    def testMigrationIsIdempotent(): Unit \ Assert + IO =
        let config = testConfig("migration_idempotent");
        discard migrate(config);
        let applied = migrate(config);
        Assert.assertEq(expected = 0, applied)
}
