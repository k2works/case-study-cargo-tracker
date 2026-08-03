///
/// 荷主画面の単体テスト。
///
/// 画面関数は純粋関数のため、`render` した文字列を検証するだけでよい。
/// ブラウザも DB も起動しない。
///
mod ShipperPagesTest {
    use SharedHtml.render
    use ShipperModel.Shipper
    use ShipperModel.{registerIndividual, registerCorporate}
    use ShipperPages.{emptyForm, formOf, newForm, corporateFields, duplicateEmail, index}

    def csrf(): Option[String] = Some("t".repeat(64))

    def individual(id: String, name: String, email: String): Shipper =
        match registerIndividual(id, name, email, Some("03-1234-5678"), None) {
            case Ok(s)  => s
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    def corporate(): Shipper =
        match registerCorporate("bbbbbbbb-0000-0000-0000-000000000000", "株式会社サンプル",
                                "sales@example.com", None, None, "CT-0001", 1500) {
            case Ok(s)  => s
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    // ---- 登録フォーム ----

    /// 必要な入力欄がすべてあること
    @Test
    def testFormHasAllFields(): Unit \ Assert =
        let html = render(newForm(emptyForm(), csrf()));
        let fields = "name" :: "email" :: "phone" :: "address" :: "shipperType" :: Nil;
        Assert.assertTrue(fields |> List.forAll(f ->
            String.contains(substr = "name=\"${f}\"", html)))

    /// CSRF トークンが自動で入ること
    @Test
    def testFormIncludesCsrfToken(): Unit \ Assert =
        Assert.assertTrue(String.contains(substr = "name=\"_csrf\"",
            render(newForm(emptyForm(), csrf()))))

    ///
    /// 個人を選んでいる間は法人固有の欄を出さないこと。
    ///
    /// 出しっぱなしにすると、個人荷主の登録で「契約番号は？」と利用者が迷う。
    ///
    @Test
    def testHidesCorporateFieldsForIndividual(): Unit \ Assert =
        let html = render(newForm(emptyForm(), csrf()));
        Assert.assertTrue(not String.contains(substr = "name=\"contractNumber\"", html));
        Assert.assertTrue(not String.contains(substr = "name=\"discountRate\"", html))

    /// 法人を選ぶと契約番号と割引率の欄が出ること（US03 受入基準 1）
    @Test
    def testShowsCorporateFieldsForCorporate(): Unit \ Assert =
        let view = formOf("", "", "", "", "CORPORATE", "", "", Nil);
        let html = render(newForm(view, csrf()));
        Assert.assertTrue(String.contains(substr = "name=\"contractNumber\"", html));
        Assert.assertTrue(String.contains(substr = "name=\"discountRate\"", html))

    ///
    /// 種別の切替が htmx の部分更新であること。
    ///
    /// ページ全体を再読み込みすると、**入力途中の他の項目が消える**。
    ///
    @Test
    def testTypeSelectUsesHtmxPartialUpdate(): Unit \ Assert =
        let html = render(newForm(emptyForm(), csrf()));
        Assert.assertTrue(String.contains(substr = "hx-get=\"/shippers/new/corporate-fields\"", html));
        Assert.assertTrue(String.contains(substr = "hx-target=\"#corporate-fields\"", html))

    ///
    /// 部分更新と全体描画が同じ内容を返すこと。
    ///
    /// 別々に作ると「初回表示では出るのに切り替えると出ない」食い違いが生まれる。
    ///
    @Test
    def testPartialAndFullRenderAgree(): Unit \ Assert =
        let view = formOf("", "", "", "", "CORPORATE", "CT-1", "10", Nil);
        let partial = render(corporateFields(view));
        Assert.assertTrue(String.contains(substr = partial, render(newForm(view, csrf()))))

    ///
    /// 入力エラー時に入力値が保持されること。
    ///
    /// 打ち直しを強いると、長い住所を入れた利用者が最もつらい。
    ///
    @Test
    def testKeepsInputOnValidationError(): Unit \ Assert =
        let view = formOf("山田太郎", "bad-email", "03-1234-5678", "東京都千代田区",
                          "INDIVIDUAL", "", "",
                          ("email", "メールアドレスの形式が正しくありません") :: Nil);
        let html = render(newForm(view, csrf()));
        Assert.assertTrue(String.contains(substr = "value=\"山田太郎\"", html));
        Assert.assertTrue(String.contains(substr = "value=\"bad-email\"", html));
        Assert.assertTrue(String.contains(substr = "value=\"東京都千代田区\"", html));
        Assert.assertTrue(String.contains(substr = "メールアドレスの形式が正しくありません", html))

    ///
    /// エラーメッセージに内部語が出ないこと（Try T8）。
    ///
    /// `EmailIsMalformed` のような列挙値を画面へ出さない。
    ///
    @Test
    def testErrorMessageUsesBusinessLanguage(): Unit \ Assert =
        let view = formOf("", "", "", "", "INDIVIDUAL", "", "",
                          ("email", "メールアドレスの形式が正しくありません（例: yamada@example.com）") :: Nil);
        let html = render(newForm(view, csrf()));
        Assert.assertTrue(not String.contains(substr = "EmailIsMalformed", html));
        Assert.assertTrue(not String.contains(substr = "ShipperError", html))

    /// 入力がエスケープされること（XSS）
    @Test
    def testEscapesInput(): Unit \ Assert =
        let view = formOf("<script>alert(1)</script>", "", "", "", "INDIVIDUAL", "", "", Nil);
        let html = render(newForm(view, csrf()));
        Assert.assertTrue(not String.contains(substr = "<script>alert(1)</script>", html))

    // ---- 重複（Try T4） ----

    ///
    /// 重複時に「次に取れる行動」が 2 つ示されること。
    ///
    /// **これが本テストの主眼**である。「重複しています」だけでは、利用者は
    /// 入力をやり直す以外の選択肢を持てない。
    ///
    @Test
    def testDuplicateOffersTwoWaysForward(): Unit \ Assert =
        let existing = individual("aaaaaaaa-0000-0000-0000-000000000000",
                                  "既存 花子", "yamada@example.com");
        let view = formOf("山田太郎", "yamada@example.com", "", "", "INDIVIDUAL", "", "", Nil);
        let html = render(duplicateEmail(existing, view, csrf()));
        Assert.assertTrue(String.contains(substr = "既存の荷主を使う", html));
        Assert.assertTrue(String.contains(substr = "入力を修正する", html))

    /// 重複時に既存荷主が誰か分かること
    @Test
    def testDuplicateShowsExistingShipper(): Unit \ Assert =
        let existing = individual("aaaaaaaa-0000-0000-0000-000000000000",
                                  "既存 花子", "yamada@example.com");
        let html = render(duplicateEmail(existing, emptyForm(), csrf()));
        Assert.assertTrue(String.contains(substr = "既存 花子", html));
        Assert.assertTrue(String.contains(substr = "SHP-AAAAAAAA", html))

    /// 重複時も入力値を保持したフォームを併置すること
    @Test
    def testDuplicateKeepsFormInput(): Unit \ Assert =
        let existing = individual("aaaaaaaa-0000-0000-0000-000000000000",
                                  "既存 花子", "yamada@example.com");
        let view = formOf("山田太郎", "yamada@example.com", "", "", "INDIVIDUAL", "", "", Nil);
        Assert.assertTrue(String.contains(substr = "value=\"山田太郎\"",
            render(duplicateEmail(existing, view, csrf()))))

    // ---- 一覧 ----

    /// 0 件のときに空の表ではなく案内を出すこと
    @Test
    def testEmptyListShowsGuidance(): Unit \ Assert =
        let html = render(index(Nil, None));
        Assert.assertTrue(String.contains(substr = "登録されている荷主はありません", html));
        Assert.assertTrue(not String.contains(substr = "<tbody", html))

    /// 一覧に荷主コード・名前・種別・メールが出ること
    @Test
    def testListShowsShipperColumns(): Unit \ Assert =
        let html = render(index(individual("aaaaaaaa-0000-0000-0000-000000000000",
                                           "山田太郎", "yamada@example.com") :: Nil, None));
        Assert.assertTrue(String.contains(substr = "SHP-AAAAAAAA", html));
        Assert.assertTrue(String.contains(substr = "山田太郎", html));
        Assert.assertTrue(String.contains(substr = "個人", html));
        Assert.assertTrue(String.contains(substr = "yamada@example.com", html))

    ///
    /// 割引率が百分率で表示されること。
    ///
    /// 内部表現（万分率）をそのまま出すと `1500%` に見える。
    ///
    @Test
    def testListShowsDiscountRateAsPercent(): Unit \ Assert =
        let html = render(index(corporate() :: Nil, None));
        Assert.assertTrue(String.contains(substr = "15.00%", html));
        Assert.assertTrue(not String.contains(substr = "1500%", html))

    /// 個人荷主の割引率欄は空欄の代わりに `-` を出すこと
    @Test
    def testListShowsDashForIndividualDiscount(): Unit \ Assert =
        let html = render(index(individual("aaaaaaaa-0000-0000-0000-000000000000",
                                           "山田太郎", "yamada@example.com") :: Nil, None));
        Assert.assertTrue(String.contains(substr = "<td>-</td>", html))

    /// 一覧から登録画面への導線があること
    @Test
    def testListLinksToRegistration(): Unit \ Assert =
        Assert.assertTrue(String.contains(substr = "href=\"/shippers/new\"", render(index(Nil, None))))

    /// 種別が内部語ではなく業務の言葉で出ること（Try T8）
    @Test
    def testListUsesBusinessLanguageForType(): Unit \ Assert =
        let html = render(index(corporate() :: Nil, None));
        Assert.assertTrue(String.contains(substr = "法人", html));
        Assert.assertTrue(not String.contains(substr = "CORPORATE", html))

    // ---- 通知（IT5 TS09） ----

    /// 通知が成功の見た目で出ること
    @Test
    def testListShowsNoticeAsSuccessAlert(): Unit \ Assert =
        let html = render(index(Nil, Some("荷主を登録しました。荷主コードは SHP-1A2B3C4D です。")));
        Assert.assertTrue(String.contains(substr = "alert-success", html));
        Assert.assertTrue(String.contains(substr = "SHP-1A2B3C4D", html))

    /// 通知がないときは通知領域を出さないこと
    @Test
    def testListOmitsAlertWithoutNotice(): Unit \ Assert =
        Assert.assertTrue(not String.contains(substr = "alert-success", render(index(Nil, None))))

    ///
    /// **通知が一覧より前に出る**こと。
    ///
    /// 「表示される」だけでは不十分で、位置が後ろだと一覧を読み始めてから
    /// 気付くことになる。操作の結果は最初に目に入る位置に要る。
    ///
    @Test
    def testNoticeAppearsBeforeTheList(): Unit \ Assert =
        let html = render(index(corporate() :: Nil, Some("荷主を登録しました。荷主コードは SHP-1A2B3C4D です。")));
        match (String.indexOf(substr = "alert-success", html),
               String.indexOf(substr = "shipper-list", html)) {
            case (Some(a), Some(b)) => Assert.assertTrue(a < b)
            case _                  => Assert.fail("通知または一覧が描画されていない")
        }

    ///
    /// 通知の文言が**エスケープされる**こと。
    ///
    /// 文言はサーバが組み立てるため現状は安全だが、将来の変更で外部由来の値が
    /// 混じったときに気付けるようにしておく。
    ///
    @Test
    def testNoticeIsEscaped(): Unit \ Assert =
        let html = render(index(Nil, Some("<script>alert(1)</script>")));
        Assert.assertTrue(not String.contains(substr = "<script>alert(1)</script>", html))
}
