///
/// 見積の画面（`Html` を返す純粋関数。US01・IT8）。
///
mod EstimatePages {
    use EstimationModel.{Estimate, CargoKind, RouteCandidate}
    use SharedHtml.{Html, element, text, empty, attr, class_, id}
    use SharedHtmlComponents.{form, formField, alert}
    use SharedHtmlLayout.renderPage
    use SharedSecurity.Principal

    ///
    /// 入力途中の見積フォーム。
    ///
    /// **数値も文字列のまま持つ**。`1,200` のような入力をそのまま画面へ戻せなければ、
    /// 利用者は何を直せばよいか分からない（予約フォームと同じ形）。
    ///
    pub enum EstimateFormView({
        origin = String,
        destination = String,
        arrivalDeadline = String,
        cargoType = String,
        weight = String,
        errors = List[(String, String)]
    })

    pub def formOf(origin: String, destination: String, arrivalDeadline: String,
                   cargoType: String, weight: String,
                   errors: List[(String, String)]): EstimateFormView =
        EstimateFormView.EstimateFormView({
            origin = origin, destination = destination,
            arrivalDeadline = arrivalDeadline, cargoType = cargoType,
            weight = weight, errors = errors
        })

    pub def emptyForm(): EstimateFormView = formOf("", "", "", "GENERAL", "", Nil)

    pub def withError(view: EstimateFormView, error: (String, String)): EstimateFormView =
        let EstimateFormView.EstimateFormView(v) = view;
        EstimateFormView.EstimateFormView({ errors = v#errors ::: error :: Nil | v })

    def errorsFor(view: EstimateFormView, field: String): List[String] =
        let EstimateFormView.EstimateFormView(v) = view;
        v#errors |> List.filterMap(match (f, msg) -> if (f == field) Some(msg) else None)

    ///
    /// 場所の選択。
    ///
    /// **UN/LOCODE を手入力させない**。営業担当者に 5 文字のコードの暗記を
    /// 求めるのは業務の要求ではない（予約フォームと同じ判断）。
    ///
    def locationSelect(name: String, label: String, selected: String,
                       locations: List[(String, String)], errors: List[String]): Html =
        let cls = if (List.isEmpty(errors)) "form-select" else "form-select is-invalid";
        let blank = element("option", attr("value", "") :: Nil, text("選択してください") :: Nil);
        let options = locations |> List.map(match (code, locationName) -> {
            let attrs = attr("value", code) ::
                        (if (code == selected) attr("selected", "selected") :: Nil else Nil);
            element("option", attrs, text("${locationName} (${code})") :: Nil)
        });
        element("div", class_("mb-3") :: Nil,
            element("label", attr("for", name) :: class_("form-label") :: Nil,
                text(label) :: Nil) ::
            element("select", id(name) :: attr("name", name) :: class_(cls) :: Nil,
                blank :: options) ::
            (errors |> List.map(msg ->
                element("div", class_("invalid-feedback") :: Nil, text(msg) :: Nil))))

    def cargoKindSelect(view: EstimateFormView): Html =
        let EstimateFormView.EstimateFormView(v) = view;
        let errors = errorsFor(view, "cargoType");
        let cls = if (List.isEmpty(errors)) "form-select" else "form-select is-invalid";
        let option = k -> {
            let value = EstimationModel.cargoKindToPersisted(k);
            let attrs = attr("value", value) ::
                        (if (value == v#cargoType) attr("selected", "selected") :: Nil else Nil);
            element("option", attrs, text(EstimationModel.cargoKindLabel(k)) :: Nil)
        };
        element("div", class_("mb-3") :: Nil,
            element("label", attr("for", "cargoType") :: class_("form-label") :: Nil,
                text("貨物種別") :: Nil) ::
            element("select", id("cargoType") :: attr("name", "cargoType") ::
                              class_(cls) :: Nil,
                EstimationModel.allCargoKinds() |> List.map(option)) ::
            (errors |> List.map(msg ->
                element("div", class_("invalid-feedback") :: Nil, text(msg) :: Nil))))

    ///
    /// 見積フォーム。
    ///
    /// **危険物申告の入力欄は出さない**（US01 受入基準 6 は未充足）。
    /// 見積の段階で必要なのは種別だけであり、割増はそれで決まる。
    /// 申告情報（クラス・UN 番号・品名）は予約時に要る（US05 で実装済み）。
    /// **要らない情報を先に聞くと、電話口の即答という利用場面が壊れる**。
    ///
    def newForm(view: EstimateFormView, locations: List[(String, String)],
                csrfToken: Option[String]): Html =
        let EstimateFormView.EstimateFormView(v) = view;
        element("div", Nil,
            element("h1", class_("h4 mb-3") :: Nil, text("輸送見積の作成") :: Nil) ::
            element("p", class_("text-muted small") :: Nil,
                text("表示される料金は概算です。実際の運賃は航路・季節・船腹の状況で変動します") :: Nil) ::
            form("/estimates", "post", csrfToken,
                locationSelect("origin", "出発地", v#origin, locations,
                               errorsFor(view, "origin")) ::
                locationSelect("destination", "目的地", v#destination, locations,
                               errorsFor(view, "destination")) ::
                formField("arrivalDeadline", "希望期限（YYYY-MM-DD）", v#arrivalDeadline,
                          errorsFor(view, "arrivalDeadline")) ::
                cargoKindSelect(view) ::
                formField("weight", "重量（kg）", v#weight, errorsFor(view, "weight")) ::
                element("button", attr("type", "submit") :: class_("btn btn-primary") :: Nil,
                    text("見積を作成") :: Nil) ::
                element("a", attr("href", "/estimates") ::
                             class_("btn btn-link") :: Nil,
                    text("一覧へ戻る") :: Nil) :: Nil) :: Nil)

    pub def renderNew(p: Option[Principal], view: EstimateFormView,
                      locations: List[(String, String)], csrfToken: Option[String]): String =
        renderPage("輸送見積の作成", p, "/estimates", csrfToken,
                   newForm(view, locations, csrfToken))

    // ---- 一覧 ----

    def estimateRow(e: Estimate): Html =
        let estimateNumber = EstimationModel.estimateIdOf(e);
        element("tr", Nil,
            element("td", Nil,
                element("a", attr("href", "/estimates/${EstimationModel.estimateIdOf(e)}") :: Nil,
                    text(estimateNumber) :: Nil) :: Nil) ::
            element("td", Nil, text(EstimationModel.originOf(e)) :: Nil) ::
            element("td", Nil, text(EstimationModel.destinationOf(e)) :: Nil) ::
            element("td", Nil, text(EstimationModel.arrivalDeadlineOf(e)) :: Nil) ::
            element("td", Nil,
                text(EstimationModel.cargoKindLabel(EstimationModel.cargoKindOf(e))) :: Nil) ::
            // **最安値を一覧に出す**。詳細を開かずに「いくらくらいか」が分かる。
            // 電話口では、まず桁が合っているかを確かめたい
            element("td", Nil, text(cheapestText(e)) :: Nil) :: Nil)

    ///
    /// 最安の候補の金額。
    ///
    /// **候補が無ければ金額を出さない**。0 円と書くと「無料」に読める。
    ///
    def cheapestText(e: Estimate): String =
        EstimationModel.candidatesOf(e)
            |> List.map(EstimationModel.candidateCostOf)
            |> List.minimum
            |> Option.map(SharedMoney.toYenText)
            |> Option.getWithDefault("—")

    pub def index(estimates: List[Estimate], notice: Option[String]): Html =
        let body =
            if (List.isEmpty(estimates))
                element("p", class_("text-muted") :: Nil,
                    text("作成された見積はありません。[新規見積作成] から作成してください") :: Nil)
            else
                element("table", class_("table table-striped align-middle") :: Nil,
                    element("thead", Nil,
                        element("tr", Nil,
                            element("th", Nil, text("見積番号") :: Nil) ::
                            element("th", Nil, text("出発地") :: Nil) ::
                            element("th", Nil, text("目的地") :: Nil) ::
                            element("th", Nil, text("希望期限") :: Nil) ::
                            element("th", Nil, text("貨物種別") :: Nil) ::
                            element("th", Nil, text("最安概算") :: Nil) :: Nil) :: Nil) ::
                    element("tbody", id("estimate-list") :: Nil,
                        List.map(estimateRow, estimates)) :: Nil);
        element("div", Nil,
            (match notice {
                case None    => empty()
                case Some(m) => alert("success", m)
             }) ::
            element("div", class_("d-flex justify-content-between align-items-center mb-3") :: Nil,
                element("h1", class_("h4 mb-0") :: Nil, text("輸送見積一覧") :: Nil) ::
                element("a", class_("btn btn-primary") :: attr("href", "/estimates/new") :: Nil,
                    text("新規見積作成") :: Nil) :: Nil) ::
            body :: Nil)

    pub def renderIndex(p: Option[Principal], estimates: List[Estimate],
                        csrfToken: Option[String], notice: Option[String]): String =
        renderPage("輸送見積一覧", p, "/estimates", csrfToken, index(estimates, notice))

    // ---- 詳細 ----

    def field(label: String, value: String): List[Html] =
        element("dt", class_("col-sm-3") :: Nil, text(label) :: Nil) ::
        element("dd", class_("col-sm-9") :: Nil, text(value) :: Nil) :: Nil

    /// 重量はグラム保持。**桁の扱いはドメインに置く**（IT8 レビュー）
    def weightText(e: Estimate): String =
        EstimationModel.weightText(EstimationModel.weightGramsOf(e))

    def candidateRow(c: RouteCandidate): Html =
        element("tr", Nil,
            element("td", Nil,
                text(EstimationModel.candidateVoyageNumbersOf(c) |> List.join(" → ")) :: Nil) ::
            element("td", Nil,
                text(match EstimationModel.candidateTransitPortsOf(c) {
                         case Nil   => "直行"
                         case ports => ports |> List.join(" → ")
                     }) :: Nil) ::
            element("td", Nil,
                text("${EstimationModel.candidateTransitDaysOf(c)} 日") :: Nil) ::
            element("td", Nil,
                text(SharedMoney.toYenText(EstimationModel.candidateCostOf(c))) :: Nil) :: Nil)

    ///
    /// 見積詳細。
    ///
    /// **候補が無いことを明示する**（US01 受入基準 5）。0 件の表を出して黙ると、
    /// 営業は「まだ計算中なのか」「壊れているのか」を判断できない。
    ///
    pub def detail(e: Estimate): Html =
        let candidates = EstimationModel.candidatesOf(e);
        let candidateBlock =
            if (List.isEmpty(candidates))
                element("div", class_("alert alert-warning") :: Nil,
                    text("希望期限に間に合う経路がありません。" +
                         "期限を延ばすか、担当の経路設計者にご相談ください") :: Nil)
            else
                element("table", class_("table table-striped align-middle") :: Nil,
                    element("thead", Nil,
                        element("tr", Nil,
                            element("th", Nil, text("航海番号") :: Nil) ::
                            element("th", Nil, text("経由港") :: Nil) ::
                            element("th", Nil, text("所要日数") :: Nil) ::
                            element("th", Nil, text("概算料金") :: Nil) :: Nil) :: Nil) ::
                    element("tbody", id("candidate-list") :: Nil,
                        List.map(candidateRow, candidates)) :: Nil);
        element("div", Nil,
            element("h1", class_("h4 mb-3") :: Nil,
                text("見積 ${EstimationModel.estimateIdOf(e)}") :: Nil) ::
            element("dl", class_("row") :: Nil,
                field("出発地", EstimationModel.originOf(e)) :::
                field("目的地", EstimationModel.destinationOf(e)) :::
                field("希望期限", EstimationModel.arrivalDeadlineOf(e)) :::
                field("貨物種別",
                      EstimationModel.cargoKindLabel(EstimationModel.cargoKindOf(e))) :::
                field("重量", weightText(e)) :::
                field("状態", EstimationModel.statusLabel(EstimationModel.statusOf(e)))) ::
            element("h2", class_("h5 mt-4 mb-2") :: Nil, text("ルート候補") :: Nil) ::
            element("p", class_("text-muted small") :: Nil,
                text("料金は概算です。実際の運賃は航路・季節・船腹の状況で変動します") :: Nil) ::
            candidateBlock ::
            element("a", attr("href", "/estimates") :: class_("btn btn-link") :: Nil,
                text("一覧へ戻る") :: Nil) :: Nil)

    pub def renderDetail(p: Option[Principal], e: Estimate,
                         csrfToken: Option[String]): String =
        renderPage("見積詳細", p, "/estimates", csrfToken, detail(e))

    /// 見積が見つからないときの画面
    pub def renderNotFound(p: Option[Principal], csrfToken: Option[String]): String =
        renderPage("見積が見つかりません", p, "/estimates", csrfToken,
            element("div", Nil,
                element("h1", class_("h4 mb-3") :: Nil, text("見積が見つかりません") :: Nil) ::
                element("p", class_("text-muted") :: Nil,
                    text("見積番号をご確認ください。一覧から選び直すこともできます") :: Nil) ::
                element("a", attr("href", "/estimates") :: class_("btn btn-link") :: Nil,
                    text("一覧へ戻る") :: Nil) :: Nil))
}
