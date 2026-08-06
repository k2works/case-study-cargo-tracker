///
/// パスワードのハッシュ化と検証のテスト（セキュリティ回帰テスト）。
///
/// [テスト戦略](../../../../docs/design/test_strategy.md) 8.4 の「パスワード」の項目に対応する。
/// 自作の認証機構であるため、**アルゴリズムの性質そのもの**を固定する。
///
mod PasswordTest {
    use SharedBcryptPassword.withBcryptPassword
    use SharedPassword.Password

    def plain(): String = "correct horse battery staple"

    /// 正しいパスワードを受理すること
    @Test
    def testAcceptsCorrectPassword(): Unit \ Assert + IO =
        let ok = withBcryptPassword(() -> {
            let hashed = Password.hash(plain());
            Password.verify(plain(), hashed)
        });
        Assert.assertTrue(ok)

    /// 誤ったパスワードを拒否すること
    @Test
    def testRejectsWrongPassword(): Unit \ Assert + IO =
        let ok = withBcryptPassword(() -> {
            let hashed = Password.hash(plain());
            Password.verify("wrong password", hashed)
        });
        Assert.assertTrue(not ok)

    ///
    /// 同じ平文でも毎回異なるハッシュになること（ソルトが効いていること）。
    ///
    /// ソルトがないと、DB が漏洩したときにレインボーテーブルで一括解読される。
    ///
    @Test
    def testHashIsSaltedPerCall(): Unit \ Assert + IO =
        let (a, b) = withBcryptPassword(() -> (Password.hash(plain()), Password.hash(plain())));
        Assert.assertTrue(a != b)

    ///
    /// ハッシュに平文が含まれないこと。
    ///
    /// 「平文がログ・レスポンス・DB に現れない」ことの最小の担保。
    ///
    @Test
    def testHashDoesNotContainPlaintext(): Unit \ Assert + IO =
        let hashed = withBcryptPassword(() -> Password.hash(plain()));
        Assert.assertTrue(not String.contains(substr = plain(), hashed))

    ///
    /// コスト 12 で生成されていること（非機能要件 4.1）。
    ///
    /// BCrypt のハッシュは `$2a$12$...` の形式でコストを含む。
    /// 下げると総当たりへの耐性が落ちるため、値そのものを固定する。
    ///
    @Test
    def testUsesCostTwelve(): Unit \ Assert + IO =
        let hashed = withBcryptPassword(() -> Password.hash(plain()));
        Assert.assertTrue(String.contains(substr = "$12$", hashed))

    ///
    /// 壊れたハッシュでは認証を通さないこと。
    ///
    /// DB の値が切り詰められた場合などに、例外で 500 になるのではなく
    /// 「認証失敗」として扱う。**例外を握ることで認証が素通りしないこと**を固定する。
    ///
    @Test
    def testRejectsMalformedHash(): Unit \ Assert + IO =
        let results = withBcryptPassword(() -> {
            Password.verify(plain(), "") ::
            Password.verify(plain(), "not-a-bcrypt-hash") ::
            Password.verify(plain(), "$2a$12$tooshort") :: Nil
        });
        Assert.assertTrue(results |> List.forAll(r -> not r))

    /// 空のパスワードでも検証が例外にならないこと
    @Test
    def testHandlesEmptyPlaintext(): Unit \ Assert + IO =
        let ok = withBcryptPassword(() -> {
            let hashed = Password.hash(plain());
            Password.verify("", hashed)
        });
        Assert.assertTrue(not ok)
}
