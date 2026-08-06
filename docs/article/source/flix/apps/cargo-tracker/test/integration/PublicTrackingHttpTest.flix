///
/// 公開貨物追跡の受入テスト（HTTP レベル）。
///
/// アウトサイドインで最初に書くテスト。HTTP リクエストからデータベースまでを縦断する。
/// US18（追跡情報を照会する）の公開追跡経路に対応する。
///
mod PublicTrackingHttpTest {
    use SharedHttpTestClient.{get, status, body}
    use TestApp.withApp
    use TrackingFixtures.sampleTrackingNumber

    /// 存在する追跡番号で、輸送状態・現在地・イベント履歴が表示される
    @Test
    def testShowsTrackingDetail(): Unit \ Assert + IO =
        withApp("public_tracking_detail", port -> {
            let res = get(port, "/public/tracking/" + sampleTrackingNumber());
            SharedHttpTestClient.assertStatus(200, res);
            let html = body(res);
            Assert.assertTrue(String.contains(substr = sampleTrackingNumber(), html));
            Assert.assertTrue(String.contains(substr = "輸送中", html));
            // UN/LOCODE の併記まで縦断で固定する（都市名だけでは同名港を区別できない）
            Assert.assertTrue(String.contains(substr = "Tokyo（JPTYO）", html))
        })

    /// 存在しない追跡番号ではエラーメッセージが表示される（HTTP 200 で画面内表示）
    @Test
    def testShowsNotFoundMessage(): Unit \ Assert + IO =
        withApp("public_tracking_notfound", port -> {
            let res = get(port, "/public/tracking/TRK-99999999-9999");
            SharedHttpTestClient.assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "該当する貨物が見つかりません", body(res)))
        })

    /// 認証なしでアクセスできる（Cookie を送らない）
    @Test
    def testAccessibleWithoutAuth(): Unit \ Assert + IO =
        withApp("public_tracking_anon", port -> {
            let res = get(port, "/public/tracking/" + sampleTrackingNumber());
            SharedHttpTestClient.assertStatus(200, res)
        })

    /// ヘルスチェック（Readiness）が DB 疎通を検査して 200 を返す
    @Test
    def testReadyReturnsUp(): Unit \ Assert + IO =
        withApp("public_tracking_health", port -> {
            let res = get(port, "/health/ready");
            SharedHttpTestClient.assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "UP", body(res)))
        })

    /// クエリパラメータによる照会（入力フォームの送信先）が結果を返すこと
    @Test
    def testSearchByQueryParam(): Unit \ Assert + IO =
        withApp("http_search_query", p -> {
            let res = get(p, "/public/tracking?trackingNumber=" + sampleTrackingNumber());
            SharedHttpTestClient.assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = sampleTrackingNumber(), body(res)))
        })

    /// 追跡番号未指定では入力フォームのみを返すこと（未検出扱いにしない）
    @Test
    def testSearchWithoutTrackingNumberShowsFormOnly(): Unit \ Assert + IO =
        withApp("http_search_empty", p -> {
            let res = get(p, "/public/tracking");
            SharedHttpTestClient.assertStatus(200, res);
            let html = body(res);
            Assert.assertTrue(String.contains(substr = "id=\"trackingNumber\"", html));
            Assert.assertTrue(not String.contains(substr = "見つかりません", html))
        })

    /// 推定到着日が画面に出ること（DB から画面までの縦断）
    @Test
    def testShowsEstimatedArrival(): Unit \ Assert + IO =
        withApp("http_eta", p -> {
            let res = get(p, "/public/tracking/" + sampleTrackingNumber());
            let html = body(res);
            // 書式まで固定する。部分一致だけでは `2026-08-10 00:00:00` でも緑になる
            Assert.assertTrue(String.contains(substr = "推定到着日: 2026-08-10<", html));
            Assert.assertTrue(not String.contains(substr = "00:00:00", html))
        })

    /// イベント履歴が新しい順で並ぶこと（SQL の ORDER BY とリスト反転の組み合わせを固定する）
    @Test
    def testEventsAreOrderedNewestFirst(): Unit \ Assert + IO =
        withApp("http_event_order", p -> {
            let html = body(get(p, "/public/tracking/" + sampleTrackingNumber()));
            let loadAt = String.indexOf(substr = "積込（LOAD）", html) |> Option.getWithDefault(-1);
            let receiveAt = String.indexOf(substr = "受取（RECEIVE）", html) |> Option.getWithDefault(-1);
            Assert.assertTrue(loadAt >= 0 and receiveAt >= 0);
            Assert.assertTrue(loadAt < receiveAt)
        })

    /// 追跡番号が属性へ反映される経路でエスケープが効くこと（属性脱出の防止）
    @Test
    def testEscapesTrackingNumberInFormValue(): Unit \ Assert + IO =
        withApp("http_attr_escape", p -> {
            let html = body(get(p, "/public/tracking/%22%3E%3Cscript%3E"));
            Assert.assertTrue(not String.contains(substr = "<script>", html))
        })

    /// 空白のみの入力は未入力として扱うこと（コピー＆ペーストで混じる空白への耐性）
    @Test
    def testBlankTrackingNumberIsTreatedAsEmpty(): Unit \ Assert + IO =
        withApp("http_blank_input", p -> {
            let html = body(get(p, "/public/tracking?trackingNumber=%20%20"));
            Assert.assertTrue(String.contains(substr = "追跡番号を入力してください", html));
            Assert.assertTrue(not String.contains(substr = "見つかりません", html))
        })

    /// 前後に空白のある追跡番号でも照会できること
    @Test
    def testTrackingNumberIsTrimmed(): Unit \ Assert + IO =
        withApp("http_trim_input", p -> {
            let html = body(get(p, "/public/tracking?trackingNumber=%20" + sampleTrackingNumber() + "%20"));
            Assert.assertTrue(String.contains(substr = "tracking-result", html))
        })
}
