///
/// 受入テスト用のアプリケーション起動支援。
///
/// マイグレーション適用 → シードデータ投入 → サーバ起動 → テスト実行 → 停止 を行う。
///
mod TestApp {
    use SharedDbMigration.migrate
    use SharedDbPool.{create, h2Config, close}
    use SharedDbPool.DataSource
    use SharedHttpServer.{port, stop, instanceId}
    use AppComposition.startServer

    ///
    /// アプリケーションを起動してテストを実行する。
    ///
    /// ポートは 0 を指定して OS に空きポートを選ばせる（並列実行時の競合を防ぐ）。
    ///
    pub def withApp(dbName: String, f: Int32 -> a \ ef + IO): a \ ef + IO =
        withAppAndDb(dbName, (p, _) -> f(p))

    ///
    /// ポートと**この起動の識別子**を渡してテストを実行する。
    ///
    /// 応答の `X-App-Instance` と突き合わせるために要る。応答に識別子を
    /// 添えるだけでは、受け取った値が期待どおりかを判断できない
    /// （IT6 レビュー H5）。残る仮説（`stop(0)` 直後のポート再割り当てで
    /// **別の起動が応答する**）は、この突合でしか検出できない。
    ///
    pub def withAppInstance(dbName: String, f: (Int32, String) -> a \ ef + IO): a \ ef + IO =
        let config = h2Config(dbName);
        discard migrate(config);
        let ds = create(config);
        TrackingFixtures.seedWithArrival(ds);
        AuthFixtures.seedUsers(ds);
        let server = startServer(ds, 0);
        let result = f(port(server), instanceId(server));
        stop(server);
        close(ds);
        result

    ///
    /// データソースも渡してテストを実行する。
    ///
    /// **HTTP からは作れない状態**を用意するために要る。IT6 では
    /// 「利用者と荷主の紐付け」がそれにあたる（画面から設定する機能は無く、
    /// 運用では利用者登録時に決まる）。可視範囲（観点表の軸 6）を検証するには
    /// この状態が要る。
    ///
    /// 通常は `withApp` を使う。**利用者と同じ経路で状態を作れるなら
    /// そちらを使う**（IT4 で「一覧に出ている値だけで予約が完了する」形の
    /// テストが設計の欠陥を見つけた経緯がある）。
    ///
    pub def withAppAndDb(dbName: String,
                         f: (Int32, DataSource) -> a \ ef + IO): a \ ef + IO =
        let config = h2Config(dbName);
        discard migrate(config);
        let ds = create(config);
        TrackingFixtures.seedWithArrival(ds);
        AuthFixtures.seedUsers(ds);
        let server = startServer(ds, 0);
        let result = f(port(server), ds);
        stop(server);
        close(ds);
        result
}
