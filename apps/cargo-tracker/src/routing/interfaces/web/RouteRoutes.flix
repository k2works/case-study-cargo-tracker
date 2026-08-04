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
    use SharedClock.Clock
    use SharedSession.Session
    use VoyageRepo.VoyageRepo

    ///
    /// 経路割り当て画面（`GET /bookings/{bookingId}/route`）。
    ///
    /// **読み取りのみ**である。集約の状態は変えない。
    ///
    pub def assignPage(req: Request): Response \ Session + BookingRouteRequest + VoyageRepo + Clock =
        let bookingId = pathParam(req, "bookingId") |> Option.getWithDefault("");
        // **形式検証を先に通す**。予約番号はパス変数として外から来る値であり、
        // 検証せずに渡すと SQL の型変換まで届く。IT4（`2026-02-30`）と
        // IT6（`?departureFrom=abc`）で 2 度、この経路から応答が返らなくなった。
        // `<input>` はブラウザ経由の入力しか守らず、URL は誰でも書ける（Try T5）
        if (not isBookingIdText(bookingId))
            notAvailable(bookingId)
        else
            // **`case _` が要らない形になった**（IT7 レビュー M10）。
            // 3 つ組だった頃は「あり得ない組み合わせ」を握り潰す枝が要り、
            // 枝を足し忘れても気付けなかった
            match findFor(bookingId) {
                case RouteCandidatesResult.Found(f) =>
                    html(RoutePages.renderAssign(Session.current(), f#request, f#candidates,
                                                 emptyReasonOf(f#hasVoyages),
                                                 Session.csrfToken()))
                // 依頼は引けたが探索条件として成立しない（DB の値が壊れている）。
                // **候補 0 件として見せない**。条件を緩めても解決しないためである
                case RouteCandidatesResult.RequestIsMalformed(_, _) => notAvailable(bookingId)
                case RouteCandidatesResult.RequestNotAvailable      => notAvailable(bookingId)
            }

    ///
    /// 予約番号として**あり得る形**か（大文字英数字とハイフン 1〜20 文字）。
    ///
    /// **`BK-` 接頭辞は見ない**。接頭辞は Booking の採番規則であり、
    /// Routing がそれを知ると BC 独立性（`arch-lint` 規約 4）が
    /// コメントの上だけで破れる。ここで断ちたいのは
    /// **検証を通さない値が SQL の型変換へ届く経路**であって、
    /// 予約番号の正しさではない。存在しない番号は ACL が `None` を返す
    /// （IT7 レビュー・architect 指摘。当初のコメントは接頭辞を見ると書いていたが、
    /// 実装は見ていなかった）。
    ///
    def isBookingIdText(value: String): Bool =
        SharedCalendarText.isUpperAlnumCode(20, value)

    ///
    /// 候補が 0 件である理由を決める。
    ///
    /// **航海が 1 件も無いのか、あるが合わないのか**で次の一手が変わる
    /// （航路管理へ導くか、営業への相談へ導くか）。
    ///
    /// 事実（航海が登録されているか）は照会サービスが返す。ここで
    /// `VoyageRepo.findAll` を呼び直さないのは、同じ読み取りを 2 度行うことになり、
    /// **1 度目と 2 度目の間に登録が入れば食い違う**ためである。
    ///
    def emptyReasonOf(hasVoyages: Bool): EmptyReason =
        if (hasVoyages) EmptyReason.NoRouteSatisfiesRequest
        else EmptyReason.NoVoyagesRegistered

    def notAvailable(bookingId: String): Response \ Session =
        htmlWithStatus(404, RoutePages.renderNotAvailable(Session.current(), bookingId,
                                                          Session.csrfToken()))
}
