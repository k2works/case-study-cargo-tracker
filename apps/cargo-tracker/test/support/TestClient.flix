///
/// HTTP レベルの統合テスト用クライアント。
///
/// JDK 内蔵の HttpClient を使う（WireMock 等の外部依存を持ち込まない）。
///
mod SharedHttpTestClient {
    import java.net.URI
    import java.net.http.HttpClient
    import java.net.http.HttpRequest
    import java.net.http.HttpResponse
    import java.net.http.HttpResponse$BodyHandlers
    import java.net.http.HttpRequest$BodyPublishers
    import java.net.http.HttpRequest$Builder
    import java.net.http.HttpClient$Redirect
    import java.util.Optional
    import java.time.Duration

    /// テスト用のレスポンス（ステータス・ボディ・主要ヘッダ）
    pub enum TestResponse(Int32, String, Map[String, String])

    /// ステータスコード
    pub def status(res: TestResponse): Int32 =
        let TestResponse.TestResponse(s, _, _) = res;
        s

    /// ボディ
    pub def body(res: TestResponse): String =
        let TestResponse.TestResponse(_, b, _) = res;
        b

    /// レスポンスヘッダ
    pub def header(res: TestResponse, name: String): Option[String] =
        let TestResponse.TestResponse(_, _, hs) = res;
        Map.get(String.toLowerCase(name), hs)

    /// GET リクエストを送る（認証情報は付けない）
    pub def get(portNumber: Int32, requestPath: String): TestResponse \ IO =
        getWith(portNumber, requestPath, Map.empty())

    ///
    /// ヘッダを指定して GET する。
    ///
    /// リダイレクトは追わない。302 とその Location を検証したいテストが多く、
    /// 自動追従すると認可の挙動が見えなくなるため。
    ///
    pub def getWith(portNumber: Int32, requestPath: String,
                    requestHeaders: Map[String, String]): TestResponse \ IO =
        let client = newClient();
        let uri = URI.create("http://127.0.0.1:${portNumber}${requestPath}");
        let base = HttpRequest.newBuilder(uri);
        let timed = base.timeout(requestTimeout());
        let builder = applyHeaders(timed, Map.toList(requestHeaders));
        let res = client.send(builder.build(), HttpResponse$BodyHandlers.ofString());
        toTestResponse(res)

    ///
    /// 応答を待つ上限。
    ///
    /// **無いとテストが永久に止まる**。サーバが応答を書かずに終わる種類の不具合
    /// （IT4・IT5 で観測した「応答が返らない」）は、タイムアウトが無いと
    /// 「失敗」ではなく「終わらない」として現れ、どのテストが原因かも分からない。
    /// 上限を置けば、**止まった経路が失敗として記録される**。
    ///
    /// 10 秒は、H2 の起動とマイグレーションを含む最も遅い経路でも十分に余る。
    ///
    def requestTimeout(): Duration \ IO = Duration.ofSeconds(10i64)

    /// リダイレクトを追わないクライアント（302 と Location を検証したいため）
    def newClient(): HttpClient \ IO =
        let clientBuilder = HttpClient.newBuilder();
        let noRedirect = clientBuilder.followRedirects(HttpClient$Redirect.NEVER);
        let timed = noRedirect.connectTimeout(requestTimeout());
        timed.build()

    /// フォーム送信（`application/x-www-form-urlencoded`）
    pub def post(portNumber: Int32, requestPath: String, formBody: String): TestResponse \ IO =
        postWith(portNumber, requestPath, formBody, Map.empty())

    /// ヘッダを指定してフォーム送信する
    pub def postWith(portNumber: Int32, requestPath: String, formBody: String,
                     requestHeaders: Map[String, String]): TestResponse \ IO =
        let client = newClient();
        let uri = URI.create("http://127.0.0.1:${portNumber}${requestPath}");
        let publisher = HttpRequest$BodyPublishers.ofString(formBody);
        // 呼び出しは 1 つずつ束縛する（1 行に連ねるとパースできない）。
        // `.POST(...)` は大文字始まりのためモジュール参照と解釈される。`method` を使う
        let newBuilder = HttpRequest.newBuilder(uri);
        let timed = newBuilder.timeout(requestTimeout());
        let posted = timed.method("POST", publisher);
        let base = posted.header("Content-Type", "application/x-www-form-urlencoded");
        let builder = applyHeaders(base, Map.toList(requestHeaders));
        let res = client.send(builder.build(), HttpResponse$BodyHandlers.ofString());
        toTestResponse(res)

    ///
    /// ステータスコードを検証し、**食い違ったらボディも見せて落とす**。
    ///
    /// `Assert.assertEq` はメッセージを取れないため、`expected 200, but got 403` だけが
    /// 残る。403 の本文には理由が書かれているのに、それが失敗の記録に残らない。
    /// 再現しない失敗（IT4 で 1 度だけ観測した公開追跡の 403）を次に見たとき、
    /// **記録だけで原因に辿り着ける**ようにする。
    ///
    pub def assertStatus(expected: Int32, res: TestResponse): Unit \ Assert =
        if (status(res) == expected)
            ()
        else if (instanceOf(res) == "(不明)")
            // **応答したのが自分たちのアプリではない**（IT11・Try T6）。
            // 診断ヘッダは全応答に付く（`Composition` の `withDiagnostics`）ため、
            // 無いということは別のプロセスが同じポートで応答している。
            // IT9 から 3 イテレーション続いた間欠失敗はこれだった——
            // テストは `localhost` へ繋ぎ、`localhost` は環境によって `::1` を先に引く。
            // サーバは IPv4 のワイルドカードに束縛するため、
            // **`::1` の同じ番号を握っている別プロセス**があると、そちらへ届く。
            Assert.fail("アプリ以外が応答しました。期待 ${expected} / 実際 ${status(res)}" +
                        " / 診断ヘッダ（X-App-Instance）が無い" +
                        " / 本文: ${body(res)}" +
                        " — 同じポートを別プロセスが握っている可能性があります")
        else
            Assert.fail("ステータスが期待と異なります。期待 ${expected} / 実際 ${status(res)}" +
                        " / 処理したルート: ${routeOf(res)}" +
                        " / 処理したインスタンス: ${instanceOf(res)}" +
                        " / 本文: ${body(res)}")

    ///
    /// ログインする（IT9・TS16 以降の唯一の入口）。
    ///
    /// **`GET /login` を先に踏む**。ログインの POST は CSRF トークンを要求する
    /// ようになった（二重送信 Cookie）。トークンは Cookie とフォームの両方に
    /// 置かれるため、**画面を開かずにログインすることはできない**——
    /// これは利用者の経路そのものである。
    ///
    /// **各テストが同じ手順を書き写さない**。IT9 で防御を入れたとき、
    /// 8 ファイルのログイン補助がすべて 403 になった。手順が 1 箇所にあれば
    /// 直すのも 1 箇所で済む（`escapeLike` が 3 箇所に増えた IT6 M6 の教訓）。
    ///
    /// 返すのはセッション Cookie（`Cookie` ヘッダに入れる形）である。
    ///
    pub def login(port: Int32, username: String, plainPassword: String):
            String \ Assert + IO =
        let page = get(port, "/login");
        assertStatus(200, page);
        let csrfCookie = cookiePairOf(page);
        let token = csrfTokenIn(body(page));
        let form = "username=" + username + "&password=" +
                   String.replace(src = " ", dst = "+", plainPassword) +
                   "&_csrf=" + token;
        let res = postWith(port, "/login", form, Map#{"Cookie" => csrfCookie});
        assertStatus(302, res);
        cookiePairOf(res)

    ///
    /// ログインを**試みる**（応答をそのまま返す）。
    ///
    /// 失敗を検証するテスト（誤ったパスワード・ロックアウト）が使う。
    /// `login` と違い、302 を期待せず応答を返す。
    ///
    /// **CSRF トークンの取得は共通**である。ログインが成功するか否かに関わらず、
    /// トークンが無ければ 403 になり、**認証の失敗と CSRF の失敗が区別できなくなる**。
    ///
    /// **`GET /login` に `assertStatus` を掛ける**（IT9 レビュー H6）。
    /// 掛けないと、記録済みの間欠 404 が「フォームに CSRF トークンが
    /// 見つからなかった」として現れ、**404 という事実が失敗記録から消える**。
    pub def attemptLogin(port: Int32, username: String, plainPassword: String):
            TestResponse \ Assert + IO =
        let page = get(port, "/login");
        assertStatus(200, page);
        let csrfCookie = cookiePairOf(page);
        let token = csrfTokenIn(body(page));
        let form = "username=" + username + "&password=" +
                   String.replace(src = " ", dst = "+", plainPassword) +
                   "&_csrf=" + token;
        postWith(port, "/login", form, Map#{"Cookie" => csrfCookie})

    ///
    /// `Set-Cookie` の `名前=値` の部分だけを取り出す（属性は落とす）。
    ///
    /// **失敗にルートとインスタンスを添える**（IT9 レビュー H6）。
    /// 共通化で 8 ファイル分の診断が一斉に弱くなっていた。`Set-Cookie` 欠落は
    /// 記録済みの間欠失敗そのものであり、どのインスタンスが応答したかが要る。
    ///
    def cookiePairOf(res: TestResponse): String \ Assert =
        match header(res, "Set-Cookie") {
            case None    =>
                Assert.fail("Set-Cookie が得られなかった" +
                            " / ステータス: ${status(res)}" +
                            " / 処理したルート: ${routeOf(res)}" +
                            " / 処理したインスタンス: ${instanceOf(res)}" +
                            " / 本文: ${body(res)}");
                ""
            case Some(v) => match String.indexOf(substr = ";", v) {
                case None    => v
                case Some(i) => String.slice(start = 0, end = i, v)
            }
        }

    /// フォームの隠しフィールドから CSRF トークンを取り出す
    def csrfTokenIn(html: String): String \ Assert =
        let marker = "name=\"_csrf\" value=\"";
        match String.indexOf(substr = marker, html) {
            case None => { Assert.fail("フォームに CSRF トークンが見つからなかった"); "" }
            case Some(i) =>
                let start = i + String.length(marker);
                String.slice(start = start, end = start + 64, html)
        }

    ///
    /// 同じ GET を並行に投げ、全応答を集める。
    ///
    /// **既存のテストは全てリクエストを 1 本ずつ順に送っている**。サーバは
    /// 仮想スレッドで並行処理するため（`SharedHttpServer.start`）、共有状態の取り違えや
    /// 応答の交差といった不具合は、逐次テストでは**原理的に検出できない**。
    ///
    /// IT4 で公開追跡の GET が 1 度だけ 403 を返す事象を観測したが、
    /// `Anonymous` のルートで 403 に至る経路は論理上存在しない。
    /// 「記録から辿れる」だけでは足りず、**再現させる手段**が要る。
    ///
    /// Java の `Runnable` は実装できない（`run` は Flix の予約語）。Flix の
    /// `spawn` と `Channel` で書く。
    pub def getConcurrently(portNumber: Int32, requestPath: String,
                            count: Int32): List[TestResponse] \ IO + Chan + NonDet =
        region rc {
            let (sender, receiver) = Channel.buffered(count);
            List.forEach(_ ->
                spawn {
                    Channel.send(get(portNumber, requestPath), sender)
                } @ rc,
                List.range(0, count));
            List.range(0, count) |> List.map(_ -> Channel.recv(receiver))
        }

    ///
    /// **異なる経路・異なる認証状態**のリクエストを並行に投げ、
    /// それぞれの応答が「自分の期待」を満たすかを確かめる。
    ///
    /// `getConcurrently` は同じ経路を同時に叩くため、**応答が入れ替わっても
    /// 気付けない**（どれも同じ内容だから）。IT4 の間欠 403 と IT5 の間欠 404 は
    /// いずれも「そのルートでは起こり得ない応答」であり、
    /// **別のリクエストの結果が返っている**線が濃い。それを捕まえるには、
    /// 区別できる応答を混ぜて投げる必要がある。
    ///
    /// 各要素は `(パス, Cookie, 期待ステータス, 本文に含まれるべき文字列)`。
    /// 戻り値は期待と食い違った要素の説明のリスト（空なら全て期待どおり）。
    ///
    /// **期待本文は `Option` にする**。当初は `String` で、空文字を渡すと
    /// `String.contains(substr = "", body)` が常に真になり、
    /// **本文の検証が黙って無効になった**。「ステータスだけで判定する」と
    /// 「本文を書き忘れた」が区別できない形であり、IT5 で 5 件見つかった
    /// 空虚なアサートを、ヘルパーが量産できる状態だった（IT6 レビュー H7）。
    /// `None` は「ステータスだけで判定する」という明示的な選択である。
    ///
    pub def probeConcurrently(portNumber: Int32,
                              requests: List[(String, Option[String], Int32, Option[String])],
                              rounds: Int32): List[String] \ IO + Chan + NonDet =
        region rc {
            let all = List.range(0, rounds) |> List.flatMap(_ -> requests);
            let count = List.length(all);
            let (sender, receiver) = Channel.buffered(count);
            List.forEach(spec ->
                spawn {
                    let (path, cookie, expectedStatus, expectedBody) = spec;
                    let headers = match cookie {
                        case None    => Map.empty()
                        case Some(c) => Map#{"Cookie" => c}
                    };
                    let res = getWith(portNumber, path, headers);
                    let problem =
                        if (status(res) != expectedStatus)
                            Some("${path}（認証: ${authLabel(cookie)}）は ${expectedStatus} を" +
                                 "期待したが ${status(res)} を返した。" +
                                 "処理したルート: ${routeOf(res)}（インスタンス: " +
                                 "${instanceOf(res)}）。本文: ${body(res)}")
                        else if (match expectedBody {
                                     case None    => false
                                     case Some(t) => not String.contains(substr = t, body(res))
                                 })
                            // **ステータスだけでは応答の取り違えを検出できない**。
                            // 別経路の 200 が返っても 200 は 200 である
                            Some("${path}（認証: ${authLabel(cookie)}）の本文に " +
                                 "「${expectedBody |> Option.getWithDefault("")}」が含まれない。" +
                                 "別の応答が返った可能性がある。" +
                                 "処理したルート: ${routeOf(res)}（インスタンス: " +
                                 "${instanceOf(res)}）。本文: ${body(res)}")
                        else
                            None;
                    Channel.send(problem, sender)
                } @ rc,
                all);
            List.range(0, count)
                |> List.filterMap(_ -> Channel.recv(receiver))
        }

    ///
    /// 同じ POST を並行に投げ、ステータスの一覧を返す。
    ///
    /// **read-modify-write の経路**（予約の引き渡し・航海の更新）は、
    /// 同時到達で両方が「実行前の状態」を読み、どちらも成功しうる。
    /// 逐次テストでは原理的に検出できない（IT5 レビュー M8）。
    ///
    pub def postConcurrently(portNumber: Int32, requestPath: String, formBody: String,
                             cookie: String, count: Int32): List[Int32] \ IO + Chan + NonDet =
        region rc {
            let (sender, receiver) = Channel.buffered(count);
            List.forEach(_ ->
                spawn {
                    let res = postWith(portNumber, requestPath, formBody,
                                       Map#{"Cookie" => cookie});
                    Channel.send(status(res), sender)
                } @ rc,
                List.range(0, count));
            List.range(0, count) |> List.map(_ -> Channel.recv(receiver))
        }

    ///
    /// **異なる 2 つの要求を同時に投げる**（IT9 レビュー H2）。
    ///
    /// `postConcurrently` は同じ要求を N 本投げる形であり、
    /// **別々の利用者が別々の操作を同時に行う交錯**を作れない。
    /// 経路の割り当て（経路設計者）と予約の確定（営業）はまさにその形である。
    ///
    /// 応答を投げた順に返す（1 本目・2 本目）。
    ///
    pub def postTwoConcurrently(portNumber: Int32,
                                first: (String, String, String),
                                second: (String, String, String)):
            (TestResponse, TestResponse) \ IO + Chan + NonDet =
        region rc {
            let (sender, receiver) = Channel.buffered(2);
            let fire = (index, spec) -> {
                let (requestPath, formBody, cookie) = spec;
                spawn {
                    let res = postWith(portNumber, requestPath, formBody,
                                       Map#{"Cookie" => cookie});
                    Channel.send((index, res), sender)
                } @ rc
            };
            fire(0, first);
            fire(1, second);
            let a = Channel.recv(receiver);
            let b = Channel.recv(receiver);
            if (fst(a) == 0) (snd(a), snd(b)) else (snd(b), snd(a))
        }

    /// 応答を処理したルート（`APP_TRACE_ROUTE=true` のときのみ付与される）
    pub def routeOf(res: TestResponse): String =
        header(res, "X-Route") |> Option.getWithDefault("(不明)")

    /// 応答を処理したアプリケーションインスタンス（同上）
    pub def instanceOf(res: TestResponse): String =
        header(res, "X-App-Instance") |> Option.getWithDefault("(不明)")

    ///
    /// **起動した識別子と応答した識別子が一致すること**。
    ///
    /// 残る仮説（`stop(0)` 直後に同じポートが再割り当てされ、別の起動が
    /// 応答する）を切り分けられるのはこの突合だけである。識別子を応答へ
    /// 添えて印字するところまでは IT6 の当初もできていたが、
    /// **期待値が無いため、値を見ても判断できなかった**（IT6 レビュー H5）。
    ///
    /// ここが落ちたら ADR-0005 の対応表の H5/H6 が現実に起きたということである。
    ///
    pub def assertInstance(expected: String, res: TestResponse): Unit \ Assert =
        let actual = instanceOf(res);
        if (actual == expected) ()
        else Assert.fail("別の起動が応答しました（ADR-0005 の仮説 H5・H6）。" +
                         "起動した識別子: ${expected} / 応答した識別子: ${actual} / " +
                         "処理したルート: ${routeOf(res)}")

    def authLabel(cookie: Option[String]): String =
        if (Option.isEmpty(cookie)) "なし" else "あり"

    /// ヘッダを順に適用する
    def applyHeaders(builder: HttpRequest$Builder,
                     entries: List[(String, String)]): HttpRequest$Builder \ IO =
        match entries {
            case Nil => builder
            case (name, value) :: rest => applyHeaders(builder.header(name, value), rest)
        }

    /// レスポンスを変換する（ヘッダ名は小文字へ正規化する）
    def toTestResponse(res: HttpResponse[String]): TestResponse \ IO =
        let responseHeaders = res.headers();
        let location = responseHeaders.firstValue("Location");
        let setCookie = responseHeaders.firstValue("Set-Cookie");
        let route = responseHeaders.firstValue("X-Route");
        let appInstance = responseHeaders.firstValue("X-App-Instance");
        let retryAfter = responseHeaders.firstValue("Retry-After");
        let hs = Map.empty()
            |> insertIfPresent("location", location)
            |> insertIfPresent("set-cookie", setCookie)
            // **どのルートが処理したか**を失敗メッセージに出せるようにする。
            // 「別のルートが処理した」のか「そのルートが想定外の結果を返した」のかを
            // 切り分けられないことが、IT4・IT5 の間欠異常の原因特定を止めていた
            |> insertIfPresent("x-route", route)
            // **どの起動が応答したか**。`X-Route` は全インスタンスで同じ表を持つため
            // 別インスタンスが応答しても期待どおりの名前を返す。
            // 残る仮説（ポート再割り当て・接続の再利用）を切り分けられるのは
            // 起動ごとに変わるこの値だけである（ADR-0005 の対応表 H5・H6）
            |> insertIfPresent("x-app-instance", appInstance)
            // **バックプレッシャの `Retry-After`**（TS12）。拒否だけして再試行の
            // 目安を示さないと、クライアントは即座に再送して過負荷を増幅させる。
            //
            // 収集するヘッダを列挙にしているのは、使わない値を運ぶ意味が無いためだが、
            // **列挙から漏れたヘッダは「送っていない」と区別が付かない**。
            // IT7 で実際に踏んだ（サーバは送っていたがテストからは見えなかった）
            |> insertIfPresent("retry-after", retryAfter);
        TestResponse.TestResponse(res.statusCode(), res.body(), hs)

    /// `Optional` の値があれば Map へ入れる
    def insertIfPresent(name: String, value: Optional[String],
                        acc: Map[String, String]): Map[String, String] =
        if (value.isPresent()) Map.insert(name, value.get(), acc) else acc
}
