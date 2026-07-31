///
/// セッション管理（ポート）。
///
/// 「今のリクエストが誰のものか」を扱う操作を効果として宣言する。
/// 保存先（DB / インメモリ / ElastiCache）はハンドラ側に閉じる。
///
/// セッションは**リクエスト単位の文脈**であるため、効果として表現するのが素直である。
/// 引数で持ち回すと、経路のすべての関数がセッションを知ることになる。
///
mod SharedSession {
    use SharedSecurity.Principal

    ///
    /// セッション効果。
    ///
    /// `current` が `Option` を返すのは、未認証が異常ではなく通常の状態だからである
    /// （公開追跡は未認証で使う）。
    ///
    pub eff Session {
        /// 現在のリクエストの利用者
        def current(): Option[Principal]

        ///
        /// ログインしてセッションを開始する。
        ///
        /// 既存のセッションがあれば破棄してから作る。**セッション固定攻撃対策**であり、
        /// 同時に同一利用者の既存セッションも無効化する（ADR-0003）。
        ///
        def login(principal: Principal): String

        /// ログアウトしてセッションを破棄する
        def logout(): Unit

        /// 現在のセッションの CSRF トークン
        def csrfToken(): Option[String]
    }
}
