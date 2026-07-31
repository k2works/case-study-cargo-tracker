///
/// 合成ルート。すべての効果ハンドラをここで適用する。
///
/// **arch-lint 規約 5**: `run ... with handler` の適用は本モジュールとテストコードにのみ現れる。
/// アプリケーション層の途中でハンドラを適用すると実装が固定され、差し替え可能性が失われる。
///
mod AppComposition {
    use SharedDbPool.DataSource
    use SharedDbTx.readOnly
    use SharedHttp.Response
    use SharedHttp.HttpMethod
    use SharedHttpRouter.Route
    use SharedHttpServer.{RunningServer, start, dispatch}
    use TrackingJdbcReadDb.withJdbcReadDb

    ///
    /// ルーティング表。
    ///
    /// 認可要件（RequiredRole）をルート定義の一部として持つ。
    /// IT1 のルートはすべて `Anonymous`（認証基盤は IT3）。
    ///
    pub def routes(): List[Route[TrackingReadDb.ReadDb]] =
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/public/tracking/{trackingNumber}"),
                    SharedHttp.RequiredRole.Anonymous, TrackingPublicRoutes.show) ::
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/health/live"),
                    SharedHttp.RequiredRole.Anonymous, req -> checked_ecast(HealthRoutes.live(req))) ::
        Nil

    ///
    /// 1 リクエストを処理する。
    ///
    /// リクエスト 1 件 = トランザクション 1 件 = コネクション 1 本の対応になる。
    /// 読み取り専用トランザクションの中で JDBC ハンドラを適用する。
    ///
    pub def handleRequest(ds: DataSource, methodStr: String, requestPath: String): Response \ IO =
        if (requestPath == "/health/ready")
            HealthRoutes.ready(ds)
        else
            readOnly(ds, () ->
                withJdbcReadDb(() ->
                    dispatch(routes(), methodStr, requestPath)))

    /// サーバを起動する
    pub def startServer(ds: DataSource, portNumber: Int32): RunningServer \ IO =
        start(portNumber, (m, p) -> handleRequest(ds, m, p))
}
