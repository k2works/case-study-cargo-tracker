///
/// 荷主の画面（`Html` を返す純粋関数）。
///
/// **効果を要求しない**。`render` した文字列を検証するだけで単体テストが書ける。
///
/// 入力値は**ドメイン型ではなく文字列のまま**受け取る（`ShipperFormView`）。
/// 値オブジェクトへ変換できない入力をそのまま画面へ戻す必要があるためであり、
/// 再入力時の値保持と型安全性はこれで両立する（[UI 設計](../../../../../../docs/design/ui_design.md)）。
///
mod ShipperPages {
    use SharedHtml.{Html, element, text, empty, attr, class_, id}
    use SharedHtmlComponents.{form, formField, alert}
    use SharedHtmlLayout.renderPage
    use SharedSecurity.Principal
    use ShipperModel.{Shipper, ShipperType}

    ///
    /// 入力途中の荷主フォーム。
    ///
    /// `errors` はフィールド名とメッセージの組。フィールドに紐づかない
    /// 全体エラーは `""` をフィールド名に使う。
    ///
    pub enum ShipperFormView({
        name = String,
        email = String,
        phone = String,
        address = String,
        shipperType = String,
        contractNumber = String,
        discountRate = String,
        errors = List[(String, String)]
    })

    /// 空のフォーム
    pub def emptyForm(): ShipperFormView =
        ShipperFormView.ShipperFormView({
            name = "", email = "", phone = "", address = "",
            shipperType = "INDIVIDUAL", contractNumber = "", discountRate = "",
            errors = Nil
        })

    /// 入力値を保持したフォーム
    pub def formOf(name: String, email: String, phone: String, address: String,
                   shipperType: String, contractNumber: String, discountRate: String,
                   errors: List[(String, String)]): ShipperFormView =
        ShipperFormView.ShipperFormView({
            name = name, email = email, phone = phone, address = address,
            shipperType = shipperType, contractNumber = contractNumber,
            discountRate = discountRate, errors = errors
        })

    /// 指定フィールドのエラーメッセージ
    def errorsFor(view: ShipperFormView, field: String): List[String] =
        let ShipperFormView.ShipperFormView(v) = view;
        v#errors |> List.filterMap(pair -> {
            let (f, msg) = pair;
            if (f == field) Some(msg) else None
        })

    /// 法人が選択されているか
    pub def isCorporateSelected(view: ShipperFormView): Bool =
        let ShipperFormView.ShipperFormView(v) = view;
        v#shipperType == "CORPORATE"

    // ---- 荷主登録（/shippers/new） ----

    ///
    /// 荷主種別の選択。
    ///
    /// 変更時に htmx で法人固有フィールドだけを差し替える（US03 受入基準 1）。
    /// ページ全体を再読み込みしないのは、**入力途中の他の項目を失わない**ためである。
    ///
    /// 選択肢 1 つ（選択中なら `selected` を付ける）
    def typeOption(value: String, label: String, selected: String): Html =
        let attrs = attr("value", value) ::
                    (if (value == selected) attr("selected", "selected") :: Nil else Nil);
        element("option", attrs, text(label) :: Nil)

    def shipperTypeSelect(view: ShipperFormView): Html =
        let selected = if (isCorporateSelected(view)) "CORPORATE" else "INDIVIDUAL";
        element("div", class_("mb-3") :: Nil,
            element("label", attr("for", "shipperType") :: class_("form-label") :: Nil,
                text("荷主種別") :: Nil) ::
            element("select",
                id("shipperType") :: attr("name", "shipperType") :: class_("form-select") ::
                attr("hx-get", "/shippers/new/corporate-fields") ::
                attr("hx-target", "#corporate-fields") ::
                attr("hx-swap", "innerHTML") ::
                attr("hx-trigger", "change") :: Nil,
                typeOption("INDIVIDUAL", "個人", selected) ::
                typeOption("CORPORATE", "法人", selected) :: Nil) :: Nil)

    ///
    /// 法人固有の入力欄。
    ///
    /// 全体描画でも htmx の部分更新でも**同じ関数**を使う。片方だけ直すと
    /// 「初回表示では出るのに切り替えると出ない」といった食い違いが生まれる。
    ///
    pub def corporateFields(view: ShipperFormView): Html =
        if (not isCorporateSelected(view))
            empty()
        else
            let ShipperFormView.ShipperFormView(v) = view;
            element("div", Nil,
                formField("contractNumber", "契約番号", v#contractNumber,
                          errorsFor(view, "contractNumber")) ::
                formField("discountRate", "割引率（%。0〜30 の範囲で入力）", v#discountRate,
                          errorsFor(view, "discountRate")) :: Nil)

    /// 登録フォーム
    pub def newForm(view: ShipperFormView, csrfToken: Option[String]): Html =
        let ShipperFormView.ShipperFormView(v) = view;
        let globalErrors = errorsFor(view, "")
            |> List.map(msg -> alert("danger", msg));
        element("div", class_("row") :: Nil,
            element("div", class_("col-md-8 offset-md-2") :: Nil,
                element("h1", class_("h4 mb-3") :: Nil, text("荷主登録") :: Nil) ::
                globalErrors :::
                form("/shippers", "post", csrfToken,
                    formField("name", "氏名 / 社名", v#name, errorsFor(view, "name")) ::
                    formField("email", "メールアドレス", v#email, errorsFor(view, "email")) ::
                    formField("phone", "電話番号（任意）", v#phone, errorsFor(view, "phone")) ::
                    formField("address", "住所（任意）", v#address, errorsFor(view, "address")) ::
                    shipperTypeSelect(view) ::
                    element("div", id("corporate-fields") :: Nil,
                        corporateFields(view) :: Nil) ::
                    element("div", class_("d-flex gap-2") :: Nil,
                        element("button", class_("btn btn-primary") ::
                                          attr("type", "submit") :: Nil,
                            text("登録する") :: Nil) ::
                        element("a", class_("btn btn-outline-secondary") ::
                                     attr("href", "/shippers") :: Nil,
                            text("キャンセル") :: Nil) :: Nil) :: Nil) :: Nil) :: Nil)

    // ---- メール重複（US02 受入基準 2・Try T4） ----

    ///
    /// 重複したメールアドレスの案内。
    ///
    /// **「重複しています」で終わらせない**。利用者が次に取れる行動を 2 つ示す。
    ///
    /// 1. 既存の荷主を使う（一覧へ移動して確認する）
    /// 2. 入力を修正する（入力値を保持したままフォームへ戻る）
    ///
    /// どちらも示さないと、利用者は入力をやり直す以外の選択肢を持てない。
    ///
    pub def duplicateEmail(existing: Shipper, view: ShipperFormView,
                           csrfToken: Option[String]): Html =
        element("div", class_("row") :: Nil,
            element("div", class_("col-md-8 offset-md-2") :: Nil,
                element("h1", class_("h4 mb-3") :: Nil, text("荷主登録") :: Nil) ::
                alert("warning",
                    "このメールアドレスは既に登録されています: " +
                    "${ShipperModel.nameOf(existing)}（${ShipperModel.shipperCodeOf(existing)}）") ::
                element("div", class_("d-flex gap-2 mb-4") :: Nil,
                    element("a", class_("btn btn-primary") ::
                                 // **着地先で絞り込まれる語を渡す**。IT4 は `?code=` を
                                 // 渡していたが一覧は読み捨てており、押した先で全件が
                                 // 出ていた。導線は着地先まで通して初めて意味を持つ
                                 attr("href", "/shippers?q=${ShipperModel.shipperCodeOf(existing)}") :: Nil,
                        text("既存の荷主を使う") :: Nil) ::
                    element("a", class_("btn btn-outline-secondary") ::
                                 attr("href", "#shipper-form") :: Nil,
                        text("入力を修正する") :: Nil) :: Nil) ::
                element("div", id("shipper-form") :: Nil,
                    newForm(view, csrfToken) :: Nil) :: Nil) :: Nil)

    // ---- 荷主一覧（/shippers） ----

    /// 一覧の 1 行
    def shipperRow(s: Shipper): Html =
        let discount = match ShipperModel.discountRateOf(s) {
            case None    => "-"
            case Some(r) => "${ShipperModel.discountRatePercent(r)}%"
        };
        element("tr", Nil,
            element("td", Nil, text(ShipperModel.shipperCodeOf(s)) :: Nil) ::
            element("td", Nil, text(ShipperModel.nameOf(s)) :: Nil) ::
            element("td", Nil,
                text(ShipperModel.shipperTypeLabel(ShipperModel.shipperTypeOf(s))) :: Nil) ::
            element("td", Nil, text(ShipperModel.emailOf(s)) :: Nil) ::
            element("td", Nil,
                text(ShipperModel.phoneOf(s) |> Option.getWithDefault("-")) :: Nil) ::
            element("td", Nil, text(discount) :: Nil) :: Nil)

    ///
    /// 荷主一覧。
    ///
    /// 0 件のときに空の表を出さない。**まだ 1 件も登録していない**のか
    /// **検索に一致しなかった**のかを利用者が判断できるよう、文言で伝える。
    ///
    pub def index(shippers: List[Shipper], notice: Option[String], keyword: String): Html =
        let searching = String.trim(keyword) != "";
        let body =
            if (List.isEmpty(shippers))
                // **「登録がない」と「条件に合わない」を区別する**。同じ文言だと、
                // 絞り込んだ結果 0 件だったのか、そもそも 1 件も無いのかが分からず、
                // 利用者は登録済みのはずの荷主を疑うことになる
                element("p", class_("text-muted") :: Nil,
                    text(if (searching)
                             "該当する荷主はありません。検索条件を変えてお試しください"
                         else
                             "登録されている荷主はありません。[新規荷主登録] から登録してください") :: Nil)
            else
                element("table", class_("table table-striped align-middle") :: Nil,
                    element("thead", Nil,
                        element("tr", Nil,
                            element("th", Nil, text("荷主コード") :: Nil) ::
                            element("th", Nil, text("氏名 / 社名") :: Nil) ::
                            element("th", Nil, text("種別") :: Nil) ::
                            element("th", Nil, text("メールアドレス") :: Nil) ::
                            element("th", Nil, text("電話番号") :: Nil) ::
                            element("th", Nil, text("割引率") :: Nil) :: Nil) :: Nil) ::
                    element("tbody", id("shipper-list") :: Nil,
                        List.map(shipperRow, shippers)) :: Nil);
        element("div", Nil,
            // 通知は見出しの前に置く。**操作の結果は最初に目に入る位置**にある
            // 必要がある（下にあると一覧を読み始めてから気付く）
            (match notice {
                case None    => empty()
                case Some(m) => alert("success", m)
             }) ::
            element("div", class_("d-flex justify-content-between align-items-center mb-3") :: Nil,
                element("h1", class_("h4 mb-0") :: Nil, text("荷主一覧") :: Nil) ::
                element("a", class_("btn btn-primary") ::
                             attr("href", "/shippers/new") :: Nil,
                    text("新規荷主登録") :: Nil) :: Nil) ::
            searchBox(keyword) ::
            body :: Nil)

    ///
    /// 絞り込みの入力欄。
    ///
    /// GET のフォームにする。**検索結果を URL で共有・再訪できる**ようにするためで、
    /// POST にすると再読み込みで再送信の確認が出る。CSRF トークンは要らない
    /// （状態を変えない）。
    ///
    /// 入力した語を `value` へ書き戻す。消えると絞り込みのたびに打ち直しになる。
    ///
    def searchBox(keyword: String): Html =
        element("form", attr("method", "get") :: attr("action", "/shippers") ::
                        class_("row g-2 mb-3") :: Nil,
            element("div", class_("col-auto flex-grow-1") :: Nil,
                element("input", attr("type", "text") :: attr("name", "q") ::
                                 attr("value", keyword) :: id("shipper-search") ::
                                 attr("placeholder", "氏名 / 社名・荷主コード・メールアドレスで絞り込む") ::
                                 class_("form-control") :: Nil, Nil) :: Nil) ::
            element("div", class_("col-auto") :: Nil,
                element("button", attr("type", "submit") :: class_("btn btn-outline-secondary") :: Nil,
                    text("絞り込む") :: Nil) :: Nil) ::
            (if (String.trim(keyword) == "")
                 empty()
             else
                 element("div", class_("col-auto") :: Nil,
                     element("a", attr("href", "/shippers") ::
                                  class_("btn btn-link") :: Nil,
                         text("解除") :: Nil) :: Nil)) :: Nil)

    // ---- 描画 ----

    pub def renderNew(p: Option[Principal], view: ShipperFormView,
                      csrfToken: Option[String]): String =
        renderPage("荷主登録", p, "/shippers", csrfToken, newForm(view, csrfToken))

    pub def renderDuplicate(p: Option[Principal], existing: Shipper, view: ShipperFormView,
                            csrfToken: Option[String]): String =
        renderPage("荷主登録", p, "/shippers", csrfToken,
                   duplicateEmail(existing, view, csrfToken))

    ///
    /// 荷主一覧。
    ///
    /// `notice` は PRG の遷移先で 1 回だけ出す通知（発行された荷主コードなど）。
    /// **文言は呼び出し側が組み立てて渡す**。ここでは表示位置だけを決める。
    ///
    pub def renderIndex(p: Option[Principal], shippers: List[Shipper],
                        csrfToken: Option[String], notice: Option[String],
                        keyword: String): String =
        renderPage("荷主一覧", p, "/shippers", csrfToken, index(shippers, notice, keyword))

    /// htmx の部分更新（法人固有フィールドのみ）
    pub def renderCorporateFields(view: ShipperFormView): String =
        SharedHtml.render(corporateFields(view))
}
