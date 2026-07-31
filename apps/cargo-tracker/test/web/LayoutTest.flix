mod LayoutTest {
    use SharedHtml.{text, element, class_, render}
    use SharedHtmlLayout.{page, nav, footer, principal, renderPage}
    use SharedHttp.Role

    def body(): SharedHtml.Html =
        element("p", class_("lead") :: Nil, text("本文") :: Nil)

    /// ページ枠がタイトル・本文・フッターを含むこと
    @Test
    def testPageRendersTitleBodyAndFooter(): Unit \ Assert =
        let html = renderPage("貨物追跡", None, "", body());
        Assert.assertTrue(String.contains(substr = "<title>貨物追跡 | CargoTracker</title>", html));
        Assert.assertTrue(String.contains(substr = "本文", html));
        Assert.assertTrue(String.contains(substr = "CargoTracker System", html))

    /// DOCTYPE と lang 属性を持つこと（アクセシビリティ・互換性の前提）
    @Test
    def testPageIsWellFormedDocument(): Unit \ Assert =
        let html = renderPage("貨物追跡", None, "", body());
        Assert.assertTrue(String.startsWith(prefix = "<!DOCTYPE html><html lang=\"ja\">", html))

    /// 未認証ではナビゲーションを表示しないこと（公開追跡ページの要件）
    @Test
    def testNavIsHiddenForAnonymous(): Unit \ Assert =
        Assert.assertEq(expected = "", render(nav(None, "")))

    /// ロールに応じてメニュー項目を出し分けること
    @Test
    def testNavShowsOnlyItemsForRole(): Unit \ Assert =
        let html = render(nav(Some(principal("山田", Role.Accountant)), "/billing/invoices"));
        Assert.assertTrue(String.contains(substr = "請求管理", html));
        Assert.assertTrue(not String.contains(substr = "荷役管理", html))

    /// 全ロール共通の項目（ダッシュボード・ログアウト）が常に出ること
    @Test
    def testNavAlwaysShowsCommonItems(): Unit \ Assert =
        let html = render(nav(Some(principal("山田", Role.Handler)), "/handling"));
        Assert.assertTrue(String.contains(substr = "ダッシュボード", html));
        Assert.assertTrue(String.contains(substr = "ログアウト", html))

    /// 現在位置のメニューに active を付けること
    @Test
    def testNavMarksActiveItem(): Unit \ Assert =
        let html = render(nav(Some(principal("山田", Role.Handler)), "/handling"));
        Assert.assertTrue(String.contains(substr = "nav-link active", html))

    /// ナビゲーションに ARIA 属性が付くこと（ui_design.md のアクセシビリティ要件）
    @Test
    def testNavHasAriaAttributes(): Unit \ Assert =
        let html = render(nav(Some(principal("山田", Role.Admin)), "/"));
        Assert.assertTrue(String.contains(substr = "role=\"navigation\"", html));
        Assert.assertTrue(String.contains(substr = "aria-label=\"メインナビゲーション\"", html))

    /// 表示名がエスケープされること（ナビは全画面に出るため XSS の影響が最も大きい）
    @Test
    def testNavEscapesDisplayName(): Unit \ Assert =
        let html = render(nav(Some(principal("<script>", Role.Admin)), "/"));
        Assert.assertTrue(not String.contains(substr = "<script>", html))

    /// フッターに著作権表示があること
    @Test
    def testFooterRendersCopyright(): Unit \ Assert =
        Assert.assertTrue(String.contains(substr = "CargoTracker System", render(footer())))

    /// page はナビを本文より前に置くこと（Tab 順序の前提）
    @Test
    def testPagePlacesNavBeforeBody(): Unit \ Assert =
        let html = render(page("在庫", Some(principal("山田", Role.Admin)), "/", body()));
        let navAt = String.indexOf(substr = "メインナビゲーション", html) |> Option.getWithDefault(-1);
        let bodyAt = String.indexOf(substr = "本文", html) |> Option.getWithDefault(-1);
        Assert.assertTrue(navAt >= 0 and bodyAt >= 0 and navAt < bodyAt)
}
