///
/// `Shipper` 集約と値オブジェクトの単体テスト。
///
/// 中盤（インサイドアウト）の最初のテスト。**効果を一切要求しない**。
/// DB もサーバも起動せず、入力と出力だけでビジネスルールを固定する。
///
/// 対応するビジネスルールは [ドメインモデル設計](../../../../docs/design/domain-model.md)
/// の「Shipper Context - ビジネスルール」1〜5 である。
///
mod ShipperTest {
    use ShipperModel.{Shipper, ShipperType, ShipperError}
    use ShipperModel.{registerIndividual, registerCorporate, shipperCodeOf, emailOf,
                      nameOf, addressOf, phoneOf, shipperTypeOf, discountRateOf,
                      contractNumberOf, shipperIdOf, discountRatePercent}

    def anId(): String = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"

    ///
    /// 失敗の理由だけを取り出す。
    ///
    /// `Shipper` はレコードであり `Eq` を導出できない。**成功時の値を比較したいのではなく
    /// 失敗の理由を比較したい**ので、理由だけを取り出して突き合わせる。
    ///
    def errorOf(r: Result[ShipperError, Shipper]): Option[ShipperError] = match r {
        case Err(e) => Some(e)
        case Ok(_)  => None
    }

    // ---- 個人荷主（US02） ----

    /// 必須項目だけで個人荷主を登録できること
    @Test
    def testRegistersIndividualWithRequiredFieldsOnly(): Unit \ Assert =
        match registerIndividual(anId(), "山田太郎", "yamada@example.com", None, None) {
            case Err(e) => Assert.fail("個人荷主の登録に失敗した: ${e}")
            case Ok(s)  =>
                Assert.assertEq(expected = "山田太郎", nameOf(s));
                Assert.assertEq(expected = "yamada@example.com", emailOf(s));
                Assert.assertTrue(shipperTypeOf(s) == ShipperType.Individual);
                Assert.assertTrue(Option.isEmpty(phoneOf(s)));
                Assert.assertTrue(Option.isEmpty(addressOf(s)))
        }

    /// 任意項目（電話・住所）を伴って登録できること
    @Test
    def testRegistersIndividualWithOptionalFields(): Unit \ Assert =
        match registerIndividual(anId(), "山田太郎", "yamada@example.com",
                                 Some("03-1234-5678"), Some("東京都千代田区 1-1-1")) {
            case Err(e) => Assert.fail("任意項目付きの登録に失敗した: ${e}")
            case Ok(s)  =>
                Assert.assertEq(expected = Some("03-1234-5678"), phoneOf(s));
                Assert.assertEq(expected = Some("東京都千代田区 1-1-1"), addressOf(s))
        }

    ///
    /// 荷主コードが `SHP-` + 8 文字で自動生成されること（ビジネスルール 5）。
    ///
    /// 利用者に見えるのはこのコードである（`ShipperId` は内部識別子）。
    ///
    @Test
    def testGeneratesShipperCodeFromId(): Unit \ Assert =
        match registerIndividual(anId(), "山田太郎", "yamada@example.com", None, None) {
            case Err(e) => Assert.fail("登録に失敗した: ${e}")
            case Ok(s)  =>
                let code = shipperCodeOf(s);
                Assert.assertTrue(String.startsWith(prefix = "SHP-", code));
                Assert.assertEq(expected = 12, String.length(code));
                // ID の先頭 8 文字から作る。同じ ID なら同じコードになる
                Assert.assertEq(expected = "SHP-3F2504E0", code)
        }

    /// 個人荷主は法人固有の項目を持たないこと
    @Test
    def testIndividualHasNoCorporateFields(): Unit \ Assert =
        match registerIndividual(anId(), "山田太郎", "yamada@example.com", None, None) {
            case Err(e) => Assert.fail("登録に失敗した: ${e}")
            case Ok(s)  =>
                Assert.assertTrue(Option.isEmpty(contractNumberOf(s)));
                Assert.assertTrue(Option.isEmpty(discountRateOf(s)))
        }

    // ---- 必須項目の検証（ビジネスルール 1） ----

    /// 荷主名が空なら登録できないこと
    @Test
    def testRejectsEmptyName(): Unit \ Assert =
        Assert.assertEq(expected = Some(ShipperError.NameIsRequired),
            errorOf(registerIndividual(anId(), "", "yamada@example.com", None, None)))

    /// 空白のみの荷主名も空として扱うこと
    @Test
    def testRejectsBlankName(): Unit \ Assert =
        Assert.assertEq(expected = Some(ShipperError.NameIsRequired),
            errorOf(registerIndividual(anId(), "   ", "yamada@example.com", None, None)))

    /// 荷主名が 200 文字を超えたら登録できないこと（DB の桁数と一致させる）
    @Test
    def testRejectsTooLongName(): Unit \ Assert =
        Assert.assertEq(expected = Some(ShipperError.NameIsTooLong),
            errorOf(registerIndividual(anId(), "あ".repeat(201), "yamada@example.com", None, None)))

    /// 200 文字ちょうどは登録できること（境界の内側）
    @Test
    def testAcceptsNameAtMaxLength(): Unit \ Assert =
        Assert.assertTrue(Result.isOk(
            registerIndividual(anId(), "あ".repeat(200), "yamada@example.com", None, None)))

    /// メールアドレスが空なら登録できないこと
    @Test
    def testRejectsEmptyEmail(): Unit \ Assert =
        Assert.assertEq(expected = Some(ShipperError.EmailIsRequired),
            errorOf(registerIndividual(anId(), "山田太郎", "", None, None)))

    ///
    /// メールアドレスの形式を検証すること。
    ///
    /// 厳密な RFC 準拠は目指さない（正規表現で完全に表現できない）。
    /// **明らかに誤っている入力を弾く**ことで、打ち間違いに気付かせるのが目的である。
    ///
    @Test
    def testRejectsMalformedEmail(): Unit \ Assert =
        let malformed = "yamada" :: "@example.com" :: "yamada@" :: "yamada@example" ::
                        "yamada example@test.com" :: Nil;
        Assert.assertTrue(malformed |> List.forAll(bad ->
            errorOf(registerIndividual(anId(), "山田太郎", bad, None, None)) == Some(ShipperError.EmailIsMalformed)))

    /// メールアドレスが 200 文字を超えたら登録できないこと（DB の桁数と一致）
    @Test
    def testRejectsTooLongEmail(): Unit \ Assert =
        let localPart = "a".repeat(190);
        Assert.assertEq(expected = Some(ShipperError.EmailIsMalformed),
            errorOf(registerIndividual(anId(), "山田太郎", localPart + "@example.com", None, None)))

    /// 電話番号が 50 文字を超えたら登録できないこと
    @Test
    def testRejectsTooLongPhone(): Unit \ Assert =
        Assert.assertEq(expected = Some(ShipperError.PhoneIsTooLong),
            errorOf(registerIndividual(anId(), "山田太郎", "yamada@example.com",
                                       Some("0".repeat(51)), None)))

    /// 契約番号が 50 文字を超えたら登録できないこと
    @Test
    def testRejectsTooLongContractNumber(): Unit \ Assert =
        Assert.assertEq(expected = Some(ShipperError.ContractNumberIsTooLong),
            errorOf(registerCorporate(anId(), "株式会社サンプル", "sales@example.com", None, None,
                                      "C".repeat(51), 1500)))

    /// 住所 500 文字ちょうどは登録できること（境界の内側）
    @Test
    def testAcceptsAddressAtMaxLength(): Unit \ Assert =
        Assert.assertTrue(Result.isOk(
            registerIndividual(anId(), "山田太郎", "yamada@example.com", None,
                               Some("あ".repeat(500)))))

    ///
    /// 空白のみの任意項目を未入力として扱うこと。
    ///
    /// フォームは未入力を空文字で送る。ここで揃えないと、DB に
    /// 「空文字が入った荷主」と「未入力の荷主」が混在する。
    ///
    @Test
    def testTreatsBlankOptionalAsAbsent(): Unit \ Assert =
        match registerIndividual(anId(), "山田太郎", "yamada@example.com",
                                 Some("   "), Some("  ")) {
            case Err(e) => Assert.fail("登録に失敗した: ${e}")
            case Ok(s)  =>
                Assert.assertTrue(Option.isEmpty(phoneOf(s)));
                Assert.assertTrue(Option.isEmpty(addressOf(s)))
        }

    /// 住所が 500 文字を超えたら登録できないこと
    @Test
    def testRejectsTooLongAddress(): Unit \ Assert =
        Assert.assertEq(expected = Some(ShipperError.AddressIsTooLong),
            errorOf(registerIndividual(anId(), "山田太郎", "yamada@example.com", None, Some("あ".repeat(501)))))

    // ---- 法人荷主（US03・ビジネスルール 3・4） ----

    /// 契約番号と割引率を伴って法人荷主を登録できること
    @Test
    def testRegistersCorporateWithContract(): Unit \ Assert =
        match registerCorporate(anId(), "株式会社サンプル", "sales@example.com", None, None,
                                "CT-0001", 1500) {
            case Err(e) => Assert.fail("法人荷主の登録に失敗した: ${e}")
            case Ok(s)  =>
                Assert.assertTrue(shipperTypeOf(s) == ShipperType.Corporate);
                Assert.assertEq(expected = Some("CT-0001"), contractNumberOf(s));
                Assert.assertEq(expected = Some(1500), discountRateOf(s))
        }

    ///
    /// 契約番号が空の法人は登録できないこと。
    ///
    /// **壊れ方**: ここを通すと、法人として登録したのに割引が効かない荷主が生まれる。
    /// 個人へ静かに倒すのではなく、入力の誤りとして返す。
    ///
    @Test
    def testRejectsCorporateWithoutContractNumber(): Unit \ Assert =
        Assert.assertEq(expected = Some(ShipperError.ContractNumberIsRequired),
            errorOf(registerCorporate(anId(), "株式会社サンプル", "sales@example.com", None, None, "", 1500)))

    ///
    /// 割引率が 30% を超えたら登録できないこと（ビジネスルール 4）。
    ///
    /// 割引率は万分率（`3000` = 30.00%）で保持する。`NUMERIC(5,4)` に対応し、
    /// 浮動小数点の誤差を持ち込まない。
    ///
    @Test
    def testRejectsDiscountRateOver30Percent(): Unit \ Assert =
        Assert.assertEq(expected = Some(ShipperError.DiscountRateOutOfRange),
            errorOf(registerCorporate(anId(), "株式会社サンプル", "sales@example.com", None, None,
                              "CT-0001", 3001)))

    /// 30% ちょうどは登録できること（境界の内側）
    @Test
    def testAcceptsDiscountRateAtUpperBound(): Unit \ Assert =
        Assert.assertTrue(Result.isOk(
            registerCorporate(anId(), "株式会社サンプル", "sales@example.com", None, None,
                              "CT-0001", 3000)))

    /// 0% も登録できること（割引なしの法人契約）
    @Test
    def testAcceptsZeroDiscountRate(): Unit \ Assert =
        Assert.assertTrue(Result.isOk(
            registerCorporate(anId(), "株式会社サンプル", "sales@example.com", None, None,
                              "CT-0001", 0)))

    /// 負の割引率は登録できないこと（割増になってしまう）
    @Test
    def testRejectsNegativeDiscountRate(): Unit \ Assert =
        Assert.assertEq(expected = Some(ShipperError.DiscountRateOutOfRange),
            errorOf(registerCorporate(anId(), "株式会社サンプル", "sales@example.com", None, None,
                              "CT-0001", -1)))

    ///
    /// 割引率を画面表示用の百分率へ変換できること。
    ///
    /// 内部表現（万分率）をそのまま画面に出すと `1500%` に見える。
    ///
    @Test
    def testFormatsDiscountRateAsPercent(): Unit \ Assert =
        Assert.assertEq(expected = "15.00", discountRatePercent(1500));
        Assert.assertEq(expected = "30.00", discountRatePercent(3000));
        Assert.assertEq(expected = "0.00", discountRatePercent(0));
        Assert.assertEq(expected = "7.50", discountRatePercent(750))

    // ---- 荷主 ID ----

    /// 荷主 ID が空なら登録できないこと
    @Test
    def testRejectsEmptyShipperId(): Unit \ Assert =
        Assert.assertEq(expected = Some(ShipperError.ShipperIdIsRequired),
            errorOf(registerIndividual("", "山田太郎", "yamada@example.com", None, None)))

    /// 荷主 ID が保持されること
    @Test
    def testKeepsShipperId(): Unit \ Assert =
        match registerIndividual(anId(), "山田太郎", "yamada@example.com", None, None) {
            case Err(e) => Assert.fail("登録に失敗した: ${e}")
            case Ok(s)  => Assert.assertEq(expected = anId(), shipperIdOf(s))
        }

    // ---- 永続化値との対応 ----

    /// 荷主種別の永続化値との相互変換が対応していること
    @Test
    def testShipperTypeRoundTrip(): Unit \ Assert =
        let types = ShipperType.Individual :: ShipperType.Corporate :: Nil;
        Assert.assertTrue(types |> List.forAll(t ->
            ShipperModel.shipperTypeFromPersisted(ShipperModel.shipperTypeToPersisted(t)) == Some(t)))

    ///
    /// 未知の永続化値は種別として受け付けないこと。
    ///
    /// 既定値（個人）へ倒すと、DB の値が壊れたときに法人が個人として扱われ、
    /// **割引が静かに消える**。
    ///
    @Test
    def testUnknownShipperTypeIsRejected(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(ShipperModel.shipperTypeFromPersisted("UNKNOWN")));
        Assert.assertTrue(Option.isEmpty(ShipperModel.shipperTypeFromPersisted("")))
}
