///
/// 認証つき追跡詳細のルート（US17。IT11）。
///
/// **公開追跡（`TrackingPublicRoutes`）と分ける**。公開側は未認証で開くもので、
/// 状態を変える操作を持たない。
///
mod TrackingRoutes {
    use SharedHttp.{Request, Response, formParam, html, pathParam}
    use SharedHttpAuth.{redirect, withHeader}
    use SharedSession.Session
    use TrackingReadDb.ReadDb
    use TrackingRepo.TrackingRepo
    use TrackingUpdateStatus.UpdateResult

    ///
    /// `GET /tracking`（追跡の入口）。
    ///
    /// `?trackingNumber=` があればそのまま詳細を出す。**GET で受ける**のは、
    /// 結果の URL を共有・再訪できるようにするためである（公開追跡と同じ判断）。
    ///
    pub def search(req: Request): Response \ Session + ReadDb =
        match SharedHttp.queryParam(req, "trackingNumber")
                |> Option.map(String.trim) {
            case Some(num) if num != "" => showNumber(num, None)
            case _                      => html(TrackingPages.renderSearch(
                                               Session.current(), Session.csrfToken()))
        }

    /// `GET /tracking/{trackingNumber}`
    pub def show(req: Request): Response \ Session + ReadDb =
        showWith(req, None)

    def showWith(req: Request, message: Option[String]): Response \ Session + ReadDb =
        showNumber(pathParam(req, "trackingNumber") |> Option.getWithDefault(""), message)

    def showNumber(number: String, message: Option[String]):
            Response \ Session + ReadDb =
        html(TrackingPages.renderShow(Session.current(), number,
                                      TrackingQuery.findByTrackingNumber(number),
                                      Session.csrfToken(), message))

    ///
    /// `POST /tracking/{trackingNumber}/status`（US17 受入基準 2・3）。
    ///
    /// **拒まれたら詳細画面を再描画する**。理由を出さずに一覧へ戻すと、
    /// 追跡管理者は何が悪かったのかを推測することになる。
    ///
    pub def updateStatus(req: Request):
            Response \ Session + ReadDb + TrackingRepo =
        let number = pathParam(req, "trackingNumber") |> Option.getWithDefault("");
        let statusText = param(req, "transportStatus");
        match SharedTransportStatus.fromPersisted(statusText) {
            // 選択肢から選ぶため通常は起きない。**既定値へ倒さない**
            case None => showWith(req, Some("状態を選択してください"))
            case Some(status) =>
                match TrackingUpdateStatus.update(number, status,
                        param(req, "location"), param(req, "occurredAt"),
                        latestEventTimeOf(number)) {
                    case UpdateResult.Updated  => redirect("/tracking/${number}")
                    case UpdateResult.NotFound =>
                        showWith(req, Some("その追跡番号の貨物は見つかりません"))
                    case UpdateResult.Rejected(e) => showWith(req, Some(messageOf(e)))
                }
        }

    ///
    /// 直近の出来事の時刻。
    ///
    /// **読み取り側（`ReadDb`）から取る**。集約が履歴を持つ形にすると、
    /// 状態を 1 つ変えるだけの操作で全履歴を読むことになる。
    ///
    def latestEventTimeOf(trackingNumber: String): Option[String] \ ReadDb =
        TrackingQuery.findByTrackingNumber(trackingNumber)
            |> Option.flatMap(v -> List.head(TrackingView.events(v)))
            |> Option.map(TrackingView.eventTime)

    def param(req: Request, name: String): String =
        formParam(req, name) |> Option.getWithDefault("")

    ///
    /// 拒否の理由を画面の言葉へ。**次に何をすればよいかを言う**。
    ///
    def messageOf(e: TrackingModel.TrackingError): String = match e {
        case TrackingModel.TrackingError.StatusWouldRewind =>
            "現在の状態より前の段階へ戻すことはできません。" +
            "誤りの訂正が必要な場合は、例外として記録してください"
        case TrackingModel.TrackingError.OccurredAtIsRequired =>
            "発生日時を入力してください"
        case TrackingModel.TrackingError.OccurredAtIsMalformed =>
            "発生日時は実在する日時で、直近の記録より後を指定してください" +
            "（例: 2026-08-05T10:00）"
        case TrackingModel.TrackingError.LocationIsRequired =>
            "場所（港コード）を入力してください"
        case _ =>
            "入力を確認してください"
    }
}
