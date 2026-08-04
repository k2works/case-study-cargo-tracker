///
/// 経路割り当ての受入テスト（US08）。
///
/// HTTP リクエストからデータベースまでを縦断する。
/// **予約 → 引き渡し → 航海登録 → 経路候補**の連結が主眼である。
///
/// [テスト戦略](../../../../docs/design/test_strategy.md) の方針どおり、
/// **経路の組み合わせごとの検証は `RouteFinderTest`（ドメイン単体）に置き**、
/// ここでは経路ごとに 1 本だけ通す。IT6 引き継ぎ M11（統合テストの比率 35%）への
/// 対応であり、Datalog の組み合わせを HTTP で網羅するとサーバ起動が跳ね上がる。
///
/// **DB 名は `route_assign_` で始める**。H2 のインメモリ DB は名前で共有されるため、
/// 他のテストと同じ名前を使うとシードが 2 度走って主キー違反になる（IT6 で発生）。
///
/// **日付は 2099 年を使う**。予約の到着期限には「本日以降」という業務ルールがあり
/// （`BookingModel`）、実時刻を使うテストで近い将来の日付を書くと**その日が来た瞬間に
/// 落ちる**。当初 2026-04 と書いて実際に落ちた。テストが時間の経過で壊れる形にしない。
///
mod RouteAssignHttpTest {
    use SharedHttpTestClient.{status, body, assertStatus, TestResponse}
    use TestApp.withApp

    def password(): String = "correct horse battery staple"

    // ---- ヘルパー ----

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
                let rest = String.slice(start = i + String.length(marker),
                                        end = String.length(html), html);
                match String.indexOf(substr = "\"", rest) {
                    case None    => { Assert.fail("CSRF トークンが閉じていない"); "" }
                    case Some(j) => String.slice(start = 0, end = j, rest)
                }
        }

    /// 荷主を登録して荷主コードを返す
    def registerShipper(p: Int32, cookie: String, email: String): String \ Assert + IO =
        let token = csrfTokenOf(p, "/shippers/new", cookie);
        assertStatus(302, postWith(p, "/shippers",
            "_csrf=" + token + "&shipperType=INDIVIDUAL&name=" + "route-test" +
            "&email=" + email, cookie));
        let html = body(getWith(p, "/shippers?q=" + email, cookie));
        match String.indexOf(substr = "SHP-", html) {
            case None    => { Assert.fail("荷主一覧に ${email} が見つからない"); "" }
            case Some(i) => String.slice(start = i, end = i + 12, html)
        }

    ///
    /// 予約を登録し、経路設計者へ引き渡して予約番号を返す。
    ///
    /// **引き渡しまで行う**のが要点である。`ROUTE_PROPOSED` でなければ
    /// 経路割り当ての対象にならない（ACL が SQL で絞る）。
    ///
    def bookAndAssign(p: Int32, sales: String, email: String,
                      origin: String, destination: String, deadline: String,
                      cargoType: String): String \ Assert + IO =
        let code = registerShipper(p, sales, email);
        let token = csrfTokenOf(p, "/bookings/new", sales);
        let res = postWith(p, "/bookings",
            "_csrf=" + token + "&shipperCode=" + code +
            "&origin=" + origin + "&destination=" + destination +
            "&arrivalDeadline=" + deadline + "&cargoType=" + cargoType +
            "&weight=1200", sales);
        assertStatus(302, res);
        let bookingId = match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None    => { Assert.fail("予約の応答に通知 Cookie が付いていない"); "" }
            case Some(v) => match String.indexOf(substr = "BK-", v) {
                case None    => { Assert.fail("通知に予約番号が含まれていない: ${v}"); "" }
                case Some(i) => String.slice(start = i, end = i + 11, v)
            }
        };
        let assignToken = csrfTokenOf(p, "/bookings/" + bookingId, sales);
        assertStatus(302, postWith(p, "/bookings/" + bookingId + "/assign-to-routing",
                                   "_csrf=" + assignToken, sales));
        bookingId

    /// 区間 1 本の航海を登録する
    def registerVoyage(p: Int32, router: String, number: String,
                       origin: String, destination: String,
                       departure: String, arrival: String): Unit \ Assert + IO =
        let token = csrfTokenOf(p, "/voyages/new", router);
        assertStatus(302, postWith(p, "/voyages",
            "_csrf=" + token + "&voyageNumber=" + number +
            "&vesselName=TESTSHIP&carrierName=TESTLINE&capabilities=GENERAL" +
            "&legDeparture=" + origin + "&legArrival=" + destination +
            "&legDepartureTime=" + departure + "&legArrivalTime=" + arrival,
            router))

    ///
    /// 候補のラジオボタンから 1 件目の選択キーを取り出す。
    ///
    /// **キーは航海番号の連結**である（`V-0001S` / `V-0001S|V-0002S`）。
    /// 候補は永続化されないため、サーバは受け取ったキーで候補を**引き直して**
    /// 突き合わせる。**フォームから区間の中身を受け取らない**——
    /// 受け取ると利用者が改竄でき、実在しない経路を予約に紐付けられる。
    ///
    def firstCandidateKey(html: String): String \ Assert =
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

    /// 選んだ候補を割り当てる
    def assignRoute(p: Int32, router: String, bookingId: String,
                    candidateKey: String): TestResponse \ Assert + IO =
        let path = "/bookings/" + bookingId + "/route";
        let token = csrfTokenOf(p, path, router);
        postWith(p, path, "_csrf=" + token + "&candidate=" + candidateKey, router)

    // ---- US09 / US11: 経路の選択・確定と予約への紐付け ----

    ///
    /// **候補を 1 件選んで割り当てられること**（US09 受入基準 2・3／US11 全基準）。
    ///
    /// 割り当て後、予約詳細に**確定した経路が出る**。
    /// 経路状態は `ROUTED` になり、表示は「経路提案済」へ変わる——
    /// **`BookingStatus` は `ROUTE_PROPOSED` のまま**である（着手前の決定 2）。
    /// `CONFIRMED` へ進めるのは営業が荷主の承認を確認したとき（US13）だけである。
    ///
    /// この式が偽になる入力: 割り当て時に `CONFIRMED` まで進める実装
    /// （US13 の確定操作が意味を失い、荷主未承認で「予約確定」になる）。
    ///
    @Test
    def testRouterSelectsAndAttachesRoute(): Unit \ Assert + IO =
        withApp("route_assign_select", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9101S", "JPOSA", "USLAX",
                           "2099-01-05T10:00", "2099-01-20T08:00");
            let bookingId = bookAndAssign(p, sales, "yamada@example.com",
                                          "JPOSA", "USLAX", "2099-12-31", "GENERAL");
            let page = body(getWith(p, "/bookings/" + bookingId + "/route", router));
            let key = firstCandidateKey(page);
            assertStatus(302, assignRoute(p, router, bookingId, key));

            let detail = body(getWith(p, "/bookings/" + bookingId, router));
            // US11 受入基準 1: 確定経路と予約番号を確認できる
            Assert.assertTrue(String.contains(substr = "V-9101S", detail));
            Assert.assertTrue(String.contains(substr = bookingId, detail));
            // US11 受入基準 3: 状態の表示が「経路提案済」へ変わる
            Assert.assertTrue(String.contains(substr = "経路提案済", detail));
            // **予約確定にはしない**（US13 の職掌）
            Assert.assertTrue(not String.contains(substr = "予約確定", detail))
        })

    ///
    /// **積替のある候補も割り当てられること**（壊れ方の観点・軸 1）。
    ///
    /// 直行だけを通す実装だと、区間 2 本の旅程で連結の検証が動かない。
    ///
    @Test
    def testTransshipmentRouteCanBeAttached(): Unit \ Assert + IO =
        withApp("route_assign_attach_transship", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9201S", "JPOSA", "JPTYO",
                           "2099-01-03T10:00", "2099-01-04T09:00");
            registerVoyage(p, router, "V-9202S", "JPTYO", "USLAX",
                           "2099-01-06T10:00", "2099-01-22T08:00");
            let bookingId = bookAndAssign(p, sales, "yamada@example.com",
                                          "JPOSA", "USLAX", "2099-12-31", "GENERAL");
            let page = body(getWith(p, "/bookings/" + bookingId + "/route", router));
            let key = firstCandidateKey(page);
            assertStatus(302, assignRoute(p, router, bookingId, key));
            let detail = body(getWith(p, "/bookings/" + bookingId, router));
            Assert.assertTrue(String.contains(substr = "V-9201S", detail));
            Assert.assertTrue(String.contains(substr = "V-9202S", detail))
        })

    ///
    /// **存在しない候補キーは拒むこと**（改竄の防止）。
    ///
    /// 候補は永続化されないため、サーバは受け取ったキーで候補を引き直す。
    /// 一致しなければ**予約に何も書かない**。
    ///
    /// この式が偽になる入力: フォームから区間の中身を受け取って
    /// そのまま保存する実装（実在しない航海番号の旅程が作れる）。
    ///
    @Test
    def testUnknownCandidateIsRejected(): Unit \ Assert + IO =
        withApp("route_assign_tamper", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9301S", "JPOSA", "USLAX",
                           "2099-01-05T10:00", "2099-01-20T08:00");
            let bookingId = bookAndAssign(p, sales, "yamada@example.com",
                                          "JPOSA", "USLAX", "2099-12-31", "GENERAL");
            let res = assignRoute(p, router, bookingId, "V-NOSUCH");
            // 割り当ては起きず、画面には理由が出る
            Assert.assertEq(expected = 200, status(res));
            Assert.assertTrue(String.contains(substr = "選択した経路", body(res)));
            let detail = body(getWith(p, "/bookings/" + bookingId, router));
            Assert.assertTrue(not String.contains(substr = "経路提案済", detail))
        })

    ///
    /// **営業担当者は割り当てできないこと**（軸 6・職掌）。
    ///
    /// 経路の選択・確定は経路設計者の専門業務である。
    ///
    @Test
    def testSalesCannotAttachRoute(): Unit \ Assert + IO =
        withApp("route_assign_authz", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9401S", "JPOSA", "USLAX",
                           "2099-01-05T10:00", "2099-01-20T08:00");
            let bookingId = bookAndAssign(p, sales, "yamada@example.com",
                                          "JPOSA", "USLAX", "2099-12-31", "GENERAL");
            let page = body(getWith(p, "/bookings/" + bookingId + "/route", router));
            let key = firstCandidateKey(page);
            Assert.assertEq(expected = 403, status(assignRoute(p, sales, bookingId, key)))
        })

    // ---- デモ項目 1: 直行便が最優先で提示される ----

    ///
    /// 引き渡し済みの予約に対し、直行便が候補として提示されること。
    ///
    /// この式が偽になる入力: ACL が `booking_status` で絞らず、
    /// 引き渡し前の予約まで対象にする実装（別のテストで確かめる）。
    ///
    @Test
    def testDirectVoyageIsOffered(): Unit \ Assert + IO =
        withApp("route_assign_direct", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            let bookingId = bookAndAssign(p, sales, "route-direct@example.com",
                                          "JPOSA", "USLAX", "2099-04-15", "GENERAL");
            registerVoyage(p, router, "V-RT-DIRECT", "JPOSA", "USLAX",
                           "2099-04-01T18:00", "2099-04-14T08:00");
            let res = getWith(p, "/bookings/" + bookingId + "/route", router);
            assertStatus(200, res);
            let html = body(res);
            Assert.assertTrue(String.contains(substr = "V-RT-DIRECT", html));
            Assert.assertTrue(String.contains(substr = "直行", html));
            // 依頼の条件が出ること（US07 受入基準 1・引き継ぎ M5）
            Assert.assertTrue(String.contains(substr = "JPOSA", html));
            Assert.assertTrue(String.contains(substr = "2099-04-15", html))
        })

    // ---- デモ項目 2: 寄港地を経由する積み替え ----

    ///
    /// 2 本の航海をつないだ候補が提示されること。
    ///
    /// この式が偽になる入力: 単一航海の中でしか到達可能性を判定しない実装。
    ///
    @Test
    def testTransshipmentIsOffered(): Unit \ Assert + IO =
        withApp("route_assign_transship", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            let bookingId = bookAndAssign(p, sales, "route-trans@example.com",
                                          "JPOSA", "USLAX", "2099-04-15", "GENERAL");
            registerVoyage(p, router, "V-RT-FEEDER", "JPOSA", "CNSHA",
                           "2099-04-02T08:00", "2099-04-04T08:00");
            registerVoyage(p, router, "V-RT-MAIN", "CNSHA", "USLAX",
                           "2099-04-05T00:00", "2099-04-13T06:00");
            let html = body(getWith(p, "/bookings/" + bookingId + "/route", router));
            Assert.assertTrue(String.contains(substr = "V-RT-FEEDER", html));
            Assert.assertTrue(String.contains(substr = "V-RT-MAIN", html));
            // 経由港が出ること（受入基準 3）
            Assert.assertTrue(String.contains(substr = "CNSHA", html))
        })

    // ---- デモ項目 5: 候補 0 件でも行き止まりにしない ----

    ///
    /// 航海が 1 件も無いとき、その旨と次の一手が示されること。
    ///
    /// この式が偽になる入力: 空の表だけを出す実装。
    ///
    @Test
    def testNoVoyagesGuidesToVoyageManagement(): Unit \ Assert + IO =
        withApp("route_assign_no_voyage", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            let bookingId = bookAndAssign(p, sales, "route-empty@example.com",
                                          "JPOSA", "USLAX", "2099-04-15", "GENERAL");
            let html = body(getWith(p, "/bookings/" + bookingId + "/route", router));
            Assert.assertTrue(String.contains(substr = "航海スケジュールが登録されていません", html));
            Assert.assertTrue(String.contains(substr = "href=\"/voyages\"", html))
        })

    ///
    /// 航海はあるが条件に合わないとき、別の文言と導線になること。
    ///
    /// **「未登録」と「合わない」を同じ画面にしない**。経路設計者は
    /// 「登録すべきか」「条件を緩めるべきか」を判断できなくなる。
    ///
    /// この式が偽になる入力: 0 件をすべて同じ文言で扱う実装。
    ///
    @Test
    def testNoMatchingRouteGuidesToConsultation(): Unit \ Assert + IO =
        withApp("route_assign_no_match", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            let bookingId = bookAndAssign(p, sales, "route-nomatch@example.com",
                                          "JPOSA", "USLAX", "2099-04-15", "GENERAL");
            // 仕向地の違う航海だけを登録する
            registerVoyage(p, router, "V-RT-OTHER", "JPOSA", "DEHAM",
                           "2099-04-01T18:00", "2099-04-10T08:00");
            let html = body(getWith(p, "/bookings/" + bookingId + "/route", router));
            Assert.assertTrue(String.contains(substr = "条件を満たす経路がありません", html));
            Assert.assertTrue(not String.contains(substr = "航海スケジュールが登録されていません", html))
        })

    // ---- 対象の絞り込み ----

    ///
    /// **引き渡し前の予約は対象外になること**。
    ///
    /// ACL は `booking_status = 'ROUTE_PROPOSED'` で絞る。絞らないと、
    /// 営業がまだ内容を詰めている予約に経路設計者が着手できてしまう。
    ///
    /// この式が偽になる入力: ACL の SQL から状態の条件を外した実装。
    ///
    @Test
    def testPreliminaryBookingIsNotAvailable(): Unit \ Assert + IO =
        withApp("route_assign_preliminary", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            let code = registerShipper(p, sales, "route-prelim@example.com");
            let token = csrfTokenOf(p, "/bookings/new", sales);
            let res = postWith(p, "/bookings",
                "_csrf=" + token + "&shipperCode=" + code +
                "&origin=JPOSA&destination=USLAX&arrivalDeadline=2099-04-15" +
                "&cargoType=GENERAL&weight=1200", sales);
            let bookingId = match SharedHttpTestClient.header(res, "Set-Cookie") {
                case None    => { Assert.fail("通知 Cookie が無い"); "" }
                case Some(v) => match String.indexOf(substr = "BK-", v) {
                    case None    => { Assert.fail("予約番号が無い"); "" }
                    case Some(i) => String.slice(start = i, end = i + 11, v)
                }
            };
            // 引き渡していないので対象外
            assertStatus(404, getWith(p, "/bookings/" + bookingId + "/route", router))
        })

    ///
    /// **存在しない予約と引き渡し前の予約が同じ応答になること**。
    ///
    /// 書き分けると、存在の有無が漏れる（観点表の軸 6）。
    ///
    /// この式が偽になる入力: 存在しないときだけ 404、引き渡し前は 409 を返す実装。
    ///
    @Test
    def testUnknownBookingLooksTheSame(): Unit \ Assert + IO =
        withApp("route_assign_unknown", p -> {
            let router = loginAs(p, AuthFixtures.routerUsername());
            let res = getWith(p, "/bookings/BK-99999999/route", router);
            assertStatus(404, res);
            Assert.assertTrue(String.contains(substr = "経路設計の対象ではありません", body(res)))
        })

    ///
    /// **形式が不正な予約番号で応答が返ること**。
    ///
    /// パス変数は URL に直接書ける。`<input>` はブラウザ経由の入力しか守らない。
    /// IT4（`2026-02-30`）と IT6（`?departureFrom=abc`）はいずれも
    /// **検証を通さない値が SQL の型変換へ届き、応答が返らなくなった**（Try T5）。
    ///
    /// この式が偽になる入力: `pathParam` の値をそのまま SQL へ渡す実装。
    ///
    @Test
    def testMalformedBookingIdDoesNotBreak(): Unit \ Assert + IO =
        withApp("route_assign_malformed", p -> {
            let router = loginAs(p, AuthFixtures.routerUsername());
            let bad = "'%20OR%201=1--" :: "abc" :: "%E6%97%A5%E6%9C%AC%E8%AA%9E" :: Nil;
            let statuses = bad |> List.map(id ->
                status(getWith(p, "/bookings/" + id + "/route", router)));
            // すべて 404（応答が返らない・500 になるのを禁じる）
            Assert.assertEq(expected = 404 :: 404 :: 404 :: Nil, statuses)
        })

    // ---- 認可と導線 ----

    ///
    /// 営業担当者は経路割り当てを開けないこと。
    ///
    /// 経路の選択・割り当ては経路設計者の職掌である
    /// （`architecture_backend.md` のロール表）。営業は US06 の引き渡しまで。
    ///
    /// この式が偽になる入力: ルートを `salesOrRouter()` で登録した実装。
    ///
    @Test
    def testSalesCannotOpenRouteAssignment(): Unit \ Assert + IO =
        withApp("route_assign_forbidden", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            assertStatus(403, getWith(p, "/bookings/BK-00000001/route", sales))
        })

    ///
    /// **経路設計者が予約詳細から経路割り当てへ辿り着けること**。
    ///
    /// IT6 は「画面単体は正しいがロールをまたぐと切れる」欠陥を 3 件出し、
    /// `operation.md` の暫定運用は経路設計者が `/bookings` を開けないために
    /// 実行できなかった（レビュー M2-3）。**書いた手順が実行できるか**を
    /// テストで確かめる（Try T4）。
    ///
    /// この式が偽になる入力: `/bookings` を営業と荷主だけに開いたままの実装、
    /// または予約詳細に経路割り当てのリンクを置かない実装。
    ///
    @Test
    def testRouterReachesAssignmentFromBookingDetail(): Unit \ Assert + IO =
        withApp("route_assign_navigation", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            let bookingId = bookAndAssign(p, sales, "route-nav@example.com",
                                          "JPOSA", "USLAX", "2099-04-15", "GENERAL");
            // ダッシュボードに予約の入口がある
            Assert.assertTrue(String.contains(substr = "href=\"/bookings\"",
                                              body(getWith(p, "/", router))));
            // 一覧が開ける
            assertStatus(200, getWith(p, "/bookings", router));
            // 詳細から経路割り当てへのリンクがある
            let detail = body(getWith(p, "/bookings/" + bookingId, router));
            Assert.assertTrue(String.contains(
                substr = "href=\"/bookings/" + bookingId + "/route\"", detail))
        })

    ///
    /// **営業には経路割り当てのリンクを出さないこと**。
    ///
    /// 押すと 403 になるボタンを出さない（観点表の軸 3-7）。
    /// IT6 は荷主に「新規予約登録」を出して押すと 403 になっていた（P2）。
    ///
    /// この式が偽になる入力: 予約詳細が常に経路割り当てのリンクを出す実装。
    ///
    @Test
    def testSalesDoesNotSeeRouteLink(): Unit \ Assert + IO =
        withApp("route_assign_no_link", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let bookingId = bookAndAssign(p, sales, "route-nolink@example.com",
                                          "JPOSA", "USLAX", "2099-04-15", "GENERAL");
            let detail = body(getWith(p, "/bookings/" + bookingId, sales));
            Assert.assertTrue(not String.contains(
                substr = "href=\"/bookings/" + bookingId + "/route\"", detail))
        })
}
