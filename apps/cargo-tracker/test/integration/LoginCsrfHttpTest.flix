///
/// ログイン CSRF の検証（IT9・TS16）。
///
/// # このテストが存在する理由
///
/// IT8 のクローズ時レビューで、`POST /login` に CSRF 検証が効いていないことが
/// 指摘された。実装を確認すると、これは**見落としではなく意識的な先送り**だった
/// （`SharedHttpAuth.requiresCsrf` のコメントが「未認証で変更できる業務データが
/// ないため実害がない。対策が必要になった時点で ADR で判断する」と書いていた）。
///
/// **その前提が IT8 で崩れた。** 見積（US01）が入り、営業担当者が
/// 業務データを作る画面が増えた。ログイン CSRF の害は「未認証でデータを
/// 変えられること」ではなく、**被害者が攻撃者のアカウントでログインさせられ、
/// その後の作業がすべて攻撃者の口座に記録されること**である。
///
/// 見積を作った営業は、それが自分のものだと思っている。攻撃者は後から読める。
///
/// # 方式: 二重送信 Cookie
///
/// セッションが成立する前なので、セッションのトークンは使えない。
/// `GET /login` で乱数を Cookie とフォームの両方に置き、`POST /login` で
/// 一致を見る。**攻撃者は被害者のブラウザの Cookie を読めない**ため、
/// フォーム側の値を一致させられない。
///
mod LoginCsrfHttpTest {
    use SharedHttpTestClient.{status, body, assertStatus, TestResponse}
    use TestApp.withApp

    def salesUser(): String = "yamada"
    def password(): String = "correct+horse+battery+staple"

    /// ログイン画面を開き、Cookie とフォームのトークンを取り出す
    def openLoginPage(p: Int32): (String, String) \ Assert + IO =
        let res = SharedHttpTestClient.get(p, "/login");
        assertStatus(200, res);
        let cookie = match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None    => { Assert.fail("ログイン画面が CSRF Cookie を発行していない"); "" }
            case Some(v) => match String.indexOf(substr = ";", v) {
                case None    => v
                case Some(i) => String.slice(start = 0, end = i, v)
            }
        };
        let marker = "name=\"_csrf\" value=\"";
        let token = match String.indexOf(substr = marker, body(res)) {
            case None => { Assert.fail("ログインフォームに CSRF トークンが無い"); "" }
            case Some(i) =>
                let start = i + String.length(marker);
                String.slice(start = start, end = start + 64, body(res))
        };
        (cookie, token)

    def postLogin(p: Int32, formBody: String, cookie: String): TestResponse \ IO =
        SharedHttpTestClient.postWith(p, "/login", formBody, Map#{"Cookie" => cookie})

    ///
    /// **正しいトークンならログインできること**（回帰）。
    ///
    /// 防御を入れて既存の経路が通らなくなっては意味がない。
    ///
    @Test
    def testLoginSucceedsWithMatchingToken(): Unit \ Assert + IO =
        withApp("login_csrf_ok", p -> {
            let opened = openLoginPage(p);
            let cookie = fst(opened);
            let token = snd(opened);
            let res = postLogin(p,
                "username=" + salesUser() + "&password=" + password() + "&_csrf=" + token,
                cookie);
            assertStatus(302, res);
            Assert.assertTrue(not Option.isEmpty(SharedHttpTestClient.header(res, "Set-Cookie")))
        })

    ///
    /// **トークンが無ければ拒否すること**（攻撃の再現）。
    ///
    /// 攻撃者のページが `<form action="被害者のサイト/login" method="post">` を
    /// 自動送信する形である。攻撃者は Cookie を読めないため、
    /// フォームにトークンを入れられない。
    ///
    /// この式が偽になる入力: `requiresCsrf` が `Anonymous` に `false` を返す実装。
    ///
    @Test
    def testLoginIsRejectedWithoutToken(): Unit \ Assert + IO =
        withApp("login_csrf_missing", p -> {
            let cookie = fst(openLoginPage(p));
            let res = postLogin(p,
                "username=" + salesUser() + "&password=" + password(), cookie);
            Assert.assertEq(expected = 403, status(res))
        })

    ///
    /// **他人のトークンでは拒否すること**。
    ///
    /// 攻撃者が自分でログイン画面を開いて得たトークンをフォームに埋めても、
    /// **被害者のブラウザが送る Cookie は被害者のもの**であり一致しない。
    ///
    @Test
    def testLoginIsRejectedWithForeignToken(): Unit \ Assert + IO =
        withApp("login_csrf_foreign", p -> {
            let victimCookie = fst(openLoginPage(p));
            let attackerToken = snd(openLoginPage(p));
            let res = postLogin(p,
                "username=" + salesUser() + "&password=" + password() +
                "&_csrf=" + attackerToken,
                victimCookie);
            Assert.assertEq(expected = 403, status(res))
        })

    ///
    /// **Cookie が無ければ拒否すること**。
    ///
    /// フォームのトークンだけを盗んでも通らない。
    ///
    @Test
    def testLoginIsRejectedWithoutCookie(): Unit \ Assert + IO =
        withApp("login_csrf_no_cookie", p -> {
            let token = snd(openLoginPage(p));
            let res = SharedHttpTestClient.post(p, "/login",
                "username=" + salesUser() + "&password=" + password() + "&_csrf=" + token);
            Assert.assertEq(expected = 403, status(res))
        })
}
