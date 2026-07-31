///
/// 実行環境の判定。
///
/// 開発（H2）と本番（PostgreSQL）の区別を 1 箇所に集約する。
/// 判定が各所へ散ると、**片方だけ直して本番で開発向けの挙動が残る**という
/// 最も危険な形の漏れが起きる。
///
mod SharedAppEnv {
    import java.lang.System

    ///
    /// 開発環境かどうか。
    ///
    /// `DATABASE_URL` が未設定なら H2（開発）とみなす。`Main` の接続設定と同じ判定であり、
    /// **開発向けの挙動は必ずこの判定を通す**。
    ///
    pub def isDevelopment(): Bool \ IO =
        Object.isNull(System.getenv("DATABASE_URL"))

    ///
    /// 開発用の既定資格情報（ログイン名・パスワード）。
    ///
    /// 開発時にログイン画面へ初期値として入れる。毎回の手入力は、認証を挟んだ画面を
    /// 確認するたびに発生する摩擦であり、確認そのものを避ける動機になる。
    ///
    /// **本番では絶対に使わない**。`isDevelopment()` が真のときにのみ参照すること。
    /// 値は `SharedDbDevSeed.seedUsers` が投入する利用者と一致させる。
    ///
    pub def devCredentials(): (String, String) = ("yamada", "password")
}
