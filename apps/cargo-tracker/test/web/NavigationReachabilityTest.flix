///
/// **ナビゲーションの各項目が、実際に到達できる画面を指していること**。
///
/// IT4 と IT6 で 2 回、同じ形の欠陥が出た。
///
/// | IT | 欠陥 |
/// | :--: | :--- |
/// | IT4 | ロール別の作業入口が無く、そのロールが機能へ到達できない |
/// | IT6 | `/voyages` は営業へ開いているのに navbar が経路設計者のみ |
/// | IT6 | navbar の未実装リンク 5 件が行き止まり（押すと 404） |
///
/// いずれも「画面単体は正しいが、そこへ至る導線が切れている」ものであり、
/// **画面のテストでは原理的に検出できない**。導線とルートの対応そのものを
/// 検査対象にしないと、次に画面を足したときにまた起きる。
///
/// **`navItems` が唯一の定義**である（navbar とダッシュボードの作業入口は
/// どちらもここから作られる）。本モジュールはその定義と
/// `AppComposition.appRoutes` を突き合わせる。
///
/// **未実装の項目を隠さない**のは、荷受人・荷役担当者・経理が
/// 「担当する業務がありません」とだけ表示される状態を作らないためである。
/// 権限は正しく、問い合わせても解決しない。まだ作っていないことが
/// 分かる表示にし、かつ**押せなくする**（IT6 レビュー M3）。
///
mod NavigationReachabilityTest {
    use AppComposition.appRoutes
    use SharedDbPool.{create, h2Config, close}
    use SharedHtml.render
    use SharedHtmlLayout.{navItems, navHref, navLabel, navRoles, navStatus, nav}
    use SharedHtmlLayout.NavStatus
    use SharedHttp.HttpMethod
    use SharedHttpAuth.{Decision, decide}
    use SharedHttpRouter.{Route, routeMethod, routeRole, routePattern, patternText}
    use SharedSecurity.{Role, principal, allRoles, roleLabel}

    def withRoutes(f: List[Route[AppComposition.AppEff]] -> a \ ef): a \ ef + IO =
        let ds = create(h2Config("nav_reachability_check"));
        let result = f(appRoutes(ds));
        close(ds);
        result

    /// 表に載っている `GET` のパスパターン
    def getPaths(routes: List[Route[AppComposition.AppEff]]): List[String] =
        routes
            |> List.filter(r -> routeMethod(r) == HttpMethod.Get)
            |> List.map(r -> patternText(routePattern(r)))

    def implemented(): List[SharedHtmlLayout.NavItem] =
        navItems() |> List.filter(item -> navStatus(item) == NavStatus.Implemented)

    def planned(): List[SharedHtmlLayout.NavItem] =
        navItems() |> List.filter(item -> navStatus(item) == NavStatus.Planned)

    ///
    /// **実装済みと宣言した項目は、すべてルートを持つこと**。
    ///
    /// この式が偽になる入力: `navItems` に `("見積", "/estimates", ..., Implemented)`
    /// を書きながら `appRoutes` に `/estimates` を登録しない場合。
    /// IT6 時点の `navItems` は実際にこの状態であり、5 件が行き止まりだった。
    ///
    @Test
    def testImplementedNavItemsHaveRoutes(): Unit \ Assert + IO =
        withRoutes(routes -> {
            let paths = getPaths(routes);
            let dangling = implemented()
                |> List.map(navHref)
                |> List.filter(href -> not List.memberOf(href, paths));
            Assert.assertEq(expected = Nil, dangling)
        })

    ///
    /// **準備中と宣言した項目は、ルートを持たないこと**。
    ///
    /// 逆向きの検査である。画面を実装したのに `Planned` のままにすると、
    /// 利用者は「準備中」の表示を見続け、**動いている機能へ到達できない**。
    /// IT6 の `/voyages` はこれと同型（ルートは営業へ開いていたが導線が無い）だった。
    ///
    /// この式が偽になる入力: `/estimates` を実装してルートを足したのに
    /// `navItems` の状態を `Implemented` へ変え忘れた場合。
    ///
    @Test
    def testPlannedNavItemsHaveNoRoutes(): Unit \ Assert + IO =
        withRoutes(routes -> {
            let paths = getPaths(routes);
            let stale = planned()
                |> List.map(navHref)
                |> List.filter(href -> List.memberOf(href, paths));
            Assert.assertEq(expected = Nil, stale)
        })

    ///
    /// **項目を見られるロールは、そのルートを通れること**。
    ///
    /// IT6 レビュー H8（`/voyages` は営業に開いているのに navbar が経路設計者のみ）
    /// の**逆向き**——「navbar には出るが押すと 403」——を捕まえる。
    /// どちらも「表示」と「認可」が別の場所で定義されていることに起因する。
    ///
    /// この式が偽になる入力: `navItems` の `/shippers` に `Role.Shipper` を足すと、
    /// ルートは `salesOnly()` のままなので荷主は押した瞬間 403 になる。
    ///
    @Test
    def testNavItemRolesCanPassTheRoute(): Unit \ Assert + IO =
        withRoutes(routes -> {
            let requiredOf = href ->
                routes
                    |> List.find(r -> routeMethod(r) == HttpMethod.Get and
                                      patternText(routePattern(r)) == href)
                    |> Option.map(routeRole);
            let violations = implemented()
                |> List.flatMap(item ->
                    match requiredOf(navHref(item)) {
                        // ルートが無い場合は testImplementedNavItemsHaveRoutes の担当
                        case None       => Nil
                        case Some(need) =>
                            navRoles(item)
                                |> List.filter(r ->
                                    decide(need, Some(principal(1i64, "利用者", r))) != Decision.Allow)
                                |> List.map(r ->
                                    "${navHref(item)} は ${roleLabel(r)} に表示されるがルートが通さない")
                    });
            Assert.assertEq(expected = Nil, violations)
        })

    ///
    /// **準備中の項目にリンクを張らないこと**。
    ///
    /// IT6 の欠陥は「見えること」ではなく「押せて 404 になること」だった。
    /// 表示は残し、`href` を外す。
    ///
    /// この式が偽になる入力: `navItem` が `Planned` にも `href` を付けた場合。
    ///
    @Test
    def testPlannedItemsAreNotLinks(): Unit \ Assert =
        let html = render(nav(Some(principal(1i64, "管理者", Role.Admin)), "/", None));
        let linked = planned()
            |> List.map(navHref)
            |> List.filter(href -> String.contains(substr = "href=\"${href}\"", html));
        Assert.assertEq(expected = Nil, linked)

    ///
    /// **準備中と分かる表示にすること**。
    ///
    /// 押せないだけで理由が分からないと、利用者は権限の問題だと考えて
    /// 管理者へ問い合わせる。問い合わせても解決しない。
    ///
    /// この式が偽になる入力: `href` を外しただけで文言を変えなかった場合。
    ///
    @Test
    def testPlannedItemsAreLabelledAsPreparing(): Unit \ Assert =
        let html = render(nav(Some(principal(1i64, "管理者", Role.Admin)), "/", None));
        Assert.assertTrue(String.contains(substr = "準備中", html))

    ///
    /// **押せる入口を 1 つも持たないロールの一覧が、想定どおりであること**。
    ///
    /// ダッシュボード以外に到達先がないロールは、ログインしても何もできない。
    /// IT6 時点では荷受人・荷役担当者・追跡管理者・経理の 4 つがこの状態だった
    /// （項目はあるが全て 404）。
    ///
    /// **「0 件であること」とは書けない**。精算は将来リリースであり、
    /// 経理担当者の入口は v1.0.0 の範囲では作られない。
    /// 書けないものを書くと、通すために対象を狭めるか、テストを消すことになる。
    ///
    /// そこで**現在の状態を明示的に固定する**。画面が実装されたらこの一覧は
    /// 短くなり、テストが落ちる。落ちたら一覧から消す。逆に**新しいロールが
    /// 入口を失ったときも落ちる**。それが本テストの目的である。
    ///
    /// この式が偽になる入力: 荷受人の `/public/tracking` を `Planned` に戻すと
    /// 一覧に荷受人が加わり落ちる。
    ///
    /// # 失敗の方向を分ける（IT8・TS14。IT7 レビュー M18）
    ///
    /// IT7 は 1 本の `assertEq` で固定していた。**「伸びた」と「縮んだ」が
    /// 同じ落ち方をする**ため、落ちたときに欠陥なのか前進なのかを
    /// メッセージから判断できない。前進のつもりで期待値を書き換える手が
    /// 習慣になると、**欠陥で伸びたときも同じ手で潰してしまう**。
    ///
    /// 方向ごとに分けたのは、**片方は絶対に許されない**からである。
    /// 伸びた（`testNoNewRoleLosesItsEntry`）＝そのロールがログインしても
    /// 何もできない状態を新たに作った、という欠陥である。
    /// 縮んだ（`testStrandedRolesShrinkOnlyAsPlanned`）＝画面を実装した前進であり、
    /// 一覧から消すのが正しい対応である。
    ///
    def strandedRoles(): List[String] =
        let usable = r ->
            implemented()
                |> List.filter(item -> navHref(item) != "/")
                |> List.exists(item -> SharedHtmlLayout.visibleTo(r, navRoles(item)));
        // 並びは `allRoles()` の順序をそのまま保つ（`List.filter` は順序を変えない）
        allRoles() |> List.filter(r -> not usable(r)) |> List.map(roleLabel)

    ///
    /// 入口を持たないロールとして**想定済み**のもの。
    ///
    /// **荷役作業員は IT10（US15・荷役管理）で解消した**。IT7 から 3 イテレーション
    /// 「押せる入口を 1 つも持たないロール」だった。残るのは経理担当者だけで、
    /// 精算は将来リリース（ADR-0007 でスコープ外）である。
    ///
    def knownStranded(): List[String] = "経理担当者" :: Nil

    ///
    /// **想定外のロールが入口を失っていないこと**（欠陥の方向）。
    ///
    /// ここが落ちたら**前進ではない**。期待値を書き換えて通してはならない。
    /// そのロールに押せる入口を用意するか、`Planned` の項目を実装する。
    ///
    @Test
    def testNoNewRoleLosesItsEntry(): Unit \ Assert =
        let unexpected = strandedRoles() |> List.filter(r -> not List.memberOf(r, knownStranded()));
        Assert.assertEq(expected = Nil, unexpected)

    ///
    /// **解消済みのロールが一覧に残っていないこと**（前進の方向）。
    ///
    /// ここが落ちたら**前進である**。画面が実装され、そのロールは入口を得た。
    /// `knownStranded()` から消すのが正しい対応である。
    ///
    @Test
    def testStrandedRolesShrinkOnlyAsPlanned(): Unit \ Assert =
        let stale = knownStranded() |> List.filter(r -> not List.memberOf(r, strandedRoles()));
        Assert.assertEq(expected = Nil, stale)

    ///
    /// **ダッシュボードの作業入口も準備中を押せないこと**。
    ///
    /// navbar とダッシュボードは同じ `navItems` から作るが、描画は別関数である。
    /// 片方だけ直すと、もう片方に行き止まりが残る。
    ///
    /// この式が偽になる入力: `entryCard` が `Planned` にも `href` を付けた場合。
    ///
    @Test
    def testDashboardPreparingEntriesAreNotLinks(): Unit \ Assert =
        let html = render(SharedAuthPages.dashboard(principal(1i64, "経理", Role.Accountant)));
        Assert.assertTrue(not String.contains(substr = "href=\"/billing/invoices\"", html));
        Assert.assertTrue(String.contains(substr = "準備中", html))
}
