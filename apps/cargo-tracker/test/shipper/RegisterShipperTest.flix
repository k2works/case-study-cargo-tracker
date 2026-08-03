///
/// 荷主登録ユースケースの単体テスト。
///
/// **DB を起動しない**。`ShipperRepo` と `IdGen` にインメモリのハンドラを適用するだけで、
/// 判断（重複判定・種別による分岐・保存の有無）を固定できる。
///
/// IT3 のふりかえり P2 で「判断の順序に意味があると宣言しながら、その順序を固定する
/// テストが 1 件もなかった」ことが問題になった（Try T3）。**統合テストで済ませない**。
///
mod RegisterShipperTest {
    use InMemoryHandlers.{withInMemoryShipperRepo, withFixedIds}
    use ShipperModel.{Shipper, ShipperType, ShipperError}
    use ShipperModel.{registerIndividual, emailOf, nameOf, shipperCodeOf, shipperIdOf,
                      shipperTypeOf, contractNumberOf, discountRateOf}
    use ShipperRegister.{RegisterResult, RegisterCommand, register}

    def newId(): String = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"

    def individualCommand(): RegisterCommand =
        ShipperRegister.individualCommand("山田太郎", "yamada@example.com", None, None)

    def corporateCommand(): RegisterCommand =
        ShipperRegister.corporateCommand("株式会社サンプル", "sales@example.com", None, None,
                                         "CT-0001", 1500)

    /// 既存の荷主（重複判定の相手）
    def existing(): Shipper =
        match registerIndividual("11111111-0000-0000-0000-000000000000",
                                 "既存 花子", "yamada@example.com", None, None) {
            case Ok(s)  => s
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    /// リポジトリと ID 生成を差し替えて登録を実行する
    def execute(initial: List[Shipper], cmd: RegisterCommand): (RegisterResult, List[Shipper]) =
        withFixedIds(newId(), () ->
            withInMemoryShipperRepo(initial, () -> register(cmd)))

    // ---- 正常系 ----

    /// 個人荷主を登録すると保存され、発行された荷主が返ること
    @Test
    def testRegistersIndividual(): Unit \ Assert =
        let (result, stored) = execute(Nil, individualCommand());
        match result {
            case RegisterResult.Registered(s) =>
                Assert.assertEq(expected = "山田太郎", nameOf(s));
                Assert.assertEq(expected = newId(), shipperIdOf(s));
                Assert.assertEq(expected = "SHP-3F2504E0", shipperCodeOf(s));
                Assert.assertEq(expected = 1, List.length(stored))
            case other => Assert.fail("登録に失敗した: ${ShipperRegister.resultLabel(other)}")
        }

    /// 法人荷主は契約番号と割引率を伴って保存されること
    @Test
    def testRegistersCorporate(): Unit \ Assert =
        let (result, stored) = execute(Nil, corporateCommand());
        match result {
            case RegisterResult.Registered(s) =>
                Assert.assertTrue(shipperTypeOf(s) == ShipperType.Corporate);
                Assert.assertEq(expected = Some("CT-0001"), contractNumberOf(s));
                Assert.assertEq(expected = Some(1500), discountRateOf(s));
                Assert.assertEq(expected = 1, List.length(stored))
            case other => Assert.fail("法人の登録に失敗した: ${ShipperRegister.resultLabel(other)}")
        }

    // ---- 重複（US02 受入基準 2） ----

    ///
    /// 同一メールが登録済みなら、**既存の荷主を添えて**重複を返すこと。
    ///
    /// 「重複しています」だけでは利用者は次に何もできない。既存の荷主を返すことで
    /// 画面が「この荷主を使う」という導線を出せる（Try T4）。
    ///
    @Test
    def testReturnsExistingShipperOnDuplicateEmail(): Unit \ Assert =
        let (result, stored) = execute(existing() :: Nil, individualCommand());
        match result {
            case RegisterResult.DuplicateEmail(found) =>
                Assert.assertEq(expected = "既存 花子", nameOf(found));
                // **保存されていないこと**。重複なのに 2 件目ができては意味がない
                Assert.assertEq(expected = 1, List.length(stored))
            case other => Assert.fail("重複が検出されなかった: ${ShipperRegister.resultLabel(other)}")
        }

    ///
    /// メールの比較で大文字小文字を区別しないこと。
    ///
    /// `Yamada@Example.com` と `yamada@example.com` は同じ宛先である。
    /// 区別すると、同じ人が別の荷主として二重登録される。
    ///
    @Test
    def testDuplicateCheckIsCaseInsensitive(): Unit \ Assert =
        let cmd = ShipperRegister.individualCommand("山田太郎", "Yamada@Example.com", None, None);
        let (result, _) = execute(existing() :: Nil, cmd);
        match result {
            case RegisterResult.DuplicateEmail(_) => ()
            case other => Assert.fail("大文字小文字違いの重複が検出されなかった: ${ShipperRegister.resultLabel(other)}")
        }

    // ---- 判断の順序（Try T3） ----

    ///
    /// **入力の検証が重複判定より先に走ること**。
    ///
    /// 順序が逆だと、メールの形式が壊れている入力で重複検索が走る。
    /// 「重複していません」と返してから形式エラーを出すことになり、
    /// 利用者は 2 回に分けて誤りを知らされる。
    ///
    @Test
    def testValidatesBeforeCheckingDuplicate(): Unit \ Assert =
        let cmd = ShipperRegister.individualCommand("", "yamada@example.com", None, None);
        let (result, stored) = execute(existing() :: Nil, cmd);
        match result {
            case RegisterResult.Invalid(e) =>
                Assert.assertTrue(e == ShipperError.NameIsRequired);
                Assert.assertEq(expected = 1, List.length(stored))
            case other => Assert.fail("検証エラーより先に重複が返された: ${ShipperRegister.resultLabel(other)}")
        }

    ///
    /// 検証に失敗したら保存しないこと。
    ///
    /// **壊れ方**: ここが破れると、不正な荷主が DB に残る。
    ///
    @Test
    def testDoesNotSaveInvalidShipper(): Unit \ Assert =
        let cmd = ShipperRegister.individualCommand("山田太郎", "not-an-email", None, None);
        let (result, stored) = execute(Nil, cmd);
        match result {
            case RegisterResult.Invalid(e) =>
                Assert.assertTrue(e == ShipperError.EmailIsMalformed);
                Assert.assertTrue(List.isEmpty(stored))
            case other => Assert.fail("不正な入力が保存された: ${ShipperRegister.resultLabel(other)}")
        }

    ///
    /// 法人で契約番号が欠けている場合、個人へ倒さずに検証エラーを返すこと。
    ///
    /// **壊れ方**: 個人へ倒すと、法人として登録したつもりの荷主に割引が効かない。
    ///
    @Test
    def testCorporateWithoutContractIsInvalid(): Unit \ Assert =
        let cmd = ShipperRegister.corporateCommand("株式会社サンプル", "sales@example.com",
                                                   None, None, "", 1500);
        let (result, stored) = execute(Nil, cmd);
        match result {
            case RegisterResult.Invalid(e) =>
                Assert.assertTrue(e == ShipperError.ContractNumberIsRequired);
                Assert.assertTrue(List.isEmpty(stored))
            case other => Assert.fail("契約番号なしの法人が登録された: ${ShipperRegister.resultLabel(other)}")
        }

    // ---- 一覧 ----

    /// 登録した荷主が一覧に現れること
    @Test
    def testListsRegisteredShippers(): Unit \ Assert =
        let listed = withFixedIds(newId(), () ->
            withInMemoryShipperRepo(existing() :: Nil, () -> {
                discard register(individualCommand());
                ShipperRegister.listAll()
            }));
        // 重複メールのため登録されず、既存の 1 件のみ
        Assert.assertEq(expected = 1, List.length(fst(listed)))
}
