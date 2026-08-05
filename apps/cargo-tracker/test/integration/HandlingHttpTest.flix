///
/// 荷役作業の記録（US15・IT10）。
///
/// # この利用者は毎朝どう使うか
///
/// 1. 荷役作業員が現場で貨物を受け取り、**貼られた追跡番号**を読む
/// 2. 荷役管理から登録画面を開き、種別・場所・日時を入れる
/// 3. 登録すると**荷主が見る追跡画面にすぐ反映される**
///
/// 予定と違う港で積み込んだときは、記録は受け付けたうえで
/// 「経路設計者へ連絡してください」と伝える——**記録を拒まない**。
/// 拒むと現場が記録できず、追跡が実態から離れる。
///
/// # 通知は将来リリース
///
/// 受入基準 5（荷主への状態変更通知）は **US12 と同じ通知基盤**であり将来リリースである。
///
mod HandlingHttpTest {
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

    def registerShipper(p: Int32, sales: String, email: String): String \ Assert + IO =
        let token = csrfTokenOf(p, "/shippers/new", sales);
        assertStatus(302, postWith(p, "/shippers",
            "name=山田太郎&email=" + email + "&shipperType=INDIVIDUAL&_csrf=" + token, sales));
        let html = body(getWith(p, "/shippers", sales));
        match String.indexOf(substr = "SHP-", html) {
            case None    => { Assert.fail("荷主一覧に荷主コードが見つからなかった"); "" }
            case Some(i) => String.slice(start = i, end = i + 12, html)
        }

    def registerVoyage(p: Int32, router: String, number: String,
                       origin: String, destination: String): Unit \ Assert + IO =
        let token = csrfTokenOf(p, "/voyages/new", router);
        assertStatus(302, postWith(p, "/voyages",
            "_csrf=" + token + "&voyageNumber=" + number +
            "&vesselName=TESTSHIP&carrierName=TESTLINE&capabilities=GENERAL" +
            "&legDeparture=" + origin + "&legArrival=" + destination +
            "&legDepartureTime=2099-01-05T10:00&legArrivalTime=2099-01-20T08:00", router))

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

    ///
    /// 追跡番号まで発行された貨物を作る（US04 → US06 → US09/US11 → US13 → US14）。
    ///
    /// **利用者と同じ経路で状態を作る**。SQL で直接作ると、
    /// 途中の遷移が壊れていても気付けない。
    ///
    /// **予約番号と追跡番号の両方を返す**。追跡番号から予約を引く画面が無いため
    /// （予約一覧は追跡番号で検索できない。IT10 レビュー・利用者代表の指摘）、
    /// 作成時に控えておくしかない
    def trackedCargoWithBooking(p: Int32, sales: String, router: String, email: String,
                                voyageNumber: String): (String, String) \ Assert + IO =
        registerVoyage(p, router, voyageNumber, "JPOSA", "USLAX");
        let code = registerShipper(p, sales, email);
        let bookToken = csrfTokenOf(p, "/bookings/new", sales);
        let res = postWith(p, "/bookings",
            "_csrf=" + bookToken + "&shipperCode=" + code +
            "&origin=JPOSA&destination=USLAX&arrivalDeadline=2099-12-31" +
            "&cargoType=GENERAL&weight=1200", sales);
        assertStatus(302, res);
        let bookingId = match header(res, "Set-Cookie") {
            case None    => { Assert.fail("予約の応答に通知 Cookie が付いていない"); "" }
            case Some(v) => match String.indexOf(substr = "BK-", v) {
                case None    => { Assert.fail("通知に予約番号が含まれていない"); "" }
                case Some(i) => String.slice(start = i, end = i + 11, v)
            }
        };
        let assignToken = csrfTokenOf(p, "/bookings/" + bookingId, sales);
        assertStatus(302, postWith(p, "/bookings/" + bookingId + "/assign-to-routing",
                                   "_csrf=" + assignToken, sales));
        let routePage = body(getWith(p, "/bookings/" + bookingId + "/route", router));
        let key = candidateKeyIn(routePage);
        let routeToken = csrfTokenOf(p, "/bookings", router);
        assertStatus(302, postWith(p, "/bookings/" + bookingId + "/route",
                                   "_csrf=" + routeToken + "&candidate=" + key, router));
        let confirmToken = csrfTokenOf(p, "/bookings", sales);
        assertStatus(302, postWith(p, "/bookings/" + bookingId + "/confirm",
                                   "_csrf=" + confirmToken + "&action=confirm", sales));
        let issueToken = csrfTokenOf(p, "/bookings", router);
        assertStatus(302, postWith(p, "/bookings/" + bookingId + "/tracking-number",
                                   "_csrf=" + issueToken, router));
        // 発行された追跡番号を予約詳細から読む（利用者と同じ経路）
        let detail = body(getWith(p, "/bookings/" + bookingId, sales));
        let marker = "id=\"tracking-number\">";
        let number = match String.indexOf(substr = marker, detail) {
            case None => { Assert.fail("予約詳細に追跡番号が出ていません"); "" }
            case Some(i) =>
                let start = i + String.length(marker);
                let rest = String.slice(start = start, end = String.length(detail), detail);
                match String.indexOf(substr = "<", rest) {
                    case None    => { Assert.fail("追跡番号が閉じていない"); "" }
                    case Some(j) => String.slice(start = 0, end = j, rest)
                }
        };
        (bookingId, number)

    /// 追跡番号だけが要るとき
    def trackedCargo(p: Int32, sales: String, router: String, email: String,
                     voyageNumber: String): String \ Assert + IO =
        snd(trackedCargoWithBooking(p, sales, router, email, voyageNumber))

    /// 荷役を登録する
    def registerHandling(p: Int32, actor: String, trackingNumber: String,
                         handlingType: String, location: String,
                         voyageNumber: String): TestResponse \ Assert + IO =
        let token = csrfTokenOf(p, "/handling/new", actor);
        postWith(p, "/handling",
            "_csrf=" + token + "&trackingNumber=" + trackingNumber +
            "&handlingType=" + handlingType + "&location=" + location +
            "&completionTime=2026-08-01T10:00&voyageNumber=" + voyageNumber, actor)

    /// 通知 Cookie を次の要求へ持たせる（ブラウザがやっていることを手で書く）
    def withFlashOf(res: TestResponse, sessionCookie: String): String =
        match header(res, "Set-Cookie") {
            case None    => sessionCookie
            case Some(v) => match String.indexOf(substr = ";", v) {
                case None    => sessionCookie + "; " + v
                case Some(i) => sessionCookie + "; " + String.slice(start = 0, end = i, v)
            }
        }

    // ---- 受入基準 1・2・3・4: 追跡番号で特定し、記録し、状態が変わる ----

    ///
    /// **追跡番号で貨物を特定して受領を記録でき、荷主の画面に反映されること**
    /// （受入基準 1・2・3・4）。
    ///
    /// **公開追跡まで確かめる**。記録が荷主に見えなければ、
    /// US15 の「荷主がリアルタイムで確認できる」は満たされていない。
    ///
    @Test
    def testHandlerRegistersReceive(): Unit \ Assert + IO =
        withApp("handling_receive_ok", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            let worker = loginAs(p, AuthFixtures.handlerUsername());
            let number = trackedCargo(p, sales, router, "receive@example.com", "V-9701S");

            // 登録前は「未受取」
            Assert.assertTrue(String.contains(substr = "未受取",
                body(SharedHttpTestClient.get(p, "/public/tracking/" + number))));

            assertStatus(302, registerHandling(p, worker, number, "RECEIVE", "JPOSA", ""));

            // 履歴に出ること
            let history = body(getWith(p, "/handling", worker));
            Assert.assertTrue(String.contains(substr = number, history));
            Assert.assertTrue(String.contains(substr = "受領", history));

            // **荷主の画面に反映されること**（同期。ADR-0012）
            Assert.assertTrue(String.contains(substr = "受取済",
                body(SharedHttpTestClient.get(p, "/public/tracking/" + number))))
        })

    ///
    /// **積込を記録すると「積み込み済」になること**（受入基準 4）。
    ///
    @Test
    def testHandlerRegistersLoad(): Unit \ Assert + IO =
        withApp("handling_load_ok", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            let worker = loginAs(p, AuthFixtures.handlerUsername());
            let number = trackedCargo(p, sales, router, "load@example.com", "V-9702S");

            assertStatus(302,
                registerHandling(p, worker, number, "LOAD", "JPOSA", "V-9702S"));

            Assert.assertTrue(String.contains(substr = "積み込み済",
                body(SharedHttpTestClient.get(p, "/public/tracking/" + number))))
        })

    ///
    /// **荷役の記録が荷主の追跡タイムラインに 1 行として現れること**（IT10 レビュー H8）。
    ///
    /// IT10 では輸送状態のバッジだけが変わり、**いつ・どこで何をしたかが出なかった**。
    /// 登録は `handling_activity` へ入るが、公開追跡が読むのは
    /// `tracking_handling_event` で、**そこには誰も書いていなかった**。
    ///
    /// US15 の「なぜ」は荷主がリアルタイムで確認できることであり、
    /// バッジが変わるだけでは「荷物が今どうなっているか」は分からない。
    ///
    /// **積込まで 2 件記録する**。1 件だけだと、上書きしている実装でも緑になる。
    ///
    @Test
    def testHandlingAppearsInTrackingTimeline(): Unit \ Assert + IO =
        withApp("handling_timeline", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            let worker = loginAs(p, AuthFixtures.handlerUsername());
            let number = trackedCargo(p, sales, router, "timeline@example.com", "V-9711S");

            // 登録前は履歴が空である（この文言が消えることを後で確かめる）
            Assert.assertTrue(String.contains(substr = "イベント履歴はまだありません",
                body(SharedHttpTestClient.get(p, "/public/tracking/" + number))));

            assertStatus(302, registerHandling(p, worker, number, "RECEIVE", "JPOSA", ""));
            assertStatus(302,
                registerHandling(p, worker, number, "LOAD", "JPOSA", "V-9711S"));

            let page = body(SharedHttpTestClient.get(p, "/public/tracking/" + number));
            Assert.assertTrue(not String.contains(substr = "イベント履歴はまだありません", page));
            // **2 件とも残る**（後の記録が前の記録を消さない）
            Assert.assertTrue(String.contains(substr = "受取（RECEIVE）", page));
            Assert.assertTrue(String.contains(substr = "積込（LOAD）", page));
            // **いつ・どこで**が出る
            Assert.assertTrue(String.contains(substr = "2026-08-01 10:00", page));
            Assert.assertTrue(String.contains(substr = "JPOSA", page))
        })

    // ---- 受入基準 6: 存在しない追跡番号 ----

    ///
    /// **存在しない追跡番号は「次に何をすればよいか」を言うこと**（受入基準 6）。
    ///
    /// 追跡番号の打ち間違いは日常的に起きる。「エラーです」だけでは、
    /// 荷役作業員は番号が悪いのか権限が無いのかを判断できない。
    ///
    /// **何も書かないこと**も確かめる——貨物が無いのに履歴だけ残ると、
    /// 後から「どの貨物の記録か」が分からない。
    ///
    @Test
    def testUnknownTrackingNumberIsRejected(): Unit \ Assert + IO =
        withApp("handling_unknown_tracking", p -> {
            let worker = loginAs(p, AuthFixtures.handlerUsername());
            let res = registerHandling(p, worker, "TRK-20260805-9999",
                                       "RECEIVE", "JPOSA", "");
            Assert.assertEq(expected = 200, status(res));
            Assert.assertTrue(String.contains(substr = "登録されていません", body(res)));
            Assert.assertTrue(String.contains(substr = "経路設計者が発行します", body(res)));
            // 履歴には何も残らない
            Assert.assertTrue(String.contains(substr = "handling-empty",
                body(getWith(p, "/handling", worker))))
        })

    // ---- 受入基準 7: 予定ルートと異なる場合の警告 ----

    ///
    /// **予定外の港で積み込んだら経路逸脱として記録し、記録自体は受け付けること**
    /// （受入基準 7）。
    ///
    /// 記録を拒むと現場が記録できず、追跡が実態から離れる。
    /// 貨物は実際にその港で積まれている——事実は残す。
    ///
    /// **予約の経路状態が MISROUTED になること**まで確かめる（ADR-0012 の
    /// Handling → Booking）。荷役だけが知っていても、経路設計者は気付けない。
    ///
    @Test
    def testLoadAtUnplannedPortIsMisrouted(): Unit \ Assert + IO =
        withApp("handling_misrouted", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            let worker = loginAs(p, AuthFixtures.handlerUsername());
            let created = trackedCargoWithBooking(p, sales, router,
                                                  "misroute@example.com", "V-9703S");
            let bookingId = fst(created);
            let number = snd(created);

            // 予定は JPOSA 積込（V-9703S は JPOSA → USLAX の直行）。
            // **JPTYO で積んだ**——マスタに実在する港でなければ FK で弾かれるため、
            // 「予定に無い港」として実在する別の港を使う
            let res = registerHandling(p, worker, number, "LOAD", "JPTYO", "V-9703S");
            assertStatus(302, res);

            // **記録は残る**
            let history = body(getWith(p, "/handling", withFlashOf(res, worker)));
            Assert.assertTrue(String.contains(substr = "経路逸脱", history));
            // **次の一手を言う**
            Assert.assertTrue(String.contains(substr = "経路設計者へご連絡ください", history));
            // 輸送状態は進む（貨物は実際に積まれている）
            Assert.assertTrue(String.contains(substr = "積み込み済",
                body(SharedHttpTestClient.get(p, "/public/tracking/" + number))));

            // **予約側にも届いていること**（ADR-0012 の Handling → Booking）。
            // IT10 のレビューで、本テストが Handling と Tracking しか見ておらず
            // `BookingMisroutingReport` の経路が**実質未検証**だったことが分かった。
            // 経路全体を削除しても緑のままだった
            let detail = body(getWith(p, "/bookings/" + bookingId, router));
            Assert.assertTrue(String.contains(substr = "経路逸脱", detail));
            // **旅程が消えていないこと**。`markMisrouted` は `saveItinerary` で書くため、
            // DELETE + INSERT の経路を通る。どの経路のつもりだったかが残らないと、
            // 経路設計者はどこで外れたかを追えない
            Assert.assertTrue(String.contains(substr = "V-9703S", detail))
        })

    ///
    /// **受領の場所が違っても経路逸脱にはしないこと**（受入基準 7 の書き分け）。
    ///
    /// 集荷場所が予定と違うのは「別の倉庫で受け取った」程度のことが多く、
    /// 経路そのものは生きている。MISROUTED にすると経路設計者が無用な作り直しをする。
    ///
    @Test
    def testReceiveAtDifferentPortWarnsOnly(): Unit \ Assert + IO =
        withApp("handling_receive_warned", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            let worker = loginAs(p, AuthFixtures.handlerUsername());
            let number = trackedCargo(p, sales, router, "warned@example.com", "V-9704S");

            let res = registerHandling(p, worker, number, "RECEIVE", "JPTYO", "");
            assertStatus(302, res);

            let history = body(getWith(p, "/handling", withFlashOf(res, worker)));
            Assert.assertTrue(String.contains(substr = "要確認", history));
            Assert.assertTrue(not String.contains(substr = "経路逸脱", history))
        })

    // ---- 到達性（Try T1） ----

    ///
    /// **荷役作業員が navbar から登録画面へ到達できること**（受入基準 7 の前提）。
    ///
    /// IT7 から 3 イテレーション、荷役作業員は「押せる入口を 1 つも持たないロール」だった。
    /// 受入テストが URL を直接叩くと、入口が無くても緑になる（IT9 レビュー H1）。
    /// **画面の HTML からリンクを辿る**。
    ///
    @Test
    def testHandlerReachesHandlingRegistration(): Unit \ Assert + IO =
        withApp("handling_reachable", p -> {
            let worker = loginAs(p, AuthFixtures.handlerUsername());
            // ダッシュボードから荷役管理へ
            let dashboard = body(getWith(p, "/", worker));
            Assert.assertTrue(String.contains(substr = "href=\"/handling\"", dashboard));
            // 荷役管理から登録画面へ
            let index = body(getWith(p, "/handling", worker));
            Assert.assertTrue(String.contains(substr = "href=\"/handling/new\"", index));
            assertStatus(200, getWith(p, "/handling/new", worker))
        })

    // ---- 認可 ----

    /// 営業担当者は荷役を登録できないこと（現場で作業した本人が記録する）
    @Test
    def testSalesCannotRegisterHandling(): Unit \ Assert + IO =
        withApp("handling_sales_forbidden", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            assertStatus(403, getWith(p, "/handling/new", sales));
            assertStatus(403, postWith(p, "/handling", "trackingNumber=X", sales))
        })

    ///
    /// **追跡管理者は履歴を見られるが登録はできないこと**。
    ///
    /// 追跡管理者は荷主からの問い合わせに答える立場であり、
    /// 「いつどこで積んだか」を見られないと答えられない。
    /// 一方、記録するのは現場で作業した本人である。
    ///
    @Test
    def testTrackerCanViewButNotRegister(): Unit \ Assert + IO =
        withApp("handling_tracker_readonly", p -> {
            let tracker = loginAs(p, AuthFixtures.trackerUsername());
            assertStatus(200, getWith(p, "/handling", tracker));
            assertStatus(403, getWith(p, "/handling/new", tracker));
            // 登録の導線も出さない（押せるのに失敗するボタンを出さない）
            Assert.assertTrue(not String.contains(substr = "href=\"/handling/new\"",
                body(getWith(p, "/handling", tracker))))
        })

    // ---- 入力の保持 ----

    ///
    /// **拒まれても入力を保つこと**。
    ///
    /// 現場は片手で入力している。追跡番号と日時を打ち直させない。
    ///
    @Test
    def testKeepsInputWhenRejected(): Unit \ Assert + IO =
        withApp("handling_keeps_input", p -> {
            let worker = loginAs(p, AuthFixtures.handlerUsername());
            // 積込なのに航海番号が無い
            let res = registerHandling(p, worker, "TRK-20260805-0001",
                                       "LOAD", "JPOSA", "");
            Assert.assertEq(expected = 200, status(res));
            Assert.assertTrue(String.contains(substr = "航海番号が必要です", body(res)));
            Assert.assertTrue(String.contains(substr = "TRK-20260805-0001", body(res)));
            Assert.assertTrue(String.contains(substr = "JPOSA", body(res)))
        })
}
