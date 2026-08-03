///
/// 貨物予約の受入テスト（US04）。
///
/// **2 つのコンテキストをまたぐ経路**を端まで通す。荷主を登録してから予約するため、
/// Shipper Context と Booking Context の連携（ACL 経由）が実際に成立していることを見る。
///
mod BookingHttpTest {
    use SharedHttpTestClient.{get, status, body, assertStatus, TestResponse}
    use TestApp.withApp

    def salesUser(): String = "yamada"
    def password(): String = "correct horse battery staple"

    def login(p: Int32): String \ Assert + IO =
        let res = SharedHttpTestClient.post(p, "/login",
            "username=" + salesUser() + "&password=" +
            String.replace(src = " ", dst = "+", password()));
        match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None    => { Assert.fail("ログインで Set-Cookie が得られなかった"); "" }
            case Some(v) => match String.indexOf(substr = ";", v) {
                case None    => v
                case Some(i) => String.slice(start = 0, end = i, v)
            }
        }

    def getWith(p: Int32, path: String, cookie: String): TestResponse \ IO =
        SharedHttpTestClient.getWith(p, path, Map#{"Cookie" => cookie})

    def postWith(p: Int32, path: String, formBody: String, cookie: String): TestResponse \ IO =
        SharedHttpTestClient.postWith(p, path, formBody, Map#{"Cookie" => cookie})

    /// 指定ページのフォームから CSRF トークンを取り出す
    def csrfTokenOf(p: Int32, path: String, cookie: String): String \ Assert + IO =
        let html = body(getWith(p, path, cookie));
        let marker = "name=\"_csrf\" value=\"";
        match String.indexOf(substr = marker, html) {
            case None => { Assert.fail("${path} に CSRF トークンが見つからなかった"); "" }
            case Some(i) =>
                let start = i + String.length(marker);
                String.slice(start = start, end = start + 64, html)
        }

    /// 荷主を登録し、その荷主 ID を一覧の荷主コードから逆引きせずに使うため、
    /// 登録済みの荷主 ID を DB から得る代わりに ACL 経由で確かめられる形にする
    def registerShipper(p: Int32, cookie: String, email: String): Unit \ Assert + IO =
        let token = csrfTokenOf(p, "/shippers/new", cookie);
        discard postWith(p, "/shippers",
            "name=山田太郎&email=" + email + "&shipperType=INDIVIDUAL&_csrf=" + token, cookie)

    ///
    /// 荷主一覧から荷主コードを取り出す。
    ///
    /// **利用者と同じ経路で識別子を得る**。一覧に出ている値だけで予約が完了できることを、
    /// テストの手順そのもので確かめる。
    ///
    def anyShipperCode(p: Int32, cookie: String): String \ Assert + IO =
        let html = body(getWith(p, "/shippers", cookie));
        match String.indexOf(substr = "SHP-", html) {
            case None    => { Assert.fail("荷主一覧に荷主コードが見つからなかった"); "" }
            case Some(i) => String.slice(start = i, end = i + 12, html)
        }

    def book(p: Int32, cookie: String, formBody: String): TestResponse \ Assert + IO =
        let token = csrfTokenOf(p, "/bookings/new", cookie);
        postWith(p, "/bookings", formBody + "&_csrf=" + token, cookie)

    // ---- US04 正常系: 2 つのコンテキストをまたぐ経路 ----

    ///
    /// 荷主を登録し、その荷主コードで予約できること。
    ///
    /// **利用者と同じ経路で識別子を得る**。荷主一覧に出ている値だけで予約が完了
    /// できなければ、機能があっても使えない。IT4 の受入テストを書く過程で、
    /// 予約フォームが内部識別子（UUID）を求めており**画面上どこにも出ていない**
    /// ことに気付き、荷主コードを受け取る形へ是正した。
    ///
    @Test
    def testBooksCargoUsingShipperCodeFromList(): Unit \ Assert + IO =
        withApp("booking_happy_path", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            assertStatus(302, res);
            Assert.assertEq(expected = Some("/bookings"),
                SharedHttpTestClient.header(res, "Location"));
            let listed = body(getWith(p, "/bookings", cookie));
            Assert.assertTrue(String.contains(substr = "BK-", listed));
            Assert.assertTrue(String.contains(substr = "JPOSA", listed));
            Assert.assertTrue(String.contains(substr = "USLAX", listed));
            // 状態が業務の言葉で出ること（列挙値を画面に出さない）
            Assert.assertTrue(String.contains(substr = "仮受付", listed));
            Assert.assertTrue(not String.contains(substr = "PRELIMINARY", listed));
            // 重量がキログラム表記であること
            Assert.assertTrue(String.contains(substr = "1200 kg", listed))
        })

    /// 小数を含む重量が桁落ちせずに往復すること
    @Test
    def testBooksCargoWithFractionalWeight(): Unit \ Assert + IO =
        withApp("booking_fraction_weight", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            discard book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=12.5");
            Assert.assertTrue(String.contains(substr = "12.500 kg",
                body(getWith(p, "/bookings", cookie))))
        })

    // ---- 認可 ----

    /// 未認証では予約画面に到達できないこと
    @Test
    def testRequiresLogin(): Unit \ Assert + IO =
        withApp("booking_auth", p -> {
            assertStatus(302, get(p, "/bookings"));
            assertStatus(302, get(p, "/bookings/new"))
        })

    // ---- US04 受入条件 7: 存在しない荷主 ----

    ///
    /// 存在しない荷主 ID では入力エラーになること（500 にならない）。
    ///
    /// **壊れ方**: Booking と Shipper の間に DB 外部キーはない。ACL で確認しなければ
    /// 存在しない荷主に紐づく貨物が生まれる。外部キーを張っていたとしても、
    /// 利用者に見えるのは 500 である。
    ///
    @Test
    def testRejectsUnknownShipperWithInputError(): Unit \ Assert + IO =
        withApp("booking_unknown_shipper", p -> {
            let cookie = login(p);
            let res = book(p, cookie,
                "shipperCode=SHP-99999999&origin=JPOSA" +
                "&destination=USLAX&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "この荷主コードは登録されていません", body(res)));
            Assert.assertTrue(String.contains(substr = "登録されている貨物予約はありません",
                body(getWith(p, "/bookings", cookie))))
        })

    // ---- 入力の検証 ----

    /// 出発地と目的地が同一なら登録できないこと
    @Test
    def testRejectsSameOriginAndDestination(): Unit \ Assert + IO =
        withApp("booking_same_port", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let res = book(p, cookie,
                "shipperCode=SHP-99999999&origin=JPOSA" +
                "&destination=JPOSA&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            assertStatus(200, res);
            // 荷主の確認が先に走るため、まず荷主エラーが返る（順序の確認）
            Assert.assertTrue(String.contains(substr = "この荷主コードは登録されていません", body(res)))
        })

    ///
    /// 重量に数値でない値を入れたら弾かれること。
    ///
    /// **壊れ方**: 0 とみなすと、打ち間違いが「重量 0 の予約」としてドメインの検証に回り、
    /// 利用者には何を直せばよいか伝わらない。
    ///
    @Test
    def testRejectsNonNumericWeight(): Unit \ Assert + IO =
        withApp("booking_weight_text", p -> {
            let cookie = login(p);
            let res = book(p, cookie,
                "shipperCode=SHP-XXXXXXXX&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=千二百");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "重量を数値（kg）で入力してください", body(res)))
        })

    /// CSRF トークンなしの登録が拒否されること
    @Test
    def testRejectsWithoutCsrfToken(): Unit \ Assert + IO =
        withApp("booking_csrf", p -> {
            let cookie = login(p);
            let res = postWith(p, "/bookings",
                "shipperCode=SHP-XXXXXXXX&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200", cookie);
            assertStatus(403, res)
        })

    // ---- 画面 ----

    /// 予約登録フォームに必要な入力欄があること
    @Test
    def testFormHasRequiredFields(): Unit \ Assert + IO =
        withApp("booking_form", p -> {
            let cookie = login(p);
            let html = body(getWith(p, "/bookings/new", cookie));
            let fields = "shipperCode" :: "origin" :: "destination" ::
                         "arrivalDeadline" :: "cargoType" :: "weight" :: Nil;
            Assert.assertTrue(fields |> List.forAll(f ->
                String.contains(substr = "name=\"${f}\"", html)))
        })

    ///
    /// 荷主が未登録のときの導線があること（US04 受入条件 8・Try T4）。
    ///
    /// 予約しようとして荷主がいないと分かった利用者は、そこで手が止まる。
    ///
    @Test
    def testOffersShipperRegistrationLink(): Unit \ Assert + IO =
        withApp("booking_shipper_link", p -> {
            let cookie = login(p);
            let html = body(getWith(p, "/bookings/new", cookie));
            Assert.assertTrue(String.contains(substr = "href=\"/shippers/new\"", html));
            Assert.assertTrue(String.contains(substr = "この画面に戻って予約", html))
        })

    /// 0 件のときに案内が出ること
    @Test
    def testEmptyListShowsGuidance(): Unit \ Assert + IO =
        withApp("booking_empty", p -> {
            let cookie = login(p);
            Assert.assertTrue(String.contains(substr = "登録されている貨物予約はありません",
                body(getWith(p, "/bookings", cookie))))
        })

    /// 貨物種別の選択肢が業務の言葉で出ること
    @Test
    def testCargoTypeOptionsUseBusinessLanguage(): Unit \ Assert + IO =
        withApp("booking_type_labels", p -> {
            let cookie = login(p);
            let html = body(getWith(p, "/bookings/new", cookie));
            Assert.assertTrue(String.contains(substr = "一般貨物", html));
            Assert.assertTrue(String.contains(substr = "危険物", html));
            Assert.assertTrue(String.contains(substr = "冷凍・冷蔵貨物", html))
        })

    ///
    /// US05 の拡張点（貨物種別に応じた追加入力欄）が用意されていること。
    ///
    /// IT4 では中身が空である。**差し替え先が存在すること**を固定しておけば、
    /// IT5 は「中身を作る」だけで済み、フォーム全体を作り直さずに済む。
    ///
    @Test
    def testCargoTypeExtensionPointExists(): Unit \ Assert + IO =
        withApp("booking_extension_point", p -> {
            let cookie = login(p);
            let html = body(getWith(p, "/bookings/new", cookie));
            Assert.assertTrue(String.contains(substr = "id=\"cargo-type-fields\"", html));
            Assert.assertTrue(String.contains(
                substr = "hx-get=\"/bookings/new/cargo-type-fields\"", html));
            // 断片のルートも応答すること
            assertStatus(200, getWith(p, "/bookings/new/cargo-type-fields?cargoType=HAZARDOUS", cookie))
        })

    /// 荷主が navbar・ダッシュボードから予約へ到達できること
    @Test
    def testReachableFromNavigation(): Unit \ Assert + IO =
        withApp("booking_nav", p -> {
            let cookie = login(p);
            Assert.assertTrue(String.contains(substr = "href=\"/bookings\"",
                body(getWith(p, "/", cookie))))
        })
}
