///
/// JDK 内蔵 HttpServer の起動とリクエストのディスパッチ。
///
/// **スレッドモデル**（docs/design/architecture_backend.md の HTTP ランタイム設計）:
/// `setExecutor` を明示しないと accept ループと同一スレッドで全リクエストが直列処理されるため、
/// 仮想スレッドの Executor を必ず設定する。
///
mod SharedHttpServer {
    import java.lang.String
    import java.nio.charset.StandardCharsets
    import java.util.concurrent.Executors
    import java.net.InetSocketAddress
    import com.sun.net.httpserver.HttpServer
    import com.sun.net.httpserver.HttpExchange
    import com.sun.net.httpserver.HttpHandler
    import com.sun.net.httpserver.Headers
    import java.lang.{String => JString}

    use SharedHttp.{HttpMethod, Request, Response, fullRequest, parseQuery, parseQueryPairs, plain, status, body, headers}
    use SharedHttpRouter.{Route, routeHandler}

    /// 起動中のサーバ
    ///
    /// 起動中のサーバ（サーバ本体・実ポート・**起動ごとの識別子**）。
    ///
    /// 識別子を持たせるのは、**どの起動が応答したか**を呼び出し側が
    /// 突き合わせられるようにするためである。応答に添えるだけでは、
    /// 受け取った値が期待どおりかを判断する材料が誰にも無い
    /// （IT6 レビュー H5。ADR-0005 の対応表 H5・H6）。
    ///
    pub enum RunningServer(HttpServer, Int32, String)

    /// サーバのポート番号
    pub def port(s: RunningServer): Int32 =
        let RunningServer.RunningServer(_, p, _) = s;
        p

    /// メソッド文字列を ADT へ変換する
    def parseMethod(m: String): Option[HttpMethod] = match m {
        case "GET"    => Some(HttpMethod.Get)
        case "POST"   => Some(HttpMethod.Post)
        case "PUT"    => Some(HttpMethod.Put)
        case "DELETE" => Some(HttpMethod.Delete)
        case _        => None
    }

    /// 405 応答
    def methodNotAllowed(): Response = plain(405, "Method Not Allowed")

    /// 404 応答
    def notFound(): Response = plain(404, "Not Found")

    ///
    /// 解決済みのルートを、認可の関門を通してから実行する。
    ///
    /// **`guard` はハンドラを呼ぶ前に評価する**。順序が逆だと、認可で弾くはずの
    /// リクエストで副作用が起きる。
    ///
    /// ルートの解決そのものは合成ルートが行う。トランザクションを開くかどうかは
    /// ルートの `TxMode` で決まり、それはハンドラを呼ぶ前に読む必要があるためである。
    ///
    pub def runRoute(route: Route[ef], req: Request, params: Map[String, String],
                     guard: SharedHttp.RequiredRole -> Request -> Option[Response]):
            Response \ ef =
        let withParams = SharedHttp.withPathParams(req, params);
        match guard(SharedHttpRouter.routeRole(route))(withParams) {
            case Some(rejected) => rejected
            case None           => routeHandler(route)(withParams)
        }

    /// メソッド文字列を ADT へ変換する（合成ルートから使う）
    pub def toMethod(m: String): Option[HttpMethod] = parseMethod(m)

    /// 405 応答（合成ルートから使う）
    pub def methodNotAllowedResponse(): Response = methodNotAllowed()

    /// 404 応答（合成ルートから使う）
    pub def notFoundResponse(): Response = notFound()

    /// レスポンスを HttpExchange へ書き出す
    def writeResponse(exchange: HttpExchange, res: Response): Unit \ IO =
        let bodyText = body(res);
        let bytes = bodyText.getBytes(StandardCharsets.UTF_8);
        let respHeaders = exchange.getResponseHeaders();
        let headerList = Map.toList(headers(res));
        List.forEach(pair -> {
            let (name, value) = pair;
            respHeaders.set(name, value)
        }, headerList);
        let length = Int32.toInt64(Array.length(bytes));
        exchange.sendResponseHeaders(status(res), length);
        let out = exchange.getResponseBody();
        out.write(bytes);
        out.close()

    ///
    /// サーバを起動する。
    ///
    /// `handle` はリクエストのメソッドとパスからレスポンスを返す関数。
    /// 効果はすべて合成ルートで解決済みであることを前提とする（IO のみ残る）。
    ///
    /// 起動ごとの識別子（`X-App-Instance` として応答に添える値）
    pub def instanceId(s: RunningServer): String =
        let RunningServer.RunningServer(_, _, id) = s;
        id

    pub def start(portNumber: Int32, instanceIdValue: String,
                  dispatchFn: Request -> Response \ IO): RunningServer \ IO =
        let address = new InetSocketAddress(portNumber);
        let server = HttpServer.create(address, 0);
        let httpHandler = new HttpHandler {
            def handle(_this: HttpHandler, exchange: HttpExchange): Unit \ IO =
                let methodStr = exchange.getRequestMethod();
                let uri = exchange.getRequestURI();
                let requestPath = uri.getPath();
                let queryMap = parseQuery(uri.getRawQuery());
                let headerMap = readHeaders(exchange);
                // 状態を変更するメソッドのみボディを読む。GET のボディは仕様上意味を持たない
                // **ボディは 1 度しか読めない**。ペア列へ復号してから Map を作る
                let formPairs = match parseMethod(methodStr) {
                    case Some(HttpMethod.Get) => Nil
                    case _                    => parseQueryPairs(readBody(exchange))
                };
                let formMap = formPairs |> List.foldLeft((acc, kv) -> {
                    let (k, v) = kv;
                    // 先勝ち（`formParam` は先頭を返す）
                    if (Map.memberOf(k, acc)) acc else Map.insert(k, v, acc)
                }, Map.empty());
                let res = match parseMethod(methodStr) {
                    case None    => methodNotAllowed()
                    case Some(m) =>
                        dispatchFn(SharedHttp.requestWithFormPairs(
                            m, requestPath, Map.empty(), queryMap, formMap, formPairs, headerMap))
                };
                writeResponse(exchange, res)
        };
        discard server.createContext("/", httpHandler);
        // 仮想スレッドの Executor を設定しないと全リクエストが直列処理される
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        let actualPort = server.getAddress().getPort();
        RunningServer.RunningServer(server, actualPort, instanceIdValue)

    ///
    /// アプリケーションが参照するヘッダの一覧。
    ///
    /// 全ヘッダを写し取らないのは、使わない値をリクエストへ運ぶ意味がないうえ、
    /// 認証情報を含むヘッダが意図せずログや画面へ流れる経路を作らないためである。
    ///
    /// `com.sun.net.httpserver.Headers` は名前の大文字小文字を区別しないため、
    /// 小文字で列挙してそのまま `Request` のキーに使う。本モジュールは
    /// `java.lang.String` を import しており `String.toLowerCase` を呼べない（制約 15）。
    def interestingHeaders(): List[String] = "cookie" :: "hx-request" :: "content-type" :: Nil

    ///
    /// リクエストヘッダを読む。
    ///
    /// ヘッダ名は大文字小文字を区別しないため、小文字へ正規化して保持する。
    ///
    def readHeaders(exchange: HttpExchange): Map[String, String] \ IO =
        let requestHeaders = exchange.getRequestHeaders();
        collectHeaders(requestHeaders, interestingHeaders(), Map.empty())

    /// 指定したヘッダを順に読み取る（`List.foldLeft` は効果を持つ関数を受け取れない）
    def collectHeaders(requestHeaders: Headers, names: List[String],
                       acc: Map[String, String]): Map[String, String] \ IO =
        match names {
            case Nil => acc
            case name :: rest =>
                let value = requestHeaders.getFirst(name);
                let next = if (Object.isNull(value)) acc else Map.insert(name, value, acc);
                collectHeaders(requestHeaders, rest, next)
        }

    /// リクエストボディを読む
    def readBody(exchange: HttpExchange): String \ IO =
        let stream = exchange.getRequestBody();
        let bytes = stream.readAllBytes();
        stream.close();
        new JString(bytes, StandardCharsets.UTF_8)

    /// サーバを停止する
    pub def stop(s: RunningServer): Unit \ IO =
        let RunningServer.RunningServer(server, _, _) = s;
        server.stop(0)
}
