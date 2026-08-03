///
/// ルーティングの HTTP 経路テスト。
///
/// `RouterTest` は解決アルゴリズムを、`AppRoutesTest` は表の構造を検証する。
/// 本モジュールは**実際に起動したサーバへリクエストを送り**、単一表への統合で
/// 変わった振る舞いが端まで通っていることを確かめる（IT4・TS08）。
///
mod RoutingHttpTest {
    use SharedHttpTestClient.{get, post, status, body}
    use TestApp.withApp

    ///
    /// パス一致・メソッド不一致で 405 を返すこと。
    ///
    /// IT3 では表を効果集合ごとに分けて順に試し、どの表にも当たらなければ 404 にしていた。
    /// そのため `POST /public/tracking/TRK-001` のように「パスはあるが GET 専用」の
    /// 経路が **404（存在しない）** と応答していた。存在しないのか、その操作が
    /// 許されないのかは、API の呼び出し側にとって別の意味を持つ。
    ///
    @Test
    def testReturnsMethodNotAllowed(): Unit \ Assert + IO =
        withApp("routing_405", p -> {
            let res = post(p, "/public/tracking", "");
            SharedHttpTestClient.assertStatus(405, res)
        })

    /// パスパラメータを含むルートでも 405 を返すこと
    @Test
    def testReturnsMethodNotAllowedOnParameterizedPath(): Unit \ Assert + IO =
        withApp("routing_405_param", p -> {
            let res = post(p, "/public/tracking/TRK-20260803-0001", "");
            SharedHttpTestClient.assertStatus(405, res)
        })

    /// 存在しないパスは 404 のままであること（405 に倒れていないこと）
    @Test
    def testReturnsNotFoundForUnknownPath(): Unit \ Assert + IO =
        withApp("routing_404", p -> {
            Assert.assertEq(expected = 404, status(get(p, "/no-such-path")));
            Assert.assertEq(expected = 404, status(post(p, "/no-such-path", "")))
        })

    ///
    /// `/health/live` がルーティング表に載り、DB を使わずに応答すること。
    ///
    /// liveness が DB を見ると、**DB 障害時にコンテナが再起動を繰り返す**
    /// （IT3 レビュー H4）。`TxMode.NoTx` はそれを型と定義で保証する仕組みであり、
    /// ここでは「表に載せても DB を触らないまま応答する」ことを端で確認する。
    ///
    @Test
    def testLivenessRespondsWithoutDatabase(): Unit \ Assert + IO =
        withApp("routing_live", p -> {
            let res = get(p, "/health/live");
            SharedHttpTestClient.assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "UP", body(res)))
        })

    ///
    /// `/health/ready` がルーティング表に載っていること（IT3 レビュー M9）。
    ///
    /// IT3 ではルート表の外でパス文字列を直接比較していた。「認可の正典は
    /// ルート表」という設計に対する例外であり、例外は増えると設計を空文にする。
    ///
    @Test
    def testReadinessIsServedFromRoutingTable(): Unit \ Assert + IO =
        withApp("routing_ready", p -> {
            let res = get(p, "/health/ready");
            SharedHttpTestClient.assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "\"db\":\"UP\"", body(res)))
        })

    ///
    /// **並行アクセスでも公開追跡が常に 200 を返すこと**。
    ///
    /// IT4 の実装中、公開追跡の `GET` が 1 度だけ 403 を返す事象を観測した。
    /// `Anonymous` のルートで 403 に至る経路は論理上存在しない
    /// （`decide` は必ず `Allow`、`requiresCsrf(Anonymous)` は `false`、
    /// `isStateChanging(Get)` は `false`）。したがって認可の誤りではありえず、
    /// **応答の取り違え**が第一の疑いになる。
    ///
    /// **既存のテストは全てリクエストを 1 本ずつ順に送っており**、サーバが
    /// 仮想スレッドで並行処理する以上、この種の不具合は原理的に検出できなかった。
    /// 記録から辿れるようにするだけでは足りず、**再現させる手段**として並行に投げる。
    ///
    /// 1 度でも 200 以外が出たら本文とともに落とす。判定材料は決定的である。
    ///
    @Test
    def testPublicTrackingStaysConsistentUnderConcurrency(): Unit \ Assert + IO + Chan + NonDet =
        withApp("routing_concurrent", p -> {
            let responses = SharedHttpTestClient.getConcurrently(p, "/public/tracking", 50);
            Assert.assertEq(expected = 50, List.length(responses));
            let unexpected = responses |> List.filter(r -> status(r) != 200);
            match List.head(unexpected) {
                case None    => ()
                case Some(r) =>
                    Assert.fail("並行アクセスで公開追跡が ${status(r)} を返しました" +
                                "（${List.length(unexpected)}/50 件）。本文: ${body(r)}")
            }
        })

    ///
    /// 並行アクセスでヘルスチェックが混線しないこと。
    ///
    /// `/health/live` は `TxMode.NoTx` であり、`runWithoutDb` のスタブハンドラ
    /// （呼ばれたら `bug!`）の下で動く。並行時に別リクエストの効果ハンドラを
    /// 掴むようなことがあれば、ここで落ちる。
    ///
    @Test
    def testLivenessStaysConsistentUnderConcurrency(): Unit \ Assert + IO + Chan + NonDet =
        withApp("routing_concurrent_live", p -> {
            let responses = SharedHttpTestClient.getConcurrently(p, "/health/live", 50);
            let unexpected = responses |> List.filter(r -> status(r) != 200);
            match List.head(unexpected) {
                case None    => ()
                case Some(r) =>
                    Assert.fail("並行アクセスで liveness が ${status(r)} を返しました。本文: ${body(r)}")
            }
        })

    /// ヘルスチェックにも 405 の判定が効くこと（表に載っている証拠）
    @Test
    def testHealthRoutesRejectPost(): Unit \ Assert + IO =
        withApp("routing_health_405", p -> {
            Assert.assertEq(expected = 405, status(post(p, "/health/live", "")));
            Assert.assertEq(expected = 405, status(post(p, "/health/ready", "")))
        })
}
