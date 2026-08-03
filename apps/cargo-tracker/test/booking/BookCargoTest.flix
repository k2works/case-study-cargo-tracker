///
/// 貨物予約ユースケースの単体テスト。
///
/// **DB も Shipper Context も起動しない**。`CargoRepo`・`IdGen`・
/// `ShipperExistenceChecker` にハンドラを適用するだけで判断を固定できる。
///
/// ACL をスタブにすることで、Booking の単体テストが Shipper の実装に
/// 引きずられない。これがコンテキストを分けたことの実利である。
///
mod BookCargoTest {
    use BookingBookCargo.{BookCommand, BookResult, book, command, resultLabel}
    use BookingModel.{BookingStatus, CargoType, BookingError}
    use BookingModel.{bookingIdOf, shipperIdOf, bookingStatusOf, cargoTypeOf}
    use InMemoryHandlers.{withInMemoryCargoRepo, withStubShipperExistence, withFixedIds}

    def knownCode(): String = "SHP-3F2504E0"
    def knownShipper(): String = "3f2504e0-4f89-11d3-9a0c-0305e82c3301"
    def unknownCode(): String = "SHP-99999999"
    def newId(): String = "aabbccdd-1111-2222-3333-444444444444"

    def validCommand(shipperCode: String): BookCommand =
        command(shipperCode, "JPOSA", "USLAX", "2026-12-31", CargoType.General,
                1200000i64, None, None, None)

    /// ハンドラを差し替えて予約を実行する
    def execute(cmd: BookCommand): (BookResult, List[BookingModel.Cargo]) =
        withStubShipperExistence((knownCode(), knownShipper()) :: Nil, () ->
            withFixedIds(newId(), () ->
                withInMemoryCargoRepo(Nil, () -> book(cmd))))

    // ---- 正常系 ----

    ///
    /// 予約すると予約番号が発行され、状態が「仮受付」になること（US04 受入基準 4）。
    ///
    @Test
    def testBooksCargoAsPreliminary(): Unit \ Assert =
        let (result, stored) = execute(validCommand(knownCode()));
        match result {
            case BookResult.Booked(c) =>
                Assert.assertEq(expected = "BK-AABBCCDD", bookingIdOf(c));
                Assert.assertTrue(bookingStatusOf(c) == BookingStatus.Preliminary);
                Assert.assertEq(expected = knownShipper(), shipperIdOf(c));
                Assert.assertEq(expected = 1, List.length(stored))
            case other => Assert.fail("予約に失敗した: ${resultLabel(other)}")
        }

    /// 貨物種別が保持されること
    @Test
    def testKeepsCargoType(): Unit \ Assert =
        let cmd = command(knownCode(), "JPOSA", "USLAX", "2026-12-31",
                          CargoType.Refrigerated, 1000i64, None, None, None);
        let (result, _) = execute(cmd);
        match result {
            case BookResult.Booked(c) =>
                Assert.assertTrue(cargoTypeOf(c) == CargoType.Refrigerated)
            case other => Assert.fail("予約に失敗した: ${resultLabel(other)}")
        }

    // ---- ACL: 荷主の存在確認（US04 受入基準 1・受入条件 7） ----

    ///
    /// 存在しない荷主 ID では予約できないこと。
    ///
    /// **壊れ方**: Booking と Shipper は別コンテキストであり DB 外部キーを張らない。
    /// ここで確認しなければ、存在しない荷主に紐づく貨物が静かに生まれる。
    /// 外部キーを張っていたとしても、利用者に見えるのは 500 になる。
    ///
    @Test
    def testRejectsUnknownShipper(): Unit \ Assert =
        let (result, stored) = execute(validCommand(unknownCode()));
        match result {
            case BookResult.Invalid(e) =>
                Assert.assertTrue(e == BookingError.ShipperNotFound);
                Assert.assertTrue(List.isEmpty(stored))
            case other => Assert.fail("存在しない荷主で予約が通った: ${resultLabel(other)}")
        }

    /// 荷主 ID が空なら予約できないこと
    @Test
    def testRejectsEmptyShipperId(): Unit \ Assert =
        let (result, stored) = execute(validCommand(""));
        match result {
            case BookResult.Invalid(e) =>
                Assert.assertTrue(e == BookingError.ShipperIdIsRequired);
                Assert.assertTrue(List.isEmpty(stored))
            case other => Assert.fail("荷主 ID なしで予約が通った: ${resultLabel(other)}")
        }

    ///
    /// **荷主の確認が入力の検証より先に走ること**。
    ///
    /// 荷主が存在しないうえに港コードも壊れている場合、返るのは `ShipperNotFound` である。
    /// 順序が逆だと、荷主が存在しないことに気付く前に入力の修正を求めることになり、
    /// 直しても直しても通らない状態に利用者を置く。
    ///
    @Test
    def testChecksShipperBeforeValidatingInput(): Unit \ Assert =
        let cmd = command(unknownCode(), "BAD", "USLAX", "2026-12-31",
                          CargoType.General, 1000i64, None, None, None);
        let (result, _) = execute(cmd);
        match result {
            case BookResult.Invalid(e) => Assert.assertTrue(e == BookingError.ShipperNotFound)
            case other => Assert.fail("順序が期待と異なる: ${resultLabel(other)}")
        }

    // ---- 検証に失敗したら保存しない ----

    /// 出発地と目的地が同一なら保存されないこと
    @Test
    def testDoesNotSaveWhenOriginEqualsDestination(): Unit \ Assert =
        let cmd = command(knownCode(), "JPOSA", "JPOSA", "2026-12-31",
                          CargoType.General, 1000i64, None, None, None);
        let (result, stored) = execute(cmd);
        match result {
            case BookResult.Invalid(e) =>
                Assert.assertTrue(e == BookingError.OriginAndDestinationAreSame);
                Assert.assertTrue(List.isEmpty(stored))
            case other => Assert.fail("同一港の予約が通った: ${resultLabel(other)}")
        }

    /// 重量が 0 なら保存されないこと
    @Test
    def testDoesNotSaveWithZeroWeight(): Unit \ Assert =
        let cmd = command(knownCode(), "JPOSA", "USLAX", "2026-12-31",
                          CargoType.General, 0i64, None, None, None);
        let (result, stored) = execute(cmd);
        match result {
            case BookResult.Invalid(e) =>
                Assert.assertTrue(e == BookingError.WeightIsNotPositive);
                Assert.assertTrue(List.isEmpty(stored))
            case other => Assert.fail("重量 0 の予約が通った: ${resultLabel(other)}")
        }

    // ---- 一覧 ----

    /// 予約した貨物が一覧に現れること
    @Test
    def testListsBookedCargo(): Unit \ Assert =
        let listed = withStubShipperExistence((knownCode(), knownShipper()) :: Nil, () ->
            withFixedIds(newId(), () ->
                withInMemoryCargoRepo(Nil, () -> {
                    discard book(validCommand(knownCode()));
                    BookingBookCargo.listAll()
                })));
        Assert.assertEq(expected = 1, List.length(fst(listed)))
}
