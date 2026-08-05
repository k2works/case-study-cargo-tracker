///
/// 認証つき追跡詳細のルート（US17。IT11）。
///
/// **公開追跡（`TrackingPublicRoutes`）と分ける**。公開側は未認証で開くもので、
/// 状態を変える操作を持たない。
///
mod TrackingRoutes {
    use SharedHttp.{Request, Response, formParam, html, pathParam}
    use SharedLocationCatalog.LocationCatalog
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
    pub def search(req: Request): Response \ Session + ReadDb + LocationCatalog =
        match SharedHttp.queryParam(req, "trackingNumber")
                |> Option.map(String.trim) {
            case Some(num) if num != "" => showNumber(num, None)
            case _                      => html(TrackingPages.renderSearch(
                                               Session.current(), Session.csrfToken()))
        }

    /// `GET /tracking/{trackingNumber}`
    pub def show(req: Request): Response \ Session + ReadDb + LocationCatalog =
        showWith(req, None)

    def showWith(req: Request, message: Option[String]):
            Response \ Session + ReadDb + LocationCatalog =
        showNumber(pathParam(req, "trackingNumber") |> Option.getWithDefault(""), message)

    def showNumber(number: String, message: Option[String]):
            Response \ Session + ReadDb + LocationCatalog =
        html(TrackingPages.renderShow(Session.current(), number,
                                      TrackingQuery.findByTrackingNumber(number),
                                      LocationCatalog.all(),
                                      Session.csrfToken(), message))

    ///
    /// `POST /tracking/{trackingNumber}/status`（US17 受入基準 2・3）。
    ///
    /// **拒まれたら詳細画面を再描画する**。理由を出さずに一覧へ戻すと、
    /// 追跡管理者は何が悪かったのかを推測することになる。
    ///
    pub def updateStatus(req: Request):
            Response \ Session + ReadDb + TrackingRepo + LocationCatalog =
        let number = pathParam(req, "trackingNumber") |> Option.getWithDefault("");
        let statusText = param(req, "transportStatus");
        // **マスタに無い港を集約の手前で止める**（IT11 クローズ時レビュー）。
        // 集約は形式（5 文字・英数字）しか見ない——`TOKYO` は形式として正しく、
        // 実在するかはマスタが知っていることである（荷役登録と同じ形）
        if (not isKnownLocation(param(req, "location")))
            showWith(req, Some("取り扱っていない場所です。" +
                               "一覧から出来事のあった港を選んでください"))
        else
        match SharedTransportStatus.fromPersisted(statusText) {
            // 選択肢から選ぶため通常は起きない。**既定値へ倒さない**
            case None => showWith(req, Some("状態を選択してください"))
            case Some(status) =>
                match TrackingUpdateStatus.update(number, status,
                        param(req, "location"), param(req, "occurredAt")) {
                    case UpdateResult.Updated  => redirect("/tracking/${number}")
                    case UpdateResult.NotFound =>
                        showWith(req, Some("その追跡番号の貨物は見つかりません"))
                    case UpdateResult.Rejected(e) => showWith(req, Some(messageOf(e)))
                }
        }

    ///
    /// マスタにある港か（IT11 クローズ時レビュー）。
    ///
    /// **空は通す**。未入力は集約が「場所を選んでください」と言う——
    /// ここで拒むと次の一手が変わってしまう（`HandlingRoutes` と同じ判断）。
    ///
    def isKnownLocation(code: String): Bool \ LocationCatalog =
        String.trim(code) == "" or
        LocationCatalog.all() |> List.exists(match (known, _) ->
            known == String.toUpperCase(String.trim(code)))

    def param(req: Request, name: String): String =
        formParam(req, name) |> Option.getWithDefault("")

    ///
    /// 拒否の理由を画面の言葉へ。**次に何をすればよいかを言う**。
    ///
    def messageOf(e: TrackingModel.TrackingError): String = match e {
        // **実行できる手順を書く**（IT11 レビュー）。当初は「例外として記録して
        // ください」と案内していたが、例外の記録は US19（IT12）であり
        // **現時点では存在しない**。次の一手が存在しない案内は空振りである
        case TrackingModel.TrackingError.StatusWouldRewind =>
            "現在の状態より前の段階へ戻すことはできません。" +
            "誤って記録した場合は、追跡番号を控えて運用担当へご連絡ください"
        case TrackingModel.TrackingError.OccurredAtIsRequired =>
            "発生日時を入力してください"
        case TrackingModel.TrackingError.OccurredAtIsMalformed =>
            "発生日時に実在する日時を指定してください（例: 2026-08-05T10:00）"
        case TrackingModel.TrackingError.OccurredAtWouldRewind =>
            "発生日時が直近の記録より前です。" +
            "直近の記録より後の日時を入力してください"
        case TrackingModel.TrackingError.LocationIsRequired =>
            "場所を選んでください"
        case TrackingModel.TrackingError.LocationCodeIsMalformed =>
            "取り扱っていない場所です。一覧から出来事のあった港を選んでください"
        case TrackingModel.TrackingError.StatusIsNotSelectable =>
            "その状態は手動では設定できません。" +
            "異常が起きている場合は、追跡番号を控えて運用担当へご連絡ください"
        case _ =>
            "入力を確認してください"
    }
}
