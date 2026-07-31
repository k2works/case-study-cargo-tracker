///
/// 認証のルートハンドラ（入力アダプタ）。
///
/// リクエストから値を取り出し、ユースケースを呼び、画面関数の結果を返す。
/// 判断（認証の成否・ロックの有無）はユースケース側にあり、ここには持たない。
///
mod SharedAuthRoutes {
    import java.time.Instant

    use SharedAuthService.{AuthResult, login => authenticate, logout => endSession}
    use SharedHttp.{Request, Response, formParam, html, queryParam}
    use SharedHttpAuth.redirect
    use SharedPassword.Password
    use SharedSession.Session
    use SharedUserRepo.UserRepo
    use SharedAuthPages.{renderLogin, renderDashboard, invalidCredentialsMessage,
                         lockedMessage, disabledMessage, timeoutMessage,
                         supersededMessage, loggedOutMessage}

    ///
    /// `GET /login`。
    ///
    /// `?timeout` が付いていればセッション切れの案内を出す（UI 設計の仕様）。
    ///
    pub def loginPage(req: Request): Response \ Session + IO =
        html(renderLogin(Session.csrfToken(), "", noticeOf(req), prefill()))

    ///
    /// クエリパラメータから案内文を決める。
    ///
    /// 理由ごとに文言を分けるのは、利用者が「なぜログイン画面に戻されたか」を
    /// 誤解しないためである。
    ///
    def noticeOf(req: Request): Option[String] =
        if (not Option.isEmpty(queryParam(req, "timeout")))
            Some(timeoutMessage())
        else if (not Option.isEmpty(queryParam(req, "superseded")))
            Some(supersededMessage())
        else if (not Option.isEmpty(queryParam(req, "loggedout")))
            Some(loggedOutMessage())
        else
            None

    ///
    /// 開発環境の既定資格情報。
    ///
    /// 本番では `None` を返す。判定は `SharedAppEnv` に集約されており、
    /// ここで環境変数を直接見ない（判定が散ると片方だけ直す漏れが起きる）。
    ///
    def prefill(): Option[(String, String)] \ IO =
        if (SharedAppEnv.isDevelopment()) Some(SharedAppEnv.devCredentials()) else None

    ///
    /// `POST /login`。
    ///
    /// 成功時はダッシュボードへリダイレクトする（PRG パターン）。ログイン結果を
    /// そのまま描画すると、再読み込みでフォームが再送信される。
    ///
    /// 失敗時は入力されたユーザー名をフォームへ戻す。打ち直しを強いないためであり、
    /// パスワードは戻さない（HTML に書き出さない）。
    ///
    pub def loginSubmit(req: Request): Response \ UserRepo + Password + Session + IO =
        let name = formParam(req, "username") |> Option.getWithDefault("");
        let plainPassword = formParam(req, "password") |> Option.getWithDefault("");
        let nowMillis = Instant.now().toEpochMilli();
        let result = authenticate(name, plainPassword, nowMillis);
        audit(name, result);
        match result {
            case AuthResult.Success            => redirect("/")
            case AuthResult.Locked             => failure(name, lockedMessage())
            case AuthResult.Disabled           => failure(name, disabledMessage())
            case AuthResult.InvalidCredentials => failure(name, invalidCredentialsMessage())
        }

    ///
    /// ログイン試行を記録する（US26 受入基準 6）。
    ///
    /// **パスワードは記録しない**。失敗の理由は型で分かれているため、
    /// 平文を残さずとも運用で必要な情報は揃う。
    ///
    /// 標準出力への 1 行出力に留める。ログ集約基盤は本プロジェクトのスコープ外であり、
    /// 実運用ではコンテナの標準出力を収集する前提とする。
    ///
    def audit(name: String, result: AuthResult): Unit \ IO =
        let outcome = match result {
            case AuthResult.Success            => "success"
            case AuthResult.InvalidCredentials => "invalid_credentials"
            case AuthResult.Locked             => "locked"
            case AuthResult.Disabled           => "disabled"
        };
        println("event=login username=${sanitize(name)} outcome=${outcome}")

    ///
    /// ログへ出す値から行の構造を壊す文字を取り除く。
    ///
    /// ユーザー名はフォーム入力そのままであり、改行や `outcome=success` を含む値を
    /// 送られると、ログ 1 行の構造を破って偽の成功イベントを注入できる。
    /// 監査証跡を ADR-0003 の根拠にしている以上、行の完全性は設計上の関心事である。
    ///
    def sanitize(value: String): String =
        value |> String.replace(src = "\n", dst = " ")
              |> String.replace(src = "\r", dst = " ")
              |> String.replace(src = " ", dst = "_")

    /// 失敗時の応答（200 で画面を返す）
    def failure(name: String, message: String): Response \ Session =
        // 失敗後は既定値を入れない。入力し直した値を尊重する
        html(renderLogin(Session.csrfToken(), name, Some(message), None))

    ///
    /// `POST /logout`。
    ///
    /// GET ではなく POST にするのは、第三者のリンク踏ませでログアウトが
    /// 発火しないようにするためである（CSRF トークンも検証される）。
    ///
    pub def logout(_req: Request): Response \ Session + IO =
        let name = Session.current() |> Option.map(SharedSecurity.username) |> Option.getWithDefault("");
        endSession();
        println("event=logout username=${sanitize(name)}");
        redirect("/login?loggedout")

    ///
    /// `GET /`（ダッシュボード）。
    ///
    /// 認可は合成ルートの関門が担うため、ここへ到達した時点で認証済みである。
    /// それでも `None` の分岐を残すのは、関門を外した変更が入ったときに
    /// 未認証の情報が漏れるより、ログインへ戻す方が安全だからである。
    ///
    pub def dashboard(_req: Request): Response \ Session =
        match Session.current() {
            case Some(p) => html(renderDashboard(p, Session.csrfToken()))
            case None    => redirect("/login")
        }
}
