///
/// 公開貨物追跡のルートハンドラ（入力アダプタ）。
///
/// リクエストからパスパラメータを取り出し、クエリサービスを呼び、画面関数の結果を返す。
/// ビジネスロジックは持たない。
///
mod TrackingPublicRoutes {
    use SharedHttp.{Request, Response, pathParam, queryParam, html}
    use TrackingReadDb.ReadDb
    use TrackingQuery.findByTrackingNumber
    use TrackingPublicPages.{renderShow, renderForm}

    /// `GET /public/tracking/{trackingNumber}`
    pub def show(req: Request): Response \ ReadDb =
        respond(pathParam(req, "trackingNumber"))

    ///
    /// `GET /public/tracking`（入力フォームの入口）。
    ///
    /// `?trackingNumber=` があれば照会する。GET で受けるのは、検索結果の URL を
    /// 共有・再訪できるようにするためである。
    ///
    pub def search(req: Request): Response \ ReadDb =
        respond(queryParam(req, "trackingNumber"))

    /// 追跡番号（未指定・空文字を含む）から応答を組み立てる
    def respond(trackingNumber: Option[String]): Response \ ReadDb =
        match trackingNumber {
            case Some(num) if num != "" => html(renderShow(num, findByTrackingNumber(num)))
            case _                      => html(renderForm())
        }
}
