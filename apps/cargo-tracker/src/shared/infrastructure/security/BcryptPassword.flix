///
/// `Password` 効果の BCrypt 実装（本番アダプタ）。
///
/// コスト 12 は [非機能要件](../../../../../../docs/design/non_functional.md) 4.1 の指定による。
/// 総当たりに要する時間を稼ぐためのパラメータであり、下げてはならない。
///
/// ハッシュ計算は 1 回あたり数百ミリ秒かかる。ログイン以外の経路でこの効果を要求しないこと。
///
mod SharedBcryptPassword {
    import org.mindrot.jbcrypt.BCrypt

    use SharedPassword.Password

    /// BCrypt のコスト（2^12 回のストレッチ）
    def cost(): Int32 = 12

    ///
    /// BCrypt のハッシュとして妥当な形式か。
    ///
    /// 不正な形式（空文字・切り詰められた値）を `checkpw` に渡すと jBCrypt は例外を投げる。
    /// **例外に頼らず事前に検査する**のは、Flix の `try/catch` がネストしたラムダで
    /// 不正なバイトコードを生成する不具合があり、テスト実行時に初めて失敗するためである
    /// （手順書 7.5 の制約 12）。
    ///
    /// 形式は `$2a$12$` + 53 文字（ソルト 22 + ハッシュ 31）で全体 60 文字。
    ///
    def isValidHashFormat(hashed: String): Bool =
        String.length(hashed) == 60 and
        (String.startsWith(prefix = "$2a$", hashed) or
         String.startsWith(prefix = "$2b$", hashed) or
         String.startsWith(prefix = "$2y$", hashed))

    ///
    /// 平文とハッシュを照合する。
    ///
    /// DB の値が壊れている場合は 500 ではなく「認証失敗」として扱う。
    /// **形式が不正なら常に false であり、認証が素通りすることはない。**
    ///
    def verifyPassword(plain: String, hashed: String): Bool \ IO =
        if (not isValidHashFormat(hashed))
            false
        else
            BCrypt.checkpw(plain, hashed)

    /// 平文をハッシュ化する
    def hashPassword(plain: String): String \ IO =
        BCrypt.hashpw(plain, BCrypt.gensalt(cost()))

    ///
    /// `Password` 効果に BCrypt 実装を注入する。
    ///
    /// 合成ルートとテストからのみ適用する（arch-lint 規約 5）。
    ///
    pub def withBcryptPassword(f: Unit -> a \ ef + Password): a \ (ef - Password) + IO =
        run {
            f()
        } with handler Password {
            def verify(plain, hashed, k) = k(verifyPassword(plain, hashed))
            def hash(plain, k) = k(hashPassword(plain))
        }
}
