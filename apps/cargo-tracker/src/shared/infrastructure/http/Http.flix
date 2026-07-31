///
/// HTTP のリクエスト・レスポンス・ルーティングの型定義。
///
/// フレームワークを持たないため、JDK 内蔵の HttpServer の上に薄い層を自作する（ADR-0002）。
/// ルーティング表は ADT のリストとして定義し、**認可要件をルート定義の一部**として持たせる。
/// これにより認可設定の付け忘れが型で防がれ、ルーティング表自体を単体テストできる。
///
mod SharedHttp {

    /// HTTP メソッド
    pub enum HttpMethod with Eq, ToString {
        case Get
        case Post
        case Put
        case Delete
    }

    /// ロール（docs/design/architecture_backend.md のロール設計を正典とする）
    pub enum Role with Eq, ToString {
        case Shipper
        case Consignee
        case Sales
        case Router
        case Handler
        case Tracker
        case Accountant
        case Admin
    }

    /// ルートに必要な認可要件
    pub enum RequiredRole {
        case Anonymous
        case RoleRequired(Role)
        case RoleAnyOf(List[Role])
    }

    /// リクエスト
    pub enum Request({
        method = HttpMethod,
        path = String,
        pathParams = Map[String, String],
        queryParams = Map[String, String],
        headers = Map[String, String]
    })

    /// レスポンス
    pub enum Response({
        status = Int32,
        headers = Map[String, String],
        body = String
    })

    /// パスパターン（`/public/tracking/{trackingNumber}` の形式）
    pub enum PathPattern(String)

    /// リクエストからパスパラメータを取り出す
    pub def pathParam(req: Request, name: String): Option[String] =
        let Request.Request(r) = req;
        Map.get(name, r#pathParams)

    /// リクエストのパスを取り出す
    pub def path(req: Request): String =
        let Request.Request(r) = req;
        r#path

    /// リクエストのメソッドを取り出す
    pub def method(req: Request): HttpMethod =
        let Request.Request(r) = req;
        r#method

    /// htmx からのリクエストかを判定する
    pub def isHtmx(req: Request): Bool =
        let Request.Request(r) = req;
        Map.memberOf("HX-Request", r#headers)

    /// リクエストを組み立てる
    pub def request(m: HttpMethod, p: String, params: Map[String, String],
                    headers: Map[String, String]): Request =
        Request.Request({
            method = m, path = p, pathParams = params,
            queryParams = Map.empty(), headers = headers
        })

    /// HTML レスポンス
    pub def html(bodyText: String): Response =
        Response.Response({
            status = 200,
            headers = Map#{"Content-Type" => "text/html; charset=utf-8"},
            body = bodyText
        })

    /// プレーンテキストレスポンス
    pub def plain(statusCode: Int32, bodyText: String): Response =
        Response.Response({
            status = statusCode,
            headers = Map#{"Content-Type" => "text/plain; charset=utf-8"},
            body = bodyText
        })

    /// JSON レスポンス
    pub def json(statusCode: Int32, bodyText: String): Response =
        Response.Response({
            status = statusCode,
            headers = Map#{"Content-Type" => "application/json; charset=utf-8"},
            body = bodyText
        })

    /// レスポンスのステータスコード
    pub def status(res: Response): Int32 =
        let Response.Response(r) = res;
        r#status

    /// レスポンスのボディ
    pub def body(res: Response): String =
        let Response.Response(r) = res;
        r#body

    /// レスポンスのヘッダ
    pub def headers(res: Response): Map[String, String] =
        let Response.Response(r) = res;
        r#headers
}
