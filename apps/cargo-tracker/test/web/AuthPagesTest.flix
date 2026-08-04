///
/// ログイン画面のテスト。
///
/// 開発環境の既定値は**利便性のための機能でありながら、誤って本番へ出ると
/// 資格情報の露出になる**。出る条件と出ない条件の両方を固定する。
///
mod AuthPagesTest {
    use SharedAuthPages.{renderLogin, renderDashboard, invalidCredentialsMessage}
    use SharedSecurity.{Role, principal}

    def devPrefill(): Option[(String, String)] = Some(("yamada", "password"))

    /// 既定値が渡されるとユーザー名・パスワードが入力済みになること
    @Test
    def testPrefillsCredentialsInDevelopment(): Unit \ Assert =
        let html = renderLogin(None, "", None, devPrefill());
        Assert.assertTrue(String.contains(substr = "id=\"username\" name=\"username\" value=\"yamada\"", html));
        Assert.assertTrue(String.contains(substr = "value=\"password\"", html))

    /// 既定値が入っている理由が画面に示されること
    @Test
    def testShowsDevelopmentNotice(): Unit \ Assert =
        let html = renderLogin(None, "", None, devPrefill());
        Assert.assertTrue(String.contains(substr = "開発環境のため", html))

    ///
    /// 既定値が渡されなければ空欄であること（本番の挙動）。
    ///
    /// パスワード欄に `value` 属性が出ないことまで確認する。
    ///
    @Test
    def testNoPrefillWithoutDevelopment(): Unit \ Assert =
        let html = renderLogin(None, "", None, None);
        Assert.assertTrue(String.contains(substr = "name=\"username\" value=\"\"", html));
        Assert.assertTrue(not String.contains(substr = "value=\"password\"", html));
        Assert.assertTrue(not String.contains(substr = "開発環境のため", html))

    ///
    /// 認証に失敗した後はパスワードを戻さないこと。
    ///
    /// 開発環境であっても、入力し直した内容を上書きしない。
    ///
    @Test
    def testDoesNotRestorePasswordAfterFailure(): Unit \ Assert =
        let html = renderLogin(None, "taro", Some(invalidCredentialsMessage()), devPrefill());
        Assert.assertTrue(String.contains(substr = "value=\"taro\"", html));
        Assert.assertTrue(not String.contains(substr = "value=\"password\"", html))

    /// 失敗メッセージが表示されること
    @Test
    def testShowsFailureMessage(): Unit \ Assert =
        let html = renderLogin(None, "", Some(invalidCredentialsMessage()), None);
        Assert.assertTrue(String.contains(substr = invalidCredentialsMessage(), html))

    /// パスワード欄が type="password" であること（画面に平文が見えない）
    @Test
    def testPasswordFieldIsMasked(): Unit \ Assert =
        let html = renderLogin(None, "", None, devPrefill());
        Assert.assertTrue(String.contains(substr = "type=\"password\" id=\"password\"", html))

    /// ダッシュボードに利用者名とロールが業務の言葉で出ること
    @Test
    def testDashboardShowsUserAndRole(): Unit \ Assert =
        let html = renderDashboard(principal(1i64, "yamada", Role.Sales), None);
        Assert.assertTrue(String.contains(substr = "yamada さん（営業担当者）", html));
        Assert.assertTrue(not String.contains(substr = "ROLE_SALES", html))

    // ---- ダッシュボードの作業入口（IT3 レビュー L7） ----

    ///
    /// すべてのロールがダッシュボードから 1 つ以上の作業入口を持つこと。
    ///
    /// **navbar に出ているだけでは足りない**。上部メニューの小さなリンクは、
    /// 手袋をしたまま屋外で操作する荷役作業員にとって押しにくい。
    /// 本文にも押しやすい入口があることを、全ロールについて確かめる。
    ///
    @Test
    def testEveryRoleHasWorkEntryOnDashboard(): Unit \ Assert =
        let hasEntry = r ->
            String.contains(substr = "work-entries",
                renderDashboard(principal(1i64, "利用者", r), None));
        Assert.assertTrue(SharedSecurity.allRoles() |> List.forAll(hasEntry))

    ///
    /// 営業担当者の入口に荷主管理があること。
    ///
    /// 画面を作っても、そのロールがダッシュボードから到達できなければ
    /// **機能がないのと同じ**である。
    ///
    @Test
    def testSalesHasShipperEntry(): Unit \ Assert =
        let html = renderDashboard(principal(1i64, "山田", Role.Sales), None);
        Assert.assertTrue(String.contains(substr = "href=\"/shippers\"", html));
        Assert.assertTrue(String.contains(substr = "荷主", html))

    ///
    /// 担当外の業務が入口に出ないこと。
    ///
    /// 押しても 403 になる入口を出すと、利用者は「壊れている」と受け取る。
    ///
    @Test
    def testDoesNotShowEntriesForOtherRoles(): Unit \ Assert =
        let html = renderDashboard(principal(1i64, "田中", Role.Handler), None);
        Assert.assertTrue(not String.contains(substr = "href=\"/shippers\"", html));
        // 荷役管理は未実装（IT9）。**入口は出すがリンクにしない**。
        // 消すと荷役作業員は入口が 1 つも無くなり、権限の問題だと誤解する
        // （IT6 レビュー M3。`NavigationReachabilityTest` が状態を固定する）
        Assert.assertTrue(String.contains(substr = "荷役管理（準備中）", html));
        Assert.assertTrue(not String.contains(substr = "href=\"/handling\"", html))

    ///
    /// 管理者はすべての入口を持つこと。
    ///
    /// ロール表で「全機能」と定義されている。個別の列挙に書き漏らして
    /// 管理者が締め出されないことを確かめる。
    ///
    @Test
    def testAdminHasAllEntries(): Unit \ Assert =
        let html = renderDashboard(principal(1i64, "管理", Role.Admin), None);
        // 実装済みは押せること。**未実装を press できる形で並べない**
        let links = "/shippers" :: "/bookings" :: "/public/tracking" :: "/voyages" ::
                    // 見積は IT8（US01）で実装した
                    "/estimates" :: Nil;
        Assert.assertTrue(links |> List.forAll(h ->
            String.contains(substr = "href=\"${h}\"", html)));
        // 未実装も入口としては見えること（管理者から機能の存在を隠さない）
        let preparing = "荷役管理（準備中）" :: "請求管理（準備中）" ::
                        "管理設定（準備中）" :: Nil;
        Assert.assertTrue(preparing |> List.forAll(label ->
            String.contains(substr = label, html)))
}
