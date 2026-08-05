///
/// 遅延例外の処理（US19。IT12）。
///
/// # この利用者は毎朝どう使うか
///
/// 1. 追跡管理者が船社から「本船が 3 日遅れる」と連絡を受ける
/// 2. 追跡詳細を開き、遅延を記録する（場所・日時・理由）
/// 3. **荷主は自分の追跡画面で遅延と理由を知る**——通知は将来リリースであり、
///    それまで荷主が自分で確認できることが唯一の伝達手段になる
/// 4. 新しい到着予定日が決まったら対応内容として入力する
/// 5. 遅延が解消したら解決し、**発生前の状態に戻る**
///
/// # 通知は将来リリース
///
/// 受入基準 3（荷主への通知）は US12 と同じ通知基盤であり将来リリースである。
/// **ただし画面には出す**——出さないと荷主は遅延を知る手段を持たない。
///
mod TrackingExceptionHttpTest {
    use SharedHttpTestClient.{status, body, assertStatus, header, TestResponse}
    use TestApp.withApp

    def password(): String = "correct horse battery staple"

    def loginAs(p: Int32, name: String): String \ Assert + IO =
        SharedHttpTestClient.login(p, name, password())

    def getWith(p: Int32, path: String, cookie: String): TestResponse \ IO =
        SharedHttpTestClient.getWith(p, path, Map#{"Cookie" => cookie})

    def postWith(p: Int32, path: String, formBody: String, cookie: String): TestResponse \ IO =
        SharedHttpTestClient.postWith(p, path, formBody, Map#{"Cookie" => cookie})

    def csrfTokenOf(p: Int32, path: String, cookie: String): String \ Assert + IO =
        let html = body(getWith(p, path, cookie));
        let marker = "name=\"_csrf\" value=\"";
        match String.indexOf(substr = marker, html) {
            case None => { Assert.fail("${path} に CSRF トークンが見つからなかった"); "" }
            case Some(i) =>
                let start = i + String.length(marker);
                String.slice(start = start, end = start + 64, html)
        }

    /// 開発シードと同じ形の追跡活動を用意する（追跡番号だけが要る）
    def seededTrackingNumber(): String = "TRK-20260803-0001"

    /// 遅延を記録する
    def raiseDelay(p: Int32, tracker: String, number: String,
                   occurredAt: String, description: String):
            TestResponse \ Assert + IO =
        let token = csrfTokenOf(p, "/tracking/" + number, tracker);
        postWith(p, "/tracking/" + number + "/exceptions",
            "_csrf=" + token + "&exceptionType=DELAY&location=USLAX" +
            "&occurredAt=" + occurredAt + "&description=" + description, tracker)

    // ---- 受入基準 1・2: 記録できる・状態が変わる ----

    ///
    /// **遅延を記録すると貨物状態が「異常あり」になり、荷主の画面に出ること**
    /// （受入基準 1・2・3 の代替）。
    ///
    /// **荷主の画面まで確かめる**。通知が将来リリースである以上、
    /// 荷主が自分で確認できなければ**遅延を知る手段が存在しない**。
    ///
    @Test
    def testTrackerRaisesDelay(): Unit \ Assert + IO =
        withApp("tracking_exception_raise", p -> {
            let tracker = loginAs(p, AuthFixtures.trackerUsername());
            let number = seededTrackingNumber();

            assertStatus(302, raiseDelay(p, tracker, number, "2026-08-05T09:00",
                                         "本船の遅延により3日遅れる見込み"));

            // 荷主の画面（未認証）に出る
            let publicPage = body(SharedHttpTestClient.get(p, "/public/tracking/" + number));
            Assert.assertTrue(String.contains(substr = "異常あり", publicPage));
            Assert.assertTrue(String.contains(substr = "本船の遅延", publicPage))
        })

    ///
    /// **対応内容を入力すると、荷主の画面の到着予定が変わること**（受入基準 4）。
    ///
    /// 荷主が知りたいのは「遅れている」ことより「**いつ着くのか**」である。
    ///
    @Test
    def testResolutionUpdatesEstimatedArrival(): Unit \ Assert + IO =
        withApp("tracking_exception_resolution", p -> {
            let tracker = loginAs(p, AuthFixtures.trackerUsername());
            let number = seededTrackingNumber();
            assertStatus(302, raiseDelay(p, tracker, number, "2026-08-05T09:00",
                                         "本船の遅延"));

            let token = csrfTokenOf(p, "/tracking/" + number, tracker);
            assertStatus(302, postWith(p, "/tracking/" + number + "/exceptions/resolution",
                "_csrf=" + token + "&revisedArrival=2026-08-20" +
                "&resolutionNotes=代替船に振り替え", tracker));

            let publicPage = body(SharedHttpTestClient.get(p, "/public/tracking/" + number));
            Assert.assertTrue(String.contains(substr = "2026-08-20", publicPage));
            Assert.assertTrue(String.contains(substr = "代替船に振り替え", publicPage))
        })

    ///
    /// **例外を解決すると発生前の状態に戻ること**（受入基準 5・観点 1）。
    ///
    /// **発生前の状態はカラムに永続化する**。履歴から再導出すると、
    /// ユニットテストが緑でもクロスリクエストで誤復帰する（記録済みの教訓）。
    ///
    @Test
    def testResolveRestoresPreviousStatus(): Unit \ Assert + IO =
        withApp("tracking_exception_resolve", p -> {
            let tracker = loginAs(p, AuthFixtures.trackerUsername());
            let number = seededTrackingNumber();

            // 発生前は「輸送中」（開発シードの状態）
            Assert.assertTrue(String.contains(substr = "輸送中",
                body(SharedHttpTestClient.get(p, "/public/tracking/" + number))));

            assertStatus(302, raiseDelay(p, tracker, number, "2026-08-05T09:00", "遅延"));
            Assert.assertTrue(String.contains(substr = "異常あり",
                body(SharedHttpTestClient.get(p, "/public/tracking/" + number))));

            let token = csrfTokenOf(p, "/tracking/" + number, tracker);
            assertStatus(302, postWith(p, "/tracking/" + number + "/exceptions/resolve",
                                       "_csrf=" + token, tracker));

            // **発生前の状態へ戻る**
            Assert.assertTrue(String.contains(substr = "輸送中",
                body(SharedHttpTestClient.get(p, "/public/tracking/" + number))))
        })

    ///
    /// **荷役作業員は例外を記録できないこと**（US19 は追跡管理者の操作）。
    ///
    /// あわせて**未認証はログインへ送られること**を確かめる。
    ///
    @Test
    def testExceptionIsTrackerOnly(): Unit \ Assert + IO =
        withApp("tracking_exception_authz", p -> {
            let worker = loginAs(p, AuthFixtures.handlerUsername());
            let number = seededTrackingNumber();
            Assert.assertEq(expected = 403,
                status(postWith(p, "/tracking/" + number + "/exceptions",
                                "exceptionType=DELAY", worker)));
            Assert.assertEq(expected = 302,
                status(SharedHttpTestClient.post(p, "/tracking/" + number + "/exceptions",
                                                 "exceptionType=DELAY")));
            // 公開追跡は開いたまま（観点 5）
            Assert.assertEq(expected = 200,
                status(SharedHttpTestClient.get(p, "/public/tracking")))
        })

    ///
    /// **同じ画面に同じ `id` を 2 つ出さないこと**（IT12・E2E が捕まえた）。
    ///
    /// 追跡詳細には「状態の更新」と「例外の記録」の 2 つのフォームがあり、
    /// どちらも場所と日時を選ばせる。`id` が重なると、ラベルの `for` が
    /// どちらを指すか決まらない——**支援技術と自動操作の両方が最初の 1 つを掴む**。
    ///
    /// E2E が「例外の内容が荷主の画面に出ない」として落ちて初めて分かった。
    /// HTTP テストはフォームを組み立てず直接 POST するため、**この形は見えない**。
    ///
    @Test
    def testFormFieldIdsAreUnique(): Unit \ Assert + IO =
        withApp("tracking_unique_ids", p -> {
            let tracker = loginAs(p, AuthFixtures.trackerUsername());
            let page = body(getWith(p, "/tracking/" + seededTrackingNumber(), tracker));
            Assert.assertEq(expected = 1, occurrences("id=\"location\"", page));
            Assert.assertEq(expected = 1, occurrences("id=\"occurredAt\"", page));
            Assert.assertEq(expected = 1, occurrences("id=\"exceptionLocation\"", page));
            Assert.assertEq(expected = 1,
                            occurrences("id=\"exceptionOccurredAt\"", page))
        })

    /// 部分文字列の出現回数
    def occurrences(needle: String, haystack: String): Int32 =
        countFrom(needle, haystack, 0)

    def countFrom(needle: String, haystack: String, acc: Int32): Int32 =
        match String.indexOf(substr = needle, haystack) {
            case None    => acc
            case Some(i) =>
                let rest = String.slice(start = i + String.length(needle),
                                        end = String.length(haystack), haystack);
                countFrom(needle, rest, acc + 1)
        }
}
