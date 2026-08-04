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
        // **失敗時に証拠を残す**（IT8 レビュー・tester。Try T6）。
        // Set-Cookie が無いという事実だけでは、認証に失敗したのか
        // 別インスタンスが応答したのかを区別できない。`assertStatus` は
        // ステータス・処理したルート・**インスタンス識別子**・本文を出す
        assertStatus(302, res);
        match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None    => { Assert.fail("ログインで Set-Cookie が得られなかった"); "" }
            case Some(v) => match String.indexOf(substr = ";", v) {
                case None    => v
                case Some(i) => String.slice(start = 0, end = i, v)
            }
        }

    /// 指定した利用者でログインする
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
    ///
    /// 荷主の氏名（IT8・TS14。IT6 レビュー M10）。
    ///
    /// **メールアドレスから一意に決める**。IT6 の実装は全員を「山田太郎」に
    /// していたため、一覧の**行と荷主の対応**を検証できなかった——
    /// 荷主名の列を全行入れ替えても緑のままである。
    ///
    /// 名前を変えると `yamada@example.com` の荷主名は「山田太郎」のままに
    /// なる（既存のアサーションを壊さない）。**フィクスチャの識別力を上げる
    /// ためだけに既存テストの期待値を書き換えない**。
    ///
    def nameFor(email: String): String = match email {
        case "yamada@example.com" => "山田太郎"
        // **同じ部分文字列に当たる別の荷主**。`IN` 句の要素が 2 以上になる
        case "yamada2@example.com" => "山田花子"
        case "kimura@example.com" => "木村花子"
        case "tanaka@example.com" => "田中一郎"
        case other                => "荷主 " + other
    }

    def registerShipper(p: Int32, cookie: String, email: String): Unit \ Assert + IO =
        let token = csrfTokenOf(p, "/shippers/new", cookie);
        let res = postWith(p, "/shippers",
            "name=" + nameFor(email) + "&email=" + email +
            "&shipperType=INDIVIDUAL&_csrf=" + token, cookie);
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
            // **要素の中身**として列挙値が出ていないこと。IT8 で状態の絞り込み欄を
            // 足したため、`value="PRELIMINARY"` は属性として正しく現れる
            // （URL と DB で状態の呼び名を 1 つに保つための語彙である）。
            // 素の `contains` に戻すと、この欄を足した瞬間に落ちる
            Assert.assertTrue(not String.contains(substr = ">PRELIMINARY<", listed));
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
            Assert.assertTrue(String.contains(substr = "12.5 kg",
                body(getWith(p, "/bookings", cookie))))
        })

    // ---- 一覧の絞り込み（IT8・TS14。IT7 M14 + IT6 M4） ----

    ///
    /// **状態と荷主名で絞れること**（実際の SQL を通す）。
    ///
    /// `CargoSearchTest` は同じ判断をインメモリのスタブで固定しているが、
    /// スタブと SQL が食い違えば単体で緑・本番で赤になる。
    /// **`IN` 句の組み立てとプレースホルダの並びは、SQL を実行しないと確かめられない**。
    ///
    /// 予約を 2 件作り、片方だけ経路設計へ引き渡して状態を分ける。
    /// **1 件だけの題材では「絞れた」と「たまたま全件だった」が区別できない**。
    ///
    @Test
    def testFiltersBookingListByStatusAndShipperName(): Unit \ Assert + IO =
        withApp("booking_list_filter", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            let form = "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                       "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200";
            assertStatus(302, book(p, cookie, form));
            assertStatus(302, book(p, cookie, form));
            let all = body(getWith(p, "/bookings", cookie));
            let ids = bookingIdsIn(all);
            Assert.assertEq(expected = 2, List.length(ids));

            // 片方を経路設計へ引き渡し、状態を分ける
            let target = List.head(ids) |> Option.getWithDefault("");
            let token = csrfTokenOf(p, "/bookings/" + target, cookie);
            assertStatus(302, postWith(p, "/bookings/" + target + "/assign-to-routing",
                                       "_csrf=" + token, cookie));

            // 状態で絞る
            Assert.assertEq(expected = target :: Nil,
                bookingIdsIn(body(getWith(p, "/bookings?status=ROUTE_PROPOSED", cookie))));
            Assert.assertEq(expected = 1,
                List.length(bookingIdsIn(body(getWith(p, "/bookings?status=PRELIMINARY", cookie)))));

            // 荷主名で絞る（登録した荷主名は「山田太郎」）
            Assert.assertEq(expected = 2,
                List.length(bookingIdsIn(body(getWith(p, "/bookings?shipper=山田", cookie)))));

            // **`IN` 句の要素が 2 以上の経路を通す**（IT8 レビュー・tester）。
            // 荷主 1 件では `IN (?)` しか実行されず、`selectMatching` が
            // 8 番目から順に詰める並びは一度も確かめられない。
            // 「山田」に当たる荷主をもう 1 件足し、両者の予約が返ることを見る
            registerShipper(p, cookie, "yamada2@example.com");
            let secondCode = shipperCodeFor(p, cookie, "yamada2@example.com");
            assertStatus(302, book(p, cookie,
                "shipperCode=" + secondCode + "&origin=JPTYO&destination=DEHAM" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=800"));
            Assert.assertEq(expected = 3,
                List.length(bookingIdsIn(body(getWith(p, "/bookings?shipper=山田", cookie)))));

            // **該当する荷主が無ければ 0 件**（「絞らない」ではない）
            Assert.assertEq(expected = Nil,
                bookingIdsIn(body(getWith(p, "/bookings?shipper=該当なし", cookie))));

            // 状態と荷主名は AND で重なる
            Assert.assertEq(expected = target :: Nil,
                bookingIdsIn(body(getWith(p,
                    "/bookings?shipper=山田&status=ROUTE_PROPOSED", cookie))))
        })

    ///
    /// **知らない状態語は 0 件にし、理由を画面が言うこと**（IT8 計画・軸 1）。
    ///
    /// 全件へ倒すと、利用者は絞ったつもりで別の状態の予約を対象に作業する。
    /// 0 件にするなら「予約が無い」ではなく「そんな状態は無い」と言う必要がある——
    /// **利用者が取る次の行動が違う**（登録を待つのか、綴りを直すのか）。
    ///
    @Test
    def testRejectsUnknownStatusInBookingList(): Unit \ Assert + IO =
        withApp("booking_list_unknown_status", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            assertStatus(302, book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200"));
            let listed = body(getWith(p, "/bookings?status=NO_SUCH", cookie));
            Assert.assertEq(expected = Nil, bookingIdsIn(listed));
            Assert.assertTrue(String.contains(substr = "という予約状態はありません", listed))
        })

    ///
    /// 一覧の HTML から予約番号を拾う。
    ///
    /// **`BK-` の出現数を数えない**。絞り込み欄の `placeholder` や通知など、
    /// 表以外の場所に現れうる文字列で数が狂う。**行の詳細リンクだけ**を見る。
    ///
    def bookingIdsIn(html: String): List[String] =
        collectBookingIds(html, Nil) |> List.reverse

    def collectBookingIds(rest: String, acc: List[String]): List[String] =
        let marker = "href=\"/bookings/BK-";
        match String.indexOf(substr = marker, rest) {
            case None    => acc
            case Some(i) =>
                let start = i + String.length("href=\"/bookings/");
                let id = String.slice(start = start, end = start + 11, rest);
                let tail = String.slice(start = start + 11, end = String.length(rest), rest);
                collectBookingIds(tail, id :: acc)
        }

    ///
    /// **一覧の行と荷主が正しく対応していること**（IT6 レビュー M10）。
    ///
    /// IT6 のフィクスチャは荷主名を全件同一にしていたため、
    /// **荷主名の列を全行入れ替えても緑のまま**だった。
    /// 予約番号だけでは「誰の貨物か」が分からず、荷主名を出したのは
    /// 「先週の件どうなってる？」の電話に応えるためである（IT5 レビュー M15）。
    /// 対応が狂っていれば、別の荷主の予約について話すことになる。
    ///
    /// この式が偽になる入力: 荷主名を予約と無関係に引く実装
    /// （たとえば一覧の先頭の荷主を全行に使う）。
    ///
    @Test
    def testEachRowShowsItsOwnShipper(): Unit \ Assert + IO =
        withApp("booking_row_shipper", p -> {
            let sales = login(p);
            let mine = registerShipperAndBook(p, sales, "kimura@example.com", "JPOSA", "USLAX");
            let theirs = registerShipperAndBook(p, sales, "tanaka@example.com", "JPTYO", "DEHAM");
            let listed = body(getWith(p, "/bookings", sales));
            // **行を切り出してから**荷主名を見る。ページ全体に対する
            // `contains` では、2 つの名前が「どこかにある」ことしか言えない
            Assert.assertTrue(String.contains(substr = "木村花子", rowOf(listed, mine)));
            Assert.assertTrue(not String.contains(substr = "田中一郎", rowOf(listed, mine)));
            Assert.assertTrue(String.contains(substr = "田中一郎", rowOf(listed, theirs)));
            Assert.assertTrue(not String.contains(substr = "木村花子", rowOf(listed, theirs)))
        })

    /// 予約番号を含む `<tr>` を切り出す（見つからなければ空文字）
    def rowOf(html: String, bookingId: String): String =
        match String.indexOf(substr = bookingId, html) {
            case None => ""
            case Some(i) =>
                let tail = String.slice(start = i, end = String.length(html), html);
                match String.indexOf(substr = "</tr>", tail) {
                    case None    => tail
                    case Some(j) => String.slice(start = 0, end = j, tail)
                }
        }

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

    // ---- US06: 予約詳細と経路設計への引き渡し ----

    ///
    /// 予約番号を指定して予約情報を確認できること（受入基準 1）。
    ///
    @Test
    def testShowsBookingDetail(): Unit \ Assert + IO =
        withApp("booking_detail", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            discard book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            let bookingId = anyBookingId(p, cookie);
            let detail = body(getWith(p, "/bookings/" + bookingId, cookie));
            Assert.assertTrue(String.contains(substr = bookingId, detail));
            Assert.assertTrue(String.contains(substr = "JPOSA", detail));
            Assert.assertTrue(String.contains(substr = "USLAX", detail));
            Assert.assertTrue(String.contains(substr = "2026-12-31", detail));
            // 状態は業務の言葉で出ること
            Assert.assertTrue(String.contains(substr = "仮受付", detail));
            Assert.assertTrue(not String.contains(substr = "PRELIMINARY", detail))
        })

    ///
    /// **予約詳細に荷主が出ること**（US06 受入基準 1）。
    ///
    /// 引き渡し前の確認は「誰の貨物か」から始まる。荷主が出ないと与信・契約の
    /// 判断も荷主への確認電話もできず、経路設計者からの問い合わせも増える。
    /// IT5 のレビューで利用者代表が「受入基準 1 の実質的な中身」と指摘した。
    ///
    @Test
    def testDetailShowsShipper(): Unit \ Assert + IO =
        withApp("booking_detail_shipper", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            discard book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            let detail = body(getWith(p, "/bookings/" + anyBookingId(p, cookie), cookie));
            Assert.assertTrue(String.contains(substr = code, detail));
            Assert.assertTrue(String.contains(substr = "山田太郎", detail))
        })

    // ---- 二重引き渡しの並行実行（IT6・IT5 レビュー M8・観点表の軸 4-3/4-4） ----

    ///
    /// 同じ予約への引き渡しを**同時に**投げても、成功は 1 本だけであること。
    ///
    /// `assign` は read-modify-write である。同時到達すると両方が「仮受付」を
    /// 読み、どちらも成功しうる。そうなると経路設計側に同じ依頼が二重に積まれる。
    ///
    /// IT5 の `testRejectsAssigningTwice` は 1 本目の応答を待ってから 2 本目を
    /// 送るため、**連打・二重送信という実際の壊れ方を通っていなかった**。
    ///
    @Test
    def testConcurrentAssignSucceedsOnlyOnce(): Unit \ Assert + IO + Chan + NonDet =
        withApp("booking_assign_concurrent", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            discard book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            let bookingId = anyBookingId(p, cookie);
            let token = csrfTokenOf(p, "/bookings/" + bookingId, cookie);
            let statuses = SharedHttpTestClient.postConcurrently(
                p, "/bookings/" + bookingId + "/assign-to-routing",
                "_csrf=" + token, cookie, 8);
            let succeeded = statuses |> List.count(s -> s == 302);
            if (succeeded == 1)
                ()
            else
                Assert.fail("同時に 8 本投げて成功が ${succeeded} 本でした（期待 1 本）。" +
                            "ステータス: ${statuses}");
            // 状態は 1 度だけ進んでいること
            let detail = body(getWith(p, "/bookings/" + bookingId, cookie));
            Assert.assertTrue(String.contains(substr = "経路設計中", detail))
        })

    // ---- 可視範囲（IT6 TS11・観点表の軸 6） ----

    ///
    /// 荷主ロールには**自分の予約だけ**が見えること（軸 6-1）。
    ///
    /// IT5 では一覧が全件を返しており、荷主が他社の予約番号・仕向地・品名を
    /// 読めた。絞れないため予約画面ごと隠す形で止めていた。
    ///
    @Test
    def testShipperSeesOnlyOwnBookings(): Unit \ Assert + IO =
        TestApp.withAppAndDb("booking_visible_own", (p, ds) -> {
            let sales = login(p);
            // 2 人の荷主とそれぞれの予約を作る
            let mine = registerShipperAndBook(p, sales, "kimura@example.com", "JPOSA", "USLAX");
            let theirs = registerShipperAndBook(p, sales, "tanaka@example.com", "JPTYO", "DEHAM");
            AuthFixtures.linkShipper(ds, AuthFixtures.shipperUsername(), shipperCodeFor(p, sales, "kimura@example.com"));
            let shipper = loginAs(p, AuthFixtures.shipperUsername());
            let listed = body(getWith(p, "/bookings", shipper));
            Assert.assertTrue(String.contains(substr = mine, listed));
            // **他人の予約が現れないこと**が本テストの主眼
            Assert.assertTrue(not String.contains(substr = theirs, listed))
        })

    ///
    /// 他人の予約の詳細は **404** であること（軸 6-2・軸 6-3）。
    ///
    /// 一覧を絞っても詳細が開けるなら、URL を直接叩けば読める。
    /// **403 を返すと「その予約は存在する」ことが分かる**ため 404 に統一する。
    ///
    @Test
    def testOtherShipperBookingIsNotFound(): Unit \ Assert + IO =
        TestApp.withAppAndDb("booking_visible_other", (p, ds) -> {
            let sales = login(p);
            discard registerShipperAndBook(p, sales, "kimura@example.com", "JPOSA", "USLAX");
            let theirs = registerShipperAndBook(p, sales, "tanaka@example.com", "JPTYO", "DEHAM");
            AuthFixtures.linkShipper(ds, AuthFixtures.shipperUsername(), shipperCodeFor(p, sales, "kimura@example.com"));
            let shipper = loginAs(p, AuthFixtures.shipperUsername());
            assertStatus(404, getWith(p, "/bookings/" + theirs, shipper))
        })

    /// 自分の予約の詳細は開けること
    @Test
    def testOwnBookingDetailIsVisible(): Unit \ Assert + IO =
        TestApp.withAppAndDb("booking_visible_own_detail", (p, ds) -> {
            let sales = login(p);
            let mine = registerShipperAndBook(p, sales, "kimura@example.com", "JPOSA", "USLAX");
            AuthFixtures.linkShipper(ds, AuthFixtures.shipperUsername(), shipperCodeFor(p, sales, "kimura@example.com"));
            let shipper = loginAs(p, AuthFixtures.shipperUsername());
            assertStatus(200, getWith(p, "/bookings/" + mine, shipper))
        })

    ///
    /// 荷主に紐づかない荷主ロールは **0 件**であること（軸 6-4 の裏）。
    ///
    /// 全件へ倒すと、紐付けを忘れた利用者に全社の予約が見える。
    ///
    @Test
    def testUnlinkedShipperSeesNothing(): Unit \ Assert + IO =
        withApp("booking_visible_unlinked", p -> {
            let sales = login(p);
            discard registerShipperAndBook(p, sales, "kimura@example.com", "JPOSA", "USLAX");
            let shipper = loginAs(p, AuthFixtures.shipperUsername());
            let listed = body(getWith(p, "/bookings", shipper));
            Assert.assertTrue(not String.contains(substr = "BK-", listed))
        })

    /// 営業担当者は全件見えること（軸 6-4）
    @Test
    def testSalesSeesAllBookings(): Unit \ Assert + IO =
        withApp("booking_visible_sales", p -> {
            let sales = login(p);
            let a = registerShipperAndBook(p, sales, "kimura@example.com", "JPOSA", "USLAX");
            let b = registerShipperAndBook(p, sales, "tanaka@example.com", "JPTYO", "DEHAM");
            let listed = body(getWith(p, "/bookings", sales));
            Assert.assertTrue(String.contains(substr = a, listed));
            Assert.assertTrue(String.contains(substr = b, listed))
        })

    // ---- 予約一覧の絞り込みと荷主名（IT6 TS11・IT5 レビュー M15） ----

    /// 予約番号・出発地・仕向地で絞り込めること
    @Test
    def testFiltersBookings(): Unit \ Assert + IO =
        withApp("booking_filter", p -> {
            let sales = login(p);
            let a = registerShipperAndBook(p, sales, "kimura@example.com", "JPOSA", "USLAX");
            let b = registerShipperAndBook(p, sales, "tanaka@example.com", "JPTYO", "DEHAM");
            let byOrigin = body(getWith(p, "/bookings?q=JPTYO", sales));
            Assert.assertTrue(String.contains(substr = b, byOrigin));
            Assert.assertTrue(not String.contains(substr = a, byOrigin));
            let byId = body(getWith(p, "/bookings?q=" + a, sales));
            Assert.assertTrue(String.contains(substr = a, byId));
            Assert.assertTrue(not String.contains(substr = b, byId))
        })

    /// 一覧に荷主名の列があること
    @Test
    def testListShowsShipperName(): Unit \ Assert + IO =
        withApp("booking_shipper_column", p -> {
            let sales = login(p);
            discard registerShipperAndBook(p, sales, "kimura@example.com", "JPOSA", "USLAX");
            let listed = body(getWith(p, "/bookings", sales));
            // **登録した荷主の名前**を見る。IT6 は全荷主が「山田太郎」だったため、
            // 木村を登録して山田を探すという食い違いが緑のまま通っていた（M10）
            Assert.assertTrue(String.contains(substr = "木村花子", listed));
            Assert.assertTrue(String.contains(substr = "SHP-", listed))
        })

    ///
    /// **絞り込みの結果に他人の予約が混ざらない**こと（軸 6-5）。
    ///
    /// 可視範囲と絞り込みは AND で重ねる。片方だけを適用すると混ざる。
    ///
    @Test
    def testFilterDoesNotLeakOtherShipperBookings(): Unit \ Assert + IO =
        TestApp.withAppAndDb("booking_filter_scope", (p, ds) -> {
            let sales = login(p);
            discard registerShipperAndBook(p, sales, "kimura@example.com", "JPOSA", "USLAX");
            let theirs = registerShipperAndBook(p, sales, "tanaka@example.com", "JPTYO", "DEHAM");
            AuthFixtures.linkShipper(ds, AuthFixtures.shipperUsername(), shipperCodeFor(p, sales, "kimura@example.com"));
            let shipper = loginAs(p, AuthFixtures.shipperUsername());
            // 他人の予約に一致する語で検索しても出ないこと
            let listed = body(getWith(p, "/bookings?q=JPTYO", shipper));
            Assert.assertTrue(not String.contains(substr = theirs, listed))
        })

    /// 該当なしと未登録を区別すること
    @Test
    def testFilterShowsNoMatchMessage(): Unit \ Assert + IO =
        withApp("booking_filter_none", p -> {
            let sales = login(p);
            discard registerShipperAndBook(p, sales, "kimura@example.com", "JPOSA", "USLAX");
            let listed = body(getWith(p, "/bookings?q=NOSUCHPORT", sales));
            Assert.assertTrue(String.contains(substr = "該当する予約はありません", listed))
        })

    /// SQL のワイルドカードをそのままの文字として扱うこと
    @Test
    def testFilterTreatsWildcardAsLiteral(): Unit \ Assert + IO =
        withApp("booking_filter_wildcard", p -> {
            let sales = login(p);
            let a = registerShipperAndBook(p, sales, "kimura@example.com", "JPOSA", "USLAX");
            let listed = body(getWith(p, "/bookings?q=%25", sales));
            Assert.assertTrue(not String.contains(substr = a, listed))
        })

    /// 荷主ロールが navbar から予約へ到達できること
    @Test
    def testShipperReachesBookingsFromNavigation(): Unit \ Assert + IO =
        withApp("booking_shipper_nav", p -> {
            let shipper = loginAs(p, AuthFixtures.shipperUsername());
            Assert.assertTrue(String.contains(substr = "href=\"/bookings\"",
                body(getWith(p, "/", shipper))))
        })

    // ---- 可視範囲テストのヘルパー ----

    /// 荷主を登録し、その荷主で予約して予約番号を返す
    def registerShipperAndBook(p: Int32, cookie: String, email: String,
                               origin: String, destination: String): String \ Assert + IO =
        registerShipper(p, cookie, email);
        let code = shipperCodeFor(p, cookie, email);
        let res = book(p, cookie,
            "shipperCode=" + code + "&origin=" + origin + "&destination=" + destination +
            "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
        assertStatus(302, res);
        bookingIdOfFlash(res)

    /// 応答の通知 Cookie から発行された予約番号を取り出す
    def bookingIdOfFlash(res: TestResponse): String \ Assert =
        match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None => { Assert.fail("予約の応答に通知 Cookie が付いていない"); "" }
            case Some(v) => match String.indexOf(substr = "BK-", v) {
                case None    => { Assert.fail("通知に予約番号が含まれていない: ${v}"); "" }
                case Some(i) => String.slice(start = i, end = i + 11, v)
            }
        }

    /// メールアドレスから荷主コードを引く（一覧の絞り込みを使う）
    def shipperCodeFor(p: Int32, cookie: String, email: String): String \ Assert + IO =
        let html = body(getWith(p, "/shippers?q=" + email, cookie));
        match String.indexOf(substr = "SHP-", html) {
            case None    => { Assert.fail("荷主一覧に ${email} の荷主コードが見つからない"); "" }
            case Some(i) => String.slice(start = i, end = i + 12, html)
        }


    /// 冷凍貨物の詳細に温度条件が出ること（内部表現をそのまま出さない）
    @Test
    def testDetailShowsTemperatureInReadableForm(): Unit \ Assert + IO =
        withApp("booking_detail_temp", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            discard book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=REFRIGERATED&weight=1200" +
                "&minTemperature=-25.5&maxTemperature=-18&temperatureUnit=CELSIUS");
            let detail = body(getWith(p, "/bookings/" + anyBookingId(p, cookie), cookie));
            Assert.assertTrue(String.contains(substr = "-25.5", detail));
            Assert.assertTrue(String.contains(substr = "-18", detail));
            // 千分の一度の内部表現を出さないこと
            Assert.assertTrue(not String.contains(substr = "-25500", detail))
        })

    /// 危険物の詳細に申告が出ること
    @Test
    def testDetailShowsHazardousDeclaration(): Unit \ Assert + IO =
        withApp("booking_detail_hazard", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            discard book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=HAZARDOUS&weight=1200" +
                "&hazardousClass=3&unNumber=UN1203&properShippingName=" +
                "%E3%82%AC%E3%82%BD%E3%83%AA%E3%83%B3");
            let detail = body(getWith(p, "/bookings/" + anyBookingId(p, cookie), cookie));
            Assert.assertTrue(String.contains(substr = "UN1203", detail))
        })

    /// 存在しない予約番号では 404 を返すこと
    @Test
    def testReturnsNotFoundForUnknownBooking(): Unit \ Assert + IO =
        withApp("booking_detail_unknown", p -> {
            let cookie = login(p);
            assertStatus(404, getWith(p, "/bookings/BK-NOTEXIST", cookie))
        })

    ///
    /// 経路設計へ引き渡すと状態が変わること（受入基準 2）。
    ///
    @Test
    def testAssignsBookingToRouting(): Unit \ Assert + IO =
        withApp("booking_assign", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            discard book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            let bookingId = anyBookingId(p, cookie);
            let res = assign(p, cookie, bookingId);
            assertStatus(302, res);
            let detail = body(getWith(p, "/bookings/" + bookingId, cookie));
            Assert.assertTrue(String.contains(substr = "経路設計中", detail));
            Assert.assertTrue(not String.contains(substr = "仮受付", detail))
        })

    ///
    /// 二重に引き渡せないこと。
    ///
    /// 連打や再送信で 2 回届く。**2 回目が通ると経路設計側に同じ依頼が
    /// 二重に積まれる**。
    ///
    @Test
    def testRejectsAssigningTwice(): Unit \ Assert + IO =
        withApp("booking_assign_twice", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            discard book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            let bookingId = anyBookingId(p, cookie);
            assertStatus(302, assign(p, cookie, bookingId));
            let second = assign(p, cookie, bookingId);
            assertStatus(200, second);
            Assert.assertTrue(String.contains(substr = "引き渡せません", body(second)))
        })

    ///
    /// 引き渡せない予約では、ボタンが押せず**理由が示される**こと（受入基準 4）。
    ///
    /// 「押せない」だけでは、利用者は待てばよいのか誰かに聞くのか分からない。
    ///
    @Test
    def testShowsReasonWhenAssignmentIsBlocked(): Unit \ Assert + IO =
        withApp("booking_assign_blocked", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            discard book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            let bookingId = anyBookingId(p, cookie);
            discard assign(p, cookie, bookingId);
            let detail = body(getWith(p, "/bookings/" + bookingId, cookie));
            Assert.assertTrue(String.contains(substr = "経路設計へ引き渡せません", detail))
        })

    /// 仮受付の予約には引き渡しボタンが出ること
    @Test
    def testShowsAssignButtonForPreliminaryBooking(): Unit \ Assert + IO =
        withApp("booking_assign_button", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            discard book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            let detail = body(getWith(p, "/bookings/" + anyBookingId(p, cookie), cookie));
            Assert.assertTrue(String.contains(substr = "経路設計へ引き渡す", detail))
        })

    ///
    /// CSRF トークンなしの引き渡しが拒否されること。
    ///
    /// **壊れ方**: ここが破れると、第三者のサイトから予約の状態を勝手に進められる。
    ///
    @Test
    def testRejectsAssignWithoutCsrfToken(): Unit \ Assert + IO =
        withApp("booking_assign_csrf", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            discard book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            let bookingId = anyBookingId(p, cookie);
            assertStatus(403,
                postWith(p, "/bookings/" + bookingId + "/assign-to-routing", "", cookie));
            // 状態が変わっていないこと
            Assert.assertTrue(String.contains(substr = "仮受付",
                body(getWith(p, "/bookings/" + bookingId, cookie))))
        })

    /// 一覧の行から詳細へ行けること
    @Test
    def testListLinksToDetail(): Unit \ Assert + IO =
        withApp("booking_list_link", p -> {
            let cookie = login(p);
            registerShipper(p, cookie, "yamada@example.com");
            let code = anyShipperCode(p, cookie);
            discard book(p, cookie,
                "shipperCode=" + code + "&origin=JPOSA&destination=USLAX" +
                "&arrivalDeadline=2026-12-31&cargoType=GENERAL&weight=1200");
            let bookingId = anyBookingId(p, cookie);
            Assert.assertTrue(String.contains(substr = "href=\"/bookings/" + bookingId + "\"",
                body(getWith(p, "/bookings", cookie))))
        })

    /// 一覧から予約番号を 1 つ取り出す
    def anyBookingId(p: Int32, cookie: String): String \ Assert + IO =
        let html = body(getWith(p, "/bookings", cookie));
        match String.indexOf(substr = "BK-", html) {
            case None    => { Assert.fail("予約一覧に予約番号が見つからなかった"); "" }
            case Some(i) => String.slice(start = i, end = i + 11, html)
        }

    /// 経路設計へ引き渡す
    def assign(p: Int32, cookie: String, bookingId: String): TestResponse \ Assert + IO =
        let token = csrfTokenOf(p, "/bookings/" + bookingId, cookie);
        postWith(p, "/bookings/" + bookingId + "/assign-to-routing", "_csrf=" + token, cookie)

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
            Assert.assertTrue(String.contains(
                substr = "危険物を選んだ場合は危険物申告", body(res)));
            Assert.assertTrue(String.contains(substr = "invalid-feedback", body(res)))
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
            Assert.assertTrue(String.contains(
                substr = "冷凍・冷蔵貨物を選んだ場合は温度管理条件", body(res)));
            Assert.assertTrue(String.contains(substr = "invalid-feedback", body(res)))
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
            // **エラーメッセージ全文で検証する**。「UN 番号」だけだと入力欄の
            // ラベル（`UN 番号（例: UN1203）`）に一致し、**エラーが 1 つも
            // 描画されなくても緑になる**（IT5 レビューで発見した空振り）
            Assert.assertTrue(String.contains(
                substr = "UN 番号は UN と数字 4 桁で入力してください", body(res)));
            Assert.assertTrue(String.contains(substr = "invalid-feedback", body(res)))
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
            Assert.assertTrue(String.contains(
                substr = "温度は数値で入力してください", body(res)));
            Assert.assertTrue(String.contains(substr = "invalid-feedback", body(res)))
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
            Assert.assertTrue(String.contains(
                substr = "最低温度は最高温度以下で入力してください", body(res)));
            Assert.assertTrue(String.contains(substr = "invalid-feedback", body(res)))
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
            Assert.assertTrue(String.contains(
                substr = "危険物申告は貨物種別が「危険物」のときだけ", body(res)))
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
                "&hazardousClass=HAZ-CLASS-3&unNumber=UN1203&properShippingName=" +
                "%E3%82%AC%E3%82%BD%E3%83%AA%E3%83%B3");
            assertStatus(200, res);
            let html = body(res);
            Assert.assertTrue(String.contains(substr = "value=\"UN1203\"", html));
            Assert.assertTrue(String.contains(substr = "value=\"HAZ-CLASS-3\"", html));
            Assert.assertTrue(String.contains(substr = "value=\"ガソリン\"", html))
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
            // **保存されたことまで確かめる**。一覧に `BK-` があることだけでは、
            // 寸法が無視されて NULL 保存されても緑になり、
            // このテストが是正しようとした欠陥（レビュー M8）を再検出できない
            let detail = body(getWith(p, "/bookings/" + anyBookingId(p, cookie), cookie));
            Assert.assertTrue(String.contains(substr = "120 × 80 × 100 cm", detail))
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
        // **失敗時に証拠を残す**（IT8 レビュー・tester。Try T6）。
        // Set-Cookie が無いという事実だけでは、認証に失敗したのか
        // 別インスタンスが応答したのかを区別できない。`assertStatus` は
        // ステータス・処理したルート・**インスタンス識別子**・本文を出す
        assertStatus(302, res);
        match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None    => { Assert.fail("予約の応答に通知 Cookie が付いていない"); "" }
            case Some(v) => match String.indexOf(substr = ";", v) {
                case None    => v
                case Some(i) => String.slice(start = 0, end = i, v)
            }
        }
}
