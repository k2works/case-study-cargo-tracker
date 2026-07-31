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
    use TrackingPublicPages.{renderShow, renderForm, renderEmptyInput}

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

    ///
    /// 追跡番号（未指定・空文字を含む）から応答を組み立てる。
    ///
    /// 空文字での送信は「追跡するを押したのに画面が戻っただけ」に見えるため、
    /// 未指定（入口を開いただけ）と区別して案内を出す。
    ///
    /// 前後の空白は取り除く。QR コードの読み取りやコピー＆ペーストで空白が混じることがあり、
    /// 利用者から見れば同じ番号を入力したつもりだからである。空白のみの入力は未入力として扱う。
    ///
    def respond(trackingNumber: Option[String]): Response \ ReadDb =
        match Option.map(String.trim, trackingNumber) {
            case Some(num) if num != "" => html(renderShow(num, findByTrackingNumber(num)))
            case Some(_)                => html(renderEmptyInput())
            case None                   => html(renderForm())
        }
}
