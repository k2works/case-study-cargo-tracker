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


    // ---- 到達性（IT9 レビュー H1） ----

    ///
    /// **確定画面へのリンクが予約詳細に出ていること**。
    ///
    /// `act` は URL を直接 POST するため、**画面からの入口が無くてもテストは緑になる**。
    /// 実際 IT9 のレビューで、確定済みの予約からは差し戻せず、仮受付の予約は
    /// キャンセルできない状態が見つかった（どちらも導線が出ていなかった）。
    ///
    /// 到達性は「画面があるか」ではなく「**その状態の予約から開けるか**」で決まる。
    /// キャンセル済み以外のすべての状態で入口が出ることを確かめる。
    ///
    /// この式が偽になる入力: `confirmLink` を `ROUTE_PROPOSED and ROUTED` に戻す実装。
    ///
    @Test
    def testConfirmScreenIsReachableFromEveryLiveState(): Unit \ Assert + IO =
        withApp("booking_confirm_reachable", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9507S", "JPOSA", "USLAX");
            let bookingId = bookedAndRouted(p, sales, router, "reach@example.com");
            let link = "/bookings/" + bookingId + "/confirm";
            let detail = () -> body(getWith(p, "/bookings/" + bookingId, sales));

            // 経路提案済（割り当て済み）
            Assert.assertTrue(String.contains(substr = link, detail()));

            // 確定済み——ここで消えると差し戻しがどこからもできない
            assertStatus(302, act(p, sales, bookingId, "confirm"));
            Assert.assertTrue(String.contains(substr = link, detail()));

            // 差し戻し後（経路未割り当ての経路設計中）——ここで消えるとキャンセルできない
            assertStatus(302, act(p, sales, bookingId, "sendBack"));
            Assert.assertTrue(String.contains(substr = link, detail()));

            // キャンセル済みでは消える（もう操作は無い）
            assertStatus(302, act(p, sales, bookingId, "cancel"));
            Assert.assertTrue(not String.contains(substr = link, detail()))
        })

    ///
    /// **確定前でも経路設計へ差し戻せること**（受入基準 4）。
    ///
    /// 荷主の「別便で」という返事は確定前に来るほうが多い。
    ///
    @Test
    def testSendsBackBeforeConfirmation(): Unit \ Assert + IO =
        withApp("booking_confirm_sendback_early", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9508S", "JPOSA", "USLAX");
            let bookingId = bookedAndRouted(p, sales, router, "early@example.com");
            assertStatus(302, act(p, sales, bookingId, "sendBack"));
            let detail = body(getWith(p, "/bookings/" + bookingId, sales));
            Assert.assertTrue(String.contains(substr = "経路設計中", detail));
            Assert.assertTrue(not String.contains(substr = "経路提案済", detail))
        })

    ///
    /// **経路が決まる前の予約もキャンセルできること**（受入基準 5）。
    ///
    /// キャンセルが最も多いのは経路が決まる前（与信 NG・二重登録・引き取り中止）。
    /// 止められないと、経路設計者は死んだ予約に経路を設計する。
    ///
    @Test
    def testCancelsPreliminaryBooking(): Unit \ Assert + IO =
        withApp("booking_confirm_cancel_preliminary", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let code = registerShipper(p, sales, "pre@example.com");
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
            // 仮受付のまま確定画面へ入れること
            let link = "/bookings/" + bookingId + "/confirm";
            Assert.assertTrue(
                String.contains(substr = link, body(getWith(p, "/bookings/" + bookingId, sales))));
            assertStatus(302, act(p, sales, bookingId, "cancel"));
            Assert.assertTrue(String.contains(substr = "キャンセル",
                body(getWith(p, "/bookings/" + bookingId, sales))))
        })


    // ---- 同時実行（IT9 レビュー H2） ----

    ///
    /// **同時に確定を投げても、成功が 1 本だけであること**。
    ///
    /// 確定は「引く → 集約に判断させる → 書く」形である。行を押さえずに読むと、
    /// 全員が `{経路提案済, 経路あり}` を読み、**全員が確定に成功する**。
    /// 集約の `CargoIsNotUnderRouting` 検査は*読んだ瞬間の状態*にしか効かず、
    /// DB の行には効かない。
    ///
    /// 同じことが経路の割り当て（経路設計者）との交錯でも起こり、そちらは
    /// 「荷主が承認していない経路での確定」になる。ただし**どちらが勝つかは
    /// 競争の結果であってよい**ため、観測から行ロックの有無を識別できない。
    /// 最初はその交錯でテストを書いたが、**ロックを外しても緑のまま**だった——
    /// 空振りの安全装置である。識別できるのは**同一操作の二重成功**のほうなので、
    /// そちらで固定する（[ADR-0008](../../../../docs/adr/ADR-0008-optimistic-locking-by-content-version.md)
    /// の「読む時点で行を押さえる」）。
    ///
    /// 4 本のうち 302（成功）がちょうど 1 本、残りは 200（拒否の再描画）であること。
    /// **内訳まで見る**——「成功 1 本以外」だけでは、403 で全滅した場合と区別できない。
    ///
    /// # このテストが識別できないこと（正直に書く）
    ///
    /// **`findForUpdate` を外しても本テストは緑のままである**（実測）。
    /// テスト用の構成では要求が実質的に直列化されており、行ロックの有無が
    /// 観測に現れない。したがって本テストは「確定が二重に通らない」という
    /// *観測可能な不変条件*を固定するものであって、**行ロックが効いていることの
    /// 証明ではない**。
    ///
    /// 行ロックの効果を識別するテストは本 IT では作れなかった。
    /// 「入れたこと」ではなく「働くこと」を検証する——という規律に対して、
    /// ここは**未達**である。IT10 の課題として `docs/development/retrospective-9.md`
    /// に記録した。
    @Test
    def testConcurrentConfirmSucceedsOnlyOnce(): Unit \ Assert + IO + Chan + NonDet =
        withApp("booking_confirm_concurrent", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9509S", "JPOSA", "USLAX");
            let bookingId = bookedAndRouted(p, sales, router, "race@example.com");
            let token = csrfTokenOf(p, "/bookings", sales);
            let statuses = SharedHttpTestClient.postConcurrently(
                p, "/bookings/" + bookingId + "/confirm",
                "_csrf=" + token + "&action=confirm", sales, 4);
            let succeeded = statuses |> List.count(st -> st == 302);
            let rejected = statuses |> List.count(st -> st == 200);
            if (succeeded == 1 and rejected == 3) ()
            else Assert.fail("同時に 4 本確定したところ、成功 ${succeeded} 本 / " +
                             "拒否 ${rejected} 本でした（期待 1 / 3）。" +
                             "ステータス: ${statuses}")
        })


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
            // **画面に出るのは表示名「予約確定」**である
            // （[UI 設計](../../../../docs/design/ui_design.md)の
            // 「BookingStatus バッジ定義」が `CONFIRMED` を
            // 表示名を「予約確定」へ揃えた（IT10・TS17。IT9 レビュー L10——
            // 現場もそう呼び、US13 の受入基準もそう書いている）。
            // 受入基準の「予約確定」は状態を業務の言葉で述べたものであり、
            // **表示名を決め打ちすると定義表と二重管理になる**
            assertStatus(302, act(p, sales, bookingId, "confirm"));
            let detail = body(getWith(p, "/bookings/" + bookingId, sales));
            Assert.assertTrue(String.contains(substr = "予約確定", detail));
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
            Assert.assertTrue(not String.contains(substr = "予約確定",
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
            Assert.assertTrue(not String.contains(substr = "予約確定", detail));
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
