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

    /// テスト用のレスポンス
    pub enum TestResponse(Int32, String)

    /// ステータスコード
    pub def status(res: TestResponse): Int32 =
        let TestResponse.TestResponse(s, _) = res;
        s

    /// ボディ
    pub def body(res: TestResponse): String =
        let TestResponse.TestResponse(_, b) = res;
        b

    /// GET リクエストを送る（認証情報は付けない）
    pub def get(portNumber: Int32, requestPath: String): TestResponse \ IO =
        let client = HttpClient.newHttpClient();
        let uri = URI.create("http://localhost:${portNumber}${requestPath}");
        let builder = HttpRequest.newBuilder(uri);
        let req = builder.build();
        let res = client.send(req, HttpResponse$BodyHandlers.ofString());
        TestResponse.TestResponse(res.statusCode(), res.body())
}
