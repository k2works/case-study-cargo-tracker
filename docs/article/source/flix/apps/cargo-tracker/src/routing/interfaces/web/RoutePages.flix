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
                         csrfToken: Option[String], sortKey: String): String =
        renderPage("経路割り当て", viewer, "/bookings", csrfToken,
            assignBody(req, candidates, emptyReason, csrfToken, None, sortKey))

    ///
    /// 割り当てに失敗したときの再描画。
    ///
    /// **理由を出す**。「割り当てできません」だけでは経路設計者は
    /// 候補を選び直すべきか予約一覧へ戻るべきか判断できない。
    ///
    pub def renderAssignWithError(viewer: Option[Principal], req: RouteRequest,
                                  candidates: List[RouteCandidate],
                                  emptyReason: EmptyReason,
                                  csrfToken: Option[String], message: String,
                                  sortKey: String): String =
        renderPage("経路割り当て", viewer, "/bookings", csrfToken,
            assignBody(req, candidates, emptyReason, csrfToken, Some(message), sortKey))

    def assignBody(req: RouteRequest, candidates: List[RouteCandidate],
                   emptyReason: EmptyReason, csrfToken: Option[String],
                   notice: Option[String], sortKey: String): Html =
        element("div", Nil,
            heading(req) ::
            requestSummary(req) ::
            currentRouteSection(req) ::
            (if (List.isEmpty(candidates))
                 emptyNotice(req, emptyReason)
             else
                 candidateSection(req, sortCandidates(req, sortKey, candidates),
                                  csrfToken, notice, sortKey, List.length(candidates))) ::
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

    ///
    /// 現在割り当てられている経路（IT10・TS17。IT9 レビュー M10）。
    ///
    /// **未割り当てなら何も出さない**。「割り当てられていません」と書くより、
    /// 欄そのものが無いほうが「これから選ぶ」ことが伝わる。
    ///
    /// 選び直しは「本船がスキップになった」「スペースが取れなかった」ときに行う。
    /// **現行が分かって初めて**「今度は積替なしの便にしよう」と判断できる——
    /// IT9 までは予約詳細へ戻って控えてから開き直す必要があった。
    ///
    def currentRouteSection(req: RouteRequest): Html =
        match RoutingBookingRouteRequest.assignedVoyageNumbersOf(req) {
            case Nil     => empty()
            case numbers =>
                element("div", class_("alert alert-info") :: id("current-route") :: Nil,
                    text("現在割り当てられている経路: " + List.join(" → ", numbers)) :: Nil)
        }

    def field(label: String, value: String): List[Html] =
        element("dt", class_("col-sm-3 text-muted") :: Nil, text(label) :: Nil) ::
        element("dd", class_("col-sm-9") :: Nil, text(value) :: Nil) :: Nil

    // ---- 候補 ----

    ///
    /// 一度に出す候補の上限（IT10・TS17。IT9 レビュー M11）。
    ///
    /// 主要航路で航海登録が進むと候補は容易に 20〜30 件になる。
    /// 全件を一列で出すとスクロールして目で拾うことになり、選び間違いが起きる。
    ///
    /// **落とした件数は必ず出す**。黙って切ると「これで全部」と読まれる。
    ///
    def displayLimit(): Int32 = 20

    ///
    /// 並べ替え（IT9 レビュー M11・利用者代表）。
    ///
    /// 経路設計者が実際に見る順は「間に合う中で、積替が無いもの」
    /// 「間に合う中で、安いもの」である。既定は**推奨順**（積替の少なさ →
    /// 所要日数 → 費用）で、これが「間に合う中で積替が無いもの」にあたる。
    ///
    /// **未知のキーは推奨順に倒す**。URL は誰でも書ける——
    /// 倒さないと空の一覧が出て「候補が無い」と読まれる。
    ///
    def sortCandidates(req: RouteRequest, sortKey: String,
                       candidates: List[RouteCandidate]): List[RouteCandidate] =
        match sortKey {
            case "duration" => List.sortBy(c -> transitDaysOrMax(c), candidates)
            case "cost"     => List.sortBy(c -> costOf(req, c), candidates)
            case _          =>
                // 推奨順: 積替の少なさ → 所要日数 → 費用
                List.sortBy(c -> (RoutingRouteFinder.transshipmentCountOf(c),
                                  transitDaysOrMax(c), costOf(req, c)), candidates)
        }

    ///
    /// 所要日数。**読めない候補を先頭に出さない**ため、読めなければ最大値に倒す。
    ///
    /// 0 に倒すと「最速」として先頭へ来る——読めなかっただけの候補が
    /// 推奨の一番上に出るのは、壊れ方として最も分かりにくい。
    ///
    def transitDaysOrMax(c: RouteCandidate): Int32 =
        RoutingRouteFinder.transitDaysOf(c) |> Option.getWithDefault(2147483647)

    def costOf(req: RouteRequest, c: RouteCandidate): Int64 =
        RoutingRouteCost.quoteFor(
            RoutingBookingRouteRequest.tariffClassOf(req),
            RoutingBookingRouteRequest.weightGramsOf(req),
            c) |> SharedMoney.senOf

    pub def sortLabel(sortKey: String): String = match sortKey {
        case "duration" => "所要日数順"
        case "cost"     => "概算費用順"
        case _          => "推奨順"
    }

    /// 並べ替えの切り替え（`GET` のクエリ。状態を変えないためリンクでよい）
    def sortLinks(bookingId: String, current: String): Html =
        element("p", class_("mb-2") :: id("candidate-sort") :: Nil,
            text("並べ替え: ") ::
            sortLink(bookingId, "recommended", current) ::
            sortLink(bookingId, "duration", current) ::
            sortLink(bookingId, "cost", current) :: Nil)

    def sortLink(bookingId: String, key: String, current: String): Html =
        let active = (key == "recommended" and current != "duration" and current != "cost")
                     or key == current;
        if (active)
            element("span", class_("badge bg-secondary me-2") :: Nil,
                text(sortLabel(key)) :: Nil)
        else
            element("a", class_("me-2") ::
                         attr("href", "/bookings/${bookingId}/route?sort=${key}") :: Nil,
                text(sortLabel(key)) :: Nil)

    def candidateSection(req: RouteRequest, candidates: List[RouteCandidate],
                         csrfToken: Option[String], notice: Option[String],
                         sortKey: String, totalCount: Int32): Html =
        let bookingId = RoutingBookingRouteRequest.bookingIdOf(req);
        let shown = List.take(displayLimit(), candidates);
        let hidden = totalCount - List.length(shown);
        element("div", Nil,
            (match notice {
                case None    => empty()
                case Some(m) => alert("danger", m)
             }) ::
            element("h2", class_("h6 text-muted mb-2") :: Nil,
                text("利用可能な航路（${totalCount} 件・${sortLabel(sortKey)}）") :: Nil) ::
            sortLinks(bookingId, sortKey) ::
            (if (hidden > 0)
                element("p", class_("text-muted small") :: id("candidates-truncated") :: Nil,
                    text("上位 ${displayLimit()} 件を表示しています（残り ${hidden} 件は" +
                         "並べ替えを変えると入れ替わります）") :: Nil)
             else empty()) ::
            SharedHtmlComponents.form("/bookings/${bookingId}/route", "post", csrfToken,
                element("table", class_("table table-hover align-middle") ::
                                    id("route-candidates") :: Nil,
                    candidateHead() ::
                    element("tbody", Nil,
                        shown |> List.map(candidateRow(req))) :: Nil) ::
                element("button", attr("type", "submit") ::
                                  class_("btn btn-primary") :: Nil,
                    text("この経路を割り当て") :: Nil) :: Nil) :: Nil)

    def candidateHead(): Html =
        element("thead", Nil,
            element("tr", Nil,
                th("選択") :: th("航路番号") :: th("船名") :: th("経由港") :: th("出発予定") ::
                th("到着予定") :: th("所要日数") :: th("概算費用") :: Nil) :: Nil)

    def th(label: String): Html =
        element("th", attr("scope", "col") :: Nil, text(label) :: Nil)

    def candidateRow(req: RouteRequest, c: RouteCandidate): Html =
        let key = RoutingFindRouteCandidates.candidateKeyOf(c);
        // **現在の経路には印を付ける**（IT9 レビュー M10）。候補一覧に現行が
        // 混じっていても、どれがそれか分からなければ選び直しの判断ができない
        let isCurrent =
            RoutingBookingRouteRequest.assignedVoyageNumbersOf(req) == voyageNumbersOf(c);
        element("tr", (if (isCurrent) class_("table-info") :: Nil else Nil),
            // **ラジオで 1 件だけ選ばせる**（US09 受入基準 2）。値は候補のキーであり、
            // 区間の中身は送らない——送ると利用者が改竄できる
            element("td", Nil,
                // `required` を付ける（IT10・TS17）。**サーバ側の検査もある**——
                // ブラウザの検証は迂回できるため、`RouteRoutes.assign` が
                // 空のときのメッセージを持つ
                element("input", attr("type", "radio") :: attr("name", "candidate") ::
                                 attr("value", key) :: attr("required", "required") ::
                                 attr("aria-label", "この経路を選ぶ") :: Nil, Nil) :: Nil) ::
            element("td", Nil,
                text(voyageNumbersOf(c) |> List.join(" → ")) ::
                (if (isCurrent)
                    element("span", class_("badge bg-info text-dark ms-2") :: Nil,
                        text("現在の経路") :: Nil)
                 else empty()) :: Nil) ::
            element("td", Nil, text(vesselNamesText(c)) :: Nil) ::
            element("td", Nil, text(transitPortsText(c)) :: Nil) ::
            element("td", Nil, text(departureTimeOf(c)) :: Nil) ::
            element("td", Nil, arrivalCell(req, c) :: Nil) ::
            element("td", Nil, text(transitDaysText(c)) :: Nil) ::
            element("td", Nil, text(costText(req, c)) :: Nil) :: Nil)

    ///
    /// 概算費用（US08 受入基準 3。IT8 で充足）。
    ///
    /// **見積（US01）と同じ運賃表を使う**（`SharedFreightTariff`）。
    /// 経路割り当ての画面で見積と違う金額が出ると、
    /// 営業が荷主に伝えた額と社内の判断がずれる。
    ///
    /// 区間数を掛けるため、**積替のある候補は高くなる**。
    /// 「速いが高い」「遅いが安い」を並べて見せることが、
    /// 経路設計者が候補から選ぶための材料になる。
    ///
    /// **計算はドメインにある**（IT9・IT8 レビュー M5）。IT8 はここで
    /// 直接組み立てており、Estimation が domain に置いているのと非対称だった。
    ///
    def costText(req: RouteRequest, c: RouteCandidate): String =
        RoutingRouteCost.quoteFor(
            RoutingBookingRouteRequest.tariffClassOf(req),
            RoutingBookingRouteRequest.weightGramsOf(req),
            c)
            |> SharedMoney.toYenText

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
    /// 所要日数。**読めなければ日数を出さない**（IT8・TS14。IT7 レビュー M17）。
    ///
    /// IT7 は読めない場合に `0 日` と出していた。0 日は正当に起こりうる値
    /// （深夜出港・早朝着）であるため、**壊れた行が最速の候補として
    /// 一覧の先頭に見える**。日付が読めないのは候補の欠陥であり、
    /// 「速い」ではない。`—` にして**判断材料が無いことを示す**。
    ///
    def transitDaysText(c: RouteCandidate): String =
        match transitDaysOf(c) {
            case Some(days) => "${days} 日"
            case None       => "—"
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
        // **判定はドメインに置く**（IT7 レビュー M7）。ここで書き直していたため、
        // 同じ判定が 2 箇所にあり、片方（ドメイン側）は誰にも呼ばれていなかった
        if (RoutingRouteFinder.arrivesOnDeadline(
                RoutingBookingRouteRequest.arrivalDeadlineOf(req), c))
            element("span", class_("text-warning-emphasis") ::
                            attr("title", "到着予定日が希望期限と同じです") :: Nil,
                text("${arrival}（期限当日）") :: Nil)
        else
            text(arrival)

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
