///
/// 追跡番号の発行（US14・IT10）。
///
/// # この利用者は毎朝どう使うか
///
/// 1. 営業が荷主の承認を得て予約を確定する（US13）
/// 2. 経路設計者が予約詳細を開き、**確定済みであることを見てから**追跡番号を発行する
/// 3. 発行した番号を営業へ伝え、営業が荷主へ案内する
///
/// **確定していない予約に発行しない**——荷主が承認しなかったときに
/// 番号だけが世に出る。逆に、発行済みの予約に別の番号を入れると
/// 荷主が持っている番号で追えなくなる。
///
/// # 通知は将来リリース
///
/// 受入基準 4（荷主へのメール通知）は **US12 と同じ通知基盤**であり将来リリースである。
///
mod TrackingNumberHttpTest {
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

    /// 予約を登録する（仮受付のまま返す）
    def book(p: Int32, sales: String, email: String): String \ Assert + IO =
        let code = registerShipper(p, sales, email);
        let token = csrfTokenOf(p, "/bookings/new", sales);
        let res = postWith(p, "/bookings",
            "_csrf=" + token + "&shipperCode=" + code +
            "&origin=JPOSA&destination=USLAX&arrivalDeadline=2099-12-31" +
            "&cargoType=GENERAL&weight=1200", sales);
        assertStatus(302, res);
        match header(res, "Set-Cookie") {
            case None    => { Assert.fail("予約の応答に通知 Cookie が付いていない"); "" }
            case Some(v) => match String.indexOf(substr = "BK-", v) {
                case None    => { Assert.fail("通知に予約番号が含まれていない"); "" }
                case Some(i) => String.slice(start = i, end = i + 11, v)
            }
        }

    ///
    /// 確定済みの予約を作る（US04 → US06 → US09/US11 → US13 を通す）。
    ///
    /// **利用者と同じ経路で状態を作る**。SQL で直接作ると、
    /// 途中の遷移が壊れていても気付けない。
    ///
    def confirmedBooking(p: Int32, sales: String, router: String,
                         email: String): String \ Assert + IO =
        let bookingId = book(p, sales, email);
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
        bookingId

    /// 追跡番号を発行する
    def issue(p: Int32, actor: String, bookingId: String): TestResponse \ Assert + IO =
        let token = csrfTokenOf(p, "/bookings", actor);
        postWith(p, "/bookings/" + bookingId + "/tracking-number", "_csrf=" + token, actor)

    ///
    /// 応答が載せた通知 Cookie を、次の要求に持たせる形で返す。
    ///
    /// **ブラウザがやっていることを手で書く**。通知（Flash）は Cookie で運ばれるため、
    /// セッション Cookie だけで詳細を開くと**メッセージが出ない**——
    /// 実装が正しくてもテストが落ちる。
    ///
    def withFlashOf(res: TestResponse, sessionCookie: String): String =
        match header(res, "Set-Cookie") {
            case None    => sessionCookie
            case Some(v) => match String.indexOf(substr = ";", v) {
                case None    => sessionCookie + "; " + v
                case Some(i) => sessionCookie + "; " + String.slice(start = 0, end = i, v)
            }
        }

    /// 予約詳細に出ている追跡番号
    def issuedNumberIn(html: String): Option[String] =
        let marker = "id=\"tracking-number\">";
        match String.indexOf(substr = marker, html) {
            case None => None
            case Some(i) =>
                let start = i + String.length(marker);
                let rest = String.slice(start = start, end = String.length(html), html);
                match String.indexOf(substr = "<", rest) {
                    case None    => None
                    case Some(j) => Some(String.slice(start = 0, end = j, rest))
                }
        }

    // ---- 受入基準 1・3: 確定済みの予約に発行し、貨物状態が受領待ちになる ----

    ///
    /// **確定済みの予約に追跡番号を発行できること**（受入基準 1）。
    ///
    /// 発行すると予約は「追跡番号発行済」へ進み、詳細画面に番号が出る。
    /// **公開追跡でその番号が引けること**（受入基準 3 の「受領待ち」）まで確かめる——
    /// 番号を発行しても荷主が引けなければ、業務としては何も起きていない。
    ///
    @Test
    def testRouterIssuesTrackingNumber(): Unit \ Assert + IO =
        withApp("tracking_number_issue_ok", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9601S", "JPOSA", "USLAX");
            let bookingId = confirmedBooking(p, sales, router, "issue@example.com");

            assertStatus(302, issue(p, router, bookingId));

            let detail = body(getWith(p, "/bookings/" + bookingId, sales));
            Assert.assertTrue(String.contains(substr = "追跡番号発行済", detail));
            match issuedNumberIn(detail) {
                case None         => Assert.fail("予約詳細に追跡番号が出ていません。本文: " + detail)
                case Some(number) => {
                    Assert.assertTrue(TrackingModel.isWellFormed(number));
                    // 荷主が引けること（受領待ちで登録されている）
                    let tracking = body(SharedHttpTestClient.get(p, "/public/tracking/" + number));
                    Assert.assertTrue(String.contains(substr = "未受取", tracking))
                }
            }
        })

    // ---- 受入基準 2: 一意に採番される ----

    ///
    /// **同じ日に複数の予約へ発行しても番号が重複しないこと**（受入基準 2）。
    ///
    /// 採番は `TRK-YYYYMMDD-NNNN` で**日付を含む**ため、同一日内の連番が競合点になる。
    /// 連番は `MAX + 1` で決まり、同時に読めば同じ値が返る——
    /// **止めるのは DB の UNIQUE 制約**である。
    ///
    /// # このテストが識別できないこと（正直に書く）
    ///
    /// **UNIQUE 制約を外しても本テストは緑のままである**（実測）。
    /// テスト用構成では要求が実質的に直列化されるため、`MAX + 1` が
    /// 自然に別の値を返す。IT9 の P3 とまったく同じ空振りである。
    ///
    /// 制約が働いていることは
    /// `testDuplicateTrackingNumberIsRejectedByTheDatabase` が固定する——
    /// あちらは**制約そのものを破りにいく**ため、外すと赤くなる（実測）。
    /// 本テストは「同時に来ても業務として壊れない」ことを示すに留まる。
    ///
    @Test
    def testConcurrentIssueProducesDistinctNumbers(): Unit \ Assert + IO + Chan + NonDet =
        withApp("tracking_number_concurrent", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9602S", "JPOSA", "USLAX");
            let first = confirmedBooking(p, sales, router, "race1@example.com");
            let second = confirmedBooking(p, sales, router, "race2@example.com");
            let token = csrfTokenOf(p, "/bookings", router);

            discard SharedHttpTestClient.postTwoConcurrently(p,
                ("/bookings/" + first + "/tracking-number", "_csrf=" + token, router),
                ("/bookings/" + second + "/tracking-number", "_csrf=" + token, router));

            let a = issuedNumberIn(body(getWith(p, "/bookings/" + first, sales)));
            let b = issuedNumberIn(body(getWith(p, "/bookings/" + second, sales)));
            match (a, b) {
                case (Some(x), Some(y)) =>
                    if (x == y)
                        Assert.fail("同時に発行した 2 件へ同じ追跡番号が付きました: ${x}")
                    else ()
                case _ =>
                    // 片方が採番の競合で失敗するのは許容する（UNIQUE 制約が働いた形）。
                    // **同じ番号が 2 件付くことだけは許容しない**
                    ()
            }
        })

    ///
    /// **一意性を保証しているのが DB の UNIQUE 制約であること**（受入基準 2）。
    ///
    /// 上の同時発行テストは、テスト用構成で要求が実質的に直列化されるため
    /// **制約を外しても緑のまま**である（実測。IT9 の P3 と同じ空振り）。
    /// 「入れたこと」ではなく「働くこと」を検証する（Try T3）には、
    /// **制約そのものを破りにいく**必要がある。
    ///
    /// 発行済みの番号を別の予約へ直接書き込み、**DB が拒むこと**を確かめる。
    /// `uk_cargo_tracking_number` を外すとこのテストは赤くなる（実測）。
    ///
    /// SQL を直接叩くのは、**アプリケーションを通ると集約が先に止めてしまう**ためである。
    /// ここで確かめたいのは集約の検査ではなく、その下にある最後の砦のほうである。
    @Test
    def testDuplicateTrackingNumberIsRejectedByTheDatabase(): Unit \ Assert + IO =
        TestApp.withAppAndDb("tracking_number_unique_guard", (p, ds) -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9606S", "JPOSA", "USLAX");
            let issued = confirmedBooking(p, sales, router, "unique1@example.com");
            let other = book(p, sales, "unique2@example.com");
            assertStatus(302, issue(p, router, issued));

            let number = match issuedNumberIn(body(getWith(p, "/bookings/" + issued, sales))) {
                case Some(n) => n
                case None    => { Assert.fail("追跡番号が発行されていません"); "" }
            };

            if (TestSql.updateSucceeds(ds,
                    "UPDATE cargo SET tracking_number = '${number}' " +
                    "WHERE booking_id = '${other}'"))
                Assert.fail("重複した追跡番号 ${number} が 2 件目の予約に書けてしまいました。" +
                            "uk_cargo_tracking_number が効いていません")
            else ()
        })

    // ---- 受入基準 1 の否定側 ----

    ///
    /// **確定していない予約には発行できないこと**。
    ///
    /// 荷主は経路を見て承認する。承認していない予約に番号を出すと、
    /// **承認されなかったときに番号だけが世に出る**。
    ///
    @Test
    def testCannotIssueBeforeConfirmation(): Unit \ Assert + IO =
        withApp("tracking_number_not_confirmed", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            let bookingId = book(p, sales, "early@example.com");

            let res = issue(p, router, bookingId);
            assertStatus(302, res);

            let detail = body(getWith(p, "/bookings/" + bookingId, withFlashOf(res, sales)));
            Assert.assertTrue(Option.isEmpty(issuedNumberIn(detail)));
            Assert.assertTrue(String.contains(substr = "確定されていない", detail))
        })

    ///
    /// **二重に発行できないこと**。
    ///
    /// 既にある番号に別の番号を入れると、荷主が持っている番号で追えなくなる。
    /// 黙って上書きしない。
    ///
    @Test
    def testCannotIssueTwice(): Unit \ Assert + IO =
        withApp("tracking_number_twice", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9603S", "JPOSA", "USLAX");
            let bookingId = confirmedBooking(p, sales, router, "twice@example.com");

            assertStatus(302, issue(p, router, bookingId));
            let firstNumber = issuedNumberIn(body(getWith(p, "/bookings/" + bookingId, sales)));

            let again = issue(p, router, bookingId);
            assertStatus(302, again);
            let detail = body(getWith(p, "/bookings/" + bookingId, withFlashOf(again, sales)));

            Assert.assertTrue(String.contains(substr = "既に追跡番号が発行されています", detail));
            // **番号が変わっていないこと**。上書きされると荷主が追えなくなる
            Assert.assertEq(expected = firstNumber, issuedNumberIn(detail))
        })

    // ---- 到達性（IT9 レビュー H1・Try T1） ----

    ///
    /// **発行の導線が、その状態の予約からだけ出ること**。
    ///
    /// 受入テストが URL を直接 POST すると、画面からの入口が無くても緑になる
    /// （IT9 で差し戻しがどこからも実行できない状態を見逃した）。
    /// **状態ごとに詳細画面の HTML を見る**。
    ///
    @Test
    def testIssueLinkAppearsOnlyForConfirmedBooking(): Unit \ Assert + IO =
        withApp("tracking_number_reachable", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9604S", "JPOSA", "USLAX");
            let label = "追跡番号を発行する";
            let detailFor = actor -> bid -> body(getWith(p, "/bookings/" + bid, actor));

            // 仮受付——まだ出ない
            let preliminary = book(p, sales, "reach1@example.com");
            Assert.assertTrue(not String.contains(substr = label, detailFor(router)(preliminary)));

            // 確定済み——出る
            let confirmed = confirmedBooking(p, sales, router, "reach2@example.com");
            Assert.assertTrue(String.contains(substr = label, detailFor(router)(confirmed)));

            // **営業には出ない**（発行は経路設計者の職掌）
            Assert.assertTrue(not String.contains(substr = label, detailFor(sales)(confirmed)));

            // 発行後——消える（押せるのに必ず失敗するボタンを残さない）
            assertStatus(302, issue(p, router, confirmed));
            Assert.assertTrue(not String.contains(substr = label, detailFor(router)(confirmed)))
        })

    // ---- 認可 ----

    /// 営業担当者は追跡番号を発行できないこと
    @Test
    def testSalesCannotIssueTrackingNumber(): Unit \ Assert + IO =
        withApp("tracking_number_sales_forbidden", p -> {
            let sales = loginAs(p, AuthFixtures.activeUsername());
            let router = loginAs(p, AuthFixtures.routerUsername());
            registerVoyage(p, router, "V-9605S", "JPOSA", "USLAX");
            let bookingId = confirmedBooking(p, sales, router, "forbidden@example.com");
            assertStatus(403, issue(p, sales, bookingId))
        })
}
