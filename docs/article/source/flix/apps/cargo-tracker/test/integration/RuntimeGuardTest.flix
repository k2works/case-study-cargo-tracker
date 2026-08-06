///
/// HTTP ランタイムの防御の受入テスト（TS12）。
///
/// # このテストが存在する理由
///
/// IT6 は**安全装置を入れたのに守れていない**を 2 件同時に出した（P1）。
/// 楽観的ロックも `X-App-Instance` も設計の意図は正しく、テストも緑だった。
/// 共通していたのは「**入れたこと**を検証するテストはあるが、
/// **働くこと**を検証するテストが無かった」ことである。
///
/// | 装置 | 当時のテスト | 何を見ていなかったか |
/// | :--- | :--- | :--- |
/// | 楽観的ロック | 1 人目の確定を待って 2 人目が送る**逐次**テスト | 同時到達 |
/// | `X-App-Instance` | 値が起動ごとに変わること | 期待値との突合 |
///
/// よって本モジュールは**装置を破ろうとする**（ふりかえり Try T1）。
/// 上限を 2 に落とした構成へ同時に到達させ、遅延するハンドラでタイムアウトを起こす。
///
/// # なぜ上限を落とすか
///
/// 既定の 200 に対して 201 本を同時に投げるテストは、CI の資源を食う割に
/// 不安定である。**上限そのものを設定で変えられる**ようにし、
/// テストは 2 で確かめる。設定を通すのは本番の経路と同じである。
///
mod RuntimeGuardTest {
    use SharedHttpTestClient.{get, status, header, body, TestResponse}
    use SharedHttpRuntimeGuard.{RuntimeLimits, limits}
    use TestApp.withAppLimited

    // ---- 同時実行の上限とバックプレッシャ ----

    ///
    /// **上限に達したリクエストが `503` で拒否されること**。
    ///
    /// 仮想スレッドは無制限に生成できる。上限を設けないと DB コネクション待ちが
    /// 積み上がり、レイテンシが青天井になる
    /// （[バックエンドアーキテクチャ](../../../../docs/design/architecture_backend.md)）。
    ///
    /// **逐次に 10 本投げても上限には当たらない**。1 本ずつ完了するためである。
    /// 同時に到達させて初めて意味を持つ。
    ///
    /// この式が偽になる入力: セマフォを取得せずに素通しする実装。
    ///
    @Test
    def testRejectsWhenConcurrencyLimitReached(): Unit \ Assert + IO + Chan + NonDet =
        withAppLimited("runtime_guard_503", limits(1, 30), p -> {
            // 遅延する経路（`/health/ready?delayMs=`）へ同時に到達させる。
            // 上限 1 に対し 8 本なので、少なくとも 1 本は弾かれる
            let statuses = SharedHttpTestClient.getConcurrently(p, slowPath(400), 8);
            let rejected = statuses |> List.filter(r -> status(r) == 503);
            if (List.isEmpty(rejected))
                Assert.fail("同時実行の上限に達しても 503 が返らなかった。" +
                            "観測したステータス: ${statuses |> List.map(status)}")
            else
                ()
        })

    ///
    /// `503` に `Retry-After` が付くこと。
    ///
    /// 拒否だけして再試行の目安を示さないと、クライアントは即座に再送して
    /// 過負荷を増幅させる。
    ///
    /// この式が偽になる入力: `503` のステータスだけを返す実装。
    ///
    @Test
    def testRejectionCarriesRetryAfter(): Unit \ Assert + IO + Chan + NonDet =
        withAppLimited("runtime_guard_retry_after", limits(1, 30), p -> {
            let responses = SharedHttpTestClient.getConcurrently(p, slowPath(400), 8);
            match responses |> List.find(r -> status(r) == 503) {
                case None    => Assert.fail("503 が 1 件も観測できなかった")
                case Some(r) =>
                    match header(r, "Retry-After") {
                        case None    => Assert.fail("503 に Retry-After が付いていない")
                        // **`v != ""` では空虚である**。`"abc"` も `"-5"` も通る。
                        // RFC 9110 は delta-seconds（非負整数）を求めており、
                        // 0 だとクライアントは即座に再送して過負荷を増幅させる。
                        // 実装値をなぞらず**仕様として妥当な範囲**を見る
                        case Some(v) =>
                            match Int32.fromString(v) {
                                case None    => Assert.fail("Retry-After が秒数ではない: ${v}")
                                case Some(n) =>
                                    if (n >= 1 and n <= 60) ()
                                    else Assert.fail("Retry-After が 1〜60 秒の範囲外: ${n}")
                            }
                    }
            }
        })

    ///
    /// **拒否された後に、後続のリクエストが成功すること**。
    ///
    /// これが本モジュールで最も重要な検証である。セマフォを取得したまま
    /// 解放しない経路が 1 つでもあると、**最初の数本で枠を使い切って
    /// 以降すべてが 503 になる**。「503 が返った」だけを見るテストは、
    /// その壊れ方を緑のまま通してしまう。
    ///
    /// この式が偽になる入力: 例外やタイムアウトの経路で `release` を呼ばない実装。
    ///
    @Test
    def testPermitsAreReleasedAfterRejection(): Unit \ Assert + IO + Chan + NonDet =
        withAppLimited("runtime_guard_release", limits(1, 30), p -> {
            // まず上限を溢れさせる
            discard SharedHttpTestClient.getConcurrently(p, slowPath(300), 8);
            // 混雑が収まった後は通常どおり応答すること
            SharedHttpTestClient.assertStatus(200, get(p, "/health/live"))
        })

    ///
    /// 上限に達していなければ通常どおり応答すること。
    ///
    /// **拒否が常時起きる実装**（例: セマフォの初期値を 0 にした）を捕まえる。
    /// 上のテストは「503 が出ること」を求めるため、常に 503 でも緑になる。
    ///
    /// この式が偽になる入力: 上限の判定を反転させた実装。
    ///
    @Test
    def testAllowsRequestsBelowLimit(): Unit \ Assert + IO + Chan + NonDet =
        withAppLimited("runtime_guard_below", limits(4, 30), p -> {
            let responses = SharedHttpTestClient.getConcurrently(p, "/health/live", 3);
            let notOk = responses |> List.filter(r -> status(r) != 200) |> List.map(status);
            Assert.assertEq(expected = Nil, notOk)
        })

    ///
    /// **ヘルスチェックは枠に関係なく応答すること**。
    ///
    /// 過負荷で liveness が `503` を返すと、オーケストレータは
    /// 「混んでいるだけで生きている」インスタンスを**再起動する**。
    /// 負荷が高いときにインスタンスが落ちるため事態は必ず悪化する。
    /// IT3 で DB について学んだ再起動ループ（レビュー H4）が、
    /// **セマフォ経由で再現する**——防御を足したことが新しい単一障害点を作る。
    ///
    /// この式が偽になる入力: `guarded` が `isProbePath` の分岐を持たない実装
    /// （上限 1 が塞がっている間、`/health/live` も 503 になる）。
    ///
    @Test
    def testHealthProbesBypassTheLimit(): Unit \ Assert + IO + Chan + NonDet =
        withAppLimited("runtime_guard_probe", limits(1, 30), p -> {
            // 上限 1 を遅い経路で塞いだうえで liveness を叩く
            region rc {
                let (sender, receiver) = Channel.buffered(1);
                spawn { Channel.send(status(get(p, slowPath(1200))), sender) } @ rc;
                // 塞がるのを待つ。`/health/slow` の応答は 1.2 秒後
                let blockedProbe = status(get(p, "/health/live"));
                let readyProbe = status(get(p, "/health/ready"));
                discard Channel.recv(receiver);
                Assert.assertEq(expected = (200, 200), (blockedProbe, readyProbe))
            }
        })

    // ---- タイムアウト ----

    ///
    /// **処理が上限時間を超えたら `504` を返すこと**。
    ///
    /// 滞留したリクエストがスレッドと接続を占有し続けるのを防ぐ。
    /// 経路探索（US08）は積み替えの組み合わせが増えるほど時間がかかり、
    /// **上限を設けないと計算の暴走が応答不能に直結する**。
    ///
    /// この式が偽になる入力: タイムアウトを実装せず完了まで待つ実装
    /// （応答は 200 になり、遅延ぶんだけテストが長くなる）。
    ///
    @Test
    def testReturnsGatewayTimeout(): Unit \ Assert + IO =
        withAppLimited("runtime_guard_504", limits(8, 1), p -> {
            // 上限 1 秒に対し 2.5 秒かかる経路
            SharedHttpTestClient.assertStatus(504, get(p, slowPath(2500)))
        })

    ///
    /// **タイムアウトの後も後続が成功すること**。
    ///
    /// タイムアウトした処理がセマフォを握ったままだと、枠が減り続けて
    /// 最終的にすべてが 503 になる。`503` のときと同じ壊れ方であり、
    /// **同じ形の検証を両方に用意する**。
    ///
    /// この式が偽になる入力: タイムアウト経路でセマフォを解放しない実装。
    ///
    @Test
    def testPermitsAreReleasedAfterTimeout(): Unit \ Assert + IO =
        withAppLimited("runtime_guard_timeout_release", limits(1, 1), p -> {
            SharedHttpTestClient.assertStatus(504, get(p, slowPath(2500)));
            SharedHttpTestClient.assertStatus(200, get(p, "/health/live"))
        })

    ///
    /// 上限時間内に終わる処理は通常どおり応答すること。
    ///
    /// **常に 504 を返す実装**を捕まえる。
    ///
    /// この式が偽になる入力: タイムアウトの比較を反転させた実装。
    ///
    @Test
    def testFastRequestIsNotTimedOut(): Unit \ Assert + IO =
        withAppLimited("runtime_guard_fast", limits(8, 5), p ->
            SharedHttpTestClient.assertStatus(200, get(p, "/health/live")))

    ///
    /// `504` の本文が内部情報を漏らさないこと。
    ///
    /// 例外のメッセージやスタックトレースをそのまま返すと、
    /// 実装の構造が外から読める（観点表の軸 6）。
    ///
    /// この式が偽になる入力: 例外を `toString` して本文に載せる実装。
    ///
    @Test
    def testTimeoutDoesNotLeakInternals(): Unit \ Assert + IO =
        withAppLimited("runtime_guard_504_body", limits(8, 1), p -> {
            let text = body(get(p, slowPath(2500)));
            Assert.assertTrue(not String.contains(substr = "Exception", text));
            Assert.assertTrue(not String.contains(substr = "java.", text))
        })

    ///
    /// **診断ルートが本番の表に載らないこと**。
    ///
    /// `/health/slow` は `anonymous()` で登録され、最大 5 秒スレッドを掴む。
    /// 本番の表に漏れれば**認証なしで任意秒スレッドを掴める DoS の口**になる。
    /// 現状は `startServerWith` の既定値（`withDiagnostics = false`）に
    /// 全面的に依存しており、既定が反転しても誰も気付かない。
    ///
    /// この式が偽になる入力: `startServer` が `withDiagnostics = true` を渡す実装、
    /// あるいは `appRoutes` に診断ルートを直接足した実装。
    ///
    @Test
    def testDiagnosticRouteIsAbsentInProduction(): Unit \ Assert + IO =
        TestApp.withApp("runtime_guard_no_diagnostics", p ->
            // `withApp` は `startServer`（診断なし）で起動する
            SharedHttpTestClient.assertStatus(404, get(p, "/health/slow?ms=1")))

    ///
    /// 遅延を仕込む経路。
    ///
    /// **本番のルートに遅延の口を作らない**。`SharedHttpRuntimeGuard` は
    /// テスト用の遅延を `APP_TEST_DELAY_PATH` 相当の仕組みで持たず、
    /// 専用の診断ルート（`/health/slow`）を**テスト起動時のみ**表へ足す。
    ///
    def slowPath(delayMs: Int32): String = "/health/slow?ms=${delayMs}"
}
