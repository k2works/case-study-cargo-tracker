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
        let clientBuilder = HttpClient.newBuilder();
        let noRedirect = clientBuilder.followRedirects(HttpClient$Redirect.NEVER);
        let client = noRedirect.build();
        let uri = URI.create("http://localhost:${portNumber}${requestPath}");
        let builder = applyHeaders(HttpRequest.newBuilder(uri), Map.toList(requestHeaders));
        let res = client.send(builder.build(), HttpResponse$BodyHandlers.ofString());
        toTestResponse(res)

    /// フォーム送信（`application/x-www-form-urlencoded`）
    pub def post(portNumber: Int32, requestPath: String, formBody: String): TestResponse \ IO =
        postWith(portNumber, requestPath, formBody, Map.empty())

    /// ヘッダを指定してフォーム送信する
    pub def postWith(portNumber: Int32, requestPath: String, formBody: String,
                     requestHeaders: Map[String, String]): TestResponse \ IO =
        let clientBuilder = HttpClient.newBuilder();
        let noRedirect = clientBuilder.followRedirects(HttpClient$Redirect.NEVER);
        let client = noRedirect.build();
        let uri = URI.create("http://localhost:${portNumber}${requestPath}");
        let publisher = HttpRequest$BodyPublishers.ofString(formBody);
        // 呼び出しは 1 つずつ束縛する（1 行に連ねるとパースできない）。
        // `.POST(...)` は大文字始まりのためモジュール参照と解釈される。`method` を使う
        let newBuilder = HttpRequest.newBuilder(uri);
        let posted = newBuilder.method("POST", publisher);
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
        else
            Assert.fail("ステータスが期待と異なります。期待 ${expected} / 実際 ${status(res)} / 本文: ${body(res)}")

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
        let hs = Map.empty()
            |> insertIfPresent("location", location)
            |> insertIfPresent("set-cookie", setCookie);
        TestResponse.TestResponse(res.statusCode(), res.body(), hs)

    /// `Optional` の値があれば Map へ入れる
    def insertIfPresent(name: String, value: Optional[String],
                        acc: Map[String, String]): Map[String, String] =
        if (value.isPresent()) Map.insert(name, value.get(), acc) else acc
}
