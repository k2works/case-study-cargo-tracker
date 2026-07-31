///
/// 公開貨物追跡の画面（US18）。
///
/// 画面関数は Read Model を受け取り `Html` を返す**純粋関数**である。
/// 効果を要求しないため、render 結果を直接検証できる。
///
/// 認証を必要としない画面のため、共通ナビゲーション（Layout.nav）は表示されない
/// （`Layout.page` に `None` を渡すとナビが省かれる）。
///
mod TrackingPublicPages {
    use SharedHtml.{element, text, attr, class_, id}
    use SharedHtml.Html
    use SharedHtmlComponents.{statusBadge, form, formField}
    use SharedHtmlLayout.renderPage
    use TrackingView.TrackingDetailView
    use TrackingView.TrackingEventView

    /// 未検出時のメッセージ（docs/design/ui_design.md を文言の正典とする）
    pub def notFoundMessage(): String =
        "該当する貨物が見つかりません。追跡番号を確認の上、再度お試しください"

    ///
    /// 荷役種別の表示ラベル。
    ///
    /// 荷主・荷受人は社内システムの列挙値を知らないため、業務用語に翻訳して表示する。
    /// 英語表記を併記するのは、問い合わせ時に担当者と用語を突き合わせられるようにするため
    /// （docs/design/ui_design.md のワイヤーフレームが「積込（LOAD）」形式を正典としている）。
    ///
    pub def eventTypeLabel(eventType: String): String = match eventType {
        case "RECEIVE" => "受取（RECEIVE）"
        case "LOAD"    => "積込（LOAD）"
        case "UNLOAD"  => "荷降し（UNLOAD）"
        case "CUSTOMS" => "通関（CUSTOMS）"
        case "CLAIM"   => "引取（CLAIM）"
        case other     => other
    }

    ///
    /// 場所の表示。
    ///
    /// 都市名だけでは同名港の区別がつかないため UN/LOCODE を併記する
    /// （docs/design/ui_design.md）。どちらか一方しかない場合はある方だけを出す。
    ///
    pub def locationDisplay(name: String, unlocode: String): String =
        match (name == "", unlocode == "") {
            case (true, true)  => "-"
            case (true, false) => unlocode
            case (false, true) => name
            case (false, false) => "${name}（${unlocode}）"
        }

    ///
    /// 推定到着日の表示。
    ///
    /// 経路が未確定の貨物では到着予定が定まらないため「未定」と表示する。
    ///
    pub def estimatedArrivalDisplay(eta: String): String =
        if (eta == "") "未定" else formatDate(eta)

    ///
    /// 日付の表示（`YYYY-MM-DD`）。
    ///
    /// JDBC は `TIMESTAMP` を `2026-08-10 00:00:00` の形で返すが、到着予定は日付単位の
    /// 情報であり時刻部分は意味を持たないため落とす（docs/design/ui_design.md の書式）。
    ///
    pub def formatDate(timestamp: String): String =
        if (String.length(timestamp) >= 10)
            String.slice(start = 0, end = 10, timestamp)
        else
            timestamp

    /// 日時の表示（`YYYY-MM-DD HH:MM`）。秒は運用上の判断に影響しないため落とす
    pub def formatDateTime(timestamp: String): String =
        if (String.length(timestamp) >= 16)
            String.slice(start = 0, end = 16, timestamp)
        else
            timestamp

    /// イベント 1 行
    def eventRow(e: TrackingEventView): Html =
        element("tr", Nil,
            element("td", Nil, text(formatDateTime(TrackingView.eventTime(e))) :: Nil) ::
            element("td", Nil, text(eventTypeLabel(TrackingView.eventType(e))) :: Nil) ::
            element("td", Nil,
                text(locationDisplay(TrackingView.locationName(e),
                                     TrackingView.locationUnlocode(e))) :: Nil) :: Nil)

    /// イベント履歴テーブル
    def eventTable(events: List[TrackingEventView]): Html =
        if (List.isEmpty(events))
            element("p", class_("text-muted") :: Nil, text("イベント履歴はまだありません") :: Nil)
        else
            element("table", class_("table table-sm") :: Nil,
                element("thead", Nil,
                    element("tr", Nil,
                        element("th", Nil, text("日時") :: Nil) ::
                        element("th", Nil, text("イベント") :: Nil) ::
                        element("th", Nil, text("場所") :: Nil) :: Nil) :: Nil) ::
                element("tbody", Nil, events |> List.map(eventRow)) :: Nil)

    /// 追跡結果の本体
    def resultSection(view: TrackingDetailView): Html =
        element("div", class_("card") :: id("tracking-result") :: Nil,
            element("div", class_("card-body") :: Nil,
                element("h2", class_("h5") :: Nil,
                    text("追跡番号: ${TrackingView.trackingNumber(view)}") :: Nil) ::
                element("p", Nil,
                    text("ステータス: ") ::
                    statusBadge(TrackingView.transportStatus(view)) :: Nil) ::
                element("p", Nil, text("現在地: ${currentLocationDisplay(view)}") :: Nil) ::
                element("p", Nil,
                    text("推定到着日: ${estimatedArrivalDisplay(TrackingView.estimatedArrival(view))}") :: Nil) ::
                element("h3", class_("h6") :: Nil, text("イベント履歴") :: Nil) ::
                eventTable(TrackingView.events(view)) :: Nil) :: Nil)

    /// 現在地（最新イベントの場所）の表示
    def currentLocationDisplay(view: TrackingDetailView): String =
        match List.head(TrackingView.events(view)) {
            case Some(e) => locationDisplay(TrackingView.locationName(e),
                                            TrackingView.locationUnlocode(e))
            case None    => TrackingView.currentLocation(view)
        }

    ///
    /// 追跡番号の入力フォーム。
    ///
    /// GET で送信するのは、検索結果の URL を共有・再訪できるようにするためである。
    /// 状態を変更しないため CSRF トークンは付けない。
    ///
    def searchForm(trackingNumber: String): Html =
        form("/public/tracking", "get", None,
            formField("trackingNumber", "追跡番号", trackingNumber, Nil) ::
            element("button", class_("btn btn-primary") :: attr("type", "submit") :: Nil,
                text("照会") :: Nil) :: Nil)

    /// 未検出時の表示
    def notFoundSection(): Html =
        element("div", class_("alert alert-warning") :: id("tracking-not-found") :: Nil,
            text(notFoundMessage()) :: Nil)

    ///
    /// 公開追跡ページ全体。
    ///
    /// 反映遅延の注記を入れるのは、荷役登録からステータス反映まで最大 30 秒かかるためである
    /// （docs/design/ui_design.md の仕様）。
    ///
    pub def show(trackingNumber: String, result: Option[TrackingDetailView]): Html =
        let content = match result {
            case Some(view) => resultSection(view)
            case None       => notFoundSection()
        };
        pageBody(trackingNumber, content)

    /// 照会前（入力フォームのみ）の表示
    pub def formOnly(): Html =
        pageBody("", SharedHtml.empty())

    /// ページ本体の共通枠
    def pageBody(trackingNumber: String, content: Html): Html =
        element("div", Nil,
            element("h1", class_("h4 mb-3") :: Nil, text("CargoTracker 公開追跡") :: Nil) ::
            element("p", class_("text-muted") :: Nil,
                text("貨物の現在状況をご確認いただけます") :: Nil) ::
            searchForm(trackingNumber) ::
            content ::
            element("p", class_("text-muted small mt-3") :: Nil,
                text("最新の荷役作業が反映されるまで最大 30 秒かかります") :: Nil) ::
            element("p", class_("text-muted small") :: Nil,
                text("お問い合わせ: support@cargotracker.example.com") :: Nil) :: Nil)

    /// 照会結果ページを HTML 文字列へ描画する
    pub def renderShow(trackingNumber: String, result: Option[TrackingDetailView]): String =
        renderPage("公開追跡", None, "", show(trackingNumber, result))

    /// 入力フォームのみのページを HTML 文字列へ描画する
    pub def renderForm(): String =
        renderPage("公開追跡", None, "", formOnly())
}
