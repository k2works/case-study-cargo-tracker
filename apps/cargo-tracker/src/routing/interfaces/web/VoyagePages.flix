///
/// 航海スケジュールの画面（`Html` を返す純粋関数）。
///
/// **効果を要求しない**。`render` した文字列を検証するだけで単体テストが書ける。
///
/// 入力値は**ドメイン型ではなく文字列のまま**受け取る（`VoyageFormView`）。
/// 値オブジェクトへ変換できない入力をそのまま画面へ戻す必要があるためである。
///
mod VoyagePages {
    use RoutingModel.{Voyage, CargoCapability}
    use SharedHtml.{Html, element, text, empty, attr, class_, id}
    use SharedHtmlComponents.{form, formField, alert}
    use SharedHtmlLayout.renderPage
    use SharedSecurity.Principal

    ///
    /// 入力途中の航海スケジュール。
    ///
    /// 区間は「出発地・到着地・出発日時・到着日時」の 4 つ組の並びで持つ。
    /// **`errors` はフィールド名とメッセージの組**。フィールドに紐づかない
    /// 全体エラーは `""` をフィールド名に使う。
    ///
    pub enum VoyageFormView({
        voyageNumber = String,
        vesselName = String,
        carrierName = String,
        capabilities = List[String],
        legs = List[(String, String, String, String)],
        // 楽観的ロックの版（編集時のみ）
        version = String,
        errors = List[(String, String)]
    })

    /// 空のフォーム（区間は 1 つ用意する。0 だと何も入力できない）
    pub def emptyForm(): VoyageFormView =
        formOf("", "", "", "GENERAL" :: Nil, ("", "", "", "") :: Nil, "", Nil)

    pub def formOf(voyageNumber: String, vesselName: String, carrierName: String,
                   capabilities: List[String],
                   legs: List[(String, String, String, String)],
                   version: String,
                   errors: List[(String, String)]): VoyageFormView =
        VoyageFormView.VoyageFormView({
            voyageNumber = voyageNumber, vesselName = vesselName, carrierName = carrierName,
            capabilities = capabilities,
            // **区間が 0 件のフォームを作らない**。入力欄が無いと利用者は
            // 何もできず、画面が壊れているように見える
            legs = if (List.isEmpty(legs)) ("", "", "", "") :: Nil else legs,
            version = version, errors = errors
        })

    /// 既存の航海からフォームを作る（編集画面）
    pub def formOfVoyage(v: Voyage, version: String): VoyageFormView =
        formOf(
            RoutingModel.voyageNumberOf(v),
            RoutingModel.vesselNameOf(v),
            RoutingModel.carrierNameOf(v),
            RoutingModel.capabilitiesOf(v) |> List.map(RoutingModel.capabilityToPersisted),
            RoutingModel.movementsOf(v) |> List.map(m ->
                (RoutingModel.departureLocationOf(m), RoutingModel.arrivalLocationOf(m),
                 RoutingModel.departureTimeOf(m), RoutingModel.arrivalTimeOf(m))),
            version, Nil)

    def errorsFor(view: VoyageFormView, field: String): List[String] =
        let VoyageFormView.VoyageFormView(v) = view;
        v#errors |> List.filterMap(pair -> {
            let (f, msg) = pair;
            if (f == field) Some(msg) else None
        })

    /// フォームにエラーを添える
    pub def withError(view: VoyageFormView, error: (String, String)): VoyageFormView =
        let VoyageFormView.VoyageFormView(v) = view;
        formOf(v#voyageNumber, v#vesselName, v#carrierName, v#capabilities,
               v#legs, v#version, error :: Nil)

    /// 版を差し替える（衝突後に現在の値で出し直すため）
    pub def withVersion(view: VoyageFormView, version: String): VoyageFormView =
        let VoyageFormView.VoyageFormView(v) = view;
        formOf(v#voyageNumber, v#vesselName, v#carrierName, v#capabilities,
               v#legs, version, v#errors)

    // ---- 入力フォーム ----

    ///
    /// 区間の入力欄。
    ///
    /// **順序を利用者に見せる**（「区間 1」「区間 2」）。並びが意味を持つため、
    /// どれが最初の区間かが分からないと入力できない。
    ///
    def legFields(index: Int32, leg: (String, String, String, String),
                  locations: List[(String, String)]): Html =
        let (departure, arrival, departureTime, arrivalTime) = leg;
        element("fieldset", class_("border rounded p-3 mb-3") :: Nil,
            element("legend", class_("h6") :: Nil, text("区間 ${index + 1}") :: Nil) ::
            locationSelect("legDeparture", "出発港", departure, locations) ::
            locationSelect("legArrival", "到着港", arrival, locations) ::
            dateTimeField("legDepartureTime", "出発日時", departureTime) ::
            dateTimeField("legArrivalTime", "到着日時", arrivalTime) :: Nil)

    ///
    /// 港の選択欄。
    ///
    /// **同じ `name` を区間の数だけ出す**。フォーム送信では同名の値が
    /// 並びとして届き、区間の順序がそのまま保たれる。
    ///
    def locationSelect(name: String, label: String, selected: String,
                       locations: List[(String, String)]): Html =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: Nil, text(label) :: Nil) ::
            element("select", attr("name", name) :: class_("form-select") :: Nil,
                element("option", attr("value", "") :: Nil, text("選択してください") :: Nil) ::
                (locations |> List.map(pair -> {
                    let (code, locationName) = pair;
                    let attrs = attr("value", code) ::
                                (if (code == selected) attr("selected", "selected") :: Nil else Nil);
                    element("option", attrs, text("${locationName} (${code})") :: Nil)
                }))) :: Nil)

    ///
    /// 日時の入力欄。
    ///
    /// `datetime-local` を使う。内部表現（`YYYY-MM-DDTHH:MM`）と
    /// **ブラウザが送る形が一致する**ため、変換が要らない。
    ///
    def dateTimeField(name: String, label: String, value: String): Html =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: Nil, text(label) :: Nil) ::
            element("input", attr("type", "datetime-local") :: attr("name", name) ::
                             attr("value", value) :: class_("form-control") :: Nil, Nil) :: Nil)

    ///
    /// 対応貨物種別の選択。
    ///
    /// 複数選べる。チェックボックスにするのは、**選択肢が 3 つで固定**であり
    /// 「どれが選べるか」を一目で見せたいためである。
    ///
    def capabilityChecks(view: VoyageFormView): Html =
        let VoyageFormView.VoyageFormView(v) = view;
        let errors = errorsFor(view, "capabilities");
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: Nil, text("対応貨物種別") :: Nil) ::
            (RoutingModel.allCapabilities() |> List.map(c -> {
                let value = RoutingModel.capabilityToPersisted(c);
                let checked = if (List.memberOf(value, v#capabilities))
                                  attr("checked", "checked") :: Nil
                              else Nil;
                element("div", class_("form-check") :: Nil,
                    element("input", attr("type", "checkbox") :: attr("name", "capabilities") ::
                                     attr("value", value) :: class_("form-check-input") ::
                                     checked, Nil) ::
                    element("label", class_("form-check-label") :: Nil,
                        text(RoutingModel.capabilityLabel(c)) :: Nil) :: Nil)
            })) :::
            (errors |> List.map(msg ->
                element("div", class_("text-danger small") :: Nil, text(msg) :: Nil))))

    ///
    /// 登録・編集のフォーム。
    ///
    /// 編集時は航海番号を変更できない（識別子であり、変えると別の航海になる）。
    ///
    pub def voyageForm(view: VoyageFormView, locations: List[(String, String)],
                       csrfToken: Option[String], isEdit: Bool): Html =
        let VoyageFormView.VoyageFormView(v) = view;
        let globalErrors = errorsFor(view, "") |> List.map(msg -> alert("danger", msg));
        let action = if (isEdit) "/voyages/${v#voyageNumber}" else "/voyages";
        element("div", class_("row") :: Nil,
            element("div", class_("col-md-8 offset-md-2") :: Nil,
                element("h1", class_("h4 mb-3") :: Nil,
                    text(if (isEdit) "航海スケジュール編集" else "航海スケジュール登録") :: Nil) ::
                globalErrors :::
                form(action, "post", csrfToken,
                    (if (isEdit)
                        // 航海番号は識別子。変えると別の航海になる
                        element("div", class_("mb-3") :: Nil,
                            element("label", class_("form-label") :: Nil,
                                text("航海番号") :: Nil) ::
                            element("p", class_("form-control-plaintext") :: Nil,
                                text(v#voyageNumber) :: Nil) ::
                            element("input", attr("type", "hidden") ::
                                             attr("name", "voyageNumber") ::
                                             attr("value", v#voyageNumber) :: Nil, Nil) ::
                            // **版を持ち回る**。差分確認の間に別の利用者が
                            // 更新していたら、保存の直前に検出する
                            element("input", attr("type", "hidden") ::
                                             attr("name", "version") ::
                                             attr("value", v#version) :: Nil, Nil) :: Nil)
                     else
                        formField("voyageNumber", "航海番号", v#voyageNumber,
                                  errorsFor(view, "voyageNumber"))) ::
                    formField("vesselName", "船名", v#vesselName, errorsFor(view, "vesselName")) ::
                    formField("carrierName", "運送会社", v#carrierName,
                              errorsFor(view, "carrierName")) ::
                    capabilityChecks(view) ::
                    element("div", id("legs") :: Nil,
                        (v#legs |> List.mapWithIndex((i, leg) -> legFields(i, leg, locations)))) ::
                    // 区間の追加は htmx で行う。**ページ全体を再描画しない**ため、
                    // 入力途中の他の項目は失われない
                    element("div", class_("mb-3") :: Nil,
                        element("button", attr("type", "button") ::
                                          class_("btn btn-outline-secondary btn-sm") ::
                                          attr("hx-get", "/voyages/leg-fields") ::
                                          attr("hx-target", "#legs") ::
                                          attr("hx-swap", "beforeend") :: Nil,
                            text("区間を追加") :: Nil) :: Nil) ::
                    legErrors(view) :::
                    element("div", class_("d-flex gap-2") :: Nil,
                        element("button", class_("btn btn-primary") ::
                                          attr("type", "submit") :: Nil,
                            text(if (isEdit) "確認する" else "登録する") :: Nil) ::
                        element("a", class_("btn btn-outline-secondary") ::
                                     attr("href", "/voyages") :: Nil,
                            text("キャンセル") :: Nil) :: Nil) :: Nil) :: Nil) :: Nil)

    /// 区間に関するエラー（欄が並ぶため、まとめて出す）
    def legErrors(view: VoyageFormView): List[Html] =
        errorsFor(view, "legs") |> List.map(msg -> alert("danger", msg))

    // ---- 差分確認（US25 受入基準 2） ----

    ///
    /// 更新前後の差分。
    ///
    /// **項目ごとに旧値・新値を並べる**。「変わりました」だけでは、
    /// 利用者は何を承認するのか分からない。
    ///
    /// `notice` は競合が起きたときの警告（通常の確認では `None`）
    pub def diff(voyageNumber: String, rows: List[(String, String, String)],
                 view: VoyageFormView, csrfToken: Option[String],
                 notice: Option[String]): Html =
        element("div", class_("row") :: Nil,
            element("div", class_("col-md-8 offset-md-2") :: Nil,
                element("h1", class_("h4 mb-3") :: Nil,
                    text("更新内容の確認（${voyageNumber}）") :: Nil) ::
                (match notice {
                    case None    => Nil
                    case Some(m) => alert("warning", m) :: Nil
                 }) :::
                (if (List.isEmpty(rows))
                    alert("warning", "変更された項目はありません")
                 else
                    element("table", class_("table") :: Nil,
                        element("thead", Nil,
                            element("tr", Nil,
                                element("th", Nil, text("項目") :: Nil) ::
                                element("th", Nil, text("現在") :: Nil) ::
                                element("th", Nil, text("更新後") :: Nil) :: Nil) :: Nil) ::
                        element("tbody", id("diff-rows") :: Nil,
                            (rows |> List.map(row -> {
                                let (label, before, after) = row;
                                element("tr", Nil,
                                    element("th", Nil, text(label) :: Nil) ::
                                    element("td", class_("text-muted") :: Nil,
                                        text(before) :: Nil) ::
                                    element("td", class_("fw-bold") :: Nil,
                                        text(after) :: Nil) :: Nil)
                            }))) :: Nil)) ::
                // 入力内容をそのまま持ち回る。確認画面で作り直すと、
                // 表示した内容と保存する内容がずれる余地が生まれる
                form("/voyages/${voyageNumber}/apply", "post", csrfToken,
                    hiddenFields(view) :::
                    element("div", class_("d-flex gap-2") :: Nil,
                        element("button", class_("btn btn-primary") ::
                                          attr("type", "submit") :: Nil,
                            text("更新する") :: Nil) ::
                        element("a", class_("btn btn-outline-secondary") ::
                                     attr("href", "/voyages") :: Nil,
                            text("キャンセル") :: Nil) :: Nil) :: Nil) :: Nil) :: Nil)

    /// 確認画面が持ち回る入力値
    def hiddenFields(view: VoyageFormView): List[Html] =
        let VoyageFormView.VoyageFormView(v) = view;
        hidden("voyageNumber", v#voyageNumber) ::
        hidden("version", v#version) ::
        hidden("vesselName", v#vesselName) ::
        hidden("carrierName", v#carrierName) ::
        (v#capabilities |> List.map(c -> hidden("capabilities", c))) :::
        (v#legs |> List.flatMap(leg -> {
            let (departure, arrival, departureTime, arrivalTime) = leg;
            hidden("legDeparture", departure) ::
            hidden("legArrival", arrival) ::
            hidden("legDepartureTime", departureTime) ::
            hidden("legArrivalTime", arrivalTime) :: Nil
        }))

    def hidden(name: String, value: String): Html =
        element("input", attr("type", "hidden") :: attr("name", name) ::
                         attr("value", value) :: Nil, Nil)

    // ---- 一覧・検索（US07） ----

    ///
    /// 航路一覧（検索結果を兼ねる）。
    ///
    /// 検索条件を保持して再描画する。**0 件のとき条件を緩和できる**ようにする
    /// （US07 受入基準 4）。
    ///
    pub def index(voyages: List[Voyage], criteria: (String, String, String, String, String),
                  locations: List[(String, String)], notice: Option[String],
                  canManage: Bool): Html =
        let (origin, destination, departureFrom, departureTo, capability) = criteria;
        let searching = origin != "" or destination != "" or
                        departureFrom != "" or departureTo != "" or capability != "";
        let body =
            if (List.isEmpty(voyages))
                // **「登録がない」と「条件に合わない」を区別する**
                element("p", class_("text-muted") :: Nil,
                    text(if (searching)
                             "条件を満たす航海はありません。条件を緩和して再検索してください"
                         else
                             "登録されている航海はありません。[新規登録] から登録してください") :: Nil)
            else
                element("table", class_("table table-striped align-middle") :: Nil,
                    element("thead", Nil,
                        element("tr", Nil,
                            element("th", Nil, text("航海番号") :: Nil) ::
                            element("th", Nil, text("運送会社") :: Nil) ::
                            element("th", Nil, text("船名") :: Nil) ::
                            element("th", Nil, text("出発地") :: Nil) ::
                            element("th", Nil, text("仕向地") :: Nil) ::
                            element("th", Nil, text("出発日時") :: Nil) ::
                            element("th", Nil, text("到着日時") :: Nil) ::
                            element("th", Nil, text("寄港地") :: Nil) ::
                            element("th", Nil, text("対応貨物") :: Nil) ::
                            (if (canManage)
                                element("th", Nil, text("操作") :: Nil) :: Nil
                             else Nil)) :: Nil) ::
                    element("tbody", id("voyage-list") :: Nil,
                        (voyages |> List.map(v -> voyageRow(v, canManage)))) :: Nil);
        element("div", Nil,
            (match notice {
                case None    => empty()
                case Some(m) => alert("success", m)
             }) ::
            element("div", class_("d-flex justify-content-between align-items-center mb-3") :: Nil,
                element("h1", class_("h4 mb-0") :: Nil, text("航路一覧") :: Nil) ::
                (if (canManage)
                    element("a", class_("btn btn-primary") ::
                                 attr("href", "/voyages/new") :: Nil,
                        text("新規登録") :: Nil)
                 else empty()) :: Nil) ::
            searchBox(criteria, locations) ::
            body :: Nil)

    def voyageRow(v: Voyage, canManage: Bool): Html =
        let capabilities = RoutingModel.capabilitiesOf(v)
            |> List.map(RoutingModel.capabilityLabel)
            |> List.join("・");
        let portsOfCall = match RoutingModel.portsOfCallOf(v) {
            case Nil   => "直行"
            case ports => List.join("→", ports)
        };
        element("tr", Nil,
            element("td", Nil, text(RoutingModel.voyageNumberOf(v)) :: Nil) ::
            element("td", Nil, text(RoutingModel.carrierNameOf(v)) :: Nil) ::
            element("td", Nil, text(RoutingModel.vesselNameOf(v)) :: Nil) ::
            element("td", Nil, text(RoutingModel.originOf(v)) :: Nil) ::
            element("td", Nil, text(RoutingModel.destinationOf(v)) :: Nil) ::
            element("td", Nil, text(RoutingModel.departureTimeOfVoyage(v)) :: Nil) ::
            element("td", Nil, text(RoutingModel.arrivalTimeOfVoyage(v)) :: Nil) ::
            element("td", Nil, text(portsOfCall) :: Nil) ::
            element("td", Nil, text(capabilities) :: Nil) ::
            (if (canManage)
                element("td", Nil,
                    element("a", class_("btn btn-sm btn-outline-primary") ::
                                 attr("href", "/voyages/${RoutingModel.voyageNumberOf(v)}/edit") :: Nil,
                        text("編集") :: Nil) :: Nil) :: Nil
             else Nil))

    ///
    /// 検索条件の入力欄。
    ///
    /// GET のフォームにする（結果を URL で共有・再訪できる。状態を変えないため
    /// CSRF トークンは付けない。`arch-lint` 規約 8）。
    ///
    def searchBox(criteria: (String, String, String, String, String),
                  locations: List[(String, String)]): Html =
        let (origin, destination, departureFrom, departureTo, capability) = criteria;
        element("form", attr("method", "get") :: attr("action", "/voyages") ::
                        class_("row g-2 mb-3 align-items-end") :: Nil,
            element("div", class_("col-md-2") :: Nil,
                locationSelect("origin", "出発地", origin, locations) :: Nil) ::
            element("div", class_("col-md-2") :: Nil,
                locationSelect("destination", "仕向地", destination, locations) :: Nil) ::
            element("div", class_("col-md-2") :: Nil,
                dateField("departureFrom", "出発日（開始）", departureFrom) :: Nil) ::
            element("div", class_("col-md-2") :: Nil,
                dateField("departureTo", "出発日（終了）", departureTo) :: Nil) ::
            element("div", class_("col-md-2") :: Nil,
                capabilitySelect(capability) :: Nil) ::
            element("div", class_("col-md-2 mb-3") :: Nil,
                element("button", attr("type", "submit") ::
                                  class_("btn btn-outline-secondary") :: Nil,
                    text("検索") :: Nil) ::
                element("a", attr("href", "/voyages") :: class_("btn btn-link") :: Nil,
                    text("解除") :: Nil) :: Nil) :: Nil)

    def dateField(name: String, label: String, value: String): Html =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: Nil, text(label) :: Nil) ::
            element("input", attr("type", "date") :: attr("name", name) ::
                             attr("value", value) :: class_("form-control") :: Nil, Nil) :: Nil)

    def capabilitySelect(selected: String): Html =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: Nil, text("貨物種別") :: Nil) ::
            element("select", attr("name", "capability") :: class_("form-select") :: Nil,
                element("option", attr("value", "") :: Nil, text("指定しない") :: Nil) ::
                (RoutingModel.allCapabilities() |> List.map(c -> {
                    let value = RoutingModel.capabilityToPersisted(c);
                    let attrs = attr("value", value) ::
                                (if (value == selected) attr("selected", "selected") :: Nil else Nil);
                    element("option", attrs, text(RoutingModel.capabilityLabel(c)) :: Nil)
                }))) :: Nil)

    // ---- 描画 ----

    pub def renderNew(p: Option[Principal], view: VoyageFormView,
                      locations: List[(String, String)], csrfToken: Option[String]): String =
        renderPage("航海スケジュール登録", p, "/voyages", csrfToken,
                   voyageForm(view, locations, csrfToken, false))

    pub def renderEdit(p: Option[Principal], view: VoyageFormView,
                       locations: List[(String, String)], csrfToken: Option[String]): String =
        renderPage("航海スケジュール編集", p, "/voyages", csrfToken,
                   voyageForm(view, locations, csrfToken, true))

    pub def renderDiff(p: Option[Principal], voyageNumber: String,
                       rows: List[(String, String, String)], view: VoyageFormView,
                       csrfToken: Option[String], notice: Option[String]): String =
        renderPage("更新内容の確認", p, "/voyages", csrfToken,
                   diff(voyageNumber, rows, view, csrfToken, notice))

    pub def renderIndex(p: Option[Principal], voyages: List[Voyage],
                        criteria: (String, String, String, String, String),
                        locations: List[(String, String)],
                        csrfToken: Option[String], notice: Option[String],
                        canManage: Bool): String =
        renderPage("航路一覧", p, "/voyages", csrfToken,
                   index(voyages, criteria, locations, notice, canManage))

    ///
    /// 航海が見つからないときの画面。
    ///
    /// **一覧へ戻る導線を必ず置く**。行き止まりにすると、利用者は
    /// ブラウザの戻るしか手段がなくなる。
    ///
    pub def renderNotFound(p: Option[Principal], voyageNumber: String,
                           csrfToken: Option[String]): String =
        renderPage("航路一覧", p, "/voyages", csrfToken,
            element("div", class_("row") :: Nil,
                element("div", class_("col-md-8 offset-md-2") :: Nil,
                    alert("warning", "航海番号 ${voyageNumber} の航海は見つかりませんでした") ::
                    element("a", class_("btn btn-link") :: attr("href", "/voyages") :: Nil,
                        text("航路一覧へ戻る") :: Nil) :: Nil) :: Nil))

    /// 区間 1 つ分の断片（htmx で追加する）
    pub def renderLegFields(index: Int32, locations: List[(String, String)]): String =
        SharedHtml.render(legFields(index, ("", "", "", ""), locations))
}
