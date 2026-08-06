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
    import java.util.concurrent.ExecutorService
    import java.util.concurrent.Callable
    import java.util.concurrent.Future
    import java.util.concurrent.TimeUnit
    import java.util.concurrent.TimeoutException
    import java.lang.Exception
    import java.net.InetSocketAddress
    import com.sun.net.httpserver.HttpServer
    import com.sun.net.httpserver.HttpExchange
    import com.sun.net.httpserver.HttpHandler
    import com.sun.net.httpserver.Headers
    import java.lang.{String => JString}
    import java.lang.{Object => JObject}

    use SharedHttp.{HttpMethod, Request, Response, fullRequest, parseQuery, parseQueryPairs, plain, status, body, headers}
    use SharedHttpRuntimeGuard.{RuntimeLimits, Permits, createPermits, tryEnter, leave,
                                timeoutSecondsOf, tooBusy, timedOut}
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
    pub enum RunningServer(HttpServer, Int32, String, ExecutorService)

    /// サーバのポート番号
    pub def port(s: RunningServer): Int32 =
        let RunningServer.RunningServer(_, p, _, _) = s;
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
        let RunningServer.RunningServer(_, _, id, _) = s;
        id

    ///
    /// **`limits` は防御そのものである**。IT1 から未実装だった同時実行上限・
    /// バックプレッシャ・タイムアウトを、ここで一度だけ適用する
    /// （TS12。[バックエンドアーキテクチャ](../../../../../../docs/design/architecture_backend.md)）。
    ///
    /// 防御をルートごとに書かないのは、**書き漏らしたルートが無防備になる**
    /// ためである。認可をルーティング表の宣言にしたのと同じ判断であり、
    /// ここは「全リクエストが必ず通る 1 点」である。
    ///
    pub def start(portNumber: Int32, instanceIdValue: String, runtimeLimits: RuntimeLimits,
                  dispatchFn: Request -> Response \ IO): RunningServer \ IO =
        let address = new InetSocketAddress(portNumber);
        let server = HttpServer.create(address, 0);
        let permits = createPermits(runtimeLimits);
        // タイムアウトの監視に使う。**リクエストを処理するスレッドとは別**にする。
        // 同じスレッドで待つと、待っている間そのスレッドが解放されない
        let worker = Executors.newVirtualThreadPerTaskExecutor();
        let timeoutSec = Int32.toInt64(timeoutSecondsOf(runtimeLimits));
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
                        let request = SharedHttp.requestWithFormPairs(
                            m, requestPath, Map.empty(), queryMap, formMap, formPairs, headerMap);
                        guarded(permits, worker, timeoutSec, dispatchFn, request)
                };
                writeResponse(exchange, res)
        };
        discard server.createContext("/", httpHandler);
        // 仮想スレッドの Executor を設定しないと全リクエストが直列処理される
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        let actualPort = server.getAddress().getPort();
        RunningServer.RunningServer(server, actualPort, instanceIdValue, worker)

    ///
    /// 防御を通してリクエストを処理する。
    ///
    /// 1. 枠を確保できなければ即座に `503`（待たない）
    /// 2. 別スレッドで処理し、上限時間を超えたら `504`
    /// 3. **どの経路を通っても枠を返す**
    ///
    /// 3 が最も壊れやすい。1 つでも漏れると枠が減り続け、最終的に
    /// すべてのリクエストが `503` になる。`RuntimeGuardTest` が
    /// 「拒否・タイムアウトの後に後続が成功すること」を固定している。
    ///
    def guarded(permits: Permits, worker: ExecutorService, timeoutSec: Int64,
                dispatchFn: Request -> Response \ IO, request: Request): Response \ IO =
        // **ヘルスチェックは枠を通さない**。過負荷で liveness が 503 を返すと、
        // オーケストレータは「混んでいるだけで生きている」インスタンスを再起動し、
        // 負荷が高いときにインスタンスが落ちて事態が悪化する
        // （`SharedHttpRuntimeGuard.isProbePath` の説明を参照）
        if (SharedHttpRuntimeGuard.isProbePath(SharedHttp.path(request)))
            dispatchFn(request)
        else if (not tryEnter(permits))
            tooBusy()
        else
            let result = runWithTimeout(worker, timeoutSec, dispatchFn, request);
            leave(permits);
            result

    ///
    /// 上限時間つきで処理する。
    ///
    /// **処理を別スレッドへ渡し、結果だけを待つ**。同じスレッドで実行すると
    /// 中断する手段がなく、滞留したリクエストがスレッドと接続を占有し続ける。
    ///
    /// `Callable` の戻り値は使わない。応答は `Ref` で受け取る。Flix から
    /// Java のジェネリクスへ型を通すより、**書き込み先を明示する方が読める**。
    /// （`Runnable` は使えない。`run` が Flix の予約語であり実装できない。）
    ///
    /// 超過した場合は `cancel(true)` で割り込みを入れる。**割り込みを入れないと
    /// 処理は走り続け、DB コネクションを掴んだままになる**。
    ///
    def runWithTimeout(worker: ExecutorService, timeoutSec: Int64,
                       dispatchFn: Request -> Response \ IO,
                       request: Request): Response \ IO =
        let cell = Ref.fresh(Static, None);
        let task = new Callable[JObject] {
            def call(_this: Callable[JObject]): JObject \ IO =
                Ref.put(Some(dispatchFn(request)), cell);
                checked_cast("done")
        };
        try {
            // **`submit` も `try` の中に置く**。サーバ停止後（`worker.shutdown()` 済み）に
            // 到達したリクエストでは `RejectedExecutionException` が飛ぶ。外に置くと
            // 例外が `guarded` を貫通し、**枠を返さないまま応答も書かずに接続が閉じる**
            // ——下のコメントが「最も診断しにくい壊れ方」として警戒しているものが、
            // まさにこの経路で起きる（IT7 レビュー・programmer 指摘）
            let future = worker.submit(task);
            discard future.get(timeoutSec, TimeUnit.SECONDS);
            match Ref.get(cell) {
                // `get` が返ったのに応答が無いのは実装の誤りである。
                // 既定値で塗り潰すと、**壊れていることが誰にも見えなくなる**
                case Some(res) => res
                case None      => plain(500, "Internal Server Error")
            }
        } catch {
            // `future` はこのスコープから見えないため取り消せない。**取り消しは
            // `get` が返らなかったことの後始末であり、`try` の内側で行う**
            case _: TimeoutException => timedOut()
            // **タイムアウト以外の例外も必ず捕まえる**。`get` はハンドラ内で
            // 起きた例外を `ExecutionException` に包んで投げ直す。捕まえないと
            // ハンドラから例外が抜け、**応答を書かないまま接続が閉じる**。
            // 呼び出し側からは「応答が返らない」——最も診断しにくい壊れ方になる。
            case _: Exception => plain(500, "Internal Server Error")
        }

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

    ///
    /// サーバを停止する。
    ///
    /// **タイムアウト監視の Executor も落とす**。`start` で作ったものを
    /// `stop` で返さないと、起動と停止を繰り返すテストで積み上がる。
    /// 生成と破棄は対称に書く。
    ///
    pub def stop(s: RunningServer): Unit \ IO =
        let RunningServer.RunningServer(server, _, _, worker) = s;
        server.stop(0);
        worker.shutdown()
}
