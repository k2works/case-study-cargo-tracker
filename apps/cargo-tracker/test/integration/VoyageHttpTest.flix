///
/// 航海スケジュールの受入テスト（US24・US25・US07）。
///
/// HTTP リクエストからデータベースまでを縦断する。
/// **登録 → 検索の連結**（US24 受入基準 6）と、
/// **差分確認 → 更新**（US25 受入基準 2・3）が主眼である。
///
/// **DB 名は `voyage_http_` で始める**。H2 のインメモリ DB は名前で共有され、
/// `JdbcVoyageRepoTest` と同じ名前を使うと同じ DB を指し、
/// シードが 2 度走って主キー違反になる（IT6 で実際に発生）。
/// 観点表の軸 5（実行環境・テストの前提）に該当する。
mod VoyageHttpTest {
    use SharedHttpTestClient.{get, status, body, assertStatus, TestResponse}
    use TestApp.withApp

    def routerUser(): String = "router"
    def salesUser(): String = "yamada"
    def password(): String = "correct horse battery staple"

    // ---- ヘルパー ----

    def loginAs(p: Int32, name: String): String \ Assert + IO =
        let res = SharedHttpTestClient.post(p, "/login",
            "username=" + name + "&password=" +
            String.replace(src = " ", dst = "+", password()));
        // **失敗時に証拠を残す**（IT8 レビュー・tester。Try T6）。
        // Set-Cookie が無いという事実だけでは、認証に失敗したのか
        // 別インスタンスが応答したのかを区別できない。`assertStatus` は
        // ステータス・処理したルート・**インスタンス識別子**・本文を出す
        assertStatus(302, res);
        match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None    => { Assert.fail("${name} のログインで Set-Cookie が得られなかった"); "" }
            case Some(v) => match String.indexOf(substr = ";", v) {
                case None    => v
                case Some(i) => String.slice(start = 0, end = i, v)
            }
        }

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

    /// 直行便の入力（大阪 → LA）
    def directForm(): String =
        "vesselName=SS+Sakura&carrierName=Sakura+Line&capabilities=GENERAL" +
        "&legDeparture=JPOSA&legArrival=USLAX" +
        "&legDepartureTime=2026-11-01T10:00&legArrivalTime=2026-11-15T08:00"

    /// 寄港地つきの入力（大阪 → 東京 → LA）
    /// 大阪 → 東京 → 上海 → LA（寄港地 2 つ。**順序**の検証に使う）
    def viaTokyoAndShanghaiForm(): String =
        "vesselName=SS+Sakura&carrierName=Sakura+Line&capabilities=GENERAL" +
        "&legDeparture=JPOSA&legArrival=JPTYO" +
        "&legDepartureTime=2026-11-01T10:00&legArrivalTime=2026-11-02T09:00" +
        "&legDeparture=JPTYO&legArrival=CNSHA" +
        "&legDepartureTime=2026-11-03T10:00&legArrivalTime=2026-11-05T08:00" +
        "&legDeparture=CNSHA&legArrival=USLAX" +
        "&legDepartureTime=2026-11-06T10:00&legArrivalTime=2026-11-20T08:00"

    def registerVoyage(p: Int32, cookie: String, formBody: String): TestResponse \ Assert + IO =
        let token = csrfTokenOf(p, "/voyages/new", cookie);
        postWith(p, "/voyages", formBody + "&_csrf=" + token, cookie)

    // ---- 認可 ----

    /// 未認証では航路画面に到達できないこと
    @Test
    def testRequiresLogin(): Unit \ Assert + IO =
        withApp("voyage_http_auth", p -> {
            assertStatus(302, get(p, "/voyages"));
            assertStatus(302, get(p, "/voyages/new"))
        })

    ///
    /// 営業担当者は**検索できるが登録できない**こと。
    ///
    /// US07 は経路設計者向けだが、営業も見積の裏付けに要る。
    /// 一方で登録・更新は経路設計者の職掌である。
    ///
    @Test
    def testSalesCanSearchButNotRegister(): Unit \ Assert + IO =
        withApp("voyage_http_sales_scope", p -> {
            let cookie = loginAs(p, salesUser());
            assertStatus(200, getWith(p, "/voyages", cookie));
            assertStatus(403, getWith(p, "/voyages/new", cookie))
        })

    ///
    /// 営業の画面に**登録ボタンが出ない**こと（観点表の軸 3-7）。
    ///
    /// 押すと 403 になるボタンは、利用者に「壊れている」と受け取られる。
    ///
    @Test
    def testRegisterButtonIsHiddenFromSales(): Unit \ Assert + IO =
        withApp("voyage_http_sales_button", p -> {
            let sales = loginAs(p, salesUser());
            let listed = body(getWith(p, "/voyages", sales));
            Assert.assertTrue(not String.contains(substr = "href=\"/voyages/new\"", listed))
        })

    /// 経路設計者には登録ボタンが出ること
    @Test
    def testRegisterButtonIsVisibleToRouter(): Unit \ Assert + IO =
        withApp("voyage_http_router_button", p -> {
            let router = loginAs(p, routerUser());
            let listed = body(getWith(p, "/voyages", router));
            Assert.assertTrue(String.contains(substr = "href=\"/voyages/new\"", listed))
        })

    // ---- US24: 登録 ----

    ///
    /// 登録すると航海番号が通知に出て、**検索対象になる**こと
    /// （受入基準 5・6）。
    ///
    @Test
    def testRegistersVoyageAndFindsItBySearch(): Unit \ Assert + IO =
        withApp("voyage_http_register", p -> {
            let cookie = loginAs(p, routerUser());
            let res = registerVoyage(p, cookie, "voyageNumber=V-0100S&" + directForm());
            assertStatus(302, res);
            let flash = flashCookieOf(res);
            let listed = body(getWith(p, "/voyages", cookie + "; " + flash));
            Assert.assertTrue(String.contains(substr = "保存しました", listed));
            Assert.assertTrue(String.contains(substr = "V-0100S", listed));
            Assert.assertTrue(String.contains(substr = "Sakura Line", listed));
            // 検索でも見つかること（US24-6）
            let searched = body(getWith(p, "/voyages?origin=JPOSA&destination=USLAX", cookie));
            Assert.assertTrue(String.contains(substr = "V-0100S", searched))
        })

    ///
    /// 寄港地を複数・順序付きで登録できること（受入基準 2）。
    ///
    /// 一覧では寄港地として中間の港だけが出る（端点は別の列）。
    ///
    /// **港コードの部分一致で判定しない**。一覧ページは検索フォームの
    /// `<select>` に港マスタを全件出すため、`JPTYO` は航海が 0 件でも
    /// 常に本文へ含まれる。当初の判定は常に真であり、受入基準 2 の
    /// 唯一の HTTP 検証が何も見ていなかった（IT6 レビュー H6）。
    /// **寄港地セルの内容**（`<td>` に囲まれた並び）で判定する。
    ///
    @Test
    def testRegistersVoyageWithPortsOfCall(): Unit \ Assert + IO =
        withApp("voyage_http_ports", p -> {
            let cookie = loginAs(p, routerUser());
            assertStatus(302, registerVoyage(p, cookie,
                "voyageNumber=V-0200S&" + viaTokyoAndShanghaiForm()));
            let listed = body(getWith(p, "/voyages", cookie));
            Assert.assertTrue(String.contains(substr = "V-0200S", listed));
            // **順序どおりに並ぶこと**。集合として持つと運航の順序が消える
            Assert.assertTrue(String.contains(substr = "<td>JPTYO→CNSHA</td>", listed));
            // 端点は寄港地に含めない（大阪発 LA 行き）
            Assert.assertTrue(not String.contains(substr = "<td>JPOSA→", listed));
            // 直行の表示に倒れていないこと（対比を置く）
            Assert.assertTrue(not String.contains(substr = "<td>直行</td>", listed))
        })

    ///
    /// キャンセルすると既存のスケジュールが変わらないこと（US25 受入基準 5）。
    ///
    /// **差分確認の POST が書き込んでいないこと**が本質である。
    /// `POST /voyages/{n}`（確認）と `POST /voyages/{n}/apply`（更新）を
    /// 分けた設計は正しいが、前者が書いていないことを誰も見ていなかった
    /// （IT6 レビュー）。あわせて**行き止まりにしない**（軸 3）ことも見る。
    ///
    @Test
    def testCancelKeepsVoyageUnchanged(): Unit \ Assert + IO =
        withApp("voyage_http_cancel", p -> {
            let cookie = loginAs(p, routerUser());
            assertStatus(302, registerVoyage(p, cookie, "voyageNumber=V-1200S&" + directForm()));
            let editPage = body(getWith(p, "/voyages/V-1200S/edit", cookie));
            // 戻る先が必ずあること
            Assert.assertTrue(String.contains(substr = "href=\"/voyages\"", editPage));
            // 差分確認まで進んで離脱する
            let token = csrfTokenOf(p, "/voyages/V-1200S/edit", cookie);
            let version = versionOf(editPage);
            discard postWith(p, "/voyages/V-1200S",
                "voyageNumber=V-1200S&version=" + version +
                "&vesselName=SS+Kaede&carrierName=Sakura+Line&capabilities=GENERAL" +
                "&legDeparture=JPOSA&legArrival=USLAX" +
                "&legDepartureTime=2026-12-01T10:00&legArrivalTime=2026-12-15T08:00" +
                "&_csrf=" + token, cookie);
            // 確認しただけでは保存されない
            let listed = body(getWith(p, "/voyages", cookie));
            Assert.assertTrue(String.contains(substr = "SS Sakura", listed));
            Assert.assertTrue(not String.contains(substr = "SS Kaede", listed))
        })

    /// 直行便は寄港地欄が「直行」と出ること
    @Test
    def testDirectVoyageShowsNoPortsOfCall(): Unit \ Assert + IO =
        withApp("voyage_http_direct", p -> {
            let cookie = loginAs(p, routerUser());
            assertStatus(302, registerVoyage(p, cookie, "voyageNumber=V-0300S&" + directForm()));
            Assert.assertTrue(String.contains(substr = "直行",
                body(getWith(p, "/voyages", cookie))))
        })

    ///
    /// 必須項目が未入力なら、**どの欄かが分かるエラー**が出ること（受入基準 3）。
    ///
    @Test
    def testShowsErrorForMissingVoyageNumber(): Unit \ Assert + IO =
        withApp("voyage_http_missing_number", p -> {
            let cookie = loginAs(p, routerUser());
            let res = registerVoyage(p, cookie, "voyageNumber=&" + directForm());
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "航海番号を入力してください", body(res)));
            Assert.assertTrue(String.contains(substr = "invalid-feedback", body(res)))
        })

    @Test
    def testShowsErrorForMissingVesselName(): Unit \ Assert + IO =
        withApp("voyage_http_missing_vessel", p -> {
            let cookie = loginAs(p, routerUser());
            let res = registerVoyage(p, cookie,
                "voyageNumber=V-0400S&vesselName=&carrierName=Sakura+Line&capabilities=GENERAL" +
                "&legDeparture=JPOSA&legArrival=USLAX" +
                "&legDepartureTime=2026-11-01T10:00&legArrivalTime=2026-11-15T08:00");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "船名を入力してください", body(res)))
        })

    ///
    /// 出発日が到着日より後ならエラーが出ること（受入基準 4）。
    ///
    @Test
    def testShowsErrorForInvertedDates(): Unit \ Assert + IO =
        withApp("voyage_http_inverted", p -> {
            let cookie = loginAs(p, routerUser());
            let res = registerVoyage(p, cookie,
                "voyageNumber=V-0500S&vesselName=SS+Sakura&carrierName=Sakura+Line" +
                "&capabilities=GENERAL&legDeparture=JPOSA&legArrival=USLAX" +
                "&legDepartureTime=2026-11-15T10:00&legArrivalTime=2026-11-01T08:00");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(
                substr = "出発日時は到着日時以前で入力してください", body(res)))
        })

    ///
    /// 同一航海番号の重複登録が**入力エラーになる**こと（受入基準 5 の裏）。
    ///
    /// **500 にしない**。利用者が取れる行動（別の番号にする・既存を編集する）を示す。
    ///
    @Test
    def testRejectsDuplicateVoyageNumber(): Unit \ Assert + IO =
        withApp("voyage_http_duplicate", p -> {
            let cookie = loginAs(p, routerUser());
            assertStatus(302, registerVoyage(p, cookie, "voyageNumber=V-0600S&" + directForm()));
            let res = registerVoyage(p, cookie, "voyageNumber=V-0600S&" + directForm());
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "既に登録されています", body(res)));
            Assert.assertTrue(String.contains(substr = "編集", body(res)))
        })

    /// 対応貨物種別が未選択ならエラーが出ること
    @Test
    def testShowsErrorForMissingCapabilities(): Unit \ Assert + IO =
        withApp("voyage_http_no_capability", p -> {
            let cookie = loginAs(p, routerUser());
            let res = registerVoyage(p, cookie,
                "voyageNumber=V-0700S&vesselName=SS+Sakura&carrierName=Sakura+Line" +
                "&legDeparture=JPOSA&legArrival=USLAX" +
                "&legDepartureTime=2026-11-01T10:00&legArrivalTime=2026-11-15T08:00");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(
                substr = "対応貨物種別を 1 つ以上選択してください", body(res)))
        })

    ///
    /// 区間がつながっていないとエラーが出ること。
    ///
    /// 連結していない並びは**同じ航海の航程ではない**。
    ///
    @Test
    def testShowsErrorForDisconnectedLegs(): Unit \ Assert + IO =
        withApp("voyage_http_disconnected", p -> {
            let cookie = loginAs(p, routerUser());
            let res = registerVoyage(p, cookie,
                "voyageNumber=V-0800S&vesselName=SS+Sakura&carrierName=Sakura+Line" +
                "&capabilities=GENERAL" +
                "&legDeparture=JPOSA&legArrival=JPTYO" +
                "&legDepartureTime=2026-11-01T10:00&legArrivalTime=2026-11-02T09:00" +
                "&legDeparture=CNSHA&legArrival=USLAX" +
                "&legDepartureTime=2026-11-03T10:00&legArrivalTime=2026-11-15T08:00");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "区間がつながっていません", body(res)))
        })

    /// 区間の追加（htmx）が断片を返すこと
    @Test
    def testLegFieldsFragment(): Unit \ Assert + IO =
        withApp("voyage_http_leg_fragment", p -> {
            let cookie = loginAs(p, routerUser());
            let res = getWith(p, "/voyages/leg-fields?index=1", cookie);
            assertStatus(200, res);
            let html = body(res);
            Assert.assertTrue(String.contains(substr = "name=\"legDeparture\"", html));
            // 断片であること（ページ全体を返していない）
            Assert.assertTrue(not String.contains(substr = "<html", html))
        })

    /// CSRF トークンなしの登録が拒否されること
    @Test
    def testRejectsRegistrationWithoutCsrfToken(): Unit \ Assert + IO =
        withApp("voyage_http_csrf", p -> {
            let cookie = loginAs(p, routerUser());
            assertStatus(403,
                postWith(p, "/voyages", "voyageNumber=V-0900S&" + directForm(), cookie));
            Assert.assertTrue(not String.contains(substr = "V-0900S",
                body(getWith(p, "/voyages", cookie))))
        })

    // ---- US25: 更新 ----

    ///
    /// 差分を確認してから更新できること（受入基準 1・2・3・4）。
    ///
    @Test
    def testUpdatesVoyageAfterConfirmingDiff(): Unit \ Assert + IO =
        withApp("voyage_http_update", p -> {
            let cookie = loginAs(p, routerUser());
            assertStatus(302, registerVoyage(p, cookie, "voyageNumber=V-1000S&" + directForm()));
            // 編集画面を開く（受入基準 1）
            let editPage = getWith(p, "/voyages/V-1000S/edit", cookie);
            assertStatus(200, editPage);
            Assert.assertTrue(String.contains(substr = "SS Sakura", body(editPage)));
            let version = versionOf(body(editPage));
            let token = csrfTokenOf(p, "/voyages/V-1000S/edit", cookie);
            // 差分を確認する（受入基準 2）
            let confirmed = postWith(p, "/voyages/V-1000S",
                "voyageNumber=V-1000S&version=" + version +
                "&vesselName=SS+Kaede&carrierName=Sakura+Line&capabilities=GENERAL" +
                "&legDeparture=JPOSA&legArrival=USLAX" +
                "&legDepartureTime=2026-11-01T10:00&legArrivalTime=2026-11-15T08:00" +
                "&_csrf=" + token, cookie);
            assertStatus(200, confirmed);
            let diffHtml = body(confirmed);
            // **旧値と新値の両方が出る**こと
            Assert.assertTrue(String.contains(substr = "SS Sakura", diffHtml));
            Assert.assertTrue(String.contains(substr = "SS Kaede", diffHtml));
            // 更新する（受入基準 3）
            let applied = postWith(p, "/voyages/V-1000S/apply",
                "voyageNumber=V-1000S&version=" + version +
                "&vesselName=SS+Kaede&carrierName=Sakura+Line&capabilities=GENERAL" +
                "&legDeparture=JPOSA&legArrival=USLAX" +
                "&legDepartureTime=2026-11-01T10:00&legArrivalTime=2026-11-15T08:00" +
                "&_csrf=" + token, cookie);
            assertStatus(302, applied);
            // 検索結果に反映されること（受入基準 4）
            let listed = body(getWith(p, "/voyages", cookie));
            Assert.assertTrue(String.contains(substr = "SS Kaede", listed));
            Assert.assertTrue(not String.contains(substr = "SS Sakura", listed))
        })

    ///
    /// 差分確認の間に別の利用者が更新していたら、**保存されない**こと。
    ///
    /// ロストアップデートの防止（観点表の軸 4）。運航変更は複数人が
    /// 同時に触りうる情報であり、消えたことに誰も気付かない。
    ///
    @Test
    def testRejectsStaleUpdate(): Unit \ Assert + IO =
        withApp("voyage_http_stale", p -> {
            let cookie = loginAs(p, routerUser());
            assertStatus(302, registerVoyage(p, cookie, "voyageNumber=V-1100S&" + directForm()));
            let version = versionOf(body(getWith(p, "/voyages/V-1100S/edit", cookie)));
            let token = csrfTokenOf(p, "/voyages/V-1100S/edit", cookie);
            let body1 = "voyageNumber=V-1100S&version=" + version +
                "&carrierName=Sakura+Line&capabilities=GENERAL" +
                "&legDeparture=JPOSA&legArrival=USLAX" +
                "&legDepartureTime=2026-11-01T10:00&legArrivalTime=2026-11-15T08:00" +
                "&_csrf=" + token;
            // 1 人目が更新する
            assertStatus(302, postWith(p, "/voyages/V-1100S/apply",
                body1 + "&vesselName=SS+Kaede", cookie));
            // 2 人目が**古い版のまま**更新しようとする
            let stale = postWith(p, "/voyages/V-1100S/apply",
                body1 + "&vesselName=SS+Momiji", cookie);
            assertStatus(200, stale);
            Assert.assertTrue(String.contains(
                substr = "別の利用者によって更新されました", body(stale)));
            // **1 人目の更新が残っている**こと
            let listed = body(getWith(p, "/voyages", cookie));
            Assert.assertTrue(String.contains(substr = "SS Kaede", listed));
            Assert.assertTrue(not String.contains(substr = "SS Momiji", listed))
        })

    ///
    /// **同じ版で同時に更新したとき、成功が 1 本だけであること**（ADR-0008）。
    ///
    /// 逐次のテスト（`testRejectsStaleUpdate`）は「1 人目の確定を待ってから
    /// 2 人目が送る」形であり、**同時到達を通っていない**。版の照合は
    /// 「読む → 比べる → 書く」であって、この 3 つの間に別のトランザクションが
    /// 書けば、両方が同じ版を読んで両方が書ける。
    ///
    /// IT6 は `postConcurrently` を新設しながら、**新設した安全装置の側には
    /// 適用していなかった**（IT6 レビュー H4）。IT5 で予約の引き渡しについて
    /// 指摘したのとまったく同じ形である。
    ///
    /// 8 本のうち 302（成功）がちょうど 1 本、残りは 200（`Stale` の再確認画面）
    /// であること。**内訳まで見る**（すべて失敗しても「成功 1 本以外」は
    /// 成り立たないが、403 で落ちていた場合と区別できない）。
    ///
    @Test
    def testConcurrentUpdateSucceedsOnlyOnce(): Unit \ Assert + IO + Chan + NonDet =
        withApp("voyage_http_concurrent_update", p -> {
            let cookie = loginAs(p, routerUser());
            assertStatus(302, registerVoyage(p, cookie, "voyageNumber=V-1300S&" + directForm()));
            let version = versionOf(body(getWith(p, "/voyages/V-1300S/edit", cookie)));
            let token = csrfTokenOf(p, "/voyages/V-1300S/edit", cookie);
            let formBody = "voyageNumber=V-1300S&version=" + version +
                "&vesselName=SS+Kaede&carrierName=Sakura+Line&capabilities=GENERAL" +
                "&legDeparture=JPOSA&legArrival=USLAX" +
                "&legDepartureTime=2026-12-01T10:00&legArrivalTime=2026-12-15T08:00" +
                "&_csrf=" + token;
            let statuses = SharedHttpTestClient.postConcurrently(
                p, "/voyages/V-1300S/apply", formBody, cookie, 8);
            let succeeded = statuses |> List.count(st -> st == 302);
            let rejected = statuses |> List.count(st -> st == 200);
            if (succeeded == 1 and rejected == 7) ()
            else Assert.fail("同じ版で 8 本同時に更新したところ、成功 ${succeeded} 本 / " +
                             "再確認 ${rejected} 本でした（期待 1 / 7）。" +
                             "ステータス: ${statuses}")
        })

    /// 存在しない航海番号の編集は 404 であること
    @Test
    def testEditReturnsNotFoundForUnknownVoyage(): Unit \ Assert + IO =
        withApp("voyage_http_edit_unknown", p -> {
            let cookie = loginAs(p, routerUser());
            assertStatus(404, getWith(p, "/voyages/V-NOTEXIST/edit", cookie))
        })

    // ---- US07: 検索 ----

    /// 複数の航海を登録する
    def seedVoyages(p: Int32, cookie: String): Unit \ Assert + IO =
        assertStatus(302, registerVoyage(p, cookie, "voyageNumber=V-2000S&" + directForm()));
        assertStatus(302, registerVoyage(p, cookie,
            "voyageNumber=V-2100S&vesselName=SS+Kaede&carrierName=Kaede+Line" +
            "&capabilities=REFRIGERATED&legDeparture=JPTYO&legArrival=USLAX" +
            "&legDepartureTime=2026-12-01T10:00&legArrivalTime=2026-12-15T08:00"))

    /// 出発地で絞れること（受入基準 2）
    @Test
    def testSearchByOrigin(): Unit \ Assert + IO =
        withApp("voyage_http_search_origin", p -> {
            let cookie = loginAs(p, routerUser());
            seedVoyages(p, cookie);
            let found = body(getWith(p, "/voyages?origin=JPTYO", cookie));
            Assert.assertTrue(String.contains(substr = "V-2100S", found));
            Assert.assertTrue(not String.contains(substr = "V-2000S", found))
        })

    ///
    /// 貨物種別で絞れること（受入基準 6）。
    ///
    /// 冷凍貨物を運べる航海だけが残る。
    ///
    @Test
    def testSearchByCapability(): Unit \ Assert + IO =
        withApp("voyage_http_search_capability", p -> {
            let cookie = loginAs(p, routerUser());
            seedVoyages(p, cookie);
            let found = body(getWith(p, "/voyages?capability=REFRIGERATED", cookie));
            Assert.assertTrue(String.contains(substr = "V-2100S", found));
            Assert.assertTrue(not String.contains(substr = "V-2000S", found))
        })

    ///
    /// 条件を満たす航海が無いとき、**その旨が出て条件を緩和できる**こと
    /// （受入基準 4）。
    ///
    @Test
    def testSearchShowsNoMatchAndKeepsCriteria(): Unit \ Assert + IO =
        withApp("voyage_http_search_none", p -> {
            let cookie = loginAs(p, routerUser());
            seedVoyages(p, cookie);
            let found = body(getWith(p, "/voyages?origin=DEHAM", cookie));
            Assert.assertTrue(String.contains(substr = "条件を満たす航海はありません", found));
            // 条件が保持され、緩和して再検索できること
            Assert.assertTrue(String.contains(substr = "value=\"DEHAM\" selected", found) or
                              String.contains(substr = "DEHAM\" selected=\"selected\"", found))
        })

    /// 出発期間で絞れること
    @Test
    def testSearchByDepartureRange(): Unit \ Assert + IO =
        withApp("voyage_http_search_range", p -> {
            let cookie = loginAs(p, routerUser());
            seedVoyages(p, cookie);
            let found = body(getWith(p,
                "/voyages?departureFrom=2026-12-01&departureTo=2026-12-31", cookie));
            Assert.assertTrue(String.contains(substr = "V-2100S", found));
            Assert.assertTrue(not String.contains(substr = "V-2000S", found))
        })

    /// 経路設計者が navbar から航路一覧へ到達できること
    @Test
    def testReachableFromNavigation(): Unit \ Assert + IO =
        withApp("voyage_http_nav", p -> {
            let cookie = loginAs(p, routerUser());
            Assert.assertTrue(String.contains(substr = "href=\"/voyages\"",
                body(getWith(p, "/", cookie))))
        })

    // ---- ヘルパー ----

    /// 編集画面から版を取り出す
    def versionOf(html: String): String \ Assert =
        let marker = "name=\"version\" value=\"";
        match String.indexOf(substr = marker, html) {
            case None => { Assert.fail("編集画面に版が見つからなかった"); "" }
            case Some(i) =>
                let start = i + String.length(marker);
                let rest = String.slice(start = start, end = String.length(html), html);
                match String.indexOf(substr = "\"", rest) {
                    case None    => { Assert.fail("版が閉じていない"); "" }
                    case Some(q) => String.slice(start = 0, end = q, rest)
                }
        }

    /// レスポンスの `Set-Cookie` から通知 Cookie を取り出す
    def flashCookieOf(res: TestResponse): String \ Assert =
        // **失敗時に証拠を残す**（IT8 レビュー・tester。Try T6）。
        // Set-Cookie が無いという事実だけでは、認証に失敗したのか
        // 別インスタンスが応答したのかを区別できない。`assertStatus` は
        // ステータス・処理したルート・**インスタンス識別子**・本文を出す
        assertStatus(302, res);
        match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None    => { Assert.fail("登録の応答に通知 Cookie が付いていない"); "" }
            case Some(v) => match String.indexOf(substr = ";", v) {
                case None    => v
                case Some(i) => String.slice(start = 0, end = i, v)
            }
        }
}
