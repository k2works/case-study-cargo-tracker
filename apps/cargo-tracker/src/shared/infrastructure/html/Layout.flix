///
/// 全画面共通のレイアウト（TS04）。
///
/// ナビゲーション項目とその表示ロールは docs/design/ui_design.md
/// 「共通レイアウト設計」を正典とする。表示制御をここに集約するのは、
/// ロールごとの導線欠落（到達できない画面）を一箇所で検査できるようにするためである。
///
/// 認可そのものはルーティング表の `RequiredRole` が担う。ナビの出し分けは
/// **導線の提示**であって認可ではない。
///
mod SharedHtmlLayout {
    use SharedHtml.{element, text, fragment, empty, attr, class_, id, render, renderDocument}
    use SharedHtml.Html
    use SharedHtmlComponents.form
    use SharedSecurity.Role
    use SharedSecurity.Principal
    use SharedSecurity.{role, username}

    /// ナビゲーション項目（ラベル・遷移先・表示ロール。空リストは全ロール）
    ///
    /// ナビゲーション項目（表示名・遷移先・表示ロール）。
    ///
    /// **ダッシュボードの作業入口もこの表から作る**。navbar と入口を別々に定義すると、
    /// 画面を追加したときに片方だけ更新され、そのロールが機能へ到達できなくなる
    /// （IT3 レビュー L7）。単一の定義から両方を導く。
    ///
    pub def navItems(): List[(String, String, List[Role])] =
        ("ダッシュボード", "/", Nil) ::
        ("見積", "/estimates", Role.Sales :: Role.Shipper :: Nil) ::
        ("荷主", "/shippers", Role.Sales :: Nil) ::
        ("貨物予約", "/bookings", Role.Sales :: Role.Shipper :: Nil) ::
        ("貨物追跡", "/tracking", Role.Shipper :: Role.Consignee :: Role.Tracker :: Nil) ::
        ("荷役管理", "/handling", Role.Handler :: Role.Tracker :: Nil) ::
        ("航路管理", "/voyages", Role.Router :: Nil) ::
        ("請求管理", "/billing/invoices", Role.Accountant :: Nil) ::
        ("管理設定", "/admin/discount-policies", Role.Admin :: Nil) :: Nil

    ///
    /// 当該ロールに表示する項目か。
    ///
    /// `Admin` は全機能を持つ（バックエンドアーキテクチャのロール表）ため、
    /// 個別の項目に列挙しなくてもすべて表示する。列挙side に書き漏らすと
    /// 管理者が業務画面へ到達できなくなるため、ここで一括して扱う。
    ///
    pub def visibleTo(r: Role, roles: List[Role]): Bool =
        r == Role.Admin or List.isEmpty(roles) or List.memberOf(r, roles)

    /// メニュー 1 項目
    def navItem(label: String, href: String, activeNav: String): Html =
        let cls = if (href == activeNav) "nav-link active" else "nav-link";
        element("li", class_("nav-item") :: Nil,
            element("a", class_(cls) :: attr("href", href) :: Nil, text(label) :: Nil) :: Nil)

    ///
    /// ナビゲーションバー。
    ///
    /// 未認証（公開追跡ページなど）では表示しない。遷移先がすべて認証必須のため、
    /// 出しても行き止まりになる。
    ///
    pub def nav(p: Option[Principal], activeNav: String, csrfToken: Option[String]): Html = match p {
        case None => empty()
        case Some(user) =>
            let items =
                navItems()
                    |> List.filter(match (_, _, roles) -> visibleTo(role(user), roles))
                    |> List.map(match (label, href, _) -> navItem(label, href, activeNav));
            element("nav",
                class_("navbar navbar-expand-lg navbar-dark bg-dark mb-4") ::
                attr("role", "navigation") ::
                attr("aria-label", "メインナビゲーション") :: Nil,
                element("div", class_("container-fluid") :: Nil,
                    element("a", class_("navbar-brand") :: attr("href", "/") :: Nil,
                        text("CargoTracker") :: Nil) ::
                    element("ul", class_("navbar-nav me-auto") :: Nil, items) ::
                    element("span", class_("navbar-text me-3") :: Nil,
                        text(username(user)) :: Nil) ::
                    logoutForm(csrfToken) :: Nil) :: Nil)
    }

    ///
    /// ログアウト。
    ///
    /// リンクではなく POST フォームにするのは、GET によるログアウトが
    /// 第三者のリンク踏ませで発火しうるためである。CSRF トークンもここで注入する。
    ///
    def logoutForm(csrfToken: Option[String]): Html =
        form("/logout", "post", csrfToken,
            element("button", class_("btn btn-outline-light btn-sm") ::
                              attr("type", "submit") :: Nil,
                text("ログアウト") :: Nil) :: Nil)

    /// フッター（著作権表示は中央揃え）
    pub def footer(): Html =
        element("footer", class_("text-muted small mt-4 border-top pt-3") :: Nil,
            element("div", class_("container-fluid text-center") :: Nil,
                text("Copyright (c) 2026 CargoTracker System") :: Nil) :: Nil)

    ///
    /// ページ全体の枠。
    ///
    /// `activeNav` は現在位置のメニューの遷移先（例 `/tracking`）。
    ///
    pub def page(title: String, p: Option[Principal], activeNav: String,
                 csrfToken: Option[String], body: Html): Html =
        element("html", attr("lang", "ja") :: Nil,
            element("head", Nil,
                element("meta", attr("charset", "utf-8") :: Nil, Nil) ::
                element("meta", attr("name", "viewport") ::
                                attr("content", "width=device-width, initial-scale=1") :: Nil, Nil) ::
                element("title", Nil, text("${title} | CargoTracker") :: Nil) ::
                element("link", attr("rel", "stylesheet") ::
                                attr("href", "/static/css/bootstrap.min.css") :: Nil, Nil) ::
                element("script", attr("src", "/static/js/htmx.min.js") ::
                                  attr("defer", "defer") :: Nil, Nil) :: Nil) ::
            element("body", Nil,
                nav(p, activeNav, csrfToken) ::
                element("main", class_("container-fluid py-2") :: id("main") :: Nil,
                    body :: Nil) ::
                footer() :: Nil) :: Nil)

    /// ページを HTML 文字列へ描画する
    pub def renderPage(title: String, p: Option[Principal], activeNav: String,
                       csrfToken: Option[String], body: Html): String =
        renderDocument(page(title, p, activeNav, csrfToken, body))
}
