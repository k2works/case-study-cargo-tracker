///
/// 荷主登録の受入テスト（US02・US03）。
///
/// HTTP リクエストからデータベースまでを縦断する。**書き込み経路を端まで通す
/// 本プロジェクト初の受入テスト**であり、PRG・CSRF・認可・重複導線を通しで見る。
///
mod ShipperHttpTest {
    use SharedHttpTestClient.{get, post, status, body, assertStatus, TestResponse}
    use TestApp.withApp

    def salesUser(): String = "yamada"
    def password(): String = "correct horse battery staple"

    // ---- ヘルパー ----

    def login(p: Int32, name: String): String \ Assert + IO =
        let res = SharedHttpTestClient.post(p, "/login",
            "username=" + name + "&password=" + String.replace(src = " ", dst = "+", password()));
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

    /// 登録フォームから CSRF トークンを取り出す
    def csrfTokenOf(p: Int32, cookie: String): String \ Assert + IO =
        let html = body(getWith(p, "/shippers/new", cookie));
        let marker = "name=\"_csrf\" value=\"";
        match String.indexOf(substr = marker, html) {
            case None => { Assert.fail("荷主登録フォームに CSRF トークンが見つからなかった"); "" }
            case Some(i) =>
                let start = i + String.length(marker);
                String.slice(start = start, end = start + 64, html)
        }

    /// 荷主を登録する
    def register(p: Int32, cookie: String, formBody: String): TestResponse \ Assert + IO =
        let token = csrfTokenOf(p, cookie);
        postWith(p, "/shippers", formBody + "&_csrf=" + token, cookie)

    // ---- 認可（ナビゲーション整合の前提） ----

    /// 未認証では荷主画面に到達できないこと
    @Test
    def testRequiresLogin(): Unit \ Assert + IO =
        withApp("shipper_auth", p -> {
            assertStatus(302, get(p, "/shippers"));
            assertStatus(302, get(p, "/shippers/new"))
        })

    ///
    /// 営業担当者以外は荷主画面に到達できないこと。
    ///
    /// 荷主の登録・管理は営業担当者の職掌である（ロール表）。
    ///
    @Test
    def testForbidsNonSalesRole(): Unit \ Assert + IO =
        withApp("shipper_role", p -> {
            // `suzuki` は営業担当者。荷役作業員（taro）は無効化されているため、
            // ここでは営業担当者が通ることを確かめる（拒否は AuthorizationTest が網羅する）
            let cookie = login(p, AuthFixtures.secondUsername());
            assertStatus(200, getWith(p, "/shippers", cookie))
        })

    // ---- US02: 個人荷主の登録 ----

    ///
    /// 個人荷主を登録すると一覧に現れること。
    ///
    /// 登録は PRG で `/shippers` へリダイレクトする。結果をそのまま描画すると、
    /// 再読み込みでフォームが再送信される。
    ///
    @Test
    def testRegistersIndividualShipper(): Unit \ Assert + IO =
        withApp("shipper_register", p -> {
            let cookie = login(p, salesUser());
            let res = register(p, cookie,
                "name=山田太郎&email=yamada@example.com&phone=03-1234-5678" +
                "&address=東京都千代田区&shipperType=INDIVIDUAL");
            assertStatus(302, res);
            Assert.assertEq(expected = Some("/shippers"),
                SharedHttpTestClient.header(res, "Location"));
            let listed = body(getWith(p, "/shippers", cookie));
            Assert.assertTrue(String.contains(substr = "山田太郎", listed));
            Assert.assertTrue(String.contains(substr = "yamada@example.com", listed));
            Assert.assertTrue(String.contains(substr = "SHP-", listed))
        })

    /// 入力エラーでは登録されず、同じ画面に入力値とメッセージが残ること
    @Test
    def testShowsValidationErrorWithoutSaving(): Unit \ Assert + IO =
        withApp("shipper_invalid", p -> {
            let cookie = login(p, salesUser());
            let res = register(p, cookie,
                "name=山田太郎&email=not-an-email&shipperType=INDIVIDUAL");
            assertStatus(200, res);
            let html = body(res);
            Assert.assertTrue(String.contains(substr = "メールアドレスの形式", html));
            Assert.assertTrue(String.contains(substr = "value=\"山田太郎\"", html));
            // 一覧に現れないこと
            Assert.assertTrue(String.contains(substr = "登録されている荷主はありません",
                body(getWith(p, "/shippers", cookie))))
        })

    ///
    /// CSRF トークンなしの登録が拒否されること。
    ///
    /// **壊れ方**: ここが破れると、第三者のサイトから荷主を勝手に登録できる。
    ///
    @Test
    def testRejectsRegistrationWithoutCsrfToken(): Unit \ Assert + IO =
        withApp("shipper_csrf", p -> {
            let cookie = login(p, salesUser());
            let res = postWith(p, "/shippers",
                "name=山田太郎&email=yamada@example.com&shipperType=INDIVIDUAL", cookie);
            assertStatus(403, res);
            Assert.assertTrue(String.contains(substr = "登録されている荷主はありません",
                body(getWith(p, "/shippers", cookie))))
        })

    // ---- US02 受入基準 2: メール重複（Try T4） ----

    ///
    /// 同一メールでの登録が、既存の荷主と 2 つの導線を示すこと。
    ///
    /// **重複が 500 にならないこと**（US02 受入条件 5 の壊れ方）もここで確かめる。
    ///
    @Test
    def testDuplicateEmailShowsChoices(): Unit \ Assert + IO =
        withApp("shipper_duplicate", p -> {
            let cookie = login(p, salesUser());
            discard register(p, cookie,
                "name=既存 花子&email=yamada@example.com&shipperType=INDIVIDUAL");
            let res = register(p, cookie,
                "name=山田太郎&email=yamada@example.com&shipperType=INDIVIDUAL");
            assertStatus(200, res);
            let html = body(res);
            Assert.assertTrue(String.contains(substr = "既に登録されています", html));
            Assert.assertTrue(String.contains(substr = "既存の荷主を使う", html));
            Assert.assertTrue(String.contains(substr = "入力を修正する", html));
            Assert.assertTrue(String.contains(substr = "既存 花子", html))
        })

    /// 大文字小文字違いのメールでも重複と判定されること
    @Test
    def testDuplicateDetectionIsCaseInsensitive(): Unit \ Assert + IO =
        withApp("shipper_duplicate_case", p -> {
            let cookie = login(p, salesUser());
            discard register(p, cookie,
                "name=既存 花子&email=yamada@example.com&shipperType=INDIVIDUAL");
            let res = register(p, cookie,
                "name=山田太郎&email=YAMADA@EXAMPLE.COM&shipperType=INDIVIDUAL");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "既に登録されています", body(res)))
        })

    // ---- US03: 法人荷主 ----

    /// 法人荷主を契約番号・割引率とともに登録できること
    @Test
    def testRegistersCorporateShipper(): Unit \ Assert + IO =
        withApp("shipper_corporate", p -> {
            let cookie = login(p, salesUser());
            let res = register(p, cookie,
                "name=株式会社サンプル&email=sales@example.com&shipperType=CORPORATE" +
                "&contractNumber=CT-0001&discountRate=15");
            assertStatus(302, res);
            let listed = body(getWith(p, "/shippers", cookie));
            Assert.assertTrue(String.contains(substr = "株式会社サンプル", listed));
            Assert.assertTrue(String.contains(substr = "法人", listed));
            Assert.assertTrue(String.contains(substr = "15.00%", listed))
        })

    /// 割引率が 30% を超えると登録できないこと
    @Test
    def testRejectsDiscountRateOverLimit(): Unit \ Assert + IO =
        withApp("shipper_rate_limit", p -> {
            let cookie = login(p, salesUser());
            let res = register(p, cookie,
                "name=株式会社サンプル&email=sales@example.com&shipperType=CORPORATE" +
                "&contractNumber=CT-0001&discountRate=31");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "0 〜 30 の範囲", body(res)))
        })

    ///
    /// 割引率に数値でない値を入れたら弾かれること。
    ///
    /// **壊れ方**: 解釈できない入力を 0 とみなすと、打ち間違いが
    /// 「割引なしの法人」として静かに通る。
    ///
    @Test
    def testRejectsNonNumericDiscountRate(): Unit \ Assert + IO =
        withApp("shipper_rate_text", p -> {
            let cookie = login(p, salesUser());
            let res = register(p, cookie,
                "name=株式会社サンプル&email=sales@example.com&shipperType=CORPORATE" +
                "&contractNumber=CT-0001&discountRate=十五");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "0 〜 30 の数値", body(res)))
        })

    /// 法人で契約番号が空なら弾かれること
    @Test
    def testRejectsCorporateWithoutContractNumber(): Unit \ Assert + IO =
        withApp("shipper_no_contract", p -> {
            let cookie = login(p, salesUser());
            let res = register(p, cookie,
                "name=株式会社サンプル&email=sales@example.com&shipperType=CORPORATE" +
                "&contractNumber=&discountRate=15");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "契約番号を入力してください", body(res)))
        })

    // ---- htmx 部分更新（US03 受入基準 1） ----

    /// 法人を選ぶと法人固有フィールドの断片が返ること
    @Test
    def testCorporateFieldsFragment(): Unit \ Assert + IO =
        withApp("shipper_htmx", p -> {
            let cookie = login(p, salesUser());
            let res = getWith(p, "/shippers/new/corporate-fields?shipperType=CORPORATE", cookie);
            assertStatus(200, res);
            let html = body(res);
            Assert.assertTrue(String.contains(substr = "name=\"contractNumber\"", html));
            // 断片であること（ページ全体を返していない）
            Assert.assertTrue(not String.contains(substr = "<html", html))
        })

    /// 個人を選ぶと法人固有フィールドが空になること
    @Test
    def testCorporateFieldsFragmentIsEmptyForIndividual(): Unit \ Assert + IO =
        withApp("shipper_htmx_individual", p -> {
            let cookie = login(p, salesUser());
            let res = getWith(p, "/shippers/new/corporate-fields?shipperType=INDIVIDUAL", cookie);
            assertStatus(200, res);
            Assert.assertTrue(not String.contains(substr = "contractNumber", body(res)))
        })

    // ---- ナビゲーション整合 ----

    ///
    /// 営業担当者が navbar から荷主一覧へ到達できること。
    ///
    /// 受入基準を満たしていても、**そのロールが画面に到達できなければ機能は無い**
    /// のと同じである（IT3 レビュー L7）。
    ///
    @Test
    def testSalesReachesShippersFromNavigation(): Unit \ Assert + IO =
        withApp("shipper_nav", p -> {
            let cookie = login(p, salesUser());
            let dashboard = body(getWith(p, "/", cookie));
            Assert.assertTrue(String.contains(substr = "href=\"/shippers\"", dashboard))
        })
}
