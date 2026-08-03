///
/// CSRF 保護のテスト（セキュリティ回帰テスト）。
///
/// [テスト戦略](../../../../docs/design/test_strategy.md) 8.4 の「CSRF」の項目に対応する。
/// 自作の検証であるため、**通してはいけない組み合わせ**を網羅で固定する。
///
mod CsrfTest {
    use SharedHttp.HttpMethod
    use SharedHttp.RequiredRole
    use SharedHttpAuth.{isValidCsrf, isStateChanging, csrfFieldName, requiresCsrf}
    use SharedSecurity.Role

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

    ///
    /// 検証対象となるルートの判定を固定すること（IT3 レビュー M14）。
    ///
    /// `requiresCsrf` は private でテストがなかった。**`Anonymous` な POST ルートが
    /// 追加された瞬間に無防備になる**判定であり、その境界をテストで押さえる。
    /// ログインは「セッション成立前でトークンが存在しない」ため意図的に対象外である。
    ///
    @Test
    def testAnonymousRoutesAreExemptFromCsrf(): Unit \ Assert =
        Assert.assertTrue(not requiresCsrf(RequiredRole.Anonymous))

    /// ロールを要求するルートはすべて検証対象であること
    @Test
    def testAuthenticatedRoutesRequireCsrf(): Unit \ Assert =
        Assert.assertTrue(requiresCsrf(RequiredRole.RoleRequired(Role.Sales)));
        Assert.assertTrue(requiresCsrf(RequiredRole.RoleAnyOf(Role.Sales :: Role.Admin :: Nil)));
        // 空のロール集合（誰も通れないルート）も対象から漏らさない
        Assert.assertTrue(requiresCsrf(RequiredRole.RoleAnyOf(Nil)))

    ///
    /// トークンの比較が長さの違いで早期に終わらないこと。
    ///
    /// 内容を検証するのではなく、**先頭 1 文字だけ違う場合と全部違う場合の
    /// どちらも等しく拒否される**ことを固定する（IT3 レビュー L5 の定数時間比較）。
    ///
    @Test
    def testRejectsTokensDifferingAtAnyPosition(): Unit \ Assert =
        let expected = token();
        Assert.assertTrue(not isValidCsrf(Some(expected), Some("b" + String.dropLeft(1, expected))));
        Assert.assertTrue(not isValidCsrf(Some(expected), Some(String.dropRight(1, expected) + "b")));
        // 長さ違いも拒否する
        Assert.assertTrue(not isValidCsrf(Some(expected), Some(String.dropRight(1, expected))));
        Assert.assertTrue(not isValidCsrf(Some(expected), Some(expected + "b")))

    /// フォームフィールド名が Components.form と一致していること
    @Test
    def testFieldNameMatchesFormComponent(): Unit \ Assert =
        let html = SharedHtml.render(
            SharedHtmlComponents.form("/x", "post", Some(token()), Nil));
        Assert.assertTrue(String.contains(substr = "name=\"" + csrfFieldName() + "\"", html))
}
