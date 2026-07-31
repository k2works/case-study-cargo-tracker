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

    ///
    /// アカウントロック時の文言（US26 受入基準 4 の「通知」）。
    ///
    /// 「30 分後に」だけでは、本当にパスワードを思い出せない利用者は 30 分待って
    /// また失敗する。本船作業の直前に締め出された荷役作業員にとって 30 分は
    /// 業務が止まる時間であり、待てない業務があることを前提に逃げ道を示す。
    ///
    pub def lockedMessage(): String =
        "アカウントが一時的にロックされています。30 分後に再度お試しいただくか、" +
        "お急ぎの場合は管理者にお問い合わせください"

    /// 無効化されたアカウントの文言（US26 受入基準 5）
    pub def disabledMessage(): String =
        "このアカウントは利用できません。管理者にお問い合わせください"

    /// セッションタイムアウト時の文言
    pub def timeoutMessage(): String =
        "一定時間操作がなかったため、ログアウトしました。再度ログインしてください"

    ///
    /// 別端末のログインでセッションが終了したときの文言。
    ///
    /// 同時セッション数は 1（[ADR-0003](../../../../../../docs/adr/ADR-0003-session-concurrency.md)）。
    /// **タイムアウトの文言を流用してはならない**。実際には放置していないのに
    /// 「操作がなかったため」と出すのは事実と違う案内であり、
    /// 「入力途中の内容が消えた、システムが落ちた」という障害報告につながる。
    ///
    pub def supersededMessage(): String =
        "別の端末でログインされたため、この端末のログインは終了しました。" +
        "同時に複数の端末ではご利用いただけません"

    /// ログアウト完了の文言（共用端末で「切れたか」を確かめられるようにする）
    pub def loggedOutMessage(): String = "ログアウトしました"

    /// ログインフォーム
    def loginForm(csrfToken: Option[String], enteredName: String, prefillPassword: String): Html =
        form("/login", "post", csrfToken,
            formField("username", "ユーザー名", enteredName, Nil) ::
            passwordField(prefillPassword) ::
            element("button", class_("btn btn-primary") :: attr("type", "submit") :: Nil,
                text("ログイン") :: Nil) :: Nil)

    ///
    /// パスワードの入力欄。
    ///
    /// `Components.formField` を使わないのは `type="password"` が必要なためである。
    ///
    /// **認証失敗時に入力値を戻さない**。パスワードを HTML へ書き出さないためである。
    /// 唯一の例外が開発環境の初期値（`prefill`）で、これは開発用の既知の値であり、
    /// かつ `SharedAppEnv.isDevelopment()` が真のときにしか渡されない。
    ///
    def passwordField(prefill: String): Html =
        let value = if (prefill == "") Nil else attr("value", prefill) :: Nil;
        element("div", class_("mb-3") :: Nil,
            element("label", attr("for", "password") :: class_("form-label") :: Nil,
                text("パスワード") :: Nil) ::
            element("input",
                attr("type", "password") :: id("password") :: attr("name", "password") ::
                attr("autocomplete", "current-password") :: class_("form-control") :: value,
                Nil) :: Nil)

    ///
    /// ログイン画面。
    ///
    /// `prefill` は開発環境でのみ渡される既定の資格情報。毎回の手入力は、
    /// 認証を挟んだ画面を確認するたびに発生する摩擦になる。
    ///
    pub def login(csrfToken: Option[String], enteredName: String, message: Option[String],
                  prefill: Option[(String, String)]): Html =
        let (defaultName, defaultPassword) = match prefill {
            case Some((n, p)) => (n, p)
            case None         => ("", "")
        };
        let name = if (enteredName == "") defaultName else enteredName;
        // 認証に失敗した後は、開発環境でもパスワードを入れ直さない
        let passwordValue = if (enteredName == "") defaultPassword else "";
        element("div", class_("row justify-content-center") :: Nil,
            element("div", class_("col-md-6") :: Nil,
                element("h1", class_("h4 mb-3") :: Nil, text("CargoTracker") :: Nil) ::
                element("p", class_("text-muted") :: Nil,
                    text("国際貨物輸送管理システム") :: Nil) ::
                messageBlock(message) ::
                devNotice(prefill) ::
                loginForm(csrfToken, name, passwordValue) ::
                element("p", class_("text-muted small") :: Nil,
                    text("アカウントの発行・パスワードの再設定は管理者に連絡してください") :: Nil) :: Nil) :: Nil)

    ///
    /// 開発環境であることの注記。
    ///
    /// 既定値が入っている理由を示さないと、本番で同じ挙動だと誤解されうる。
    ///
    def devNotice(prefill: Option[(String, String)]): Html = match prefill {
        case None => empty()
        case Some((n, _)) =>
            element("div", class_("alert alert-secondary small") :: Nil,
                text("開発環境のため、既定の利用者（${n}）を入力済みにしています") :: Nil)
    }

    ///
    /// メッセージがあれば表示する。
    ///
    /// ログアウト完了は利用者の操作どおりの結果であり、警告色にしない。
    ///
    def messageBlock(message: Option[String]): Html = match message {
        case None      => empty()
        case Some(msg) =>
            let kind = if (msg == loggedOutMessage()) "success" else "danger";
            alert(kind, msg)
    }

    /// ログイン画面を描画する
    pub def renderLogin(csrfToken: Option[String], enteredName: String,
                        message: Option[String], prefill: Option[(String, String)]): String =
        renderPage("ログイン", None, "", csrfToken, login(csrfToken, enteredName, message, prefill))

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
