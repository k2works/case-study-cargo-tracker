///
/// 予約の確定・差し戻し・キャンセル（US13・IT9）。
///
/// # この利用者は毎朝どう使うか
///
/// 1. 経路設計者が経路を割り当てる（US09/US11）
/// 2. 営業が荷主へルートを提案し、**電話やメールで返事を待つ**
/// 3. 「それでお願いします」→ 確定。「もっと安く」→ 差し戻し。「今回は見送り」→ キャンセル
///
/// **3 つの返事すべてに操作がある必要がある**。確定だけ実装すると、
/// 断られた予約が「経路設計中」のまま一覧に残り続け、
/// [日次手順](../../../../docs/design/operation.md)の滞留確認が意味を失う。
///
/// # 通知は将来リリース
///
/// 受入基準 3（経路設計者への追跡番号発行依頼）と 6（荷主へのキャンセル確認）は
/// **US12 と同じく将来リリース**である。本 IT は予約一覧の絞り込み（IT8）と
/// 運用手順で代替する。
///
mod BookingConfirmHttpTest {
    use SharedHttpTestClient.{status, body, assertStatus, TestResponse}
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

    /// 荷主を登録して荷主コードを返す
    def registerShipper(p: Int32, sales: String, email: String): String \ Assert + IO =
        let token = csrfTokenOf(p, "/shippers/new", sales);
        assertStatus(302, postWith(p, "/shippers",
            "name=山田太郎&email=" + email + "&shipperType=INDIVIDUAL&_csrf=" + token, sales));
        let html = body(getWith(p, "/shippers", sales));
        match String.indexOf(substr = "SHP-", html) {
            case None    => { Assert.fail("荷主一覧に荷主コードが見つからなかった"); "" }
            case Some(i) => String.slice(start = i, end = i + 12, html)
        }

    /// 航海を 1 本登録する
    def registerVoyage(p: Int32, router: String, number: String,
                       origin: String, destination: String): Unit \ Assert + IO =
        let token = csrfTokenOf(p, "/voyages/new", router);
        assertStatus(302, postWith(p, "/voyages",
            "_csrf=" + token + "&voyageNumber=" + number +
            "&vesselName=TESTSHIP&carrierName=TESTLINE&capabilities=GENERAL" +
            "&legDeparture=" + origin + "&legArrival=" + destination +
            "&legDepartureTime=2099-01-05T10:00&legArrivalTime=2099-01-20T08:00", router))

    ///
    /// 経路が割り当てられた予約を作る（US04 → US06 → US09/US11 を通す）。
    ///
    /// **利用者と同じ経路で状態を作る**。SQL で直接作ると、
    /// 途中の遷移が壊れていても気付けない。
    ///
    def bookedAndRouted(p: Int32, sales: String, router: String,
                        email: String): String \ Assert + IO =
        let code = registerShipper(p, sales, email);
        let bookToken = csrfTokenOf(p, "/bookings/new", sales);
        let res = postWith(p, "/bookings",
            "_csrf=" + bookToken + "&shipperCode=" + code +
            "&origin=JPOSA&destination=USLAX&arrivalDeadline=2099-12-31" +
            "&cargoType=GENERAL&weight=1200", sales);
        assertStatus(302, res);
        let bookingId = match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None    => { Assert.fail("予約の応答に通知 Cookie が付いていない"); "" }
            case Some(v) => match String.indexOf(substr = "BK-", v) {
                case None    => { Assert.fail("通知に予約番号が含まれていない"); "" }
                case Some(i) => String.slice(start = i, end = i + 11, v)
            }
        };
        let assignToken = csrfTokenOf(p, "/bookings/" + bookingId, sales);
        assertStatus(302, postWith(p, "/bookings/" + bookingId + "/assign-to-routing",
                                   "_csrf=" + assignToken, sales));
        // 経路設計者が候補を割り当てる
        let routePage = body(getWith(p, "/bookings/" + bookingId + "/route", router));
        let key = candidateKeyIn(routePage);
        let routeToken = csrfTokenOf(p, "/bookings", router);
        assertStatus(302, postWith(p, "/bookings/" + bookingId + "/route",
                                   "_csrf=" + routeToken + "&candidate=" + key, router));
        bookingId

    def candidateKeyIn(html: String): String \ Assert =
        let marker = "name=\"candidate\" value=\"";
        match String.indexOf(substr = marker, html) {
            case None => { Assert.fail("候補の選択欄が見つからない"); "" }
            case Some(i) =>
                let start = i + String.length(marker);
                let rest = String.slice(start = start, end = String.length(html), html);
                match String.indexOf(substr = "\"", rest) {
                    case None    => { Assert.fail("選択欄の値が閉じていない"); "" }
                    case Some(j) => String.slice(start = 0, end = j, rest)
                }
        }

    /// 確定確認画面から操作する
    def act(p: Int32, actor: String, bookingId: String,
            action: String): TestResponse \ Assert + IO =
        let path = "/bookings/" + bookingId + "/confirm";
        let token = csrfTokenOf(p, "/bookings", actor);
        postWith(p, path, "_csrf=" + token + "&action=" + action, actor)

    // ---- 受入基準 1・2: 内容と経路を確認して確定する ----

    ///
    /// **予約内容と選択ルートを確認してから確定できること**（受入基準 1・2）。
    ///
    /// 確認画面を経ずに確定できる導線は作らない。**押し間違いが
    /// 取り返しのつかない操作になる**（荷主が承認したという記録である）。
    ///
    @Test
    def testSalesConfirmsBooking(): Unit \ Assert + IO =
        withApp("booking_confirm_ok", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9501S", "JPOSA", "USLAX");
            let bookingId = bookedAndRouted(p, sales, router, "yamada@example.com");

            // 受入基準 1: 予約内容と選択ルートを確認できる
            let confirmPage = body(getWith(p, "/bookings/" + bookingId + "/confirm", sales));
            Assert.assertTrue(String.contains(substr = bookingId, confirmPage));
            Assert.assertTrue(String.contains(substr = "V-9501S", confirmPage));
            Assert.assertTrue(String.contains(substr = "JPOSA", confirmPage));

            // 受入基準 2: 確定すると予約確定の状態になる
            //
            // **画面に出るのは表示名「確認済」**である
            // （[UI 設計](../../../../docs/design/ui_design.md)の
            // 「BookingStatus バッジ定義」が `CONFIRMED` を
            // 表示名「確認済」・説明「予約確定」と定めている）。
            // 受入基準の「予約確定」は状態を業務の言葉で述べたものであり、
            // **表示名を決め打ちすると定義表と二重管理になる**
            assertStatus(302, act(p, sales, bookingId, "confirm"));
            let detail = body(getWith(p, "/bookings/" + bookingId, sales));
            Assert.assertTrue(String.contains(substr = "確認済", detail));
            // 前の状態は消えている
            Assert.assertTrue(not String.contains(substr = "経路提案済", detail));
            // 経路は残る（確定したのは経路ごとである）
            Assert.assertTrue(String.contains(substr = "V-9501S", detail))
        })

    ///
    /// **経路が未割り当てなら確定できないこと**。
    ///
    /// 荷主は経路を見て承認する。経路が無いまま確定すると、
    /// **何を承認したのか分からない記録**が残る。
    ///
    @Test
    def testCannotConfirmWithoutRoute(): Unit \ Assert + IO =
        withApp("booking_confirm_no_route", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let code = registerShipper(p, sales, "yamada@example.com");
            let bookToken = csrfTokenOf(p, "/bookings/new", sales);
            let res = postWith(p, "/bookings",
                "_csrf=" + bookToken + "&shipperCode=" + code +
                "&origin=JPOSA&destination=USLAX&arrivalDeadline=2099-12-31" +
                "&cargoType=GENERAL&weight=1200", sales);
            assertStatus(302, res);
            let bookingId = match SharedHttpTestClient.header(res, "Set-Cookie") {
                case None    => { Assert.fail("通知 Cookie が無い"); "" }
                case Some(v) => match String.indexOf(substr = "BK-", v) {
                    case None    => { Assert.fail("予約番号が無い"); "" }
                    case Some(i) => String.slice(start = i, end = i + 11, v)
                }
            };
            let confirmed = act(p, sales, bookingId, "confirm");
            Assert.assertEq(expected = 200, status(confirmed));
            Assert.assertTrue(String.contains(substr = "経路", body(confirmed)));
            Assert.assertTrue(not String.contains(substr = "確認済",
                body(getWith(p, "/bookings/" + bookingId, sales))))
        })

    // ---- 受入基準 4: 差し戻し ----

    ///
    /// **差し戻すと経路も外れ、選び直せること**（受入基準 4。壊れ方の観点・軸 4）。
    ///
    /// 荷主が「もっと安く」と言えば経路から選び直す。
    /// **旅程だけ消して経路状態が残る**と「経路提案済だが経路が無い」という
    /// 読めない状態になり、経路設計者は何をすべきか分からない。
    ///
    /// この式が偽になる入力: `BookingStatus` だけ戻して旅程を残す実装。
    ///
    @Test
    def testSendsBackToRoutingAndAllowsReselection(): Unit \ Assert + IO =
        withApp("booking_confirm_sendback", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9601S", "JPOSA", "USLAX");
            let bookingId = bookedAndRouted(p, sales, router, "yamada@example.com");
            assertStatus(302, act(p, sales, bookingId, "confirm"));

            // 荷主がルート変更を希望した
            assertStatus(302, act(p, sales, bookingId, "sendBack"));
            let detail = body(getWith(p, "/bookings/" + bookingId, sales));
            Assert.assertTrue(String.contains(substr = "経路設計中", detail));
            Assert.assertTrue(not String.contains(substr = "確認済", detail));
            // **経路も外れている**（旅程が残っていない）
            Assert.assertTrue(not String.contains(substr = "V-9601S", detail));

            // 経路設計者が選び直せる
            let routePage = body(getWith(p, "/bookings/" + bookingId + "/route", router));
            let key = candidateKeyIn(routePage);
            let routeToken = csrfTokenOf(p, "/bookings", router);
            assertStatus(302, postWith(p, "/bookings/" + bookingId + "/route",
                                       "_csrf=" + routeToken + "&candidate=" + key, router));
            Assert.assertTrue(String.contains(substr = "V-9601S",
                body(getWith(p, "/bookings/" + bookingId, sales))))
        })

    // ---- 受入基準 5: キャンセル ----

    ///
    /// **キャンセルできること**（受入基準 5）。
    ///
    /// 断られた予約が「経路設計中」のまま残ると、
    /// 日次手順の滞留確認に毎朝現れ続ける。
    ///
    @Test
    def testCancelsBooking(): Unit \ Assert + IO =
        withApp("booking_confirm_cancel", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9701S", "JPOSA", "USLAX");
            let bookingId = bookedAndRouted(p, sales, router, "yamada@example.com");
            assertStatus(302, act(p, sales, bookingId, "cancel"));
            let detail = body(getWith(p, "/bookings/" + bookingId, sales));
            Assert.assertTrue(String.contains(substr = "キャンセル", detail));
            // **絞り込みから外れる**（日次手順に現れない）
            let proposed = body(getWith(p, "/bookings?status=ROUTE_PROPOSED", sales));
            Assert.assertTrue(not String.contains(substr = bookingId, proposed))
        })

    // ---- 可視範囲・職掌（軸 6） ----

    ///
    /// **経路設計者は確定できないこと**（職掌）。
    ///
    /// 荷主の承認を確認するのは営業の仕事である。
    ///
    @Test
    def testRouterCannotConfirm(): Unit \ Assert + IO =
        withApp("booking_confirm_authz", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9801S", "JPOSA", "USLAX");
            let bookingId = bookedAndRouted(p, sales, router, "yamada@example.com");
            Assert.assertEq(expected = 403, status(act(p, router, bookingId, "confirm")))
        })

    ///
    /// **荷主は自分の予約でも確定できないこと**（軸 6）。
    ///
    /// 荷主が承認したことを**記録するのは営業**である。
    /// 荷主自身が確定できると、承認の記録が「荷主が押した」ことになり、
    /// 営業が確認したという事実が残らない。
    ///
    @Test
    def testShipperCannotConfirm(): Unit \ Assert + IO =
        TestApp.withAppAndDb("booking_confirm_shipper", (p, ds) -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9901S", "JPOSA", "USLAX");
            let bookingId = bookedAndRouted(p, sales, router, "kimura@example.com");
            let html = body(getWith(p, "/shippers", sales));
            let code = match String.indexOf(substr = "SHP-", html) {
                case None    => { Assert.fail("荷主コードが無い"); "" }
                case Some(i) => String.slice(start = i, end = i + 12, html)
            };
            AuthFixtures.linkShipper(ds, AuthFixtures.shipperUsername(), code);
            let shipper = loginAs(p, AuthFixtures.shipperUsername());
            Assert.assertEq(expected = 403, status(act(p, shipper, bookingId, "confirm")))
        })
}
