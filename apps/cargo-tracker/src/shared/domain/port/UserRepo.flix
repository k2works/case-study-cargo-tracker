///
/// 利用者の参照とログイン試行の記録（ポート）。
///
/// 認証は業務ドメインではなく共有カーネルの関心事として扱う。
/// どの Bounded Context にも属さず、すべての BC の入口で必要になるためである。
///
mod SharedUserRepo {
    use SharedSecurity.Role

    ///
    /// 認証に必要な利用者の情報。
    ///
    /// `Principal` と分けているのは、**パスワードハッシュを画面やログへ運ばない**ためである。
    /// 認証が済んだ時点で `Principal` へ変換し、以降はそちらだけを扱う。
    ///
    pub enum UserRecord({
        userId = Int64,
        username = String,
        passwordHash = String,
        enabled = Bool,
        role = Role,
        failedAttempts = Int32,
        lockedUntilMillis = Int64
    })

    /// 利用者 ID
    pub def userId(u: UserRecord): Int64 =
        let UserRecord.UserRecord(x) = u;
        x#userId

    /// ログイン名
    pub def username(u: UserRecord): String =
        let UserRecord.UserRecord(x) = u;
        x#username

    /// パスワードハッシュ
    pub def passwordHash(u: UserRecord): String =
        let UserRecord.UserRecord(x) = u;
        x#passwordHash

    /// アカウントが有効か
    pub def enabled(u: UserRecord): Bool =
        let UserRecord.UserRecord(x) = u;
        x#enabled

    /// ロール
    pub def role(u: UserRecord): Role =
        let UserRecord.UserRecord(x) = u;
        x#role

    /// 連続失敗回数
    pub def failedAttempts(u: UserRecord): Int32 =
        let UserRecord.UserRecord(x) = u;
        x#failedAttempts

    /// ロック解除時刻（ミリ秒。ロックされていなければ 0）
    pub def lockedUntilMillis(u: UserRecord): Int64 =
        let UserRecord.UserRecord(x) = u;
        x#lockedUntilMillis

    /// 利用者を組み立てる
    pub def userRecord(id: Int64, name: String, hash: String, isEnabled: Bool,
                       r: Role, failures: Int32, lockedUntil: Int64): UserRecord =
        UserRecord.UserRecord({
            userId = id, username = name, passwordHash = hash, enabled = isEnabled,
            role = r, failedAttempts = failures, lockedUntilMillis = lockedUntil
        })

    ///
    /// 利用者リポジトリ効果。
    ///
    /// 失敗回数の増減をリポジトリに置くのは、`users` の行に対する更新であり
    /// 集約の外で数えても意味がないためである。
    ///
    pub eff UserRepo {
        /// ログイン名で利用者を引く
        def findByUsername(name: String): Option[UserRecord]

        /// ログイン失敗を記録する（5 回でロックする）
        def recordFailure(userId: Int64): Unit

        /// ログイン成功として失敗回数をリセットする
        def resetFailures(userId: Int64): Unit
    }
}
