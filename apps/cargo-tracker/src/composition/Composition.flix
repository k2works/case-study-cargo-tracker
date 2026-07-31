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
    use SharedDbTx.Tx
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
        // ダッシュボード（全ロール共通の入口）
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/"),
                    SharedHttp.RequiredRole.RoleAnyOf(SharedSecurity.allRoles()),
                    SharedAuthRoutes.dashboard) ::
        Nil

    /// `Session + IO` を要するルーティング表（監査ログ・環境判定で IO を要する）
    pub def logoutRoutes(): List[Route[Session + IO]] =
        Route.Route(HttpMethod.Post, SharedHttp.PathPattern.PathPattern("/logout"),
                    SharedHttp.RequiredRole.RoleAnyOf(SharedSecurity.allRoles()),
                    SharedAuthRoutes.logout) ::
        // ログイン画面（開発環境の既定値を出すため環境判定の IO を要する）
        Route.Route(HttpMethod.Get, SharedHttp.PathPattern.PathPattern("/login"),
                    SharedHttp.RequiredRole.Anonymous, SharedAuthRoutes.loginPage) ::
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

    ///
    /// 効果を要求しないルーティング表。
    ///
    /// 表の分割基準は**効果集合**であって業務ではない。名前もそれに合わせる
    /// （`SharedHttpStatic` の静的ファイル配信とは別物）。
    ///
    pub def pureRoutes(): List[Route[{}]] =
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
        // DB を必要としない経路はトランザクションの手前で処理する。
        // liveness は「DB を見ない」ことが仕様であり（DB 障害時の再起動ループを防ぐ）、
        // ここでコネクションを取ると防ごうとした事象そのものを起こす。
        // 未知パスの 404 も同じ理由で手前に置く。
        match tryDispatch(pureRoutes(), req, anonymousGuard()) {
            case Some(res) => res
            case None      =>
                if (SharedHttp.path(req) == "/health/ready")
                    SharedHealthRoutes.ready(ds)
                else
                    handleWithSession(ds, req)
        }

    /// 認可を要しない経路の関門（`pureRoutes` はすべて `Anonymous`）
    def anonymousGuard(): SharedHttp.RequiredRole -> Request -> Option[Response] =
        _ -> _ -> None

    ///
    /// セッションを伴うリクエストを処理する。
    ///
    /// **`withBcryptPassword` をトランザクションの外側に置く**。BCrypt はコスト 12 で
    /// 1 回あたり数百ミリ秒かかり、内側に置くと計算中も DB コネクションを掴んだままになる。
    /// ログインの同時実行がそのままプールの専有時間に効くため、枯渇の第一候補になる。
    ///
    def handleWithSession(ds: DataSource, req: Request): Response \ IO =
        let cookieId = cookieValue(SharedHttp.header(req, "cookie"), sessionCookieName());
        // セッションの発行・破棄はハンドラの内側で起きるため、Cookie の指示を
        // ここで受け取り、レスポンスの組み立て時に反映する
        let cookieDirective = Ref.fresh(Static, None);
        let response = withBcryptPassword(() ->
            transactional(ds, () ->
                withJdbcSession(cookieId, change -> Ref.put(Some(change), cookieDirective), () ->
                    withJdbcUserRepo(() -> {
                        // 認可判定の入力はリクエスト開始時点で固定する。
                        // 同一リクエスト内でログインが成立しても guard は見ない（PRG のため問題ない）
                        let principal = Session.current();
                        let csrf = Session.csrfToken();
                        let g = guard(principal, csrf, Session.terminationReason());
                        dispatchAll(req, g)
                    }))));
        applyCookie(response, Ref.get(cookieDirective))

    ///
    /// 効果集合ごとに分けた表を順に試す。
    ///
    /// Flix は効果の上位変換を持たないため（宣言効果と実効果の完全一致が必要）、
    /// 要求する効果が異なるハンドラを 1 つの表へ入れられない。表を分けて順に試すのが
    /// 現時点で取りうる形である。**単一表への統合は IT4 の課題**とする
    /// （IT3 レビューでの指摘。BC が増えるほどこの構造の負担が増す）。
    ///
    def dispatchAll(req: Request, g: SharedHttp.RequiredRole -> Request -> Option[Response]):
            Response \ Session + UserRepo + Password + Tx + IO =
        match tryDispatch(loginRoutes(), req, g) {
            case Some(res) => res
            case None      => dispatchAfterLogin(req, g)
        }

    /// ログイン以外の表を試す
    def dispatchAfterLogin(req: Request, g: SharedHttp.RequiredRole -> Request -> Option[Response]):
            Response \ Session + Tx + IO =
        match tryDispatch(logoutRoutes(), req, g) {
            case Some(res) => res
            case None      =>
                match tryDispatch(sessionRoutes(), req, g) {
                    case Some(res) => res
                    case None      => dispatchTracking(req, g)
                }
        }

    /// 貨物追跡の表を試す（`ReadDb` を要する）
    def dispatchTracking(req: Request, g: SharedHttp.RequiredRole -> Request -> Option[Response]):
            Response \ Tx + IO =
        withJdbcReadDb(() ->
            match tryDispatch(trackingRoutes(), req, g) {
                case Some(res) => res
                case None      => SharedHttpServer.notFoundResponse()
            })

    ///
    /// 認可と CSRF の関門。
    ///
    /// ハンドラを呼ぶ**前**に判定する。順序が逆だと、認可で弾くはずのリクエストで
    /// 副作用が起きる。判定そのものは純粋関数（`decide` / `isValidCsrf`）に委ねる。
    ///
    def guard(principal: Option[SharedSecurity.Principal], csrf: Option[String],
              reason: Option[SharedSession.TerminationReason]):
            SharedHttp.RequiredRole -> Request -> Option[Response] =
        required -> req -> match decide(required, principal) {
            case Decision.RequireLogin => Some(redirect(loginUrl(reason)))
            case Decision.Deny         => Some(forbidden())
            case Decision.Allow        =>
                if (requiresCsrf(required) and isStateChanging(SharedHttp.method(req))
                    and not isValidCsrf(csrf, SharedHttp.formParam(req, csrfFieldName())))
                    Some(forbidden())
                else
                    None
        }

    ///
    /// ログイン画面の URL。
    ///
    /// セッションが終了していた理由を伝える。**「操作がなかったため」と
    /// 「別端末でログインされたため」を取り違えない**ためであり、
    /// 事実と違う案内は「入力途中の内容が消えた」という障害報告につながる。
    ///
    def loginUrl(reason: Option[SharedSession.TerminationReason]): String = match reason {
        case Some(SharedSession.TerminationReason.Timeout)    => "/login?timeout"
        case Some(SharedSession.TerminationReason.Superseded) => "/login?superseded"
        case None                                             => "/login"
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
            case Some(None)            => withHeader(res, "Set-Cookie", clearSessionCookie(SharedAppEnv.isSecureCookie()))
            case Some(Some(sessionId)) => withHeader(res, "Set-Cookie", setSessionCookie(sessionId, SharedAppEnv.isSecureCookie()))
        }

    /// サーバを起動する
    pub def startServer(ds: DataSource, portNumber: Int32): RunningServer \ IO =
        start(portNumber, req -> handleRequest(ds, req))
}
