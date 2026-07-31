///
/// 静的ファイル配信の受入テスト。
///
/// IT2 のレビューで「Bootstrap の CSS が読み込まれておらず、公開追跡の
/// 『一目で状態が分かる』という価値が成立していない」と指摘された（H8）。
/// 配信そのものより、**利用者に届く経路が通っていること**を検証する。
///
/// 自作の配信であるため、パストラバーサルの防止をセキュリティ回帰テストとして固定する。
///
mod StaticFilesTest {
    use SharedHttpTestClient.{get, status, body}
    use TestApp.withApp

    /// CSS が 200 で返り、Content-Type が `text/css` であること
    @Test
    def testServesCss(): Unit \ Assert + IO =
        withApp("static_css", p -> {
            let res = get(p, "/static/css/bootstrap.min.css");
            Assert.assertEq(expected = 200, status(res));
            Assert.assertTrue(String.contains(substr = "bootstrap", String.toLowerCase(body(res))))
        })

    /// JavaScript が 200 で返ること
    @Test
    def testServesJavaScript(): Unit \ Assert + IO =
        withApp("static_js", p -> {
            Assert.assertEq(expected = 200, status(get(p, "/static/js/htmx.min.js")))
        })

    /// 存在しないファイルは 404 を返すこと
    @Test
    def testReturnsNotFoundForMissingFile(): Unit \ Assert + IO =
        withApp("static_missing", p -> {
            Assert.assertEq(expected = 404, status(get(p, "/static/css/nope.css")))
        })

    ///
    /// パストラバーサルを拒否すること（セキュリティ回帰テスト）。
    ///
    /// `..` を含むパスで配信ディレクトリの外へ出られると、設定ファイルや
    /// マイグレーション SQL が読み出せてしまう。
    ///
    @Test
    def testRejectsPathTraversal(): Unit \ Assert + IO =
        withApp("static_traversal", p -> {
            let res = get(p, "/static/../db/migration/V1__init.sql");
            Assert.assertTrue(status(res) != 200);
            Assert.assertTrue(not String.contains(substr = "CREATE TABLE", body(res)))
        })

    /// エンコードされたパストラバーサルも拒否すること
    @Test
    def testRejectsEncodedPathTraversal(): Unit \ Assert + IO =
        withApp("static_traversal_encoded", p -> {
            let res = get(p, "/static/%2E%2E/db/migration/V1__init.sql");
            Assert.assertTrue(status(res) != 200)
        })

    ///
    /// 許可されていない拡張子は配信しないこと。
    ///
    /// 配信対象を許可リストで限定する。`resources/static/` に置き忘れた
    /// 資材が公開される事故を防ぐ。
    ///
    @Test
    def testRejectsDisallowedExtension(): Unit \ Assert + IO =
        withApp("static_ext", p -> {
            Assert.assertTrue(status(get(p, "/static/secret.txt")) != 200)
        })

    /// 公開追跡ページが CSS を読み込んでいること（利用者に届く経路の検証）
    @Test
    def testPublicTrackingLoadsStylesheet(): Unit \ Assert + IO =
        withApp("static_linked", p -> {
            let html = body(get(p, "/public/tracking"));
            Assert.assertTrue(String.contains(
                substr = "<link rel=\"stylesheet\" href=\"/static/css/bootstrap.min.css\">", html))
        })
}
