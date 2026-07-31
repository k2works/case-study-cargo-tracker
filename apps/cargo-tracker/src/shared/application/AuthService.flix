///
/// 認証のユースケース。
///
/// 効果（`UserRepo`・`Password`・`Session`）のみに依存し、DB も HTTP も知らない。
/// ハンドラを差し替えれば、DB を起動せずに認証の判断そのものを検証できる。
///
mod SharedAuthService {
    use SharedPassword.Password
    use SharedSecurity.principal
    use SharedSession.Session
    use SharedUserRepo.UserRepo
    use SharedUserRepo.{UserRecord, userId, username, passwordHash, enabled, role,
                        lockedUntilMillis}

    ///
    /// 認証の結果。
    ///
    /// 失敗の理由を型で分けるのは、**画面に出す文言を変えるため**ではなく、
    /// ログに残す情報を変えるためである。利用者への文言は、
    /// アカウントの存否を漏らさないよう一律にする。
    ///
    pub enum AuthResult with Eq {
        /// 認証成功
        case Success
        /// ユーザー名またはパスワードが違う
        case InvalidCredentials
        /// アカウントがロックされている
        case Locked
        /// アカウントが無効化されている
        case Disabled
    }

    ///
    /// ログインを試みる。
    ///
    /// 判定の順序に意味がある。
    ///
    /// 1. **利用者が存在しない場合もパスワード検証と同じ経路を通す**わけではないが、
    ///    画面に出す文言は同一にする（存在するユーザー名を総当たりで特定させない）
    /// 2. ロックの判定をパスワード検証より先に行う。ロック中に正しいパスワードで
    ///    入っても通してはならない
    /// 3. 無効化の判定もパスワード検証より先に行う
    ///
    pub def login(name: String, plainPassword: String, nowMillis: Int64): AuthResult \
            UserRepo + Password + Session =
        match UserRepo.findByUsername(name) {
            case None => AuthResult.InvalidCredentials
            case Some(user) =>
                if (not enabled(user))
                    AuthResult.Disabled
                else if (isLocked(user, nowMillis))
                    AuthResult.Locked
                else if (Password.verify(plainPassword, passwordHash(user))) {
                    UserRepo.resetFailures(userId(user));
                    discard Session.login(principal(userId(user), username(user), role(user)));
                    AuthResult.Success
                } else {
                    UserRepo.recordFailure(userId(user));
                    AuthResult.InvalidCredentials
                }
        }

    ///
    /// ロック中かどうか。
    ///
    /// 解除時刻を過ぎていれば自動的に解除される（30 分。非機能要件 4.1）。
    /// 明示的な解除処理を持たないのは、時刻の比較だけで済むうえ、
    /// 解除漏れという状態を作らないためである。
    ///
    pub def isLocked(user: UserRecord, nowMillis: Int64): Bool =
        lockedUntilMillis(user) > nowMillis

    /// ログアウトする
    pub def logout(): Unit \ Session =
        Session.logout()
}
