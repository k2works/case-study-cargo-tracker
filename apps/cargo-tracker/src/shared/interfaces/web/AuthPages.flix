///
/// ログイン画面とダッシュボード（US26・US27）。
///
/// 画面関数は純粋関数として書く。render 結果を直接検証できる。
///
mod SharedAuthPages {
    use SharedHtml.{element, text, attr, class_, id, empty}
    use SharedHtml.Html
    use SharedHtmlComponents.{form, formField, alert}
    use SharedHtmlLayout.renderPage
    use SharedSecurity.{Principal, Role}
    use SharedSecurity.{username, role, roleLabel}

    ///
    /// 認証失敗時の文言。
    ///
    /// 「利用者が存在しない」と「パスワードが違う」を区別しない。区別すると、
    /// 存在するユーザー名を総当たりで特定できてしまう。
    ///
    pub def invalidCredentialsMessage(): String = "ユーザー名またはパスワードが正しくありません"

    /// アカウントロック時の文言（US26 受入基準 4 の「通知」）
    pub def lockedMessage(): String =
        "アカウントが一時的にロックされています。30 分後に再度お試しください"

    /// 無効化されたアカウントの文言（US26 受入基準 5）
    pub def disabledMessage(): String =
        "このアカウントは利用できません。管理者にお問い合わせください"

    /// セッションタイムアウト時の文言
    pub def timeoutMessage(): String =
        "一定時間操作がなかったため、ログアウトしました。再度ログインしてください"

    /// ログインフォーム
    def loginForm(csrfToken: Option[String], enteredName: String): Html =
        form("/login", "post", csrfToken,
            formField("username", "ユーザー名", enteredName, Nil) ::
            passwordField() ::
            element("button", class_("btn btn-primary") :: attr("type", "submit") :: Nil,
                text("ログイン") :: Nil) :: Nil)

    ///
    /// パスワードの入力欄。
    ///
    /// `Components.formField` を使わないのは `type="password"` が必要なためである。
    /// 入力値を `value` に戻さないのは、パスワードを HTML へ書き出さないためである。
    ///
    def passwordField(): Html =
        element("div", class_("mb-3") :: Nil,
            element("label", attr("for", "password") :: class_("form-label") :: Nil,
                text("パスワード") :: Nil) ::
            element("input",
                attr("type", "password") :: id("password") :: attr("name", "password") ::
                attr("autocomplete", "current-password") :: class_("form-control") :: Nil, Nil) :: Nil)

    /// ログイン画面
    pub def login(csrfToken: Option[String], enteredName: String, message: Option[String]): Html =
        element("div", class_("row justify-content-center") :: Nil,
            element("div", class_("col-md-6") :: Nil,
                element("h1", class_("h4 mb-3") :: Nil, text("CargoTracker") :: Nil) ::
                element("p", class_("text-muted") :: Nil,
                    text("国際貨物輸送管理システム") :: Nil) ::
                messageBlock(message) ::
                loginForm(csrfToken, enteredName) ::
                element("p", class_("text-muted small") :: Nil,
                    text("アカウントがない場合は管理者に連絡してください") :: Nil) :: Nil) :: Nil)

    /// メッセージがあれば表示する
    def messageBlock(message: Option[String]): Html = match message {
        case None      => empty()
        case Some(msg) => alert("danger", msg)
    }

    /// ログイン画面を描画する
    pub def renderLogin(csrfToken: Option[String], enteredName: String,
                        message: Option[String]): String =
        renderPage("ログイン", None, "", csrfToken, login(csrfToken, enteredName, message))

    ///
    /// ダッシュボード。
    ///
    /// サマリーカード（予約件数・輸送中・未払い請求）は、各 Bounded Context が
    /// 実装されて初めて表示できる。IT3 では**ロール別の作業入口**のみを出す
    /// （[UI 設計](../../../../../../docs/design/ui_design.md) に段階的実装として明記）。
    ///
    pub def dashboard(p: Principal): Html =
        element("div", Nil,
            element("h1", class_("h4 mb-3") :: Nil, text("ダッシュボード") :: Nil) ::
            element("p", class_("text-muted") :: Nil,
                text("${username(p)} さん（${roleLabel(role(p))}）としてログインしています") :: Nil) ::
            element("p", class_("text-muted small") :: Nil,
                text("上部のメニューから業務を選択してください") :: Nil) :: Nil)

    /// ダッシュボードを描画する
    pub def renderDashboard(p: Principal, csrfToken: Option[String]): String =
        renderPage("ダッシュボード", Some(p), "/", csrfToken, dashboard(p))
}
