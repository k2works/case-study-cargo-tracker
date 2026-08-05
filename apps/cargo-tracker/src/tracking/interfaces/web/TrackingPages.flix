///
/// 認証つき追跡詳細の画面（US17。IT11）。
///
/// **公開追跡（`TrackingPublicPages`）と分ける**。見せる相手が違う——
/// 公開側は荷主・荷受人が未認証で開くもので、操作を持たない。
/// こちらは追跡管理者が状態を手で直す画面であり、フォームを持つ。
///
/// 公開側の画面関数に `canUpdate` を足して分岐させる形は採らない。
/// **未認証の画面に操作を出さないことが引数の渡し方に依存する**——
/// 関数を分けておけば型で守れる。
///
/// こちらの画面には**分岐を置かない**。ここへ到達できること自体が
/// 認可（`trackerOnly`）を通ったことであり、画面側でもう一度判定すると
/// **条件が 2 箇所に散る**（IT9 H1・IT10 M9 と同じ形）。
///
mod TrackingPages {
    use SharedHtml.{Html, attr, class_, element, empty, id, text}
    use SharedHtmlComponents.{alert, form, statusBadge}
    use SharedHtmlLayout.renderPage
    use SharedSecurity.Principal
    use TrackingView.{TrackingDetailView, TrackingEventView}

    ///
    /// 追跡の入口（US17。IT11）。
    ///
    /// **追跡管理者の作業はここから始まる**。荷主から追跡番号を告げられ、
    /// それを入れて状況を見る——荷役履歴を経由するのは遠回りである。
    ///
    /// 公開追跡（`/public/tracking`）と形は同じだが、**別の画面である**。
    /// 着地先が違う（認証つきの詳細）ため、同じ関数を共用すると
    /// 未認証の利用者を認証つきの画面へ送ることになる。
    ///
    pub def renderSearch(p: Option[Principal], csrfToken: Option[String]): String =
        renderPage("貨物追跡", p, "/tracking", csrfToken,
            element("div", class_("row") :: Nil,
                element("div", class_("col-md-6") :: Nil,
                    element("h1", class_("h4 mb-3") :: Nil, text("貨物追跡") :: Nil) ::
                    element("p", class_("text-muted") :: Nil,
                        text("荷主から伝えられた追跡番号を入力してください") :: Nil) ::
                    element("form", attr("method", "get") ::
                                    attr("action", "/tracking") :: Nil,
                        element("div", class_("input-group") :: Nil,
                            element("input", class_("form-control") ::
                                             id("trackingNumber") ::
                                             attr("type", "text") ::
                                             attr("name", "trackingNumber") ::
                                             attr("placeholder",
                                                  "TRK-YYYYMMDD-NNNN") :: Nil, Nil) ::
                            element("button", class_("btn btn-primary") ::
                                              attr("type", "submit") :: Nil,
                                text("追跡する") :: Nil) :: Nil) :: Nil) :: Nil) :: Nil))

    ///
    /// 追跡詳細。
    ///
    /// **見つからないときは追跡番号をそのまま出す**。打ち間違いに気付けるようにする
    /// （公開追跡と同じ判断）。
    ///
    ///
    /// 入力の保持（IT12・IT11 レビュー M12）。
    ///
    /// 拒まれたときに打ち直させない。荷役登録は「現場は片手で入力している」として
    /// 保持しているのに、こちらだけ消えていた。
    ///
    pub enum UpdateForm({ status = String, location = String, occurredAt = String })

    pub def updateForm(status: String, location: String, occurredAt: String): UpdateForm =
        UpdateForm.UpdateForm({ status = status, location = location,
                                occurredAt = occurredAt })

    pub def emptyUpdateForm(): UpdateForm = updateForm("", "", "")

    pub def renderShow(p: Option[Principal], trackingNumber: String,
                       view: Option[TrackingDetailView],
                       locations: List[(String, String)], form: UpdateForm,
                       csrfToken: Option[String], message: Option[String]): String =
        renderPage("追跡詳細", p, "/tracking", csrfToken,
            element("div", Nil,
                (match message {
                    case None    => empty()
                    case Some(m) => alert("danger", m)
                 }) ::
                element("h1", class_("h4 mb-3") :: Nil, text("追跡詳細") :: Nil) ::
                (match view {
                    case None =>
                        element("p", class_("text-muted") :: id("tracking-missing") :: Nil,
                            text("追跡番号「${trackingNumber}」の貨物は見つかりません。" +
                                 "番号をご確認ください") :: Nil)
                    case Some(v) =>
                        element("div", Nil,
                            summary(v) ::
                            updateSection(v, locations, form, csrfToken) ::
                            exceptionSection(v, locations, csrfToken) ::
                            eventTable(TrackingView.events(v)) :: Nil)
                 }) :: Nil))

    def summary(v: TrackingDetailView): Html =
        element("dl", class_("row") :: Nil,
            term("追跡番号") ::
            element("dd", class_("col-sm-9") :: id("tracking-number") :: Nil,
                text(TrackingView.trackingNumber(v)) :: Nil) ::
            term("現在の状態") ::
            element("dd", class_("col-sm-9") :: Nil,
                statusBadge(TrackingView.transportStatus(v)) :: Nil) ::
            term("推定到着日") ::
            element("dd", class_("col-sm-9") :: Nil,
                text(TrackingPublicPages.estimatedArrivalDisplay(
                    TrackingView.estimatedArrival(v))) :: Nil) :: Nil)

    def term(label: String): Html =
        element("dt", class_("col-sm-3 text-muted") :: Nil, text(label) :: Nil)

    ///
    /// 手動更新のフォーム（US17 受入基準 2）。
    ///
    /// **状態は選択肢から選ばせる**。輸送状態は 9 値の列挙であり、
    /// 手入力させると綴りの誤りが `UNKNOWN` として記録される。
    ///
    /// 選べるのは**段階を持つ 7 値**に限る。`EXCEPTION` と `UNKNOWN` は
    /// 手で付ける状態ではない——例外は US19/US20 の入口から入る。
    ///
    def updateSection(v: TrackingDetailView, locations: List[(String, String)],
                      f: UpdateForm, csrfToken: Option[String]): Html =
        let UpdateForm.UpdateForm(x) = f;
        element("div", class_("card mb-3") :: Nil,
            element("div", class_("card-body") :: Nil,
                element("h2", class_("h6") :: Nil, text("状態を更新する") :: Nil) ::
                element("p", class_("small text-muted") :: Nil,
                    text("荷役では記録されない状態変化（出港・入港など）を記録します") :: Nil) ::
                form("/tracking/${TrackingView.trackingNumber(v)}/status",
                     "post", csrfToken,
                    statusField(x#status) :::
                    locationField(locations, x#location, "location") :::
                    dateTimeField("発生日時", "occurredAt", x#occurredAt) :::
                    (element("button", attr("type", "submit") ::
                                       class_("btn btn-primary") :: Nil,
                        text("更新する") :: Nil) :: Nil)) :: Nil) :: Nil)

    ///
    /// 例外の記録と対応（US19・IT12）。
    ///
    /// **状態の手動更新と分ける**。例外は理由と対応の記録を伴う業務であり、
    /// 状態だけを付け替える操作とは規則が違う（IT11 で `EXCEPTION` を
    /// 手動更新の選択肢から外したのと同じ判断）。
    ///
    def exceptionSection(v: TrackingDetailView, locations: List[(String, String)],
                         csrfToken: Option[String]): Html =
        let number = TrackingView.trackingNumber(v);
        element("div", class_("card mb-3") :: Nil,
            element("div", class_("card-body") :: Nil,
                element("h2", class_("h6") :: Nil, text("例外を記録する") :: Nil) ::
                element("p", class_("small text-muted") :: Nil,
                    text("遅延など、輸送の妨げになっている事情を記録します。" +
                         "内容は荷主の追跡画面にそのまま出ます") :: Nil) ::
                form("/tracking/${number}/exceptions", "post", csrfToken,
                    exceptionTypeField() :::
                    locationField(locations, "", "exceptionLocation") :::
                    dateTimeField2("発生日時", "occurredAt", "exceptionOccurredAt") :::
                    textAreaField("内容", "description",
                                  "荷主が読みます。何が起きたかを書いてください") :::
                    (element("button", attr("type", "submit") ::
                                       class_("btn btn-warning") :: Nil,
                        text("例外を記録する") :: Nil) :: Nil)) ::
                element("hr", Nil, Nil) ::
                element("h2", class_("h6") :: Nil, text("対応を記録する") :: Nil) ::
                form("/tracking/${number}/exceptions/resolution", "post", csrfToken,
                    dateField("新しい到着予定日", "revisedArrival") :::
                    textAreaField("対応方針", "resolutionNotes",
                                  "荷主が読みます。どう対応するかを書いてください") :::
                    (element("button", attr("type", "submit") ::
                                       class_("btn btn-outline-primary") :: Nil,
                        text("対応を記録する") :: Nil) :: Nil)) ::
                form("/tracking/${number}/exceptions/resolve", "post", csrfToken,
                    element("button", attr("type", "submit") ::
                                      class_("btn btn-outline-success mt-2") :: Nil,
                        text("例外を解決する（発生前の状態に戻す）") :: Nil) :: Nil)
                    :: Nil) :: Nil)

    def exceptionTypeField(): List[Html] =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") ::
                             attr("for", "exceptionType") :: Nil,
                text("例外の種別") :: Nil) ::
            // **本リリースは遅延のみ**。選べるのに動かない選択肢を出さない
            element("select", attr("name", "exceptionType") ::
                              id("exceptionType") :: class_("form-select") :: Nil,
                element("option", attr("value", "DELAY") :: Nil,
                    text("遅延") :: Nil) :: Nil) :: Nil) :: Nil

    def textAreaField(label: String, name: String, hint: String): List[Html] =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: attr("for", name) :: Nil,
                text(label) :: Nil) ::
            element("textarea", attr("name", name) :: id(name) ::
                                class_("form-control") :: attr("rows", "2") :: Nil,
                text("") :: Nil) ::
            element("div", class_("form-text") :: Nil, text(hint) :: Nil) :: Nil) :: Nil

    def dateField(label: String, name: String): List[Html] =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: attr("for", name) :: Nil,
                text(label) :: Nil) ::
            element("input", attr("type", "date") :: attr("name", name) ::
                             id(name) :: class_("form-control") :: Nil, Nil) :: Nil) :: Nil

    def statusField(selected: String): List[Html] =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") ::
                             attr("for", "transportStatus") :: Nil,
                text("新しい状態") :: Nil) ::
            element("select", attr("name", "transportStatus") ::
                              id("transportStatus") :: class_("form-select") :: Nil,
                selectableStatuses() |> List.map(s -> statusOption(s, selected)))
                    :: Nil) :: Nil

    ///
    /// 手で選べる状態。
    ///
    /// **`EXCEPTION` と `UNKNOWN` を出さない**。例外は理由と対応の記録を伴う
    /// 業務であり（US19/US20）、状態だけを付け替える操作にしてはいけない。
    ///
    pub def selectableStatuses(): List[SharedTransportStatus.TransportStatus] =
        SharedTransportStatus.TransportStatus.NotReceived ::
        SharedTransportStatus.TransportStatus.Received ::
        SharedTransportStatus.TransportStatus.Loaded ::
        SharedTransportStatus.TransportStatus.OnboardCarrier ::
        SharedTransportStatus.TransportStatus.Unloaded ::
        SharedTransportStatus.TransportStatus.AwaitingClaim ::
        SharedTransportStatus.TransportStatus.Claimed :: Nil

    def statusOption(s: SharedTransportStatus.TransportStatus, selected: String): Html =
        let value = SharedTransportStatus.toPersisted(s);
        let attrs = attr("value", value) ::
                    (if (value == selected) attr("selected", "selected") :: Nil else Nil);
        element("option", attrs, text(SharedHtmlComponents.statusLabel(value)) :: Nil)

    ///
    /// 場所は**マスタから選ばせる**（IT11 レビュー）。
    ///
    /// 当初は自由入力で、集約は空文字しか見ていなかった。
    /// `tracking_handling_event.location_unlocode` は `location` への外部キーであり、
    /// `TOKYO` と打つと 500 になる——**荷役登録では同じ IT で塞いだ穴**（M17）が、
    /// 同じ IT で新設したこちらには入っていなかった。
    ///
    ///
    /// **`id` を呼び出し側が決める**（IT12・E2E が捕まえた）。
    ///
    /// 追跡詳細には「状態の更新」と「例外の記録」の 2 つのフォームがあり、
    /// どちらも場所を選ばせる。`id` を固定にすると**同じ id が 2 つ**になり、
    /// ラベルの `for` がどちらを指すか決まらない——支援技術と自動操作の
    /// 両方が最初の 1 つを掴む。E2E が「例外の内容が出ない」として落ちて分かった。
    ///
    def locationField(locations: List[(String, String)], selected: String,
                      fieldId: String): List[Html] =
        let blank = element("option", attr("value", "") :: Nil,
                            text("選択してください") :: Nil);
        let options = locations |> List.map(match (code, locationName) -> {
            let attrs = attr("value", code) ::
                        (if (code == selected) attr("selected", "selected") :: Nil else Nil);
            element("option", attrs, text("${locationName} (${code})") :: Nil)
        });
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: attr("for", fieldId) :: Nil,
                text("場所") :: Nil) ::
            element("select", attr("name", "location") :: id(fieldId) ::
                              class_("form-select") :: Nil, blank :: options) ::
            element("div", class_("form-text") :: Nil,
                text("出来事のあった港を選んでください") :: Nil) :: Nil) :: Nil

    /// **`id` を名前と分けられる版**（同じ名前の欄が 2 つあるページのため）
    def dateTimeField2(label: String, name: String, fieldId: String): List[Html] =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: attr("for", fieldId) :: Nil,
                text(label) :: Nil) ::
            element("input", attr("type", "datetime-local") :: attr("name", name) ::
                             id(fieldId) :: class_("form-control") :: Nil, Nil) :: Nil) :: Nil

    def dateTimeField(label: String, name: String, value: String): List[Html] =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: attr("for", name) :: Nil,
                text(label) :: Nil) ::
            element("input", attr("type", "datetime-local") :: attr("name", name) ::
                             id(name) :: class_("form-control") ::
                             attr("value", value) :: Nil, Nil) :: Nil) :: Nil

    ///
    /// 出来事の履歴。
    ///
    /// **公開追跡と同じ内容を出す**。追跡管理者が荷主からの問い合わせに
    /// 答えるとき、見ているものが違うと話が噛み合わない。
    ///
    def eventTable(events: List[TrackingEventView]): Html =
        if (List.isEmpty(events))
            element("p", class_("text-muted") :: id("tracking-events-empty") :: Nil,
                text("イベント履歴はまだありません") :: Nil)
        else
            element("table", class_("table table-sm") :: id("tracking-events") :: Nil,
                element("thead", Nil,
                    element("tr", Nil,
                        element("th", Nil, text("日時") :: Nil) ::
                        element("th", Nil, text("イベント") :: Nil) ::
                        element("th", Nil, text("場所") :: Nil) :: Nil) :: Nil) ::
                element("tbody", Nil, events |> List.map(eventRow)) :: Nil)

    def eventRow(e: TrackingEventView): Html =
        element("tr", Nil,
            element("td", Nil,
                text(TrackingPublicPages.formatDateTime(TrackingView.eventTime(e))) :: Nil) ::
            element("td", Nil,
                text(TrackingPublicPages.eventTypeLabel(TrackingView.eventType(e))) :: Nil) ::
            element("td", Nil,
                text(TrackingPublicPages.locationDisplay(
                    TrackingView.locationName(e),
                    TrackingView.locationUnlocode(e))) :: Nil) :: Nil)
}
