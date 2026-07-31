///
/// HTTP のリクエスト・レスポンス・ルーティングの型定義。
///
/// フレームワークを持たないため、JDK 内蔵の HttpServer の上に薄い層を自作する（ADR-0002）。
/// ルーティング表は ADT のリストとして定義し、**認可要件をルート定義の一部**として持たせる。
/// これにより認可設定の付け忘れが型で防がれ、ルーティング表自体を単体テストできる。
///
mod SharedHttp {
    use SharedSecurity.Role

    import java.net.URLDecoder
    import java.nio.charset.Charset


    /// HTTP メソッド
    pub enum HttpMethod with Eq, ToString {
        case Get
        case Post
        case Put
        case Delete
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

    /// リクエストからクエリパラメータを取り出す
    pub def queryParam(req: Request, name: String): Option[String] =
        let Request.Request(r) = req;
        Map.get(name, r#queryParams)

    /// リクエストを組み立てる
    pub def request(m: HttpMethod, p: String, params: Map[String, String],
                    headers: Map[String, String]): Request =
        requestWithQuery(m, p, params, Map.empty(), headers)

    /// クエリパラメータを含めてリクエストを組み立てる
    pub def requestWithQuery(m: HttpMethod, p: String, params: Map[String, String],
                             queryMap: Map[String, String],
                             headers: Map[String, String]): Request =
        Request.Request({
            method = m, path = p, pathParams = params,
            queryParams = queryMap, headers = headers
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

    ///
    /// 任意の Content-Type のテキストレスポンス（静的ファイル配信用）。
    ///
    /// キャッシュを 1 時間としているのは、Bootstrap のような不変の資材を
    /// 毎リクエスト転送しないためである。更新時はファイル名を変える運用とする。
    ///
    pub def binaryText(contentType: String, bodyText: String): Response =
        Response.Response({
            status = 200,
            headers = Map#{"Content-Type" => contentType, "Cache-Control" => "public, max-age=3600"},
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

    ///
    /// クエリ文字列を解析する。
    ///
    /// `application/x-www-form-urlencoded` として復号する（GET フォームの送信形式）。
    /// 値のない項目（`?a`）は空文字として扱い、同名キーは先勝ちとする。
    ///
    pub def parseQuery(raw: String): Map[String, String] \ IO =
        if (Object.isNull(raw) or raw == "")
            Map.empty()
        else
            String.split(regex = "&", raw)
                |> List.filter(pair -> pair != "")
                |> List.map(parsePair)
                |> List.foldLeft((acc, kv) -> {
                       let (k, v) = kv;
                       if (Map.memberOf(k, acc)) acc else Map.insert(k, v, acc)
                   }, Map.empty())

    /// `key=value` を復号する
    def parsePair(pair: String): (String, String) \ IO =
        let charset = Charset.forName("UTF-8");
        match String.indexOf(substr = "=", pair) {
            case None => (URLDecoder.decode(pair, charset), "")
            case Some(i) =>
                let k = String.slice(start = 0, end = i, pair);
                let v = String.slice(start = i + 1, end = String.length(pair), pair);
                (URLDecoder.decode(k, charset), URLDecoder.decode(v, charset))
        }
}
