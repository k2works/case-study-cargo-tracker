///
/// 経路割り当ての画面（US08）。`Html` を返す純粋関数である。
///
/// **効果を要求しない**。`render` した文字列を検証するだけで単体テストが書ける。
///
/// 本 IT の範囲は**候補の表示まで**である。ラジオ選択・選択中の航路詳細・
/// 「この経路を割り当てる」は経路の選択・確定（US09）であり IT8 で足す。
/// [UI 設計](../../../../../../docs/design/ui_design.md)「経路割り当て」の
/// ワイヤーフレームはその完成形を示している。
///
mod RoutePages {
    use RoutingBookingRouteRequest.RouteRequest
    use RoutingRouteFinder.RouteCandidate
    use RoutingRouteFinder.{legsOf, voyageNumbersOf, transitPortsOf, transitDaysOf,
                            departureTimeOf, arrivalTimeOf, legVesselNameOf}
    use SharedHtml.{Html, element, text, empty, attr, class_, id}
    use SharedHtmlComponents.alert
    use SharedHtmlLayout.renderPage
    use SharedSecurity.Principal

    ///
    /// 候補が 0 件である理由。
    ///
    /// **「航海が未登録」と「条件に合う経路が無い」を分ける**。同じ文言にすると、
    /// 経路設計者は「航海を登録すべきか」「条件を緩めるべきか」を判断できない
    /// （観点表の軸 3。IT6 の検索でも同じ分け方をした）。
    ///
    pub enum EmptyReason with Eq, ToString {
        /// 航海スケジュールが 1 件も登録されていない
        case NoVoyagesRegistered
        /// 航海はあるが条件を満たす組み合わせが無い
        case NoRouteSatisfiesRequest
    }

    ///
    /// 経路割り当て画面。
    ///
    /// `emptyReason` は候補が 0 件のときだけ意味を持つ。
    ///
    pub def renderAssign(viewer: Option[Principal], req: RouteRequest,
                         candidates: List[RouteCandidate],
                         emptyReason: EmptyReason,
                         csrfToken: Option[String]): String =
        renderPage("経路割り当て", viewer, "/bookings", csrfToken,
            assignBody(req, candidates, emptyReason))

    def assignBody(req: RouteRequest, candidates: List[RouteCandidate],
                   emptyReason: EmptyReason): Html =
        element("div", Nil,
            heading(req) ::
            requestSummary(req) ::
            (if (List.isEmpty(candidates))
                 emptyNotice(req, emptyReason)
             else
                 candidateSection(req, candidates)) ::
            backLink(req) :: Nil)

    def heading(req: RouteRequest): Html =
        element("h1", class_("h4 mb-3") :: Nil,
            text("経路割り当て　${RoutingBookingRouteRequest.bookingIdOf(req)}") :: Nil)

    ///
    /// 依頼の条件（US07 受入基準 1）。
    ///
    /// **予約番号を指定して出発地・仕向地・期限・貨物仕様を確認できる**状態を
    /// ここで満たす。IT6 では US07 の受入基準 1 が未充足のまま残っていた
    /// （引き継ぎ M5）。経路設計者は条件を見ずに経路を選べない。
    ///
    def requestSummary(req: RouteRequest): Html =
        element("dl", class_("row border rounded p-3 mb-4") :: id("route-request") :: Nil,
            field("出発地", RoutingBookingRouteRequest.originOf(req)) :::
            field("仕向地", RoutingBookingRouteRequest.destinationOf(req)) :::
            field("到着期限", RoutingBookingRouteRequest.arrivalDeadlineOf(req)) :::
            field("貨物仕様", RoutingBookingRouteRequest.cargoTypeLabelOf(req)))

    def field(label: String, value: String): List[Html] =
        element("dt", class_("col-sm-3 text-muted") :: Nil, text(label) :: Nil) ::
        element("dd", class_("col-sm-9") :: Nil, text(value) :: Nil) :: Nil

    // ---- 候補 ----

    def candidateSection(req: RouteRequest, candidates: List[RouteCandidate]): Html =
        element("div", Nil,
            element("h2", class_("h6 text-muted mb-2") :: Nil,
                text("利用可能な航路（${List.length(candidates)} 件・推奨順）") :: Nil) ::
            element("table", class_("table table-hover align-middle") ::
                                id("route-candidates") :: Nil,
                candidateHead() ::
                element("tbody", Nil,
                    candidates |> List.map(candidateRow(req))) :: Nil) ::
            selectionNote() :: Nil)

    def candidateHead(): Html =
        element("thead", Nil,
            element("tr", Nil,
                th("航路番号") :: th("船名") :: th("経由港") :: th("出発予定") ::
                th("到着予定") :: th("所要日数") :: Nil) :: Nil)

    def th(label: String): Html =
        element("th", attr("scope", "col") :: Nil, text(label) :: Nil)

    def candidateRow(req: RouteRequest, c: RouteCandidate): Html =
        element("tr", Nil,
            element("td", Nil, text(voyageNumbersOf(c) |> List.join(" → ")) :: Nil) ::
            element("td", Nil, text(vesselNamesText(c)) :: Nil) ::
            element("td", Nil, text(transitPortsText(c)) :: Nil) ::
            element("td", Nil, text(departureTimeOf(c)) :: Nil) ::
            element("td", Nil, arrivalCell(req, c) :: Nil) ::
            element("td", Nil, text("${transitDaysOf(c)} 日") :: Nil) :: Nil)

    ///
    /// 船名。
    ///
    /// **船社によって危険物の受け入れ可否・スペース確保のしやすさ・書類の
    /// 締め切りが違う**。「同じ所要日数なら A 社を選ぶ」という判断は日常的に
    /// 行われており、航路番号だけでは荷主にも社内にも説明できない
    /// （IT7 レビュー・user-rep 指摘。US07 受入基準 4）。
    ///
    def vesselNamesText(c: RouteCandidate): String =
        legsOf(c) |> List.map(legVesselNameOf) |> List.join(" → ")

    ///
    /// 経由港。直行便は空欄ではなく「直行」と書く。
    ///
    /// **積み替え回数の列は持たない**。経由港の数がそのまま積み替え回数であり、
    /// 6 列のうち 2 列が同じことを言っていた（IT7 レビュー・user-rep 指摘）。
    /// 空いた列を船名に譲る。
    ///
    def transitPortsText(c: RouteCandidate): String =
        match transitPortsOf(c) {
            case Nil   => "直行"
            case ports => ports |> List.join(" → ")
        }

    ///
    /// 到着予定。**期限当日に着く候補には注意を添える**。
    ///
    /// 期限内ではあるが余裕が無い。遅延が 1 日でも出れば間に合わない。
    /// [UI 設計](../../../../../../docs/design/ui_design.md) の「希望期限超過は
    /// ⚠ 付きで警告」を、**期限内のみを返す本 IT の仕様に合わせて読み替えた**もの。
    /// 超過する候補はそもそも提示しない。
    ///
    def arrivalCell(req: RouteRequest, c: RouteCandidate): Html =
        let arrival = arrivalTimeOf(c);
        if (SharedCalendarText.dateOf(arrival) ==
                RoutingBookingRouteRequest.arrivalDeadlineOf(req))
            element("span", class_("text-warning-emphasis") ::
                            attr("title", "到着予定日が希望期限と同じです") :: Nil,
                text("${arrival}（期限当日）") :: Nil)
        else
            text(arrival)

    ///
    /// 選択がまだできないことを明示する。
    ///
    /// **押せないことを黙っていると、利用者は壊れていると受け取る**。
    /// 未実装のナビ項目を「準備中」と示したのと同じ判断である（IT6 レビュー M3）。
    ///
    def selectionNote(): Html =
        alert("info", "経路の選択・確定は次のイテレーションで利用できるようになります。" +
                      "現在は候補の確認のみ行えます")

    // ---- 候補 0 件 ----

    ///
    /// 候補が 0 件のときの案内。
    ///
    /// **行き止まりを作らない**。理由に応じて次の一手を示す
    /// （[UI 設計](../../../../../../docs/design/ui_design.md)「候補 0 件」）。
    /// 条件を変えての再算出（US10）は将来リリースのため、
    /// **営業担当者への協議依頼**を導線として置く。
    ///
    def emptyNotice(req: RouteRequest, reason: EmptyReason): Html =
        let (message, guide) = match reason {
            case EmptyReason.NoVoyagesRegistered =>
                ("航海スケジュールが登録されていません",
                 "先に航路管理から航海スケジュールを登録してください")
            case EmptyReason.NoRouteSatisfiesRequest =>
                ("条件を満たす経路がありません",
                 "到着期限・貨物種別のいずれかを緩められないか、営業担当者に相談してください")
        };
        element("div", id("route-candidates-empty") :: Nil,
            alert("warning", message) ::
            element("p", class_("text-muted") :: Nil, text(guide) :: Nil) ::
            emptyActions(req, reason) :: Nil)

    def emptyActions(req: RouteRequest, reason: EmptyReason): Html =
        let voyageLink =
            element("a", class_("btn btn-outline-primary me-2") ::
                         attr("href", "/voyages") :: Nil,
                text("航路管理を開く") :: Nil);
        let consultLink =
            element("a", class_("btn btn-outline-secondary") ::
                         attr("href", "/bookings/" +
                              RoutingBookingRouteRequest.bookingIdOf(req)) :: Nil,
                text("予約内容を確認して営業担当者に相談する") :: Nil);
        match reason {
            case EmptyReason.NoVoyagesRegistered     =>
                element("div", Nil, voyageLink :: consultLink :: Nil)
            case EmptyReason.NoRouteSatisfiesRequest =>
                element("div", Nil, consultLink :: voyageLink :: Nil)
        }

    // ---- 共通 ----

    def backLink(req: RouteRequest): Html =
        element("p", class_("mt-4") :: Nil,
            element("a", attr("href", "/bookings/" +
                              RoutingBookingRouteRequest.bookingIdOf(req)) :: Nil,
                text("← 予約詳細へ戻る") :: Nil) :: Nil)

    ///
    /// 依頼が見つからない場合の画面。
    ///
    /// **404 でも画面を返す**。空の応答にすると利用者はブラウザの戻るしか
    /// 手段がなくなる（`BookingPages.renderNotFound` と同じ判断）。
    ///
    /// **「存在しない」と「まだ引き渡されていない」を書き分けない**。
    /// 経路設計者にとってはどちらも「今は作業できない」であり、
    /// 書き分けると**存在の有無が漏れる**（観点表の軸 6）。
    ///
    pub def renderNotAvailable(viewer: Option[Principal], bookingId: String,
                               csrfToken: Option[String]): String =
        renderPage("経路割り当て", viewer, "/bookings", csrfToken,
            element("div", Nil,
                element("h1", class_("h4 mb-3") :: Nil, text("経路割り当て") :: Nil) ::
                alert("warning",
                    "予約 ${bookingId} は経路設計の対象ではありません。" +
                    "経路設計者へ引き渡された予約のみ経路を割り当てられます") ::
                element("p", class_("mt-3") :: Nil,
                    element("a", attr("href", "/bookings") :: Nil,
                        text("← 予約一覧へ戻る") :: Nil) :: Nil) :: Nil))
}
