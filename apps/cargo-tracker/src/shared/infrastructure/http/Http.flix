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

    ///
    /// ルートに必要な認可要件。
    ///
    /// `Eq` を導出するのは、**ルート定義の認可要件と設計の可否表を突き合わせる**
    /// テストのためである（IT3 レビュー M17）。比較できなければ、可否表と実装が
    /// ずれていることを人手のレビューでしか見つけられない。
    ///
    pub enum RequiredRole with Eq {
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
        formParams = Map[String, String],
        // 生のフォームペア列（**順序と重複を保つ**）。
        // 同名フィールドが並ぶ入力（寄港地の区間・複数選択のチェックボックス）は
        // `Map` では表現できない。`formParam` は先頭を返し、
        // `formParams` は並びを返す
        formPairs = List[(String, String)],
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
        fullRequest(m, p, params, queryMap, Map.empty(), headers)

    /// すべての要素を指定してリクエストを組み立てる
    pub def fullRequest(m: HttpMethod, p: String, params: Map[String, String],
                        queryMap: Map[String, String], formMap: Map[String, String],
                        headers: Map[String, String]): Request =
        requestWithFormPairs(m, p, params, queryMap, formMap, Map.toList(formMap), headers)

    /// 生のフォームペア列を含めてリクエストを組み立てる（サーバから使う）
    pub def requestWithFormPairs(m: HttpMethod, p: String, params: Map[String, String],
                                 queryMap: Map[String, String], formMap: Map[String, String],
                                 pairs: List[(String, String)],
                                 headers: Map[String, String]): Request =
        Request.Request({
            method = m, path = p, pathParams = params,
            queryParams = queryMap, formParams = formMap, formPairs = pairs,
            headers = headers
        })

    ///
    /// フォームパラメータを取り出す（POST ボディ）。
    ///
    /// `application/x-www-form-urlencoded` を前提とする。ファイルアップロードは扱わない。
    ///
    pub def formParam(req: Request, name: String): Option[String] =
        let Request.Request(r) = req;
        Map.get(name, r#formParams)

    ///
    /// 同名フィールドの値を**送信順**で取り出す。
    ///
    /// 寄港地の区間（`legDeparture` が区間の数だけ並ぶ）や複数選択の
    /// チェックボックス（`capabilities`）は、順序と重複に意味がある。
    /// `formParam` は先頭しか返さないため、これらには使えない。
    ///
    pub def formParams(req: Request, name: String): List[String] =
        let Request.Request(r) = req;
        r#formPairs |> List.filterMap(pair -> {
            let (k, v) = pair;
            if (k == name) Some(v) else None
        })

    /// パスパラメータを差し替えたリクエストを返す（ルート解決後に使う）
    pub def withPathParams(req: Request, params: Map[String, String]): Request =
        let Request.Request(r) = req;
        Request.Request({
            method = r#method, path = r#path, pathParams = params,
            queryParams = r#queryParams, formParams = r#formParams,
            formPairs = r#formPairs, headers = r#headers
        })

    /// リクエストヘッダを取り出す（名前は大文字小文字を区別しない）
    pub def header(req: Request, name: String): Option[String] =
        let Request.Request(r) = req;
        Map.get(String.toLowerCase(name), r#headers)

    /// HTML レスポンス
    pub def html(bodyText: String): Response =
        Response.Response({
            status = 200,
            headers = Map#{"Content-Type" => "text/html; charset=utf-8"},
            body = bodyText
        })

    ///
    /// ステータスを指定した HTML レスポンス。
    ///
    /// 404 でも**画面を返す**ために要る。本文が空の 404 は、利用者に
    /// 「壊れた」としか伝わらず、一覧へ戻る導線も示せない。
    ///
    pub def htmlWithStatus(statusCode: Int32, bodyText: String): Response =
        Response.Response({
            status = statusCode,
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
    /// `key=value&...` をペアの並びへ復号する（**順序と重複を保つ**）。
    ///
    /// `application/x-www-form-urlencoded` として復号する。
    /// 同名キーを潰さないのは、区間の入力（`legDeparture` を区間数だけ送る）を
    /// 受け取るためである。**順序が意味を持つ**入力はここを使う。
    ///
    pub def parseQueryPairs(raw: String): List[(String, String)] \ IO =
        if (Object.isNull(raw) or raw == "")
            Nil
        else
            String.split(regex = "&", raw)
                |> List.filter(pair -> pair != "")
                |> List.map(parsePair)

    ///
    /// クエリ文字列を `Map` へ解析する。
    ///
    /// 値のない項目（`?a`）は空文字として扱い、**同名キーは先勝ち**とする。
    /// 順序と重複が要る入力には `parseQueryPairs` を使う。
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
