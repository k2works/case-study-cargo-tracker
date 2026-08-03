///
/// **実際のルーティング表**に対する不変条件のテスト。
///
/// `RouterTest` は解決アルゴリズムを架空の表で検証する。本モジュールが見るのは
/// `AppComposition.appRoutes` そのものであり、**ルートを 1 本追加した瞬間に
/// 壊れうる約束**を押さえる。IT3 では「ルート定義の `RequiredRole` と
/// 設計の可否表を照合するテストがない」ことが指摘されていた（レビュー M17）。
///
mod AppRoutesTest {
    use AppComposition.appRoutes
    use SharedDbPool.{create, h2Config, close}
    use SharedDbPool.DataSource
    use SharedHttp.RequiredRole
    use SharedHttpRouter.{Route, TxMode, routeKey, routeRole, routeTxMode, duplicateKeys}
    use SharedSecurity.{Role, allRoles}

    /// 表の効果集合（`appRoutes` の戻り値と一致させる）
    type alias AppEff = SharedSession.Session + SharedUserRepo.UserRepo +
                        SharedPassword.Password + TrackingReadDb.ReadDb +
                        ShipperRepo.ShipperRepo + SharedClock.Clock +
                        SharedIdGen.IdGen + IO

    /// 表の構造だけを見るため、マイグレーションもシードも要らない
    def withRoutes(f: List[Route[AppEff]] -> a \ ef): a \ ef + IO =
        let ds = create(h2Config("app_routes_check"));
        let result = f(appRoutes(ds));
        close(ds);
        result

    ///
    /// 表に二重定義がないこと。
    ///
    /// 起動時にも検査しているが、**起動しないと分からないのでは遅い**。
    /// ルートを追加した本人が、その場でテストとして受け取れる形にしておく。
    ///
    @Test
    def testNoDuplicateRoutes(): Unit \ Assert + IO =
        withRoutes(routes -> Assert.assertEq(expected = Nil, duplicateKeys(routes)))

    ///
    /// `NoTx` のルートは未認証で通れるものに限ること。
    ///
    /// 認可の判定にはセッションの参照（= DB アクセス）が要る。`NoTx` なのに
    /// ロールを要求するルートを書くと、合成ルートの `runWithoutDb` が
    /// `Session` を要求されて落ちる。**その落下を実行時ではなくテストで受け取る**。
    ///
    @Test
    def testNoTxRoutesAreAnonymous(): Unit \ Assert + IO =
        withRoutes(routes -> {
            let violations = routes
                |> List.filter(r -> routeTxMode(r) == TxMode.NoTx)
                |> List.filter(r -> routeRole(r) != RequiredRole.Anonymous)
                |> List.map(routeKey);
            Assert.assertEq(expected = Nil, violations)
        })

    ///
    /// 状態を変更しないルートに書き込みトランザクションを与えていないこと。
    ///
    /// `GET` は状態を変更しない前提で CSRF 検証を免除している。その `GET` が
    /// 書き込みトランザクションを開いていたら、**免除の前提そのものが崩れている**。
    ///
    @Test
    def testGetRoutesDoNotWrite(): Unit \ Assert + IO =
        withRoutes(routes -> {
            let violations = routes
                |> List.filter(r -> SharedHttpRouter.routeMethod(r) == SharedHttp.HttpMethod.Get)
                |> List.filter(r -> routeTxMode(r) == TxMode.Write)
                |> List.map(routeKey);
            Assert.assertEq(expected = Nil, violations)
        })

    ///
    /// ルート定義の認可要件が設計の可否表と一致すること（IT3 レビュー M17）。
    ///
    /// [バックエンドアーキテクチャ](../../../../docs/design/architecture_backend.md) の
    /// 「認可の可否表」が設計側の正典であり、**ルーティング表が実装側の正典**である。
    /// 両者がずれたら、どちらかが嘘をついている。`decide` の網羅テスト
    /// （`AuthorizationTest`）は判定関数を見るだけで、**どのルートに何を宣言したか**は見ない。
    ///
    @Test
    def testRouteRolesMatchDesignTable(): Unit \ Assert + IO =
        withRoutes(routes -> {
            let actual = routes |> List.map(r -> (routeKey(r), roleLabel(routeRole(r)))) |> List.sort;
            Assert.assertEq(expected = expectedAuthorizationTable(), actual)
        })

    ///
    /// 設計の可否表（IT4 時点）。
    ///
    /// **ルートを追加したら本表と設計ドキュメントを同一コミットで更新する**。
    /// 更新を忘れるとこのテストが落ちる。それが狙いである。
    ///
    def expectedAuthorizationTable(): List[(String, String)] =
        ("GET /", "全ロール") ::
        ("GET /health/live", "Anonymous") ::
        ("GET /health/ready", "Anonymous") ::
        ("GET /login", "Anonymous") ::
        ("GET /public/tracking", "Anonymous") ::
        ("GET /public/tracking/{trackingNumber}", "Anonymous") ::
        ("GET /shippers", "ROLE_SALES") ::
        ("GET /shippers/new", "ROLE_SALES") ::
        ("GET /shippers/new/corporate-fields", "ROLE_SALES") ::
        ("POST /login", "Anonymous") ::
        ("POST /logout", "全ロール") ::
        ("POST /shippers", "ROLE_SALES") ::
        Nil |> List.sort

    /// 認可要件の表示名。可否表の記法に合わせる
    def roleLabel(required: RequiredRole): String = match required {
        case RequiredRole.Anonymous       => "Anonymous"
        case RequiredRole.RoleRequired(r) => SharedSecurity.roleToPersisted(r)
        case RequiredRole.RoleAnyOf(rs)   =>
            if (List.length(rs) == List.length(allRoles()))
                "全ロール"
            else
                rs |> List.map(SharedSecurity.roleToPersisted) |> List.sort |> List.join(",")
    }
}
