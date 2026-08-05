///
/// 荷役作業の画面（US15。IT10）。
///
mod HandlingPages {
    use SharedHtml.{Html, attr, class_, element, empty, id, text}
    use SharedHtmlComponents.{alert, form}
    use SharedHtmlLayout.renderPage
    use SharedSecurity.Principal
    use HandlingModel.{HandlingActivity, HandlingType, HandlingValidity}

    /// 入力の保持（拒まれたときに打ち直させない）
    pub enum HandlingForm({
        trackingNumber = String,
        handlingType = String,
        location = String,
        completionTime = String,
        voyageNumber = String
    })

    pub def handlingForm(trackingNumber: String, handlingType: String, location: String,
                         completionTime: String, voyageNumber: String): HandlingForm =
        HandlingForm.HandlingForm({
            trackingNumber = trackingNumber, handlingType = handlingType,
            location = location, completionTime = completionTime,
            voyageNumber = voyageNumber
        })

    pub def emptyForm(): HandlingForm = handlingForm("", "RECEIVE", "", "", "")

    def formField(f: HandlingForm, name: String): String =
        let HandlingForm.HandlingForm(x) = f;
        match name {
            case "trackingNumber" => x#trackingNumber
            case "handlingType"   => x#handlingType
            case "location"       => x#location
            case "completionTime" => x#completionTime
            case _                => x#voyageNumber
        }

    // ---- 一覧 ----

    pub def renderIndex(p: Option[Principal], activities: List[HandlingActivity],
                        csrfToken: Option[String], notice: Option[String],
                        canRegister: Bool): String =
        renderPage("荷役管理", p, "/handling", csrfToken,
            element("div", Nil,
                (match notice {
                    case None    => empty()
                    case Some(m) => alert("info", m)
                 }) ::
                element("h1", class_("h4 mb-3") :: Nil, text("荷役管理") :: Nil) ::
                (if (canRegister)
                    element("p", Nil,
                        element("a", class_("btn btn-primary") ::
                                     attr("href", "/handling/new") :: Nil,
                            text("荷役を登録する") :: Nil) :: Nil)
                 else empty()) ::
                historyTable(activities) :: Nil))

    ///
    /// 荷役履歴。
    ///
    /// **1 件も無いときは表を出さない**。空の表は「読めなかった」のか
    /// 「まだ無い」のかが区別できない（IT9 の旅程と同じ判断）。
    ///
    def historyTable(activities: List[HandlingActivity]): Html =
        if (List.isEmpty(activities))
            element("p", class_("text-muted") :: id("handling-empty") :: Nil,
                text("荷役の記録はまだありません。") :: Nil)
        else
            element("table", class_("table table-sm") :: id("handling-history") :: Nil,
                element("thead", Nil,
                    element("tr", Nil,
                        th("追跡番号") :: th("種別") :: th("場所") :: th("航海番号") ::
                        th("作業日時") :: th("判定") :: Nil) :: Nil) ::
                element("tbody", Nil, activities |> List.map(row)) :: Nil)

    def th(label: String): Html =
        element("th", attr("scope", "col") :: Nil, text(label) :: Nil)

    def row(a: HandlingActivity): Html =
        element("tr", Nil,
            element("td", Nil, text(HandlingModel.trackingNumberOf(a)) :: Nil) ::
            element("td", Nil,
                text(HandlingModel.typeLabel(HandlingModel.handlingTypeOf(a))) :: Nil) ::
            element("td", Nil, text(HandlingModel.locationOf(a)) :: Nil) ::
            element("td", Nil,
                text(HandlingModel.voyageNumberOf(a) |> Option.getWithDefault("—")) :: Nil) ::
            element("td", Nil, text(HandlingModel.eventCompletionTimeOf(a)) :: Nil) ::
            element("td", Nil, validityBadge(HandlingModel.validityOf(a)) :: Nil) :: Nil)

    ///
    /// 判定のバッジ。**色だけに依存せずテキストを併記する**（アクセシビリティ）。
    ///
    def validityBadge(v: HandlingValidity): Html = match v {
        case HandlingValidity.Valid =>
            element("span", class_("badge bg-success") :: Nil, text("正常") :: Nil)
        case HandlingValidity.Warned =>
            element("span", class_("badge bg-warning text-dark") :: Nil,
                text("要確認") :: Nil)
        case HandlingValidity.Misrouted =>
            element("span", class_("badge bg-danger") :: Nil, text("経路逸脱") :: Nil)
    }

    // ---- 登録 ----

    pub def renderNew(p: Option[Principal], f: HandlingForm,
                      csrfToken: Option[String], errorMessage: Option[String]): String =
        renderPage("荷役の登録", p, "/handling", csrfToken,
            element("div", class_("row") :: Nil,
                element("div", class_("col-md-8 offset-md-2") :: Nil,
                    (match errorMessage {
                        case None    => empty()
                        case Some(m) => alert("danger", m)
                     }) ::
                    element("h1", class_("h4 mb-3") :: Nil, text("荷役の登録") :: Nil) ::
                    form("/handling", "post", csrfToken,
                        textField("追跡番号", "trackingNumber", f,
                                  "貨物に貼られた追跡番号（例: TRK-20260805-0001）") :::
                        typeField(f) :::
                        textField("作業場所", "location", f,
                                  "UN/LOCODE 5 文字（例: JPOSA）") :::
                        dateTimeField("作業日時", "completionTime", f) :::
                        textField("航海番号", "voyageNumber", f,
                                  "積込・荷降しのときは必須。受領のときは空にしてください") :::
                        (element("button", attr("type", "submit") ::
                                           class_("btn btn-primary") :: Nil,
                            text("登録する") :: Nil) :: Nil)) ::
                    element("a", class_("btn btn-link") ::
                                 attr("href", "/handling") :: Nil,
                        text("荷役管理へ戻る") :: Nil) :: Nil) :: Nil))

    def textField(label: String, name: String, f: HandlingForm, hint: String): List[Html] =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: attr("for", name) :: Nil,
                text(label) :: Nil) ::
            element("input", attr("type", "text") :: attr("name", name) ::
                             id(name) :: class_("form-control") ::
                             attr("value", formField(f, name)) :: Nil, Nil) ::
            element("div", class_("form-text") :: Nil, text(hint) :: Nil) :: Nil) :: Nil

    def dateTimeField(label: String, name: String, f: HandlingForm): List[Html] =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: attr("for", name) :: Nil,
                text(label) :: Nil) ::
            element("input", attr("type", "datetime-local") :: attr("name", name) ::
                             id(name) :: class_("form-control") ::
                             attr("value", formField(f, name)) :: Nil, Nil) :: Nil) :: Nil

    ///
    /// 作業種別。**引取（CLAIM）と通関（CUSTOMS）は出さない**——US16・IT11 である。
    /// 選べるのに動かない選択肢を出すと、利用者は壊れていると受け取る。
    ///
    def typeField(f: HandlingForm): List[Html] =
        let selected = formField(f, "handlingType");
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: attr("for", "handlingType") :: Nil,
                text("作業種別") :: Nil) ::
            element("select", attr("name", "handlingType") :: id("handlingType") ::
                              class_("form-select") :: Nil,
                typeOption("RECEIVE", HandlingType.Receive, selected) ::
                typeOption("LOAD", HandlingType.Load, selected) ::
                typeOption("UNLOAD", HandlingType.Unload, selected) :: Nil) :: Nil) :: Nil

    def typeOption(value: String, t: HandlingType, selected: String): Html =
        element("option",
            attr("value", value) ::
            (if (value == selected) attr("selected", "selected") :: Nil else Nil),
            text(HandlingModel.typeLabel(t)) :: Nil)
}
