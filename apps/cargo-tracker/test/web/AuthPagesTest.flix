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
}
