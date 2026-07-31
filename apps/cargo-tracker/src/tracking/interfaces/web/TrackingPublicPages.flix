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
    use SharedHtml.{element, text, class_, id}
    use SharedHtml.Html
    use SharedHtmlComponents.statusBadge
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

    /// イベント 1 行
    def eventRow(e: TrackingEventView): Html =
        element("tr", Nil,
            element("td", Nil, text(TrackingView.eventTime(e)) :: Nil) ::
            element("td", Nil, text(eventTypeLabel(TrackingView.eventType(e))) :: Nil) ::
            element("td", Nil, text(TrackingView.locationName(e)) :: Nil) :: Nil)

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
                element("p", Nil, text("現在地: ${TrackingView.currentLocation(view)}") :: Nil) ::
                element("h3", class_("h6") :: Nil, text("イベント履歴") :: Nil) ::
                eventTable(TrackingView.events(view)) :: Nil) :: Nil)

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
    pub def show(result: Option[TrackingDetailView]): Html =
        let content = match result {
            case Some(view) => resultSection(view)
            case None       => notFoundSection()
        };
        element("div", Nil,
            element("h1", class_("h4 mb-3") :: Nil, text("CargoTracker 公開追跡") :: Nil) ::
            element("p", class_("text-muted") :: Nil,
                text("貨物の現在状況をご確認いただけます") :: Nil) ::
            content ::
            element("p", class_("text-muted small mt-3") :: Nil,
                text("最新の荷役作業が反映されるまで最大 30 秒かかります") :: Nil) ::
            element("p", class_("text-muted small") :: Nil,
                text("お問い合わせ: support@cargotracker.example.com") :: Nil) :: Nil)

    /// ページを HTML 文字列へ描画する
    pub def renderShow(result: Option[TrackingDetailView]): String =
        renderPage("公開追跡", None, "", show(result))
}
