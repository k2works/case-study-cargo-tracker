///
/// 受入テスト用のアプリケーション起動支援。
///
/// マイグレーション適用 → シードデータ投入 → サーバ起動 → テスト実行 → 停止 を行う。
///
mod TestApp {
    use SharedDbMigration.migrate
    use SharedDbPool.{create, h2Config, close}
    use SharedDbPool.DataSource
    use SharedHttpServer.{port, stop}
    use AppComposition.startServer

    ///
    /// アプリケーションを起動してテストを実行する。
    ///
    /// ポートは 0 を指定して OS に空きポートを選ばせる（並列実行時の競合を防ぐ）。
    ///
    pub def withApp(dbName: String, f: Int32 -> a \ ef + IO): a \ ef + IO =
        let config = h2Config(dbName);
        discard migrate(config);
        let ds = create(config);
        TrackingFixtures.seedWithArrival(ds);
        let server = startServer(ds, 0);
        let result = f(port(server));
        stop(server);
        close(ds);
        result
}
