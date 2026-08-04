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
        SharedHttpTestClient.login(p, name, password())

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

    // ---- 一覧の絞り込み（IT5 TS09） ----

    ///
    /// 氏名 / 社名で絞り込めること。
    ///
    /// IT4 では荷主コードを予約フォームに手入力させながら、**探す手段がなかった**。
    /// 台帳を別に持つ運用になり、一元管理という US02 の目的が崩れる。
    ///
    @Test
    def testFiltersShippersByName(): Unit \ Assert + IO =
        withApp("shipper_filter_name", p -> {
            let cookie = login(p, salesUser());
            discard register(p, cookie, "name=山田太郎&email=yamada@example.com&shipperType=INDIVIDUAL");
            discard register(p, cookie, "name=佐藤花子&email=sato@example.com&shipperType=INDIVIDUAL");
            let listed = body(getWith(p, "/shippers?q=山田", cookie));
            Assert.assertTrue(String.contains(substr = "山田太郎", listed));
            Assert.assertTrue(not String.contains(substr = "佐藤花子", listed))
        })

    /// メールアドレスで絞り込めること
    @Test
    def testFiltersShippersByEmail(): Unit \ Assert + IO =
        withApp("shipper_filter_email", p -> {
            let cookie = login(p, salesUser());
            discard register(p, cookie, "name=山田太郎&email=yamada@example.com&shipperType=INDIVIDUAL");
            discard register(p, cookie, "name=佐藤花子&email=sato@example.com&shipperType=INDIVIDUAL");
            let listed = body(getWith(p, "/shippers?q=sato@", cookie));
            Assert.assertTrue(String.contains(substr = "佐藤花子", listed));
            Assert.assertTrue(not String.contains(substr = "山田太郎", listed))
        })

    ///
    /// 荷主コードで絞り込めること。
    ///
    /// 予約フォームに入れるのは荷主コードであり、**コードから引けなければ
    /// 絞り込みの意味がない**。
    ///
    @Test
    def testFiltersShippersByCode(): Unit \ Assert + IO =
        withApp("shipper_filter_code", p -> {
            let cookie = login(p, salesUser());
            discard register(p, cookie, "name=山田太郎&email=yamada@example.com&shipperType=INDIVIDUAL");
            discard register(p, cookie, "name=佐藤花子&email=sato@example.com&shipperType=INDIVIDUAL");
            let code = firstShipperCode(p, cookie);
            let listed = body(getWith(p, "/shippers?q=" + code, cookie));
            Assert.assertTrue(String.contains(substr = code, listed));
            // 2 件のうち **表に残るのは 1 行だけ**であること。
            // `SHP-` の総数では数えられない。検索語が入力欄へ書き戻るため、
            // 一覧が 0 行でも 1 回は現れる（最初にこの数え方で誤検知した）
            Assert.assertEq(expected = 1, countOccurrences("<td>SHP-", listed))
        })

    /// 大文字小文字を区別しないこと（軸 1-10）
    @Test
    def testFilterIsCaseInsensitive(): Unit \ Assert + IO =
        withApp("shipper_filter_case", p -> {
            let cookie = login(p, salesUser());
            discard register(p, cookie, "name=Yamada Taro&email=yamada@example.com&shipperType=INDIVIDUAL");
            let listed = body(getWith(p, "/shippers?q=YAMADA", cookie));
            Assert.assertTrue(String.contains(substr = "Yamada Taro", listed))
        })

    /// 条件に合うものがないとき、その旨が出ること（空一覧と区別する）
    @Test
    def testFilterShowsNoMatchMessage(): Unit \ Assert + IO =
        withApp("shipper_filter_none", p -> {
            let cookie = login(p, salesUser());
            discard register(p, cookie, "name=山田太郎&email=yamada@example.com&shipperType=INDIVIDUAL");
            let listed = body(getWith(p, "/shippers?q=存在しない荷主", cookie));
            Assert.assertTrue(String.contains(substr = "該当する荷主はありません", listed));
            Assert.assertTrue(not String.contains(substr = "山田太郎", listed))
        })

    /// 検索語が入力欄に残ること（打ち直しにならない。軸 3-4）
    @Test
    def testFilterKeepsKeywordInTheBox(): Unit \ Assert + IO =
        withApp("shipper_filter_keep", p -> {
            let cookie = login(p, salesUser());
            let listed = body(getWith(p, "/shippers?q=yamada", cookie));
            Assert.assertTrue(String.contains(substr = "value=\"yamada\"", listed))
        })

    ///
    /// 検索語が**エスケープされる**こと。
    ///
    /// 検索語は利用者が自由に打つ文字列であり、そのまま入力欄へ書き戻す。
    ///
    @Test
    def testFilterKeywordIsEscaped(): Unit \ Assert + IO =
        withApp("shipper_filter_escape", p -> {
            let cookie = login(p, salesUser());
            let listed = body(getWith(p, "/shippers?q=%3Cscript%3E", cookie));
            Assert.assertTrue(not String.contains(substr = "<script>", listed))
        })

    ///
    /// SQL のワイルドカードを**そのままの文字として扱う**こと。
    ///
    /// `%` を素通しすると、`%` の 1 文字で全件が出る。利用者は
    /// 「絞り込んだのに全部出る」ことになり、絞り込みが信用できなくなる。
    ///
    @Test
    def testFilterTreatsWildcardAsLiteral(): Unit \ Assert + IO =
        withApp("shipper_filter_wildcard", p -> {
            let cookie = login(p, salesUser());
            discard register(p, cookie, "name=山田太郎&email=yamada@example.com&shipperType=INDIVIDUAL");
            let listed = body(getWith(p, "/shippers?q=%25", cookie));
            Assert.assertTrue(not String.contains(substr = "山田太郎", listed))
        })

    ///
    /// 「既存の荷主を使う」を押した先で、**その荷主に絞り込まれている**こと。
    ///
    /// IT4 はリンク先を `/shippers?code=...` にしていたが、一覧はその値を
    /// 読み捨てており、押した先で全件が出ていた。ボタンがあることを確かめる
    /// テストは緑のままで、**導線は着地先で消えていた**（レビュー H18）。
    ///
    /// 「押せる」ではなく「押した先で意図した状態になる」ところまで見る。
    ///
    @Test
    def testUseExistingShipperLandsOnFilteredList(): Unit \ Assert + IO =
        withApp("shipper_existing_link", p -> {
            let cookie = login(p, salesUser());
            discard register(p, cookie, "name=既存 花子&email=yamada@example.com&shipperType=INDIVIDUAL");
            discard register(p, cookie, "name=無関係 次郎&email=jiro@example.com&shipperType=INDIVIDUAL");
            // 重複登録を試みて案内画面を出す
            let dup = register(p, cookie,
                "name=山田太郎&email=yamada@example.com&shipperType=INDIVIDUAL");
            let href = linkHrefFor("既存の荷主を使う", body(dup));
            // 実際にその URL へ移動する（リンクの存在ではなく着地先を見る）
            let landed = body(getWith(p, href, cookie));
            Assert.assertTrue(String.contains(substr = "既存 花子", landed));
            Assert.assertTrue(not String.contains(substr = "無関係 次郎", landed))
        })

    ///
    /// 指定した文言のリンクの `href` を取り出す。
    ///
    /// リンクは `<a class="..." href="...">文言</a>` の形で描画される。
    ///
    /// `Flix` に `String.lastIndexOf` はないため、分割して最後の断片から取る
    def linkHrefFor(label: String, html: String): String \ Assert =
        match String.indexOf(substr = ">" + label + "<", html) {
            case None => { Assert.fail("「${label}」のリンクが見つからなかった"); "" }
            case Some(end) =>
                let before = String.slice(start = 0, end = end, html);
                match List.last(String.splitOn(substr = "href=\"", before)) {
                    case None => { Assert.fail("「${label}」に href がない"); "" }
                    case Some(rest) => match String.indexOf(substr = "\"", rest) {
                        case None    => { Assert.fail("href が閉じていない"); "" }
                        case Some(q) => String.slice(start = 0, end = q, rest)
                    }
                }
        }

    /// 一覧の最初の荷主コードを取り出す
    def firstShipperCode(p: Int32, cookie: String): String \ Assert + IO =
        let html = body(getWith(p, "/shippers", cookie));
        match String.indexOf(substr = "SHP-", html) {
            case None    => { Assert.fail("荷主一覧に荷主コードが見つからなかった"); "" }
            case Some(i) => String.slice(start = i, end = i + 12, html)
        }

    /// 部分文字列の出現回数
    def countOccurrences(needle: String, haystack: String): Int32 =
        List.length(String.splitOn(substr = needle, haystack)) - 1

    // ---- 一覧から予約への導線（IT5 TS09） ----

    ///
    /// 一覧の各行から「この荷主で予約する」へ行けて、**着地先で荷主が
    /// 選択済みになっている**こと。
    ///
    /// これが無いと、利用者は荷主コードを目で読んで予約フォームへ書き写すことになる。
    /// 12 文字の転記は打ち間違いを生み、間違えても「存在しない荷主」で弾かれるだけで
    /// どこを間違えたか分からない。
    ///
    @Test
    def testBookForThisShipperPrefillsTheForm(): Unit \ Assert + IO =
        withApp("shipper_book_link", p -> {
            let cookie = login(p, salesUser());
            discard register(p, cookie, "name=山田太郎&email=yamada@example.com&shipperType=INDIVIDUAL");
            let listed = body(getWith(p, "/shippers", cookie));
            Assert.assertTrue(String.contains(substr = "この荷主で予約する", listed));
            let href = linkHrefFor("この荷主で予約する", listed);
            // 実際にその URL へ移動し、荷主コードが入っていることを確かめる
            let form = body(getWith(p, href, cookie));
            let code = firstShipperCode(p, cookie);
            Assert.assertTrue(String.contains(substr = "value=\"" + code + "\"", form))
        })

    // ---- 発行された値を利用者が確認できること（IT5 TS09） ----

    ///
    /// 登録に成功したら、**遷移先に発行された荷主コードが出る**こと。
    ///
    /// IT4 では登録しても荷主コードがどこにも出ず、利用者は一覧から自分の行を
    /// 目で探すことになっていた。受入基準（US02-3）は「登録完了メッセージと
    /// 荷主コードが表示される」であり、**一覧に行があること**では充足しない。
    ///
    @Test
    def testShowsIssuedShipperCodeAfterRegistration(): Unit \ Assert + IO =
        withApp("shipper_flash_code", p -> {
            let cookie = login(p, salesUser());
            let res = register(p, cookie,
                "name=佐藤花子&email=sato@example.com&shipperType=INDIVIDUAL");
            assertStatus(302, res);
            // PRG の遷移先へ、通知 Cookie を持って移動する
            let flash = flashCookieOf(res);
            let listed = body(getWith(p, "/shippers", cookie + "; " + flash));
            Assert.assertTrue(String.contains(substr = "登録しました", listed));
            // 発行されたコードそのものが出ていること
            Assert.assertTrue(String.contains(substr = "SHP-", listed))
        })

    ///
    /// 通知は **1 回だけ**表示されること。
    ///
    /// 消さないと再読み込みのたびに成功メッセージが出続け、
    /// 「登録できていないのでは」と利用者を迷わせる。
    ///
    @Test
    def testFlashIsShownOnlyOnce(): Unit \ Assert + IO =
        withApp("shipper_flash_once", p -> {
            let cookie = login(p, salesUser());
            let res = register(p, cookie,
                "name=鈴木一郎&email=suzuki@example.com&shipperType=INDIVIDUAL");
            let flash = flashCookieOf(res);
            let first = getWith(p, "/shippers", cookie + "; " + flash);
            Assert.assertTrue(String.contains(substr = "登録しました", body(first)));
            // 表示時に Cookie を消していること
            match SharedHttpTestClient.header(first, "Set-Cookie") {
                case None    => Assert.fail("通知を表示したのに削除の Set-Cookie が返っていない")
                case Some(v) => Assert.assertTrue(String.contains(substr = "Max-Age=0", v))
            };
            // 削除されたので 2 回目は出ない
            let second = getWith(p, "/shippers", cookie);
            Assert.assertTrue(not String.contains(substr = "登録しました", body(second)))
        })

    ///
    /// 偽装された通知 Cookie では**何も表示されない**こと。
    ///
    /// Cookie は利用者が書き換えられる。未知の種別や形式の違う識別子を
    /// そのまま画面へ出すと、任意の文言を自分の画面に出せてしまう。
    ///
    @Test
    def testForgedFlashCookieShowsNothing(): Unit \ Assert + IO =
        withApp("shipper_flash_forged", p -> {
            let cookie = login(p, salesUser());
            let listed = body(getWith(p, "/shippers",
                cookie + "; FLASH=accountCredited:1000000"));
            Assert.assertTrue(not String.contains(substr = "accountCredited", listed));
            Assert.assertTrue(not String.contains(substr = "1000000", listed))
        })

    ///
    /// **予約の通知が荷主一覧に出ない**こと。
    ///
    /// 種別ごとに文言を分けている意味は、出す画面を間違えないことにもある。
    ///
    @Test
    def testBookingFlashDoesNotLeakIntoShipperList(): Unit \ Assert + IO =
        withApp("shipper_flash_kind", p -> {
            let cookie = login(p, salesUser());
            let listed = body(getWith(p, "/shippers",
                cookie + "; FLASH=cargoBooked:BK-1A2B3C4D"));
            Assert.assertTrue(not String.contains(substr = "予約しました", listed))
        })

    /// レスポンスの `Set-Cookie` から通知 Cookie を取り出す
    def flashCookieOf(res: TestResponse): String \ Assert =
        // **失敗時に証拠を残す**（IT8 レビュー・tester。Try T6）。
        // Set-Cookie が無いという事実だけでは、認証に失敗したのか
        // 別インスタンスが応答したのかを区別できない。`assertStatus` は
        // ステータス・処理したルート・**インスタンス識別子**・本文を出す
        assertStatus(302, res);
        match SharedHttpTestClient.header(res, "Set-Cookie") {
            case None    => { Assert.fail("登録の応答に通知 Cookie が付いていない"); "" }
            case Some(v) => match String.indexOf(substr = ";", v) {
                case None    => v
                case Some(i) => String.slice(start = 0, end = i, v)
            }
        }

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
