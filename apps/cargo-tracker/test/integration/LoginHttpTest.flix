///
/// ログイン・ログアウトの受入テスト（US26・US27）。
///
/// アウトサイドインで最初に書くテスト。HTTP リクエストからデータベースまでを縦断する。
///
mod LoginHttpTest {
    use SharedHttpTestClient.{get, post, status, body, TestResponse}
    use TestApp.withApp

    def username(): String = "yamada"
    def password(): String = "correct horse battery staple"

    /// ログイン画面が未認証で表示されること
    @Test
    def testLoginPageIsPublic(): Unit \ Assert + IO =
        withApp("login_page", p -> {
            let res = get(p, "/login");
            Assert.assertEq(expected = 200, status(res));
            let html = body(res);
            Assert.assertTrue(String.contains(substr = "ユーザー名", html));
            Assert.assertTrue(String.contains(substr = "パスワード", html))
        })

    /// 未認証でダッシュボードへアクセスするとログイン画面へ誘導されること（US26 受入基準 7）
    @Test
    def testRedirectsAnonymousToLogin(): Unit \ Assert + IO =
        withApp("login_redirect", p -> {
            let res = get(p, "/");
            Assert.assertEq(expected = 302, status(res));
            Assert.assertEq(expected = Some("/login"), SharedHttpTestClient.header(res, "Location"))
        })

    /// 公開追跡は未認証のままアクセスできること（US26 受入基準 7 の例外）
    @Test
    def testPublicTrackingStaysAnonymous(): Unit \ Assert + IO =
        withApp("login_public_ok", p -> {
            Assert.assertEq(expected = 200, status(get(p, "/public/tracking")))
        })

    /// 正しい資格情報でログインするとダッシュボードへ遷移すること
    @Test
    def testLoginRedirectsToDashboard(): Unit \ Assert + IO =
        withApp("login_success", p -> {
            let res = login(p, username(), password());
            Assert.assertEq(expected = 302, status(res));
            Assert.assertEq(expected = Some("/"), SharedHttpTestClient.header(res, "Location"));
            Assert.assertTrue(not Option.isEmpty(SharedHttpTestClient.header(res, "Set-Cookie")))
        })

    ///
    /// 誤ったパスワードで一律のメッセージを表示すること。
    ///
    /// 「利用者が存在しない」と「パスワードが違う」を区別すると、
    /// 存在するユーザー名を総当たりで特定できてしまう。
    ///
    @Test
    def testShowsGenericFailureMessage(): Unit \ Assert + IO =
        withApp("login_wrong_password", p -> {
            let wrongPassword = body(login(p, username(), "wrong"));
            let unknownUser = body(login(p, "nobody", password()));
            let expected = "ユーザー名またはパスワードが正しくありません";
            Assert.assertTrue(String.contains(substr = expected, wrongPassword));
            Assert.assertTrue(String.contains(substr = expected, unknownUser))
        })

    /// ログイン後にダッシュボードが表示されること
    @Test
    def testDashboardIsVisibleAfterLogin(): Unit \ Assert + IO =
        withApp("login_dashboard", p -> {
            let sessionCookie = loginAndGetCookie(p);
            let res = getWithCookie(p, "/", sessionCookie);
            Assert.assertEq(expected = 200, status(res));
            Assert.assertTrue(String.contains(substr = "ダッシュボード", body(res)))
        })

    ///
    /// 5 回連続で失敗するとアカウントがロックされること（US26 受入基準 4）。
    ///
    /// 6 回目は正しいパスワードでも通らない。
    ///
    @Test
    def testLocksAccountAfterFiveFailures(): Unit \ Assert + IO =
        withApp("login_lockout", p -> {
            failTimes(p, 5);
            let res = login(p, username(), password());
            let html = body(res);
            Assert.assertEq(expected = 200, status(res));
            Assert.assertTrue(String.contains(substr = "ロック", html))
        })

    /// ロック前の失敗回数は成功でリセットされること
    @Test
    def testSuccessResetsFailureCount(): Unit \ Assert + IO =
        withApp("login_reset", p -> {
            failTimes(p, 4);
            discard login(p, username(), password());
            failTimes(p, 4);
            // 通算 8 回失敗しているが、連続では 4 回のためロックされない
            let res = login(p, username(), password());
            Assert.assertEq(expected = 302, status(res))
        })

    /// 無効化されたアカウントでログインできないこと（US26 受入基準 5）
    @Test
    def testRejectsDisabledAccount(): Unit \ Assert + IO =
        withApp("login_disabled", p -> {
            let html = body(login(p, "taro", password()));
            Assert.assertTrue(String.contains(substr = "管理者", html))
        })

    /// ログアウトでセッションが無効になること（US27 受入基準 1・2）
    @Test
    def testSessionIsInvalidatedAfterLogout(): Unit \ Assert + IO =
        withApp("logout", p -> {
            let sessionCookie = loginAndGetCookie(p);
            let csrf = csrfTokenOf(p, sessionCookie);
            let res = postWithCookie(p, "/logout", "_csrf=" + csrf, sessionCookie);
            Assert.assertEq(expected = 302, status(res));
            // ログアウト後は同じ Cookie でダッシュボードへ入れない
            let after = getWithCookie(p, "/", sessionCookie);
            Assert.assertEq(expected = 302, status(after));
            Assert.assertEq(expected = Some("/login"), SharedHttpTestClient.header(after, "Location"))
        })

    ///
    /// CSRF トークンなしのログアウトが拒否されること。
    ///
    /// ログアウトは状態を変更する操作であり、第三者のリンク踏ませで
    /// 発火してはならない。
    ///
    @Test
    def testLogoutRequiresCsrfToken(): Unit \ Assert + IO =
        withApp("logout_csrf", p -> {
            let sessionCookie = loginAndGetCookie(p);
            let res = postWithCookie(p, "/logout", "", sessionCookie);
            Assert.assertEq(expected = 403, status(res))
        })

    // ---- ヘルパー ----

    /// 誤ったパスワードで指定回数ログインを試みる
    def failTimes(p: Int32, times: Int32): Unit \ IO =
        if (times <= 0)
            ()
        else {
            discard login(p, username(), "wrong");
            failTimes(p, times - 1)
        }

    def login(p: Int32, name: String, pass: String): TestResponse \ IO =
        post(p, "/login", "username=" + name + "&password=" + urlEncode(pass))

    def loginAndGetCookie(p: Int32): String \ IO =
        let res = login(p, username(), password());
        match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None    => ""
            case Some(v) => sessionCookieOf(v)
        }

    /// `SESSIONID=xxx; Path=/; ...` から `SESSIONID=xxx` だけを取り出す
    def sessionCookieOf(setCookie: String): String =
        match String.indexOf(substr = ";", setCookie) {
            case None    => setCookie
            case Some(i) => String.slice(start = 0, end = i, setCookie)
        }

    /// ダッシュボードのフォームから CSRF トークンを取り出す
    def csrfTokenOf(p: Int32, cookie: String): String \ IO =
        let html = body(getWithCookie(p, "/", cookie));
        let marker = "name=\"_csrf\" value=\"";
        match String.indexOf(substr = marker, html) {
            case None => ""
            case Some(i) =>
                let start = i + String.length(marker);
                String.slice(start = start, end = start + 64, html)
        }

    def urlEncode(s: String): String =
        String.replace(src = " ", dst = "+", s)

    def getWithCookie(p: Int32, path: String, cookie: String): TestResponse \ IO =
        SharedHttpTestClient.getWith(p, path, Map#{"Cookie" => cookie})

    def postWithCookie(p: Int32, path: String, formBody: String, cookie: String): TestResponse \ IO =
        SharedHttpTestClient.postWith(p, path, formBody, Map#{"Cookie" => cookie})
}
