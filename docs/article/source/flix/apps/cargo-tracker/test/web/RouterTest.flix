///
/// ルーティング表の解決とその不変条件のテスト。
///
/// IT3 では効果集合ごとに表を 4 分割していた。分割された表は
/// **同一パスが 2 表にあると先勝ちで片方の認可が静かに消える**うえ、
/// パス一致・メソッド不一致で 405 ではなく 404 を返していた。
/// 単一表へ統合したうえで、その 2 点をここで固定する（IT3 レビュー H16）。
///
mod RouterTest {
    use SharedHttp.{HttpMethod, PathPattern, Request, RequiredRole, Response, plain}
    use SharedHttpRouter.{Route, Resolution, TxMode, resolve, duplicateKeys, routeTxMode, routeRole}
    use SharedSecurity.Role

    /// 効果を要求しないハンドラ（表の解決だけを見るため中身は問わない）
    def okResponse(_req: Request): Response = plain(200, "ok")

    def route(m: HttpMethod, p: String, role: RequiredRole, mode: TxMode): Route[{}] =
        Route.Route(m, PathPattern.PathPattern(p), role, mode, okResponse)

    def sample(): List[Route[{}]] =
        route(HttpMethod.Get, "/health/live", RequiredRole.Anonymous, TxMode.NoTx) ::
        route(HttpMethod.Get, "/bookings", RequiredRole.RoleAnyOf(Role.Sales :: Nil), TxMode.ReadOnly) ::
        route(HttpMethod.Post, "/bookings", RequiredRole.RoleAnyOf(Role.Sales :: Nil), TxMode.Write) ::
        route(HttpMethod.Get, "/bookings/{bookingId}", RequiredRole.RoleAnyOf(Role.Sales :: Nil), TxMode.ReadOnly) ::
        Nil

    // ---- 解決 ----

    /// 完全一致のパスを解決できること
    @Test
    def testResolvesExactPath(): Unit \ Assert =
        match resolve(sample(), HttpMethod.Get, "/bookings") {
            case Resolution.Found(r, params) =>
                Assert.assertTrue(Map.isEmpty(params));
                Assert.assertTrue(routeTxMode(r) == TxMode.ReadOnly)
            case _ => Assert.fail("/bookings が解決できなかった")
        }

    /// パスパラメータを取り出せること
    @Test
    def testExtractsPathParameters(): Unit \ Assert =
        match resolve(sample(), HttpMethod.Get, "/bookings/BK-0001") {
            case Resolution.Found(_, params) =>
                Assert.assertEq(expected = Some("BK-0001"), Map.get("bookingId", params))
            case _ => Assert.fail("パスパラメータ付きのルートが解決できなかった")
        }

    ///
    /// 同じパスでもメソッドが違えば別のルートとして解決すること。
    ///
    /// `GET /bookings`（一覧）と `POST /bookings`（登録）は書き込みの有無が違う。
    /// 取り違えると読み取り専用トランザクションで登録しようとして落ちる。
    ///
    @Test
    def testDistinguishesMethodsOnSamePath(): Unit \ Assert =
        match (resolve(sample(), HttpMethod.Get, "/bookings"),
               resolve(sample(), HttpMethod.Post, "/bookings")) {
            case (Resolution.Found(g, _), Resolution.Found(p, _)) =>
                Assert.assertTrue(routeTxMode(g) == TxMode.ReadOnly);
                Assert.assertTrue(routeTxMode(p) == TxMode.Write)
            case _ => Assert.fail("メソッド違いのルートを解決できなかった")
        }

    ///
    /// パスは一致するがメソッドが一致しない場合に 405 の判定を返すこと。
    ///
    /// IT3 では 404 を返していた。「存在しない」と「その操作は許されない」は
    /// 利用者にとっても API の呼び出し側にとっても別の意味である。
    ///
    @Test
    def testReturnsMethodMismatchNotNotFound(): Unit \ Assert =
        match resolve(sample(), HttpMethod.Delete, "/bookings") {
            case Resolution.MethodMismatch => ()
            case Resolution.NotFound       => Assert.fail("メソッド不一致が 404 と判定された")
            case Resolution.Found(_, _)    => Assert.fail("DELETE /bookings が解決されてしまった")
        }

    /// パスパラメータを含むルートでもメソッド不一致を判定できること
    @Test
    def testMethodMismatchOnParameterizedPath(): Unit \ Assert =
        match resolve(sample(), HttpMethod.Post, "/bookings/BK-0001") {
            case Resolution.MethodMismatch => ()
            case _ => Assert.fail("パスパラメータ付きのメソッド不一致を判定できなかった")
        }

    /// 存在しないパスは 404 の判定を返すこと
    @Test
    def testReturnsNotFoundForUnknownPath(): Unit \ Assert =
        match resolve(sample(), HttpMethod.Get, "/unknown") {
            case Resolution.NotFound => ()
            case _ => Assert.fail("未知のパスが 404 と判定されなかった")
        }

    /// セグメント数が違うパスは一致しないこと
    @Test
    def testDoesNotMatchDifferentSegmentCount(): Unit \ Assert =
        match resolve(sample(), HttpMethod.Get, "/bookings/BK-0001/legs") {
            case Resolution.NotFound => ()
            case _ => Assert.fail("セグメント数の違うパスが一致した")
        }

    // ---- 二重定義の検出 ----

    ///
    /// 同一メソッド・同一パターンの二重定義を検出すること。
    ///
    /// **これが本テストの主眼**である。表が分かれていた IT3 では、同じパスが
    /// 2 つの表にあっても先勝ちで通り、後ろの表の認可要件が静かに消えていた。
    /// 単一表にしただけでは同じ事故が表の中で起きるため、検出を機械化する。
    ///
    @Test
    def testDetectsDuplicateRouteDefinitions(): Unit \ Assert =
        let dup = route(HttpMethod.Get, "/bookings", RequiredRole.Anonymous, TxMode.ReadOnly) :: sample();
        Assert.assertEq(expected = "GET /bookings" :: Nil, duplicateKeys(dup))

    /// 重複がなければ空を返すこと
    @Test
    def testNoDuplicatesInWellFormedTable(): Unit \ Assert =
        Assert.assertTrue(List.isEmpty(duplicateKeys(sample())))

    ///
    /// 同じパスでもメソッドが違えば重複ではないこと。
    ///
    /// これを重複と誤検出すると、`GET` / `POST` の対で作る通常の CRUD が書けなくなる。
    ///
    @Test
    def testSamePathDifferentMethodIsNotDuplicate(): Unit \ Assert =
        Assert.assertTrue(List.isEmpty(duplicateKeys(
            route(HttpMethod.Get, "/x", RequiredRole.Anonymous, TxMode.NoTx) ::
            route(HttpMethod.Post, "/x", RequiredRole.Anonymous, TxMode.Write) :: Nil)))

    /// 重複を 1 件だけ報告すること（同じキーが 3 つあっても列挙は 1 回）
    @Test
    def testReportsEachDuplicateKeyOnce(): Unit \ Assert =
        let triple =
            route(HttpMethod.Get, "/x", RequiredRole.Anonymous, TxMode.NoTx) ::
            route(HttpMethod.Get, "/x", RequiredRole.Anonymous, TxMode.NoTx) ::
            route(HttpMethod.Get, "/x", RequiredRole.Anonymous, TxMode.NoTx) :: Nil;
        Assert.assertEq(expected = "GET /x" :: Nil, duplicateKeys(triple))

    // ---- TxMode の不変条件 ----

    ///
    /// DB を使わないルートは未認証で通れるものに限ること。
    ///
    /// 認可の判定にはセッションの参照（= DB アクセス）が要る。`NoTx` なのに
    /// ロールを要求するルートは、**認可を評価できないまま通す**か、
    /// 防ごうとした DB アクセスを起こすかのどちらかになる。
    ///
    @Test
    def testNoTxRoutesAreAnonymous(): Unit \ Assert =
        let violations = sample()
            |> List.filter(r -> routeTxMode(r) == TxMode.NoTx)
            |> List.filter(r -> routeRole(r) != RequiredRole.Anonymous);
        Assert.assertTrue(List.isEmpty(violations))
}
