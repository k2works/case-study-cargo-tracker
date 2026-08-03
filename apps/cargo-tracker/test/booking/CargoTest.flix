///
/// `Cargo` 集約と値オブジェクトの単体テスト。
///
/// 効果を一切要求しない。DB もサーバも起動せず、ビジネスルールだけを固定する。
/// 対応するルールは [ドメインモデル設計](../../../../docs/design/domain-model.md)
/// の「Booking Context - ビジネスルール」1・2 と、状態遷移の起点（ルール 4）である。
///
mod CargoTest {
    use BookingModel.{Cargo, BookingStatus, CargoType, BookingError}
    use BookingModel.{bookCargo, bookingIdOf, shipperIdOf, bookingStatusOf, cargoTypeOf,
                      weightGramsOf, quantityOf, descriptionOf, dimensionsOf,
                      routeSpecificationOf, originOf, destinationOf, arrivalDeadlineOf,
                      generateBookingId, weightKilograms}

    def aBookingId(): String = "BK-3F2504E0"
    def aShipperId(): String = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
    def aDeadline(): String = "2026-12-31"

    /// 冷凍・冷蔵は温度条件が必須（BK-8）
    def aTemperature(): BookingModel.TemperatureRequirement =
        match BookingModel.requireTemperatureRequirement(-25000i64, -18000i64, "CELSIUS") {
            case Ok(t)  => t
            case Err(e) => bug!("フィクスチャの生成に失敗した: ${e}")
        }

    def book(origin: String, destination: String, deadline: String,
             weightGrams: Int64): Result[BookingError, Cargo] =
        bookCargo(aBookingId(), aShipperId(), origin, destination, deadline,
                  CargoType.General, weightGrams, None, None, None, None, None)

    def errorOf(r: Result[BookingError, Cargo]): Option[BookingError] = match r {
        case Err(e) => Some(e)
        case Ok(_)  => None
    }

    // ---- 正常系 ----

    /// 必須項目だけで予約できること
    @Test
    def testBooksCargoWithRequiredFields(): Unit \ Assert =
        match book("JPOSA", "USLAX", aDeadline(), 1200000i64) {
            case Err(e) => Assert.fail("予約に失敗した: ${e}")
            case Ok(c)  =>
                Assert.assertEq(expected = aBookingId(), bookingIdOf(c));
                Assert.assertEq(expected = aShipperId(), shipperIdOf(c));
                Assert.assertEq(expected = "JPOSA", originOf(routeSpecificationOf(c)));
                Assert.assertEq(expected = "USLAX", destinationOf(routeSpecificationOf(c)));
                Assert.assertEq(expected = aDeadline(), arrivalDeadlineOf(routeSpecificationOf(c)))
        }

    ///
    /// 登録直後の状態が「仮受付」であること（US04 受入基準 4）。
    ///
    /// 状態を引数で受け取らないため、**任意の状態の貨物は作れない**。
    /// これが状態遷移のルールの起点になる。
    ///
    @Test
    def testNewCargoIsPreliminary(): Unit \ Assert =
        match book("JPOSA", "USLAX", aDeadline(), 1000i64) {
            case Err(e) => Assert.fail("予約に失敗した: ${e}")
            case Ok(c)  => Assert.assertTrue(bookingStatusOf(c) == BookingStatus.Preliminary)
        }

    /// 港コードが小文字でも大文字に正規化されること
    @Test
    def testNormalizesLocationCodeToUpperCase(): Unit \ Assert =
        match book("jposa", "uslax", aDeadline(), 1000i64) {
            case Err(e) => Assert.fail("予約に失敗した: ${e}")
            case Ok(c)  =>
                Assert.assertEq(expected = "JPOSA", originOf(routeSpecificationOf(c)));
                Assert.assertEq(expected = "USLAX", destinationOf(routeSpecificationOf(c)))
        }

    /// 任意項目（寸法・個数・品名）を伴って予約できること
    @Test
    def testBooksCargoWithOptionalFields(): Unit \ Assert =
        match bookCargo(aBookingId(), aShipperId(), "JPOSA", "USLAX", aDeadline(),
                        CargoType.Refrigerated, 1000i64, Some((100, 200, 300)), Some(5),
                        Some("冷凍マグロ"), None, Some(aTemperature())) {
            case Err(e) => Assert.fail("予約に失敗した: ${e}")
            case Ok(c)  =>
                Assert.assertEq(expected = Some((100, 200, 300)), dimensionsOf(c));
                Assert.assertEq(expected = Some(5), quantityOf(c));
                Assert.assertEq(expected = Some("冷凍マグロ"), descriptionOf(c));
                Assert.assertTrue(cargoTypeOf(c) == CargoType.Refrigerated)
        }

    // ---- ビジネスルール 2: 出発地と目的地 ----

    ///
    /// 出発地と目的地が同一なら予約できないこと。
    ///
    /// 運ぶ先が出発地なら運ぶ必要がない。**これを通すと、経路設計が
    /// 「距離 0 の経路」を探すことになる**。
    ///
    @Test
    def testRejectsSameOriginAndDestination(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.OriginAndDestinationAreSame),
            errorOf(book("JPOSA", "JPOSA", aDeadline(), 1000i64)))

    /// 大文字小文字が違うだけの同一港も同一とみなすこと
    @Test
    def testRejectsSameLocationIgnoringCase(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.OriginAndDestinationAreSame),
            errorOf(book("JPOSA", "jposa", aDeadline(), 1000i64)))

    /// 出発地が空なら予約できないこと
    @Test
    def testRejectsEmptyOrigin(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.OriginIsRequired),
            errorOf(book("", "USLAX", aDeadline(), 1000i64)))

    /// 目的地が空なら予約できないこと
    @Test
    def testRejectsEmptyDestination(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.DestinationIsRequired),
            errorOf(book("JPOSA", "", aDeadline(), 1000i64)))

    ///
    /// UN/LOCODE の形でない港コードを弾くこと。
    ///
    /// 5 文字の英数字であることだけを見る。**実在するかは検査しない**
    /// （それは `location` テーブルの役目）。
    ///
    @Test
    def testRejectsMalformedLocationCode(): Unit \ Assert =
        let malformed = "JP" :: "JPOSAX" :: "JP-SA" :: "日本大阪港" :: Nil;
        Assert.assertTrue(malformed |> List.forAll(bad ->
            errorOf(book(bad, "USLAX", aDeadline(), 1000i64)) ==
                Some(BookingError.LocationCodeIsMalformed)))

    // ---- 到着期限 ----

    /// 到着期限が空なら予約できないこと
    @Test
    def testRejectsEmptyArrivalDeadline(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.ArrivalDeadlineIsRequired),
            errorOf(book("JPOSA", "USLAX", "", 1000i64)))

    /// 日付の形でない到着期限を弾くこと
    @Test
    def testRejectsMalformedArrivalDeadline(): Unit \ Assert =
        let malformed = "2026/12/31" :: "31-12-2026" :: "2026-1-1" :: "来年末" :: Nil;
        Assert.assertTrue(malformed |> List.forAll(bad ->
            errorOf(book("JPOSA", "USLAX", bad, 1000i64)) ==
                Some(BookingError.ArrivalDeadlineIsMalformed)))

    // ---- 重量 ----

    ///
    /// 重量 0 の貨物は予約できないこと。
    ///
    /// 料金は重量に比例する。0 を通すと**料金 0 の予約**が作れる。
    ///
    @Test
    def testRejectsZeroWeight(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.WeightIsNotPositive),
            errorOf(book("JPOSA", "USLAX", aDeadline(), 0i64)))

    /// 負の重量も弾くこと
    @Test
    def testRejectsNegativeWeight(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.WeightIsNotPositive),
            errorOf(book("JPOSA", "USLAX", aDeadline(), -1i64)))

    /// 1 グラムは予約できること（境界の内側）
    @Test
    def testAcceptsMinimalWeight(): Unit \ Assert =
        Assert.assertTrue(Result.isOk(book("JPOSA", "USLAX", aDeadline(), 1i64)))

    // ---- 任意項目の境界 ----

    /// 個数 0 を弾くこと（0 個の貨物は運ぶ対象にならない）
    @Test
    def testRejectsZeroQuantity(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.QuantityIsNotPositive),
            errorOf(bookCargo(aBookingId(), aShipperId(), "JPOSA", "USLAX", aDeadline(),
                              CargoType.General, 1000i64, None, Some(0), None, None, None)))

    /// 寸法に 0 以下の辺があれば弾くこと
    @Test
    def testRejectsNonPositiveDimension(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.DimensionIsNotPositive),
            errorOf(bookCargo(aBookingId(), aShipperId(), "JPOSA", "USLAX", aDeadline(),
                              CargoType.General, 1000i64, Some((100, 0, 300)), None, None, None, None)))

    /// 品名が 500 文字を超えたら弾くこと
    @Test
    def testRejectsTooLongDescription(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.DescriptionIsTooLong),
            errorOf(bookCargo(aBookingId(), aShipperId(), "JPOSA", "USLAX", aDeadline(),
                              CargoType.General, 1000i64, None, None, Some("あ".repeat(501)), None, None)))

    /// 空文字の品名は未入力として扱うこと
    @Test
    def testTreatsBlankDescriptionAsAbsent(): Unit \ Assert =
        match bookCargo(aBookingId(), aShipperId(), "JPOSA", "USLAX", aDeadline(),
                        CargoType.General, 1000i64, None, None, Some("   "), None, None) {
            case Err(e) => Assert.fail("予約に失敗した: ${e}")
            case Ok(c)  => Assert.assertTrue(Option.isEmpty(descriptionOf(c)))
        }

    // ---- 識別子 ----

    /// 荷主 ID が空なら予約できないこと
    @Test
    def testRejectsEmptyShipperId(): Unit \ Assert =
        Assert.assertEq(expected = Some(BookingError.ShipperIdIsRequired),
            errorOf(bookCargo(aBookingId(), "", "JPOSA", "USLAX", aDeadline(),
                              CargoType.General, 1000i64, None, None, None, None, None)))

    /// 予約番号が `BK-` + 8 文字で生成されること
    @Test
    def testGeneratesBookingId(): Unit \ Assert =
        Assert.assertEq(expected = "BK-3F2504E0", generateBookingId(aShipperId()));
        Assert.assertEq(expected = 11, String.length(generateBookingId(aShipperId())))

    // ---- 永続化値との対応 ----

    /// 予約状態の永続化値が往復すること
    @Test
    def testBookingStatusRoundTrip(): Unit \ Assert =
        let all = BookingStatus.Preliminary :: BookingStatus.RouteProposed ::
                  BookingStatus.Confirmed :: BookingStatus.TrackingIssued ::
                  BookingStatus.InTransit :: BookingStatus.Delivered ::
                  BookingStatus.Settled :: BookingStatus.Cancelled :: Nil;
        Assert.assertTrue(all |> List.forAll(s ->
            BookingModel.statusFromPersisted(BookingModel.statusToPersisted(s)) == Some(s)))

    ///
    /// 未知の予約状態を既定値へ倒さないこと。
    ///
    /// 「仮受付」へ倒すと、**確定済みの予約が未確定に見える**。
    ///
    @Test
    def testUnknownBookingStatusIsRejected(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(BookingModel.statusFromPersisted("UNKNOWN")))

    /// 貨物種別の永続化値が往復すること
    @Test
    def testCargoTypeRoundTrip(): Unit \ Assert =
        Assert.assertTrue(BookingModel.allCargoTypes() |> List.forAll(t ->
            BookingModel.cargoTypeFromPersisted(BookingModel.cargoTypeToPersisted(t)) == Some(t)))

    ///
    /// 設計にない貨物種別を受け付けないこと。
    ///
    /// UI 設計には一時期 `PERISHABLE` という値が書かれていたが、ドメインモデルには
    /// 存在しない。**設計の食い違いが実装へ入り込んでいないこと**をここで固定する。
    ///
    @Test
    def testRejectsUndefinedCargoType(): Unit \ Assert =
        Assert.assertTrue(Option.isEmpty(BookingModel.cargoTypeFromPersisted("PERISHABLE")));
        Assert.assertTrue(Option.isEmpty(BookingModel.cargoTypeFromPersisted("GENERAL_CARGO")))

    // ---- 表示 ----

    ///
    /// 重量がキログラム表記になること。
    ///
    /// 内部はグラム。そのまま出すと `1200000` と表示され、桁を読み違える。
    ///
    @Test
    def testFormatsWeightAsKilograms(): Unit \ Assert =
        Assert.assertEq(expected = "1200", weightKilograms(1200000i64));
        Assert.assertEq(expected = "1.500", weightKilograms(1500i64));
        Assert.assertEq(expected = "0.001", weightKilograms(1i64));
        Assert.assertEq(expected = "12.050", weightKilograms(12050i64))

    /// 予約状態が業務の言葉で表示されること（画面に列挙値を出さない）
    @Test
    def testStatusLabelUsesBusinessLanguage(): Unit \ Assert =
        Assert.assertEq(expected = "仮受付", BookingModel.statusLabel(BookingStatus.Preliminary));
        Assert.assertEq(expected = "経路提案済", BookingModel.statusLabel(BookingStatus.RouteProposed))

    ///
    /// 表示ラベルとバッジ配色が UI 設計の定義と一致すること。
    ///
    /// **設計が正典**であり、実装で独自の言い回しを作らない。IT4 で
    /// 「経路設計中」「予約確定」「精算済」という独自ラベルを書いてしまい、
    /// 設計との突合で気付いた。
    ///
    @Test
    def testStatusLabelsMatchUiDesign(): Unit \ Assert =
        let expected =
            (BookingStatus.Preliminary, "仮受付", "badge bg-warning text-dark") ::
            (BookingStatus.RouteProposed, "経路提案済", "badge bg-primary") ::
            (BookingStatus.Confirmed, "確認済", "badge bg-success") ::
            (BookingStatus.TrackingIssued, "追跡番号発行済", "badge bg-info text-dark") ::
            (BookingStatus.InTransit, "輸送中", "badge bg-primary") ::
            (BookingStatus.Delivered, "配送完了", "badge bg-success") ::
            (BookingStatus.Settled, "精算完了", "badge bg-secondary") ::
            (BookingStatus.Cancelled, "キャンセル", "badge bg-danger") :: Nil;
        Assert.assertTrue(expected |> List.forAll(match (s, label, cls) ->
            BookingModel.statusLabel(s) == label and BookingModel.statusBadgeClass(s) == cls))

    /// 貨物種別が業務の言葉で表示されること
    @Test
    def testCargoTypeLabelUsesBusinessLanguage(): Unit \ Assert =
        Assert.assertEq(expected = "一般貨物", BookingModel.cargoTypeLabel(CargoType.General));
        Assert.assertEq(expected = "危険物", BookingModel.cargoTypeLabel(CargoType.Hazardous))
}
