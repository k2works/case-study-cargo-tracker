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
                         lockedMessage, disabledMessage, timeoutMessage}

    ///
    /// `GET /login`。
    ///
    /// `?timeout` が付いていればセッション切れの案内を出す（UI 設計の仕様）。
    ///
    pub def loginPage(req: Request): Response \ Session =
        let message = match queryParam(req, "timeout") {
            case Some(_) => Some(timeoutMessage())
            case None    => None
        };
        html(renderLogin(Session.csrfToken(), "", message))

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
        match authenticate(name, plainPassword, nowMillis) {
            case AuthResult.Success            => redirect("/")
            case AuthResult.Locked             => failure(name, lockedMessage())
            case AuthResult.Disabled           => failure(name, disabledMessage())
            case AuthResult.InvalidCredentials => failure(name, invalidCredentialsMessage())
        }

    /// 失敗時の応答（200 で画面を返す）
    def failure(name: String, message: String): Response \ Session =
        html(renderLogin(Session.csrfToken(), name, Some(message)))

    ///
    /// `POST /logout`。
    ///
    /// GET ではなく POST にするのは、第三者のリンク踏ませでログアウトが
    /// 発火しないようにするためである（CSRF トークンも検証される）。
    ///
    pub def logout(_req: Request): Response \ Session =
        endSession();
        redirect("/login")

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
