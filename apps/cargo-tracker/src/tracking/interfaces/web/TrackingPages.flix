///
/// 認証つき追跡詳細の画面（US17。IT11）。
///
/// **公開追跡（`TrackingPublicPages`）と分ける**。見せる相手が違う——
/// 公開側は荷主・荷受人が未認証で開くもので、操作を持たない。
/// こちらは追跡管理者が状態を手で直す画面であり、フォームを持つ。
///
/// 同じ関数に `canUpdate` を足して分岐させると、**未認証の画面に
/// 操作を出さないことが引数の渡し方に依存する**——分けておけば型で守れる。
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
    pub def renderShow(p: Option[Principal], trackingNumber: String,
                       view: Option[TrackingDetailView], canUpdate: Bool,
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
                            (if (canUpdate) updateForm(v, csrfToken) else empty()) ::
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
    def updateForm(v: TrackingDetailView, csrfToken: Option[String]): Html =
        element("div", class_("card mb-3") :: Nil,
            element("div", class_("card-body") :: Nil,
                element("h2", class_("h6") :: Nil, text("状態を更新する") :: Nil) ::
                element("p", class_("small text-muted") :: Nil,
                    text("荷役では記録されない状態変化（出港・入港など）を記録します") :: Nil) ::
                form("/tracking/${TrackingView.trackingNumber(v)}/status",
                     "post", csrfToken,
                    statusField() :::
                    textField("場所（港コード）", "location",
                              "作業や出来事のあった港（例: JPOSA）") :::
                    dateTimeField("発生日時", "occurredAt") :::
                    (element("button", attr("type", "submit") ::
                                       class_("btn btn-primary") :: Nil,
                        text("更新する") :: Nil) :: Nil)) :: Nil) :: Nil)

    def statusField(): List[Html] =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") ::
                             attr("for", "transportStatus") :: Nil,
                text("新しい状態") :: Nil) ::
            element("select", attr("name", "transportStatus") ::
                              id("transportStatus") :: class_("form-select") :: Nil,
                selectableStatuses() |> List.map(statusOption)) :: Nil) :: Nil

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

    def statusOption(s: SharedTransportStatus.TransportStatus): Html =
        let value = SharedTransportStatus.toPersisted(s);
        element("option", attr("value", value) :: Nil,
            text(SharedHtmlComponents.statusLabel(value)) :: Nil)

    def textField(label: String, name: String, hint: String): List[Html] =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: attr("for", name) :: Nil,
                text(label) :: Nil) ::
            element("input", attr("type", "text") :: attr("name", name) ::
                             id(name) :: class_("form-control") :: Nil, Nil) ::
            element("div", class_("form-text") :: Nil, text(hint) :: Nil) :: Nil) :: Nil

    def dateTimeField(label: String, name: String): List[Html] =
        element("div", class_("mb-3") :: Nil,
            element("label", class_("form-label") :: attr("for", name) :: Nil,
                text(label) :: Nil) ::
            element("input", attr("type", "datetime-local") :: attr("name", name) ::
                             id(name) :: class_("form-control") :: Nil, Nil) :: Nil) :: Nil

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
