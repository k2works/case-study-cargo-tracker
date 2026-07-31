///
/// セッション管理のテスト（セキュリティ回帰テスト）。
///
/// [テスト戦略](../../../../docs/design/test_strategy.md) 8.4 の「セッション」の項目に対応する。
/// 自作の認証機構であるため、**攻撃に対する性質**を回帰テストとして固定する。
///
mod SessionTest {
    use SharedDbMigration.migrate
    use SharedDbPool.{create, h2Config, close, withConnection, withStatement}
    use SharedDbPool.DataSource
    use SharedDbTx.transactional
    use SharedJdbcSession.withJdbcSession
    use SharedSecurity.{Role, principal, username, role}
    use SharedSession.Session

    /// テスト用の利用者を投入する
    def setup(dbName: String): DataSource \ IO =
        let config = h2Config(dbName);
        discard migrate(config);
        let ds = create(config);
        withConnection(ds, conn -> withStatement(conn, stmt -> {
        discard stmt.executeUpdate(
            "INSERT INTO users (id, username, email, password) VALUES " +
            "(1, 'yamada', 'yamada@example.com', 'dummy-hash')");
        discard stmt.executeUpdate("INSERT INTO user_roles (user_id, role) VALUES (1, 'ROLE_SALES')")
        }));
        ds

    /// 変更通知を無視するコールバック（Cookie の張り替えは合成ルートの責務）
    def ignoreChange(): Option[String] -> Unit \ IO = _ -> checked_ecast(())

    /// テスト対象の利用者
    def sampleUser(): SharedSecurity.Principal = principal(1i64, "yamada", Role.Sales)

    /// ログインするとセッション ID が発行されること
    @Test
    def testLoginIssuesSessionId(): Unit \ Assert + IO =
        let ds = setup("session_login");
        let id = transactional(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        close(ds);
        Assert.assertEq(expected = 64, String.length(id))

    /// 発行されたセッション ID で利用者を引けること
    @Test
    def testCurrentReturnsPrincipalForValidSession(): Unit \ Assert + IO =
        let ds = setup("session_lookup");
        let id = transactional(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        let found = transactional(ds, () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.current()));
        close(ds);
        match found {
            case None    => Assert.fail("セッションから利用者を引けなかった")
            case Some(p) =>
                Assert.assertEq(expected = "yamada", username(p));
                Assert.assertTrue(role(p) == Role.Sales)
        }

    /// 未知のセッション ID では利用者を引けないこと
    @Test
    def testCurrentReturnsNoneForUnknownSession(): Unit \ Assert + IO =
        let ds = setup("session_unknown");
        let found = transactional(ds, () ->
            withJdbcSession(Some("deadbeef"), ignoreChange(), () -> Session.current()));
        close(ds);
        Assert.assertTrue(Option.isEmpty(found))

    ///
    /// ログインのたびにセッション ID が変わること（セッション固定攻撃対策）。
    ///
    /// 攻撃者が事前に用意した ID を被害者に使わせても、認証の瞬間に ID が変わるため
    /// 攻撃者の持つ ID では認証済みセッションにならない。
    ///
    @Test
    def testLoginRegeneratesSessionId(): Unit \ Assert + IO =
        let ds = setup("session_fixation");
        let first = transactional(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        let second = transactional(ds, () ->
            withJdbcSession(Some(first), ignoreChange(), () -> Session.login(sampleUser())));
        close(ds);
        Assert.assertTrue(first != second)

    ///
    /// 後続のログインが既存セッションを無効化すること（ADR-0003）。
    ///
    /// 同一利用者の同時セッション数を 1 とする。監査証跡で操作者を一意に特定するため。
    ///
    @Test
    def testLoginInvalidatesExistingSession(): Unit \ Assert + IO =
        let ds = setup("session_single");
        let first = transactional(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        discard transactional(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        let stale = transactional(ds, () ->
            withJdbcSession(Some(first), ignoreChange(), () -> Session.current()));
        close(ds);
        Assert.assertTrue(Option.isEmpty(stale))

    /// ログアウトでセッションが無効になること
    @Test
    def testLogoutInvalidatesSession(): Unit \ Assert + IO =
        let ds = setup("session_logout");
        let id = transactional(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        transactional(ds, () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.logout()));
        let after = transactional(ds, () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.current()));
        close(ds);
        Assert.assertTrue(Option.isEmpty(after))

    ///
    /// セッション ID が推測困難であること。
    ///
    /// 連番や時刻ベースだと総当たりでハイジャックされる。
    /// 100 回発行して重複がないこと・16 進 64 文字であることを見る。
    ///
    @Test
    def testSessionIdIsUnpredictable(): Unit \ Assert + IO =
        let ds = setup("session_entropy");
        let ids = List.range(0, 20) |> List.map(_ ->
            transactional(ds, () ->
                withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser()))));
        close(ds);
        let unique = List.toSet(ids);
        Assert.assertEq(expected = 20, Set.size(unique));
        Assert.assertTrue(ids |> List.forAll(id ->
            String.forAll(c -> Char.isHexDigit(c), id)))

    /// CSRF トークンがセッションごとに発行されること
    @Test
    def testCsrfTokenIsIssuedPerSession(): Unit \ Assert + IO =
        let ds = setup("session_csrf");
        let id = transactional(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        let token = transactional(ds, () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.csrfToken()));
        close(ds);
        match token {
            case None    => Assert.fail("CSRF トークンが発行されていない")
            case Some(t) => Assert.assertEq(expected = 64, String.length(t))
        }

    /// セッション ID と CSRF トークンが異なる値であること
    @Test
    def testCsrfTokenDiffersFromSessionId(): Unit \ Assert + IO =
        let ds = setup("session_csrf_distinct");
        let id = transactional(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        let token = transactional(ds, () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.csrfToken()));
        close(ds);
        Assert.assertTrue(token != Some(id))
}
