///
/// 公開貨物追跡のルートハンドラ（入力アダプタ）。
///
/// リクエストからパスパラメータを取り出し、クエリサービスを呼び、画面関数の結果を返す。
/// ビジネスロジックは持たない。
///
mod TrackingPublicRoutes {
    use SharedHttp.{Request, Response, pathParam, html}
    use TrackingReadDb.ReadDb
    use TrackingQuery.findByTrackingNumber
    use TrackingPublicPages.renderShow

    /// `GET /public/tracking/{trackingNumber}`
    pub def show(req: Request): Response \ ReadDb =
        let result = match pathParam(req, "trackingNumber") {
            case Some(num) => findByTrackingNumber(num)
            case None      => None
        };
        html(renderShow(result))
}
