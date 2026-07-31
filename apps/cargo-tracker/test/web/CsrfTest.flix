///
/// CSRF 保護のテスト（セキュリティ回帰テスト）。
///
/// [テスト戦略](../../../../docs/design/test_strategy.md) 8.4 の「CSRF」の項目に対応する。
/// 自作の検証であるため、**通してはいけない組み合わせ**を網羅で固定する。
///
mod CsrfTest {
    use SharedHttp.HttpMethod
    use SharedHttpAuth.{isValidCsrf, isStateChanging, csrfFieldName}

    def token(): String = "a".repeat(64)

    /// 一致するトークンを受理すること
    @Test
    def testAcceptsMatchingToken(): Unit \ Assert =
        Assert.assertTrue(isValidCsrf(Some(token()), Some(token())))

    /// トークンなしの POST を拒否すること
    @Test
    def testRejectsMissingToken(): Unit \ Assert =
        Assert.assertTrue(not isValidCsrf(Some(token()), None))

    /// 他セッションのトークンを拒否すること
    @Test
    def testRejectsTokenFromOtherSession(): Unit \ Assert =
        Assert.assertTrue(not isValidCsrf(Some(token()), Some("b".repeat(64))))

    ///
    /// セッションにトークンがない場合を拒否すること。
    ///
    /// 未認証で状態を変更する経路は存在しない。あれば設計の誤りであり、
    /// 「トークンがないから素通り」にしてはならない。
    ///
    @Test
    def testRejectsWhenSessionHasNoToken(): Unit \ Assert =
        Assert.assertTrue(not isValidCsrf(None, Some(token())));
        Assert.assertTrue(not isValidCsrf(None, None))

    /// 空文字のトークン同士を一致とみなさないこと
    @Test
    def testRejectsEmptyTokens(): Unit \ Assert =
        Assert.assertTrue(not isValidCsrf(Some(""), Some("")))

    /// GET は検証対象外、状態を変更するメソッドは検証対象であること
    @Test
    def testStateChangingMethods(): Unit \ Assert =
        Assert.assertTrue(not isStateChanging(HttpMethod.Get));
        Assert.assertTrue(isStateChanging(HttpMethod.Post));
        Assert.assertTrue(isStateChanging(HttpMethod.Put));
        Assert.assertTrue(isStateChanging(HttpMethod.Delete))

    /// フォームフィールド名が Components.form と一致していること
    @Test
    def testFieldNameMatchesFormComponent(): Unit \ Assert =
        let html = SharedHtml.render(
            SharedHtmlComponents.form("/x", "post", Some(token()), Nil));
        Assert.assertTrue(String.contains(substr = "name=\"" + csrfFieldName() + "\"", html))
}
