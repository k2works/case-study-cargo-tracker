///
/// 合成ルート。すべての効果ハンドラをここで適用する。
///
/// **arch-lint 規約 5**: 効果ハンドラの**合成**（複数ハンドラの入れ子適用）は
/// 本モジュールとテストコードにのみ現れる。個々のハンドラを定義するラップ関数
/// （`withJdbcReadDb` 等）は対応するアダプタ配下に置いてよい。
///
/// アプリケーション層の途中でハンドラを適用すると実装が固定され、差し替え可能性が失われる。
///
mod AppComposition {
    use SharedDbPool.DataSource
    use SharedDbTx.readOnly
    use SharedHttp.Response
    use SharedHttp.HttpMethod
    use SharedHttpRouter.Route
    use SharedHttpServer.{RunningServer, start, dispatchWithQuery}
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
        // 追跡番号を伴わない入口（入力フォームのみ）。照会はクエリパラメータで受ける
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/public/tracking"),
                    SharedHttp.RequiredRole.Anonymous, TrackingPublicRoutes.search) ::
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/health/live"),
                    // live は効果を要求しない純粋関数のため、ルート表の効果型へ合わせて上位変換する
                    SharedHttp.RequiredRole.Anonymous, req -> checked_ecast(SharedHealthRoutes.live(req))) ::
        Nil

    ///
    /// 1 リクエストを処理する。
    ///
    /// リクエスト 1 件 = トランザクション 1 件 = コネクション 1 本の対応になる。
    /// 読み取り専用トランザクションの中で JDBC ハンドラを適用する。
    ///
    pub def handleRequest(ds: DataSource, methodStr: String, requestPath: String,
                          queryMap: Map[String, String]): Response \ IO =
        if (requestPath == "/health/ready")
            SharedHealthRoutes.ready(ds)
        else
            readOnly(ds, () ->
                withJdbcReadDb(() ->
                    dispatchWithQuery(routes(), methodStr, requestPath, queryMap)))

    /// サーバを起動する
    pub def startServer(ds: DataSource, portNumber: Int32): RunningServer \ IO =
        start(portNumber, (m, p, q) -> handleRequest(ds, m, p, q))
}
