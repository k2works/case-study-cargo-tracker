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
    /// `APP_ENV=development` を**明示したときだけ**開発とみなす。
    ///
    /// 「`DATABASE_URL` が未設定なら開発」という判定はフェイルオープンであり、
    /// 本番で環境変数の注入に失敗した場合や、接続設定のキー名を変えた場合に、
    /// **本番のログイン画面へ開発用の資格情報が印字される**。
    /// 未設定・未知の値は本番として扱う（フェイルクローズ）。
    ///
    pub def isDevelopment(): Bool \ IO =
        let value = System.getenv("APP_ENV");
        not Object.isNull(value) and value == "development"

    ///
    /// Cookie に `Secure` を付けるか。
    ///
    /// 開発判定と別の変数で決めるのは、**DB の接続先と HTTPS 終端の有無が独立に決まる**
    /// ためである（HTTPS を張ったステージングで H2 を使うこともありうる）。
    /// 既定で付けないのは、HTTP の開発環境で付けるとブラウザが Cookie を送らず、
    /// ログインしても即座に未認証へ戻るためである。
    ///
    ///
    /// 応答に診断用ヘッダ（`X-Route`）を付けるか。
    ///
    /// **`isDevelopment` と分ける**。IT5 では開発判定に相乗りさせたが、
    /// `APP_ENV` を設定しているのは開発サーバと E2E だけで、
    /// **間欠異常が実際に観測されたフル テストでは無効**だった。
    /// 診断を有効にするかは、開発かどうかとは独立に決まる
    /// （`isSecureCookie` を `isDevelopment` から分けたのと同じ理由）。
    ///
    /// 既定は無効。本番で経路の内部名を晒す理由はない。
    ///
    pub def isRouteTraceEnabled(): Bool \ IO =
        let value = System.getenv("APP_TRACE_ROUTE");
        (not Object.isNull(value) and value == "true") or isDevelopment()

    pub def isSecureCookie(): Bool \ IO =
        let value = System.getenv("APP_SECURE_COOKIE");
        not Object.isNull(value) and value == "true"

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
