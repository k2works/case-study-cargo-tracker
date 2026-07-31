mod LayoutTest {
    use SharedHtml.{text, element, class_, render}
    use SharedHtmlLayout.{page, nav, footer, renderPage}
    use SharedSecurity.Role
    use SharedSecurity.{principal, allRoles, roleLabel}

    def body(): SharedHtml.Html =
        element("p", class_("lead") :: Nil, text("本文") :: Nil)

    /// ページ枠がタイトル・本文・フッターを含むこと
    @Test
    def testPageRendersTitleBodyAndFooter(): Unit \ Assert =
        let html = renderPage("貨物追跡", None, "", None, body());
        Assert.assertTrue(String.contains(substr = "<title>貨物追跡 | CargoTracker</title>", html));
        Assert.assertTrue(String.contains(substr = "本文", html));
        Assert.assertTrue(String.contains(substr = "CargoTracker System", html))

    /// DOCTYPE と lang 属性を持つこと（アクセシビリティ・互換性の前提）
    @Test
    def testPageIsWellFormedDocument(): Unit \ Assert =
        let html = renderPage("貨物追跡", None, "", None, body());
        Assert.assertTrue(String.startsWith(prefix = "<!DOCTYPE html><html lang=\"ja\">", html))

    /// 未認証ではナビゲーションを表示しないこと（公開追跡ページの要件）
    @Test
    def testNavIsHiddenForAnonymous(): Unit \ Assert =
        Assert.assertEq(expected = "", render(nav(None, "", None)))

    /// ロールに応じてメニュー項目を出し分けること
    @Test
    def testNavShowsOnlyItemsForRole(): Unit \ Assert =
        let html = render(nav(Some(principal(1i64, "山田", Role.Accountant)), "/billing/invoices", None));
        Assert.assertTrue(String.contains(substr = "請求管理", html));
        Assert.assertTrue(not String.contains(substr = "荷役管理", html))

    /// 全ロール共通の項目（ダッシュボード・ログアウト）が常に出ること
    @Test
    def testNavAlwaysShowsCommonItems(): Unit \ Assert =
        let html = render(nav(Some(principal(1i64, "山田", Role.Handler)), "/handling", None));
        Assert.assertTrue(String.contains(substr = "ダッシュボード", html));
        Assert.assertTrue(String.contains(substr = "ログアウト", html))

    /// 現在位置のメニューに active を付けること
    @Test
    def testNavMarksActiveItem(): Unit \ Assert =
        let html = render(nav(Some(principal(1i64, "山田", Role.Handler)), "/handling", None));
        Assert.assertTrue(String.contains(substr = "nav-link active", html))

    /// ナビゲーションに ARIA 属性が付くこと（ui_design.md のアクセシビリティ要件）
    @Test
    def testNavHasAriaAttributes(): Unit \ Assert =
        let html = render(nav(Some(principal(1i64, "山田", Role.Admin)), "/", None));
        Assert.assertTrue(String.contains(substr = "role=\"navigation\"", html));
        Assert.assertTrue(String.contains(substr = "aria-label=\"メインナビゲーション\"", html))

    /// 表示名がエスケープされること（ナビは全画面に出るため XSS の影響が最も大きい）
    @Test
    def testNavEscapesDisplayName(): Unit \ Assert =
        let html = render(nav(Some(principal(1i64, "<script>", Role.Admin)), "/", None));
        Assert.assertTrue(not String.contains(substr = "<script>", html))

    /// フッターに著作権表示があること
    @Test
    def testFooterRendersCopyright(): Unit \ Assert =
        Assert.assertTrue(String.contains(substr = "CargoTracker System", render(footer())))

    /// page はナビを本文より前に置くこと（Tab 順序の前提）
    @Test
    def testPagePlacesNavBeforeBody(): Unit \ Assert =
        let html = render(page("在庫", Some(principal(1i64, "山田", Role.Admin)), "/", None, body()));
        let navAt = String.indexOf(substr = "メインナビゲーション", html) |> Option.getWithDefault(-1);
        let bodyAt = String.indexOf(substr = "本文", html) |> Option.getWithDefault(-1);
        Assert.assertTrue(navAt >= 0 and bodyAt >= 0 and navAt < bodyAt)

    ///
    /// すべてのロールが少なくとも 1 つの作業入口を持つこと。
    ///
    /// ナビは「ロールごとの導線欠落を一箇所で検査できるようにする」ために集約している。
    /// その検査がテストとして存在していなかった（IT2 レビュー M14）。
    /// ダッシュボードは全ロール共通のため、それ以外に固有の入口があることを見る。
    ///
    @Test
    def testEveryRoleHasAtLeastOneWorkEntry(): Unit \ Assert =
        let hasEntry = r -> {
            let html = render(nav(Some(principal(1i64, "利用者", r)), "/", None));
            let items = String.splitOn(substr = "nav-item", html) |> List.length;
            // ダッシュボード（全ロール共通）に加えて 1 つ以上あること
            items >= 3
        };
        Assert.assertTrue(allRoles() |> List.forAll(hasEntry))

    /// ロール外のメニューが出ないこと（表駆動）
    @Test
    def testNavHidesItemsOfOtherRoles(): Unit \ Assert =
        let cases =
            (Role.Consignee,  "貨物追跡", "請求管理") ::
            (Role.Sales,      "見積",     "荷役管理") ::
            (Role.Router,     "航路管理", "請求管理") ::
            (Role.Handler,    "荷役管理", "見積") ::
            (Role.Tracker,    "荷役管理", "見積") ::
            (Role.Accountant, "請求管理", "航路管理") ::
            (Role.Shipper,    "貨物予約", "管理設定") :: Nil;
        Assert.assertTrue(cases |> List.forAll(match (r, visible, hidden) -> {
            let html = render(nav(Some(principal(1i64, "利用者", r)), "/", None));
            String.contains(substr = visible, html) and not String.contains(substr = hidden, html)
        }))

    ///
    /// 管理者はすべてのメニューを見られること。
    ///
    /// ロール表で `Admin` は「全機能」と定義されている。項目ごとの表示ロールに
    /// `Admin` を書き漏らすと管理者が業務画面へ到達できなくなるため、網羅で固定する。
    ///
    @Test
    def testAdminSeesAllItems(): Unit \ Assert =
        let html = render(nav(Some(principal(1i64, "管理者", Role.Admin)), "/", None));
        let items = "ダッシュボード" :: "見積" :: "荷主" :: "貨物予約" :: "貨物追跡" ::
                    "荷役管理" :: "航路管理" :: "請求管理" :: "管理設定" :: Nil;
        Assert.assertTrue(items |> List.forAll(label -> String.contains(substr = label, html)))
}
