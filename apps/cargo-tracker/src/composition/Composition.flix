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
    use SharedHttpServer.{RunningServer, start, dispatchGuarded, tryDispatch}
    use TrackingJdbcReadDb.withJdbcReadDb

    use SharedJdbcSession.withJdbcSession
    use SharedJdbcUserRepo.withJdbcUserRepo
    use SharedBcryptPassword.withBcryptPassword
    use SharedPassword.Password
    use SharedSession.Session
    use SharedUserRepo.UserRepo
    use SharedHttpAuth.{Decision, decide, cookieValue, sessionCookieName,
                        setSessionCookie, clearSessionCookie, withHeader,
                        redirect, forbidden, isStateChanging, isValidCsrf, csrfFieldName}
    ///
    /// ルーティング表。
    ///
    /// 認可要件（RequiredRole）をルート定義の一部として持つ。
    /// IT1 のルートはすべて `Anonymous`（認証基盤は IT3）。
    ///
    /// ログイン処理のルーティング表（資格情報の検証を行うため効果が最も多い）
    pub def loginRoutes(): List[Route[Session + UserRepo + Password + IO]] =
        Route.Route(HttpMethod.Post, SharedHttp.PathPattern.PathPattern("/login"),
                    SharedHttp.RequiredRole.Anonymous, SharedAuthRoutes.loginSubmit) ::
        Nil

    ///
    /// セッションのみを要する画面のルーティング表。
    ///
    /// **表を効果集合ごとに分けている理由**: Flix の効果は不変であり、宣言効果と
    /// 実際に使う効果が一致していなければならない（宣言だけ広げるとコンパイルエラー）。
    /// そのため要求する効果が異なるハンドラを 1 つの表へ混在させられない。
    /// `handleDynamic` が表を順に試す。
    ///
    pub def sessionRoutes(): List[Route[Session]] =
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/login"),
                    SharedHttp.RequiredRole.Anonymous, SharedAuthRoutes.loginPage) ::
        Route.Route(HttpMethod.Post, SharedHttp.PathPattern.PathPattern("/logout"),
                    SharedHttp.RequiredRole.RoleAnyOf(SharedSecurity.allRoles()),
                    SharedAuthRoutes.logout) ::
        // ダッシュボード（全ロール共通の入口）
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/"),
                    SharedHttp.RequiredRole.RoleAnyOf(SharedSecurity.allRoles()),
                    SharedAuthRoutes.dashboard) ::
        Nil

    ///
    /// 貨物追跡のルーティング表（`ReadDb` を要求する）。
    ///
    /// 認証系と分けているのは、Flix の効果が不変で、要求する効果の異なるハンドラを
    /// 1 つの表へ混在させられないためである。表は `handleDynamic` が順に試す。
    ///
    pub def trackingRoutes(): List[Route[TrackingReadDb.ReadDb]] =
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/public/tracking/{trackingNumber}"),
                    SharedHttp.RequiredRole.Anonymous, TrackingPublicRoutes.show) ::
        // 追跡番号を伴わない入口（入力フォームのみ）。照会はクエリパラメータで受ける
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/public/tracking"),
                    SharedHttp.RequiredRole.Anonymous, TrackingPublicRoutes.search) ::
        Nil

    /// 効果を要求しないルーティング表（ヘルスチェック）
    pub def staticRoutes(): List[Route[{}]] =
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/health/live"),
                    SharedHttp.RequiredRole.Anonymous, SharedHealthRoutes.live) ::
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
                    withJdbcUserRepo(() ->
                        withBcryptPassword(() -> {
                            let principal = Session.current();
                            let csrf = Session.csrfToken();
                            let g = guard(principal, csrf);
                            match tryDispatch(loginRoutes(), req, g) {
                                case Some(res) => res
                                case None      =>
                                    match tryDispatch(sessionRoutes(), req, g) {
                                        case Some(res) => res
                                        case None      => withJdbcReadDb(() ->
                                            match tryDispatch(trackingRoutes(), req, g) {
                                                case Some(res) => res
                                                case None      => dispatchGuarded(staticRoutes(), req, g)
                                            })
                                    }
                            }
                        }))));
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
                if (requiresCsrf(required) and isStateChanging(SharedHttp.method(req))
                    and not isValidCsrf(csrf, SharedHttp.formParam(req, csrfFieldName())))
                    Some(forbidden())
                else
                    None
        }

    ///
    /// CSRF トークンの検証対象か。
    ///
    /// 未認証（`Anonymous`）のルートは対象外とする。ログインはセッションが成立する
    /// 前の操作であり、検証すべきトークンがまだ存在しない。
    ///
    /// ログイン自体を狙う CSRF（攻撃者の資格情報でログインさせる）は防げないが、
    /// 本システムでは未認証で変更できる業務データがないため実害がない。
    /// 対策が必要になった時点で、未認証セッションの発行を ADR で判断する。
    ///
    def requiresCsrf(required: SharedHttp.RequiredRole): Bool = match required {
        case SharedHttp.RequiredRole.Anonymous => false
        case _                                 => true
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
