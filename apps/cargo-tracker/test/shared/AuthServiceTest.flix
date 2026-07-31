///
/// 認証ユースケースの単体テスト。
///
/// `AuthService.login` は「判定の順序に意味がある」と宣言しているが、IT3 の実装時点では
/// その順序を固定するテストがなく、DB 込みの統合テストで間接的に触れているだけだった。
/// **順序が壊れると「ロック中でも正しいパスワードなら入れる」といった穴が開く**ため、
/// 効果ハンドラを差し替えて DB なしで直接検証する。
///
mod AuthServiceTest {
    use SharedAuthService.{AuthResult, login, isLocked}
    use SharedPassword.Password
    use SharedSecurity.{Role, Principal}
    use SharedSession.Session
    use SharedUserRepo.UserRepo
    use SharedUserRepo.{UserRecord, userRecord}

    def correctPassword(): String = "correct horse battery staple"
    def storedHash(): String = "hash-of-correct-password"

    /// 時刻の基準（ミリ秒）
    def now(): Int64 = 1_000_000i64

    ///
    /// テスト用の利用者。
    ///
    /// `enabled` / `lockedUntil` を引数にするのは、**状態を直接与えて判定の順序を見る**ため。
    /// 「5 回失敗させる」経路で作ると、経路の副産物と判定そのものを区別できない。
    ///
    def user(isEnabled: Bool, lockedUntil: Int64): UserRecord =
        userRecord(1i64, "yamada", storedHash(), isEnabled, Role.Sales, 0, lockedUntil)

    /// パスワード効果のインメモリ実装（保存ハッシュと一致する平文だけを受理する）
    def withStubPassword(f: Unit -> a \ ef + Password): a \ ef - Password =
        run {
            f()
        } with handler Password {
            def verify(plain, hashed, k) = k(hashed == storedHash() and plain == correctPassword())
            def hash(_plain, k) = k(storedHash())
        }

    /// 利用者リポジトリのインメモリ実装（記録された操作も返す）
    def withStubUserRepo(found: Option[UserRecord], recorded: Ref[List[String], Static],
                         f: Unit -> a \ ef + UserRepo): a \ (ef - UserRepo) + IO =
        run {
            f()
        } with handler UserRepo {
            def findByUsername(_name, k) = k(found)
            def recordFailure(_id, k) = {
                Ref.put("failure" :: Ref.get(recorded), recorded);
                k()
            }
            def resetFailures(_id, k) = {
                Ref.put("reset" :: Ref.get(recorded), recorded);
                k()
            }
        }

    /// セッション効果のインメモリ実装（ログインの発生を記録する）
    def withStubSession(loggedIn: Ref[Bool, Static], f: Unit -> a \ ef + Session): a \ (ef - Session) + IO =
        run {
            f()
        } with handler Session {
            def current(k) = k(None)
            def csrfToken(k) = k(None)
            def terminationReason(k) = k(None)
            def login(_p, k) = {
                Ref.put(true, loggedIn);
                k("session-id")
            }
            def logout(k) = k()
        }

    /// 認証を実行し、結果と副作用を返す
    def attempt(found: Option[UserRecord], plain: String, nowMillis: Int64):
            (AuthResult, Bool, List[String]) \ IO =
        let recorded = Ref.fresh(Static, Nil);
        let loggedIn = Ref.fresh(Static, false);
        let result = withStubUserRepo(found, recorded, () ->
            withStubSession(loggedIn, () ->
                withStubPassword(() -> login("yamada", plain, nowMillis))));
        (result, Ref.get(loggedIn), Ref.get(recorded))

    /// 正しい資格情報で成功し、失敗回数がリセットされ、セッションが作られること
    @Test
    def testSucceedsWithCorrectPassword(): Unit \ Assert + IO =
        let (result, loggedIn, recorded) = attempt(Some(user(true, 0i64)), correctPassword(), now());
        Assert.assertTrue(result == AuthResult.Success);
        Assert.assertTrue(loggedIn);
        Assert.assertTrue(List.memberOf("reset", recorded))

    /// 誤ったパスワードで失敗し、失敗が記録され、セッションが作られないこと
    @Test
    def testRecordsFailureForWrongPassword(): Unit \ Assert + IO =
        let (result, loggedIn, recorded) = attempt(Some(user(true, 0i64)), "wrong", now());
        Assert.assertTrue(result == AuthResult.InvalidCredentials);
        Assert.assertTrue(not loggedIn);
        Assert.assertTrue(List.memberOf("failure", recorded))

    ///
    /// ロック中は正しいパスワードでも通らないこと（判定の順序 2）。
    ///
    /// これが本テストの中心である。パスワード検証を先に置くと、ロック中でも
    /// 正しいパスワードを知っている攻撃者は入れてしまう。
    ///
    @Test
    def testRejectsCorrectPasswordWhileLocked(): Unit \ Assert + IO =
        let (result, loggedIn, _) = attempt(Some(user(true, now() + 1i64)), correctPassword(), now());
        Assert.assertTrue(result == AuthResult.Locked);
        Assert.assertTrue(not loggedIn)

    ///
    /// 無効化されたアカウントは正しいパスワードでも通らないこと（判定の順序 3）。
    ///
    /// ロックより先に判定する。無効化は恒久、ロックは一時であり、
    /// 恒久的な状態の方を優先して案内する。
    ///
    @Test
    def testRejectsDisabledAccountBeforePasswordCheck(): Unit \ Assert + IO =
        let (result, loggedIn, recorded) = attempt(Some(user(false, 0i64)), correctPassword(), now());
        Assert.assertTrue(result == AuthResult.Disabled);
        Assert.assertTrue(not loggedIn);
        // 無効化アカウントでは失敗回数を数えない（数えるとロックと二重の状態になる）
        Assert.assertTrue(not List.memberOf("failure", recorded))

    ///
    /// 存在しない利用者でもパスワード照合を通ること（ユーザー列挙の防止）。
    ///
    /// 照合を省くと、応答時間の差でユーザー名の存否が判別できる。文言を揃えても
    /// タイミングで漏れては意味がない。
    ///
    @Test
    def testVerifiesPasswordEvenForUnknownUser(): Unit \ Assert + IO =
        let verified = Ref.fresh(Static, false);
        let recorded = Ref.fresh(Static, Nil);
        let loggedIn = Ref.fresh(Static, false);
        let result = withStubUserRepo(None, recorded, () ->
            withStubSession(loggedIn, () ->
                run {
                    login("nobody", correctPassword(), now())
                } with handler Password {
                    def verify(_plain, _hashed, k) = {
                        Ref.put(true, verified);
                        k(false)
                    }
                    def hash(_plain, k) = k(storedHash())
                }));
        Assert.assertTrue(result == AuthResult.InvalidCredentials);
        Assert.assertTrue(Ref.get(verified))

    // ---- ロック解除の境界値（30 分） ----

    /// 解除時刻の直前はロック中であること
    @Test
    def testLockedJustBeforeExpiry(): Unit \ Assert =
        Assert.assertTrue(isLocked(user(true, now() + 1i64), now()))

    ///
    /// 解除時刻ちょうどは解除されていること。
    ///
    /// 判定が `>` であることを固定する。`>=` にすると 1 ミリ秒だけ長くロックされる。
    ///
    @Test
    def testUnlockedAtExactExpiry(): Unit \ Assert =
        Assert.assertTrue(not isLocked(user(true, now()), now()))

    /// 解除時刻を過ぎていれば解除されていること（恒久ロックのバグを防ぐ）
    @Test
    def testUnlockedAfterExpiry(): Unit \ Assert + IO =
        let (result, loggedIn, _) = attempt(Some(user(true, now() - 1i64)), correctPassword(), now());
        Assert.assertTrue(result == AuthResult.Success);
        Assert.assertTrue(loggedIn)

    /// ロックされていない利用者（解除時刻 0）は通常判定であること
    @Test
    def testNotLockedWhenNeverLocked(): Unit \ Assert =
        Assert.assertTrue(not isLocked(user(true, 0i64), now()))
}
