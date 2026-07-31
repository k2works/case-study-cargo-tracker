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
    import java.lang.System
    use SharedDbPool.DataSource
    use SharedDbTx.transactional
    use SharedHttp.Response
    use SharedHttp.HttpMethod
    use SharedHttpRouter.Route
    use SharedHttp.Request
    use SharedHttpServer.{RunningServer, start, dispatchGuarded}
    use TrackingJdbcReadDb.withJdbcReadDb
    use SharedJdbcSession.withJdbcSession
    use SharedSession.Session
    use SharedHttpAuth.{Decision, decide, cookieValue, sessionCookieName,
                        setSessionCookie, clearSessionCookie, withHeader,
                        redirect, forbidden, isStateChanging, isValidCsrf, csrfFieldName}

    ///
    /// ルーティング表。
    ///
    /// 認可要件（RequiredRole）をルート定義の一部として持つ。
    /// IT1 のルートはすべて `Anonymous`（認証基盤は IT3）。
    ///
    pub def routes(): List[Route[TrackingReadDb.ReadDb + Session]] =
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/public/tracking/{trackingNumber}"),
                    SharedHttp.RequiredRole.Anonymous,
                    req -> checked_ecast(TrackingPublicRoutes.show(req))) ::
        // 追跡番号を伴わない入口（入力フォームのみ）。照会はクエリパラメータで受ける
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/public/tracking"),
                    SharedHttp.RequiredRole.Anonymous,
                    req -> checked_ecast(TrackingPublicRoutes.search(req))) ::
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
    pub def handleRequest(ds: DataSource, req: Request): Response \ IO =
        let requestPath = SharedHttp.path(req);
        match SharedHttpStatic.handle(requestPath) {
            case Some(res) => res
            case None      => handleDynamic(ds, req)
        }

    ///
    /// 静的ファイル以外のリクエストを処理する。
    ///
    /// **トランザクションの扱い**: 現時点では全リクエストを書き込みトランザクションで包む。
    /// 読み取りのみのリクエストでもコミットが走るが、変更がないため無害である。
    /// ルートごとにモード（読み取り専用 / 書き込み）を持たせる案は、書き込み経路が
    /// 本格化する IT4 で判断する（IT2 レビュー M13）。
    ///
    def handleDynamic(ds: DataSource, req: Request): Response \ IO =
        if (SharedHttp.path(req) == "/health/ready")
            SharedHealthRoutes.ready(ds)
        else
            let cookieId = cookieValue(SharedHttp.header(req, "cookie"), sessionCookieName());
            // セッションの発行・破棄はハンドラの内側で起きるため、Cookie の指示を
            // ここで受け取り、レスポンスの組み立て時に反映する
            let cookieDirective = Ref.fresh(Static, None);
            let response = transactional(ds, () ->
                withJdbcSession(cookieId, change -> Ref.put(Some(change), cookieDirective), () ->
                    withJdbcReadDb(() -> {
                        let principal = Session.current();
                        let csrf = Session.csrfToken();
                        dispatchGuarded(routes(), req, guard(principal, csrf))
                    })));
            applyCookie(response, Ref.get(cookieDirective))

    ///
    /// 認可と CSRF の関門。
    ///
    /// ハンドラを呼ぶ**前**に判定する。順序が逆だと、認可で弾くはずのリクエストで
    /// 副作用が起きる。判定そのものは純粋関数（`decide` / `isValidCsrf`）に委ねる。
    ///
    def guard(principal: Option[SharedSecurity.Principal], csrf: Option[String]):
            SharedHttp.RequiredRole -> Request -> Option[Response] =
        required -> req -> match decide(required, principal) {
            case Decision.RequireLogin => Some(redirect("/login"))
            case Decision.Deny         => Some(forbidden())
            case Decision.Allow        =>
                if (isStateChanging(SharedHttp.method(req))
                    and not isValidCsrf(csrf, SharedHttp.formParam(req, csrfFieldName())))
                    Some(forbidden())
                else
                    None
        }

    ///
    /// セッションの変更を Cookie へ反映する。
    ///
    /// `Secure` 属性は本番（HTTPS）でのみ付ける。開発は HTTP のため、付けると
    /// ブラウザが Cookie を送らずログインが成立しない。
    ///
    def applyCookie(res: Response, directive: Option[Option[String]]): Response \ IO =
        match directive {
            case None                  => res
            case Some(None)            => withHeader(res, "Set-Cookie", clearSessionCookie(isSecure()))
            case Some(Some(sessionId)) => withHeader(res, "Set-Cookie", setSessionCookie(sessionId, isSecure()))
        }

    ///
    /// Cookie に `Secure` を付けるか。
    ///
    /// HTTPS で運用する環境では `APP_SECURE_COOKIE=true` を設定する。
    /// 既定で付けないのは、開発（HTTP）で付けるとブラウザが Cookie を送らず、
    /// ログインしても即座に未認証へ戻るためである。
    ///
    def isSecure(): Bool \ IO =
        let value = System.getenv("APP_SECURE_COOKIE");
        not Object.isNull(value) and value == "true"

    /// サーバを起動する
    pub def startServer(ds: DataSource, portNumber: Int32): RunningServer \ IO =
        start(portNumber, req -> handleRequest(ds, req))
}
