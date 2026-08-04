///
/// 経路割り当てのルートハンドラ（US08）。
///
/// 責務は 3 つに限る。
///
/// 1. リクエストから予約番号を取り出す
/// 2. 照会サービスを呼ぶ
/// 3. 結果を画面へ写す
///
/// 判断そのものはユースケースとドメインにある。ここに業務ルールを書かない。
///
mod RouteRoutes {
    use RoutingBookingRouteRequest.BookingRouteRequest
    use RoutingFindRouteCandidates.{RouteCandidatesResult, findFor}
    use RoutePages.EmptyReason
    use SharedHttp.{Request, Response, pathParam, html, htmlWithStatus}
    use SharedSession.Session
    use VoyageRepo.VoyageRepo

    ///
    /// 経路割り当て画面（`GET /bookings/{bookingId}/route`）。
    ///
    /// **読み取りのみ**である。集約の状態は変えない。
    ///
    pub def assignPage(req: Request): Response \ Session + BookingRouteRequest + VoyageRepo =
        let bookingId = pathParam(req, "bookingId") |> Option.getWithDefault("");
        // **形式検証を先に通す**。予約番号はパス変数として外から来る値であり、
        // 検証せずに渡すと SQL の型変換まで届く。IT4（`2026-02-30`）と
        // IT6（`?departureFrom=abc`）で 2 度、この経路から応答が返らなくなった。
        // `<input>` はブラウザ経由の入力しか守らず、URL は誰でも書ける（Try T5）
        if (not isBookingIdText(bookingId))
            notAvailable(bookingId)
        else
            let (result, request, candidates) = findFor(bookingId);
            match (result, request) {
                case (RouteCandidatesResult.Found, Some(r)) =>
                    html(RoutePages.renderAssign(Session.current(), r, candidates,
                                                 emptyReasonOf(candidates),
                                                 Session.csrfToken()))
                // 依頼は引けたが探索条件として成立しない（DB の値が壊れている）。
                // **候補 0 件として見せない**。条件を緩めても解決しないためである
                case (RouteCandidatesResult.RequestIsMalformed(_), _) => notAvailable(bookingId)
                case _                                               => notAvailable(bookingId)
            }

    ///
    /// 予約番号の形（`BK-` + 大文字英数字）。
    ///
    /// 規則そのものは共有カーネルの `isUpperAlnumCode` に持たせている。
    /// **Booking のモジュールを参照しない**（`arch-lint` 規約 4）。
    ///
    def isBookingIdText(value: String): Bool =
        SharedCalendarText.isUpperAlnumCode(20, value)

    ///
    /// 候補が 0 件である理由を決める。
    ///
    /// **航海が 1 件も無いのか、あるが合わないのか**で次の一手が変わる。
    /// 判定に `VoyageRepo.findAll` をもう一度使わないのは、同じ読み取りを
    /// 2 度行うことになるためである。照会サービスが候補を返した時点で
    /// 「航海があるか」は分かる——が、候補 0 件だとその情報が失われる。
    /// よってここで一度だけ件数を確かめる。
    ///
    def emptyReasonOf(candidates: List[RoutingRouteFinder.RouteCandidate]):
            EmptyReason \ VoyageRepo =
        if (not List.isEmpty(candidates))
            // 0 件でないなら理由は使われない。読み取りを増やさない
            EmptyReason.NoRouteSatisfiesRequest
        else if (List.isEmpty(VoyageRepo.findAll()))
            EmptyReason.NoVoyagesRegistered
        else
            EmptyReason.NoRouteSatisfiesRequest

    def notAvailable(bookingId: String): Response \ Session =
        htmlWithStatus(404, RoutePages.renderNotAvailable(Session.current(), bookingId,
                                                          Session.csrfToken()))
}
