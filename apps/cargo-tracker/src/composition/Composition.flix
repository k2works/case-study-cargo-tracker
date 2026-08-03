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
    use SharedDbTx.{transactional, readOnly}
    use SharedDbTx.Tx
    use SharedHttp.Response
    use SharedHttp.HttpMethod
    use SharedHttp.Request
    use SharedHttpRouter.{Route, Resolution, TxMode, resolve, duplicateKeys, routeTxMode, routeKey}
    use SharedHttpServer.{RunningServer, start, runRoute}
    use TrackingJdbcReadDb.withJdbcReadDb
    use TrackingReadDb.ReadDb

    use SharedJdbcSession.withJdbcSession
    use SharedJdbcUserRepo.withJdbcUserRepo
    use SharedBcryptPassword.withBcryptPassword
    use SharedSystemClock.withSystemClock
    use SharedClock.Clock
    use SharedLocationCatalog.LocationCatalog
    use SharedJdbcLocationCatalog.withJdbcLocationCatalog
    use SharedIdGen.IdGen
    use ShipperRepo.ShipperRepo
    use ShipperJdbcRepo.withJdbcShipperRepo
    use CargoRepo.CargoRepo
    use VoyageRepo.VoyageRepo
    use BookingJdbcCargoRepo.withJdbcCargoRepo
    use RoutingJdbcVoyageRepo.withJdbcVoyageRepo
    use ShipperExistenceChecker.ShipperExistenceChecker
    use BookingJdbcShipperExistence.withJdbcShipperExistence
    use SharedUuidIdGen.withUuidIdGen
    use SharedPassword.Password
    use SharedSession.Session
    use SharedUserRepo.UserRepo
    use SharedHttpAuth.{Decision, decide, cookieValue, sessionCookieName,
                        setSessionCookie, clearSessionCookie, withHeader,
                        redirect, forbidden, isStateChanging, isValidCsrf, csrfFieldName,
                        requiresCsrf}

    ///
    /// アプリケーションのハンドラが要求しうる効果の全体集合。
    ///
    /// **1 箇所で定義する**。IT4 のレビューまでは同じ列挙を 8 箇所へ手書きしており、
    /// BC を 1 つ足すたびに 8 箇所を同時編集する必要があった。それが
    /// 「効果集合が単調増加する設計」の真のコストだった。
    ///
    /// **BC 追加時に触るのはこの 1 行と `touchAll` の本体だけ**である。
    /// IT4 時点のコメントは「`touchAll` の宣言も手書きで残る」としていたが、
    /// IT5 で実際に試したところ型別名で通った。**確かめずに制約を書いていた**。
    ///
    pub type alias AppEff = Session + UserRepo + Password + ReadDb + ShipperRepo + CargoRepo + VoyageRepo + ShipperExistenceChecker + LocationCatalog + Clock + IdGen + IO

    // ------------------------------------------------------------------
    // 効果の持ち上げ（lift）
    // ------------------------------------------------------------------

    ///
    /// アプリケーションの全ハンドラが要求しうる効果を「使った」ことにする。
    ///
    /// **なぜこれが要るか**: Flix の効果は不変であり、宣言効果と実効果が完全に一致
    /// しなければならない（使わない効果を宣言すると `Unused effect` でコンパイルエラー）。
    /// そのため、要求する効果の異なるハンドラを 1 つのリストへ入れられない。
    /// IT3 はこれを理由にルーティング表を効果集合ごとへ 4 分割していたが、その形は
    /// 「ルート表が認可の唯一の正典」という設計そのものを壊していた。
    ///
    /// **仕組み**: 効果は**構文上使われていれば宣言できる**。実行される必要はない。
    /// 引数で受け取った偽を分岐条件に使うため、コンパイラは枝を刈れず効果は型に残る。
    /// 呼び出し側は常に `false` を渡すので、**中の操作は 1 度も実行されない**
    /// （`RouterLiftTest` で呼び出し回数 0 を固定している）。
    ///
    /// **なぜポートに no-op 操作を足さないか**: `touch()` のような業務的な意味を持たない
    /// 操作をドメインのポートへ足すと、ランタイムの都合がドメイン層へ漏れる。
    /// 全ハンドラ（本番・インメモリ・テスト）に実装義務も生じる。
    /// この税は**合成ルートという配線専門の層に閉じ込める**のが筋である。
    ///
    def touchAll(shouldRun: Bool): Unit \ AppEff =
        if (shouldRun) {
            discard Session.current();
            discard UserRepo.findByUsername("");
            discard Password.verify("", "");
            discard ReadDb.findByTrackingNumber("");
            discard ShipperRepo.findAll();
            discard CargoRepo.findAll();
            discard ShipperExistenceChecker.resolveShipperId("");
            discard LocationCatalog.all();
            discard VoyageRepo.findAll();
            discard IdGen.newId();
            discard Clock.nowMillis();
            discard System.currentTimeMillis()
        } else ()

    ///
    /// ハンドラの効果集合をアプリケーション全体の集合へ揃える。
    ///
    /// 各 BC のハンドラは**自分が使う効果だけを宣言してよい**。表へ載せる時点の
    /// `lift(...)` 1 つで吸収するため、ハンドラ側の記述は増えない。
    ///
    def lift(h: Request -> Response \ ef):
            Request -> Response \ ef + AppEff =
        req -> { touchAll(false); h(req) }

    // ------------------------------------------------------------------
    // ルーティング表（単一）
    // ------------------------------------------------------------------

    ///
    /// アプリケーションの唯一のルーティング表。
    ///
    /// 認可要件（`RequiredRole`）とトランザクションモード（`TxMode`）を
    /// ルート定義の一部として持つ。**この表が認可の唯一の正典である**。
    ///
    /// 並びは業務単位（BC 単位）とする。効果集合で分けない。
    ///
    pub def appRoutes(ds: DataSource):
            List[Route[AppEff]] =
        // --- 運用（DB を使わない・使うが業務トランザクションではない） ---
        route(HttpMethod.Get, "/health/live", anonymous(), TxMode.NoTx,
              lift(SharedHealthRoutes.live)) ::
        // readiness は DB 疎通の検査そのものが目的のため、業務トランザクションを
        // 開かずにプールから直接コネクションを取る
        route(HttpMethod.Get, "/health/ready", anonymous(), TxMode.NoTx,
              lift(_req -> SharedHealthRoutes.ready(ds))) ::

        // --- 認証（Shared） ---
        route(HttpMethod.Get, "/login", anonymous(), TxMode.ReadOnly,
              lift(SharedAuthRoutes.loginPage)) ::
        route(HttpMethod.Post, "/login", anonymous(), TxMode.Write,
              lift(SharedAuthRoutes.loginSubmit)) ::
        route(HttpMethod.Post, "/logout", allRoles(), TxMode.Write,
              lift(SharedAuthRoutes.logout)) ::
        route(HttpMethod.Get, "/", allRoles(), TxMode.ReadOnly,
              lift(SharedAuthRoutes.dashboard)) ::

        // --- Shipper Context（荷主・営業担当者） ---
        route(HttpMethod.Get, "/shippers", salesOnly(), TxMode.ReadOnly,
              lift(ShipperRoutes.index)) ::
        route(HttpMethod.Get, "/shippers/new", salesOnly(), TxMode.ReadOnly,
              lift(ShipperRoutes.newPage)) ::
        // 法人固有フィールドの部分更新（htmx）。DB を使わないが認可は要るため ReadOnly
        route(HttpMethod.Get, "/shippers/new/corporate-fields", salesOnly(), TxMode.ReadOnly,
              lift(ShipperRoutes.corporateFields)) ::
        route(HttpMethod.Post, "/shippers", salesOnly(), TxMode.Write,
              lift(ShipperRoutes.create)) ::

        // --- Booking Context（貨物予約） ---
        // 荷主も開ける。可視範囲は `listVisible` / `findVisible` が絞る
        route(HttpMethod.Get, "/bookings", salesOrShipper(), TxMode.ReadOnly,
              lift(BookingRoutes.index)) ::
        route(HttpMethod.Get, "/bookings/new", salesOnly(), TxMode.ReadOnly,
              lift(BookingRoutes.newPage)) ::
        // 貨物種別に応じた追加入力欄（htmx）。US05 の拡張点
        route(HttpMethod.Get, "/bookings/new/cargo-type-fields", salesOnly(), TxMode.ReadOnly,
              lift(BookingRoutes.cargoTypeFields)) ::
        route(HttpMethod.Post, "/bookings", salesOnly(), TxMode.Write,
              lift(BookingRoutes.create)) ::
        // 予約詳細（US06）。`/bookings/new` より後に置いても、パターンの
        // 一致は静的セグメントを優先するため取り違えない（`RouterTest` が固定）
        route(HttpMethod.Get, "/bookings/{bookingId}", salesOrShipper(), TxMode.ReadOnly,
              lift(BookingRoutes.show)) ::
        route(HttpMethod.Post, "/bookings/{bookingId}/assign-to-routing", salesOnly(), TxMode.Write,
              lift(BookingRoutes.assignToRouting)) ::

        // --- Routing Context（航海スケジュール。IT6） ---
        // 検索は営業も使う（見積の裏付けに要る）。登録・更新は経路設計者のみ
        route(HttpMethod.Get, "/voyages", salesOrRouter(), TxMode.ReadOnly,
              lift(VoyageRoutes.index)) ::
        route(HttpMethod.Get, "/voyages/new", routerOnly(), TxMode.ReadOnly,
              lift(VoyageRoutes.newPage)) ::
        // 区間の追加（htmx）。DB を見ないが `LocationCatalog` を使うため ReadOnly
        route(HttpMethod.Get, "/voyages/leg-fields", routerOnly(), TxMode.ReadOnly,
              lift(VoyageRoutes.legFields)) ::
        route(HttpMethod.Post, "/voyages", routerOnly(), TxMode.Write,
              lift(VoyageRoutes.create)) ::
        route(HttpMethod.Get, "/voyages/{voyageNumber}/edit", routerOnly(), TxMode.ReadOnly,
              lift(VoyageRoutes.editPage)) ::
        // 差分の確認は保存しない。`ReadOnly` で足りる
        route(HttpMethod.Post, "/voyages/{voyageNumber}", routerOnly(), TxMode.ReadOnly,
              lift(VoyageRoutes.confirm)) ::
        route(HttpMethod.Post, "/voyages/{voyageNumber}/apply", routerOnly(), TxMode.Write,
              lift(VoyageRoutes.apply)) ::

        // --- Tracking Context（公開追跡・認証不要） ---
        route(HttpMethod.Get, "/public/tracking", anonymous(), TxMode.ReadOnly,
              lift(TrackingPublicRoutes.search)) ::
        route(HttpMethod.Get, "/public/tracking/{trackingNumber}", anonymous(), TxMode.ReadOnly,
              lift(TrackingPublicRoutes.show)) ::
        Nil

    ///
    /// ルート定義の組み立て。
    ///
    /// ハンドラは `lift` 済みのものを受け取る。`route` の側で `lift` を適用しないのは、
    /// **効果変数が具体化されないと `ef + All = All` を Flix が確かめられない**ためである
    /// （`route` を効果多相にすると「未知の `ef` を足した集合」と「`All`」が単一化できない）。
    /// 呼び出し側で `lift(...)` と書くことにより、各ルートで効果が具体化される。
    ///
    def route(m: HttpMethod, pattern: String, role: SharedHttp.RequiredRole, mode: TxMode,
              h: Request -> Response \ AppEff):
            Route[AppEff] =
        Route.Route(m, SharedHttp.PathPattern.PathPattern(pattern), role, mode, h)

    def anonymous(): SharedHttp.RequiredRole = SharedHttp.RequiredRole.Anonymous

    /// 荷主の管理は営業担当者の職掌（バックエンドアーキテクチャのロール表）
    def salesOnly(): SharedHttp.RequiredRole =
        SharedHttp.RequiredRole.RoleAnyOf(SharedSecurity.Role.Sales :: Nil)

    ///
    /// 予約の閲覧は荷主自身も行う（UI 設計の画面一覧）。
    ///
    /// **到達できることと、見えるデータの範囲は別**である。範囲は
    /// `BookingBookCargo.listVisible` / `findVisible` が絞る（観点表の軸 6）。
    ///
    def salesOrShipper(): SharedHttp.RequiredRole =
        SharedHttp.RequiredRole.RoleAnyOf(
            SharedSecurity.Role.Sales :: SharedSecurity.Role.Shipper :: Nil)

    /// 航海スケジュールの登録・更新は経路設計者の職掌（UI 設計のロール表）
    def routerOnly(): SharedHttp.RequiredRole =
        SharedHttp.RequiredRole.RoleAnyOf(SharedSecurity.Role.Router :: Nil)

    ///
    /// 航海の検索は営業も使う。
    ///
    /// US07 は経路設計者向けだが、営業も見積の裏付けに要る
    /// （US01 が「航海スケジュール情報をもとにルート概算候補」を求める）。
    ///
    def salesOrRouter(): SharedHttp.RequiredRole =
        SharedHttp.RequiredRole.RoleAnyOf(
            SharedSecurity.Role.Sales :: SharedSecurity.Role.Router :: Nil)

    def allRoles(): SharedHttp.RequiredRole =
        SharedHttp.RequiredRole.RoleAnyOf(SharedSecurity.allRoles())

    // ------------------------------------------------------------------
    // ディスパッチ
    // ------------------------------------------------------------------

    ///
    /// 1 リクエストを処理する。
    ///
    /// **ルートの解決をトランザクションの手前で行う**。トランザクションを開くかどうかは
    /// ルートの `TxMode` が決めるため、解決してからでないと判断できない。
    /// `/health/live` が DB を見ると DB 障害時に再起動ループを起こす（IT3 レビュー H4）。
    ///
    pub def handleRequest(ds: DataSource, req: Request): Response \ IO =
        match SharedHttpStatic.handle(SharedHttp.path(req)) {
            case Some(res) => res
            case None      => handleDynamic(ds, req)
        }

    ///
    /// 静的ファイル以外のリクエストを処理する。
    ///
    /// **どのルートで処理したかを応答に添える**（`X-Route`）。IT4 の間欠 403 と
    /// IT5 の間欠 404 は、いずれも「そのルートでは起こり得ない応答」だった。
    /// 再現しない事象に対して、次の発生時に
    /// **「別のルートが処理した」のか「そのルートが想定外の結果を返した」のか**を
    /// 切り分けられる材料がないことが、原因特定を止めていた。
    ///
    /// 有効化は `APP_TRACE_ROUTE=true`（または開発環境）。**開発判定に
    /// 相乗りさせない**。IT5 当初はそうしていたが、`APP_ENV` を設定しているのは
    /// 開発サーバと E2E だけで、間欠異常が実際に観測されたフル テストでは
    /// 無効だった。本番で経路の内部名を晒す理由はない。
    ///
    def handleDynamic(ds: DataSource, req: Request): Response \ IO =
        let resolution = resolve(appRoutes(ds), SharedHttp.method(req), SharedHttp.path(req));
        let (response, routeName) = match resolution {
            case Resolution.NotFound       => (SharedHttpServer.notFoundResponse(), "(not-found)")
            case Resolution.MethodMismatch => (SharedHttpServer.methodNotAllowedResponse(),
                                               "(method-mismatch)")
            case Resolution.Found(r, params) =>
                let res = match routeTxMode(r) {
                    case TxMode.NoTx     => runWithoutDb(r, req, params)
                    case TxMode.ReadOnly => runInTransaction(ds, false, r, req, params)
                    case TxMode.Write    => runInTransaction(ds, true, r, req, params)
                };
                (res, routeKey(r))
        };
        if (SharedAppEnv.isRouteTraceEnabled())
            withHeader(response, "X-Route", routeName)
        else
            response

    ///
    /// DB を使わないルートを実行する。
    ///
    /// 効果の型は表全体で揃っているため、使わないハンドラも供給する必要がある。
    /// **供給するのは「呼ばれたら設計の誤り」を表明するハンドラ**である。
    /// 既定値を返す実装にすると、`NoTx` と宣言したルートが実際には DB を要求していた
    /// ときに、**未認証として静かに通ってしまう**。
    ///
    /// `NoTx` のルートが `Anonymous` に限られることは `RouterLiftTest` で固定している。
    ///
    def runWithoutDb(r: Route[AppEff],
                     req: Request, params: Map[String, String]): Response \ IO =
        // `Clock` と `IdGen` は DB を要さないため、`NoTx` の経路でも本物を供給してよい
        withSystemClock(() ->
        withUuidIdGen(() ->
        run {
            run {
                run {
                    run {
                    run {
                        run {
                        run {
                            run {
                                run {
                                    runRoute(r, req, params, anonymousGuard())
                                } with handler LocationCatalog {
                                    def all(_k) = noDbViolation("LocationCatalog")
                                }
                            } with handler ShipperExistenceChecker {
                                def resolveShipperId(_c, _k) = noDbViolation("ShipperExistenceChecker")
                                def describeShipper(_i, _k) = noDbViolation("ShipperExistenceChecker")
                            }
                        } with handler VoyageRepo {
                            def save(_v, _k)                  = noDbViolation("VoyageRepo")
                            def findByVoyageNumber(_n, _k)    = noDbViolation("VoyageRepo")
                            def findAll(_k)                   = noDbViolation("VoyageRepo")
                            def replace(_v, _k)               = noDbViolation("VoyageRepo")
                            def search(_o, _d, _f, _t, _c, _k) = noDbViolation("VoyageRepo")
                        }
                        } with handler CargoRepo {
                            def save(_c, _k)             = noDbViolation("CargoRepo")
                            def findByBookingId(_i, _k)  = noDbViolation("CargoRepo")
                            def findAll(_k)              = noDbViolation("CargoRepo")
                            def updateStatus(_c, _k)     = noDbViolation("CargoRepo")
                            def search(_s, _q, _k)       = noDbViolation("CargoRepo")
                        }
                    } with handler ShipperRepo {
                            def save(_s, _k)            = noDbViolation("ShipperRepo")
                            def findByEmail(_e, _k)     = noDbViolation("ShipperRepo")
                            def findByShipperId(_i, _k) = noDbViolation("ShipperRepo")
                            def findAll(_k)             = noDbViolation("ShipperRepo")
                            def search(_q, _k)          = noDbViolation("ShipperRepo")
                        }
                    } with handler ReadDb {
                        def findByTrackingNumber(_n, _k) = noDbViolation("ReadDb")
                    }
                } with handler Password {
                    def hash(_p, _k)       = noDbViolation("Password")
                    def verify(_p, _h, _k) = noDbViolation("Password")
                }
            } with handler UserRepo {
                def findByUsername(_n, _k) = noDbViolation("UserRepo")
                def recordFailure(_id, _k) = noDbViolation("UserRepo")
                def resetFailures(_id, _k) = noDbViolation("UserRepo")
            }
        } with handler Session {
            def current(_k)           = noDbViolation("Session")
            def login(_p, _k)         = noDbViolation("Session")
            def logout(_k)            = noDbViolation("Session")
            def csrfToken(_k)         = noDbViolation("Session")
            def terminationReason(_k) = noDbViolation("Session")
        }))

    ///
    /// `NoTx` と宣言したルートが DB を要する効果を使ったときに落とす。
    ///
    /// 既定値を返さないのは、**未認証として静かに通す**方がはるかに危険だからである。
    /// ここで落ちるのは実装の誤りであり、開発とテストの段階で必ず表面化する。
    ///
    def noDbViolation(effectName: String): a =
        bug!("TxMode.NoTx と宣言したルートが ${effectName} を要求しました。ルート定義の TxMode を見直してください")

    ///
    /// トランザクションの中でルートを実行する。
    ///
    /// **`withBcryptPassword` をトランザクションの外側に置く**。BCrypt はコスト 12 で
    /// 1 回あたり数百ミリ秒かかり、内側に置くと計算中も DB コネクションを掴んだままになる。
    /// ログインの同時実行がそのままプールの専有時間に効くため、枯渇の第一候補になる。
    ///
    def runInTransaction(ds: DataSource, isWrite: Bool,
                         r: Route[AppEff],
                         req: Request, params: Map[String, String]): Response \ IO =
        let cookieId = cookieValue(SharedHttp.header(req, "cookie"), sessionCookieName());
        // セッションの発行・破棄はハンドラの内側で起きるため、Cookie の指示を
        // ここで受け取り、レスポンスの組み立て時に反映する
        let cookieDirective = Ref.fresh(Static, None);
        let inTx = () ->
            withJdbcSession(cookieId, change -> Ref.put(Some(change), cookieDirective), () ->
                withJdbcUserRepo(() ->
                    withJdbcShipperRepo(() ->
                    withJdbcCargoRepo(() ->
                    withJdbcVoyageRepo(() ->
                    withJdbcShipperExistence(() ->
                    withJdbcLocationCatalog(() ->
                    withJdbcReadDb(() -> {
                        // 認可判定の入力はリクエスト開始時点で固定する。
                        // 同一リクエスト内でログインが成立しても guard は見ない（PRG のため問題ない）
                        let principal = Session.current();
                        let csrf = Session.csrfToken();
                        let g = guard(principal, csrf, Session.terminationReason());
                        runRoute(r, req, params, g)
                    }))))))));
        // `Clock` はトランザクションの外側に置く。内側に置く理由がなく、
        // 外側なら `TxMode.NoTx` の経路でも同じハンドラを使える
        let response = withUuidIdGen(() ->
            withSystemClock(() ->
            withBcryptPassword(() ->
                if (isWrite) transactional(ds, inTx) else readOnly(ds, inTx))));
        applyCookie(response, Ref.get(cookieDirective))

    /// 認可を要しない経路の関門（`NoTx` のルートはすべて `Anonymous`）
    def anonymousGuard(): SharedHttp.RequiredRole -> Request -> Option[Response] =
        _ -> _ -> None

    ///
    /// 認可と CSRF の関門。
    ///
    /// ハンドラを呼ぶ**前**に判定する。順序が逆だと、認可で弾くはずのリクエストで
    /// 副作用が起きる。判定そのものは純粋関数（`decide` / `isValidCsrf` / `requiresCsrf`）に委ねる。
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

    ///
    /// サーバを起動する。
    ///
    /// 起動時にルーティング表の二重定義を検査する。**同じメソッド・同じパターンを
    /// 2 度書くと先勝ちで後の認可要件が静かに消える**ため、起動を止めて気付かせる。
    ///
    pub def startServer(ds: DataSource, portNumber: Int32): RunningServer \ IO =
        match duplicateKeys(appRoutes(ds)) {
            case Nil  => start(portNumber, req -> handleRequest(ds, req))
            case dups => bug!("ルーティング表に二重定義があります: ${dups}")
        }
}
