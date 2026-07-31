///
/// 公開貨物追跡の受入テスト（HTTP レベル）。
///
/// アウトサイドインで最初に書くテスト。HTTP リクエストからデータベースまでを縦断する。
/// US18（追跡情報を照会する）の公開追跡経路に対応する。
///
mod PublicTrackingHttpTest {
    use SharedHttpTestClient.{get, status, body}
    use TestApp.withApp

    /// 存在する追跡番号で、輸送状態・現在地・イベント履歴が表示される
    @Test
    def testShowsTrackingDetail(): Unit \ Assert + IO =
        withApp("public_tracking_detail", port -> {
            let res = get(port, "/public/tracking/TRK-20260803-0001");
            Assert.assertEq(expected = 200, status(res));
            let html = body(res);
            Assert.assertTrue(String.contains(substr = "TRK-20260803-0001", html));
            Assert.assertTrue(String.contains(substr = "輸送中", html));
            Assert.assertTrue(String.contains(substr = "Tokyo", html))
        })

    /// 存在しない追跡番号ではエラーメッセージが表示される（HTTP 200 で画面内表示）
    @Test
    def testShowsNotFoundMessage(): Unit \ Assert + IO =
        withApp("public_tracking_notfound", port -> {
            let res = get(port, "/public/tracking/TRK-99999999-9999");
            Assert.assertEq(expected = 200, status(res));
            Assert.assertTrue(String.contains(substr = "該当する貨物が見つかりません", body(res)))
        })

    /// 認証なしでアクセスできる（Cookie を送らない）
    @Test
    def testAccessibleWithoutAuth(): Unit \ Assert + IO =
        withApp("public_tracking_anon", port -> {
            let res = get(port, "/public/tracking/TRK-20260803-0001");
            Assert.assertEq(expected = 200, status(res))
        })

    /// ヘルスチェック（Readiness）が DB 疎通を検査して 200 を返す
    @Test
    def testReadyReturnsUp(): Unit \ Assert + IO =
        withApp("public_tracking_health", port -> {
            let res = get(port, "/health/ready");
            Assert.assertEq(expected = 200, status(res));
            Assert.assertTrue(String.contains(substr = "UP", body(res)))
        })

    /// クエリパラメータによる照会（入力フォームの送信先）が結果を返すこと
    @Test
    def testSearchByQueryParam(): Unit \ Assert + IO =
        withApp("http_search_query", p -> {
            let res = get(p, "/public/tracking?trackingNumber=TRK-20260803-0001");
            Assert.assertEq(expected = 200, status(res));
            Assert.assertTrue(String.contains(substr = "TRK-20260803-0001", body(res)))
        })

    /// 追跡番号未指定では入力フォームのみを返すこと（未検出扱いにしない）
    @Test
    def testSearchWithoutTrackingNumberShowsFormOnly(): Unit \ Assert + IO =
        withApp("http_search_empty", p -> {
            let res = get(p, "/public/tracking");
            Assert.assertEq(expected = 200, status(res));
            let html = body(res);
            Assert.assertTrue(String.contains(substr = "id=\"trackingNumber\"", html));
            Assert.assertTrue(not String.contains(substr = "見つかりません", html))
        })

    /// 推定到着日が画面に出ること（DB から画面までの縦断）
    @Test
    def testShowsEstimatedArrival(): Unit \ Assert + IO =
        withApp("http_eta", p -> {
            let res = get(p, "/public/tracking/TRK-20260803-0001");
            Assert.assertTrue(String.contains(substr = "2026-08-10", body(res)))
        })
}
