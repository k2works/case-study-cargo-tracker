///
/// 危険物申告・温度管理条件の単体テスト（US05）。
///
/// 対応するビジネスルールは [ドメインモデル設計](../../../../docs/design/domain-model.md)
/// の Booking Context ルール 7・8（`business_rule_traceability.md` の BK-7・BK-8）。
///
/// 効果を一切要求しない。値オブジェクトの不変条件と、集約が種別ごとの必須性を
/// 守ることだけを固定する。
///
mod SpecialRequirementsTest {
    use BookingModel.{HazardousDeclaration, TemperatureRequirement, TemperatureUnit,
                      BookingError, CargoType}
    use BookingModel.{requireHazardousDeclaration, requireTemperatureRequirement,
                      hazardousClassOf, unNumberOf, properShippingNameOf,
                      minTemperatureMilliOf, maxTemperatureMilliOf, temperatureUnitOf,
                      temperatureUnitToPersisted, temperatureUnitFromPersisted}

    // ---- 危険物申告 ----

    @Test
    def testBuildsHazardousDeclaration(): Unit \ Assert =
        match requireHazardousDeclaration("3", "UN1203", "ガソリン") {
            case Err(e) => Assert.fail("有効な申告が拒否された: ${e}")
            case Ok(d)  =>
                Assert.assertEq(expected = "3", hazardousClassOf(d));
                Assert.assertEq(expected = "UN1203", unNumberOf(d));
                Assert.assertEq(expected = "ガソリン", properShippingNameOf(d))
        }

    @Test
    def testRejectsEmptyHazardousClass(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.HazardousClassIsRequired),
                        errOf(requireHazardousDeclaration("", "UN1203", "ガソリン")))

    @Test
    def testRejectsBlankHazardousClass(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.HazardousClassIsRequired),
                        errOf(requireHazardousDeclaration("   ", "UN1203", "ガソリン")))

    ///
    /// UN 番号は `UN` + 4 桁。
    ///
    /// 形式を検証するのは、**書類と積載制限がこの番号で決まる**ためである。
    /// 誤った番号のまま輸送すると、法令上の要件を満たさない貨物が動く。
    ///
    @Test
    def testRejectsMalformedUnNumber(): Unit \ Assert =
        let cases = "1203" :: "UN123" :: "UN12034" :: "UNABCD" :: "un1203" :: "" :: Nil;
        Assert.assertTrue(cases |> List.forAll(v ->
            errOf(requireHazardousDeclaration("3", v, "ガソリン")) ==
                Some(BookingError.UnNumberIsMalformed)))

    /// 全角数字の UN 番号を通さないこと（軸 1-5）
    @Test
    def testRejectsFullWidthUnNumber(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.UnNumberIsMalformed),
                        errOf(requireHazardousDeclaration("3", "UN１２０３", "ガソリン")))

    @Test
    def testRejectsEmptyProperShippingName(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.ProperShippingNameIsRequired),
                        errOf(requireHazardousDeclaration("3", "UN1203", "")))

    ///
    /// 正式輸送品名の長さ上限は DB 列（`VARCHAR(200)`）に合わせる。
    ///
    /// 上限を持たないと、DB で切り詰められるか例外になる。
    /// **アプリが受け入れた値を DB が拒否する**状態は、利用者から見て
    /// 「登録したのに保存されていない」という最も分かりにくい壊れ方になる。
    ///
    @Test
    def testRejectsTooLongProperShippingName(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.ProperShippingNameIsTooLong),
                        errOf(requireHazardousDeclaration("3", "UN1203", "あ".repeat(201))))

    @Test
    def testAcceptsProperShippingNameAtTheLimit(): Unit \ Assert =
        Assert.assertEq(expected = None,
                        errOf(requireHazardousDeclaration("3", "UN1203", "あ".repeat(200))))

    // ---- 温度管理条件 ----

    ///
    /// 温度は**千分の一度の整数**で保持する。
    ///
    /// 浮動小数点で持つと、料金や積載判定に誤差が混ざる。IT4 で割引率（万分率）と
    /// 重量（グラム）に採った方針をここでも守る。
    ///
    @Test
    def testBuildsTemperatureRequirement(): Unit \ Assert =
        match requireTemperatureRequirement(-25500i64, -18000i64, "CELSIUS") {
            case Err(e) => Assert.fail("有効な温度条件が拒否された: ${e}")
            case Ok(t)  =>
                Assert.assertEq(expected = -25500i64, minTemperatureMilliOf(t));
                Assert.assertEq(expected = -18000i64, maxTemperatureMilliOf(t));
                Assert.assertEq(expected = TemperatureUnit.Celsius, temperatureUnitOf(t))
        }

    ///
    /// **負の温度の符号が消えないこと**。
    ///
    /// IT4 で「小数の文字列 → 固定小数点整数」の変換を 2 箇所に書き、
    /// どちらも負の符号を落とした（`-0.5` が `0.50%` になった）。
    /// 冷凍貨物の温度は**ほぼ常に負**であり、ここで同じ失敗をすると
    /// 保冷条件が正反対になる。
    ///
    @Test
    def testKeepsSignOfNegativeTemperature(): Unit \ Assert =
        match requireTemperatureRequirement(-500i64, -100i64, "CELSIUS") {
            case Err(e) => Assert.fail("負の温度が拒否された: ${e}")
            case Ok(t)  =>
                Assert.assertEq(expected = -500i64, minTemperatureMilliOf(t));
                Assert.assertEq(expected = -100i64, maxTemperatureMilliOf(t))
        }

    /// 0 度をまたぐ範囲を扱えること
    @Test
    def testAcceptsRangeAcrossZero(): Unit \ Assert =
        match requireTemperatureRequirement(-5000i64, 5000i64, "CELSIUS") {
            case Err(e) => Assert.fail("0 度をまたぐ範囲が拒否された: ${e}")
            case Ok(t)  =>
                Assert.assertEq(expected = -5000i64, minTemperatureMilliOf(t));
                Assert.assertEq(expected = 5000i64, maxTemperatureMilliOf(t))
        }

    ///
    /// 上下限の逆転を拒否すること。
    ///
    /// 通してしまうと、**どの温度でも条件を満たさない**貨物が登録できる。
    /// 荷役側は「条件を満たす保管場所がない」としか分からない。
    ///
    @Test
    def testRejectsInvertedRange(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.TemperatureRangeIsInverted),
                        errOf(requireTemperatureRequirement(-18000i64, -25000i64, "CELSIUS")))

    /// 上下限が同じ値は許すこと（一点管理）
    @Test
    def testAcceptsEqualBounds(): Unit \ Assert =
        Assert.assertEq(expected = None,
                        errOf(requireTemperatureRequirement(-20000i64, -20000i64, "CELSIUS")))

    ///
    /// **文字列の解釈はここでは扱わない**。
    ///
    /// 温度の入力（`-25.5`・`-25℃`・全角）を数へ直すのは境界の責務であり、
    /// `SharedDecimal.parseSignedScaled`（`DecimalTest`）と
    /// 予約フォームの受入テスト（`BookingHttpTest`）が固定している。
    /// ドメインを infrastructure へ依存させないための切り分けである
    /// （arch-lint 規約 1）。
    ///

    @Test
    def testRejectsUnknownTemperatureUnit(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.TemperatureUnitIsMalformed),
                        errOf(requireTemperatureRequirement(-25000i64, -18000i64, "KELVIN")))

    /// 華氏も扱えること
    @Test
    def testAcceptsFahrenheit(): Unit \ Assert =
        match requireTemperatureRequirement(-13000i64, 0i64, "FAHRENHEIT") {
            case Err(e) => Assert.fail("華氏が拒否された: ${e}")
            case Ok(t)  => Assert.assertEq(expected = TemperatureUnit.Fahrenheit,
                                           temperatureUnitOf(t))
        }

    // ---- 永続化値との対応 ----

    @Test
    def testTemperatureUnitRoundTrip(): Unit \ Assert =
        let units = TemperatureUnit.Celsius :: TemperatureUnit.Fahrenheit :: Nil;
        Assert.assertTrue(units |> List.forAll(u ->
            temperatureUnitFromPersisted(temperatureUnitToPersisted(u)) == Some(u)))

    ///
    /// 未知の永続化値を既定値へ倒さないこと。
    ///
    /// 華氏を摂氏として読み出すと、`-13°F`（= -25°C）が `-13°C` になる。
    /// 12 度の差は冷凍と冷蔵の違いであり、貨物を傷める。
    ///
    @Test
    def testRejectsUnknownPersistedTemperatureUnit(): Unit \ Assert =
        Assert.assertEq(expected = None, temperatureUnitFromPersisted("KELVIN"))

    // 温度の表示は `SharedDecimal.formatScaled` の責務へ移した（IT5 レビュー）。
    // ドメインが infrastructure を参照しないためであり、桁と符号の検証は
    // `DecimalTest.testFormatsNegativeValues` などが持つ。

    // ---- 集約が守る必須性（BK-7・BK-8） ----

    ///
    /// 危険物なら申告が必須であること（BK-7）。
    ///
    /// **不変条件は集約が守る**。画面側だけで必須にすると、別の入口
    /// （API・バッチ・テスト）から申告なしの危険物が入る。
    ///
    @Test
    def testRequiresHazardousDeclarationForHazardousCargo(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.HazardousDeclarationIsRequired),
                        errOf(book(CargoType.Hazardous, None, None)))

    /// 危険物なら申告付きで予約できること
    @Test
    def testBooksHazardousCargoWithDeclaration(): Unit \ Assert =
        Assert.assertEq(expected = None,
                        errOf(book(CargoType.Hazardous, Some(aDeclaration()), None)))

    ///
    /// 危険物以外に申告を付けられないこと。
    ///
    /// 付けられると、一般貨物に危険物の書類が付いた状態が作れる。
    /// 取扱港・積載制限の判定が種別と申告で食い違う。
    ///
    @Test
    def testRejectsHazardousDeclarationForNonHazardousCargo(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.HazardousDeclarationIsNotApplicable),
                        errOf(book(CargoType.General, Some(aDeclaration()), None)))

    /// 冷凍・冷蔵なら温度条件が必須であること（BK-8）
    @Test
    def testRequiresTemperatureRequirementForRefrigeratedCargo(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.TemperatureRequirementIsRequired),
                        errOf(book(CargoType.Refrigerated, None, None)))

    /// 冷凍・冷蔵なら温度条件付きで予約できること
    @Test
    def testBooksRefrigeratedCargoWithTemperature(): Unit \ Assert =
        Assert.assertEq(expected = None,
                        errOf(book(CargoType.Refrigerated, None, Some(aTemperature()))))

    /// 冷凍・冷蔵以外に温度条件を付けられないこと
    @Test
    def testRejectsTemperatureRequirementForNonRefrigeratedCargo(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.TemperatureRequirementIsNotApplicable),
                        errOf(book(CargoType.General, None, Some(aTemperature()))))

    ///
    /// 種別の取り違えを拒むこと。
    ///
    /// 危険物に温度条件だけを付けた場合、申告が無いことが先に分かる。
    ///
    @Test
    def testRejectsTemperatureOnHazardousCargo(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.HazardousDeclarationIsRequired),
                        errOf(book(CargoType.Hazardous, None, Some(aTemperature()))))

    /// 一般貨物は両方なしで予約できること
    @Test
    def testBooksGeneralCargoWithoutSpecialRequirements(): Unit \ Assert =
        Assert.assertEq(expected = None, errOf(book(CargoType.General, None, None)))

    ///
    /// 保持した特別要件を取り出せること。
    ///
    /// 保存しても読み出せなければ、予約詳細で確認できない。
    ///
    @Test
    def testKeepsSpecialRequirements(): Unit \ Assert =
        match book(CargoType.Refrigerated, None, Some(aTemperature())) {
            case Err(e) => Assert.fail("予約に失敗した: ${e}")
            case Ok(c)  => match BookingModel.temperatureRequirementOf(c) {
                case None    => Assert.fail("温度条件が保持されていない")
                case Some(t) => Assert.assertEq(expected = -25500i64, minTemperatureMilliOf(t))
            }
        }

    // ---- ヘルパー ----

    def aDeclaration(): HazardousDeclaration =
        match requireHazardousDeclaration("3", "UN1203", "ガソリン") {
            case Ok(d)  => d
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    def aTemperature(): TemperatureRequirement =
        match requireTemperatureRequirement(-25500i64, -18000i64, "CELSIUS") {
            case Ok(t)  => t
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    def book(cargoType: BookingModel.CargoType,
             hazardous: Option[HazardousDeclaration],
             temperature: Option[TemperatureRequirement]):
            Result[BookingError, BookingModel.Cargo] =
        BookingModel.bookCargo("BK-3F2504E0", "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
                               "JPOSA", "USLAX", "2026-12-31", "2026-01-01", cargoType, 1200000i64,
                               None, None, None, hazardous, temperature)

    def errOf(r: Result[BookingError, t]): Option[BookingError] = match r {
        case Err(e) => Some(e)
        case Ok(_)  => None
    }
}
