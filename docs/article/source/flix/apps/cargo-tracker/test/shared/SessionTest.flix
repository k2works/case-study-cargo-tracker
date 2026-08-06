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
    use SharedSystemClock.withSystemClock
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

    ///
    /// システム時刻でトランザクションを実行する。
    ///
    /// `Clock` を効果にしたため、`transactional` だけでは効果が解決されない。
    /// 時刻に依存しないテストではシステム時刻をそのまま使う。
    ///
    def inTx(ds: DataSource, f: Unit -> a \ ef + SharedDbTx.Tx + SharedClock.Clock): a \ (ef - SharedDbTx.Tx - SharedClock.Clock) + IO =
        withSystemClock(() -> transactional(ds, f))

    /// 変更通知を無視するコールバック（Cookie の張り替えは合成ルートの責務）
    def ignoreChange(): Option[String] -> Unit \ IO = _ -> checked_ecast(())

    /// テスト対象の利用者
    def sampleUser(): SharedSecurity.Principal = principal(1i64, "yamada", Role.Sales)

    /// ログインするとセッション ID が発行されること
    @Test
    def testLoginIssuesSessionId(): Unit \ Assert + IO =
        let ds = setup("session_login");
        let id = inTx(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        close(ds);
        Assert.assertEq(expected = 64, String.length(id))

    /// 発行されたセッション ID で利用者を引けること
    @Test
    def testCurrentReturnsPrincipalForValidSession(): Unit \ Assert + IO =
        let ds = setup("session_lookup");
        let id = inTx(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        let found = inTx(ds, () ->
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
        let found = inTx(ds, () ->
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
        let first = inTx(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        let second = inTx(ds, () ->
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
        let first = inTx(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        discard inTx(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        let stale = inTx(ds, () ->
            withJdbcSession(Some(first), ignoreChange(), () -> Session.current()));
        close(ds);
        Assert.assertTrue(Option.isEmpty(stale))

    /// ログアウトでセッションが無効になること
    @Test
    def testLogoutInvalidatesSession(): Unit \ Assert + IO =
        let ds = setup("session_logout");
        let id = inTx(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        inTx(ds, () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.logout()));
        let after = inTx(ds, () ->
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
            inTx(ds, () ->
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
        let id = inTx(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        let token = inTx(ds, () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.csrfToken()));
        close(ds);
        match token {
            case None    => Assert.fail("CSRF トークンが発行されていない")
            case Some(t) => Assert.assertEq(expected = 64, String.length(t))
        }

    // ---- AU-11: ロール別のセッションタイムアウト ----
    //
    // IT3 では「時刻を注入できない」という理由だけで未検証のまま残っていた
    // （IT3 レビュー M5・M10）。`Clock` をポート化したことで検証できるようになった。
    //
    // 非機能要件 4.1: 荷役作業員は 2 時間、その他は 30 分。**最終アクセスから**起算する。

    /// 固定時刻でトランザクションを実行する
    def inTxAt(ds: DataSource, atMillis: Int64,
               f: Unit -> a \ ef + SharedDbTx.Tx + SharedClock.Clock):
            a \ (ef - SharedDbTx.Tx - SharedClock.Clock) + IO =
        // セッションの検証はミリ秒だけを見る。日付は使わないため既定値でよい
        InMemoryHandlers.withFixedClock(atMillis, "2026-09-16", () -> transactional(ds, f))

    /// 基準時刻（2026-09-16 00:00:00 UTC 相当。値そのものに意味はない）
    def baseMillis(): Int64 = 1789430400000i64

    def minutes(n: Int64): Int64 = n * 60000i64

    def handlerUser(): SharedSecurity.Principal = principal(2i64, "taro", Role.Handler)

    /// 荷役作業員を投入する（ロール別タイムアウトの検証に要る）
    def setupWithHandler(dbName: String): DataSource \ IO =
        let ds = setup(dbName);
        withConnection(ds, conn -> withStatement(conn, stmt -> {
        discard stmt.executeUpdate(
            "INSERT INTO users (id, username, email, password) VALUES " +
            "(2, 'taro', 'taro@example.com', 'dummy-hash')");
        discard stmt.executeUpdate("INSERT INTO user_roles (user_id, role) VALUES (2, 'ROLE_HANDLER')")
        }));
        ds

    ///
    /// 一般ロールは 30 分の手前ではセッションが生きていること（境界の下側）。
    ///
    @Test
    def testGeneralRoleAliveJustBeforeTimeout(): Unit \ Assert + IO =
        let ds = setup("session_timeout_sales_alive");
        let id = inTxAt(ds, baseMillis(), () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        let found = inTxAt(ds, baseMillis() + minutes(29i64), () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.current()));
        close(ds);
        Assert.assertTrue(not Option.isEmpty(found))

    ///
    /// 一般ロールは 30 分を過ぎるとセッションが切れること（境界の上側）。
    ///
    /// **31 分ではなく 30 分ちょうども試す**。`>` と `>=` の取り違えは、
    /// 境界のどちら側だけを見ていても気付けない。
    ///
    @Test
    def testGeneralRoleExpiresAfterThirtyMinutes(): Unit \ Assert + IO =
        let ds = setup("session_timeout_sales_expired");
        let id = inTxAt(ds, baseMillis(), () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        let atExactly = inTxAt(ds, baseMillis() + minutes(30i64), () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.current()));
        let after = inTxAt(ds, baseMillis() + minutes(31i64), () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.current()));
        close(ds);
        Assert.assertTrue(Option.isEmpty(atExactly));
        Assert.assertTrue(Option.isEmpty(after))

    ///
    /// 荷役作業員は 30 分では切れず、2 時間まで保つこと。
    ///
    /// 港湾・倉庫での連続作業とバーコードスキャンを考慮した緩和である。
    /// **ロールで値が変わることそのもの**を固定する。一般ロールと同じ値に
    /// 退化していても、荷役作業員のテストがなければ気付けない。
    ///
    @Test
    def testHandlerRoleHasLongerTimeout(): Unit \ Assert + IO =
        let ds = setupWithHandler("session_timeout_handler");
        let id = inTxAt(ds, baseMillis(), () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(handlerUser())));
        let atOneHour = inTxAt(ds, baseMillis() + minutes(60i64), () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.current()));
        close(ds);
        Assert.assertTrue(not Option.isEmpty(atOneHour))

    /// 荷役作業員も 2 時間を過ぎれば切れること
    @Test
    def testHandlerRoleExpiresAfterTwoHours(): Unit \ Assert + IO =
        let ds = setupWithHandler("session_timeout_handler_expired");
        let id = inTxAt(ds, baseMillis(), () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(handlerUser())));
        let after = inTxAt(ds, baseMillis() + minutes(121i64), () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.current()));
        close(ds);
        Assert.assertTrue(Option.isEmpty(after))

    ///
    /// タイムアウトが**最終アクセスから**起算されること。
    ///
    /// 作成時に一度だけ失効時刻を決めると絶対タイムアウトになり、
    /// 連続して操作していても 30 分で切断される。IT3 のレビュー H6 で
    /// 一度作り込んだ欠陥であり、時刻を固定できる今こそ回帰テストにする。
    ///
    @Test
    def testTimeoutIsMeasuredFromLastAccess(): Unit \ Assert + IO =
        let ds = setup("session_timeout_sliding");
        let id = inTxAt(ds, baseMillis(), () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        // 20 分後にアクセスして失効時刻を延ばす
        let mid = inTxAt(ds, baseMillis() + minutes(20i64), () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.current()));
        // 作成から 40 分後。絶対タイムアウトなら切れているが、最終アクセスからなら生きている
        let later = inTxAt(ds, baseMillis() + minutes(40i64), () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.current()));
        close(ds);
        Assert.assertTrue(not Option.isEmpty(mid));
        Assert.assertTrue(not Option.isEmpty(later))

    /// セッション ID と CSRF トークンが異なる値であること
    @Test
    def testCsrfTokenDiffersFromSessionId(): Unit \ Assert + IO =
        let ds = setup("session_csrf_distinct");
        let id = inTx(ds, () ->
            withJdbcSession(None, ignoreChange(), () -> Session.login(sampleUser())));
        let token = inTx(ds, () ->
            withJdbcSession(Some(id), ignoreChange(), () -> Session.csrfToken()));
        close(ds);
        Assert.assertTrue(token != Some(id))
}
