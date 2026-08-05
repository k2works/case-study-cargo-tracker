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

    ///
    /// ナビゲーション項目の実装状態。
    ///
    /// **未実装の項目を表から消さない**。消すと荷受人・荷役作業員・経理は
    /// 項目が 1 つも無くなり、「担当する業務がありません。権限については
    /// 管理者にお問い合わせください」とだけ表示される。権限は正しいので
    /// 問い合わせても解決しない（IT6 レビュー M3）。
    ///
    /// 状態を持たせることで、**未実装であることを利用者に伝えつつ押せなくする**。
    /// あわせて `NavigationReachabilityTest` が
    /// 「`Implemented` にはルートがある」「`Planned` にはルートがない」を機械検査する。
    /// 画面を実装して状態を変え忘れれば落ちる。
    ///
    pub enum NavStatus with Eq, ToString {
        /// ルーティング表に登録済みで、押せば開く
        case Implemented
        /// 未実装。表示はするがリンクにしない
        case Planned
    }

    ///
    /// ナビゲーション項目（表示名・遷移先・表示ロール・実装状態）。
    /// 表示ロールの空リストは全ロールを意味する。
    ///
    /// **ダッシュボードの作業入口もこの表から作る**。navbar と入口を別々に定義すると、
    /// 画面を追加したときに片方だけ更新され、そのロールが機能へ到達できなくなる
    /// （IT3 レビュー L7）。単一の定義から両方を導く。
    ///
    /// # なぜレコードにするか（IT8・TS14。IT7 レビュー M9）
    ///
    /// IT7 は 4 要素タプル + 4 つのアクセサだった。**要素を 1 つ足すたびに
    /// アクセサ 4 つの型注釈をすべて直す**必要があり、しかも位置で意味が決まる
    /// ため、`(String, String, ...)` の 1 番目と 2 番目を取り違えても
    /// コンパイルは通る（表示名と遷移先はどちらも `String` である）。
    ///
    /// レコードなら**名前で意味が決まる**。
    ///
    pub enum NavItem({
        label = String,
        href = String,
        // 表示するロール（空リストは全ロール）
        roles = List[Role],
        status = NavStatus
    })

    def item(label: String, href: String, roles: List[Role], status: NavStatus): NavItem =
        NavItem.NavItem({ label = label, href = href, roles = roles, status = status })

    pub def navItems(): List[NavItem] =
        item("ダッシュボード", "/", Nil, NavStatus.Implemented) ::
        // **営業担当者のみ**（IT8・US01）。IT7 まで荷主にも出す予定だったが、
        // 見積は社内の価格判断（運賃・割増）を含み、荷主が自分で作れる情報ではない。
        // ルート表（`salesOnly()`）と表示ロールを一致させる
        item("見積", "/estimates", Role.Sales :: Nil, NavStatus.Implemented) ::
        item("荷主", "/shippers", Role.Sales :: Nil, NavStatus.Implemented) ::
        // 荷主にも出す。IT6 で利用者と荷主を紐づけ、荷主ロールは
        // **自分の予約だけ**が一覧に出るようにした（IT5 では絞れないため隠していた）
        // **経路設計者にも出す**（IT7）。経路割り当ての入口は予約詳細であり、
        // 一覧から辿れなければ作業を始められない。IT6 の運用手順は
        // これが無いまま「予約一覧から対象を選ぶ」と書かれていた
        item("貨物予約", "/bookings",
            Role.Sales :: Role.Shipper :: Role.Router :: Nil, NavStatus.Implemented) ::
        // 認証を要する追跡画面は未実装（US17・IT9）。一方で**公開追跡は動いている**ため、
        // 荷受人・荷主・追跡管理者の入口としてそちらを出す。荷受人にとっては
        // これが唯一の押せる入口である（`NavigationReachabilityTest` が固定）
        item("貨物追跡", "/public/tracking",
            Role.Shipper :: Role.Consignee :: Role.Tracker :: Nil, NavStatus.Implemented) ::
        // **荷役作業員に初めて押せる入口ができた**（IT10・US15）。
        // IT7 から「押せる入口を 1 つも持たないロール」だった
        item("荷役管理", "/handling", Role.Handler :: Role.Tracker :: Nil, NavStatus.Implemented) ::
        // **営業にも出す**。ルート表は `/voyages` を営業へ開いており
        // （見積の裏付け・荷主への納期回答に日常的に要る）、
        // `AppRoutesTest` も `ROLE_ROUTER,ROLE_SALES` を固定しているのに、
        // navbar だけが経路設計者のみだった。URL を直打ちする営業はいない
        // （IT6 レビュー H8。IT4 と同じ形の導線欠落）
        item("航路管理", "/voyages", Role.Router :: Role.Sales :: Nil, NavStatus.Implemented) ::
        item("請求管理", "/billing/invoices", Role.Accountant :: Nil, NavStatus.Planned) ::
        item("管理設定", "/admin/discount-policies", Role.Admin :: Nil, NavStatus.Planned) :: Nil

    /// 項目の表示名
    pub def navLabel(item: NavItem): String = let NavItem.NavItem(x) = item; x#label

    /// 項目の遷移先
    pub def navHref(item: NavItem): String = let NavItem.NavItem(x) = item; x#href

    /// 項目を表示するロール（空リストは全ロール）
    pub def navRoles(item: NavItem): List[Role] = let NavItem.NavItem(x) = item; x#roles

    /// 項目の実装状態
    pub def navStatus(item: NavItem): NavStatus = let NavItem.NavItem(x) = item; x#status

    ///
    /// 当該ロールに表示する項目か。
    ///
    /// `Admin` は全機能を持つ（バックエンドアーキテクチャのロール表）ため、
    /// 個別の項目に列挙しなくてもすべて表示する。列挙side に書き漏らすと
    /// 管理者が業務画面へ到達できなくなるため、ここで一括して扱う。
    ///
    pub def visibleTo(r: Role, roles: List[Role]): Bool =
        r == Role.Admin or List.isEmpty(roles) or List.memberOf(r, roles)

    ///
    /// メニュー 1 項目。
    ///
    /// **未実装の項目はリンクにしない**。IT6 の欠陥は「見えること」ではなく
    /// 「押せて 404 になること」だった。`href` を外し、準備中であることを
    /// 文言と `aria-disabled` の両方で伝える（読み上げ環境では見た目の
    /// 淡さが伝わらない）。
    ///
    def navItem(item: NavItem, activeNav: String): Html =
        let label = navLabel(item);
        match navStatus(item) {
            case NavStatus.Implemented =>
                let href = navHref(item);
                let cls = if (href == activeNav) "nav-link active" else "nav-link";
                element("li", class_("nav-item") :: Nil,
                    element("a", class_(cls) :: attr("href", href) :: Nil,
                        text(label) :: Nil) :: Nil)
            case NavStatus.Planned =>
                element("li", class_("nav-item") :: Nil,
                    element("span",
                        class_("nav-link disabled") ::
                        attr("aria-disabled", "true") ::
                        attr("title", "この機能はまだ利用できません") :: Nil,
                        text("${label}（準備中）") :: Nil) :: Nil)
        }

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
                    |> List.filter(item -> visibleTo(role(user), navRoles(item)))
                    |> List.map(item -> navItem(item, activeNav));
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
