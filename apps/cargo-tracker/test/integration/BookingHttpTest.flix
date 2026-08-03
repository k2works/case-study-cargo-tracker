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
        let res = postWith(p, "/shippers",
            "name=山田太郎&email=" + email + "&shipperType=INDIVIDUAL&_csrf=" + token, cookie);
        // **失敗をここで止める**。捨てると、荷主登録が 403 や検証エラーで失敗しても
        // テストは進み、後続の `anyShipperCode` が「一覧に SHP- がない」という
        // 遠い場所で落ちる（Try T6）
        assertStatus(302, res)

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

    ///
    /// 出発地と目的地が同一なら登録できないこと。
    ///
    /// **実在する荷主コードで試す**。存在しない荷主コードを使うと ACL で先に弾かれ、
    /// テスト名に反して `OriginAndDestinationAreSame` が一度も通らない
    /// （IT4 のレビューで指摘された「緑だが守っていない」形）。
    ///
    @Test
    def testRejectsSameOriginAndDestination(): Unit \ Assert + IO =
        withApp("booking_same_port", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA" +
                "&destination=JPOSA&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "出発地と目的地が同じです", body(res)))
        })

    ///
    /// マスタにない港コードが入力エラーになること（500 にならない）。
    ///
    /// **壊れ方**: `origin_unlocode` は `location` への外部キーである。形式が正しくても
    /// マスタにないコード（`KRPUS` など）は保存時に外部キー違反となり、
    /// トランザクションは例外を捕捉しないため**応答が返らない**。
    /// 荷主コードは ACL で守っているのに港コードは素通り、という非対称があった。
    ///
    @Test
    def testRejectsUnavailableLocation(): Unit \ Assert + IO =
        withApp("booking_unknown_port", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=KRPUS&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "取り扱いできません", body(res)))
        })

    ///
    /// 実在しない日付が入力エラーになること（応答が返らない事態を防ぐ）。
    ///
    /// **壊れ方**: `2026-02-30` は形式検査（10 文字・4-2-2）を通り、
    /// `CAST(? AS DATE)` で DB の例外になる。利用者には 500 ですらなく
    /// 接続断が返っていた。
    ///
    @Test
    def testRejectsNonExistentDate(): Unit \ Assert + IO =
        withApp("booking_bad_date", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-02-30&cargoType=GENERAL&weight=1200");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "実在する日付", body(res)))
        })

    ///
    /// 未知の貨物種別が入力エラーになること。
    ///
    /// **壊れ方**: 当初は安全側のつもりで危険物へ倒していたが、
    /// 業務上は一般貨物が危険物として静かに登録される方が実害が大きい。
    /// htmx の断片ルートは任意のクエリ値を受け付けるため、
    /// 「画面の選択肢以外は届かない」という前提は成り立たない。
    ///
    @Test
    def testRejectsUnknownCargoType(): Unit \ Assert + IO =
        withApp("booking_bad_type", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=PERISHABLE&weight=1200");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "貨物種別を選択してください", body(res)))
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
            Assert.assertTrue(String.contains(substr = "重量は 0 より大きい数値（kg）", body(res)))
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

    // ---- US05: 危険物・冷凍貨物 ----

    ///
    /// 危険物を選ぶと申告の入力欄が出ること（受入基準 1）。
    ///
    @Test
    def testShowsHazardousFieldsForHazardousCargo(): Unit \ Assert + IO =
        withApp("booking_hazardous_fields", p -> {
            let cookie = login(p);
            let html = body(getWith(p, "/bookings/new/cargo-type-fields?cargoType=HAZARDOUS", cookie));
            Assert.assertTrue(String.contains(substr = "name=\"hazardousClass\"", html));
            Assert.assertTrue(String.contains(substr = "name=\"unNumber\"", html));
            Assert.assertTrue(String.contains(substr = "name=\"properShippingName\"", html));
            // 温度の欄は出ないこと（種別に対応しない欄を出さない）
            Assert.assertTrue(not String.contains(substr = "name=\"minTemperature\"", html))
        })

    /// 冷凍・冷蔵を選ぶと温度条件の入力欄が出ること（受入基準 2）
    @Test
    def testShowsTemperatureFieldsForRefrigeratedCargo(): Unit \ Assert + IO =
        withApp("booking_refrigerated_fields", p -> {
            let cookie = login(p);
            let html = body(getWith(p, "/bookings/new/cargo-type-fields?cargoType=REFRIGERATED", cookie));
            Assert.assertTrue(String.contains(substr = "name=\"minTemperature\"", html));
            Assert.assertTrue(String.contains(substr = "name=\"maxTemperature\"", html));
            Assert.assertTrue(String.contains(substr = "name=\"temperatureUnit\"", html));
            Assert.assertTrue(not String.contains(substr = "name=\"hazardousClass\"", html))
        })

    /// 一般貨物では追加の欄が出ないこと
    @Test
    def testShowsNoExtraFieldsForGeneralCargo(): Unit \ Assert + IO =
        withApp("booking_general_fields", p -> {
            let cookie = login(p);
            let html = body(getWith(p, "/bookings/new/cargo-type-fields?cargoType=GENERAL", cookie));
            Assert.assertTrue(not String.contains(substr = "name=\"hazardousClass\"", html));
            Assert.assertTrue(not String.contains(substr = "name=\"minTemperature\"", html))
        })

    /// 危険物を申告付きで登録できること
    @Test
    def testBooksHazardousCargo(): Unit \ Assert + IO =
        withApp("booking_hazardous", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=HAZARDOUS&weight=1200" +
                "&hazardousClass=3&unNumber=UN1203&properShippingName=" +
                "%E3%82%AC%E3%82%BD%E3%83%AA%E3%83%B3");
            assertStatus(302, res);
            let listed = body(getWith(p, "/bookings", cookie));
            Assert.assertTrue(String.contains(substr = "危険物", listed))
        })

    /// 冷凍貨物を温度条件付きで登録できること
    @Test
    def testBooksRefrigeratedCargo(): Unit \ Assert + IO =
        withApp("booking_refrigerated", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=REFRIGERATED&weight=1200" +
                "&minTemperature=-25.5&maxTemperature=-18&temperatureUnit=CELSIUS");
            assertStatus(302, res);
            let listed = body(getWith(p, "/bookings", cookie));
            Assert.assertTrue(String.contains(substr = "冷凍", listed))
        })

    ///
    /// 危険物で申告がないと登録できず、**エラーが画面に出る**こと（受入基準 1）。
    ///
    /// IT4 では貨物種別の選択欄でエラーが画面に出なかった（レビュー H14）。
    /// 「ドメインが弾く」だけでは、利用者から見て何も起きていないのと同じ。
    ///
    @Test
    def testShowsErrorWhenHazardousDeclarationIsMissing(): Unit \ Assert + IO =
        withApp("booking_hazardous_missing", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=HAZARDOUS&weight=1200");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "危険物申告", body(res)))
        })

    /// 冷凍貨物で温度条件がないと登録できず、エラーが画面に出ること（受入基準 2）
    @Test
    def testShowsErrorWhenTemperatureIsMissing(): Unit \ Assert + IO =
        withApp("booking_temperature_missing", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=REFRIGERATED&weight=1200");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "温度管理条件", body(res)))
        })

    /// UN 番号の形式違反が画面に出ること
    @Test
    def testShowsErrorForMalformedUnNumber(): Unit \ Assert + IO =
        withApp("booking_bad_un", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=HAZARDOUS&weight=1200" +
                "&hazardousClass=3&unNumber=1203&properShippingName=x");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "UN 番号", body(res)))
        })

    ///
    /// 温度が解釈できないとき、**エラーが画面に出る**こと。
    ///
    /// 単位付き（`-25℃`）・全角は利用者が実際に打ちうる形である。
    ///
    @Test
    def testShowsErrorForMalformedTemperature(): Unit \ Assert + IO =
        withApp("booking_bad_temp", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=REFRIGERATED&weight=1200" +
                "&minTemperature=-25%E2%84%83&maxTemperature=-18&temperatureUnit=CELSIUS");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "温度", body(res)))
        })

    /// 上下限が逆転しているとエラーが画面に出ること
    @Test
    def testShowsErrorForInvertedTemperatureRange(): Unit \ Assert + IO =
        withApp("booking_inverted_temp", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=REFRIGERATED&weight=1200" +
                "&minTemperature=-18&maxTemperature=-25&temperatureUnit=CELSIUS");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "最低温度", body(res)))
        })

    ///
    /// 種別に対応しない入力を**黙って捨てない**こと。
    ///
    /// 一般貨物に危険物申告を送ると、拒否されるべきである。捨てて成功させると、
    /// 利用者は申告したつもりで一般貨物として輸送される。
    ///
    @Test
    def testRejectsHazardousDeclarationOnGeneralCargo(): Unit \ Assert + IO =
        withApp("booking_hazard_on_general", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200" +
                "&hazardousClass=3&unNumber=UN1203&properShippingName=x");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "危険物申告", body(res)))
        })

    ///
    /// 入力エラーのときも、入力した特別要件が**保持される**こと。
    ///
    /// 打ち直しを強いると、UN 番号や品名を入れ直すことになる。
    ///
    @Test
    def testKeepsSpecialRequirementInputOnError(): Unit \ Assert + IO =
        withApp("booking_keeps_special_input", p -> {
            let cookie = login(p);
            let res = book(p, cookie,
                "shipperCode=SHP-UNKNOWN0&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=HAZARDOUS&weight=1200" +
                "&hazardousClass=3&unNumber=UN1203&properShippingName=x");
            assertStatus(200, res);
            let html = body(res);
            Assert.assertTrue(String.contains(substr = "value=\"UN1203\"", html));
            Assert.assertTrue(String.contains(substr = "value=\"3\"", html))
        })

    // ---- 寸法の入力（IT5 TS09・US04-2 の未充足分） ----

    ///
    /// 予約フォームに寸法を入力でき、保存されること。
    ///
    /// ドメインも DB 列も IT4 で用意されていたが、**フォームに欄がなく
    /// 常に未入力で保存されていた**（レビュー M8）。受入基準 US04-2 は
    /// 「貨物仕様（重量・寸法・個数）を入力できる」であり、未充足だった。
    ///
    @Test
    def testBooksCargoWithDimensions(): Unit \ Assert + IO =
        withApp("booking_dimensions", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200" +
                "&dimensionLength=120&dimensionWidth=80&dimensionHeight=100");
            assertStatus(302, res);
            let listed = body(getWith(p, "/bookings", cookie));
            Assert.assertTrue(String.contains(substr = "BK-", listed))
        })

    /// 寸法の入力欄があること
    @Test
    def testFormHasDimensionFields(): Unit \ Assert + IO =
        withApp("booking_dimension_fields", p -> {
            let cookie = login(p);
            let html = body(getWith(p, "/bookings/new", cookie));
            Assert.assertTrue(String.contains(substr = "name=\"dimensionLength\"", html));
            Assert.assertTrue(String.contains(substr = "name=\"dimensionWidth\"", html));
            Assert.assertTrue(String.contains(substr = "name=\"dimensionHeight\"", html))
        })

    ///
    /// 寸法は任意であり、未入力でも予約できること。
    ///
    /// 必須にすると、寸法が未確定の段階で仮受付できなくなる。
    ///
    @Test
    def testDimensionsAreOptional(): Unit \ Assert + IO =
        withApp("booking_dimension_optional", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            assertStatus(302, book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200" +
                "&dimensionLength=&dimensionWidth=&dimensionHeight="))
        })

    ///
    /// 寸法が 0 以下では登録できず、**エラーが画面に出る**こと。
    ///
    /// IT4 は貨物種別の選択欄でエラーが画面に出なかった（レビュー H14）。
    /// 「ドメインが弾く」だけでは、利用者から見て何も起きていないのと同じ。
    ///
    @Test
    def testRejectsNonPositiveDimension(): Unit \ Assert + IO =
        withApp("booking_dimension_zero", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200" +
                "&dimensionLength=0&dimensionWidth=80&dimensionHeight=100");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "寸法", body(res)))
        })

    ///
    /// 寸法を一部だけ入力した場合に、**黙って捨てない**こと。
    ///
    /// 3 つ揃って初めて寸法になる。1 つだけ入れて通ると、利用者は
    /// 入力したはずの値が保存されていないことに気付けない。
    ///
    @Test
    def testRejectsPartialDimensions(): Unit \ Assert + IO =
        withApp("booking_dimension_partial", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200" +
                "&dimensionLength=120&dimensionWidth=&dimensionHeight=");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "寸法", body(res)))
        })

    ///
    /// 全角数字を寸法として受け付けないこと（軸 1-5）。
    ///
    /// IT4 で `Char.isDigit` が全角数字を通した経緯がある。
    ///
    @Test
    def testRejectsFullWidthDimension(): Unit \ Assert + IO =
        withApp("booking_dimension_fullwidth", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200" +
                "&dimensionLength=１２０&dimensionWidth=80&dimensionHeight=100");
            assertStatus(200, res);
            Assert.assertTrue(String.contains(substr = "寸法", body(res)))
        })

    // ---- 発行された予約番号を確認できること（IT5 TS09） ----

    ///
    /// 予約に成功したら、**遷移先に発行された予約番号が出る**こと。
    ///
    /// IT4 では一覧に `BK-` の行が増えるだけで、どれが今登録したものかを
    /// 利用者が特定できなかった（US04-4 の未充足）。
    ///
    @Test
    def testShowsIssuedBookingIdAfterBooking(): Unit \ Assert + IO =
        withApp("booking_flash_id", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            assertStatus(302, res);
            let flash = flashCookieOf(res);
            let listed = body(getWith(p, "/bookings", cookie + "; " + flash));
            Assert.assertTrue(String.contains(substr = "予約しました", listed));
            Assert.assertTrue(String.contains(substr = "BK-", listed))
        })

    /// 通知は 1 回だけ表示されること
    @Test
    def testBookingFlashIsShownOnlyOnce(): Unit \ Assert + IO =
        withApp("booking_flash_once", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let res = book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            let flash = flashCookieOf(res);
            let first = getWith(p, "/bookings", cookie + "; " + flash);
            Assert.assertTrue(String.contains(substr = "予約しました", body(first)));
            let second = getWith(p, "/bookings", cookie);
            Assert.assertTrue(not String.contains(substr = "予約しました", body(second)))
        })

    ///
    /// **荷主の通知が予約一覧に出ない**こと。
    ///
    /// 種別ごとに文言を分けている意味は、出す画面を間違えないことにもある。
    /// 荷主登録の通知が予約一覧に出ると、利用者は何が起きたのか分からなくなる。
    ///
    @Test
    def testShipperFlashDoesNotLeakIntoBookingList(): Unit \ Assert + IO =
        withApp("booking_flash_kind", p -> {
            let cookie = login(p);
            let listed = body(getWith(p, "/bookings",
                cookie + "; FLASH=shipperRegistered:SHP-1A2B3C4D"));
            Assert.assertTrue(not String.contains(substr = "荷主を登録しました", listed))
        })

    /// レスポンスの `Set-Cookie` から通知 Cookie を取り出す
    def flashCookieOf(res: TestResponse): String \ Assert =
        match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None    => { Assert.fail("予約の応答に通知 Cookie が付いていない"); "" }
            case Some(v) => match String.indexOf(substr = ";", v) {
                case None    => v
                case Some(i) => String.slice(start = 0, end = i, v)
            }
        }
}
