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

    ///
    /// 表の構造だけを見るため、マイグレーションもシードも要らない。
    ///
    /// 効果集合は `AppComposition.AppEff` を参照する。**テスト側で列挙し直さない**。
    /// 列挙を写すと、BC を足すたびに本体とテストの 2 箇所を直すことになる。
    ///
    def withRoutes(f: List[Route[AppComposition.AppEff]] -> a \ ef): a \ ef + IO =
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
        List.sort(authorizationTable())

    ///
    /// 可否表の内容（並び順は問わない）。
    ///
    /// **`:: Nil |> List.sort` と書かない**。`|>` は `::` より結合が弱く、
    /// `Nil` だけがソートされる。IT4 で実際に踏み、行を足した瞬間に落ちた。
    ///
    def authorizationTable(): List[(String, String)] =
        ("GET /", "全ロール") ::
        // --- Estimation Context（IT8・US01。**営業担当者のみ**） ---
        ("GET /estimates", "ROLE_SALES") ::
        ("GET /estimates/new", "ROLE_SALES") ::
        ("GET /estimates/{estimateId}", "ROLE_SALES") ::
        ("POST /estimates", "ROLE_SALES") ::
        ("GET /health/live", "Anonymous") ::
        ("GET /health/ready", "Anonymous") ::
        ("GET /login", "Anonymous") ::
        ("GET /public/tracking", "Anonymous") ::
        ("GET /public/tracking/{trackingNumber}", "Anonymous") ::
        // 経路設計者も閲覧する（IT7。経路割り当ての入口が予約詳細のため）
        ("GET /bookings", "ROLE_ROUTER,ROLE_SALES,ROLE_SHIPPER") ::
        ("GET /bookings/new", "ROLE_SALES") ::
        ("GET /bookings/new/cargo-type-fields", "ROLE_SALES") ::
        // 予約詳細は荷主も見る。可視範囲は `findVisible` が絞る（IT6）
        ("GET /bookings/{bookingId}", "ROLE_ROUTER,ROLE_SALES,ROLE_SHIPPER") ::
        // 経路割り当て（US08）。読み取りのみ。選択・確定は US09（IT8）
        ("GET /bookings/{bookingId}/route", "ROLE_ROUTER") ::
        // 経路の選択・確定と予約への紐付け（US09・US11。IT9）
        ("POST /bookings/{bookingId}/route", "ROLE_ROUTER") ::
        ("GET /shippers", "ROLE_SALES") ::
        ("GET /shippers/new", "ROLE_SALES") ::
        ("GET /shippers/new/corporate-fields", "ROLE_SALES") ::
        // 航海の検索は営業も使う（見積の裏付け）。登録・更新は経路設計者のみ
        ("GET /voyages", "ROLE_ROUTER,ROLE_SALES") ::
        ("GET /voyages/leg-fields", "ROLE_ROUTER") ::
        ("GET /voyages/new", "ROLE_ROUTER") ::
        ("GET /voyages/{voyageNumber}/edit", "ROLE_ROUTER") ::
        ("POST /bookings", "ROLE_SALES") ::
        // 経路設計への引き渡しは営業担当者の職掌（US06）
        ("POST /bookings/{bookingId}/assign-to-routing", "ROLE_SALES") ::
        // 予約の確定・差し戻し・キャンセル（US13。IT9）
        ("GET /bookings/{bookingId}/confirm", "ROLE_SALES") ::
        ("POST /bookings/{bookingId}/confirm", "ROLE_SALES") ::
        ("POST /login", "Anonymous") ::
        ("POST /logout", "全ロール") ::
        ("POST /shippers", "ROLE_SALES") ::
        ("POST /voyages", "ROLE_ROUTER") ::
        ("POST /voyages/{voyageNumber}", "ROLE_ROUTER") ::
        ("POST /voyages/{voyageNumber}/apply", "ROLE_ROUTER") ::
        Nil

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
