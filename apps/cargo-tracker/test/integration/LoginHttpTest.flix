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

    ///
    /// 4 回目の失敗ではロックされないこと（境界の下側。IT3 レビュー M11）。
    ///
    /// 上限の 5 に対して 1 つ手前を独立したテストで押さえる。off-by-one で
    /// 「4 回でロック」になっても、5 回のテストだけでは緑のまま通過する。
    ///
    @Test
    def testNotLockedAtFourFailures(): Unit \ Assert + IO =
        withApp("login_lock_4", p -> {
            failTimes(p, 4);
            let res = login(p, username(), password());
            Assert.assertEq(expected = 302, status(res))
        })

    ///
    /// 6 回目以降も解除されないこと（境界の上側。IT3 レビュー M11）。
    ///
    /// 失敗回数をロック時に 0 へ戻す実装のため、**ロック後の失敗が
    /// カウンタを回してロックを解いてしまわないか**をここで固定する。
    ///
    @Test
    def testStaysLockedBeyondThreshold(): Unit \ Assert + IO =
        withApp("login_lock_6", p -> {
            failTimes(p, 6);
            let res = login(p, username(), password());
            Assert.assertEq(expected = 200, status(res));
            Assert.assertTrue(String.contains(substr = "ロック", body(res)))
        })

    ///
    /// ロック前の失敗回数は成功でリセットされること。
    ///
    /// 通算 8 回失敗しているが、連続では 4 回のためロックされない。
    ///
    @Test
    def testSuccessResetsFailureCount(): Unit \ Assert + IO =
        withApp("login_reset", p -> {
            failTimes(p, 4);
            discard login(p, username(), password());
            failTimes(p, 4);
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
            Assert.assertEq(expected = Some("/login?loggedout"),
                SharedHttpTestClient.header(res, "Location"));
            // ログアウト後は同じ Cookie でダッシュボードへ入れない。
            // 実ブラウザは Set-Cookie で Cookie を消すため送ってこないが、
            // 古い Cookie を送りつけられても入れないことを確認する
            let after = getWithCookie(p, "/", sessionCookie);
            Assert.assertEq(expected = 302, status(after));
            let location = SharedHttpTestClient.header(after, "Location") |> Option.getWithDefault("");
            Assert.assertTrue(String.startsWith(prefix = "/login", location))
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

    ///
    /// 他セッションの CSRF トークンでは状態を変更できないこと（IT3 レビュー H17）。
    ///
    /// 純粋関数 `isValidCsrf` の単体テストでは「配線を取り違えても緑になる」。
    /// たとえばセッションのトークンではなくリクエストの送信値同士を比べていても、
    /// 単体テストは通ってしまう。**2 つのセッションを実際に成立させ、
    /// 一方のトークンを他方のリクエストに載せて拒否されること**を HTTP 経路で固定する。
    ///
    @Test
    def testRejectsCsrfTokenFromAnotherSession(): Unit \ Assert + IO =
        withApp("csrf_cross_session", p -> {
            let cookieA = loginAndGetCookieFor(p, username());
            let cookieB = loginAndGetCookieFor(p, AuthFixtures.secondUsername());
            let tokenB = csrfTokenOf(p, cookieB);
            // B のトークンを A のセッションでログアウトに使う
            let res = postWithCookie(p, "/logout", "_csrf=" + tokenB, cookieA);
            Assert.assertEq(expected = 403, status(res));
            // A のセッションは無効化されていない（拒否はしたが壊してはいない）
            Assert.assertEq(expected = 200, status(getWithCookie(p, "/", cookieA)))
        })

    ///
    /// 自分のセッションのトークンなら通ること（上のテストの対照）。
    ///
    /// これがないと「常に 403」でも `testRejectsCsrfTokenFromAnotherSession` が
    /// 緑になり、CSRF 検証が壊れていることを検出できない。
    ///
    @Test
    def testAcceptsCsrfTokenFromOwnSession(): Unit \ Assert + IO =
        withApp("csrf_own_session", p -> {
            let cookieA = loginAndGetCookieFor(p, username());
            let tokenA = csrfTokenOf(p, cookieA);
            let res = postWithCookie(p, "/logout", "_csrf=" + tokenA, cookieA);
            Assert.assertEq(expected = 302, status(res))
        })

    // ---- ヘルパー ----

    /// 誤ったパスワードで指定回数ログインを試みる
    def failTimes(p: Int32, times: Int32): Unit \ Assert + IO =
        if (times <= 0)
            ()
        else {
            discard login(p, username(), "wrong");
            failTimes(p, times - 1)
        }

    ///
    /// ログインを試みる。
    ///
    /// **`GET /login` を先に踏む**（IT9・TS16）。ログインの POST は
    /// CSRF トークンを要求するようになった。トークンを取らずに投げると
    /// 403 になり、**認証の失敗と CSRF の失敗が区別できなくなる**。
    ///
    def login(p: Int32, name: String, pass: String): TestResponse \ Assert + IO =
        SharedHttpTestClient.attemptLogin(p, name, pass)

    ///
    /// ログインしてセッション Cookie を得る。
    ///
    /// **失敗を空文字で握りつぶさない**。空文字を返すと、後続の
    /// 「ログアウト後は入れない」テストが「そもそもログインが壊れている」場合にも
    /// 緑になる（未認証も 302 で /login へ飛ぶため期待値と一致してしまう）。
    ///
    def loginAndGetCookie(p: Int32): String \ Assert + IO =
        loginAndGetCookieFor(p, username())

    /// 利用者を指定してログインし、セッション Cookie を得る
    def loginAndGetCookieFor(p: Int32, name: String): String \ Assert + IO =
        let res = login(p, name, password());
        match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None    => { Assert.fail("${name} のログインで Set-Cookie が得られなかった"); "" }
            case Some(v) => sessionCookieOf(v)
        }

    /// `SESSIONID=xxx; Path=/; ...` から `SESSIONID=xxx` だけを取り出す
    def sessionCookieOf(setCookie: String): String =
        match String.indexOf(substr = ";", setCookie) {
            case None    => setCookie
            case Some(i) => String.slice(start = 0, end = i, setCookie)
        }

    /// ダッシュボードのフォームから CSRF トークンを取り出す
    def csrfTokenOf(p: Int32, cookie: String): String \ Assert + IO =
        let html = body(getWithCookie(p, "/", cookie));
        let marker = "name=\"_csrf\" value=\"";
        match String.indexOf(substr = marker, html) {
            case None => { Assert.fail("ダッシュボードに CSRF トークンが見つからなかった"); "" }
            case Some(i) =>
                let start = i + String.length(marker);
                String.slice(start = start, end = start + 64, html)
        }


    def getWithCookie(p: Int32, path: String, cookie: String): TestResponse \ IO =
        SharedHttpTestClient.getWith(p, path, Map#{"Cookie" => cookie})

    def postWithCookie(p: Int32, path: String, formBody: String, cookie: String): TestResponse \ IO =
        SharedHttpTestClient.postWith(p, path, formBody, Map#{"Cookie" => cookie})
}
