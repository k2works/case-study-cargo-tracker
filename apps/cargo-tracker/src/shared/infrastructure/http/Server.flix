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

    use SharedHttp.{HttpMethod, Request, Response, requestWithQuery, parseQuery, plain, status, body, headers}
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
                        let req = requestWithQuery(m, path, params, queryMap, Map.empty());
                        routeHandler(route)(req)
                }
        }

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
                  dispatchFn: (String, String, Map[String, String]) -> Response \ IO): RunningServer \ IO =
        let address = new InetSocketAddress(portNumber);
        let server = HttpServer.create(address, 0);
        let httpHandler = new HttpHandler {
            def handle(_this: HttpHandler, exchange: HttpExchange): Unit \ IO =
                let methodStr = exchange.getRequestMethod();
                let uri = exchange.getRequestURI();
                let requestPath = uri.getPath();
                let queryMap = parseQuery(uri.getRawQuery());
                let res = dispatchFn(methodStr, requestPath, queryMap);
                writeResponse(exchange, res)
        };
        discard server.createContext("/", httpHandler);
        // 仮想スレッドの Executor を設定しないと全リクエストが直列処理される
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        let actualPort = server.getAddress().getPort();
        RunningServer.RunningServer(server, actualPort)

    /// サーバを停止する
    pub def stop(s: RunningServer): Unit \ IO =
        let RunningServer.RunningServer(server, _) = s;
        server.stop(0)
}
