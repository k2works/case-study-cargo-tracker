///
/// Cookie の解析と属性のテスト（セキュリティ回帰テスト）。
///
/// [テスト戦略](../../../../docs/design/test_strategy.md) 8.4 の「Cookie」の項目に対応する。
///
mod CookieTest {
    use SharedHttpAuth.{cookieValue, sessionCookieName, setSessionCookie, clearSessionCookie}

    /// 単一の Cookie を取り出せること
    @Test
    def testReadsSingleCookie(): Unit \ Assert =
        Assert.assertEq(expected = Some("abc123"),
            cookieValue(Some("SESSIONID=abc123"), sessionCookieName()))

    /// 複数の Cookie から目的の値を取り出せること
    @Test
    def testReadsCookieAmongOthers(): Unit \ Assert =
        Assert.assertEq(expected = Some("abc123"),
            cookieValue(Some("theme=dark; SESSIONID=abc123; lang=ja"), sessionCookieName()))

    /// Cookie ヘッダがない場合は None を返すこと
    @Test
    def testReturnsNoneWithoutHeader(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(cookieValue(None, sessionCookieName())))

    /// 目的の Cookie がない場合は None を返すこと
    @Test
    def testReturnsNoneWhenAbsent(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(cookieValue(Some("theme=dark"), sessionCookieName())))

    /// 値が空の Cookie は空文字として扱うこと（削除済みの Cookie）
    @Test
    def testReadsEmptyValue(): Unit \ Assert =
        Assert.assertEq(expected = Some(""), cookieValue(Some("SESSIONID="), sessionCookieName()))

    ///
    /// 名前が前方一致する別の Cookie を取り違えないこと。
    ///
    /// `SESSIONID_OLD=...` や `XSESSIONID=...` を送りつけて、目的の Cookie が
    /// ないのに値を得たように見せかける経路を塞ぐ。キーは完全一致で比較する。
    ///
    @Test
    def testDoesNotMatchPrefixedCookieName(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(
            cookieValue(Some("SESSIONID_OLD=attacker"), sessionCookieName())));
        Assert.assertTrue(Option.isEmpty(
            cookieValue(Some("XSESSIONID=attacker"), sessionCookieName())))

    ///
    /// 同名の Cookie が複数あるとき、最初の 1 つを採ること。
    ///
    /// 攻撃者は上位ドメインやパス違いで同名 Cookie を追加でき、ブラウザは
    /// 両方を送る。**どちらを採るかを仕様として固定する**（未定義のまま放置すると、
    /// 実装を変えた瞬間に静かにセッションが乗っ取られる経路になる）。
    ///
    @Test
    def testTakesFirstOfDuplicateCookies(): Unit \ Assert =
        Assert.assertEq(expected = Some("legit"),
            cookieValue(Some("SESSIONID=legit; SESSIONID=attacker"), sessionCookieName()))

    ///
    /// 値に `=` を含む Cookie を最初の `=` で分割すること。
    ///
    /// Base64 の値は末尾に `=` を持つ。分割位置を誤ると値が切れて認証が通らなくなる。
    ///
    @Test
    def testSplitsAtFirstEqualsSign(): Unit \ Assert =
        Assert.assertEq(expected = Some("YWJjMTIz=="),
            cookieValue(Some("SESSIONID=YWJjMTIz=="), sessionCookieName()))

    /// 前後の空白があっても解析できること（`; ` 区切りの揺れ）
    @Test
    def testTrimsWhitespaceAroundPairs(): Unit \ Assert =
        Assert.assertEq(expected = Some("abc123"),
            cookieValue(Some("theme=dark;   SESSIONID=abc123  ;lang=ja"), sessionCookieName()))

    /// `=` を含まない断片を無視すること（壊れたヘッダで例外にしない）
    @Test
    def testIgnoresMalformedPairs(): Unit \ Assert =
        Assert.assertEq(expected = Some("abc123"),
            cookieValue(Some("broken; SESSIONID=abc123"), sessionCookieName()))

    ///
    /// `HttpOnly` と `SameSite=Lax` が必ず付くこと。
    ///
    /// `HttpOnly` は XSS でセッションを盗まれる経路を塞ぎ、
    /// `SameSite=Lax` は他サイトからの POST に Cookie を送らない（CSRF の一次防御）。
    ///
    @Test
    def testSessionCookieHasSecurityAttributes(): Unit \ Assert =
        let header = setSessionCookie("abc123", false);
        Assert.assertTrue(String.contains(substr = "HttpOnly", header));
        Assert.assertTrue(String.contains(substr = "SameSite=Lax", header));
        Assert.assertTrue(String.contains(substr = "Path=/", header))

    /// 本番では Secure が付くこと
    @Test
    def testSecureAttributeInProduction(): Unit \ Assert =
        Assert.assertTrue(String.contains(substr = "Secure", setSessionCookie("abc123", true)))

    ///
    /// 開発では Secure を付けないこと。
    ///
    /// HTTP の開発環境で `Secure` を付けるとブラウザが Cookie を送らず、
    /// ログインしてもすぐ未認証に戻る。
    ///
    @Test
    def testNoSecureAttributeInDevelopment(): Unit \ Assert =
        Assert.assertTrue(not String.contains(substr = "Secure", setSessionCookie("abc123", false)))

    /// ログアウト時の Cookie が即時失効すること
    @Test
    def testClearCookieExpiresImmediately(): Unit \ Assert =
        let header = clearSessionCookie(false);
        Assert.assertTrue(String.contains(substr = "Max-Age=0", header));
        Assert.assertTrue(String.contains(substr = "HttpOnly", header))
}
