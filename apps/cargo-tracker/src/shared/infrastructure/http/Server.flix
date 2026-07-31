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

    use SharedHttp.{HttpMethod, Request, Response, fullRequest, parseQuery, plain, status, body, headers}
    use SharedHttpRouter.{Route, resolve, routeHandler}

    /// 起動中のサーバ
    pub enum RunningServer(HttpServer, Int32)

    /// サーバのポート番号
    pub def port(s: RunningServer): Int32 =
        let RunningServer.RunningServer(_, p) = s;
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
    /// ルーティング表からレスポンスを決める。
    ///
    /// 認可（RequiredRole の検証）は IT3 の認証基盤実装時に追加する。
    /// 本イテレーションのルートはすべて `Anonymous` である。
    ///
    pub def dispatch(routes: List[Route[ef]], methodStr: String, path: String): Response \ ef =
        dispatchWithQuery(routes, methodStr, path, Map.empty())

    /// クエリパラメータを伴うディスパッチ
    pub def dispatchWithQuery(routes: List[Route[ef]], methodStr: String, path: String,
                              queryMap: Map[String, String]): Response \ ef =
        match parseMethod(methodStr) {
            case None => methodNotAllowed()
            case Some(m) =>
                match resolve(routes, m, path) {
                    case None => notFound()
                    case Some((route, params)) =>
                        let req = fullRequest(m, path, params, queryMap, Map.empty(), Map.empty());
                        routeHandler(route)(req)
                }
        }

    ///
    /// 組み立て済みのリクエストをディスパッチする。
    ///
    /// 認可の判定は呼び出し側（合成ルート）が `guard` として渡す。ここでルートを解決し、
    /// **ハンドラを呼ぶ前に** `guard` を通す。順序が逆だと、認可で弾くはずのリクエストで
    /// 副作用が起きる。
    ///
    pub def dispatchGuarded(routes: List[Route[ef]], req: Request,
                            guard: SharedHttp.RequiredRole -> Request -> Option[Response]):
            Response \ ef =
        match tryDispatch(routes, req, guard) {
            case Some(res) => res
            case None      => notFound()
        }

    ///
    /// ルートに一致した場合のみ処理する。
    ///
    /// 一致しない場合は `None` を返し、呼び出し側が別のルート表を試せるようにする。
    /// Flix の効果は不変（宣言と実際の効果が一致する必要がある）ため、要求する効果が
    /// 異なるハンドラを 1 つのルート表へ混在させられない。**効果集合ごとに表を分け、
    /// 順に試す**のが現時点で取りうる形である。
    ///
    pub def tryDispatch(routes: List[Route[ef]], req: Request,
                        guard: SharedHttp.RequiredRole -> Request -> Option[Response]):
            Option[Response] \ ef =
        match resolve(routes, SharedHttp.method(req), SharedHttp.path(req)) {
            case None => None
            case Some((route, params)) =>
                let withParams = SharedHttp.withPathParams(req, params);
                match guard(SharedHttpRouter.routeRole(route))(withParams) {
                    case Some(rejected) => Some(rejected)
                    case None           => Some(routeHandler(route)(withParams))
                }
        }

    /// メソッド文字列を ADT へ変換する（合成ルートから使う）
    pub def toMethod(m: String): Option[HttpMethod] = parseMethod(m)

    /// 405 応答（合成ルートから使う）
    pub def methodNotAllowedResponse(): Response = methodNotAllowed()

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
    pub def start(portNumber: Int32,
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
                let formMap = match parseMethod(methodStr) {
                    case Some(HttpMethod.Get) => Map.empty()
                    case _                    => parseQuery(readBody(exchange))
                };
                let res = match parseMethod(methodStr) {
                    case None    => methodNotAllowed()
                    case Some(m) =>
                        dispatchFn(fullRequest(m, requestPath, Map.empty(), queryMap, formMap, headerMap))
                };
                writeResponse(exchange, res)
        };
        discard server.createContext("/", httpHandler);
        // 仮想スレッドの Executor を設定しないと全リクエストが直列処理される
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        let actualPort = server.getAddress().getPort();
        RunningServer.RunningServer(server, actualPort)

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
        let RunningServer.RunningServer(server, _) = s;
        server.stop(0)
}
