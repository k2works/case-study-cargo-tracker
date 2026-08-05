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

    ///
    /// 荷役を登録できるロールか（IT11・M9）。
    ///
    /// **画面とルートで別々に条件を書かない**。IT9 の H1・IT10 の M9 は
    /// どちらも「画面は出すがルートは拒む」形であり、原因は条件が 2 箇所に
    /// あることだった。ここを唯一の定義とし、ルートの認可宣言との一致を
    /// メタテスト（`AppRoutesTest`）で機械的に確かめる。
    ///
    /// `Admin` を含めるのは、認可の既定（`SharedHttpAuth.decide`）が
    /// **管理者をすべて通す**ためである。含めないと逆向きの食い違い
    /// （通れるのにボタンが出ない）になる。
    ///
    pub def canRegisterRole(r: SharedSecurity.Role): Bool =
        r == SharedSecurity.Role.Handler or r == SharedSecurity.Role.Admin

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
                        totalCount: Int32, filter: String,
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
                filterForm(filter) ::
                truncationNotice(List.length(activities), totalCount) ::
                historyTable(activities, filter) :: Nil))

    ///
    /// 追跡番号で絞り込む（IT11・H9）。
    ///
    /// 追跡管理者が開くのは「この番号の貨物は今どこか」に答えるためであり、
    /// 全貨物が時系列で混ざった一覧では業務が回らない。
    ///
    /// **GET のフォームにする**。絞り込みは状態を変えない操作であり、
    /// URL を共有できる形にしておくと問い合わせの引き継ぎに使える。
    /// CSRF トークンは付けない——`Components.form` を使わないのはこのためである。
    ///
    def filterForm(filter: String): Html =
        element("form", attr("method", "get") :: attr("action", "/handling") ::
                        class_("row row-cols-lg-auto g-2 align-items-center mb-3") :: Nil,
            element("div", class_("col-12") :: Nil,
                element("label", class_("visually-hidden") ::
                                 attr("for", "trackingNumber") :: Nil,
                    text("追跡番号") :: Nil) ::
                element("input", class_("form-control") :: id("trackingNumber") ::
                                 attr("type", "text") ::
                                 attr("name", "trackingNumber") ::
                                 attr("placeholder", "追跡番号（TRK-YYYYMMDD-NNNN）") ::
                                 attr("value", filter) :: Nil, Nil) :: Nil) ::
            element("div", class_("col-12") :: Nil,
                element("button", class_("btn btn-outline-secondary") ::
                                  attr("type", "submit") :: Nil,
                    text("絞り込む") :: Nil) ::
                (if (filter == "") empty()
                 else element("a", class_("btn btn-link") ::
                                   attr("href", "/handling") :: Nil,
                          text("絞り込みを解除") :: Nil)) :: Nil) :: Nil)

    ///
    /// 落とした件数（IT11・H9）。
    ///
    /// **黙って切らない**。上限で切った一覧は「これで全部」と読まれ、
    /// 「記録したはずの作業が無い」という問い合わせになる。
    ///
    def truncationNotice(shown: Int32, total: Int32): Html =
        if (total <= shown) empty()
        else element("p", class_("text-muted small") :: id("handling-truncated") :: Nil,
            text("新しい ${shown} 件を表示しています（該当 ${total} 件）。" +
                 "追跡番号で絞り込むと目的の記録が見つかります") :: Nil)

    ///
    /// 荷役履歴。
    ///
    /// **1 件も無いときは表を出さない**。空の表は「読めなかった」のか
    /// 「まだ無い」のかが区別できない（IT9 の旅程と同じ判断）。
    ///
    /// 絞り込んだ結果が 0 件のときは**絞り込んだ番号を文面に出す**——
    /// 打ち間違いに気付けるようにする（IT11・H9）。
    ///
    def historyTable(activities: List[HandlingActivity], filter: String): Html =
        if (List.isEmpty(activities))
            element("p", class_("text-muted") :: id("handling-empty") :: Nil,
                text(if (filter == "") "荷役の記録はまだありません。"
                     else "追跡番号「${filter}」の荷役の記録はありません。" +
                          "番号をご確認ください") :: Nil)
        else
            element("table", class_("table table-sm") :: id("handling-history") :: Nil,
                element("thead", Nil,
                    element("tr", Nil,
                        th("追跡番号") :: th("種別") :: th("場所") :: th("航海番号") ::
                        th("作業日時") :: th("担当者") :: th("判定") :: Nil) :: Nil) ::
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
            // IT10 に記録された行は担当者が空である（M10 以前）。
            // **空欄にせず「—」を出す**——空欄は「読めなかった」と区別できない
            element("td", Nil,
                text(operatorDisplay(HandlingModel.operatorNameOf(a))) :: Nil) ::
            element("td", Nil, validityBadge(HandlingModel.validityOf(a)) :: Nil) :: Nil)

    def operatorDisplay(name: String): String = if (name == "") "—" else name

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
                      locations: List[(String, String)],
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
                        locationField(f, locations) :::
                        dateTimeField("作業日時", "completionTime", f) :::
                        textField("航海番号", "voyageNumber", f,
                                  "積込・荷降しのときは必須。受領のときは空にしてください") :::
                        (element("button", attr("type", "submit") ::
                                           class_("btn btn-primary") :: Nil,
                            text("登録する") :: Nil) :: Nil)) ::
                    element("a", class_("btn btn-link") ::
                                 attr("href", "/handling") :: Nil,
                        text("荷役管理へ戻る") :: Nil) :: Nil) :: Nil))

    ///
    /// 作業場所は**マスタから選ばせる**（IT11・M17）。
    ///
    /// IT10 は 5 文字なら何でも通し、マスタに無いコードは外部キー違反で
    /// 500 になった。荷役作業員に UN/LOCODE の暗記を求めるのは業務の要求ではない
    /// ——予約登録は IT4 で同じ判断をしている。港名を添えて選ばせる。
    ///
    /// **選択肢を出しても、照合はサーバ側で行う**（`HandlingRoutes`）。
    /// 選択肢は誰でも書き換えられる。
    ///
    def locationField(f: HandlingForm, locations: List[(String, String)]): List[Html] =
        let selected = formField(f, "location");
        let blank = element("option", attr("value", "") :: Nil,
                            text("選択してください") :: Nil);
        let options = locations |> List.map(match (code, locationName) -> {
            let attrs = attr("value", code) ::
                        (if (code == selected) attr("selected", "selected") :: Nil else Nil);
            element("option", attrs, text("${locationName} (${code})") :: Nil)
        });
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: attr("for", "location") :: Nil,
                text("作業場所") :: Nil) ::
            element("select", attr("name", "location") :: id("location") ::
                              class_("form-select") :: Nil, blank :: options) ::
            element("div", class_("form-text") :: Nil,
                text("作業を行った港を選んでください") :: Nil) :: Nil) :: Nil

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
