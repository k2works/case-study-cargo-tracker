///
/// 輸送見積の受入テスト（US01・IT8）。
///
/// # このテストを先に書く理由
///
/// 終盤の局面はアウトサイドインである（[開発戦略](../../../../docs/development/development_strategy.md)）。
/// **利用者が辿る経路を先に固定してから**、内側（ドメイン・永続化）を作る。
///
/// IT4 では受入テストを書く過程で、予約フォームが内部識別子（UUID）を要求しており
/// **画面上どこにも出ていない**ことに気付いた。機能があっても使えない状態は、
/// 単体テストでは決して現れない。
///
/// # この利用者は毎朝どう使うか（Try T4）
///
/// 1. 荷主から電話・メールで「大阪からロサンゼルスへ 1.2 トン、4 月中旬まで」と引き合いが来る
/// 2. **その場で**概算の運賃と所要日数を答えたい。折り返しにすると他社に取られる
/// 3. 出した見積は後から「あのとき何と言ったか」を確認できる必要がある
///
/// 3 が受入基準に無い要求である。**見積は口頭で答えて終わりではない**——
/// だから見積番号を発行し、一覧と詳細から後で引ける必要がある。
///
mod EstimateHttpTest {
    use SharedHttpTestClient.{status, body, assertStatus, TestResponse}
    use TestApp.withApp

    def salesUser(): String = "yamada"
    def routerUser(): String = "router"
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

    ///
    /// 見積を作成する。
    ///
    /// **期限は 2099 年**にする。実日付に近い値を書くと、その日が過ぎた瞬間に
    /// テストが落ちる（IT7 の `RouteAssignHttpTest` で実際に起きた）。
    ///
    def createEstimate(p: Int32, cookie: String, formBody: String): TestResponse \ Assert + IO =
        let token = csrfTokenOf(p, "/estimates/new", cookie);
        postWith(p, "/estimates", formBody + "&_csrf=" + token, cookie)

    def validForm(): String =
        "origin=JPOSA&destination=USLAX&arrivalDeadline=2099-12-31" +
        "&cargoType=GENERAL&weight=1200"

    /// 一覧・詳細の HTML から見積番号を拾う
    def estimateIdsIn(html: String): List[String] =
        collectIds(html, Nil) |> List.reverse

    def collectIds(rest: String, acc: List[String]): List[String] =
        let marker = "href=\"/estimates/EST-";
        match String.indexOf(substr = marker, rest) {
            case None    => acc
            case Some(i) =>
                let start = i + String.length("href=\"/estimates/");
                let id = String.slice(start = start, end = start + 12, rest);
                let tail = String.slice(start = start + 12, end = String.length(rest), rest);
                collectIds(tail, id :: acc)
        }

    // ---- E2E シナリオ①: 見積作成 → 候補と費用を確認 ----

    ///
    /// **見積を作ると番号が発行され、候補に費用と所要日数が出ること**
    /// （US01 受入基準 2・3・4）。
    ///
    /// 電話口で答えるための情報は「いくら・何日」である。
    /// **どちらか一方では答えにならない**。
    ///
    @Test
    def testCreatesEstimateAndShowsCandidatesWithCost(): Unit \ Assert + IO =
        TestApp.withAppAndDb("estimate_happy_path", (p, ds) -> {
            VoyageFixtures.seedOsakaToLosAngeles(ds);
            let sales = loginAs(p, salesUser());
            let res = createEstimate(p, sales, validForm());
            // PRG。作成の応答で画面を返すと、再読み込みで二重に作られる
            assertStatus(302, res);
            let listed = body(getWith(p, "/estimates", sales));
            match estimateIdsIn(listed) {
                case Nil => Assert.fail("見積一覧に見積番号が出ていない")
                case estimateId :: _ =>
                    let detail = body(getWith(p, "/estimates/" + estimateId, sales));
                    // 受入基準 3: 経由港・所要日数・概算料金・航海番号
                    //
                    // **実額で突き合わせる**（IT8 レビュー・tester）。`contains("円")` では
                    // 運賃計算が壊れて 0 円になっても緑のままであり、
                    // 「電話口でいくらと答えるか」という US01 の核心を守れない。
                    // 直行便（V-9001S・1 区間）は基本料金 50,000 + 120 円/kg × 1,200 kg
                    // = 194,000 円（`FreightRateTest.testCalculatesKnownExample` と同じ条件）
                    Assert.assertTrue(String.contains(substr = "V-9001S", detail));
                    Assert.assertTrue(String.contains(substr = "194,000 円", detail));
                    // 直行は 2099-01-05 発 2099-01-20 着 = 15 日
                    Assert.assertTrue(String.contains(substr = "15 日", detail));
                    // 入力した条件が残ること（「あのとき何と言ったか」を確認できる）
                    Assert.assertTrue(String.contains(substr = "JPOSA", detail));
                    Assert.assertTrue(String.contains(substr = "USLAX", detail));
                    // **積替のある候補も出ること**。直行だけを返す実装だと、
                    // 「絞り込めた」と「たまたま 1 本だった」が区別できない
                    Assert.assertTrue(String.contains(substr = "JPTYO", detail))
            }
        })

    ///
    /// **期限に間に合う経路が無ければ、その旨が出ること**（受入基準 5）。
    ///
    /// 0 件の一覧を出して黙るのではない。**候補が無いと分かって初めて**、
    /// 営業は「その期限では無理です」と答えられる。
    ///
    @Test
    def testTellsWhenNoRouteMeetsTheDeadline(): Unit \ Assert + IO =
        TestApp.withAppAndDb("estimate_no_route", (p, ds) -> {
            // **便はある**。それでも仕向地が違えば候補は出ない。
            // 便が 1 本も無い状態で確かめると、「探索が動いていない」場合も
            // 同じ結果になり、区別が付かない
            VoyageFixtures.seedOsakaToLosAngeles(ds);
            let sales = loginAs(p, salesUser());
            // 到達できない仕向地の組み合わせ（航海が登録されていない）
            let res = createEstimate(p, sales,
                "origin=JPOSA&destination=SGSIN&arrivalDeadline=2099-12-31" +
                "&cargoType=GENERAL&weight=1200");
            assertStatus(302, res);
            let listed = body(getWith(p, "/estimates", sales));
            match estimateIdsIn(listed) {
                case Nil => Assert.fail("候補が無くても見積そのものは残る必要がある")
                case estimateId :: _ =>
                    let detail = body(getWith(p, "/estimates/" + estimateId, sales));
                    Assert.assertTrue(String.contains(substr = "間に合う経路", detail));
                    // **金額を出さないこと**（計画の壊れ方の観点・軸 3）。
                    // 0 円と書くと「無料」に読める。一覧の最安列も同じ
                    Assert.assertTrue(not String.contains(substr = "0 円", detail));
                    Assert.assertTrue(not String.contains(substr = "0 円", listed))
            }
        })

    ///
    /// **入力の誤りはフォームへ戻し、入力内容を保持すること**。
    ///
    /// 打ち直させると、電話口の営業は諦めて表計算に戻る。
    ///
    @Test
    def testKeepsInputWhenValidationFails(): Unit \ Assert + IO =
        withApp("estimate_invalid", p -> {
            let sales = loginAs(p, salesUser());
            let res = createEstimate(p, sales,
                "origin=JPOSA&destination=JPOSA&arrivalDeadline=2099-12-31" +
                "&cargoType=GENERAL&weight=1200");
            // 検証エラーは 200 でフォームを返す（PRG は成功時のみ）
            assertStatus(200, res);
            let html = body(res);
            Assert.assertTrue(String.contains(substr = "出発地と目的地", html))
        })

    ///
    /// **重量が正でなければ受け付けないこと**（ビジネスルール 3）。
    ///
    @Test
    def testRejectsNonPositiveWeight(): Unit \ Assert + IO =
        withApp("estimate_weight", p -> {
            let sales = loginAs(p, salesUser());
            let res = createEstimate(p, sales,
                "origin=JPOSA&destination=USLAX&arrivalDeadline=2099-12-31" +
                "&cargoType=GENERAL&weight=0");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "重量", body(res)))
        })

    // ---- 認可 ----

    ///
    /// **見積は営業担当者の画面であること**（IT8 計画のタスク 2.6）。
    ///
    /// 荷主に開かない。見積は社内の価格判断を含み、
    /// 荷主が自分で作れる情報ではない。ナビゲーションの表示ロールも
    /// これに合わせる（`NavigationReachabilityTest` が両者の一致を見る）。
    ///
    @Test
    def testRouterCannotOpenEstimates(): Unit \ Assert + IO =
        withApp("estimate_authz", p -> {
            let router = loginAs(p, routerUser());
            // **ログインが成立していることを先に示す**（IT8 レビュー・tester）。
            // 302 は「ログイン自体が失敗した」場合にも返るため、
            // それだけでは認可が効いている証明にならない
            assertStatus(200, getWith(p, "/voyages", router));
            Assert.assertEq(expected = 403, status(getWith(p, "/estimates", router)))
        })

    /// 未認証では見積画面に到達できないこと
    @Test
    def testRequiresLogin(): Unit \ Assert + IO =
        withApp("estimate_anonymous", p -> {
            assertStatus(302, SharedHttpTestClient.get(p, "/estimates"))
        })
}
